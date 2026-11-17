package PRUEBAS

import ERRORES.ErrorDemasiadosIntentos
import MODELOS.LectorUsosGeneracionIa
import MODELOS.OrigenUsoIa
import MODELOS.UsoIaRegistrado
import PRUEBAS.DOBLES.GeneracionEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import SERVICIOS.ServicioTopeGeneracionIa
import SERVICIOS.TopeGastoGeneracionIa
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gasto del mes frente al tope de la generación con IA (D23, D28): una sola cuenta para el 429 y para el panel. */
class PruebaServicioTopeGeneracionIa {
    private val usos = GeneracionEnMemoria()

    private fun uso(fecha: String, costo: Double?, exitoso: Boolean = true, tokensEntrada: Int? = null, tokensSalida: Int? = null) =
        UsoIaRegistrado(OrigenUsoIa.GENERACION_PREGUNTAS, Instant.parse(fecha), "gpt-4o-mini", tokensEntrada, tokensSalida, costo, exitoso)

    private fun servicio(usd: Double = 5.0, instante: String = "2026-11-15T12:00:00Z", lector: LectorUsosGeneracionIa = usos) =
        ServicioTopeGeneracionIa(lector, TopeGastoGeneracionIa(usd, RelojAjustable(Instant.parse(instante))))

    /** Los montos se comparan con tolerancia: el costo estimado de una fallida sale de una multiplicación en Double. */
    private fun assertUsd(esperado: String, real: BigDecimal, mensaje: String? = null) =
        assertTrue(abs(BigDecimal(esperado).toDouble() - real.toDouble()) < 1e-12, "${mensaje.orEmpty()} esperado $esperado, fue $real")

    @Test
    fun `suma los lotes y las fallidas con su costo estimado`() = runBlocking<Unit> {
        usos.usosPrevios += uso("2026-11-03T10:00:00Z", 0.10)
        // gpt-4o-mini: 1 M tokens de entrada = USD 0.15, sin costo guardado porque falló.
        usos.usosPrevios += uso("2026-11-04T10:00:00Z", costo = null, exitoso = false, tokensEntrada = 1_000_000, tokensSalida = 0)

        val estado = servicio().estado()

        assertEquals(YearMonth.of(2026, 11), estado.mes)
        assertUsd("0.25", estado.gastadoUsd)
        assertUsd("4.75", estado.restanteUsd)
        assertEquals(LocalDate.parse("2026-12-01"), estado.fechaReposicion)
        assertFalse(estado.estaAgotado)
        assertFalse(estado.estaDesactivado)
    }

    @Test
    fun `la ventana es el mes UTC en curso, cerrada al inicio y abierta al final`() = runBlocking<Unit> {
        usos.usosPrevios += uso("2026-10-31T23:59:59Z", 1.0)
        usos.usosPrevios += uso("2026-11-01T00:00:00Z", 0.5)
        usos.usosPrevios += uso("2026-11-30T23:59:59Z", 0.25)
        usos.usosPrevios += uso("2026-12-01T00:00:00Z", 2.0)

        val estado = servicio().estado()

        assertEquals(Instant.parse("2026-11-01T00:00:00Z") to Instant.parse("2026-12-01T00:00:00Z"), usos.rangosConsultados.single())
        assertUsd("0.75", estado.gastadoUsd, "solo el 1 y el 30 de noviembre")
    }

    @Test
    fun `con el gasto igual al tope esta agotado y un millonesimo menos no`() = runBlocking<Unit> {
        usos.usosPrevios += uso("2026-11-03T10:00:00Z", 5.0)
        val igual = servicio(5.0).estado()
        assertTrue(igual.estaAgotado)
        assertEquals(0, BigDecimal.ZERO.compareTo(igual.restanteUsd))

        usos.usosPrevios.clear()
        usos.usosPrevios += uso("2026-11-03T10:00:00Z", 4.999999)
        val justoDebajo = servicio(5.0).estado()
        assertFalse(justoDebajo.estaAgotado)
        assertUsd("0.000001", justoDebajo.restanteUsd)
    }

    @Test
    fun `si dos generaciones pasaron el tope lo restante es 0, nunca negativo`() = runBlocking<Unit> {
        usos.usosPrevios += uso("2026-11-03T10:00:00Z", 5.3)
        val estado = servicio(5.0).estado()
        assertTrue(estado.estaAgotado)
        assertEquals(0, BigDecimal.ZERO.compareTo(estado.restanteUsd))
        assertUsd("5.3", estado.gastadoUsd, "el gasto se informa completo")
    }

    @Test
    fun `con tope 0 esta desactivado, sin fecha de reposicion, y el gasto se calcula igual`() = runBlocking<Unit> {
        usos.usosPrevios += uso("2026-11-03T10:00:00Z", 0.4)
        val estado = servicio(0.0).estado()
        assertTrue(estado.estaDesactivado)
        assertFalse(estado.estaAgotado, "nunca los dos a la vez")
        assertEquals(0, BigDecimal.ZERO.compareTo(estado.restanteUsd))
        assertNull(estado.fechaReposicion, "desactivada no se repone sola")
        assertUsd("0.4", estado.gastadoUsd)
    }

    @Test
    fun `en diciembre se repone el 1 de enero del ano siguiente`() = runBlocking<Unit> {
        val estado = servicio(instante = "2026-12-31T23:59:59Z").estado()
        assertEquals(YearMonth.of(2026, 12), estado.mes)
        assertEquals(LocalDate.parse("2027-01-01"), estado.fechaReposicion)
    }

    @Test
    fun `verificar bloquea exactamente cuando el estado dice agotado o desactivado`() = runBlocking<Unit> {
        // (gastado, tope): bajo, en el borde, justo debajo, pasado, desactivado con y sin gasto, montos muy chicos.
        val casos = listOf(
            0.0 to 5.0, 4.999999 to 5.0, 5.0 to 5.0, 5.3 to 5.0, 0.0 to 0.0, 1.0 to 0.0, 0.0001 to 0.0001, 0.000135 to 0.0001
        )
        casos.forEach { (gastado, tope) ->
            usos.usosPrevios.clear()
            if (gastado > 0) usos.usosPrevios += uso("2026-11-03T10:00:00Z", gastado)
            val s = servicio(tope)
            val estado = s.estado()
            val bloquea = estado.estaAgotado || estado.estaDesactivado
            val error = runCatching { s.verificar() }.exceptionOrNull()

            assertFalse(estado.estaAgotado && estado.estaDesactivado, "($gastado, $tope)")
            assertEquals(bloquea, error != null, "($gastado, $tope): el panel y el 429 deben coincidir")
            if (error != null) assertEquals("tope_generacion_ia_alcanzado", (error as ErrorDemasiadosIntentos).codigo)
        }
    }

    @Test
    fun `si el gasto no se puede leer el error sube y no hay un 429 falso`() {
        val caido = servicio(lector = LectorUsosGeneracionIa { _, _ -> throw IllegalStateException("BD caída") })
        assertFailsWith<IllegalStateException> { runBlocking { caido.estado() } }
        assertFailsWith<IllegalStateException> { runBlocking { caido.verificar() } }
    }
}
