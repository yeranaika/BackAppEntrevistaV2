package PRUEBAS

import CONFIGURACION.ConfiguracionLimites
import CONFIGURACION.LIMITE_RECUPERACION
import CONFIGURACION.LIMITE_REGISTRO
import CONFIGURACION.configurarErrores
import CONFIGURACION.configurarLimiteSolicitudes
import CONFIGURACION.configurarSerializacion
import PRUEBAS.DOBLES.CacheEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Límite por IP compartido entre instancias (Redis). Cada [TestApplication] hace de un servidor distinto; compartir
 * la misma [CacheEnMemoria] equivale a compartir el mismo Redis.
 */
class PruebaLimiteSolicitudes {
    private val reloj = RelojAjustable()
    private val cache = CacheEnMemoria { reloj.millis() }

    private fun instancia(limites: ConfiguracionLimites = ConfiguracionLimites(2, 1), cacheDeEsta: CacheEnMemoria = cache) = TestApplication {
        application {
            configurarSerializacion()
            configurarErrores()
            configurarLimiteSolicitudes(limites, cacheDeEsta)
            routing {
                rateLimit(LIMITE_REGISTRO) { post("/registro") { call.respond(HttpStatusCode.Created) } }
                rateLimit(LIMITE_RECUPERACION) { post("/recuperacion") { call.respond(HttpStatusCode.OK) } }
            }
        }
    }

    /** Levanta las instancias, corre [bloque] y las detiene siempre. */
    private fun conInstancias(vararg instancias: TestApplication, bloque: suspend () -> Unit) = runBlocking {
        instancias.forEach { it.start() }
        try { bloque() } finally { instancias.forEach { it.stop() } }
    }

    private suspend fun TestApplication.registrar(ipEnCabecera: String? = null): HttpResponse =
        client.post("/registro") { ipEnCabecera?.let { header("X-Real-IP", it) } }

    private suspend fun TestApplication.estado(ipEnCabecera: String? = null) = registrar(ipEnCabecera).status

    private val creado = HttpStatusCode.Created
    private val bloqueado = HttpStatusCode.TooManyRequests

    @Test
    fun `dos instancias con la misma cache comparten el cupo de registro`() {
        val a = instancia()
        val b = instancia()
        conInstancias(a, b) {
            assertEquals(creado, a.estado())
            assertEquals(creado, b.estado())
            assertEquals(bloqueado, a.estado(), "el cupo de 2 ya se usó entre las dos instancias")
            assertEquals(bloqueado, b.estado())
        }
    }

    @Test
    fun `dos instancias con caches distintas no comparten el cupo`() {
        val a = instancia()
        val b = instancia(cacheDeEsta = CacheEnMemoria { reloj.millis() })
        conInstancias(a, b) {
            assertEquals(creado, a.estado())
            assertEquals(creado, a.estado())
            assertEquals(creado, b.estado(), "control: sin caché común cada instancia cuenta por su lado")
        }
    }

    @Test
    fun `el cupo vuelve al vencer la ventana`() {
        val a = instancia()
        val b = instancia()
        conInstancias(a, b) {
            repeat(2) { a.estado() }
            reloj.adelantar(Duration.ofMinutes(10).minusMillis(1))
            assertEquals(bloqueado, b.estado(), "falta 1 ms")
            reloj.adelantar(Duration.ofMillis(1))
            assertEquals(creado, b.estado())
        }
    }

    @Test
    fun `los rechazos no alargan la ventana`() {
        val a = instancia()
        conInstancias(a) {
            repeat(2) { a.estado() }
            reloj.adelantar(Duration.ofMinutes(5))
            repeat(3) { assertEquals(bloqueado, a.estado()) }
            reloj.adelantar(Duration.ofMinutes(5))
            assertEquals(creado, a.estado(), "la ventana sigue contando desde la primera solicitud")
        }
    }

