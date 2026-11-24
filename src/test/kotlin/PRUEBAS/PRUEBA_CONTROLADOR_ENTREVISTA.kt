package PRUEBAS

import CONFIGURACION.VERSION_MODELO_DIVLARA
import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.TablaMetricaVideo
import MODELOS.TablaReporteEntrevista
import MODELOS.TablaSesionEntrevista
import MODELOS.TablaSesionPreguntaRespuesta
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato HTTP de /api/v1/entrevistas y del adaptador /api/prueba-practica que usa Android. */
class PruebaControladorEntrevista {
    private val json = Json { ignoreUnknownKeys = true }
    /** Igual que KtorClientProvider de la app: ignoreUnknownKeys + isLenient, nada más. */
    private val jsonAndroid = Json { ignoreUnknownKeys = true; isLenient = true }
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

    private fun sistemaConBanco() = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 10, blandas = 6) }

    // ---------- API por sesión ----------

    @Test
    fun `todas las rutas exigen sesion`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", base, "{}").status)
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "$base/actual").status)
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/prueba-practica/front", "{}").status)
    }

    @Test
    fun `flujo completo pregunta a pregunta`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        assertEquals(HttpStatusCode.NoContent, enviar("GET", "$base/actual", token = ana).status)
        val creada = enviar("POST", base, """{"cargo":"Backend Developer","nivel":"jr","cantidadPreguntas":3}""", ana)
        assertEquals(HttpStatusCode.Created, creada.status)
        val sesion = creada.objeto()
        val id = sesion.texto("sesionId")!!
        assertEquals("junior", sesion.texto("nivel"))
        assertEquals("3", sesion.texto("totalPreguntas"))
        assertFalse(creada.bodyAsText().contains("esCorrecta") || creada.bodyAsText().contains("correccion"), "no revela la respuesta correcta")
        assertEquals(id, enviar("GET", "$base/actual", token = ana).objeto().texto("sesionId"))
        assertEquals("entrevista_en_progreso", enviar("POST", base, """{"cargo":"QA"}""", ana).objeto().texto("error"))

        repeat(3) {
            val pregunta = enviar("GET", "$base/$id/siguiente", token = ana).objeto()
            val cuerpo = if (pregunta.texto("tipo") == "opcion_multiple") {
                """{"preguntaSesionId":"${pregunta.texto("preguntaSesionId")}","opcionId":"${pregunta["opciones"]!!.jsonArray[0].jsonObject.texto("opcionId")}"}"""
            } else {
                // videoClipUrl se sigue aceptando en el JSON (clientes antiguos) pero se ignora y no se guarda (D37).
                """{"preguntaSesionId":"${pregunta.texto("preguntaSesionId")}","texto":"Mi respuesta","videoClipUrl":"https://storage.ejemplo.com/clip.mp4"}"""
            }
            val respondida = enviar("POST", "$base/$id/respuestas", cuerpo, ana)
            assertEquals(HttpStatusCode.OK, respondida.status, respondida.bodyAsText())
            assertEquals("true", respondida.objeto().texto("respondida"))
            assertFalse(respondida.bodyAsText().contains("storage.ejemplo.com"), "el clip no se guarda")
        }
        assertEquals(HttpStatusCode.NoContent, enviar("GET", "$base/$id/siguiente", token = ana).status)

        val metricas = """{"metricas":[{"timestampMs":0,"contactoVisual":80.5,"postura":70,"confianza":65,"expresion":"Seguro"},
                           {"timestampMs":500,"gestos":{"toca_cara":true},"expresion":"neutral","versionModelo":"falso-cliente-v99"}]}""" // expresion y versionModelo del cliente se ignoran (D22, RNF08)
        val lote = enviar("POST", "$base/$id/metricas", metricas, ana)
        assertEquals(HttpStatusCode.Created, lote.status)
        val cuerpoLote = lote.objeto()
        assertEquals("2", cuerpoLote.texto("insertadas"))
        assertEquals(VERSION_MODELO_DIVLARA, cuerpoLote.texto("versionModelo"))
        val versionesGuardadas = transaction {
            TablaMetricaVideo.selectAll().orderBy(TablaMetricaVideo.timestampMs).map { it[TablaMetricaVideo.versionModelo] }
        }
        assertEquals(listOf(VERSION_MODELO_DIVLARA, VERSION_MODELO_DIVLARA), versionesGuardadas)

        val finalizada = enviar("POST", "$base/$id/finalizar", token = ana).objeto()
        assertEquals("finalizada", finalizada.texto("estado"))
        val preguntas = finalizada["preguntas"]!!.jsonArray.map { it.jsonObject }
        assertTrue(preguntas.all { it["correccion"] != null }, "al terminar sí muestra la corrección")
        assertEquals(1, enviar("GET", base, token = ana).objeto()["elementos"]!!.jsonArray.size)
    }

    @Test
    fun `errores con codigo y la entrevista ajena responde 404`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val beto = tokenDe(sistema, "beto@ejemplo.com")
        val id = enviar("POST", base, """{"cargo":"QA"}""", ana).objeto().texto("sesionId")!!

        assertEquals(HttpStatusCode.NotFound, enviar("GET", "$base/$id", token = beto).status)
        assertEquals("entrevista_no_encontrada", enviar("POST", "$base/$id/cancelar", token = beto).objeto().texto("error"))
        assertEquals(HttpStatusCode.BadRequest, enviar("GET", "$base/no-es-uuid", token = ana).status)
        assertEquals("pregunta_sesion_id_invalido", enviar("POST", "$base/$id/respuestas", """{"preguntaSesionId":"x","texto":"a"}""", ana).objeto().texto("error"))
        assertEquals("sin_respuestas", enviar("POST", "$base/$id/finalizar", token = ana).objeto().texto("error"))
        assertEquals("metrica_fuera_de_rango", enviar("POST", "$base/$id/metricas", """{"metricas":[{"timestampMs":1,"postura":150}]}""", ana).objeto().texto("error"))
        assertEquals("cancelada", enviar("POST", "$base/$id/cancelar", token = ana).objeto().texto("estado"))
        assertEquals(HttpStatusCode.Conflict, enviar("POST", "$base/$id/cancelar", token = ana).status)
        assertEquals("cantidad_invalida", enviar("POST", base, """{"cargo":"QA","cantidadPreguntas":50}""", ana).objeto().texto("error"))
    }

    @Test
    fun `borrar elimina clips, transcripciones y metricas pero conserva la sesion y el costo del reporte`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val beto = tokenDe(sistema, "beto@ejemplo.com")
        val id = enviar("POST", base, """{"cargo":"Backend Developer","nivel":"jr","cantidadPreguntas":3}""", ana).objeto().texto("sesionId")!!

        repeat(3) {
            val pregunta = enviar("GET", "$base/$id/siguiente", token = ana).objeto()
            val cuerpo = if (pregunta.texto("tipo") == "opcion_multiple") {
                """{"preguntaSesionId":"${pregunta.texto("preguntaSesionId")}","opcionId":"${pregunta["opciones"]!!.jsonArray[0].jsonObject.texto("opcionId")}"}"""
            } else {
                """{"preguntaSesionId":"${pregunta.texto("preguntaSesionId")}","texto":"Mi respuesta","videoClipUrl":"https://almacen.ejemplo.com/clip.mp4"}"""
            }
            assertEquals(HttpStatusCode.OK, enviar("POST", "$base/$id/respuestas", cuerpo, ana).status)
        }
        enviar("POST", "$base/$id/metricas", """{"metricas":[{"timestampMs":0,"postura":70},{"timestampMs":500,"postura":60}]}""", ana)
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "$base/$id/borrar").status)
        assertEquals("entrevista_en_curso", enviar("POST", "$base/$id/borrar", token = ana).objeto().texto("error"))

        enviar("POST", "$base/$id/finalizar", token = ana)
        val sesionId = java.util.UUID.fromString(id)
        transaction {
            TablaReporteEntrevista.insert {
                it[reporteId] = java.util.UUID.randomUUID(); it[TablaReporteEntrevista.sesionId] = sesionId
                it[resumen] = "Habló de su empleo anterior"; it[fortalezas] = listOf("Buena estructura")
                it[modeloLlm] = "modelo-x"; it[costoUsd] = java.math.BigDecimal("0.012300"); it[fechaGeneracion] = java.time.Instant.now()
            }
        }

        assertEquals("entrevista_no_encontrada", enviar("POST", "$base/$id/borrar", token = beto).objeto().texto("error"))
        val borrado = enviar("POST", "$base/$id/borrar", token = ana)
        assertEquals(HttpStatusCode.OK, borrado.status, borrado.bodyAsText())
        val cuerpo = borrado.objeto()
        assertNotNull(cuerpo.texto("mensaje"))
        assertEquals("2", cuerpo.texto("metricasBorradas"))
        assertTrue((cuerpo.texto("clipsBorrados")!!.toInt()) >= 0 && cuerpo.texto("transcripcionesBorradas") != null)

        transaction {
            val respuestas = TablaSesionPreguntaRespuesta.selectAll().where { TablaSesionPreguntaRespuesta.sesionId eq sesionId }.toList()
            assertEquals(3, respuestas.size, "las preguntas y sus puntajes se conservan")
            assertTrue(respuestas.all { it[TablaSesionPreguntaRespuesta.videoClipUrl] == null && it[TablaSesionPreguntaRespuesta.transcripcion] == null })
            assertEquals(0, TablaMetricaVideo.selectAll().where { TablaMetricaVideo.sesionId eq sesionId }.count().toInt())
            assertEquals(1, TablaSesionEntrevista.selectAll().where { TablaSesionEntrevista.sesionId eq sesionId }.count().toInt())
            val reporte = TablaReporteEntrevista.selectAll().where { TablaReporteEntrevista.sesionId eq sesionId }.single()
            assertNull(reporte[TablaReporteEntrevista.resumen])
            assertTrue(reporte[TablaReporteEntrevista.fortalezas].isEmpty())
            assertEquals(java.math.BigDecimal("0.012300"), reporte[TablaReporteEntrevista.costoUsd], "el costo de IA sigue contando para el tope")
        }

        val repetido = enviar("POST", "$base/$id/borrar", token = ana)
        assertEquals(HttpStatusCode.OK, repetido.status, "repetirlo es inocuo")
        assertEquals("0", repetido.objeto().texto("metricasBorradas"))
    }

    // ---------- Contrato de la app Android ----------

    @Test
    fun `la app Android crea y rinde la entrevista con su JSON de siempre`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        // Exactamente lo que envía EntrevistaTestViewModel.cargarPruebaEntrevista
        val pedido = """{"usuarioId":null,"nombreUsuario":"Ana","sector":"backend","nivel":"jr","metaCargo":"Backend Developer",
            "tipoPrueba":"ENT","tipoPruebaEtiqueta":"blended","tipo_prueba_etiqueta":"blended"}"""
        val creada = enviar("POST", "/api/prueba-practica/front", pedido, ana)
        assertTrue(creada.status.value in 200..299, creada.bodyAsText())
        val prueba = jsonAndroid.decodeFromString<CrearPruebaEntrevistaResApp>(creada.bodyAsText())
        assertEquals(8, prueba.preguntas.size)
        assertEquals("jr", prueba.nivel)
        assertEquals("backend", prueba.area)
        assertEquals("Backend Developer", prueba.metadata!!["metaCargo"])

        val multiples = prueba.preguntas.filter { it.tipoPregunta == "opcion_multiple" }
        val abiertas = prueba.preguntas.filter { it.tipoPregunta == "abierta" }
        assertEquals(prueba.preguntas.size, multiples.size + abiertas.size, "la app solo dibuja opcion_multiple y abierta")
        val config = jsonAndroid.decodeFromJsonElement<ConfigRespuestaApp>(multiples.first().configRespuesta)
        assertEquals(3, config.opciones!!.size)
        assertNotNull(jsonAndroid.decodeFromJsonElement<ConfigRespuestaApp>(abiertas.first().configRespuesta).max_caracteres)
        assertTrue(abiertas.all { it.tipoBanco == "BL" } && multiples.all { it.tipoBanco == "PR" })

        // Responde la primera opción de cada alternativa, una abierta y deja otra en blanco (la app las envía todas).
        val respuestas = prueba.preguntas.map { pregunta ->
            when {
                pregunta.tipoPregunta == "opcion_multiple" -> {
                    val opciones = jsonAndroid.decodeFromJsonElement<ConfigRespuestaApp>(pregunta.configRespuesta).opciones!!
                    RespuestaPreguntaApp(pregunta.preguntaId, opcionesSeleccionadas = listOf(opciones.first().id))
                }
                pregunta == abiertas.first() -> RespuestaPreguntaApp(pregunta.preguntaId, respuestaAbierta = "Situación, tarea, acción y resultado")
                else -> RespuestaPreguntaApp(pregunta.preguntaId, opcionesSeleccionadas = null, respuestaAbierta = "")
            }
        }
        val cuerpo = jsonAndroid.encodeToString(EnviarRespuestasApp(prueba.pruebaId, respuestas))
        val enviada = enviar("POST", "/api/prueba-practica/${prueba.pruebaId}/respuestas", cuerpo, ana)
        assertEquals(HttpStatusCode.OK, enviada.status, enviada.bodyAsText())

        val resultado = jsonAndroid.decodeFromString<EnviarRespuestasResApp>(enviada.bodyAsText())
        assertTrue(resultado.ok != false)
        assertEquals(multiples.size, resultado.totalPreguntas)
        assertEquals(multiples.size + 1, resultado.respondidas)
        assertEquals(resultado.correctas, resultado.puntaje)
        assertEquals(multiples.size, resultado.detalle!!.size)
        assertTrue(resultado.detalle!!.all { it.claveCorrecta != null && it.seleccionadas.size == 1 })
        assertNull(resultado.feedbackMode, "no se informa revisión con IA ni NLP: las abiertas van al reporte")
        assertTrue(resultado.feedbackGeneral!!.contains("1 respuestas abiertas"))

        // La sesión quedó finalizada y visible en la API nueva.
        assertEquals("finalizada", enviar("GET", "$base/${prueba.pruebaId}", token = ana).objeto().texto("estado"))
        assertEquals("entrevista_no_activa", enviar("POST", "/api/prueba-practica/${prueba.pruebaId}/respuestas", cuerpo, ana).objeto().texto("error"))
    }

    @Test
    fun `una entrevista abandonada en la app se reemplaza al crear otra`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val pedido = """{"sector":"backend","nivel":"junior","metaCargo":"QA","tipoPrueba":"ENT"}"""

        val primera = enviar("POST", "/api/prueba-practica/front", pedido, ana).objeto().texto("pruebaId")
        val segunda = enviar("POST", "/api/prueba-practica/front", pedido, ana)
        assertEquals(HttpStatusCode.Created, segunda.status)
        assertEquals("cancelada", enviar("GET", "$base/$primera", token = ana).objeto().texto("estado"))
    }

    @Test
    fun `si la app pide una entrevista sin banco la que tenia en curso sigue viva`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        val primera = enviar("POST", "/api/prueba-practica/front", """{"sector":"backend","nivel":"junior","metaCargo":"QA","tipoPrueba":"ENT"}""", ana)
            .objeto().texto("pruebaId")
        val sinBanco = enviar("POST", "/api/prueba-practica/front", """{"sector":"backend","nivel":"senior","metaCargo":"QA","tipoPrueba":"ENT"}""", ana)
        assertEquals(HttpStatusCode.Conflict, sinBanco.status)
        assertEquals("preguntas_insuficientes", sinBanco.objeto().texto("error"))
        assertEquals("en_progreso", enviar("GET", "$base/$primera", token = ana).objeto().texto("estado"))
    }

    @Test
    fun `la app recibe errores claros para tipos y cargos no disponibles`() = testApplication {
        val sistema = SistemaPrueba().apply { repeat(2) { sembrarBancoGeneral(tecnicas = 1, blandas = 0) } }
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        val practica = enviar("POST", "/api/prueba-practica/front", """{"sector":"x","nivel":"jr","metaCargo":"QA","tipoPrueba":"XX"}""", ana)
        assertEquals(HttpStatusCode.BadRequest, practica.status)
        assertEquals("tipo_prueba_no_soportado", practica.objeto().texto("error"))
        val sinBanco = enviar("POST", "/api/prueba-practica/front", """{"sector":"x","nivel":"jr","metaCargo":"QA","tipoPrueba":"ENT"}""", ana)
        assertEquals(HttpStatusCode.Conflict, sinBanco.status)
        assertEquals("preguntas_insuficientes", sinBanco.objeto().texto("error"))
        assertTrue(sinBanco.objeto().texto("message")!!.isNotBlank(), "la app muestra el mensaje")
    }
}

