package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.NivelExperiencia
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarPregunta
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato HTTP del reporte de entrevista, historial de reportes y progreso por skill. */
class PruebaControladorFeedback {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "/api/v1/entrevistas"

    private fun tokenDe(sistema: SistemaPrueba, correo: String): String =
        runBlocking { sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1")).tokenAcceso }

    private suspend fun ApplicationTestBuilder.enviar(metodo: String, ruta: String, cuerpo: String? = null, token: String? = null): HttpResponse {
        val configurar: HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            cuerpo?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        return if (metodo == "GET") client.get(ruta, configurar) else client.post(ruta, configurar)
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    private fun sistemaConBanco() = SistemaPrueba().apply {
        val (cargoId, kotlin) = crearCatalogo("Android Developer", "Kotlin")
        runBlocking { mercadoRepo.vincularSkill(cargoId, kotlin, NivelExperiencia.JUNIOR, 60, true) }
        repeat(5) { sembrarPregunta(skillId = kotlin) }
        repeat(3) { sembrarPregunta(CategoriaHabilidad.BLANDA) }
    }

    /** Rinde una entrevista completa por la API y devuelve su id. */
    private suspend fun ApplicationTestBuilder.rendir(token: String): String {
        val id = enviar("POST", base, """{"cargo":"Android Developer","nivel":"jr"}""", token).objeto().texto("sesionId")!!
        while (true) {
            val r = enviar("GET", "$base/$id/siguiente", token = token)
            if (r.status == HttpStatusCode.NoContent) break
            val p = r.objeto()
            val cuerpo = if (p.texto("tipo") == "opcion_multiple") {
                """{"preguntaSesionId":"${p.texto("preguntaSesionId")}","opcionId":"${p["opciones"]!!.jsonArray[0].jsonObject.texto("opcionId")}"}"""
            } else {
                """{"preguntaSesionId":"${p.texto("preguntaSesionId")}","texto":"Respuesta ideal"}"""
            }
            enviar("POST", "$base/$id/respuestas", cuerpo, token)
        }
        enviar("POST", "$base/$id/finalizar", token = token)
        return id
    }

    @Test
    fun `reporte generando y luego listo con radar y feedback por pregunta, sin datos internos`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val id = rendir(ana)

        val generando = enviar("GET", "$base/$id/reporte", token = ana).objeto()
        assertEquals("generando", generando.texto("estado"))
        assertNull(generando["puntajeGlobal"])

        runBlocking { sistema.reporte.procesar(sistema.sesionesEntrevista.buscar(UUID.fromString(id))!!) }

        val respuesta = enviar("GET", "$base/$id/reporte", token = ana)
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val reporte = respuesta.objeto()
        assertEquals("listo", reporte.texto("estado"))
        assertEquals("freemium", reporte.texto("modoEvaluacion"))
        assertTrue(reporte.texto("puntajeGlobal")!!.toDouble() in 0.0..100.0)
        assertEquals(setOf("Kotlin", "Habilidades blandas"), reporte["skills"]!!.jsonArray.map { it.jsonObject.texto("nombre") }.toSet())
        val preguntas = reporte["preguntas"]!!.jsonArray.map { it.jsonObject }
        assertEquals(8, preguntas.size)
        assertTrue(preguntas.filter { it.texto("tipo") == "abierta_texto" }.all { it["observacion"] != null && it.texto("puntaje") != null })
        assertTrue(preguntas.filter { it.texto("tipo") == "opcion_multiple" }.all { it["esCorrecta"] != null && it["respuestaIdeal"] != null })
        val texto = respuesta.bodyAsText()
        assertFalse(texto.contains("costo") || texto.contains("tokens") || texto.contains("modelo"), "el costo y el modelo son internos")
        assertEquals("false", reporte.texto("puedeReintentar"))
        assertEquals("reporte_no_reintentable", enviar("POST", "$base/$id/reporte/reintentar", token = ana).objeto().texto("error"))
    }

    @Test
    fun `un reporte con error muestra un mensaje claro y nunca el codigo interno`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val id = rendir(ana)
        runBlocking {
            sistema.reportesRepo.crearPendiente(UUID.fromString(id), java.time.Instant.now())
            sistema.reportesRepo.tomarParaGenerar(UUID.fromString(id), 3)
            sistema.reportesRepo.marcarError(UUID.fromString(id), "error_interno", java.time.Instant.now())
        }

        val respuesta = enviar("GET", "$base/$id/reporte", token = ana)
        val reporte = respuesta.objeto()
        assertEquals("error", reporte.texto("estado"))
        assertTrue(reporte.texto("error")!!.isNotBlank())
        assertFalse(respuesta.bodyAsText().contains("error_interno"))
        assertEquals("true", reporte.texto("puedeReintentar"))
    }

    @Test
    fun `historial de reportes y progreso por skill`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val id = rendir(ana)
        runBlocking { sistema.reporte.procesar(sistema.sesionesEntrevista.buscar(UUID.fromString(id))!!) }

        val reportes = runBlocking { json.parseToJsonElement(enviar("GET", "/api/v1/reportes", token = ana).bodyAsText()).jsonArray }
        assertEquals(id, reportes.single().jsonObject.texto("sesionId"))
        assertEquals("listo", reportes.single().jsonObject.texto("estado"))

        val progreso = runBlocking { json.parseToJsonElement(enviar("GET", "/api/v1/me/progreso", token = ana).bodyAsText()).jsonArray }
        val kotlin = progreso.single().jsonObject
        assertEquals("Kotlin", kotlin.texto("nombre"))
        assertEquals(1, kotlin["historial"]!!.jsonArray.size)
    }

    @Test
    fun `permisos y estados`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val beto = tokenDe(sistema, "beto@ejemplo.com")
        val enCurso = enviar("POST", base, """{"cargo":"Android Developer","nivel":"jr"}""", ana).objeto().texto("sesionId")

        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "$base/$enCurso/reporte").status)
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/api/v1/me/progreso").status)
        assertEquals("entrevista_no_finalizada", enviar("GET", "$base/$enCurso/reporte", token = ana).objeto().texto("error"))
        assertEquals(HttpStatusCode.NotFound, enviar("GET", "$base/$enCurso/reporte", token = beto).status)
        assertEquals(HttpStatusCode.BadRequest, enviar("GET", "$base/abc/reporte", token = ana).status)
        enviar("POST", "$base/$enCurso/cancelar", token = ana)
        assertEquals("reporte_no_disponible", enviar("GET", "$base/$enCurso/reporte", token = ana).objeto().texto("error"))
    }
}
