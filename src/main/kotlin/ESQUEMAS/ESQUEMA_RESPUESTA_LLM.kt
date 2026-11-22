package ESQUEMAS

import MODELOS.CategoriaHabilidad
import MODELOS.FeedbackGenerico
import MODELOS.LARGO_MAXIMO_FEEDBACK_GENERICO
import MODELOS.NivelFeedbackGenerico
import MODELOS.TipoPregunta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

// Lo que el LLM debe devolver. Se exige con un JSON Schema estricto y se vuelve a validar al recibirlo.

const val OPCIONES_POR_PREGUNTA_IA = 4
const val CRITERIOS_MINIMOS_RUBRICA = 4
const val PALABRAS_CLAVE_MINIMAS = 3

@Serializable
data class LoteLlm(
    val preguntas: List<PreguntaLlm>
)

@Serializable
data class PreguntaLlm(
    val enunciado: String,
    @SerialName("tipo_pregunta") val tipoPregunta: String,
    @SerialName("respuesta_ideal") val respuestaIdeal: String,
    @SerialName("rubrica_evaluacion") val rubrica: RubricaLlm,
    /** Solo en opcion_multiple; en los demás tipos debe venir null. */
    val opciones: List<OpcionLlm>? = null
)

@Serializable
data class RubricaLlm(
    val metodo: String = "STAR",
    val criterios: List<String>,
    @SerialName("palabras_clave") val palabrasClave: List<String> = emptyList(),
    @SerialName("tiempo_esperado_seg") val tiempoEsperadoSeg: Int = 120,
    /** Otras formas correctas de responder con otras palabras: el feedback gratuito compara con la mejor. */
    @SerialName("respuestas_alternativas") val respuestasAlternativas: List<String> = emptyList(),
    /** Grupos de términos equivalentes, p. ej. ["deadlock", "interbloqueo"]: cuentan como la misma palabra. */
    val sinonimos: List<List<String>> = emptyList(),
    /** Un texto para respuesta correcta, a medias y mala (D36): el feedback gratuito lo muestra sin gastar tokens. */
    @SerialName("feedback_generico") val feedbackGenerico: FeedbackGenerico? = null
)

@Serializable
data class OpcionLlm(
    val texto: String,
    @SerialName("es_correcta") val esCorrecta: Boolean,
    val explicacion: String = ""
)

