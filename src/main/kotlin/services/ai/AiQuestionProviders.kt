package services.ai

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val providerJson = Json { ignoreUnknownKeys = false; isLenient = false }

class OpenAiQuestionProvider(
    private val apiKey: String,
    private val client: HttpClient,
    private val endpoint: String = "https://api.openai.com/v1/chat/completions"
) : AiQuestionProvider {
    override suspend fun generate(request: AiQuestionProviderRequest): AiQuestionProviderResponse {
        if (apiKey.isBlank()) throw AiProviderNotConfiguredException

        val response = postSafely {
            client.post(endpoint) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(openAiPayload(request))
            }
        }
        val body = bodySafely(response)
        if (response.status != HttpStatusCode.OK) throw AiProviderHttpException()

        val parsed = parseEnvelope(body)
        val content = try {
            val choice = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw AiProviderInvalidResponseException()
            when (choice["finish_reason"]?.jsonPrimitive?.content) {
                "length" -> throw AiProviderTruncatedException()
            }
            val message = choice["message"]?.jsonObject ?: throw AiProviderInvalidResponseException()
            val refusal = message["refusal"]?.jsonPrimitive?.content
            if (!refusal.isNullOrBlank()) throw AiProviderRefusalException()
            message["content"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: throw AiProviderInvalidResponseException("provider_missing_content")
        } catch (e: AiQuestionGenerationException) {
            throw e
        } catch (_: Exception) {
            throw AiProviderInvalidResponseException()
        }
        val usage = runCatching { parsed["usage"]?.jsonObject }.getOrNull()

        return AiQuestionProviderResponse(
            rawContent = content,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            actualModel = request.model
        )
    }

    private fun openAiPayload(request: AiQuestionProviderRequest) = buildJsonObject {
        put("model", request.model)
        put("temperature", 0.2)
        put("response_format", buildJsonObject {
            put("type", "json_schema")
            put("json_schema", buildJsonObject {
                put("name", "ai_questions_batch")
                put("strict", true)
                put("schema", request.schema)
            })
        })
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", request.prompt) })
            add(buildJsonObject { put("role", "user"); put("content", request.userInstruction) })
        })
    }
}

class AnthropicQuestionProvider(
    private val apiKey: String,
    private val client: HttpClient,
    private val endpoint: String = "https://api.anthropic.com/v1/messages"
) : AiQuestionProvider {
    override suspend fun generate(request: AiQuestionProviderRequest): AiQuestionProviderResponse {
        if (apiKey.isBlank()) throw AiProviderNotConfiguredException

        val response = postSafely {
            client.post(endpoint) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(anthropicPayload(request))
            }
        }
        val body = bodySafely(response)
        if (response.status != HttpStatusCode.OK) throw AiProviderHttpException()

        val parsed = parseEnvelope(body)
        val text = try {
            when (parsed["stop_reason"]?.jsonPrimitive?.content) {
                "refusal" -> throw AiProviderRefusalException()
                "max_tokens", "model_context_window_exceeded" -> throw AiProviderTruncatedException()
            }
            parsed["content"]?.jsonArray
                ?.firstOrNull { element -> element.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: throw AiProviderInvalidResponseException("provider_missing_content")
        } catch (e: AiQuestionGenerationException) {
            throw e
        } catch (_: Exception) {
            throw AiProviderInvalidResponseException()
        }
        val usage = runCatching { parsed["usage"]?.jsonObject }.getOrNull()

        return AiQuestionProviderResponse(
            rawContent = text,
            promptTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
            actualModel = request.model
        )
    }

    private fun anthropicPayload(request: AiQuestionProviderRequest) = buildJsonObject {
        put("model", request.model)
        put("max_tokens", 8192)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "${request.prompt}\n\n${request.userInstruction}")
            })
        })
        put("output_config", buildJsonObject {
            put("format", buildJsonObject {
                put("type", "json_schema")
                put("schema", request.schema)
            })
        })
    }
}

private suspend fun postSafely(block: suspend () -> HttpResponse): HttpResponse = try {
    block()
} catch (e: AiQuestionGenerationException) {
    throw e
} catch (_: Exception) {
    throw AiProviderHttpException()
}

private suspend fun bodySafely(response: HttpResponse): String = try {
    response.bodyAsText()
} catch (_: Exception) {
    throw AiProviderHttpException()
}

private fun parseEnvelope(body: String) = try {
    providerJson.parseToJsonElement(body).jsonObject
} catch (_: Exception) {
    throw AiProviderInvalidResponseException()
}
