package PRUEBAS

import ERRORES.ErrorServicioExterno
import ESQUEMAS.EsquemaJsonLoteLlm
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.ProveedorPreguntasIa
import INTEGRACIONES.RespuestaProveedorIa
import INTEGRACIONES.SolicitudProveedorIa
import MODELOS.CategoriaHabilidad
import MODELOS.FeedbackGenerico
import MODELOS.NivelFeedbackGenerico
import MODELOS.OpcionSnapshot
import MODELOS.PreguntaServida
import MODELOS.ROL_ADMIN
import MODELOS.TipoPregunta
import MODELOS.feedbackGenericoDe
import MODELOS.rubricaConFeedbackGenerico
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import PRUEBAS.DOBLES.SistemaPrueba
import SERVICIOS.CorrectorRespuestas
import SERVICIOS.EvaluadorRespuestaFreemium
import SERVICIOS.ValidadorLotePreguntaIa
import SERVICIOS.nivelFeedbackGenerico
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Feedback genérico por pregunta: correcto, medio y malo (D36). */
class PruebaFeedbackGenerico {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "/api/v1/admin/preguntas"
    private val completo = LoteLlmDePrueba.FEEDBACK_DE_PRUEBA

    // ---------- Modelo ----------

    @Test
    fun `se lee de la rubrica ignorando textos vacios y formas invalidas`() {
        val rubrica = buildJsonObject {
            put("palabras_clave", JsonPrimitive("x"))
            put("feedback_generico", buildJsonObject { put("correcto", " Bien "); put("medio", "  "); put("malo", JsonPrimitive(3)) })
        }
        assertEquals(FeedbackGenerico(correcto = "Bien"), feedbackGenericoDe(rubrica))
        assertTrue(feedbackGenericoDe(null).vacio)
        assertTrue(feedbackGenericoDe(buildJsonObject { put("feedback_generico", "texto suelto") }).vacio)
    }

    @Test
    fun `combinar completa lo que falta y sobrescribir reemplaza sin perder el resto de la rubrica`() {
        val actual = FeedbackGenerico(correcto = "Propio del admin")
        assertEquals("Propio del admin", actual.combinar(completo, sobrescribir = false).correcto)
        assertEquals(completo.medio, actual.combinar(completo, sobrescribir = false).medio)
        assertEquals(completo, actual.combinar(completo, sobrescribir = true))

        val rubrica = buildJsonObject { put("metodo", "STAR") }
        val nueva = rubricaConFeedbackGenerico(rubrica, completo)
        assertEquals("STAR", nueva["metodo"]!!.jsonPrimitive.content)
        assertEquals(completo, feedbackGenericoDe(nueva))
    }

    // ---------- Generación con IA ----------

