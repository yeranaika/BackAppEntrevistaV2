package data.models.sync

import kotlinx.serialization.Serializable

@Serializable
data class FreemiumEvaluateRequest(
    val preguntaId: String? = null,
    val userText: String,
    val idealText: String,
    val expectedKeywords: List<String> = emptyList()
)

@Serializable
data class FreemiumEvaluateResponse(
    val score: Double,
    val keywordMatchPercentage: Double,
    val similarityPercentage: Double,
    val matchedKeywords: List<String>,
    val missingKeywords: List<String>,
    val feedbackSummary: String
)

@Serializable
data class SyncAttemptBatchRequest(
    val attempts: List<SyncAttemptItem>
)

@Serializable
data class SyncAttemptItem(
    val localAttemptId: String,
    val skillId: String,
    val cargoId: String? = null,
    val modo: String, // "opcion_multiple" | "abierta_texto"
    val categoria: String = "tecnica",
    val nivelPreguntas: String = "junior",
    val puntajeTotal: Double,
    val fechaCreacionIso: String? = null,
    val respuestas: List<SyncAnswerItem> = emptyList()
)

@Serializable
data class SyncAnswerItem(
    val preguntaId: String,
    val enunciado: String,
    val opcionElegidaId: String? = null,
    val respuestaTexto: String? = null,
    val esCorrecta: Boolean = false,
    val puntaje: Double = 0.0,
    val feedbackTexto: String? = null,
    val tiempoRespuestaMs: Int? = null,
    val orden: Int = 1
)

@Serializable
data class SyncResponse(
    val success: Boolean,
    val syncedCount: Int,
    val mappings: List<IdMapping>
)

@Serializable
data class IdMapping(
    val localAttemptId: String,
    val serverAttemptId: String
)

