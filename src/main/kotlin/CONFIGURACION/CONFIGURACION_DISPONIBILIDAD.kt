package CONFIGURACION

import com.auth0.jwt.JWT
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.util.UUID

private const val CALENTAMIENTO_FREEMIUM_POR_DEFECTO = 2000
private const val CALENTAMIENTO_FREEMIUM_MAXIMO = 20_000
private const val TIEMPO_ESPERA_CALENTAMIENTO_MS = 3000L
private const val TIEMPO_MANTENER_CONEXION_MS = 60_000L

class EstadoFreemiumReadiness(cantidadSolicitada: Int) {
    val cantidadSolicitada: Int = cantidadSolicitada

    @Volatile
    private var listo = cantidadSolicitada == 0

    fun estaListo(): Boolean = listo

    internal suspend fun completar(log: Logger, calentamiento: suspend () -> Unit) {
        try {
            calentamiento()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Calentamiento HTTP Freemium incompleto; el servicio continúa disponible: {}", error.message, error)
        } finally {
            listo = true
        }
    }
}

private data class SolicitudCalentamiento(
    val ruta: String,
    val cuerpo: String? = null,
    val estadoEsperado: Int
)

fun Application.configurarCalentamientoFreemium(jwt: ConfiguracionJwt, disponibilidad: EstadoFreemiumReadiness) {
    val port = System.getenv("PORT")?.toIntOrNull()
        ?: environment.config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull()
        ?: 8080
    val token = JWT.create()
        .withIssuer(jwt.emisor)
        .withAudience(jwt.audiencia)
        .withSubject(UUID.randomUUID().toString())
        .sign(jwt.algoritmo)
    val log = environment.log
    val alcanceCalentamiento = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    monitor.subscribe(ApplicationStopped) { alcanceCalentamiento.cancel() }

    monitor.subscribe(ServerReady) {
        alcanceCalentamiento.launch {
            val inicio = System.nanoTime()
            log.info("Iniciando calentamiento HTTP Freemium: {} solicitudes locales", disponibilidad.cantidadSolicitada)
            var completadas = 0
            var inesperadas = 0

            disponibilidad.completar(log) {
                if (disponibilidad.cantidadSolicitada > 0) {
                    ejecutarCalentamiento(port, token, disponibilidad.cantidadSolicitada) {
                        completadas++
                        if (it) inesperadas++
                    }
                }
                if (inesperadas > 0) {
                    log.warn("Calentamiento HTTP Freemium: {} respuestas con estado inesperado", inesperadas)
                }
            }
            log.info(
                "Freemium HTTP listo: {}/{} solicitudes, {} estados inesperados, duración {} ms",
                completadas,
                disponibilidad.cantidadSolicitada,
                inesperadas,
                (System.nanoTime() - inicio) / 1_000_000
            )
        }
    }
}

private suspend fun ejecutarCalentamiento(port: Int, token: String, cantidad: Int, registrar: (Boolean) -> Unit) {
    val escenarios = listOf(
        SolicitudCalentamiento("/health", estadoEsperado = 200),
        SolicitudCalentamiento(
            "/api/v1/practice/evaluate-freemium",
            cuerpo = """{"userText":"SQL utiliza índices","idealText":"SQL utiliza índices","expectedKeywords":["SQL","índices"]}""",
            estadoEsperado = 200
        ),
        SolicitudCalentamiento("/api/v1/practice/evaluate-freemium", cuerpo = "{", estadoEsperado = 400),
        SolicitudCalentamiento("/ruta-inexistente-calentamiento", estadoEsperado = 404)
    )
    val url = "http://127.0.0.1:$port"
    HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = TIEMPO_ESPERA_CALENTAMIENTO_MS }
        engine {
            maxConnectionsCount = 1
            endpoint { maxConnectionsPerRoute = 1; keepAliveTime = TIEMPO_MANTENER_CONEXION_MS }
        }
    }.use { cliente ->
        repeat(cantidad) { indice ->
            val solicitud = escenarios[indice % escenarios.size]
            val respuesta = if (solicitud.cuerpo == null) {
                cliente.get(url + solicitud.ruta)
            } else {
                cliente.post(url + solicitud.ruta) {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(solicitud.cuerpo)
                }
            }
            respuesta.bodyAsText()
            registrar(respuesta.status.value != solicitud.estadoEsperado)
        }
    }
}

fun Application.crearEstadoFreemiumReadiness(): EstadoFreemiumReadiness {
    val valor = dotenv { ignoreIfMissing = true }["FREEMIUM_HTTP_WARMUP"]
        ?: System.getenv("FREEMIUM_HTTP_WARMUP")
    val cantidad = valor?.toIntOrNull()
        ?.takeIf { it in 0..CALENTAMIENTO_FREEMIUM_MAXIMO }
        ?: CALENTAMIENTO_FREEMIUM_POR_DEFECTO
    return EstadoFreemiumReadiness(cantidad)
}
