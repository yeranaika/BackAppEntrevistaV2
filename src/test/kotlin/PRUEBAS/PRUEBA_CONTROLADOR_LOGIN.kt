package PRUEBAS

import INTEGRACIONES.ClienteGoogleIdentidad
import INTEGRACIONES.IdentidadGoogle
import ESQUEMAS.SolicitudRegistro
import PRUEBAS.DOBLES.GoogleEnMemoria
import PRUEBAS.DOBLES.SistemaPrueba
import com.auth0.jwt.JWT
import MODELOS.TablaUsuario
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contrato HTTP de /auth/login, /auth/google, /auth/refresh y /auth/logout contra H2. */
class PruebaControladorLogin {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var sistema: SistemaPrueba

    @BeforeTest
    fun preparar() {
        val google = GoogleEnMemoria(
            mapOf("token-valido" to IdentidadGoogle("sub-ana", "ana@gmail.com", correoVerificado = true))
        )
        sistema = SistemaPrueba(google)
        runBlocking { sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1")) }
    }

    private fun ApplicationTestBuilder.montarApp() {
        application { sistema.montar(this) }
    }

    private suspend fun ApplicationTestBuilder.postJson(ruta: String, cuerpo: String): HttpResponse =
        client.post(ruta) {
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }

    private fun campo(cuerpo: String, nombre: String): String? =
        json.parseToJsonElement(cuerpo).jsonObject[nombre]?.jsonPrimitive?.content

    private suspend fun ApplicationTestBuilder.login(contrasena: String = "Clave-segura-1") =
        postJson("/auth/login", """{"email":"ana@ejemplo.com","password":"$contrasena"}""")

    private suspend fun ApplicationTestBuilder.refresh(token: String) =
        postJson("/auth/refresh", """{"refreshToken":"$token"}""")

    // ---------- Login ----------

    @Test
    fun `login responde 200 con accessToken y refreshToken`() = testApplication {
        montarApp()
        val respuesta = login()
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val cuerpo = respuesta.bodyAsText()
        assertEquals("user", JWT.decode(campo(cuerpo, "accessToken")).getClaim("role").asString())
        assertEquals(43, campo(cuerpo, "refreshToken")!!.length)
    }

    @Test
    fun `login mapea credenciales malas a 401, cuerpo invalido a 400 y cuenta inactiva a 403`() = testApplication {
        montarApp()

        val mala = login("Otra-clave-1")
        assertEquals(HttpStatusCode.Unauthorized, mala.status)
        assertEquals("bad_credentials", campo(mala.bodyAsText(), "error"))

        val sinCampo = postJson("/auth/login", """{"email":"ana@ejemplo.com"}""")
        assertEquals(HttpStatusCode.BadRequest, sinCampo.status)
        assertEquals("invalid_json", campo(sinCampo.bodyAsText(), "error"))

        val malformado = postJson("/auth/login", """{ esto no es json""")
        assertEquals(HttpStatusCode.BadRequest, malformado.status)
        assertEquals("invalid_json", campo(malformado.bodyAsText(), "error"))

        transaction { TablaUsuario.update({ TablaUsuario.correo eq "ana@ejemplo.com" }) { it[estado] = "inactivo" } }
        val inactiva = login()
        assertEquals(HttpStatusCode.Forbidden, inactiva.status)
        assertEquals("inactive_user", campo(inactiva.bodyAsText(), "error"))
    }

    // ---------- Google ----------

    @Test
    fun `google responde 200 con token valido y 401 con token invalido`() = testApplication {
        montarApp()
        assertEquals(HttpStatusCode.OK, postJson("/auth/google", """{"idToken":"token-valido"}""").status)

        val invalido = postJson("/auth/google", """{"idToken":"no-es-un-token"}""")
        assertEquals(HttpStatusCode.Unauthorized, invalido.status)
        assertEquals("invalid_google_token", campo(invalido.bodyAsText(), "error"))
    }

    @Test
    fun `el cliente real de Google trata un token malformado como invalido sin usar la red`() = runBlocking<Unit> {
        assertNull(ClienteGoogleIdentidad("client-id-prueba").verificar("no-es-un-token"))
    }

    // ---------- Refresh y logout ----------

    @Test
    fun `refresh rota el token y el anterior deja de servir`() = testApplication {
        montarApp()
        val original = campo(login().bodyAsText(), "refreshToken")!!

        val rotado = refresh(original)
        assertEquals(HttpStatusCode.OK, rotado.status)

        val reutilizado = refresh(original)
        assertEquals(HttpStatusCode.Unauthorized, reutilizado.status)
        assertEquals("invalid_refresh", campo(reutilizado.bodyAsText(), "error"))
    }

    @Test
    fun `refresh conserva el rol admin`() = testApplication {
        montarApp()
        runBlocking { sistema.usuarios.actualizarRol(sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id, "admin") }
        val refreshToken = campo(login().bodyAsText(), "refreshToken")!!

        val nuevoAcceso = campo(refresh(refreshToken).bodyAsText(), "accessToken")
        assertEquals("admin", JWT.decode(nuevoAcceso).getClaim("role").asString())
    }

    @Test
    fun `refresh sin token responde 400 missing_refresh`() = testApplication {
        montarApp()
        val respuesta = refresh("  ")
        assertEquals(HttpStatusCode.BadRequest, respuesta.status)
        assertEquals("missing_refresh", campo(respuesta.bodyAsText(), "error"))
    }

    @Test
    fun `solo uno de dos refresh simultaneos con el mismo token gana`() = testApplication {
        montarApp()
        val token = campo(login().bodyAsText(), "refreshToken")!!

        val estados = runBlocking {
            (1..2).map { async { refresh(token).status } }.awaitAll()
        }
        assertEquals(1, estados.count { it == HttpStatusCode.OK }, "estados: $estados")
    }

    @Test
    fun `logout revoca el refresh token y es idempotente`() = testApplication {
        montarApp()
        val token = campo(login().bodyAsText(), "refreshToken")!!

        val salida = postJson("/auth/logout", """{"refreshToken":"$token"}""")
        assertEquals(HttpStatusCode.OK, salida.status)
        assertEquals("Sesión cerrada", campo(salida.bodyAsText(), "mensaje"))
        assertEquals(HttpStatusCode.OK, postJson("/auth/logout", """{"refreshToken":"$token"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(token).status)
    }
}
