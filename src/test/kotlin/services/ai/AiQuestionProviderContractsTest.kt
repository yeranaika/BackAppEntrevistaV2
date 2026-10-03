package services.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiQuestionProviderContractsTest {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    @Test
    fun `openai chat completions request uses strict json schema and reads usage`() = runBlocking {
        var capturedBody = ""
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler { request ->
                    capturedBody = (request.body as TextContent).text
                    respond(
                        content = """
                            {
                              "choices": [{
                                "finish_reason": "stop",
                                "message": {"content": "{\"preguntas\":[]}"}
                              }],
                              "usage": {"prompt_tokens": 12, "completion_tokens": 34}
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }

        val provider = OpenAiQuestionProvider(apiKey = "test-key", client = client)
        val response = provider.generate(providerRequest(cantidad = 10, tipo = "opcion_multiple"))

        val body = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("gpt-4o-mini", body["model"]?.jsonPrimitive?.content)
        val responseFormat = body["response_format"]!!.jsonObject
        assertEquals("json_schema", responseFormat["type"]?.jsonPrimitive?.content)
        val jsonSchema = responseFormat["json_schema"]!!.jsonObject
        assertEquals("ai_questions_batch", jsonSchema["name"]?.jsonPrimitive?.content)
        assertEquals("true", jsonSchema["strict"]?.jsonPrimitive.toString())
        val schema = jsonSchema["schema"]!!.jsonObject
        assertEquals("false", schema["additionalProperties"]?.jsonPrimitive.toString())
        val preguntas = schema["properties"]!!.jsonObject["preguntas"]!!.jsonObject
        assertEquals("10", preguntas["minItems"]?.jsonPrimitive.toString())
        assertEquals("10", preguntas["maxItems"]?.jsonPrimitive.toString())
        val opciones = preguntas["items"]!!.jsonObject["properties"]!!.jsonObject["opciones"]!!.jsonObject
        assertEquals("4", opciones["minItems"]?.jsonPrimitive.toString())
        assertEquals("4", opciones["maxItems"]?.jsonPrimitive.toString())

        assertEquals("{\"preguntas\":[]}", response.rawContent)
        assertEquals(12, response.promptTokens)
        assertEquals(34, response.completionTokens)
        assertEquals("gpt-4o-mini", response.actualModel)
    }

    @Test
    fun `anthropic messages request uses output_config json schema and reads usage`() = runBlocking {
        var capturedBody = ""
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler { request ->
                    capturedBody = (request.body as TextContent).text
                    respond(
                        content = """
                            {
                              "content": [{"type": "text", "text": "{\"preguntas\":[]}"}],
                              "stop_reason": "end_turn",
                              "usage": {"input_tokens": 7, "output_tokens": 9}
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }

        val provider = AnthropicQuestionProvider(apiKey = "test-key", client = client)
        val response = provider.generate(providerRequest(cantidad = 3, tipo = "abierta_texto", model = "claude-haiku-4-5"))

        val body = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("claude-haiku-4-5", body["model"]?.jsonPrimitive?.content)
        assertEquals("8192", body["max_tokens"]?.jsonPrimitive.toString())
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]?.jsonPrimitive?.content)
        val schema = format["schema"]!!.jsonObject
        assertEquals("false", schema["additionalProperties"]?.jsonPrimitive.toString())
        val preguntas = schema["properties"]!!.jsonObject["preguntas"]!!.jsonObject
        assertEquals("3", preguntas["minItems"]?.jsonPrimitive.toString())
        assertEquals("3", preguntas["maxItems"]?.jsonPrimitive.toString())
        val opciones = preguntas["items"]!!.jsonObject["properties"]!!.jsonObject["opciones"]!!.jsonObject
        assertNotNull(opciones["type"])
        assertTrue(opciones.toString().contains("null"), "abierta_texto must require null options")

        assertEquals("{\"preguntas\":[]}", response.rawContent)
        assertEquals(7, response.promptTokens)
        assertEquals(9, response.completionTokens)
        assertEquals("claude-haiku-4-5", response.actualModel)
    }

    @Test
    fun `provider maps refusals and truncation to typed safe errors`() {
        runBlocking {
        val openAiLengthClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler {
                    respond(
                        """{"choices":[{"finish_reason":"length","message":{"content":"{}"}}],"usage":{}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
        kotlin.test.assertFailsWith<AiProviderTruncatedException> {
            OpenAiQuestionProvider("key", openAiLengthClient).generate(providerRequest())
        }

        val anthropicRefusalClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler {
                    respond(
                        """{"content":[{"type":"text","text":"no"}],"stop_reason":"refusal","usage":{}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
        kotlin.test.assertFailsWith<AiProviderRefusalException> {
            AnthropicQuestionProvider("key", anthropicRefusalClient).generate(providerRequest(model = "claude-haiku-4-5"))
        }
        }
    }

    @Test
    fun `provider transport exceptions map to safe http exception without leaked messages`() {
        runBlocking {
            val openAiClient = throwingClient("openai secret socket failure")
            val openAiError = kotlin.test.assertFailsWith<AiProviderHttpException> {
                OpenAiQuestionProvider("key", openAiClient).generate(providerRequest())
            }
            assertEquals("provider_http_error", openAiError.message)
            assertTrue(openAiError.message?.contains("secret") != true)

            val anthropicClient = throwingClient("anthropic secret socket failure")
            val anthropicError = kotlin.test.assertFailsWith<AiProviderHttpException> {
                AnthropicQuestionProvider("key", anthropicClient).generate(providerRequest(model = "claude-haiku-4-5"))
            }
            assertEquals("provider_http_error", anthropicError.message)
            assertTrue(anthropicError.message?.contains("secret") != true)
        }
    }

    @Test
    fun `provider malformed and wrong shaped 200 envelopes map to safe invalid response`() {
        runBlocking {
            val malformedOpenAi = kotlin.test.assertFailsWith<AiProviderInvalidResponseException> {
                OpenAiQuestionProvider("key", fixedClient("not json with raw-secret")).generate(providerRequest())
            }
            assertEquals("provider_invalid_response", malformedOpenAi.message)
            assertTrue(malformedOpenAi.message?.contains("raw-secret") != true)

            val wrongOpenAi = kotlin.test.assertFailsWith<AiProviderInvalidResponseException> {
                OpenAiQuestionProvider("key", fixedClient("{\"choices\":[]}")) .generate(providerRequest())
            }
            assertEquals("provider_invalid_response", wrongOpenAi.message)

            val malformedAnthropic = kotlin.test.assertFailsWith<AiProviderInvalidResponseException> {
                AnthropicQuestionProvider("key", fixedClient("not json with anthropic-secret")).generate(providerRequest(model = "claude-haiku-4-5"))
            }
            assertEquals("provider_invalid_response", malformedAnthropic.message)
            assertTrue(malformedAnthropic.message?.contains("anthropic-secret") != true)

            val wrongAnthropic = kotlin.test.assertFailsWith<AiProviderInvalidResponseException> {
                AnthropicQuestionProvider("key", fixedClient("{\"content\":[]}")) .generate(providerRequest(model = "claude-haiku-4-5"))
            }
            assertEquals("provider_missing_content", wrongAnthropic.message)
        }
    }

    private fun providerRequest(
        cantidad: Int = 1,
        tipo: String = "abierta_texto",
        model: String = "gpt-4o-mini"
    ) = AiQuestionProviderRequest(
        prompt = "prompt de prueba",
        model = model,
        schema = AiQuestionJsonSchema.build(cantidad = cantidad, tipo = tipo),
        userInstruction = "Genera las preguntas ahora."
    )

    private fun throwingClient(message: String) = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(json) }
        engine { addHandler { throw IOException(message) } }
    }

    private fun fixedClient(body: String) = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(json) }
        engine {
            addHandler {
                respond(
                    body,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
    }
}
