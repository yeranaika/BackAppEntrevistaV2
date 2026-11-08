package PRUEBAS

import ERRORES.ErrorServicioExterno
import INTEGRACIONES.ClienteGooglePlay
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El token de compra viaja en la ruta de la URL de Google; si Google tarda o la conexión falla, Ktor pone esa URL en
 * el mensaje de la excepción. El error final (que se escribe en los logs) no debe llevar el token. Sin red: MockEngine.
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
}
