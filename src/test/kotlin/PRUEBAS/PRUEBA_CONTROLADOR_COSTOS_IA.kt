package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.LectorCostosIaExposed
import MODELOS.ModoEvaluacion
import MODELOS.OrigenUsoIa
import MODELOS.ROL_ADMIN
import MODELOS.ResultadoReporte
import MODELOS.TablaGeneracionPreguntaIa
import MODELOS.UsoLlm
import PRUEBAS.DOBLES.RelojAjustable
import PRUEBAS.DOBLES.SistemaPrueba
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Las 7 claves del gasto del mes frente al tope de generación (D28): el panel cuenta con todas. */
private val CLAVES_TOPE_GENERACION = setOf("mes", "gastadoUsd", "topeUsd", "restanteUsd", "fechaReposicion", "estaDesactivado", "estaAgotado")

/** GET /api/v1/admin/costos-ia: permisos, JSON y lectura real de las dos tablas (H2). */
class PruebaControladorCostosIa {
    private val sistema = SistemaPrueba()

    private fun token(correo: String, rol: String? = null, en: SistemaPrueba = sistema): String = runBlocking {
        val par = en.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = en.usuarios.buscarPorCorreo(correo)!!.id
        en.usuarios.actualizarRol(id, rol)
        en.tokens.emitirPar(id, rol).tokenAcceso
    }

