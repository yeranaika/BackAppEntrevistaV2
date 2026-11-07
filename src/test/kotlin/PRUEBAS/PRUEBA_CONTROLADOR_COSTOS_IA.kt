package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.LectorCostosIaExposed
import MODELOS.ModoEvaluacion
import MODELOS.OrigenUsoIa
import MODELOS.ROL_ADMIN
import MODELOS.ResultadoReporte
import MODELOS.TablaGeneracionPreguntaIa
import MODELOS.UsoLlm
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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
import kotlin.test.assertTrue

/** GET /api/v1/admin/costos-ia: permisos, JSON y lectura real de las dos tablas (H2). */
class PruebaControladorCostosIa {
    private val sistema = SistemaPrueba()

    private fun token(correo: String, rol: String? = null): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, rol)
        sistema.tokens.emitirPar(id, rol).tokenAcceso
    }

    /** Un reporte de entrevista evaluado con IA, guardado por el flujo real del repositorio. */
    private fun reporteConIa(fecha: String, costo: Double) = runBlocking {
        val sesion = UUID.randomUUID()
        val instante = Instant.parse(fecha)
        sistema.reportesRepo.crearPendiente(sesion, instante)
        sistema.reportesRepo.tomarParaGenerar(sesion, 3, instante)
        sistema.reportesRepo.guardarResultado(
            sesion,
            ResultadoReporte(
                BigDecimal(80), BigDecimal(80), BigDecimal(80), BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), "ok",
                ModoEvaluacion.IA, UsoLlm("gpt-4o-mini", 1_000, 300, costo), emptyList(), emptyList(), instante
            )
        )
    }

    private fun generacion(fecha: String, costo: Double?, exitosa: Boolean, entrada: Int = 1_000, salida: Int = 500) = transaction(sistema.db) {
        TablaGeneracionPreguntaIa.insert {
            it[generacionId] = UUID.randomUUID()
            it[modeloLlm] = "gpt-4o-mini"
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
    }
}
