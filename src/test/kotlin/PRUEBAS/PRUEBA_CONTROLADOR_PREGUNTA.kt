package PRUEBAS

import ERRORES.ErrorServicioExterno
import ESQUEMAS.SolicitudRegistro
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import PRUEBAS.DOBLES.SistemaPrueba
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.JsonElement
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

/** Contrato HTTP del banco de preguntas y de la generación con IA (LLM falso). */
class PruebaControladorPregunta {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "/api/v1/admin/preguntas"

    private fun sistemaCon(proveedor: ProveedorIaGrabador = ProveedorIaGrabador(LoteLlmDePrueba.json(2, "abierta_texto"))) =
        SistemaPrueba(proveedorIa = proveedor)

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
            "POST" -> client.post(ruta, configurar)
            "PUT" -> client.put(ruta, configurar)
            "PATCH" -> client.patch(ruta, configurar)
            else -> client.delete(ruta, configurar)
        }
    }

    private fun HttpResponse.json(): JsonElement = runBlocking { json.parseToJsonElement(bodyAsText()) }
    private fun HttpResponse.objeto(): JsonObject = json().jsonObject
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    private fun preguntaOpcionMultiple(skillId: UUID) = """
        {"skillId":"$skillId","tipo":"opcion_multiple","categoria":"tecnica","nivel":"junior",
         "enunciado":"¿Qué hace suspend?","respuestaIdeal":"Marca una función que puede suspenderse",
         "opciones":[{"texto":"Permite suspender sin bloquear el hilo","esCorrecta":true,"explicacion":"Correcto"},
                     {"texto":"Crea un hilo nuevo"},{"texto":"Bloquea el hilo actual"}]}
    """.trimIndent()

    @Test
    fun `solo un admin administra el banco de preguntas`() = testApplication {
        val sistema = sistemaCon()
        application { sistema.montar(this) }
        val usuario = tokenDe(sistema, "ana@ejemplo.com")

        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", base).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("GET", base, token = usuario).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "$base/generar-ia", "{}", usuario).status)
    }

    @Test
    fun `ciclo completo crear, listar, editar, rechazar, aprobar y eliminar`() = testApplication {
        val sistema = sistemaCon()
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (_, skillId) = sistema.crearCatalogo()

        val creada = enviar("POST", base, preguntaOpcionMultiple(skillId), admin)
        assertEquals(HttpStatusCode.Created, creada.status)
        val id = creada.objeto().texto("id")!!
        assertEquals("aprobada", creada.objeto().texto("estado"))
        assertEquals("Pregunta creada y aprobada", creada.objeto().texto("mensaje"))

        val lista = enviar("GET", "$base?estado=aprobada&tipo=opcion_multiple", token = admin).objeto()
        assertEquals("1", lista.texto("total"))

        val editada = enviar("PUT", "$base/$id", preguntaOpcionMultiple(skillId).replace("¿Qué hace suspend?", "¿Para qué sirve suspend?"), admin).objeto()
        assertEquals("pendiente", editada.texto("estado"))

        val rechazada = enviar("PATCH", "$base/$id/rechazar", """{"motivo":"Muy fácil"}""", admin).objeto()
        assertEquals("Muy fácil", rechazada.texto("motivoRechazo"))
        assertEquals("aprobada", enviar("PATCH", "$base/$id/aprobar", token = admin).objeto().texto("estado"))

        val eliminada = enviar("DELETE", "$base/$id", token = admin)
        assertEquals(HttpStatusCode.OK, eliminada.status)
        assertEquals("Pregunta eliminada", eliminada.objeto().texto("mensaje"))
        assertEquals("pregunta_no_encontrada", enviar("GET", "$base/$id", token = admin).objeto().texto("error"))
    }

    @Test
    fun `errores de validacion responden 400 con codigo y mensaje`() = testApplication {
        val sistema = sistemaCon()
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (_, skillId) = sistema.crearCatalogo()

        val dosCorrectas = enviar("POST", base, preguntaOpcionMultiple(skillId).replace("\"Crea un hilo nuevo\"}", "\"Crea un hilo nuevo\",\"esCorrecta\":true}"), admin)
        assertEquals(HttpStatusCode.BadRequest, dosCorrectas.status)
        assertEquals("debe_haber_una_correcta", dosCorrectas.objeto().texto("error"))
        assertTrue(dosCorrectas.objeto().texto("mensaje")!!.isNotBlank())

        assertEquals("parametro_invalido", enviar("GET", "$base?pagina=uno", token = admin).objeto().texto("error"))
        assertEquals("id_invalido", enviar("GET", "$base/no-uuid", token = admin).objeto().texto("error"))
    }

    @Test
    fun `generar con IA crea preguntas pendientes y responde el contrato snake_case del panel`() = testApplication {
        val sistema = sistemaCon()
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (cargoId, _) = sistema.crearCatalogo()
        val pedido = """{"cargo_id":"$cargoId","nivel":"senior","cantidad":2,"tipo":"abierta_texto","categoria":"blanda"}"""

        val respuesta = enviar("POST", "$base/generar-ia", pedido, admin)
        assertEquals(HttpStatusCode.Created, respuesta.status)
        assertEquals("2", respuesta.objeto().texto("preguntas_generadas"))
        val primera = respuesta.objeto()["preguntas"]!!.jsonArray.first().jsonObject
        assertEquals("pendiente", primera.texto("estado"))
        assertTrue(primera.texto("pregunta_id")!!.isNotBlank())

        // La ruta antigua sigue respondiendo igual.
        assertEquals(HttpStatusCode.Created, enviar("POST", "/api/v1/admin/questions/generate-ai", pedido, admin).status)
        assertEquals("4", enviar("GET", "$base?generadaPorIa=true&estado=pendiente", token = admin).objeto().texto("total"))
    }

    @Test
    fun `generar con un cargo inexistente responde 404 sin llamar al LLM`() = testApplication {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        val sistema = sistemaCon(proveedor)
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)

        val respuesta = enviar("POST", "$base/generar-ia", """{"cargo_id":"${UUID.randomUUID()}","nivel":"senior","cantidad":1}""", admin)
        assertEquals(HttpStatusCode.NotFound, respuesta.status)
        assertEquals("cargo_no_encontrado", respuesta.objeto().texto("error"))
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    @Test
    fun `fallas del proveedor responden 503 o 502 sin detalles internos`() = testApplication {
        val sistema = sistemaCon(ProveedorIaGrabador(error = ErrorServicioExterno("provider_not_configured", "sin clave")))
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (cargoId, _) = sistema.crearCatalogo()

        val respuesta = enviar("POST", "$base/generar-ia", """{"cargo_id":"$cargoId","nivel":"senior","cantidad":1}""", admin)
        assertEquals(HttpStatusCode.ServiceUnavailable, respuesta.status)
        assertEquals("provider_not_configured", respuesta.objeto().texto("error"))
    }

    @Test
    fun `una respuesta inutilizable del LLM responde 502 sin mostrarla`() = testApplication {
        val sistema = sistemaCon(ProveedorIaGrabador("texto que no es json con secreto-interno"))
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (cargoId, _) = sistema.crearCatalogo()

        val respuesta = enviar("POST", "$base/generar-ia", """{"cargo_id":"$cargoId","nivel":"senior","cantidad":1}""", admin)
        assertEquals(HttpStatusCode.BadGateway, respuesta.status)
        assertEquals("provider_invalid_output", respuesta.objeto().texto("error"))
        assertFalse(respuesta.bodyAsText().contains("secreto-interno"))
    }

    @Test
    fun `el usuario ve solo preguntas aprobadas y sin la solucion`() = testApplication {
        val sistema = sistemaCon()
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val (cargoId, skillId) = sistema.crearCatalogo()

        enviar("POST", base, preguntaOpcionMultiple(skillId), admin)
        enviar("POST", "$base/generar-ia", """{"cargo_id":"$cargoId","nivel":"senior","cantidad":2}""", admin)

        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/api/v1/preguntas").status)
        val visibles = enviar("GET", "/api/v1/preguntas?cantidad=20", token = usuario).json().jsonArray
        assertEquals(1, visibles.size)
        val pregunta = visibles.single().jsonObject
        assertNull(pregunta["respuestaIdeal"])
        assertTrue(pregunta["opciones"]!!.jsonArray.all { it.jsonObject["esCorrecta"] == null })
        assertFalse(enviar("GET", "/api/v1/preguntas?skillId=$skillId", token = usuario).bodyAsText().contains("explicacion"))
    }
}
