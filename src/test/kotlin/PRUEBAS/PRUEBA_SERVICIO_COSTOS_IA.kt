package PRUEBAS

import ERRORES.ErrorValidacion
import MODELOS.LectorCostosIa
import MODELOS.OrigenUsoIa
import MODELOS.UsoIaRegistrado
import PRUEBAS.DOBLES.GeneracionEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioCostosIa
import SERVICIOS.ServicioTopeGeneracionIa
import SERVICIOS.TopeGastoGeneracionIa
import VISTAS.aRespuesta
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /** Gasto de generación que lee el tope (D28). Aparte del LectorFalso: compartirlo pisaría el rango que este recuerda. */
    private val generacionDelTope = GeneracionEnMemoria()

    private fun servicio(vararg usos: UsoIaRegistrado, topeUsd: Double = 5.0) = LectorFalso(usos.toList()).let {
        it to ServicioCostosIa(it, ServicioTopeGeneracionIa(generacionDelTope, TopeGastoGeneracionIa(topeUsd, reloj)), reloj)
    }

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
        assertTrue(generacionDelTope.rangosConsultados.isEmpty(), "un 400 no consulta el tope")
        assertEquals(LocalDate.parse("2026-04-02"), s.resumen(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-02")).hasta, "92 días sí")
    }

    // ---------- Gasto del mes frente al tope de generación (D28) ----------

    @Test
    fun `el tope de generacion es el del mes en curso aunque se consulte otro rango`() = runBlocking<Unit> {
        val octubre = uso("2026-10-15T10:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, costo = 1.0)
        generacionDelTope.usosPrevios += listOf(
            octubre,
            uso("2026-11-02T10:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, costo = 0.25)
        )
        val (_, s) = servicio(octubre)

        val resumen = s.resumen(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

        assertEquals(0, BigDecimal("1.0").compareTo(resumen.generacionPreguntas.costoUsd), "el rango pedido es octubre")
        val tope = resumen.topeGeneracionPreguntas
        assertEquals(YearMonth.of(2026, 11), tope.mes, "el reloj está en noviembre")
        assertEquals(0, BigDecimal("0.25").compareTo(tope.gastadoUsd), "solo noviembre")
        assertEquals(LocalDate.parse("2026-12-01"), tope.fechaReposicion)
        assertEquals(
            Instant.parse("2026-11-01T00:00:00Z") to Instant.parse("2026-12-01T00:00:00Z"),
            generacionDelTope.rangosConsultados.single()
        )
    }

    @Test
    fun `la vista redondea gastado y restante a 6 decimales y deja el tope como se configuro`() = runBlocking<Unit> {
        generacionDelTope.usosPrevios += uso("2026-11-02T10:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, costo = 0.0000014)
        val respuesta = servicio(topeUsd = 0.1234567).second.resumen(null, null).aRespuesta().topeGeneracionPreguntas

        assertEquals("2026-11", respuesta.mes)
        assertEquals(0.000001, respuesta.gastadoUsd, 1e-12)
        assertEquals(0.1234567, respuesta.topeUsd, 0.0, "sin redondear")
        assertEquals(0.123455, respuesta.restanteUsd, 1e-12, "0,1234553 redondeado")
        assertEquals("2026-12-01", respuesta.fechaReposicion)
        assertFalse(respuesta.estaAgotado)
        assertFalse(respuesta.estaDesactivado)
    }

    @Test
    fun `con tope 0 la vista dice desactivado, sin fecha de reposicion y sin restante`() = runBlocking<Unit> {
        generacionDelTope.usosPrevios += uso("2026-11-02T10:00:00Z", origen = OrigenUsoIa.GENERACION_PREGUNTAS, costo = 0.5)
        val respuesta = servicio(topeUsd = 0.0).second.resumen(null, null).aRespuesta().topeGeneracionPreguntas

        assertTrue(respuesta.estaDesactivado)
        assertFalse(respuesta.estaAgotado)
        assertNull(respuesta.fechaReposicion)
        assertEquals(0.0, respuesta.restanteUsd, 0.0)
        assertEquals(0.5, respuesta.gastadoUsd, 1e-12, "el gasto se informa igual")
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
