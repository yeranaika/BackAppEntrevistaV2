package ESQUEMAS

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
    @SerialName("tiempo_esperado_seg") val tiempoEsperadoSeg: Int = 120
)

@Serializable
data class OpcionLlm(
    val texto: String,
    @SerialName("es_correcta") val esCorrecta: Boolean,
    val explicacion: String = ""
)

/** JSON Schema que se envía al proveedor (structured outputs) para un lote de [cantidad] preguntas. */
object EsquemaJsonLoteLlm {
    fun construir(cantidad: Int, tipo: TipoPregunta): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("preguntas"),
        "properties" to objeto(
            "preguntas" to objeto(
                "type" to texto("array"),
                "minItems" to numero(cantidad),
                "maxItems" to numero(cantidad),
                "items" to pregunta(tipo)
            )
        )
    )

    private fun pregunta(tipo: TipoPregunta): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("enunciado", "tipo_pregunta", "respuesta_ideal", "rubrica_evaluacion", "opciones"),
        "properties" to objeto(
            "enunciado" to objeto("type" to texto("string"), "minLength" to numero(1)),
            "tipo_pregunta" to objeto("type" to texto("string"), "const" to texto(tipo.valorBd)),
            "respuesta_ideal" to objeto("type" to texto("string"), "minLength" to numero(1)),
            "rubrica_evaluacion" to rubrica(),
            "opciones" to opciones(tipo)
        )
    )

    private fun rubrica(): JsonObject = objeto(
        "type" to texto("object"),
        "additionalProperties" to logico(false),
        "required" to lista("metodo", "criterios", "palabras_clave", "tiempo_esperado_seg"),
        "properties" to objeto(
            "metodo" to objeto("type" to texto("string"), "const" to texto("STAR")),
            "criterios" to objeto(
                "type" to texto("array"),
                "minItems" to numero(CRITERIOS_MINIMOS_RUBRICA),
                "items" to objeto("type" to texto("string"), "minLength" to numero(1))
            ),
            "palabras_clave" to objeto(
                "type" to texto("array"),
                "minItems" to numero(PALABRAS_CLAVE_MINIMAS),
                "items" to objeto("type" to texto("string"), "minLength" to numero(3))
            ),
            "tiempo_esperado_seg" to objeto("type" to texto("integer"), "minimum" to numero(1))
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

    private fun objeto(vararg pares: Pair<String, JsonElement>): JsonObject =
        buildJsonObject { pares.forEach { (clave, valor) -> put(clave, valor) } }

    private fun lista(vararg valores: String): JsonArray = buildJsonArray { valores.forEach { add(texto(it)) } }
    private fun texto(valor: String) = JsonPrimitive(valor)
    private fun logico(valor: Boolean) = JsonPrimitive(valor)
    private fun numero(valor: Int) = JsonPrimitive(valor)
}
