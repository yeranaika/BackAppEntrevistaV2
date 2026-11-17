package PRUEBAS

import ERRORES.ErrorServicioExterno
import INTEGRACIONES.ClienteGooglePlay
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El token de compra viaja en la ruta de la URL de Google; si Google tarda o la conexión falla, Ktor pone esa URL en
 * el mensaje de la excepción. El error final (que se escribe en los logs) no debe llevar el token. También cómo se lee
 * una respuesta de Google, incluida la cuenta que pagó (D27). Sin red: MockEngine.
 */
class PruebaClienteGooglePlay {
    private val tokenCompra = "tokenCompraSecreto123"
    private val urlsPedidas = CopyOnWriteArrayList<String>()

    /** Cliente cuyo motor siempre lanza la excepción que arma [falla] con la petición recibida. */
    private fun clienteQueFalla(falla: (HttpRequestData) -> Throwable) = ClienteGooglePlay(
        paquete = "com.ejemplo.entrevistas",
        cliente = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    urlsPedidas.add(request.url.toString())
                    throw falla(request)
                }
            }
        },
        tokenDeAcceso = { "token-acceso-prueba" }
    )

    /** Cliente cuyo motor siempre responde [cuerpo] con [estado], como lo haría Google. */
    private fun clienteQueResponde(cuerpo: String, estado: HttpStatusCode = HttpStatusCode.OK) = ClienteGooglePlay(
        paquete = "com.ejemplo.entrevistas",
        cliente = HttpClient(MockEngine) {
            engine { addHandler { respond(cuerpo, estado, headersOf(HttpHeaders.ContentType, "application/json")) } }
        },
        tokenDeAcceso = { "token-acceso-prueba" }
    )

    /** Una suscripción pagada, confirmada y vigente; [extra] agrega campos al JSON de Google. */
    private fun suscripcionActiva(extra: String = "") =
        """{"expiryTimeMillis":"${Instant.now().plusSeconds(86_400).toEpochMilli()}","paymentState":1,"acknowledgementState":1$extra}"""

    private fun verificar(cliente: ClienteGooglePlay) = runBlocking { cliente.verificar("premium_mensual", tokenCompra) }

    private fun verificarFallando(cliente: ClienteGooglePlay): ErrorServicioExterno =
        assertFailsWith<ErrorServicioExterno> { runBlocking { cliente.verificar("premium_mensual", tokenCompra) } }

    /** stackTraceToString incluye mensajes de toda la cadena de causas y suprimidas: es lo que termina en el log. */
    private fun assertSinToken(error: ErrorServicioExterno) {
        assertEquals("google_play_no_disponible", error.codigo)
        assertFalse(error.stackTraceToString().contains(tokenCompra), "El token de compra quedó en el error final")
        generateSequence<Throwable>(error) { it.cause }.forEach { assertFalse(it.message.orEmpty().contains(tokenCompra)) }
    }

    /** Asegura que la prueba no es trivial: el token sí iba en la URL y la falla se reintentó como transitoria. */
    private fun assertReintentoConToken() {
        assertEquals(3, urlsPedidas.size)
        assertTrue(urlsPedidas.all { it.contains(tokenCompra) })
    }

    @Test
    fun `timeout de Google no deja el token de compra en el error`() {
        val error = verificarFallando(clienteQueFalla { HttpRequestTimeoutException(it.url.toString(), 15_000L) })
        assertSinToken(error)
        assertReintentoConToken()
    }

    @Test
    fun `falla de conexion con Google no deja el token de compra en el error`() {
        val error = verificarFallando(clienteQueFalla { ConnectTimeoutException("Connect timeout has expired [url=${it.url}]") })
        assertSinToken(error)
        assertReintentoConToken()
    }

    // ---------- Respuesta de Google y cuenta que pagó (D27) ----------

    @Test
    fun `lee obfuscatedExternalAccountId de la respuesta de Google`() {
        val compra = verificar(clienteQueResponde(suscripcionActiva(""","obfuscatedExternalAccountId":" abc ","emailAddress":"x@ejemplo.com"""")))
        assertTrue(compra.esValida)
        assertTrue(compra.estaActiva)
        assertEquals("abc", compra.cuentaOfuscada, "sin espacios alrededor")
    }

    @Test
    fun `sin obfuscatedExternalAccountId o vacio la cuenta queda null`() {
        assertNull(verificar(clienteQueResponde(suscripcionActiva())).cuentaOfuscada, "compra desde la tienda o app antigua")
        assertNull(verificar(clienteQueResponde(suscripcionActiva(""","obfuscatedExternalAccountId":"  """"))).cuentaOfuscada)
        assertTrue(verificar(clienteQueResponde(suscripcionActiva())).estaActiva, "sin la cuenta la compra se lee igual")
    }

    @Test
    fun `un 404 de Google es una compra no valida`() {
        val compra = verificar(clienteQueResponde("""{"error":{"code":404}}""", HttpStatusCode.NotFound))
        assertFalse(compra.esValida)
        assertFalse(compra.estaActiva)
        assertNull(compra.cuentaOfuscada)
    }
}
