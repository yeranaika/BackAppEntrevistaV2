package services

import data.models.ai.GenerateQuestionsReq
import data.models.ai.LlmQuestion
import data.models.ai.PreguntaGeneradaRes
import data.repository.ai.AiQuestionRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import services.ai.AiFailedTrace
import services.ai.AiQuestionBatchValidator
import services.ai.AiPersistenceException
import services.ai.AiProviderInvalidOutputException
import services.ai.AiProviderKind
import services.ai.AiQuestionBatchPersistenceRequest
import services.ai.AiQuestionGenerationException
import services.ai.AiQuestionGenerationUseCase
import services.ai.AiQuestionJsonSchema
import services.ai.AiQuestionPersistence
import services.ai.AiQuestionPromptBuilder
import services.ai.AiQuestionProvider
import services.ai.AiQuestionProviderRequest
import services.ai.AiQuestionProviderResponse
import services.ai.AiQuestionRequestValidator
import services.ai.AnthropicQuestionProvider
import services.ai.OpenAiQuestionProvider
import services.ai.PersistableAiQuestion
import services.ai.ValidatedAiQuestionRequest

data class GenerationResult(
    val inserted: List<PreguntaGeneradaRes>,
    val errores: Int
)

class AiQuestionGeneratorService(
    openAiKey: String,
    anthropicKey: String = "",
    private val persistence: AiQuestionPersistence = AiQuestionRepository(),
    private val httpClient: HttpClient = defaultHttpClient(),
    private val providers: Map<AiProviderKind, AiQuestionProvider> = mapOf(
        AiProviderKind.OPENAI to OpenAiQuestionProvider(openAiKey, httpClient),
        AiProviderKind.ANTHROPIC to AnthropicQuestionProvider(anthropicKey, httpClient)
    )
) : AiQuestionGenerationUseCase {
    private val costoPorToken = mapOf(
        "gpt-4o-mini" to Pair(0.00000015, 0.00000060),
        "claude-haiku-4-5" to Pair(0.000001, 0.000005)
    )

    override suspend fun generate(
        req: GenerateQuestionsReq,
        cargoNombre: String?,
        skillNombre: String?
    ): GenerationResult {
        val validated = AiQuestionRequestValidator.validate(req)
        val prompt = AiQuestionPromptBuilder.build(validated, cargoNombre, skillNombre)
        val schema = AiQuestionJsonSchema.build(validated.cantidad, validated.tipo)
        val provider = providers[validated.providerKind] ?: throw services.ai.AiProviderNotConfiguredException

        val providerResponse = try {
            provider.generate(
                AiQuestionProviderRequest(
                    prompt = prompt,
                    model = validated.actualModel,
                    schema = schema,
                    userInstruction = "Genera exactamente ${validated.cantidad} preguntas ahora. Responde solo el objeto JSON solicitado."
                )
            )
        } catch (e: AiQuestionGenerationException) {
            if (e !is services.ai.AiProviderNotConfiguredException) {
                recordFailure(failedTrace(validated, prompt, null, null, null))
            }
            throw e
        }

        val questions = try {
            AiQuestionBatchValidator.parseAndValidate(providerResponse.rawContent, validated)
        } catch (_: SerializationException) {
            recordFailure(failedTrace(validated, prompt, providerResponse.rawContent, providerResponse.promptTokens, providerResponse.completionTokens))
            throw AiProviderInvalidOutputException()
        } catch (_: IllegalArgumentException) {
            recordFailure(failedTrace(validated, prompt, providerResponse.rawContent, providerResponse.promptTokens, providerResponse.completionTokens))
            throw AiProviderInvalidOutputException()
        }

        val persistable = withTokenShares(questions, validated.actualModel, providerResponse)
        val inserted = try {
            persistence.insertSuccessfulBatch(
                AiQuestionBatchPersistenceRequest(
                    cargoId = validated.cargoId,
                    skillId = validated.skillId,
                    nivel = validated.nivel,
                    categoria = validated.categoria,
                    modelo = validated.actualModel,
                    promptEnviado = prompt,
                    respuestaRaw = providerResponse.rawContent,
                    questions = persistable
                )
            )
        } catch (_: Exception) {
            throw AiPersistenceException()
        }

        if (inserted.size != validated.cantidad) throw AiPersistenceException()

        return GenerationResult(
            inserted = inserted.mapIndexed { index, ids ->
                val question = questions[index]
                val share = persistable[index]
                PreguntaGeneradaRes(
                    preguntaId = ids.preguntaId.toString(),
                    generacionId = ids.generacionId.toString(),
                    enunciado = question.enunciado,
                    estado = "pendiente",
                    tokensInput = share.tokensInput,
                    tokensOutput = share.tokensOutput,
                    costoUsd = share.costoUsd
                )
            },
            errores = 0
        )
    }

    private fun withTokenShares(
        questions: List<LlmQuestion>,
        model: String,
        response: AiQuestionProviderResponse
    ): List<PersistableAiQuestion> {
        val size = questions.size
        val inputShares = distribute(response.promptTokens, size)
        val outputShares = distribute(response.completionTokens, size)
        val (precioIn, precioOut) = costoPorToken[model] ?: Pair(0.0, 0.0)
        return questions.mapIndexed { index, question ->
            PersistableAiQuestion(
                question = question,
                tokensInput = inputShares[index],
                tokensOutput = outputShares[index],
                costoUsd = ((inputShares[index] ?: 0) * precioIn) + ((outputShares[index] ?: 0) * precioOut)
            )
        }
    }

    private fun distribute(total: Int?, buckets: Int): List<Int?> {
        if (total == null) return List(buckets) { null }
        val base = total / buckets
        val remainder = total % buckets
        return List(buckets) { index -> base + if (index < remainder) 1 else 0 }
    }

    private fun failedTrace(
        req: ValidatedAiQuestionRequest,
        prompt: String,
        raw: String?,
        inputTokens: Int?,
        outputTokens: Int?
    ) = AiFailedTrace(
        cargoId = req.cargoId,
        skillId = req.skillId,
        nivel = req.nivel,
        modelo = req.actualModel,
        promptEnviado = prompt,
        respuestaRaw = raw,
        tokensInput = inputTokens,
        tokensOutput = outputTokens
    )

    private suspend fun recordFailure(trace: AiFailedTrace) {
        try {
            persistence.insertFailedTrace(trace)
        } catch (_: Exception) {
            throw AiPersistenceException()
        }
    }

    fun close() = httpClient.close()

    companion object {
        private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = false; isLenient = false })
            }
            install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        }
    }
}
