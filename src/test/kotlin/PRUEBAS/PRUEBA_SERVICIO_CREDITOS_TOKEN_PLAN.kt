package PRUEBAS

import CONFIGURACION.leerCreditosTokenPlan
import ERRORES.ErrorDemasiadosIntentos
import INTEGRACIONES.CREDITOS_TOKEN_PLAN_LITE
import INTEGRACIONES.creditosTokenPlanMimo
import MODELOS.LectorCostosIa
import MODELOS.OrigenUsoIa
import MODELOS.UsoIaRegistrado
import PRUEBAS.DOBLES.GeneracionEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import SERVICIOS.PlanTokenMimo
import SERVICIOS.ServicioCreditosTokenPlan
import SERVICIOS.ServicioTopeGeneracionIa
import SERVICIOS.TopeGastoGeneracionIa
import SERVICIOS.inicioPeriodoPlan
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Credits del Token Plan de MiMo (D31): lo que el panel muestra y lo que frena generar-ia con un modelo MiMo. */
class PruebaServicioCreditosTokenPlan {

    private class LectorFalso(var usos: List<UsoIaRegistrado> = emptyList()) : LectorCostosIa {
        val rangos = mutableListOf<Pair<Instant, Instant>>()
        override suspend fun usosEntre(desde: Instant, hasta: Instant): List<UsoIaRegistrado> {
            rangos += desde to hasta
            return usos.filter { !it.fecha.isBefore(desde) && it.fecha.isBefore(hasta) }
        }
    }

    private fun uso(fecha: String, modelo: String, entrada: Int?, salida: Int?, exitoso: Boolean = true, origen: OrigenUsoIa = OrigenUsoIa.GENERACION_PREGUNTAS) =
        UsoIaRegistrado(origen, Instant.parse(fecha), modelo, entrada, salida, costoUsd = 0.001, exitoso = exitoso)

    private fun servicio(lector: LectorCostosIa, creditos: Long = 1_000_000, dia: Int = 1, hoy: String = "2026-11-19T12:00:00Z") =
        ServicioCreditosTokenPlan(lector, PlanTokenMimo(creditos, dia, RelojAjustable(Instant.parse(hoy))))

    @Test
    fun `los Credits salen de la tabla oficial por token, sin descontar cache`() {
        assertEquals(1_000L * 100 + 500L * 200, creditosTokenPlanMimo("mimo-v2.6-flash", 1_000, 500))
        assertEquals(1_000L * 300 + 500L * 600, creditosTokenPlanMimo("mimo-v2.6-pro", 1_000, 500))
        assertEquals(1_000L * 300 + 500L * 600, creditosTokenPlanMimo("mimo-v9-desconocido", 1_000, 500), "un mimo-* sin tarifa cuenta como pro")
        assertEquals(0L, creditosTokenPlanMimo("gpt-4o-mini", 1_000, 500), "otros proveedores no gastan Credits")
        assertEquals(0L, creditosTokenPlanMimo("mimo-v2.6-pro", null, null))
    }

    @Test
    fun `suma solo MiMo del periodo, de reportes y generacion, tambien las fallidas`() = runBlocking<Unit> {
        val lector = LectorFalso(
            listOf(
                uso("2026-11-02T10:00:00Z", "mimo-v2.6-pro", 1_000, 500),
                uso("2026-11-03T10:00:00Z", "mimo-v2.6-flash", 2_000, 1_000, exitoso = false),
                uso("2026-11-04T10:00:00Z", "mimo-v2.6-flash", 100, 100, origen = OrigenUsoIa.REPORTE_ENTREVISTA),
                uso("2026-11-05T10:00:00Z", "gpt-4o-mini", 9_999, 9_999),
                uso("2026-10-31T23:59:59Z", "mimo-v2.6-pro", 1_000, 1_000) // periodo anterior
            )
        )

        val estado = servicio(lector).estado()!!

        // pro 300 000 + 300 000; flash fallida 200 000 + 200 000; reporte flash 10 000 + 20 000
        assertEquals(1_030_000L, estado.creditosUsados)
        assertEquals(3, estado.llamadas)
        assertEquals(LocalDate.parse("2026-11-01"), estado.desde)
        assertEquals(LocalDate.parse("2026-12-01"), estado.hasta)
        assertEquals(0L, estado.creditosRestantes, "nunca negativo")
        assertTrue(estado.estaAgotado)
    }

