package routes.admin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.models.ai.GenerateQuestionsReq
import data.models.ai.PreguntaGeneradaRes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import services.GenerationResult
import services.ai.AiProviderInvalidOutputException
import services.ai.AiProviderNotConfiguredException
import services.ai.AiQuestionGenerationUseCase
import services.ai.AiRequestValidationException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAiRoutesTest {
    private val algorithm = Algorithm.HMAC512("test-secret")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `route returns 201 only for exact full batch`() = testApplication {
        val useCase = FakeUseCase { req ->
            GenerationResult(
                inserted = (1..req.cantidad).map {
                    PreguntaGeneradaRes(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Pregunta $it", "pendiente", 1, 2, 0.1)
                },
                errores = 0
            )
        }
        installAdminAiRoute(useCase)

        val response = client.post("/api/v1/admin/questions/generate-ai") {
            bearerAuth(adminToken())
            contentType(ContentType.Application.Json)
            setBody(validBody(cantidad = 10))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("10", body["preguntas_generadas"]?.jsonPrimitive.toString())
        assertEquals("0", body["preguntas_con_error"]?.jsonPrimitive.toString())
    }

    @Test
    fun `route maps malformed json to 400 and validation to 422 without leaking details`() = testApplication {
        val useCase = FakeUseCase { throw AiRequestValidationException("invalid_uuid") }
        installAdminAiRoute(useCase)

        val malformed = client.post("/api/v1/admin/questions/generate-ai") {
            bearerAuth(adminToken())
            contentType(ContentType.Application.Json)
            setBody("{ invalid json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertFalse(malformed.bodyAsText().contains("Exception", ignoreCase = true))

        val invalid = client.post("/api/v1/admin/questions/generate-ai") {
            bearerAuth(adminToken())
            contentType(ContentType.Application.Json)
            setBody(validBody(cargoId = "not-a-uuid"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertTrue(invalid.bodyAsText().contains("invalid_uuid"))
    }

    @Test
    fun `route maps missing provider key to 503 and provider invalid output to safe 502`() = testApplication {
        var useCase: AiQuestionGenerationUseCase = FakeUseCase { throw AiProviderNotConfiguredException }
        installAdminAiRoute(object : AiQuestionGenerationUseCase {
            override suspend fun generate(req: GenerateQuestionsReq, cargoNombre: String?, skillNombre: String?) = useCase.generate(req, cargoNombre, skillNombre)
        })

        val unavailable = client.post("/api/v1/admin/questions/generate-ai") {
            bearerAuth(adminToken())
            contentType(ContentType.Application.Json)
            setBody(validBody())
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertTrue(unavailable.bodyAsText().contains("provider_not_configured"))

        useCase = FakeUseCase { throw AiProviderInvalidOutputException("raw-provider-body-secret") }
        val badGateway = client.post("/api/v1/admin/questions/generate-ai") {
            bearerAuth(adminToken())
            contentType(ContentType.Application.Json)
            setBody(validBody())
        }
        assertEquals(HttpStatusCode.BadGateway, badGateway.status)
        assertTrue(badGateway.bodyAsText().contains("provider_invalid_output"))
        assertFalse(badGateway.bodyAsText().contains("raw-provider-body-secret"))
    }

    private fun ApplicationTestBuilder.installAdminAiRoute(useCase: AiQuestionGenerationUseCase) {
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(algorithm).withIssuer("test").withAudience("test-aud").build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { adminAiRoutes(useCase) }
        }
    }

    private fun adminToken(): String = JWT.create()
        .withIssuer("test")
        .withAudience("test-aud")
        .withSubject(UUID.randomUUID().toString())
        .withClaim("role", "admin")
        .sign(algorithm)

    private fun validBody(cargoId: String? = UUID.randomUUID().toString(), cantidad: Int = 1) = """
        {
          "cargo_id": ${cargoId?.let { "\"$it\"" } ?: "null"},
          "nivel": "senior",
          "cantidad": $cantidad,
          "tipo": "abierta_texto",
          "categoria": "tecnica",
          "modelo": "gpt-4o-mini"
        }
    """.trimIndent()
}

private class FakeUseCase(
    private val block: suspend (GenerateQuestionsReq) -> GenerationResult
) : AiQuestionGenerationUseCase {
    override suspend fun generate(req: GenerateQuestionsReq, cargoNombre: String?, skillNombre: String?): GenerationResult = block(req)
}
