package PRUEBAS

import ERRORES.ErrorRespuestaExterna
import ERRORES.ErrorServicioExterno
import PRUEBAS.DOBLES.fallaCon
import UTILIDADES.PoliticaResiliencia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PruebaUtilidadResiliencia {
    private var ahora = 0L
    private val esperas = mutableListOf<Duration>()

    private fun politica(intentos: Int = 3, fallosParaAbrir: Int = 2, tiempoMaximo: Duration = 1.seconds) = PoliticaResiliencia(
        nombre = "prueba",
        tiempoMaximo = tiempoMaximo,
        intentos = intentos,
        esperaInicial = 100.milliseconds,
        fallosParaAbrir = fallosParaAbrir,
        tiempoAbierto = 30.seconds,
        ahoraMs = { ahora },
        esperar = { esperas += it }
    )

    @Test
    fun `reintenta una falla transitoria con espera creciente y devuelve el resultado`() = runBlocking<Unit> {
        var llamadas = 0
        val resultado = politica().ejecutar {
            llamadas++
            if (llamadas < 3) throw IOException("red caída")
            "ok"
        }
        assertEquals("ok", resultado)
        assertEquals(3, llamadas)
        assertEquals(2, esperas.size)
        assertTrue(esperas[1] > esperas[0], "la espera debe crecer: $esperas")
    }

    @Test
    fun `no reintenta errores que no son transitorios`() {
        var llamadas = 0
        assertFailsWith<IllegalArgumentException> {
            runBlocking { politica().ejecutar { llamadas++; throw IllegalArgumentException("dato malo") } }
        }
        assertEquals(1, llamadas)
    }

    @Test
    fun `al agotar intentos responde 503 con el nombre del servicio`() {
        fallaCon<ErrorServicioExterno>("prueba_no_disponible") { politica().ejecutar { throw IOException("caído") } }
    }

    @Test
    fun `si el error ya es de dominio se conserva su codigo`() {
        val p = politica()
        fallaCon<ErrorRespuestaExterna>("provider_http_error") {
            p.ejecutar(esReintentable = { true }) { throw ErrorRespuestaExterna("provider_http_error") }
        }
    }

    @Test
    fun `el cortocircuito se abre tras fallas seguidas y deja de llamar hasta que pasa el tiempo`() {
        val p = politica(intentos = 1, fallosParaAbrir = 2)
        repeat(2) { fallaCon<ErrorServicioExterno>("prueba_no_disponible") { p.ejecutar { throw IOException() } } }
        assertTrue(p.estaAbierto)

        var llamadas = 0
        fallaCon<ErrorServicioExterno>("prueba_no_disponible") { p.ejecutar { llamadas++ } }
        assertEquals(0, llamadas, "con el circuito abierto no se llama al servicio")

        ahora += 31_000
        assertFalse(p.estaAbierto)
        assertEquals("ok", runBlocking { p.ejecutar { "ok" } })
    }

    @Test
    fun `un intento que tarda demasiado cuenta como falla transitoria`() {
        var llamadas = 0
        fallaCon<ErrorServicioExterno>("prueba_no_disponible") {
            politica(intentos = 2, tiempoMaximo = 50.milliseconds).ejecutar { llamadas++; delay(1_000) }
        }
        assertEquals(2, llamadas)
    }

    @Test
    fun `una cancelacion real no se reintenta`() {
        var llamadas = 0
        assertFailsWith<CancellationException> {
            runBlocking { politica().ejecutar(esReintentable = { true }) { llamadas++; throw CancellationException("cliente se fue") } }
        }
        assertEquals(1, llamadas)
    }
}
