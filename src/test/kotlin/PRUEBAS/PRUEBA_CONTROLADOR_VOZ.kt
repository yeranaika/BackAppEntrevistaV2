package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Voz en la llamada: transcripción por pregunta (VOZ-3/VOZ-4) y diccionarios de terminología editables y sincronizables. */
class PruebaControladorVoz {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tokenDe(sistema: SistemaPrueba, correo: String): String =
        runBlocking { sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1")).tokenAcceso }

    private fun tokenAdmin(sistema: SistemaPrueba): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro("admin@ejemplo.com", "Clave-segura-1"))
        val id = sistema.usuarios.buscarPorCorreo("admin@ejemplo.com")!!.id
        sistema.usuarios.actualizarRol(id, ROL_ADMIN)
        sistema.tokens.emitirPar(id, ROL_ADMIN).tokenAcceso.also { check(par.tokenAcceso.isNotEmpty()) }
    }

    private fun ApplicationTestBuilder.get(ruta: String, token: String?): HttpResponse =
        runBlocking { client.get(ruta) { token?.let { bearerAuth(it) } } }

    private fun ApplicationTestBuilder.enviar(metodo: String, ruta: String, cuerpo: String?, token: String?): HttpResponse = runBlocking {
        val configurar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            if (cuerpo != null) {
                contentType(ContentType.Application.Json)
                setBody(cuerpo)
            }
        }
        when (metodo) {
            "PUT" -> client.put(ruta, configurar)
            "DELETE" -> client.delete(ruta, configurar)
            else -> client.post(ruta, configurar)
        }
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }


    // ---------- Diccionarios ----------

    private fun paquete(nombre: String = "Programador Front-end", terminos: String = """"React","TypeScript"""", extra: String = "") =
        """{"nombre":"$nombre","terminos":[$terminos],"correcciones":[{"de":"riact","a":"React"}]$extra}"""

    @Test
    fun `la app lista y baja los diccionarios iniciales y exige sesion`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        enviar("PUT", "/api/v1/admin/diccionarios/programador-front", paquete(), admin)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")

        assertEquals(HttpStatusCode.Unauthorized, get("/api/v1/diccionarios", null).status)
        val manifiesto = get("/api/v1/diccionarios", usuario).objeto()["elementos"]!!.jsonArray.single().jsonObject
        assertEquals("programador-front", manifiesto["area"]!!.jsonPrimitive.content)
        assertEquals("1", manifiesto["version"]!!.jsonPrimitive.content)
        assertEquals("2", manifiesto["totalTerminos"]!!.jsonPrimitive.content)

        val completo = get("/api/v1/diccionarios/programador-front", usuario).objeto()
        assertEquals(2, completo["terminos"]!!.jsonArray.size)
        assertEquals("React", completo["correcciones"]!!.jsonArray.single().jsonObject["a"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/diccionarios/no-existe", usuario).status)
    }

    @Test
    fun `solo un admin crea y edita diccionarios`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        assertEquals(HttpStatusCode.Unauthorized, enviar("PUT", "/api/v1/admin/diccionarios/x", paquete(), null).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("PUT", "/api/v1/admin/diccionarios/x", paquete(), usuario).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("DELETE", "/api/v1/admin/diccionarios/x", null, usuario).status)
        assertEquals(HttpStatusCode.Forbidden, get("/api/v1/admin/diccionarios", usuario).status)
    }

    @Test
    fun `crear responde 201, reemplazar 200 y cada edicion sube la version`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)

        val creado = enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps", """"Docker""""), admin)
        assertEquals(HttpStatusCode.Created, creado.status)
        assertEquals("1", creado.objeto()["version"]!!.jsonPrimitive.content)
        assertTrue(creado.objeto()["mensaje"]!!.jsonPrimitive.content.isNotBlank())

        val editado = enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps", """"Docker","Kubernetes""""), admin)
        assertEquals(HttpStatusCode.OK, editado.status)
        assertEquals("2", editado.objeto()["version"]!!.jsonPrimitive.content)
        assertEquals(2, get("/api/v1/diccionarios/devops", admin).objeto()["terminos"]!!.jsonArray.size)
    }

    @Test
    fun `guardar con una version vieja responde 409 y no pisa la edicion de otra persona`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps"), admin)
        enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps", """"Docker""""), admin) // versión 2

        val viejo = enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps", """"Otro"""", ""","versionEsperada":1"""), admin)
        assertEquals(HttpStatusCode.Conflict, viejo.status)
        assertEquals(listOf("Docker"), get("/api/v1/diccionarios/devops", admin).objeto()["terminos"]!!.jsonArray.map { it.jsonPrimitive.content })

        val vigente = enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps", """"Otro"""", ""","versionEsperada":2"""), admin)
        assertEquals(HttpStatusCode.OK, vigente.status)
        assertEquals("3", vigente.objeto()["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun `un diccionario desactivado desaparece para la app pero el panel lo sigue viendo y puede reactivarlo`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps"), admin)

        assertEquals(HttpStatusCode.OK, enviar("DELETE", "/api/v1/admin/diccionarios/devops", null, admin).status)
        assertTrue(get("/api/v1/diccionarios", usuario).objeto()["elementos"]!!.jsonArray.isEmpty())
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/diccionarios/devops", usuario).status)
        val panel = get("/api/v1/admin/diccionarios/devops", admin).objeto()
        assertFalse(panel["activo"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("2", panel["version"]!!.jsonPrimitive.content) // desactivar también es un cambio que la app debe notar
        assertEquals(HttpStatusCode.NotFound, enviar("DELETE", "/api/v1/admin/diccionarios/no-existe", null, admin).status)

        val reactivado = enviar("PUT", "/api/v1/admin/diccionarios/devops", paquete("DevOps"), admin)
        assertEquals(HttpStatusCode.OK, reactivado.status)
        assertEquals(HttpStatusCode.OK, get("/api/v1/diccionarios/devops", usuario).status)
    }

    @Test
    fun `datos invalidos del diccionario responden 400`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        fun put(area: String, cuerpo: String) = enviar("PUT", "/api/v1/admin/diccionarios/$area", cuerpo, admin).status

        assertEquals(HttpStatusCode.BadRequest, put("Front End", paquete()), "área con mayúsculas y espacio")
        assertEquals(HttpStatusCode.BadRequest, put("front", """{"nombre":" ","terminos":[]}"""), "nombre vacío")
        assertEquals(HttpStatusCode.BadRequest, put("front", """{"nombre":"F","terminos":["${"x".repeat(61)}"]}"""), "término largo")
        assertEquals(HttpStatusCode.BadRequest, put("front", """{"nombre":"F","terminos":[],"correcciones":[{"de":"React","a":"react"}]}"""), "no cambia nada")
        assertEquals(HttpStatusCode.BadRequest, put("front", """{"nombre":"F","terminos":[],"correcciones":[{"de":"","a":"React"}]}"""), "de vacío")
    }

    @Test
    fun `los terminos se limpian sin vacios ni repetidos y el json exportado se puede reimportar`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenAdmin(sistema)
        enviar("PUT", "/api/v1/admin/diccionarios/front", """{"nombre":"Front","terminos":[" React ","react","","Vue"],"correcciones":[{"de":"riact","a":"React"},{"de":"RIACT","a":"React"}]}""", admin)

        val exportado = get("/api/v1/diccionarios/front", admin).objeto()
        assertEquals(listOf("React", "Vue"), exportado["terminos"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(1, exportado["correcciones"]!!.jsonArray.size)

        // Importar en otro ambiente: el mismo JSON (con sus campos de más) sirve como cuerpo del PUT.
        val reimportado = enviar("PUT", "/api/v1/admin/diccionarios/front-copia", exportado.toString(), admin)
        assertEquals(HttpStatusCode.Created, reimportado.status)
    }

    // ---------- Transcripciones ----------

    private data class Escenario(val token: String, val sesionId: UUID, val preguntaId: UUID, val otraPreguntaId: UUID)

    private fun escenario(sistema: SistemaPrueba, correo: String = "voz@ejemplo.com"): Escenario = runBlocking {
        val token = tokenDe(sistema, correo)
        val usuarioId = sistema.usuarios.buscarPorCorreo(correo)!!.id
        val sesion = sistema.entrevista.iniciar(usuarioId, PedidoEntrevista(nombreCargo = "Backend Developer"))
        val ids = sesion.preguntas.mapNotNull { it.preguntaId }
        Escenario(token, sesion.id, ids[0], ids[1])
    }

    private fun transcripcion(e: Escenario, texto: String, parcial: Boolean = false, preguntaId: UUID = e.preguntaId, extra: String = "") =
        """{"sesionId":"${e.sesionId}","preguntaId":"$preguntaId","inicioMs":1000,"finMs":9000,"texto":"$texto","parcial":$parcial$extra}"""

    @Test
    fun `guardar una transcripcion responde 201 y se lista por sesion`() = testApplication {
        val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
        application { sistema.montar(this) }
        val e = escenario(sistema)

        val creada = enviar("POST", "/api/v1/transcripciones", transcripcion(e, "Usé Kotlin con Ktor", extra = ""","confianza":0.9"""), e.token)
        assertEquals(HttpStatusCode.Created, creada.status)
        assertEquals("Usé Kotlin con Ktor", creada.objeto()["texto"]!!.jsonPrimitive.content)
        assertEquals("es", creada.objeto()["idioma"]!!.jsonPrimitive.content)

        val lista = get("/api/v1/transcripciones?sesionId=${e.sesionId}", e.token).objeto()["elementos"]!!.jsonArray
        assertEquals(1, lista.size)
        assertEquals(e.preguntaId.toString(), lista.single().jsonObject["preguntaId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reenviar la misma pregunta la reemplaza y un parcial no pisa a un final`() = testApplication {
        val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
        application { sistema.montar(this) }
        val e = escenario(sistema)

        enviar("POST", "/api/v1/transcripciones", transcripcion(e, "borrador", parcial = true), e.token)
        val final = enviar("POST", "/api/v1/transcripciones", transcripcion(e, "versión final"), e.token)
        assertEquals(HttpStatusCode.OK, final.status)

        val tarde = enviar("POST", "/api/v1/transcripciones", transcripcion(e, "parcial atrasado", parcial = true), e.token)
        assertEquals(HttpStatusCode.OK, tarde.status)
        assertEquals("versión final", tarde.objeto()["texto"]!!.jsonPrimitive.content)

        val lista = get("/api/v1/transcripciones?sesionId=${e.sesionId}", e.token).objeto()["elementos"]!!.jsonArray
        assertEquals(1, lista.size)
        assertEquals("versión final", lista.single().jsonObject["texto"]!!.jsonPrimitive.content)
    }

    @Test
    fun `la transcripcion es privada de su dueno y exige una pregunta de esa sesion`() = testApplication {
        val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
        application { sistema.montar(this) }
        val e = escenario(sistema)
        val ajeno = tokenDe(sistema, "otro@ejemplo.com")

        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/api/v1/transcripciones", transcripcion(e, "x"), null).status)
        assertEquals(HttpStatusCode.NotFound, enviar("POST", "/api/v1/transcripciones", transcripcion(e, "hola"), ajeno).status)
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/transcripciones?sesionId=${e.sesionId}", ajeno).status)
        assertEquals(HttpStatusCode.BadRequest, enviar("POST", "/api/v1/transcripciones", transcripcion(e, "hola", preguntaId = UUID.randomUUID()), e.token).status)
    }

    @Test
    fun `datos invalidos de la transcripcion responden 400`() = testApplication {
        val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
        application { sistema.montar(this) }
        val e = escenario(sistema)
        fun enviarCuerpo(c: String) = enviar("POST", "/api/v1/transcripciones", c, e.token).status

        assertEquals(HttpStatusCode.BadRequest, enviarCuerpo(transcripcion(e, "   ")), "texto vacío")
        assertEquals(HttpStatusCode.BadRequest, enviarCuerpo(transcripcion(e, "x".repeat(8_001))), "texto largo")
        assertEquals(HttpStatusCode.BadRequest, enviarCuerpo(transcripcion(e, "hola", extra = ""","confianza":1.5""")), "confianza")
        assertEquals(HttpStatusCode.BadRequest, enviarCuerpo("""{"sesionId":"${e.sesionId}","preguntaId":"${e.preguntaId}","inicioMs":9000,"finMs":1000,"texto":"hola"}"""), "tiempos")
        assertEquals(HttpStatusCode.BadRequest, enviarCuerpo("""{"sesionId":"no-es-uuid","preguntaId":"${e.preguntaId}","inicioMs":0,"finMs":1,"texto":"hola"}"""), "uuid")
    }
}
