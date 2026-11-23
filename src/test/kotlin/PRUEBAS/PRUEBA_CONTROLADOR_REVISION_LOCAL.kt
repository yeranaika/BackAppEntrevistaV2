package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.SistemaPrueba
import SERVICIOS.RevisorTextoLibre
import SERVICIOS.RubricaRevision
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato HTTP de la revisión de texto libre sin conexión (T3.13): descarga de preguntas y subida de respuestas. */
class PruebaControladorRevisionLocal {
    private val json = Json { ignoreUnknownKeys = true }
    private val ideal = "El patrón cache-aside consulta primero Redis y, si falla, lee de PostgreSQL y guarda el resultado con un TTL."
    /** Toda pregunta nace con una skill (el banco la exige): cada prueba crea su catálogo. */
    private var skillActual: UUID = UUID.randomUUID()
    private val rubrica = """{"palabras_clave":["Redis","cache-aside","TTL","PostgreSQL"],"criterios":["menciona_invalidacion"]}"""

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
        return if (metodo == "GET") client.get(ruta, configurar) else client.post(ruta, configurar)
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    /** Crea una pregunta aprobada (el admin la crea ya aprobada) y devuelve su id. */
    private suspend fun ApplicationTestBuilder.crearPregunta(
        admin: String, tipo: String = "abierta_texto", respuestaIdeal: String? = ideal, rubricaJson: String? = rubrica, enunciado: String = "¿Cómo cacheas?"
    ): String {
        val ideal = respuestaIdeal?.let { ""","respuestaIdeal":${JsonPrimitive(it)}""" }.orEmpty()
        val rub = rubricaJson?.let { ""","rubrica":$it""" }.orEmpty()
        val opciones = if (tipo == "opcion_multiple") {
            ""","opciones":[{"texto":"a","esCorrecta":true},{"texto":"b"}]"""
        } else ""
        val cuerpo = """{"skillId":"$skillActual","tipo":"$tipo","categoria":"tecnica","nivel":"junior","enunciado":"$enunciado"$ideal$rub$opciones}"""
        val respuesta = enviar("POST", "/api/v1/admin/preguntas", cuerpo, admin)
        assertEquals(HttpStatusCode.Created, respuesta.status, respuesta.objeto().toString())
        return respuesta.objeto().texto("id")!!
    }

    private fun lote(vararg respuestas: String) = """{"respuestas":[${respuestas.joinToString(",")}]}"""
    private fun respuesta(idLocal: String, preguntaId: String, texto: String, extra: String = "") =
        """{"idLocal":"$idLocal","preguntaId":"$preguntaId","texto":${JsonPrimitive(texto)}$extra}"""

