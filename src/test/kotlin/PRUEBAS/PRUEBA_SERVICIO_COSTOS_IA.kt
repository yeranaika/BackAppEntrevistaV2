package PRUEBAS

import ERRORES.ErrorValidacion
import MODELOS.LectorCostosIa
import MODELOS.OrigenUsoIa
import MODELOS.UsoIaRegistrado
import PRUEBAS.DOBLES.RelojAjustable
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioCostosIa
import VISTAS.aRespuesta
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PruebaServicioCostosIa {
    /** Lector falso: devuelve los usos dentro del rango pedido y recuerda el rango. */
    private class LectorFalso(val usos: List<UsoIaRegistrado>) : LectorCostosIa {
        var rango: Pair<Instant, Instant>? = null
        override suspend fun usosEntre(desde: Instant, hasta: Instant): List<UsoIaRegistrado> {
            rango = desde to hasta
            return usos.filter { it.fecha >= desde && it.fecha < hasta }
        }
    }

    private val reloj = RelojAjustable(Instant.parse("2026-11-06T15:00:00Z"))

    private fun uso(
        fecha: String,
        origen: OrigenUsoIa = OrigenUsoIa.REPORTE_ENTREVISTA,
        modelo: String = "gpt-4o-mini",
        entrada: Int = 1_000,
        salida: Int = 300,
        costo: Double? = 0.0003,
        exitoso: Boolean = true
    ) = UsoIaRegistrado(origen, Instant.parse(fecha), modelo, entrada, salida, costo, exitoso)

    private fun servicio(vararg usos: UsoIaRegistrado) = LectorFalso(usos.toList()).let { it to ServicioCostosIa(it, reloj) }

    @Test
    fun `suma por origen, por dia en UTC y por modelo`() = runBlocking<Unit> {
        val (_, s) = servicio(
            uso("2026-11-01T10:00:00Z"),
            uso("2026-11-01T23:59:59Z", costo = 0.0007),
            uso("2026-11-02T00:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, modelo = "claude-haiku-4-5", costo = 0.002),
        )
        val resumen = s.resumen(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"))

        assertEquals(2, resumen.reportes.usos)
        assertEquals(0, BigDecimal("0.001").compareTo(resumen.reportes.costoUsd))
        assertEquals(1, resumen.generacionPreguntas.usos)
        assertEquals(0, BigDecimal("0.003").compareTo(resumen.total.costoUsd))
        assertEquals(2_000L + 1_000L, resumen.total.tokensEntrada)

        assertEquals(listOf(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02")), resumen.porDia.map { it.fecha }, "las 23:59:59 son del día 1")
        assertEquals(2, resumen.porDia[0].reportes.usos)
        assertEquals(1, resumen.porDia[1].generacionPreguntas.usos)

        assertEquals(listOf("claude-haiku-4-5", "gpt-4o-mini"), resumen.porModelo.map { it.first }, "de mayor a menor costo")
    }

    @Test
    fun `una llamada fallida se paga igual y su costo se estima con la tabla de precios`() = runBlocking<Unit> {
        val (_, s) = servicio(uso("2026-11-03T12:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, entrada = 1_000, salida = 500, costo = null, exitoso = false))
        val gasto = s.resumen(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-03")).generacionPreguntas

        assertEquals(0, gasto.usos)
        assertEquals(1, gasto.fallidas)
        // gpt-4o-mini: 1000 × 0,15 USD/M + 500 × 0,60 USD/M
        assertEquals(0, BigDecimal("0.00045").compareTo(gasto.costoEstimadoUsd))
        assertEquals(0, BigDecimal("0.00045").compareTo(gasto.costoUsd), "el estimado forma parte del costo")
    }

    @Test
    fun `sin fechas son los ultimos 30 dias hasta hoy en UTC`() = runBlocking<Unit> {
        val (lector, s) = servicio()
        val resumen = s.resumen(null, null)
        assertEquals(LocalDate.parse("2026-10-08"), resumen.desde)
        assertEquals(LocalDate.parse("2026-11-06"), resumen.hasta)
        assertEquals(Instant.parse("2026-10-08T00:00:00Z") to Instant.parse("2026-11-07T00:00:00Z"), lector.rango, "el último día se incluye completo")
    }

    @Test
    fun `rango invertido o de mas de 92 dias responde rango_invalido`() = runBlocking<Unit> {
        val (_, s) = servicio()
        fallaCon<ErrorValidacion>("rango_invalido") { s.resumen(LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-04")) }
        fallaCon<ErrorValidacion>("rango_invalido") { s.resumen(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-03")) } // 93 días
        assertEquals(LocalDate.parse("2026-04-02"), s.resumen(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-02")).hasta, "92 días sí")
    }

    @Test
    fun `la vista redondea los costos a 6 decimales y suma el total del dia`() = runBlocking<Unit> {
        val (_, s) = servicio(uso("2026-11-01T10:00:00Z", costo = 0.0000014), uso("2026-11-01T11:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, costo = 0.0000014))
        val respuesta = s.resumen(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01")).aRespuesta()
        assertEquals(0.000003, respuesta.total.costoUsd, 1e-12)
        assertEquals(0.000003, respuesta.porDia.single().costoUsd, 1e-12)
        assertEquals("2026-11-01", respuesta.desde)
    }
}
