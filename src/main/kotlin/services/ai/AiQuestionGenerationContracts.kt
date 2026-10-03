package services.ai

import data.models.ai.GenerateQuestionsReq
import data.models.ai.LlmQuestion
import services.GenerationResult
import java.util.UUID

enum class AiProviderKind { OPENAI, ANTHROPIC }

data class ValidatedAiQuestionRequest(
    val cargoId: UUID?,
    val skillId: UUID?,
    val nivel: String,
    val cantidad: Int,
    val tipo: String,
    val categoria: String,
    val requestedModel: String,
    val actualModel: String,
    val providerKind: AiProviderKind
)

data class AiQuestionProviderRequest(
    val prompt: String,
    val model: String,
    val schema: kotlinx.serialization.json.JsonObject,
    val userInstruction: String
)

data class AiQuestionProviderResponse(
    val rawContent: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val actualModel: String
)

interface AiQuestionProvider {
    suspend fun generate(request: AiQuestionProviderRequest): AiQuestionProviderResponse
}

interface AiQuestionGenerationUseCase {
    suspend fun generate(
        req: GenerateQuestionsReq,
        cargoNombre: String? = null,
        skillNombre: String? = null
    ): GenerationResult
}

data class PersistableAiQuestion(
    val question: LlmQuestion,
    val tokensInput: Int?,
    val tokensOutput: Int?,
    val costoUsd: Double?
)

data class AiQuestionBatchPersistenceRequest(
    val cargoId: UUID?,
    val skillId: UUID?,
    val nivel: String,
    val categoria: String,
    val modelo: String,
    val promptEnviado: String,
    val respuestaRaw: String,
    val questions: List<PersistableAiQuestion>
)

data class AiFailedTrace(
    val cargoId: UUID?,
    val skillId: UUID?,
    val nivel: String,
    val modelo: String,
    val promptEnviado: String,
    val respuestaRaw: String?,
    val tokensInput: Int?,
    val tokensOutput: Int?
)

data class InsertedQuestion(
    val preguntaId: UUID,
    val generacionId: UUID
)

interface AiQuestionPersistence {
    suspend fun insertSuccessfulBatch(request: AiQuestionBatchPersistenceRequest): List<InsertedQuestion>
    suspend fun insertFailedTrace(trace: AiFailedTrace)
}

sealed class AiQuestionGenerationException(val publicCode: String) : RuntimeException(publicCode)

class AiRequestValidationException(publicCode: String) : AiQuestionGenerationException(publicCode)

data object AiProviderNotConfiguredException : AiQuestionGenerationException("provider_not_configured")
class AiProviderHttpException : AiQuestionGenerationException("provider_http_error")
class AiProviderInvalidResponseException(publicCode: String = "provider_invalid_response") : AiQuestionGenerationException(publicCode)
class AiProviderRefusalException : AiQuestionGenerationException("provider_refusal")
class AiProviderTruncatedException : AiQuestionGenerationException("provider_truncated")
class AiProviderInvalidOutputException(@Suppress("UNUSED_PARAMETER") detail: String? = null) :
    AiQuestionGenerationException("provider_invalid_output")
class AiPersistenceException : AiQuestionGenerationException("persistence_error")

object AiQuestionRequestValidator {
    private val nivelesValidos = setOf("junior", "semisenior", "senior")
    private val tiposValidos = setOf("abierta_texto", "opcion_multiple", "simulacion_video")
    private val categoriasValidas = setOf("tecnica", "blanda")
    private val modelosValidos = setOf("gpt-4o-mini", "claude-haiku-4-5", "claude-3-5-haiku")
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

    fun validate(req: GenerateQuestionsReq): ValidatedAiQuestionRequest {
        if (req.nivel !in nivelesValidos) throw AiRequestValidationException("nivel_invalido")
        if (req.tipo !in tiposValidos) throw AiRequestValidationException("tipo_invalido")
        if (req.categoria !in categoriasValidas) throw AiRequestValidationException("categoria_invalida")
        if (req.modelo !in modelosValidos) throw AiRequestValidationException("modelo_invalido")
        if (req.cantidad !in 1..10) throw AiRequestValidationException("cantidad_invalida")
        if (req.cargoId == null && req.skillId == null) throw AiRequestValidationException("contexto_requerido")

        val cargoId = req.cargoId?.parseStrictUuid()
        val skillId = req.skillId?.parseStrictUuid()
        val actualModel = if (req.modelo == "claude-3-5-haiku") "claude-haiku-4-5" else req.modelo
        val provider = if (actualModel.startsWith("gpt")) AiProviderKind.OPENAI else AiProviderKind.ANTHROPIC

        return ValidatedAiQuestionRequest(
            cargoId = cargoId,
            skillId = skillId,
            nivel = req.nivel,
            cantidad = req.cantidad,
            tipo = req.tipo,
            categoria = req.categoria,
            requestedModel = req.modelo,
            actualModel = actualModel,
            providerKind = provider
        )
    }

    private fun String.parseStrictUuid(): UUID {
        if (!uuidRegex.matches(this)) throw AiRequestValidationException("invalid_uuid")
        return try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            throw AiRequestValidationException("invalid_uuid")
        }
    }
}
