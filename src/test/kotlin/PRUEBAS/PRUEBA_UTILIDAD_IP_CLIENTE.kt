package PRUEBAS

import UTILIDADES.claveLimiteIp
import UTILIDADES.normalizarIp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaUtilidadIpCliente {

    @Test
    fun `normalizarIp acepta IPv4 e IPv6 literales`() {
        assertEquals("203.0.113.7", normalizarIp("203.0.113.7"))
        assertEquals("10.0.0.1", normalizarIp(" 10.0.0.1 "))
        assertEquals("10.0.0.1", normalizarIp("010.000.000.001"), "forma canónica: una misma IP, una sola clave")
        assertEquals("198.51.100.4", normalizarIp("::ffff:198.51.100.4"), "IPv4 mapeada en IPv6")
        assertEquals("2001:db8:0:0:0:0:0:1", normalizarIp("2001:db8::1"))
    }

    @Test
    fun `normalizarIp rechaza lo que no es una IP literal`() {
        listOf(null, "", "   ", "unknown", "localhost", "abc;DROP", "1.2.3.4, 5.6.7.8", "256.1.1.1", "1.2.3", "ejemplo.com", "1".repeat(46))
            .forEach { assertNull(normalizarIp(it), "\"$it\"") }
    }

    @Test
    fun `normalizarIp no consulta DNS con textos que empiezan por punto`() {
        // El JDK manda al resolvedor de nombres un texto que no empieza por dígito hexadecimal o ':'.
        val enviadosAlJdk = mutableListOf<String>()
        listOf(".:", ".1:2", "..::1", ".ff::").forEach { texto ->
            assertNull(normalizarIp(texto) { enviadosAlJdk += it; it }, "\"$texto\"")
        }
        assertTrue(enviadosAlJdk.isEmpty(), "nunca deben llegar a InetAddress: $enviadosAlJdk")
        assertEquals("ff::1", normalizarIp("ff::1") { enviadosAlJdk += it; it }, "un literal IPv6 sí llega")
    }

    @Test
    fun `claveLimiteIp agrupa IPv6 por su bloque 64 y deja IPv4 igual`() {
        assertEquals("2001:db8:1:2::/64", claveLimiteIp("2001:db8:1:2:aaaa::1"))
        assertEquals("2001:db8:1:2::/64", claveLimiteIp("2001:db8:1:2:bbbb::9"))
        assertEquals("203.0.113.7", claveLimiteIp("203.0.113.7"))
        assertEquals("localhost", claveLimiteIp("localhost"))
    }
}
