package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.NivelExperiencia
import MODELOS.ROL_ADMIN
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import PRUEBAS.DOBLES.sembrarPregunta
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato HTTP de práctica, nivelación e historial: API /api/v1 y lo que usa la app Android. */
class PruebaControladorPrueba {
    private val json = Json { ignoreUnknownKeys = true }
    /** Igual que KtorClientProvider de la app. */
    private val jsonAndroid = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun tokenDe(sistema: SistemaPrueba, correo: String, rol: String? = null): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, rol)
        sistema.tokens.emitirPar(id, rol).tokenAcceso
    }

    private suspend fun ApplicationTestBuilder.enviar(metodo: String, ruta: String, cuerpo: String? = null, token: String? = null): HttpResponse {
        val configurar: HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            cuerpo?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        return when (metodo) {
            "GET" -> client.get(ruta, configurar)
            "DELETE" -> client.delete(ruta, configurar)
            else -> client.post(ruta, configurar)
        }
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    /** Banco con skill del cargo: alternativas y abiertas en los 3 niveles, más blandas. */
    private fun sistemaConBanco() = SistemaPrueba().apply {
        val (cargoId, kotlin) = crearCatalogo("Android Developer", "Kotlin")
        runBlocking { mercadoRepo.vincularSkill(cargoId, kotlin, NivelExperiencia.SEMISENIOR, 60, true) }
        NivelExperiencia.entries.forEach { nivel ->
            repeat(3) { sembrarPregunta(nivel = nivel, skillId = kotlin) }
            sembrarPregunta(nivel = nivel, skillId = kotlin, tipo = TipoPregunta.ABIERTA_TEXTO)
        }
        sembrarBancoGeneral(tecnicas = 0, blandas = 4)
    }

    private val pedidoApp = """{"usuarioId":null,"nombreUsuario":"Ana","sector":"mobile","nivel":"jr","metaCargo":"Android Developer","tipoPrueba":"%s","tipoPruebaEtiqueta":"x","tipo_prueba_etiqueta":"x"}"""

    // ---------- App Android ----------

    @Test
    fun `practica PR de la app - crear, responder con respuestaTexto y ver el resultado`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        val creada = enviar("POST", "/api/prueba-practica/front", pedidoApp.format("PR"), ana)
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())
        val prueba = jsonAndroid.decodeFromString<CrearPruebaApp>(creada.bodyAsText())
        assertEquals("PR", prueba.tipoPrueba)
        assertEquals(4, prueba.preguntas.size, "las 4 técnicas junior de la skill del cargo")
        assertTrue(prueba.preguntas.all { it.tipoBanco == "PR" && it.tipoPregunta in setOf("opcion_multiple", "abierta") })
        assertTrue(creada.bodyAsText().contains("\"opciones\"") && !creada.bodyAsText().contains("es_correcta"), "no revela la correcta")

        // Igual que PracticeTestViewModel.enviarRespuestas: abiertas con respuestaTexto, alternativas con opcionesSeleccionadas.
        val respuestas = prueba.preguntas.map { p ->
            if (p.tipoPregunta == "abierta") RespuestaApp(p.preguntaId, emptyList(), "Respuesta ideal")
            else RespuestaApp(p.preguntaId, listOf(jsonAndroid.decodeFromJsonElement<ConfigApp>(p.configRespuesta).opciones.first().id), null)
        }
        val enviada = enviar("POST", "/api/prueba-practica/${prueba.pruebaId}/respuestas", jsonAndroid.encodeToString(EnviarApp(prueba.pruebaId, respuestas)), ana)
        assertEquals(HttpStatusCode.OK, enviada.status, enviada.bodyAsText())
        val resultado = jsonAndroid.decodeFromString<ResultadoApp>(enviada.bodyAsText())
        assertEquals(4, resultado.totalPreguntas)
        assertEquals(4, resultado.respondidas)
        assertEquals(resultado.correctas, resultado.puntaje)
        assertEquals(4, resultado.detalle!!.size)
        assertEquals("nlp", resultado.feedbackMode, "la abierta se corrigió con el motor freemium")
        assertTrue(resultado.detalle.single { it.preguntaId == respuestas.first { r -> r.respuestaTexto != null }.preguntaId }.correcta, "respuesta igual a la ideal")
        assertNull(resultado.nivelDetectado)
    }

    @Test
    fun `practica BL usa preguntas blandas`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val prueba = jsonAndroid.decodeFromString<CrearPruebaApp>(enviar("POST", "/api/prueba-practica/front", pedidoApp.format("BL"), ana).bodyAsText())
        assertEquals(4, prueba.preguntas.size)
        assertTrue(prueba.preguntas.all { it.tipoBanco == "BL" })
    }

    @Test
    fun `nivelacion NV de la app - test por niveles, nivel sugerido e historial`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")

        val creada = enviar("POST", "/api/prueba-practica/front", pedidoApp.format("NV").replace("\"jr\"", "\"sr\""), ana)
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())
        val prueba = jsonAndroid.decodeFromString<CrearPruebaApp>(creada.bodyAsText())
        assertEquals("NV", prueba.tipoPrueba)
        assertEquals(9, prueba.preguntas.size, "3 por nivel, aunque la app mande un nivel")
        assertEquals(listOf("jr", "mid", "sr"), prueba.preguntas.map { it.nivel }.distinct())

        // Acierta todas las junior y semisenior, falla las senior.
        val servidas = runBlocking { sistema.nivelacionesRepo.buscarIntento(UUID.fromString(prueba.pruebaId))!!.detalle.associateBy { it.pregunta.id } }
        val respuestas = prueba.preguntas.map { p ->
            val acierta = p.nivel != "sr"
            if (p.tipoPregunta == "abierta") {
                RespuestaApp(p.preguntaId, emptyList(), if (acierta) "Respuesta ideal" else "no sé")
            } else {
                val opciones = jsonAndroid.decodeFromJsonElement<ConfigApp>(p.configRespuesta).opciones
                val correcta = servidas.getValue(p.preguntaId).pregunta.opcionCorrecta!!.id
                RespuestaApp(p.preguntaId, listOf(if (acierta) correcta else opciones.first { it.id != correcta }.id), null)
            }
        }
        val enviada = enviar("POST", "/api/prueba-practica/${prueba.pruebaId}/respuestas", jsonAndroid.encodeToString(EnviarApp(prueba.pruebaId, respuestas)), ana)
        assertEquals(HttpStatusCode.OK, enviada.status, enviada.bodyAsText())
        val resultado = jsonAndroid.decodeFromString<ResultadoApp>(enviada.bodyAsText())
        assertEquals("Semi Senior", resultado.nivelDetectado)
        assertEquals(6, resultado.puntaje)
        assertEquals(9, resultado.totalPreguntas)
        assertTrue(resultado.feedbackGeneral!!.contains("Semi Senior"))

        // El historial de la app trae el intento y la app lo filtra por tipoPrueba.
        val historial = enviar("GET", "/api/prueba-practica/intentos", token = ana)
        val intentos = runBlocking { jsonAndroid.parseToJsonElement(historial.bodyAsText()).jsonArray.map { jsonAndroid.decodeFromJsonElement<IntentoApp>(it) } }
        val nivelacion = intentos.single()
        assertEquals("nivelacion", nivelacion.tipoPrueba)
        assertEquals(prueba.pruebaId, nivelacion.pruebaId)
        assertEquals(6, nivelacion.puntaje)
        assertEquals(9, nivelacion.puntajeTotal)
        assertEquals("mid", nivelacion.nivel)
        assertEquals("finalizada", nivelacion.estado)
    }

    @Test
    fun `el historial de la app junta entrevistas, practicas y nivelaciones`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        listOf("PR", "NV").forEach { tipo -> enviar("POST", "/api/prueba-practica/front", pedidoApp.format(tipo), ana) }
        sistema.reloj.adelantar(java.time.Duration.ofMinutes(1))
        enviar("POST", "/api/prueba-practica/front", pedidoApp.format("ENT").replace("Android Developer", "Cargo libre"), ana)

        val intentos = enviar("GET", "/api/prueba-practica/intentos", token = ana).let { r ->
            runBlocking { jsonAndroid.parseToJsonElement(r.bodyAsText()).jsonArray.map { jsonAndroid.decodeFromJsonElement<IntentoApp>(it) } }
        }
        assertEquals(setOf("practica", "nivelacion", "entrevista"), intentos.map { it.tipoPrueba }.toSet())
        assertEquals("entrevista", intentos.first().tipoPrueba, "la más reciente primero")
        assertTrue(intentos.all { it.estado == "en_progreso" && it.puntaje == null })
        assertEquals(3, enviar("GET", "/api/v1/pruebas/historial", token = ana).let { runBlocking { json.parseToJsonElement(it.bodyAsText()).jsonArray.size } })
    }

    @Test
    fun `la app recibe errores claros`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        assertEquals("tipo_prueba_no_soportado", enviar("POST", "/api/prueba-practica/front", pedidoApp.format("XX"), ana).objeto().texto("error"))
        assertEquals("preguntas_insuficientes", enviar("POST", "/api/prueba-practica/front", pedidoApp.format("NV"), ana).objeto().texto("error"))
        assertEquals("prueba_no_encontrada", enviar("POST", "/api/prueba-practica/${UUID.randomUUID()}/respuestas", """{"respuestas":[]}""", ana).objeto().texto("error"))
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/api/prueba-practica/intentos").status)
    }

    // ---------- API /api/v1 ----------

    @Test
    fun `practica v1 con feedback inmediato por respuesta`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val skill = runBlocking { sistema.mercadoRepo.buscarCargoPorNombre("Android Developer")!!.let { sistema.mercadoRepo.listarRequisitos(it.id).single().skill.id } }

        val creada = enviar("POST", "/api/v1/practicas", """{"skillId":"$skill","modo":"opcion_multiple","nivel":"senior","cantidadPreguntas":2}""", ana)
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())
        val sesion = creada.objeto()
        val id = sesion.texto("sesionId")!!
        val pregunta = sesion["preguntas"]!!.jsonArray[0].jsonObject
        assertNull(pregunta["correccion"])

        val correccion = enviar("POST", "/api/v1/practicas/$id/respuestas",
            """{"preguntaId":"${pregunta.texto("preguntaId")}","opcionId":"${pregunta["opciones"]!!.jsonArray[0].jsonObject.texto("opcionId")}","tiempoRespuestaMs":1500}""", ana).objeto()
        assertNotNull(correccion.texto("correcta"))
        assertNotNull(correccion.texto("opcionCorrectaId"), "tras responder sí muestra la correcta")

        val finalizada = enviar("POST", "/api/v1/practicas/$id/finalizar", token = ana).objeto()
        assertEquals("finalizada", finalizada.texto("estado"))
        assertEquals("1", finalizada.texto("respondidas"))
        assertEquals(1, enviar("GET", "/api/v1/me/niveles-skill", token = ana).let { runBlocking { json.parseToJsonElement(it.bodyAsText()).jsonArray.size } })
        assertEquals("practica_no_activa", enviar("POST", "/api/v1/practicas/$id/finalizar", token = ana).objeto().texto("error"))
    }

    @Test
    fun `nivelacion v1 oculta la correccion hasta terminar y entrega brechas`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        assertEquals(HttpStatusCode.NoContent, enviar("GET", "/api/v1/nivelacion/resultado", token = ana).status)

        val creada = enviar("POST", "/api/v1/nivelacion", """{"cargo":"Android Developer"}""", ana)
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())
        val intento = creada.objeto()
        val id = intento.texto("intentoId")!!
        assertTrue(!creada.bodyAsText().contains("opcionCorrectaId") && intento["resultado"] == null)

        // La selección es aleatoria: se elige una de alternativas (las abiertas no traen "opciones").
        val primera = intento["preguntas"]!!.jsonArray.map { it.jsonObject }.first { it.texto("tipo") == "opcion_multiple" }
        val cuerpo = """{"respuestas":[{"preguntaId":"${primera.texto("preguntaId")}","opcionId":"${primera["opciones"]!!.jsonArray[0].jsonObject.texto("opcionId")}"}]}"""
        val respondida = enviar("POST", "/api/v1/nivelacion/$id/respuestas", cuerpo, ana).objeto()
        assertEquals("finalizada", respondida.texto("estado"))
        val resultado = respondida["resultado"]!!.jsonObject
        assertEquals("junior", resultado.texto("nivel"))
        assertEquals("Kotlin", resultado["brechas"]!!.jsonArray.single().jsonObject.texto("nombre"))
        assertEquals(id, enviar("GET", "/api/v1/nivelacion/resultado", token = ana).objeto().texto("intentoId"))
        assertEquals("nivelacion_finalizada", enviar("POST", "/api/v1/nivelacion/$id/respuestas", cuerpo, ana).objeto().texto("error"))
    }

    @Test
    fun `tests de nivelacion solo para admin`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val ids = runBlocking { sistema.preguntas.listar(MODELOS.FiltroPreguntas(tipo = TipoPregunta.OPCION_MULTIPLE), 1, 3).elementos.map { "\"${it.id}\"" } }
        val cuerpo = """{"titulo":"Nivelación Android","area":"mobile","preguntasIds":[${ids.joinToString(",")}]}"""

        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/v1/admin/tests-nivelacion", cuerpo).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/api/v1/admin/tests-nivelacion", cuerpo, ana).status)
        val creado = enviar("POST", "/api/v1/admin/tests-nivelacion", cuerpo, admin)
        assertEquals(HttpStatusCode.Created, creado.status, creado.bodyAsText())
        val testId = creado.objeto().texto("testId")
        assertEquals(1, enviar("GET", "/api/v1/admin/tests-nivelacion?activo=true", token = admin).let { runBlocking { json.parseToJsonElement(it.bodyAsText()).jsonArray.size } })
        assertEquals(HttpStatusCode.OK, enviar("DELETE", "/api/v1/admin/tests-nivelacion/$testId", token = admin).status)
        assertEquals("false", enviar("GET", "/api/v1/admin/tests-nivelacion/$testId", token = admin).objeto().texto("activo"))
        assertEquals("id_invalido", enviar("GET", "/api/v1/admin/tests-nivelacion?cargoId=abc", token = admin).objeto().texto("error"))
    }

    @Test
    fun `sincronizacion offline y evaluacion freemium con el mismo JSON de antes, ahora con sesion`() = testApplication {
        val sistema = sistemaConBanco()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val skill = runBlocking { sistema.mercadoRepo.listarSkillsActivas().first { it.nombre == "Kotlin" }.id }
        val lote = """{"attempts":[{"localAttemptId":"loc-1","skillId":"$skill","modo":"abierta_texto","categoria":"tecnica","nivelPreguntas":"junior",
            "puntajeTotal":7.5,"fechaCreacionIso":"2025-12-01T10:00:00Z","respuestas":[{"preguntaId":"offline-1","enunciado":"¿Qué es Kotlin?","respuestaTexto":"Un lenguaje","esCorrecta":true,"puntaje":7.5,"orden":1}]}]}"""

        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/v1/sync/attempts", lote).status)
        val sincronizado = enviar("POST", "/api/v1/sync/attempts", lote, ana).objeto()
        assertEquals("true", sincronizado.texto("success"))
        val idServidor = sincronizado["mappings"]!!.jsonArray.single().jsonObject.texto("serverAttemptId")
        assertEquals(idServidor, enviar("POST", "/api/v1/sync/attempts", lote, ana).objeto()["mappings"]!!.jsonArray.single().jsonObject.texto("serverAttemptId"))
        assertEquals("finalizada", enviar("GET", "/api/v1/practicas/$idServidor", token = ana).objeto().texto("estado"))

        val evaluacion = """{"userText":"Un deadlock bloquea procesos","idealText":"Un deadlock bloquea procesos que esperan recursos","expectedKeywords":["deadlock","recursos"]}"""
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/v1/practice/evaluate-freemium", evaluacion).status)
        val evaluada = enviar("POST", "/api/v1/practice/evaluate-freemium", evaluacion, ana).objeto()
        assertEquals(listOf("recursos"), evaluada["missingKeywords"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}

// Copias de los DTO de la app Android (data/remote/tests y testsNivelacion) con sus mismos valores por defecto.

@Serializable
private data class PreguntaApp(
    val preguntaId: String,
    val texto: String,
    val tipoBanco: String,
    val sector: String,
    val nivel: String,
    val tipoPregunta: String,
    val pistas: JsonElement? = null,
    val configRespuesta: JsonElement,
    val orden: Int
)

@Serializable
private data class CrearPruebaApp(
    @SerialName("pruebaId") val pruebaId: String = "",
    @SerialName("tipoPrueba") val tipoPrueba: String? = null,
    val area: String? = null,
    val nivel: String? = null,
    val metadata: Map<String, String>? = null,
    val preguntas: List<PreguntaApp> = emptyList()
)

@Serializable
private data class OpcionPracticaApp(val id: String, val texto: String)

@Serializable
private data class ConfigApp(
    val opciones: List<OpcionPracticaApp> = emptyList(),
    @SerialName("min_caracteres") val minCaracteres: Int? = null,
    @SerialName("max_caracteres") val maxCaracteres: Int? = null,
    val tipo: String? = null,
    @SerialName("respuesta_correcta") val respuestaCorrecta: String? = null
)

@Serializable
private data class RespuestaApp(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String> = emptyList(),
    val respuestaTexto: String? = null
)

@Serializable
private data class EnviarApp(val pruebaId: String, val respuestas: List<RespuestaApp>)

@Serializable
private data class DetalleApp(
    val preguntaId: String,
    val correcta: Boolean,
    val claveCorrecta: String? = null,
    val seleccionadas: List<String> = emptyList()
)

@Serializable
private data class ResultadoApp(
    val ok: Boolean? = null,
    val puntaje: Int? = null,
    val totalPreguntas: Int? = null,
    val nivelDetectado: String? = null,
    val respondidas: Int? = null,
    val correctas: Int? = null,
    val detalle: List<DetalleApp>? = null,
    val feedbackGeneral: String? = null,
    val feedbackMode: String? = null
)

@Serializable
private data class IntentoApp(
    val intentoId: String,
    val pruebaId: String,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val puntaje: Int? = null,
    val puntajeTotal: Int = 0,
    val nivel: String? = null,
    val metaCargo: String? = null,
    val tipoPrueba: String? = null,
    val estado: String? = null
)
