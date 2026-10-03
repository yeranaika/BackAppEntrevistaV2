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

/** Verifica el contrato HTTP del AuthController: códigos de estado y cuerpos de error. */
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
                // Proveedor OAuth de mentira: solo para que authenticate("google-oauth") exista
                oauth("google-oauth") {
                    urlProvider = { "http://localhost/auth/google/callback" }
                    providerLookup = {
                        OAuthServerSettings.OAuth2ServerSettings(
                            name = "google",
                            authorizeUrl = "http://localhost/authorize",
                            accessTokenUrl = "http://localhost/token",
                            requestMethod = HttpMethod.Post,
                            clientId = "test",
                            clientSecret = "test"
                        )
                    }
                    client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
                }
            }
            routing { authController(service, ISSUER, AUDIENCE, algorithm) }
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

    // ---------- Login ----------

    @Test
    fun `login responde 200, 401 con credenciales malas y 403 con usuario inactivo`() = testApplication {
        installAuth()
        registerOk("user@example.com", "Password123!")

        val ok = postJson("/auth/login", """{"email":"user@example.com","password":"Password123!"}""")
        assertEquals(HttpStatusCode.OK, ok.status)

        val bad = postJson("/auth/login", """{"email":"user@example.com","password":"Incorrecta1"}""")
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        assertEquals("bad_credentials", errorOf(bad.bodyAsText()))

        val wrongType = postJson("/auth/login", """{"email":"user@example.com"}""")
        assertEquals(HttpStatusCode.BadRequest, wrongType.status)
        assertEquals("invalid_json", errorOf(wrongType.bodyAsText()))

        val missing = postJson("/auth/login", """{"email":"nadie@example.com","password":"Password123!"}""")
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("bad_credentials", errorOf(missing.bodyAsText()))

        val uid = runBlocking { users.findByEmail("user@example.com")!!.id }
        transaction {
            UsuarioTable.update({ UsuarioTable.usuarioId eq uid }) { it[estado] = "inactivo" }
        }
        val inactive = postJson("/auth/login", """{"email":"user@example.com","password":"Password123!"}""")
        assertEquals(HttpStatusCode.Forbidden, inactive.status)
        assertEquals("inactive_user", errorOf(inactive.bodyAsText()))
    }

    @Test
    fun `login del usuario admin entrega un token con rol admin`() = testApplication {
        installAuth()
        registerOk("admin@example.com", "AdminPassword123")
        runBlocking { users.updateRol(users.findByEmail("admin@example.com")!!.id, "admin") }

        val res = postJson("/auth/login", """{"email":"admin@example.com","password":"AdminPassword123"}""")
        assertEquals(HttpStatusCode.OK, res.status)
        val token = json.parseToJsonElement(res.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
        assertEquals("admin", JWT.decode(token).getClaim("role").asString())
    }

    // ---------- Google ----------

    @Test
    fun `google con idToken invalido responde 401 y no 500`() = testApplication {
        installAuth()
        val res = postJson("/auth/google", """{"idToken":"no-es-un-token"}""")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertEquals("invalid_google_token", errorOf(res.bodyAsText()))
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