// Copias de los DTO de la app Android (data/remote/testsEntrevista) con sus mismos valores por defecto.

@Serializable
private data class PreguntaEntrevistaApp(
    val preguntaId: String,
    val texto: String,
    val tipoBanco: String,
    val sector: String,
    val nivel: String,
    val tipoPregunta: String,
    val pistas: JsonElement? = null,
    val configRespuesta: JsonElement,
    val configEvaluacion: JsonElement? = null,
    val orden: Int
)

@Serializable
private data class CrearPruebaEntrevistaResApp(
    val pruebaId: String,
    val tipoPrueba: String,
    val area: String?,
    val nivel: String?,
    val metadata: Map<String, String>?,
    val preguntas: List<PreguntaEntrevistaApp>
)

@Serializable
private data class OpcionApp(val id: String, val texto: String)

@Suppress("PropertyName")
@Serializable
private data class ConfigRespuestaApp(
    val opciones: List<OpcionApp>? = null,
    val min_caracteres: Int? = null,
    val max_caracteres: Int? = null,
    val formato: String? = null,
    val tipo: String? = null
)

@Serializable
private data class RespuestaPreguntaApp(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String>? = null,
    val respuestaAbierta: String? = null
)

@Serializable
private data class EnviarRespuestasApp(val pruebaId: String, val respuestas: List<RespuestaPreguntaApp>)

@Serializable
private data class ResultadoPreguntaApp(
    val preguntaId: String,
    val correcta: Boolean,
    val claveCorrecta: String? = null,
    val seleccionadas: List<String> = emptyList()
)

@Serializable
private data class EnviarRespuestasResApp(
    val ok: Boolean? = null,
    val puntaje: Int? = null,
    val totalPreguntas: Int? = null,
    val nivelDetectado: String? = null,
    val respondidas: Int? = null,
    val correctas: Int? = null,
    val detalle: List<ResultadoPreguntaApp>? = null,
    val feedbackGeneral: String? = null,
    val feedbackMode: String? = null,
    @SerialName("iaRevisionesRestantes") val iaRevisionesRestantes: Int? = null
)
