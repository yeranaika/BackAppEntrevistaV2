package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.SistemaPrueba
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato HTTP de la Fase 2 (usuarios) con servicios reales sobre H2. */
class PruebaControladorUsuario {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var sistema: SistemaPrueba

    /** Igual que el ErrorRes de Android: se decodifica con un Json estricto (sin ignoreUnknownKeys). */
    @Serializable
    private data class ErrorResAndroid(val error: String)

    @BeforeTest
    fun preparar() {
        sistema = SistemaPrueba()
    }

    private fun ApplicationTestBuilder.montarApp() {
        application { sistema.montar(this) }
    }

    private suspend fun ApplicationTestBuilder.enviar(
        metodo: String, ruta: String, cuerpo: String? = null, token: String? = null
    ): HttpResponse {
        val configurar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            cuerpo?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        return when (metodo) {
            "GET" -> client.get(ruta, configurar)
            "POST" -> client.post(ruta, configurar)
            "PUT" -> client.put(ruta, configurar)
            "PATCH" -> client.patch(ruta, configurar)
            "DELETE" -> client.delete(ruta, configurar)
            else -> error("Método no soportado: $metodo")
        }
    }

    private fun cuerpo(respuesta: HttpResponse): JsonObject = runBlocking {
        json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
    }

    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    /** Registra un usuario y devuelve su access token. */
    private fun registrar(correo: String = "ana@ejemplo.com", rol: String? = null): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1", nombre = "Ana"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, rol)
        sistema.tokens.emitirPar(id, rol).tokenAcceso
    }

    // ---------- Registro ----------

    @Test
    fun `registro con el JSON exacto de Android responde 201 y crea un perfil valido`() = testApplication {
        montarApp()
        val respuesta = enviar(
            "POST", "/auth/register",
            """{"email":"ana@ejemplo.com","password":"Clave-segura-1","nombre":"Ana","idioma":"es",
               "telefono":null,"fechaNacimiento":null,"genero":null,
               "nivelExperiencia":"","area":"","pais":"","notaObjetivos":"",
               "flagsAccesibilidad":{"tts":false,"altoContraste":true}}"""
        )
        assertEquals(HttpStatusCode.Created, respuesta.status)
        assertTrue(cuerpo(respuesta).texto("accessToken")!!.isNotBlank())
    }

    @Test
    fun `los errores del registro se leen con el Json estricto de Android`() = testApplication {
        montarApp()
        val debil = enviar("POST", "/auth/register", """{"email":"ana@ejemplo.com","password":"corta"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, debil.status)
        assertEquals("weak_password", Json.decodeFromString<ErrorResAndroid>(debil.bodyAsText()).error)

        registrar()
        val duplicado = enviar("POST", "/auth/register", """{"email":"ana@ejemplo.com","password":"Clave-segura-1"}""")
        assertEquals(HttpStatusCode.Conflict, duplicado.status)
        assertEquals("email_in_use", Json.decodeFromString<ErrorResAndroid>(duplicado.bodyAsText()).error)
    }

    // ---------- /me ----------

    @Test
    fun `GET me devuelve cuenta, perfil con nivel en codigo de app y cargo meta`() = testApplication {
        montarApp()
        val token = registrar()
        enviar("PUT", "/perfil/objetivo", """{"area":"TI","metaCargo":"Backend Developer","nivel":"mid"}""", token)

        val me = cuerpo(enviar("GET", "/me", token = token))
        assertEquals("ana@ejemplo.com", me.texto("email"))
        assertEquals("Ana", me.texto("nombre"))
        assertEquals("mid", me["perfil"]!!.jsonObject.texto("nivelExperiencia"))
        assertEquals("Backend Developer", me.texto("meta"))
    }

    @Test
    fun `PUT me actualiza y mapea validaciones a 400`() = testApplication {
        montarApp()
        val token = registrar()
        assertEquals(HttpStatusCode.Unauthorized, enviar("PUT", "/me", """{"nombre":"X"}""").status)
        assertEquals(HttpStatusCode.OK, enviar("PUT", "/me", """{"nombre":"Ana María","idioma":"en"}""", token).status)
        assertEquals("Ana María", cuerpo(enviar("GET", "/me", token = token)).texto("nombre"))

        val idioma = enviar("PUT", "/me", """{"idioma":"klingon"}""", token)
        assertEquals(HttpStatusCode.BadRequest, idioma.status)
        assertEquals("idioma_invalido", cuerpo(idioma).texto("error"))
        assertEquals("nothing_to_update", cuerpo(enviar("PUT", "/me", "{}", token)).texto("error"))
    }

    @Test
    fun `me perfil se guarda con el nivel de la app y se lee igual`() = testApplication {
        montarApp()
        val token = registrar()
        assertEquals(HttpStatusCode.NotFound, enviar("GET", "/me/perfil", token = token).status)

        val guardar = enviar("PUT", "/me/perfil", """{"nivelExperiencia":"sr","area":"Analista","pais":"cl"}""", token)
        assertEquals(HttpStatusCode.OK, guardar.status)

        val perfil = cuerpo(enviar("GET", "/me/perfil", token = token))
        assertEquals("sr", perfil.texto("nivelExperiencia"))
        assertEquals("CL", perfil.texto("pais"))
    }

    // ---------- Objetivo y onboarding ----------

    @Test
    fun `objetivo de carrera se crea, consulta y elimina`() = testApplication {
        montarApp()
        val token = registrar()
        assertEquals("objetivo_not_found", cuerpo(enviar("GET", "/me/objetivo", token = token)).texto("error"))

        val creado = cuerpo(enviar("PUT", "/me/objetivo", """{"nombreCargo":"Data Analyst","sector":"TI"}""", token))
        assertEquals("Data Analyst", creado.texto("nombreCargo"))
        assertEquals("Data Analyst", cuerpo(enviar("GET", "/me/objetivo", token = token)).texto("nombreCargo"))

        assertEquals(HttpStatusCode.OK, enviar("DELETE", "/me/objetivo", token = token).status)
        assertEquals(HttpStatusCode.NotFound, enviar("GET", "/me/objetivo", token = token).status)
    }

    @Test
    fun `perfil objetivo responde status ok y completa el onboarding`() = testApplication {
        montarApp()
        val token = registrar()
        assertEquals("false", cuerpo(enviar("GET", "/onboarding/status", token = token)).texto("completed"))

        val respuesta = enviar("PUT", "/perfil/objetivo", """{"area":"Desarollador","metaCargo":"Android Developer","nivel":"jr"}""", token)
        assertEquals("ok", cuerpo(respuesta).texto("status"))

        val estado = cuerpo(enviar("GET", "/onboarding/status", token = token))
        assertEquals("true", estado.texto("completed"))
        assertEquals("jr", estado["data"]!!.jsonObject.texto("nivelExperiencia"))
    }

    // ---------- Borrado de cuenta ----------

    @Test
    fun `borrar la cuenta exige confirmacion y luego el token ya no tiene usuario`() = testApplication {
        montarApp()
        val token = registrar()
        assertEquals("must_type_eliminar", cuerpo(enviar("DELETE", "/cuenta", """{"confirmar":"no"}""", token)).texto("error"))

        val borrado = enviar("DELETE", "/cuenta", """{"confirmar":"eliminar"}""", token)
        assertEquals(HttpStatusCode.OK, borrado.status)
        assertTrue(cuerpo(borrado).texto("message")!!.contains("eliminada"))
        assertEquals(HttpStatusCode.NotFound, enviar("GET", "/me", token = token).status)
    }

    // ---------- Contraseñas ----------

    @Test
    fun `recuperacion completa por correo con la respuesta que lee Android`() = testApplication {
        montarApp()
        registrar()

        val solicitud = enviar("POST", "/auth/forgot-password", """{"correo":"ana@ejemplo.com"}""")
        assertEquals(HttpStatusCode.OK, solicitud.status)
        assertTrue(cuerpo(solicitud).texto("message")!!.isNotBlank())
        val codigo = sistema.correo.enviados.single().codigo!!

        val malo = enviar("POST", "/auth/reset-password", """{"correo":"ana@ejemplo.com","codigo":"000000","nuevaContrasena":"Clave-nueva-1"}""")
        if (codigo != "000000") {
            assertEquals(HttpStatusCode.BadRequest, malo.status)
            assertEquals("Código inválido o expirado", cuerpo(malo).texto("message"))
        }

        val ok = enviar("POST", "/auth/reset-password", """{"correo":"ana@ejemplo.com","codigo":"$codigo","nuevaContrasena":"Clave-nueva-1"}""")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals(HttpStatusCode.OK, enviar("POST", "/auth/login", """{"email":"ana@ejemplo.com","password":"Clave-nueva-1"}""").status)
    }

    @Test
    fun `forgot password responde igual para un correo no registrado`() = testApplication {
        montarApp()
        val respuesta = enviar("POST", "/auth/forgot-password", """{"correo":"nadie@ejemplo.com"}""")
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertTrue(sistema.correo.enviados.isEmpty())
    }

    @Test
    fun `change password exige la contrasena actual`() = testApplication {
        montarApp()
        val token = registrar()
        val incorrecta = enviar("POST", "/auth/change-password", """{"contrasenaActual":"otra","nuevaContrasena":"Clave-nueva-1"}""", token)
        assertEquals(HttpStatusCode.BadRequest, incorrecta.status)
        assertEquals("La contraseña actual es incorrecta", cuerpo(incorrecta).texto("message"))

        val correcta = enviar("POST", "/auth/change-password", """{"contrasenaActual":"Clave-segura-1","nuevaContrasena":"Clave-nueva-1"}""", token)
        assertEquals(HttpStatusCode.OK, correcta.status)
    }

    // ---------- Administración ----------

    @Test
    fun `rutas de admin exigen rol admin`() = testApplication {
        montarApp()
        val usuario = registrar()
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/admin/usuarios").status)
        assertEquals(HttpStatusCode.Forbidden, enviar("GET", "/admin/usuarios", token = usuario).status)
    }

    @Test
    fun `admin crea, lista, desactiva y reactiva usuarios`() = testApplication {
        montarApp()
        val admin = registrar("admin@ejemplo.com", rol = ROL_ADMIN)

        val creado = enviar("POST", "/admin/usuarios", """{"correo":"Nuevo@Ejemplo.com","contrasena":"Clave-segura-1"}""", admin)
        assertEquals(HttpStatusCode.Created, creado.status)
        val id = cuerpo(creado).texto("id")!!
        assertEquals(HttpStatusCode.Created, enviar("POST", "/admin/users", """{"correo":"otro@ejemplo.com","contrasena":"Clave-segura-1"}""", admin).status)

        val lista = runBlocking { json.parseToJsonElement(enviar("GET", "/admin/usuarios", token = admin).bodyAsText()).jsonArray }
        assertEquals(3, lista.size)

        assertEquals(HttpStatusCode.OK, enviar("DELETE", "/admin/usuarios/$id", token = admin).status)
        assertEquals(
            HttpStatusCode.Forbidden,
            enviar("POST", "/auth/login", """{"email":"nuevo@ejemplo.com","password":"Clave-segura-1"}""").status
        )
        assertEquals(HttpStatusCode.OK, enviar("PATCH", "/admin/usuarios/$id/activar", token = admin).status)
        assertEquals(HttpStatusCode.OK, enviar("POST", "/auth/login", """{"email":"nuevo@ejemplo.com","password":"Clave-segura-1"}""").status)

        assertEquals("id_invalido", cuerpo(enviar("PATCH", "/admin/usuarios/no-uuid/activar", token = admin)).texto("error"))
    }

    @Test
    fun `ningun error filtra detalles internos`() = testApplication {
        montarApp()
        val token = registrar()
        val respuestas = listOf(
            enviar("PUT", "/me", "{ no es json", token),
            enviar("PUT", "/me/perfil", """{"pais":"Chile"}""", token),
            enviar("POST", "/auth/reset-password", """{"correo":"x","codigo":"1","nuevaContrasena":"1"}""")
        )
        respuestas.forEach { assertFalse(it.bodyAsText().contains("Exception"), it.bodyAsText()) }
        assertNull(cuerpo(respuestas[0])["stackTrace"])
    }
}
