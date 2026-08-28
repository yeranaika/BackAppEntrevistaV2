package routes.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

class AuthFlowTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test user registration flow`() = testApplication {
        // Test successful registration
        val email = "test-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Test User",
                    "idioma": "es"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Created, response.status, "Registration should return 201 Created")

        val body = response.bodyAsText()
        val tokenResponse = json.decodeFromString<TokenResponse>(body)

        assertNotNull(tokenResponse.accessToken, "Access token should not be null")
        assertNotNull(tokenResponse.refreshToken, "Refresh token should not be null")
        assertTrue(tokenResponse.accessToken.isNotEmpty(), "Access token should not be empty")
        assertTrue(tokenResponse.refreshToken.isNotEmpty(), "Refresh token should not be empty")
    }

    @Test
    fun `test registration with duplicate email fails`() = testApplication {
        val email = "duplicate-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        // First registration
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "First User"
                }
            """.trimIndent())
        }

        // Second registration with same email
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Second User"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Conflict, response.status, "Duplicate email should return 409 Conflict")

        val body = response.bodyAsText()
        val errorResponse = json.decodeFromString<ErrorResponse>(body)
        assertEquals("email_in_use", errorResponse.error)
    }

    @Test
    fun `test registration with weak password fails`() = testApplication {
        val email = "weak-${System.currentTimeMillis()}@example.com"
        val password = "weak"

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Test User"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, "Weak password should return 422")

        val body = response.bodyAsText()
        val errorResponse = json.decodeFromString<ErrorResponse>(body)
        assertEquals("weak_password", errorResponse.error)
    }

    @Test
    fun `test registration with invalid email fails`() = testApplication {
        val email = "invalid-email"
        val password = "TestPassword123!"

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Test User"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status, "Invalid email should return 422")

        val body = response.bodyAsText()
        val errorResponse = json.decodeFromString<ErrorResponse>(body)
        assertEquals("invalid_email", errorResponse.error)
    }

    @Test
    fun `test login with valid credentials`() = testApplication {
        // First, register a user
        val email = "login-test-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Test User"
                }
            """.trimIndent())
        }

        // Now try to login
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, response.status, "Login should return 200 OK")

        val body = response.bodyAsText()
        val tokenResponse = json.decodeFromString<TokenResponse>(body)

        assertNotNull(tokenResponse.accessToken)
        assertNotNull(tokenResponse.refreshToken)
        assertTrue(tokenResponse.accessToken.isNotEmpty())
        assertTrue(tokenResponse.refreshToken.isNotEmpty())
    }

    @Test
    fun `test login with invalid password fails`() = testApplication {
        // First, register a user
        val email = "bad-pass-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Test User"
                }
            """.trimIndent())
        }

        // Try to login with wrong password
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "WrongPassword123!"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status, "Wrong password should return 401")

        val body = response.bodyAsText()
        val errorResponse = json.decodeFromString<ErrorResponse>(body)
        assertEquals("bad_credentials", errorResponse.error)
    }

    @Test
    fun `test login with non-existent user fails`() = testApplication {
        val email = "nonexistent-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status, "Non-existent user should return 401")

        val body = response.bodyAsText()
        val errorResponse = json.decodeFromString<ErrorResponse>(body)
        assertEquals("bad_credentials", errorResponse.error)
    }

    @Test
    fun `test complete registration and login flow`() = testApplication {
        val email = "flow-test-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        // Step 1: Register
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Flow Test User",
                    "idioma": "es"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val registerBody = registerResponse.bodyAsText()
        val registerTokens = json.decodeFromString<TokenResponse>(registerBody)
        assertNotNull(registerTokens.accessToken)

        // Step 2: Login with same credentials
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginBody = loginResponse.bodyAsText()
        val loginTokens = json.decodeFromString<TokenResponse>(loginBody)
        assertNotNull(loginTokens.accessToken)

        // Both tokens should be valid (different instances)
        assertTrue(registerTokens.accessToken != loginTokens.accessToken, "New tokens should be generated on login")
    }
}