    /** Un reporte de entrevista evaluado con IA, guardado por el flujo real del repositorio. */
    private fun reporteConIa(fecha: String, costo: Double, modo: ModoEvaluacion = ModoEvaluacion.IA) = runBlocking {
        val sesion = UUID.randomUUID()
        val instante = Instant.parse(fecha)
        sistema.reportesRepo.crearPendiente(sesion, instante)
        sistema.reportesRepo.tomarParaGenerar(sesion, 3, instante)
        sistema.reportesRepo.guardarResultado(
            sesion,
            ResultadoReporte(
                BigDecimal(80), BigDecimal(80), BigDecimal(80), BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), "ok",
                modo, UsoLlm("gpt-4o-mini", 1_000, 300, costo), emptyList(), emptyList(), instante
            )
        )
    }

    private fun generacion(fecha: String, costo: Double?, exitosa: Boolean, entrada: Int = 1_000, salida: Int = 500, en: SistemaPrueba = sistema, modelo: String = "gpt-4o-mini") = transaction(en.db) {
        TablaGeneracionPreguntaIa.insert {
            it[generacionId] = UUID.randomUUID()
            it[modeloLlm] = modelo
            it[promptEnviado] = "prompt"
            it[parseExitoso] = exitosa
            it[tokensInput] = entrada
            it[tokensOutput] = salida
            it[costoUsd] = costo?.let(BigDecimal::valueOf)
            it[fechaGeneracion] = OffsetDateTime.ofInstant(Instant.parse(fecha), ZoneOffset.UTC)
        }
    }

    private suspend fun ApplicationTestBuilder.pedir(token: String?, consulta: String = "?desde=2026-11-01&hasta=2026-11-30"): HttpResponse =
        client.get("/api/v1/admin/costos-ia$consulta") { token?.let { bearerAuth(it) } }

    private fun HttpResponse.json(): JsonObject = runBlocking { Json.parseToJsonElement(bodyAsText()).jsonObject }

    @Test
    fun `el lector trae los usos de las dos tablas dentro del rango`() = runBlocking<Unit> {
        reporteConIa("2026-11-02T10:00:00Z", 0.0003)
        reporteConIa("2026-12-01T00:00:00Z", 0.0003) // fuera del rango (el fin es exclusivo)
        generacion("2026-11-02T11:00:00Z", 0.0002, exitosa = true)
        generacion("2026-11-03T11:00:00Z", null, exitosa = false)

        val usos = LectorCostosIaExposed().usosEntre(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"))

        assertEquals(1, usos.count { it.origen == OrigenUsoIa.REPORTE_ENTREVISTA })
        assertEquals(2, usos.count { it.origen == OrigenUsoIa.GENERACION_PREGUNTAS })
        assertEquals(1, usos.count { !it.exitoso && it.costoUsd == null }, "la llamada fallida llega sin costo, con sus tokens")
    }

    @Test
    fun `una evaluacion de reporte que respondio mal cuenta como llamada fallida con su costo`() = runBlocking<Unit> {
        reporteConIa("2026-11-02T10:00:00Z", 0.0003, modo = ModoEvaluacion.FREEMIUM) // el LLM respondió algo inválido
        val resumen = SERVICIOS.ServicioCostosIa(LectorCostosIaExposed(), sistema.topeGeneracionIa, sistema.reloj)
            .resumen(java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-30"))
        assertEquals(0, resumen.reportes.usos)
        assertEquals(1, resumen.reportes.fallidas)
        assertEquals(0, BigDecimal("0.0003").compareTo(resumen.reportes.costoUsd), "su costo real, ya calculado al guardarlo")
    }

    @Test
    fun `un reporte sin IA no cuenta como gasto`() = runBlocking<Unit> {
        val sesion = UUID.randomUUID()
        val fecha = Instant.parse("2026-11-02T10:00:00Z")
        sistema.reportesRepo.crearPendiente(sesion, fecha)
        sistema.reportesRepo.tomarParaGenerar(sesion, 3, fecha)
        sistema.reportesRepo.guardarResultado(
            sesion,
            ResultadoReporte(BigDecimal(50), BigDecimal(50), BigDecimal(50), BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), "ok", ModoEvaluacion.FREEMIUM, null, emptyList(), emptyList(), fecha)
        )
        assertTrue(LectorCostosIaExposed().usosEntre(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")).isEmpty())
    }

    @Test
    fun `el admin ve el gasto por dia, por modelo y el total`() = testApplication {
        application { sistema.montar(this) }
        reporteConIa("2026-11-02T10:00:00Z", 0.0003)
        generacion("2026-11-02T11:00:00Z", 0.0002, exitosa = true)
        generacion("2026-11-03T11:00:00Z", null, exitosa = false)
        val admin = token("admin@ejemplo.com", ROL_ADMIN)

        val respuesta = pedir(admin)
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val cuerpo = respuesta.json()
        assertEquals("2026-11-01", cuerpo["desde"]!!.jsonPrimitive.content)
        val total = cuerpo["total"]!!.jsonObject
        assertEquals(2, total["usos"]!!.jsonPrimitive.int)
        assertEquals(1, total["fallidas"]!!.jsonPrimitive.int)
        // 0,0003 + 0,0002 + lo estimado de la fallida (1000 × 0,15 USD/M + 500 × 0,60 USD/M = 0,00045)
        assertEquals(0.00095, total["costoUsd"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(0.00045, total["costoEstimadoUsd"]!!.jsonPrimitive.double, 1e-9)
        val dias = cuerpo["porDia"]!!.jsonArray.map { it.jsonObject["fecha"]!!.jsonPrimitive.content }
        assertEquals(listOf("2026-11-02", "2026-11-03"), dias)
        assertEquals("gpt-4o-mini", cuerpo["porModelo"]!!.jsonArray.single().jsonObject["modelo"]!!.jsonPrimitive.content)
        assertEquals(0, cuerpo["reportes"]!!.jsonObject["fallidas"]!!.jsonPrimitive.int, "campos presentes aunque valgan 0")
    }

    @Test
    fun `el admin ve el gasto del mes frente al tope aunque consulte otro rango`() = testApplication {
        // El reloj del tope fija noviembre: las filas se insertan a mano, sin generación real (que usa la hora real).
        val conTope = SistemaPrueba(topeGastoGeneracionIaUsd = 0.001, relojTopeGeneracionIa = RelojAjustable(Instant.parse("2026-11-15T12:00:00Z")))
        application { conTope.montar(this) }
        generacion("2026-11-02T11:00:00Z", 0.0002, exitosa = true, en = conTope)
        generacion("2026-11-03T11:00:00Z", null, exitosa = false, en = conTope) // 1000 + 500 tokens: 0,00045 estimado
        generacion("2026-10-31T23:59:59Z", 1.0, exitosa = true, en = conTope) // octubre: fuera del mes del tope
        val admin = token("admin@ejemplo.com", ROL_ADMIN, en = conTope)

        val cuerpo = pedir(admin, "?desde=2026-10-01&hasta=2026-10-31").json()

        assertEquals(1.0, cuerpo["generacionPreguntas"]!!.jsonObject["costoUsd"]!!.jsonPrimitive.double, 1e-9, "el rango pedido es octubre")
        val tope = cuerpo["topeGeneracionPreguntas"]!!.jsonObject
        assertEquals(CLAVES_TOPE_GENERACION, tope.keys)
        assertEquals("2026-11", tope["mes"]!!.jsonPrimitive.content)
        assertEquals(0.00065, tope["gastadoUsd"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(0.001, tope["topeUsd"]!!.jsonPrimitive.double, 0.0)
        assertEquals(0.00035, tope["restanteUsd"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("2026-12-01", tope["fechaReposicion"]!!.jsonPrimitive.content)
        assertFalse(tope["estaAgotado"]!!.jsonPrimitive.boolean)
        assertFalse(tope["estaDesactivado"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `con el tope en 0 la fecha de reposicion viaja en null y estan las 7 claves`() = testApplication {
        val desactivado = SistemaPrueba(topeGastoGeneracionIaUsd = 0.0)
        application { desactivado.montar(this) }
        val admin = token("admin@ejemplo.com", ROL_ADMIN, en = desactivado)

        val tope = pedir(admin).json()["topeGeneracionPreguntas"]!!.jsonObject

        assertEquals(CLAVES_TOPE_GENERACION, tope.keys)
        assertEquals(JsonNull, tope["fechaReposicion"], "la clave viaja aunque valga null")
        assertTrue(tope["estaDesactivado"]!!.jsonPrimitive.boolean)
        assertFalse(tope["estaAgotado"]!!.jsonPrimitive.boolean)
        assertEquals(0.0, tope["restanteUsd"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `sin Token Plan de MiMo el plan viaja en null`() = testApplication {
        application { sistema.montar(this) }
        val cuerpo = pedir(token("admin@ejemplo.com", ROL_ADMIN)).json()
        assertTrue("planTokenMimo" in cuerpo.keys, "la clave viaja aunque valga null")
        assertEquals(JsonNull, cuerpo["planTokenMimo"])
    }

    @Test
    fun `con Token Plan el admin ve los Credits del periodo frente al plan`() = testApplication {
        val conPlan = SistemaPrueba(
            relojTopeGeneracionIa = RelojAjustable(Instant.parse("2026-11-19T12:00:00Z")),
            creditosTokenPlanMimo = 4_100_000_000,
            diaRenovacionTokenPlanMimo = 18
        )
        application { conPlan.montar(this) }
        generacion("2026-11-18T09:00:00Z", 0.001, exitosa = true, en = conPlan, modelo = "mimo-v2.6-pro") // 300 000 + 300 000
        generacion("2026-11-19T09:00:00Z", null, exitosa = false, en = conPlan, modelo = "mimo-v2.6-flash") // 100 000 + 100 000
        generacion("2026-11-17T23:59:59Z", 0.001, exitosa = true, en = conPlan, modelo = "mimo-v2.6-pro") // periodo anterior
        generacion("2026-11-19T10:00:00Z", 0.001, exitosa = true, en = conPlan) // gpt: no gasta Credits
        val admin = token("admin@ejemplo.com", ROL_ADMIN, en = conPlan)

        val plan = pedir(admin).json()["planTokenMimo"]!!.jsonObject

        assertEquals(setOf("desde", "hasta", "creditosPlan", "creditosUsados", "creditosRestantes", "llamadas", "estaAgotado"), plan.keys)
        assertEquals("2026-11-18", plan["desde"]!!.jsonPrimitive.content)
        assertEquals("2026-12-18", plan["hasta"]!!.jsonPrimitive.content)
        assertEquals(4_100_000_000L, plan["creditosPlan"]!!.jsonPrimitive.long)
        assertEquals(800_000L, plan["creditosUsados"]!!.jsonPrimitive.long)
        assertEquals(4_099_200_000L, plan["creditosRestantes"]!!.jsonPrimitive.long)
        assertEquals(2, plan["llamadas"]!!.jsonPrimitive.int)
        assertFalse(plan["estaAgotado"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `solo un admin puede verlo`() = testApplication {
        application { sistema.montar(this) }
        assertEquals(HttpStatusCode.Unauthorized, pedir(null).status)
        assertEquals(HttpStatusCode.Forbidden, pedir(token("ana@ejemplo.com")).status)
    }

    @Test
    fun `fechas mal escritas o un rango invalido responden 400 con su codigo`() = testApplication {
        application { sistema.montar(this) }
        val admin = token("admin@ejemplo.com", ROL_ADMIN)
        assertEquals("parametro_invalido", pedir(admin, "?desde=01-11-2026").json()["error"]!!.jsonPrimitive.content)
        assertEquals("rango_invalido", pedir(admin, "?desde=2026-11-10&hasta=2026-11-01").json()["error"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.BadRequest, pedir(admin, "?desde=2026-01-01&hasta=2026-12-31").status)
        // %2B es "+": ISO acepta años de 9 dígitos y el último día del calendario desborda al calcular el fin del rango.
        val extrema = pedir(admin, "?desde=%2B999999999-12-30&hasta=%2B999999999-12-31")
        assertEquals(HttpStatusCode.BadRequest, extrema.status, "nunca un 500")
        assertEquals("parametro_invalido", extrema.json()["error"]!!.jsonPrimitive.content)
    }
}
