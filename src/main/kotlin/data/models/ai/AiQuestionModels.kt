package data.models.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request ────────────────────────────────────────────────────────────────

@Serializable
data class GenerateQuestionsReq(
    @SerialName("cargo_id")  val cargoId: String? = null,
    @SerialName("skill_id")  val skillId: String? = null,
    val nivel: String,                       // junior | semisenior | senior
    val cantidad: Int = 5,                   // 1-10
    val tipo: String = "abierta_texto",      // abierta_texto | opcion_multiple
    val categoria: String = "tecnica",       // tecnica | blanda
    val modelo: String = "gpt-4o-mini"       // gpt-4o-mini | claude-3-5-haiku
)

// ─── Estructura JSON exacta que el LLM debe devolver ─────────────────────────
//
// {
//   "enunciado": "...",
//   "tipo_pregunta": "abierta_texto",
//   "respuesta_ideal": "Respuesta modelo completa...",
//   "rubrica_evaluacion": {
//     "metodo": "STAR",
//     "criterios": ["...", "..."],
//     "palabras_clave": ["...", "..."],
//     "tiempo_esperado_seg": 120
//   },
//   "opciones": [{ "texto": "...", "es_correcta": true, "explicacion": "..." }]
// }

@Serializable
data class LlmQuestion(
    val enunciado: String,
    @SerialName("tipo_pregunta")      val tipoPregunta: String,
    @SerialName("respuesta_ideal")    val respuestaIdeal: String,
    @SerialName("rubrica_evaluacion") val rubricaEvaluacion: RubricaEvaluacion,
    val opciones: List<OpcionPregunta>? = null   // solo para opcion_multiple
)

@Serializable
data class RubricaEvaluacion(
    val metodo: String = "STAR",
    val criterios: List<String>,
    @SerialName("palabras_clave")       val palabrasClave: List<String> = emptyList(),
    @SerialName("tiempo_esperado_seg")  val tiempoEsperadoSeg: Int = 120
)

@Serializable
data class OpcionPregunta(
    val texto: String,
    @SerialName("es_correcta") val esCorrecta: Boolean,
    val explicacion: String = ""
)

@Serializable
data class LlmBatchResponse(
    val preguntas: List<LlmQuestion>
)

// ─── Response ────────────────────────────────────────────────────────────────

@Serializable
data class GenerateQuestionsRes(
    @SerialName("preguntas_generadas") val preguntasGeneradas: Int,
    @SerialName("preguntas_con_error") val preguntasConError: Int,
    val preguntas: List<PreguntaGeneradaRes>
)

@Serializable
data class PreguntaGeneradaRes(
    @SerialName("pregunta_id")    val preguntaId: String,
    @SerialName("generacion_id")  val generacionId: String,
    val enunciado: String,
    val estado: String,
    @SerialName("tokens_input")   val tokensInput: Int?,
    @SerialName("tokens_output")  val tokensOutput: Int?,
    @SerialName("costo_usd")      val costoUsd: Double?
)

@Serializable
data class AiErrorRes(val error: String, val detalle: String? = null)