    @Test
    fun `el 429 trae Retry-After redondeado hacia arriba y el cuerpo estandar`() {
        val a = instancia()
        conInstancias(a) {
            val primera = a.registrar()
            assertEquals("2", primera.headers["X-RateLimit-Limit"])
            a.registrar()
            reloj.adelantar(Duration.ofMinutes(10).minusMillis(500))

            val respuesta = a.registrar()
            assertEquals(bloqueado, respuesta.status)
            assertEquals("1", respuesta.headers["Retry-After"], "quedan 500 ms: se informa 1 s, nunca 0")
            val cuerpo = Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
            assertEquals("demasiadas_solicitudes", cuerpo["error"]!!.jsonPrimitive.content)
            assertTrue(cuerpo["mensaje"]!!.jsonPrimitive.content.isNotBlank())
            assertTrue(cuerpo["message"]!!.jsonPrimitive.content.isNotBlank())
        }
    }

    @Test
    fun `sin cache el limite se aplica en memoria de cada instancia`() {
        val a = instancia()
        val b = instancia()
        conInstancias(a, b) {
            cache.disponible = false
            assertEquals(creado, a.estado())
            assertEquals(creado, a.estado())
            assertEquals(bloqueado, a.estado(), "sin Redis el límite no desaparece")
            assertEquals(creado, b.estado(), "cada instancia cuenta en su memoria")

            cache.disponible = true
            assertEquals(creado, b.estado())
            assertEquals(creado, a.estado())
            assertEquals(bloqueado, b.estado(), "con Redis de vuelta manda otra vez el contador compartido")
        }
    }

    @Test
    fun `la recuperacion tiene su propio cupo y su propia clave`() {
        val a = instancia()
        conInstancias(a) {
            assertEquals(HttpStatusCode.OK, a.client.post("/recuperacion").status)
            assertEquals(bloqueado, a.client.post("/recuperacion").status)
            assertEquals(creado, a.estado(), "el registro no se ve afectado")
            assertEquals(setOf("limite:recuperacion:", "limite:registro:"), cache.contadores.keys.map { it.substringBeforeLast(':') + ":" }.toSet())
        }
    }

    @Test
    fun `con cabecera confiable cada IP tiene su cupo`() {
        val a = instancia(ConfiguracionLimites(2, 1, cabeceraIpCliente = "X-Real-IP"))
        val b = instancia(ConfiguracionLimites(2, 1, cabeceraIpCliente = "X-Real-IP"))
        conInstancias(a, b) {
            assertEquals(creado, a.estado("203.0.113.7"))
            assertEquals(creado, b.estado("203.0.113.7"))
            assertEquals(bloqueado, a.estado("203.0.113.7"))
            assertEquals(creado, b.estado("203.0.113.8"), "otra IP tiene su propio cupo")
            assertTrue(cache.contadores.containsKey("limite:registro:203.0.113.7"))
        }
    }

    @Test
    fun `sin cabecera configurada la IP enviada por el cliente se ignora`() {
        val a = instancia()
        conInstancias(a) {
            assertEquals(creado, a.estado("198.51.100.1"))
            assertEquals(creado, a.estado("198.51.100.2"))
            assertEquals(bloqueado, a.estado("198.51.100.3"), "el cliente no puede cambiar de IP con una cabecera")
        }
    }

    @Test
    fun `una cabecera invalida cae a la conexion`() {
        val a = instancia(ConfiguracionLimites(2, 1, cabeceraIpCliente = "X-Real-IP"))
        conInstancias(a) {
            val invalidas = listOf("abc", "1.2.3.4, 5.6.7.8", "999.1.1.1", "x".repeat(60))
            assertEquals(creado, a.estado(invalidas[0]))
            assertEquals(creado, a.estado(invalidas[1]))
            assertEquals(bloqueado, a.estado(invalidas[2]), "todas comparten el cupo de la conexión")
            assertEquals(bloqueado, a.estado(invalidas[3]))
            assertFalse(cache.contadores.keys.any { it.contains("abc") || it.contains(",") })
        }
    }

    @Test
    fun `X-Forwarded-For no se acepta como cabecera de IP`() {
        val error = runCatching { ConfiguracionLimites(2, 1, cabeceraIpCliente = "X-Forwarded-For") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "el cliente controla el primer valor de X-Forwarded-For")
        assertTrue(runCatching { ConfiguracionLimites(2, 1, cabeceraIpCliente = "X Real IP") }.isFailure)
    }
}
