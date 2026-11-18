package PRUEBAS

import INTEGRACIONES.IpDeLaLlamada
import INTEGRACIONES.LimitadorVentanaCompartida
import INTEGRACIONES.prefijoParaLog
import PRUEBAS.DOBLES.CacheEnMemoria
import io.ktor.server.plugins.ratelimit.RateLimiter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PruebaLimitadorVentanaCompartida {
    private var ahora = 1_000_000L
    private val cache = CacheEnMemoria { ahora }

    private fun limitador(limite: Int = 3, alFallarCache: () -> Unit = {}, alAgotarse: (Duration) -> Unit = {}) =
        LimitadorVentanaCompartida(cache, "limite:prueba:", limite, 10.minutes, alFallarCache, alAgotarse) { ahora }

    /** Consume como lo haría una solicitud desde [ip]: la IP viaja en el contexto de la corrutina. */
    private suspend fun LimitadorVentanaCompartida.desde(ip: String, tokens: Int = 1) =
        withContext(IpDeLaLlamada(ip)) { tryConsume(tokens) }

    @Test
    fun `disponible devuelve los restantes y la hora de recarga`() = runBlocking<Unit> {
        val estado = assertIs<RateLimiter.State.Available>(limitador().desde("1.2.3.4"))
        assertEquals(2, estado.remainingTokens)
        assertEquals(3, estado.limit)
        assertEquals(ahora + 10.minutes.inWholeMilliseconds, estado.refillAtTimeMillis)
        assertTrue(cache.contadores.containsKey("limite:prueba:1.2.3.4"))
    }

    @Test
    fun `un solo limitador cuenta cada IP por separado`() = runBlocking<Unit> {
        val l = limitador(limite = 1)
        assertIs<RateLimiter.State.Available>(l.desde("1.1.1.1"))
        assertIs<RateLimiter.State.Exhausted>(l.desde("1.1.1.1"))
        assertIs<RateLimiter.State.Available>(l.desde("2.2.2.2"), "otra IP, mismo limitador: su propio cupo")
    }

    @Test
    fun `sin IP en el contexto cuenta como desconocida y no deja pasar todo`() = runBlocking<Unit> {
        val l = limitador(limite = 1)
        assertIs<RateLimiter.State.Available>(l.tryConsume(1))
        assertIs<RateLimiter.State.Exhausted>(l.tryConsume(1))
        assertTrue(cache.contadores.containsKey("limite:prueba:desconocida"))
    }

    @Test
    fun `agotado redondea la espera hacia arriba y nunca informa cero`() = runBlocking<Unit> {
        val l = limitador(limite = 1)
        l.desde("1.2.3.4")
        ahora += 10.minutes.inWholeMilliseconds - 1_500
        assertEquals(2.seconds, assertIs<RateLimiter.State.Exhausted>(l.desde("1.2.3.4")).toWait, "quedan 1,5 s")
        ahora += 1_300
        assertEquals(1.seconds, assertIs<RateLimiter.State.Exhausted>(l.desde("1.2.3.4")).toWait, "quedan 200 ms")
    }

    @Test
    fun `los tokens suman la cantidad pedida`() = runBlocking<Unit> {
        assertEquals(1, assertIs<RateLimiter.State.Available>(limitador().desde("1.2.3.4", tokens = 2)).remainingTokens)
    }

    @Test
    fun `sin cache usa el respaldo en memoria por IP y avisa`() = runBlocking<Unit> {
        var avisos = 0
        val l = limitador(alFallarCache = { avisos++ })
        cache.disponible = false
        repeat(3) { assertIs<RateLimiter.State.Available>(l.desde("1.2.3.4")) }
        assertIs<RateLimiter.State.Exhausted>(l.desde("1.2.3.4"), "el respaldo también limita")
        assertIs<RateLimiter.State.Available>(l.desde("5.6.7.8"), "y cada IP tiene su cupo en el respaldo")
        assertEquals(5, avisos)
    }

    @Test
    fun `el respaldo en memoria descarta las IPs que no se usaron durante una ventana`() = runBlocking<Unit> {
        val l = limitador()
        cache.disponible = false
        l.desde("1.1.1.1")
        l.desde("2.2.2.2")
        assertEquals(2, l.ipsEnRespaldo)
        ahora += 10.minutes.inWholeMilliseconds
        l.desde("3.3.3.3")
        assertEquals(1, l.ipsEnRespaldo, "las dos anteriores ya tendrían su cupo lleno: no ocupan memoria")
    }

    @Test
    fun `solo el primer rechazo de la ventana avisa`() = runBlocking<Unit> {
        val esperas = mutableListOf<Duration>()
        val l = limitador(limite = 1, alAgotarse = { esperas += it })
        l.desde("1.2.3.4")
        repeat(3) { l.desde("1.2.3.4") }
        assertEquals(listOf(10.minutes), esperas)
    }

    @Test
    fun `el log de la cache no incluye la IP ni el correo`() {
        assertEquals("limite:registro", prefijoParaLog("limite:registro:203.0.113.7"))
        assertEquals("limite:registro", prefijoParaLog("limite:registro:2001:db8:1:2::/64"), "una IPv6 lleva ':' por dentro")
        assertEquals("login:fallos", prefijoParaLog("login:fallos:ana@ejemplo.com"))
    }
}