    @Test
    fun `sin sesion responde 401`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/api/v1/revision-local/preguntas").status)
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/v1/revision-local/respuestas", lote()).status)
    }

    @Test
    fun `la descarga trae solo preguntas abiertas aprobadas con su respuesta ideal y su rubrica`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val abierta = crearPregunta(admin)
        crearPregunta(admin, tipo = "opcion_multiple", respuestaIdeal = null, rubricaJson = null, enunciado = "¿Cuál?")
        val pendiente = crearPregunta(admin, enunciado = "¿Pendiente?")
        // Un PUT la deja pendiente; aquí basta con no aprobar otra: se edita vía repositorio.
        runBlocking { sistema.preguntas.cambiarEstado(UUID.fromString(pendiente), MODELOS.EstadoPregunta.PENDIENTE, null, null) }

        val respuesta = enviar("GET", "/api/v1/revision-local/preguntas?cantidad=10", token = usuario)
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val cuerpo = respuesta.objeto()
        assertEquals(1, cuerpo["versionRevisor"]!!.jsonPrimitive.content.toInt())
        val preguntas = cuerpo["preguntas"]!!.jsonArray
        assertEquals(listOf(abierta), preguntas.map { it.jsonObject.texto("id") })
        val p = preguntas.single().jsonObject
        assertEquals("abierta_texto", p.texto("tipo"))
        assertEquals(ideal, p.texto("respuestaIdeal"))
        assertEquals("TTL", p["rubrica"]!!.jsonObject["palabras_clave"]!!.jsonArray[2].jsonPrimitive.content)
    }

    @Test
    fun `la descarga valida la cantidad, el nivel y la categoria`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        assertEquals("cantidad_invalida", enviar("GET", "/api/v1/revision-local/preguntas?cantidad=0", token = usuario).objeto().texto("error"))
        assertEquals("cantidad_invalida", enviar("GET", "/api/v1/revision-local/preguntas?cantidad=500", token = usuario).objeto().texto("error"))
        assertEquals(HttpStatusCode.BadRequest, enviar("GET", "/api/v1/revision-local/preguntas?nivel=experto-galactico", token = usuario).status)
        assertEquals(HttpStatusCode.OK, enviar("GET", "/api/v1/revision-local/preguntas?nivel=junior&categoria=tecnica", token = usuario).status)
    }

    @Test
    fun `el servidor repite la revision con la misma formula y el reenvio no duplica`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val preguntaId = crearPregunta(admin)
        val texto = "Uso cache-aside con Redis: si no está, leo PostgreSQL y guardo con TTL."
        val directo = RevisorTextoLibre.revisar(texto, ideal, RubricaRevision.desdeJson(json.parseToJsonElement(rubrica).jsonObject))

        val cuerpo = lote(respuesta("local-1", preguntaId, texto, ""","scoreLocal":${directo.score},"bandaLocal":"${directo.banda}","revisadoEnMs":1700000000000"""))
        val primera = enviar("POST", "/api/v1/revision-local/respuestas", cuerpo, usuario)
        assertEquals(HttpStatusCode.OK, primera.status)
        val resultado = primera.objeto()
        assertEquals("1", resultado.texto("recibidas"))
        val fila = resultado["resultados"]!!.jsonArray.single().jsonObject
        assertEquals("guardada", fila.texto("estado"))
        assertEquals(directo.score, fila["scoreServidor"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(directo.banda.name, fila.texto("bandaServidor"))
        assertEquals("true", fila.texto("coincide"))

        // Reenvío del mismo idLocal (la app no supo que llegó): no duplica y devuelve lo mismo.
        val segunda = enviar("POST", "/api/v1/revision-local/respuestas", cuerpo, usuario).objeto()
        assertEquals("0", segunda.texto("recibidas"))
        assertEquals("ya_recibida", segunda["resultados"]!!.jsonArray.single().jsonObject.texto("estado"))
        val guardada = runBlocking { sistema.revisionesTexto.buscar(sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id, "local-1") }!!
        assertEquals(texto, guardada.texto)
        assertEquals(directo.score, guardada.scoreServidor, 1e-9)
    }

    @Test
    fun `si el puntaje del telefono no coincide con el del servidor lo avisa y vale el del servidor`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val preguntaId = crearPregunta(admin)
        val fila = enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("x", preguntaId, "Redis y TTL", ""","scoreLocal":99.0""")), usuario)
            .objeto()["resultados"]!!.jsonArray.single().jsonObject
        assertEquals("false", fila.texto("coincide"))
        assertTrue(fila["scoreServidor"]!!.jsonPrimitive.double < 99.0)
    }

    @Test
    fun `cada usuario tiene su propio idLocal y una pregunta que ya no existe se rechaza sin reintento`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val beto = tokenDe(sistema, "beto@ejemplo.com")
        val preguntaId = crearPregunta(admin)

        assertEquals("guardada", enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("mismo", preguntaId, "Redis")), ana)
            .objeto()["resultados"]!!.jsonArray.single().jsonObject.texto("estado"))
        assertEquals("guardada", enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("mismo", preguntaId, "Redis")), beto)
            .objeto()["resultados"]!!.jsonArray.single().jsonObject.texto("estado"))

        val inexistente = enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("otra", UUID.randomUUID().toString(), "Redis")), ana)
        assertEquals(HttpStatusCode.OK, inexistente.status)
        val fila = inexistente.objeto()["resultados"]!!.jsonArray.single().jsonObject
        assertEquals("rechazada", fila.texto("estado"))
        assertNull(fila["scoreServidor"]!!.takeIf { it !is JsonNull })
        assertFalse(fila.texto("motivo").isNullOrBlank())
    }

    @Test
    fun `una pregunta de opcion multiple no se puede subir como texto libre`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val multiple = crearPregunta(admin, tipo = "opcion_multiple", respuestaIdeal = null, rubricaJson = null)
        val fila = enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("m", multiple, "a")), usuario)
            .objeto()["resultados"]!!.jsonArray.single().jsonObject
        assertEquals("rechazada", fila.texto("estado"))
    }

    @Test
    fun `lo malformado responde 400 con codigo y no guarda nada`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val preguntaId = crearPregunta(admin)
        fun codigo(cuerpo: String) = runBlocking { enviar("POST", "/api/v1/revision-local/respuestas", cuerpo, usuario) }.let { it.status to it.objeto().texto("error") }

        assertEquals(HttpStatusCode.BadRequest to "lote_vacio", codigo(lote()))
        assertEquals(HttpStatusCode.BadRequest to "id_local_invalido", codigo(lote(respuesta("", preguntaId, "x"))))
        assertEquals(HttpStatusCode.BadRequest to "id_local_invalido", codigo(lote(respuesta("a".repeat(65), preguntaId, "x"))))
        assertEquals(HttpStatusCode.BadRequest to "pregunta_invalida", codigo(lote(respuesta("a", "no-es-uuid", "x"))))
        assertEquals(HttpStatusCode.BadRequest to "texto_vacio", codigo(lote(respuesta("a", preguntaId, "   "))))
        assertEquals(HttpStatusCode.BadRequest to "texto_demasiado_largo", codigo(lote(respuesta("a", preguntaId, "x".repeat(4001)))))
        assertEquals(HttpStatusCode.BadRequest to "puntaje_invalido", codigo(lote(respuesta("a", preguntaId, "x", ""","scoreLocal":150"""))))
        assertEquals(HttpStatusCode.BadRequest to "id_local_repetido", codigo(lote(respuesta("a", preguntaId, "x"), respuesta("a", preguntaId, "y"))))
        assertEquals(HttpStatusCode.BadRequest to "lote_demasiado_grande", codigo(lote(*Array(101) { respuesta("l$it", preguntaId, "x") })))
        // Un lote bueno con una respuesta mala no guarda la buena.
        assertEquals(HttpStatusCode.BadRequest to "texto_vacio", codigo(lote(respuesta("buena", preguntaId, "Redis"), respuesta("mala", preguntaId, ""))))
        val ana = runBlocking { sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id }
        assertNull(runBlocking { sistema.revisionesTexto.buscar(ana, "buena") })
    }

    /** Paridad de extremo a extremo: lo que sube el teléfono se puntúa en el servidor igual que en los casos dorados. */
    @Test
    fun `el servidor da el puntaje de los casos dorados`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        skillActual = sistema.crearCatalogo().second
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val casos = Json.parseToJsonElement(File("src/test/resources/revisor_casos_dorados.json").readText(Charsets.UTF_8))
            .jsonObject["casos"]!!.jsonArray.map { it.jsonObject }
            .filter { it["respuestaIdeal"] !is JsonNull && it["texto"]!!.jsonPrimitive.content.isNotBlank() }
            .filter { it["id"]!!.jsonPrimitive.content in setOf("api_excelente", "cache_buena", "capas_identica_a_la_ideal", "indices_buena_con_palabras_clave", "produccion_parcial", "valvar_parcial_con_objeto_criterio") }
        assertEquals(6, casos.size)
        for (c in casos) {
            val cuerpoPregunta = buildJsonObject {
                put("skillId", skillActual.toString()); put("tipo", "abierta_texto"); put("categoria", "tecnica"); put("nivel", "junior"); put("enunciado", "caso ${c["id"]!!.jsonPrimitive.content}")
                put("respuestaIdeal", c["respuestaIdeal"]!!.jsonPrimitive.content)
                put("rubrica", c["rubrica"]!!)
            }
            val creada = enviar("POST", "/api/v1/admin/preguntas", cuerpoPregunta.toString(), admin)
            assertEquals(HttpStatusCode.Created, creada.status, creada.objeto().toString())
            val id = creada.objeto().texto("id")!!
            val fila = enviar("POST", "/api/v1/revision-local/respuestas", lote(respuesta("g-${c["id"]!!.jsonPrimitive.content}", id, c["texto"]!!.jsonPrimitive.content)), usuario)
                .objeto()["resultados"]!!.jsonArray.single().jsonObject
            val esperado = c["esperado"]!!.jsonObject
            assertEquals(esperado["score"]!!.jsonPrimitive.double, fila["scoreServidor"]!!.jsonPrimitive.double, 0.0051, c["id"].toString())
            assertEquals(esperado["banda"]!!.jsonPrimitive.content, fila.texto("bandaServidor"), c["id"].toString())
        }
    }
}