    @Test
    fun `el esquema de generar-ia exige los tres textos`() {
        val rubrica = EsquemaJsonLoteLlm.construir(1, TipoPregunta.ABIERTA_TEXTO)["properties"]!!.jsonObject["preguntas"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject["rubrica_evaluacion"]!!.jsonObject
        assertTrue(rubrica["required"]!!.jsonArray.any { it.jsonPrimitive.content == "feedback_generico" })
        val feedback = rubrica["properties"]!!.jsonObject["feedback_generico"]!!.jsonObject
        assertEquals(listOf("correcto", "medio", "malo"), feedback["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `un lote sin feedback sigue siendo valido`() {
        val lote = LoteLlmDePrueba.json(1, "abierta_texto", feedbackGenerico = { null })
        val preguntas = ValidadorLotePreguntaIa.parsearYValidar(lote, 1, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.BLANDA)
        assertNull(preguntas.single().rubrica.feedbackGenerico)
    }

    @Test
    fun `generar-ia guarda el feedback en la rubrica y descarta solo el texto demasiado largo`() = testApplication {
        val lote = LoteLlmDePrueba.json(2, "abierta_texto", feedbackGenerico = { i ->
            if (i == 1) completo else completo.copy(malo = "x".repeat(501))
        })
        val sistema = SistemaPrueba(proveedorIa = ProveedorIaGrabador(lote))
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (cargoId, _) = sistema.crearCatalogo()

        val respuesta = enviar(
            "POST", "$base/generar-ia",
            """{"cargo_id":"$cargoId","nivel":"senior","cantidad":2,"tipo":"abierta_texto","categoria":"blanda"}""", admin
        )
        assertEquals(HttpStatusCode.Created, respuesta.status)
        val ids = respuesta.objeto()["preguntas"]!!.jsonArray.map { it.jsonObject["pregunta_id"]!!.jsonPrimitive.content }
        assertEquals(completo, feedbackDe(ids[0], admin))
        assertEquals(completo.copy(malo = null), feedbackDe(ids[1], admin))
        // Una queda incompleta: el método de completar la ve como pendiente.
        assertEquals(1, enviar("GET", "$base/feedback-generico", token = admin).objeto()["pendientes"]!!.jsonPrimitive.int)
    }

    @Test
    fun `el prompt de generar-ia pide los tres mensajes`() = testApplication {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        val sistema = SistemaPrueba(proveedorIa = proveedor)
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (cargoId, _) = sistema.crearCatalogo()
        enviar("POST", "$base/generar-ia", """{"cargo_id":"$cargoId","nivel":"senior","cantidad":1,"categoria":"blanda"}""", admin)
        val prompt = proveedor.solicitudes.single().prompt
        assertTrue(prompt.contains("feedback_generico") && prompt.contains("\"medio\""), prompt)
    }

    // ---------- Validación del admin ----------

    @Test
    fun `el admin puede escribir el feedback a mano y se valida su forma`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()

        val parcial = enviar("POST", base, preguntaAbierta(skillId, """{"correcto":"Bien hecho"}"""), admin)
        assertEquals(HttpStatusCode.Created, parcial.status)

        listOf("""{"excelente":"x"}""", """{"correcto":""}""", """{"malo":"${"x".repeat(501)}"}""", "\"solo texto\"").forEach { malo ->
            val respuesta = enviar("POST", base, preguntaAbierta(skillId, malo), admin)
            assertEquals(HttpStatusCode.BadRequest, respuesta.status, malo)
            assertTrue(respuesta.bodyAsText().contains("feedback_generico_invalido"), malo)
        }
    }

    // ---------- Corrección ----------

    private val evaluador = EvaluadorRespuestaFreemium()
    private val ideal = "Un deadlock es cuando dos procesos se bloquean mutuamente porque cada uno retiene un recurso que el otro necesita."
    private val claves = listOf("deadlock", "procesos", "recurso")

    @Test
    fun `los cortes de nivel son los del feedback estandar`() {
        assertEquals(NivelFeedbackGenerico.CORRECTO, nivelFeedbackGenerico(80.0))
        assertEquals(NivelFeedbackGenerico.MEDIO, nivelFeedbackGenerico(60.0))
        assertEquals(NivelFeedbackGenerico.MALO, nivelFeedbackGenerico(59.9))
    }

    @Test
    fun `la correccion usa el texto propio del nivel y suma los conceptos que faltaron`() {
        val correcta = evaluador.evaluar(ideal, ideal, claves, feedbackGenerico = completo)
        assertEquals(completo.correcto, correcta.feedback)

        val mala = evaluador.evaluar("No lo sé bien, algo de memoria.", ideal, claves, feedbackGenerico = completo)
        assertEquals("${completo.malo} Repasa: deadlock, procesos, recurso.", mala.feedback)
    }

    @Test
    fun `sin texto propio para el nivel se usa el feedback estandar`() {
        val mala = evaluador.evaluar("No lo sé bien, algo de memoria.", ideal, claves, feedbackGenerico = FeedbackGenerico(correcto = "Bien"))
        assertTrue(mala.feedback.startsWith("Respuesta incompleta"), mala.feedback)
        assertEquals(mala.feedback, evaluador.evaluar("No lo sé bien, algo de memoria.", ideal, claves).feedback)
    }

    @Test
    fun `en opcion multiple va el texto correcto o malo antes de la explicacion`() {
        val servida = PreguntaServida(
            id = "p1", orden = 1, enunciado = "¿Qué es un deadlock?", tipo = "opcion_multiple", categoria = "tecnica", nivel = "junior",
            opciones = listOf(OpcionSnapshot("a", "Bloqueo mutuo", true, "Así es."), OpcionSnapshot("b", "Un hilo", false, null)),
            feedbackGenerico = completo
        )
        val corrector = CorrectorRespuestas(evaluador)
        assertEquals("${completo.correcto} Así es.", corrector.corregir(servida, "a", null).feedback)
        assertEquals("${completo.malo} Así es.", corrector.corregir(servida, "b", null).feedback)
        // Pruebas guardadas antes de D36 (sin feedback): igual que antes.
        assertEquals("Así es.", corrector.corregir(servida.copy(feedbackGenerico = null), "b", null).feedback)
    }

    // ---------- Completar el feedback de preguntas existentes ----------

    @Test
    fun `completa solo lo que falta, sin cambiar el estado ni el resto de la rubrica, y audita la llamada`() = testApplication {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.feedbackJson(listOf(1, 2)))
        val sistema = SistemaPrueba(proveedorIa = proveedor)
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()
        val conUno = crearAbierta(skillId, admin, """{"correcto":"Propio del admin"}""")
        val sinNada = crearAbierta(skillId, admin, null)
        crearAbierta(skillId, admin, LoteLlmDePrueba.FEEDBACK_DE_PRUEBA.aTexto()) // ya completa: no se envía

        assertEquals(2, enviar("GET", "$base/feedback-generico", token = admin).objeto()["pendientes"]!!.jsonPrimitive.int)
        val respuesta = enviar("POST", "$base/feedback-generico/completar", """{"limite":10}""", admin)
        assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
        val cuerpo = respuesta.objeto()
        assertEquals(2, cuerpo["actualizadas"]!!.jsonPrimitive.int)
        assertEquals(0, cuerpo["pendientes"]!!.jsonPrimitive.int)
        assertEquals(1, cuerpo["llamadas_ia"]!!.jsonPrimitive.int)

        assertEquals("Propio del admin", feedbackDe(conUno, admin).correcto)
        assertEquals("Texto 1 de feedback para la pregunta.", feedbackDe(conUno, admin).medio)
        assertTrue(feedbackDe(sinNada, admin).completo)
        val pregunta = enviar("GET", "$base/$conUno", token = admin).objeto()
        assertEquals("aprobada", pregunta["estado"]!!.jsonPrimitive.content)
        assertEquals("deadlock", pregunta["rubrica"]!!.jsonObject["palabras_clave"]!!.jsonArray.first().jsonPrimitive.content)

        // Una sola llamada para las dos que faltaban, con las preguntas en el prompt y la traza en Costos IA.
        assertTrue(proveedor.solicitudes.single().prompt.contains("PREGUNTA 2"))
        assertEquals(1, sistema.generaciones.usosEntre(java.time.Instant.EPOCH, java.time.Instant.now().plusSeconds(60)).size)

        // Sin pendientes, no se llama de nuevo a la IA.
        enviar("POST", "$base/feedback-generico/completar", "{}", admin)
        assertEquals(1, proveedor.solicitudes.size)
    }

    @Test
    fun `por id y con sobrescribir reemplaza los textos que ya tenia`() = testApplication {
        val sistema = SistemaPrueba(proveedorIa = ProveedorIaGrabador(LoteLlmDePrueba.feedbackJson(listOf(1))))
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()
        val id = crearAbierta(skillId, admin, LoteLlmDePrueba.FEEDBACK_DE_PRUEBA.aTexto())

        // Sin sobrescribir ya está completa: no hay nada que hacer.
        val nada = enviar("POST", "$base/feedback-generico/completar", """{"pregunta_ids":["$id"]}""", admin).objeto()
        assertEquals(0, nada["actualizadas"]!!.jsonPrimitive.int)

        val hecho = enviar("POST", "$base/feedback-generico/completar", """{"pregunta_ids":["$id"],"sobrescribir":true}""", admin).objeto()
        assertEquals(1, hecho["actualizadas"]!!.jsonPrimitive.int)
        assertEquals("Texto 1 de feedback para la pregunta.", feedbackDe(id, admin).correcto)
    }

    @Test
    fun `lo que la IA devuelve mal queda pendiente y el resto se guarda`() = testApplication {
        // Número 1 repetido (se descartan los dos) y el 2 con un texto demasiado largo: solo se guarda el 3.
        val respuestaIa = LoteLlmDePrueba.feedbackJson(listOf(1, 1, 2, 3)) { if (it == 2) "x".repeat(501) else "Bien $it." }
        val sistema = SistemaPrueba(proveedorIa = ProveedorIaGrabador(respuestaIa))
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()
        val ids = (1..3).map { crearAbierta(skillId, admin, null, "Pregunta número $it sobre deadlocks y procesos") }

        val cuerpo = enviar("POST", "$base/feedback-generico/completar", """{"limite":3}""", admin).objeto()
        assertEquals(1, cuerpo["actualizadas"]!!.jsonPrimitive.int)
        assertEquals(2, cuerpo["omitidas"]!!.jsonPrimitive.int)
        assertEquals(2, cuerpo["pendientes"]!!.jsonPrimitive.int)
        assertEquals(listOf(ids[2]), cuerpo["pregunta_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `si la IA no devuelve nada util responde 502 y deja la llamada auditada como fallida`() = testApplication {
        val sistema = SistemaPrueba(proveedorIa = ProveedorIaGrabador("""{"feedbacks":[]}"""))
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()
        crearAbierta(skillId, admin, null)

        val respuesta = enviar("POST", "$base/feedback-generico/completar", "{}", admin)
        assertEquals(HttpStatusCode.BadGateway, respuesta.status, respuesta.bodyAsText())
        val usos = sistema.generaciones.usosEntre(java.time.Instant.EPOCH, java.time.Instant.now().plusSeconds(60))
        assertEquals(listOf(false), usos.map { it.exitoso })
    }

    @Test
    fun `si falla una llamada a mitad de camino informa lo guardado y por que se detuvo`() = testApplication {
        val proveedor = ProveedorEnSecuencia(
            listOf(
                { RespuestaProveedorIa(LoteLlmDePrueba.feedbackJson((1..10).toList()), 100, 200) },
                { throw ErrorServicioExterno("provider_unavailable", "Caído") }
            )
        )
        val sistema = SistemaPrueba()
        val servicio = SERVICIOS.ServicioFeedbackGenerico(
            mapOf(INTEGRACIONES.TipoProveedorIa.OPENAI to proveedor), MODELOS.RepositorioFeedbackGenericoExposed(), sistema.topeGeneracionIa
        )
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val (_, skillId) = sistema.crearCatalogo()
        (1..12).forEach { crearAbierta(skillId, admin, null, "Pregunta $it sobre deadlocks y procesos del sistema") }

        val resultado = servicio.completar(ESQUEMAS.SolicitudCompletarFeedbackGenerico(limite = 12))
        assertEquals(10, resultado.actualizadas.size)
        assertEquals("provider_unavailable", resultado.detenidoPor)
        assertEquals(2, resultado.pendientes)
    }

    @Test
    fun `valida el limite, el modelo y que solo lo use un admin`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val usuario = runBlocking { sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1")).tokenAcceso }

        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "$base/feedback-generico/completar", "{}", usuario).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("GET", "$base/feedback-generico", token = usuario).status)
        listOf("""{"limite":0}""", """{"limite":51}""", """{"modelo":"gpt-5"}""", """{"pregunta_ids":["no-es-uuid"]}""").forEach { pedido ->
            assertEquals(HttpStatusCode.BadRequest, enviar("POST", "$base/feedback-generico/completar", pedido, admin).status, pedido)
        }
    }

    // ---------- Apoyo ----------

    private class ProveedorEnSecuencia(private val pasos: List<() -> RespuestaProveedorIa>) : ProveedorPreguntasIa {
        private var llamada = 0
        override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa = pasos[llamada++]()
    }

    private fun FeedbackGenerico.aTexto() = aJson().toString()

    private fun preguntaAbierta(skillId: java.util.UUID, feedback: String?, enunciado: String = "¿Qué es un deadlock?"): String {
        val extra = feedback?.let { ""","feedback_generico":$it""" }.orEmpty()
        return """
            {"skillId":"$skillId","tipo":"abierta_texto","categoria":"tecnica","nivel":"junior","enunciado":"$enunciado",
             "respuestaIdeal":"Es un bloqueo mutuo entre procesos que esperan recursos.",
             "rubrica":{"palabras_clave":["deadlock","procesos"]$extra}}
        """.trimIndent()
    }

    private suspend fun ApplicationTestBuilder.crearAbierta(
        skillId: java.util.UUID,
        token: String,
        feedback: String?,
        enunciado: String = "¿Qué es un deadlock? ${java.util.UUID.randomUUID()}"
    ): String {
        val respuesta = enviar("POST", base, preguntaAbierta(skillId, feedback, enunciado), token)
        assertEquals(HttpStatusCode.Created, respuesta.status, respuesta.bodyAsText())
        return respuesta.objeto()["id"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.feedbackDe(id: String, token: String): FeedbackGenerico =
        feedbackGenericoDe(enviar("GET", "$base/$id", token = token).objeto()["rubrica"] as? JsonObject)

    private fun tokenAdmin(sistema: SistemaPrueba): String = runBlocking {
        val correo = "admin@ejemplo.com"
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, ROL_ADMIN)
        sistema.tokens.emitirPar(id, ROL_ADMIN).tokenAcceso
    }

    private suspend fun ApplicationTestBuilder.enviar(metodo: String, ruta: String, cuerpo: String? = null, token: String? = null): HttpResponse =
        if (metodo == "GET") {
            client.get(ruta) { token?.let { bearerAuth(it) } }
        } else {
            client.post(ruta) {
                token?.let { bearerAuth(it) }
                cuerpo?.let { contentType(ContentType.Application.Json); setBody(it) }
            }
        }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
}
