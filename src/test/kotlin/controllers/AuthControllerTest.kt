package controllers

import com.auth0.jwt.JWT
import data.repository.usuarios.UserRepository
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import services.AuthService
import services.AuthTestDb
import services.AuthTestDb.AUDIENCE
import services.AuthTestDb.ISSUER
import services.AuthTestDb.algorithm
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Contrato HTTP de registro y PUT /me. El de login está en PRUEBAS/PRUEBA_CONTROLADOR_LOGIN. */
class AuthControllerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var users: UserRepository
    private lateinit var service: AuthService

    @BeforeTest
    fun setup() {
        AuthTestDb.connect()
        users = UserRepository()
        service = AuthTestDb.service(users)
    }

    private fun ApplicationTestBuilder.installAuth() {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build())
                    validate { cred -> if (cred.subject != null) JWTPrincipal(cred.payload) else null }
                }
            }
            routing { authController(service) }
        }
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String): HttpResponse =
        client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun errorOf(body: String) = json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content

    private suspend fun ApplicationTestBuilder.registerOk(email: String, password: String): String {
        val res = postJson("/auth/register", """{"email":"$email","password":"$password"}""")
        assertEquals(HttpStatusCode.Created, res.status)
        return json.parseToJsonElement(res.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    // ---------- Registro ----------

    @Test
    fun `register responde 201 con tokens`() = testApplication {
        installAuth()
        registerOk("user@example.com", "Password123!")
    }

    @Test
    fun `register mapea errores de validacion a 422, duplicado a 409 y json invalido a 400`() = testApplication {
        installAuth()

        val invalidEmail = postJson("/auth/register", """{"email":"no-email","password":"Password123!"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, invalidEmail.status)
        assertEquals("invalid_email", errorOf(invalidEmail.bodyAsText()))

        val weak = postJson("/auth/register", """{"email":"a@example.com","password":"corta"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, weak.status)
        assertEquals("weak_password", errorOf(weak.bodyAsText()))

        registerOk("dup@example.com", "Password123!")
        val dup = postJson("/auth/register", """{"email":"dup@example.com","password":"Password123!"}""")
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals("email_in_use", errorOf(dup.bodyAsText()))

        val malformed = postJson("/auth/register", """{ esto no es json""")
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("invalid_json", errorOf(malformed.bodyAsText()))
    }

    // ---------- PUT /me ----------

    @Test
    fun `put me exige token, actualiza con 200 y mapea validaciones a 400`() = testApplication {
        installAuth()
        val token = registerOk("user@example.com", "Password123!")

        val noToken = client.put("/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"nombre":"X"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)

        suspend fun put(body: String) = client.put("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val ok = put("""{"nombre":"Nuevo Nombre","idioma":"en"}""")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("Nuevo Nombre", runBlocking { users.findByEmail("user@example.com")!!.nombre })

        val badLang = put("""{"idioma":"klingon"}""")
        assertEquals(HttpStatusCode.BadRequest, badLang.status)
        assertEquals("idioma_invalido", errorOf(badLang.bodyAsText()))

        val nothing = put("""{}""")
        assertEquals(HttpStatusCode.BadRequest, nothing.status)
        assertEquals("nothing_to_update", errorOf(nothing.bodyAsText()))

        val malformed = put("""{ esto no es json""")
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("invalid_json", errorOf(malformed.bodyAsText()))

        // Ningún error filtra detalles internos
        assertFalse(badLang.bodyAsText().contains("Exception"))
    }
}
