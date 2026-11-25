package INTEGRACIONES

import CONFIGURACION.ConfiguracionMotorVision
import CONFIGURACION.EstadoCargaModeloDivlara
import CONFIGURACION.EstadoModeloDivlara
import CONFIGURACION.ProveedorEstadoModeloDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.EstadoModeloDivlaraSalud
import ESQUEMAS.RespuestaError
import ESQUEMAS.RespuestaPrediccionVision
import ESQUEMAS.SolicitudPrediccionVision
import ESQUEMAS.aVentanaLandmarks
import SERVICIOS.PredictorVision
import SERVICIOS.ValidadorEntradaVision
import UTILIDADES.PoliticaResiliencia
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetSocketAddress
import java.net.Proxy
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Código 503 cuando el motor no responde a tiempo, está caído o contesta algo inutilizable (la app muestra "sin conexión"). */
const val CODIGO_MOTOR_NO_DISPONIBLE = "MOTOR_NO_DISPONIBLE"

private const val INTENTOS_MOTOR = 2 // la llamada y 1 reintento
private const val TIEMPO_CONEXION_MOTOR_MS = 1_000L
private const val VIGENCIA_ESTADO_MS = 15_000L
private const val CODIGO_INTERNO_POLITICA = "motor_no_disponible"
private const val CABECERA_WOL_TOKEN = "X-Wol-Token"
private const val ESPERA_ENTRE_WOL_MS = 60_000L

private val JSON_CLIENTE_MOTOR = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

@Serializable
private data class SaludMotor(val modeloDivlara: EstadoModeloDivlaraSalud)

/** Falla reintentable del motor (red, 5xx o cuerpo ilegible). Sin la URL ni el cuerpo, que podrían llevar datos. */
private class FallaMotor(motivo: String) : IOException(motivo)

/**
 * Cliente HTTP del motor de inferencia de visión (T3.11): el backend no carga el modelo, reenvía la solicitud a
 * `POST MOTOR_ENDPOINT` y devuelve su respuesta sin cambios.
 * - Tiempo máximo corto por intento y 1 reintento (solo fallas de red/5xx, nunca un rechazo por entrada inválida).
 * - Cortocircuito tras fallas seguidas: con el motor caído la app recibe 503 de inmediato en vez de esperar cada vez.
 * - Los rechazos del motor (400 de validación, 503 `modelo_no_disponible`) se relayan con su mismo código.
 *
 * También expone el último estado conocido del modelo para `/health` (sin bloquear: se refresca en segundo plano).
 */
