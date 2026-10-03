package services.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AiQuestionJsonSchema {
    fun build(cantidad: Int, tipo: String): JsonObject = obj(
        "type" to str("object"),
        "additionalProperties" to bool(false),
        "required" to arr("preguntas"),
        "properties" to obj(
            "preguntas" to obj(
                "type" to str("array"),
                "minItems" to num(cantidad),
                "maxItems" to num(cantidad),
                "items" to questionSchema(tipo)
            )
        )
    )

    private fun questionSchema(tipo: String): JsonObject = obj(
        "type" to str("object"),
        "additionalProperties" to bool(false),
        "required" to arr("enunciado", "tipo_pregunta", "respuesta_ideal", "rubrica_evaluacion", "opciones"),
        "properties" to obj(
            "enunciado" to obj("type" to str("string"), "minLength" to num(1)),
            "tipo_pregunta" to obj("type" to str("string"), "const" to str(tipo)),
            "respuesta_ideal" to obj("type" to str("string"), "minLength" to num(1)),
            "rubrica_evaluacion" to rubricSchema(),
            "opciones" to optionsSchema(tipo)
        )
    )

    private fun rubricSchema(): JsonObject = obj(
        "type" to str("object"),
        "additionalProperties" to bool(false),
        "required" to arr("metodo", "criterios", "palabras_clave", "tiempo_esperado_seg"),
        "properties" to obj(
            "metodo" to obj("type" to str("string"), "const" to str("STAR")),
            "criterios" to obj(
                "type" to str("array"),
                "minItems" to num(4),
                "items" to obj("type" to str("string"), "minLength" to num(1))
            ),
            "palabras_clave" to obj(
                "type" to str("array"),
                "minItems" to num(3),
                "items" to obj("type" to str("string"), "minLength" to num(3))
            ),
            "tiempo_esperado_seg" to obj("type" to str("integer"), "minimum" to num(1))
        )
    )

    private fun optionsSchema(tipo: String): JsonObject = if (tipo == "opcion_multiple") {
        obj(
            "type" to str("array"),
            "minItems" to num(4),
            "maxItems" to num(4),
            "items" to obj(
                "type" to str("object"),
                "additionalProperties" to bool(false),
                "required" to arr("texto", "es_correcta", "explicacion"),
                "properties" to obj(
                    "texto" to obj("type" to str("string"), "minLength" to num(1)),
                    "es_correcta" to obj("type" to str("boolean")),
                    "explicacion" to obj("type" to str("string"), "minLength" to num(1))
                )
            )
        )
    } else {
        obj("type" to str("null"))
    }

    private fun obj(vararg entries: Pair<String, kotlinx.serialization.json.JsonElement>): JsonObject = buildJsonObject {
        entries.forEach { (key, value) -> put(key, value) }
    }

    private fun arr(vararg values: String): JsonArray = buildJsonArray { values.forEach { add(str(it)) } }
    private fun str(value: String) = JsonPrimitive(value)
    private fun bool(value: Boolean) = JsonPrimitive(value)
    private fun num(value: Int) = JsonPrimitive(value)
}