    @Test
    fun `el periodo va del dia de renovacion al mismo dia del mes siguiente`() = runBlocking<Unit> {
        assertEquals(LocalDate.parse("2026-11-18"), inicioPeriodoPlan(LocalDate.parse("2026-11-19"), 18))
        assertEquals(LocalDate.parse("2026-11-19"), inicioPeriodoPlan(LocalDate.parse("2026-11-19"), 19), "el día de la renovación ya es periodo nuevo")
        assertEquals(LocalDate.parse("2026-10-20"), inicioPeriodoPlan(LocalDate.parse("2026-11-19"), 20))
        assertEquals(LocalDate.parse("2026-12-28"), inicioPeriodoPlan(LocalDate.parse("2027-01-05"), 28), "cruza el año")
        assertEquals(LocalDate.parse("2026-11-28"), inicioPeriodoPlan(LocalDate.parse("2026-11-30"), 31), "más de 28 se toma como 28")

        val lector = LectorFalso()
        val estado = servicio(lector, dia = 18).estado()!!
        assertEquals(LocalDate.parse("2026-11-18") to LocalDate.parse("2026-12-18"), estado.desde to estado.hasta)
        assertEquals(Instant.parse("2026-11-18T00:00:00Z") to Instant.parse("2026-12-18T00:00:00Z"), lector.rangos.single())
    }

    @Test
    fun `sin Token Plan no hay estado ni se consulta la base`() = runBlocking<Unit> {
        val lector = LectorFalso()
        val sinPlan = servicio(lector, creditos = 0)
        assertFalse(sinPlan.activo)
        assertNull(sinPlan.estado())
        sinPlan.verificar()
        assertTrue(lector.rangos.isEmpty())
    }

    @Test
    fun `con Credits justo en el plan esta agotado y uno menos no`() = runBlocking<Unit> {
        val lector = LectorFalso(listOf(uso("2026-11-02T10:00:00Z", "mimo-v2.6-flash", 10, 0))) // 1 000 Credits
        assertTrue(servicio(lector, creditos = 1_000).estado()!!.estaAgotado)
        assertFalse(servicio(lector, creditos = 1_001).estado()!!.estaAgotado)
        assertEquals(1L, servicio(lector, creditos = 1_001).estado()!!.creditosRestantes)
    }

    @Test
    fun `con Token Plan un modelo MiMo se frena por Credits y no por USD, los demas siguen con USD`() = runBlocking<Unit> {
        val reloj = RelojAjustable(Instant.parse("2026-11-19T12:00:00Z"))
        // USD gastado muy por encima del tope de 5: no debe frenar a MiMo con Token Plan.
        val generaciones = GeneracionEnMemoria().apply {
            usosPrevios += UsoIaRegistrado(OrigenUsoIa.GENERACION_PREGUNTAS, Instant.parse("2026-11-02T10:00:00Z"), "mimo-v2.6-flash", 10, 0, 50.0, true)
        }
        val lector = LectorFalso(generaciones.usosPrevios.toList())
        fun tope(creditos: Long) = ServicioTopeGeneracionIa(
            generaciones, TopeGastoGeneracionIa(5.0, reloj), ServicioCreditosTokenPlan(lector, PlanTokenMimo(creditos, 1, reloj))
        )

        tope(creditos = 2_000).verificar("mimo-v2.6-flash") // 1 000 de 2 000 Credits: pasa
        val porCredits = assertFailsWith<ErrorDemasiadosIntentos> { tope(creditos = 1_000).verificar("mimo-v2.6-pro") }
        assertEquals("tope_generacion_ia_alcanzado", porCredits.codigo)
        assertTrue(porCredits.message!!.contains("Token Plan"), porCredits.message)
        val porUsd = assertFailsWith<ErrorDemasiadosIntentos> { tope(creditos = 2_000).verificar("gpt-4o-mini") }
        assertTrue(porUsd.message!!.contains("USD"), "otro proveedor sigue con el tope en USD: ${porUsd.message}")
        assertFailsWith<ErrorDemasiadosIntentos>("sin Token Plan, MiMo vuelve al tope en USD") { tope(creditos = 0).verificar("mimo-v2.6-flash") }
    }

    @Test
    fun `MIMO_TOKEN_PLAN_CREDITOS acepta separadores y sin definir depende de la clave`() {
        assertEquals(CREDITOS_TOKEN_PLAN_LITE, leerCreditosTokenPlan(null, "tp-abc"), "clave de Token Plan: plan Lite")
        assertEquals(CREDITOS_TOKEN_PLAN_LITE, leerCreditosTokenPlan("  ", " tp-abc"))
        assertEquals(0L, leerCreditosTokenPlan(null, "sk-abc"), "clave de pago por uso: sin Token Plan")
        assertEquals(0L, leerCreditosTokenPlan(null, ""))
        assertEquals(11_000_000_000L, leerCreditosTokenPlan("11.000.000.000", "tp-abc"))
        assertEquals(11_000_000_000L, leerCreditosTokenPlan("11,000,000,000", "sk-abc"))
        assertEquals(38_000_000_000L, leerCreditosTokenPlan("38_000_000_000", ""))
        assertEquals(0L, leerCreditosTokenPlan("0", "tp-abc"), "0 apaga el Token Plan aunque la clave sea tp-")
        listOf("mucho", "-5", "4.1e9").forEach { assertEquals(0L, leerCreditosTokenPlan(it, "tp-abc"), it) }
    }
}
