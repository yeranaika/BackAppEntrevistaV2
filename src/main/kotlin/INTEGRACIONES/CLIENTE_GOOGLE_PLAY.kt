package INTEGRACIONES

import CONFIGURACION.ConfiguracionGooglePlay
import UTILIDADES.PoliticaResiliencia
import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val ALCANCE_ANDROID_PUBLISHER = "https://www.googleapis.com/auth/androidpublisher"
private const val URL_SUSCRIPCIONES = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
private const val TIMEOUT_GOOGLE_MS = 15_000L
private const val PAGO_RECIBIDO = 1
private const val PERIODO_PRUEBA = 2
private const val COMPRA_CONFIRMADA = 1
private const val SIN_CANCELAR = 0
private val DURACION_COMPRA_SIMULADA = 30.days

data class CompraGoogle(val esValida: Boolean, val estaActiva: Boolean, val expiracion: Instant?)

interface VerificadorCompraGoogle {
    /**
     * Estado de la suscripción en Google Play. Una compra rechazada por Google es [CompraGoogle.esValida] = false;
     * si Google no responde se lanza 503 (no es lo mismo que una compra inválida).
     */
    suspend fun verificar(productoId: String, tokenCompra: String): CompraGoogle
}

/** Para desarrollo (GOOGLE_PLAY_BILLING_MOCK=true): toda compra es válida por 30 días. */
class VerificadorCompraSimulado : VerificadorCompraGoogle {
    override suspend fun verificar(productoId: String, tokenCompra: String) =
        CompraGoogle(esValida = true, estaActiva = true, expiracion = Instant.now().plusSeconds(DURACION_COMPRA_SIMULADA.inWholeSeconds))
}

@Serializable
private data class SuscripcionGoogle(
    val expiryTimeMillis: String? = null,
    val paymentState: Int? = null,
    val acknowledgementState: Int? = null,
    val cancelReason: Int? = null
)

private class FallaTemporalGoogle(estado: HttpStatusCode) : IOException("Google Play respondió ${estado.value}")

class ClienteGooglePlay(
    private val config: ConfiguracionGooglePlay,
    private val cliente: HttpClient = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_GOOGLE_MS } }
) : VerificadorCompraGoogle, AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true }
    private val politica = PoliticaResiliencia(nombre = "google_play", tiempoMaximo = 20.seconds, intentos = 3)
    private val mutexCredenciales = Mutex()

    // Se valida al arrancar: una cuenta de servicio mal configurada debe impedir levantar, no fallar en un pago.
    private val credenciales: GoogleCredentials = GoogleCredentials
        .fromStream(ByteArrayInputStream(Base64.getDecoder().decode(config.cuentaServicioJsonBase64.trim())))
        .createScoped(ALCANCE_ANDROID_PUBLISHER)

    override suspend fun verificar(productoId: String, tokenCompra: String): CompraGoogle = politica.ejecutar {
        val url = "$URL_SUSCRIPCIONES/${config.paquete}/purchases/subscriptions/" +
            "${productoId.encodeURLPathPart()}/tokens/${tokenCompra.encodeURLPathPart()}"
        val respuesta = cliente.get(url) { header(HttpHeaders.Authorization, "Bearer ${tokenDeAcceso()}") }
        when {
            respuesta.status.isSuccess() -> aCompra(json.decodeFromString<SuscripcionGoogle>(respuesta.bodyAsText()))
            respuesta.status == HttpStatusCode.TooManyRequests || respuesta.status.value >= 500 -> throw FallaTemporalGoogle(respuesta.status)
            else -> CompraGoogle(esValida = false, estaActiva = false, expiracion = null)
        }
    }

    private fun aCompra(datos: SuscripcionGoogle): CompraGoogle {
        val expiracion = datos.expiryTimeMillis?.toLongOrNull()?.let(Instant::ofEpochMilli)
        val pagoOk = datos.paymentState == null || datos.paymentState == PAGO_RECIBIDO || datos.paymentState == PERIODO_PRUEBA
        val estaActiva = pagoOk &&
            datos.acknowledgementState == COMPRA_CONFIRMADA &&
            (datos.cancelReason == null || datos.cancelReason == SIN_CANCELAR) &&
            expiracion?.isAfter(Instant.now()) == true
        return CompraGoogle(esValida = true, estaActiva = estaActiva, expiracion = expiracion)
    }

    private suspend fun tokenDeAcceso(): String = mutexCredenciales.withLock {
        withContext(Dispatchers.IO) { credenciales.refreshIfExpired() }
        credenciales.accessToken?.tokenValue ?: throw IOException("Google no entregó token de acceso")
    }

    override fun close() = cliente.close()
}