/** JSON Schema que se envía al proveedor (structured outputs) para un lote de [cantidad] preguntas. */
object EsquemaJsonLoteLlm {
    fun construir(cantidad: Int, tipo: TipoPregunta, categoria: CategoriaHabilidad = CategoriaHabilidad.BLANDA): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("preguntas"),
        "properties" to objeto(
            "preguntas" to objeto(
                "type" to texto("array"),
                "minItems" to numero(cantidad),
                "maxItems" to numero(cantidad),
                "items" to pregunta(tipo, categoria)
            )
        )
    )

    private fun pregunta(tipo: TipoPregunta, categoria: CategoriaHabilidad): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("enunciado", "tipo_pregunta", "respuesta_ideal", "rubrica_evaluacion", "opciones"),
        "properties" to objeto(
            "enunciado" to objeto("type" to texto("string"), "minLength" to numero(1)),
            "tipo_pregunta" to objeto("type" to texto("string"), "const" to texto(tipo.valorBd)),
            "respuesta_ideal" to objeto("type" to texto("string"), "minLength" to numero(1)),
            "rubrica_evaluacion" to rubrica(categoria),
            "opciones" to opciones(tipo)
        )
    )

    private fun rubrica(categoria: CategoriaHabilidad): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("metodo", "criterios", "palabras_clave", "tiempo_esperado_seg", "feedback_generico"),
        "properties" to objeto(
            "metodo" to objeto(
                "type" to texto("string"),
                "const" to texto(if (categoria == CategoriaHabilidad.BLANDA) "STAR" else "TECNICA")
            ),
            "criterios" to objeto(
                "type" to texto("array"),
                "minItems" to numero(CRITERIOS_MINIMOS_RUBRICA),
                "items" to objeto("type" to texto("string"), "minLength" to numero(1))
            ),
            "palabras_clave" to objeto(
                "type" to texto("array"),
                "minItems" to numero(PALABRAS_CLAVE_MINIMAS),
                "items" to objeto("type" to texto("string"), "minLength" to numero(2))
            ),
            "tiempo_esperado_seg" to objeto("type" to texto("integer"), "minimum" to numero(1)),
            "respuestas_alternativas" to objeto(
                "type" to texto("array"),
                "items" to objeto("type" to texto("string"), "minLength" to numero(1))
            ),
            "sinonimos" to objeto(
                "type" to texto("array"),
                "items" to objeto(
                    "type" to texto("array"),
                    "items" to objeto("type" to texto("string"), "minLength" to numero(1))
                )
            ),
            "feedback_generico" to feedbackGenerico()
        )
    )

    /** Los tres textos de feedback genérico, obligatorios (D36). */
    fun feedbackGenerico(): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista(*NivelFeedbackGenerico.entries.map { it.clave }.toTypedArray()),
        "properties" to objeto(
            *NivelFeedbackGenerico.entries.map {
                it.clave to objeto(
                    "type" to texto("string"),
                    "minLength" to numero(1),
                    "maxLength" to numero(LARGO_MAXIMO_FEEDBACK_GENERICO)
                )
            }.toTypedArray()
        )
    )

    private fun opciones(tipo: TipoPregunta): JsonObject =
        if (tipo != TipoPregunta.OPCION_MULTIPLE) {
            objeto("type" to texto("null"))
        } else {
            objeto(
                "type" to texto("array"),
                "minItems" to numero(OPCIONES_POR_PREGUNTA_IA),
                "maxItems" to numero(OPCIONES_POR_PREGUNTA_IA),
                "items" to objeto(
                    "type" to texto("object"),
                    "additionalProperties" to logico(false),
                    "required" to lista("texto", "es_correcta", "explicacion"),
                    "properties" to objeto(
                        "texto" to objeto("type" to texto("string"), "minLength" to numero(1)),
                        "es_correcta" to objeto("type" to texto("boolean")),
                        "explicacion" to objeto("type" to texto("string"), "minLength" to numero(1))
                    )
                )
            )
        }

    internal fun objeto(vararg pares: Pair<String, JsonElement>): JsonObject =
        buildJsonObject { pares.forEach { (clave, valor) -> put(clave, valor) } }

    internal fun lista(vararg valores: String): JsonArray = buildJsonArray { valores.forEach { add(texto(it)) } }
    internal fun texto(valor: String) = JsonPrimitive(valor)
    internal fun logico(valor: Boolean) = JsonPrimitive(valor)
    internal fun numero(valor: Int) = JsonPrimitive(valor)
}

// ---------- Completar el feedback genérico de preguntas que ya están en el banco (D36) ----------

/** Lo que devuelve el LLM al completar feedback: uno por pregunta, con el número que se le dio en el prompt. */
@Serializable
data class LoteFeedbackGenericoLlm(
    val feedbacks: List<FeedbackGenericoPreguntaLlm>
)

@Serializable
data class FeedbackGenericoPreguntaLlm(
    val numero: Int,
    val correcto: String,
    val medio: String,
    val malo: String
) {
    fun aFeedback() = FeedbackGenerico(correcto, medio, malo).limpio()
}

/** JSON Schema del lote de feedback genérico para [cantidad] preguntas. */
object EsquemaJsonFeedbackGenericoLlm {
    fun construir(cantidad: Int): JsonObject = with(EsquemaJsonLoteLlm) {
        val textos = NivelFeedbackGenerico.entries.map {
            it.clave to objeto(
                "type" to texto("string"),
                "minLength" to numero(1),
                "maxLength" to numero(LARGO_MAXIMO_FEEDBACK_GENERICO)
            )
        }
        objeto(
            "type" to texto("object"),
            "additionalProperties" to logico(false),
            "required" to lista("feedbacks"),
            "properties" to objeto(
                "feedbacks" to objeto(
                    "type" to texto("array"),
                    "minItems" to numero(cantidad),
                    "maxItems" to numero(cantidad),
                    "items" to objeto(
                        "type" to texto("object"),
                        "additionalProperties" to logico(false),
                        "required" to lista("numero", *NivelFeedbackGenerico.entries.map { it.clave }.toTypedArray()),
                        "properties" to objeto(
                            "numero" to objeto("type" to texto("integer"), "minimum" to numero(1)),
                            *textos.toTypedArray()
                        )
                    )
                )
            )
        )
    }
}