class ClienteMotorVision(
    private val config: ConfiguracionMotorVision,
    motor: HttpClientEngine? = null,
    private val reloj: () -> Long = System::currentTimeMillis
) : PredictorVision, ProveedorEstadoModeloDivlara, AutoCloseable {

    private val log = LoggerFactory.getLogger(ClienteMotorVision::class.java)

    private val http = HttpClient(motor ?: motorEngine()) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.tiempoMaximoMs
            connectTimeoutMillis = minOf(TIEMPO_CONEXION_MOTOR_MS, config.tiempoMaximoMs)
        }
        expectSuccess = false
    }

    /**
     * D49: con `MOTOR_SOCKS_PROXY` (Tailscale en userspace dentro del contenedor de Railway), las llamadas al motor
     * y al WOL salen por ese proxy SOCKS5 con resolución remota (socks5h); sin la variable, conexión directa con CIO
     * como siempre. OkHttp porque CIO no soporta SOCKS según la tabla de engines de la documentación de Ktor.
     */
    private fun motorEngine(): HttpClientEngine =
        if (config.socksProxy.isEmpty()) CIO.create()
        else {
            val partes = config.socksProxy.removePrefix("socks5://").split(":")
            val puerto = partes.getOrNull(1)?.toIntOrNull() ?: 1055
            OkHttp.create {
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(partes[0], puerto))
            }
        }

    private val politica = PoliticaResiliencia(
        nombre = "motor",
        tiempoMaximo = config.tiempoMaximoMs.milliseconds,
        intentos = INTENTOS_MOTOR,
        esperaInicial = 100.milliseconds,
        fallosParaAbrir = 5,
        tiempoAbierto = 10.seconds
    )

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refrescando = AtomicBoolean(false)
    private val ultimoWolMs = AtomicLong(-1)

    @Volatile private var ultimoEstado: EstadoModeloDivlara = EstadoModeloDivlara.noDisponible()
    @Volatile private var estadoComprobadoEnMs = 0L

    private val urlSalud: String = config.endpoint.substringBeforeLast('/') + "/health"

    override suspend fun predecir(solicitud: SolicitudPrediccionVision): ResultadoPrediccionDivlara {
        validarAntesDeLlamar(solicitud)
        val cuerpo = JSON_CLIENTE_MOTOR.encodeToString(SolicitudPrediccionVision.serializer(), solicitud)
        val resultado = try {
            consultarDespertando(cuerpo)
        } catch (e: ErrorServicioExterno) {
            if (e.codigo == CODIGO_INTERNO_POLITICA) {
                registrarEstado(EstadoModeloDivlara(VERSION_MODELO_DIVLARA, EstadoCargaModeloDivlara.ERROR, false, error = CODIGO_MOTOR_NO_DISPONIBLE))
                log.warn("Motor de visión no disponible ({})", e.cause?.javaClass?.simpleName ?: "sin causa")
                throw ErrorServicioExterno(CODIGO_MOTOR_NO_DISPONIBLE, "El motor de visión no está disponible en este momento", e.cause)
            }
            if (e.codigo == "modelo_no_disponible") registrarEstado(EstadoModeloDivlara.noDisponible())
            throw e
        }
        registrarEstado(EstadoModeloDivlara(resultado.versionModelo, EstadoCargaModeloDivlara.CARGADO, true))
        return resultado
    }

    private suspend fun consultarConPolitica(cuerpo: String): ResultadoPrediccionDivlara =
        politica.ejecutar(esReintentable = { it is IOException || it is TimeoutCancellationException }) { consultar(cuerpo) }

    /**
     * D47: si el motor no se alcanza (conexión/timeout; no un 5xx) y hay `WOL_SERVICE_URL`, despierta la PC y reintenta
     * tras cada espera de [ConfiguracionMotorVision.esperasDespertarMs]; solo si todas fallan sale el error original.
     * Sin URL de WOL el comportamiento es el de siempre. El cortocircuito abierto (sin causa) no despierta nada.
     */
    private suspend fun consultarDespertando(cuerpo: String): ResultadoPrediccionDivlara {
        var error = try {
            return consultarConPolitica(cuerpo)
        } catch (e: ErrorServicioExterno) {
            e
        }
        if (config.wolUrl.isEmpty() || !esFallaDeConexion(error)) throw error
        enviarWol()
        for (espera in config.esperasDespertarMs) {
            delay(espera)
            try {
                return consultarConPolitica(cuerpo)
            } catch (e: ErrorServicioExterno) {
                if (e.codigo != CODIGO_INTERNO_POLITICA) throw e
                error = e
            }
        }
        throw error
    }

    private fun esFallaDeConexion(e: ErrorServicioExterno): Boolean {
        if (e.codigo != CODIGO_INTERNO_POLITICA) return false
        val causa = e.cause
        return (causa is IOException && causa !is FallaMotor) || causa is TimeoutCancellationException
    }

    /** Máximo un WOL por [ESPERA_ENTRE_WOL_MS]: las peticiones simultáneas no inundan el servicio de la NUC. */
    private suspend fun enviarWol() {
        val ahora = reloj()
        val anterior = ultimoWolMs.get()
        if (anterior >= 0 && ahora - anterior < ESPERA_ENTRE_WOL_MS) return
        if (!ultimoWolMs.compareAndSet(anterior, ahora)) return
        try {
            val respuesta = http.post(config.wolUrl) { header(CABECERA_WOL_TOKEN, config.wolToken) }
            log.info("WOL enviado al servicio de despertar (HTTP {})", respuesta.status.value)
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            log.warn("No se pudo enviar el WOL ({})", e.javaClass.simpleName) // sin URL ni token
        }
    }

    private fun registrarEstado(estado: EstadoModeloDivlara) {
        ultimoEstado = estado
        estadoComprobadoEnMs = System.currentTimeMillis()
    }

    /** Rechaza rápido lo que el motor también rechazaría (mismos códigos), sin gastar una llamada de red. */
    private fun validarAntesDeLlamar(solicitud: SolicitudPrediccionVision) {
        ValidadorEntradaVision.validarSolicitud(solicitud)
        val features = solicitud.features
        if (features != null) ValidadorEntradaVision.validarFeatures(features)
        else ValidadorEntradaVision.validarVentana(solicitud.aVentanaLandmarks())
    }

    private suspend fun consultar(cuerpo: String): ResultadoPrediccionDivlara {
        val respuesta: HttpResponse = http.post(config.endpoint) {
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        val texto = respuesta.bodyAsText()
        if (respuesta.status.isSuccess()) {
            val datos = runCatching { JSON_CLIENTE_MOTOR.decodeFromString<RespuestaPrediccionVision>(texto) }
                .getOrElse { throw FallaMotor("El motor respondió un cuerpo ilegible") }
            return ResultadoPrediccionDivlara(
                versionModelo = datos.versionModelo,
                predicciones = datos.predicciones,
                escala = datos.escala,
                manoCaraUna = datos.manoCaraUna,
                manoCaraDos = datos.manoCaraDos,
                manoCaraConteo = datos.manoCaraConteo
            )
        }
        val error = runCatching { JSON_CLIENTE_MOTOR.decodeFromString<RespuestaError>(texto) }.getOrNull()
        val estado = respuesta.status.value
        // Un rechazo explícito del motor se relaya tal cual: no se reintenta ni cuenta como motor caído.
        if (error != null && estado == 400) throw ErrorValidacion(error.error, error.mensaje ?: error.error)
        if (error != null && estado == 503) throw ErrorServicioExterno(error.error, error.mensaje ?: error.error)
        throw FallaMotor("El motor respondió HTTP $estado")
    }

    /** Último estado conocido del motor y su modelo. Nunca bloquea ni falla: `/health` es liveness. */
    override fun estado(): EstadoModeloDivlara {
        if (System.currentTimeMillis() - estadoComprobadoEnMs > VIGENCIA_ESTADO_MS) refrescarEstado()
        return ultimoEstado
    }

    private fun refrescarEstado() {
        if (!refrescando.compareAndSet(false, true)) return
        alcance.launch {
            try {
                val respuesta = http.get(urlSalud)
                val salud = JSON_CLIENTE_MOTOR.decodeFromString<SaludMotor>(respuesta.bodyAsText()).modeloDivlara
                registrarEstado(
                    EstadoModeloDivlara(
                        version = salud.version,
                        estado = if (salud.cargado) EstadoCargaModeloDivlara.CARGADO else EstadoCargaModeloDivlara.NO_DISPONIBLE,
                        cargado = salud.cargado,
                        error = salud.error
                    )
                )
            } catch (e: Exception) {
                registrarEstado(EstadoModeloDivlara(VERSION_MODELO_DIVLARA, EstadoCargaModeloDivlara.ERROR, false, error = CODIGO_MOTOR_NO_DISPONIBLE))
            } finally {
                refrescando.set(false)
            }
        }
    }

    override fun close() {
        alcance.cancel()
        http.close()
    }
}
