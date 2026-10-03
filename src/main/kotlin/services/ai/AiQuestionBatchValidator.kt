package services.ai

import data.models.ai.LlmBatchResponse
import data.models.ai.LlmQuestion
import kotlinx.serialization.json.Json

object AiQuestionBatchValidator {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
    }

    fun parseAndValidate(rawJson: String, req: ValidatedAiQuestionRequest): List<LlmQuestion> {
        val questions = strictJson.decodeFromString<LlmBatchResponse>(rawJson).preguntas
        require(questions.size == req.cantidad) { "wrong_count" }
        require(questions.map { normalizeIdentity(it.enunciado) }.toSet().size == questions.size) { "duplicate_enunciado" }
        questions.forEach { question -> validateQuestion(question, req.tipo) }
        return questions
    }

    private fun validateQuestion(question: LlmQuestion, expectedTipo: String) {
        require(question.enunciado.isNotBlank()) { "blank_enunciado" }
        require(question.tipoPregunta == expectedTipo) { "wrong_tipo" }
        require(question.respuestaIdeal.isNotBlank() && question.respuestaIdeal.length >= 40) { "incomplete_respuesta" }
        require(sentenceLikeSegments(question.respuestaIdeal) >= 3) { "respuesta_needs_three_sentences" }

        val rubric = question.rubricaEvaluacion
        require(rubric.metodo == "STAR") { "wrong_method" }
        val criteriaText = normalize(rubric.criterios.joinToString(" "))
        starComponents().forEach { component -> require(criteriaText.contains(component)) { "missing_star_$component" } }
        val answerText = normalize(question.respuestaIdeal)
        starComponents().forEach { component -> require(answerText.contains(component)) { "incomplete_answer_$component" } }
        require(rubric.palabrasClave.size >= 3 && rubric.palabrasClave.all { it.trim().length >= 3 }) { "bad_keywords" }
        require(rubric.tiempoEsperadoSeg > 0) { "bad_time" }

        if (expectedTipo == "opcion_multiple") validateMultipleChoice(question) else require(question.opciones == null) { "options_must_be_null" }
    }

    private fun validateMultipleChoice(question: LlmQuestion) {
        val options = question.opciones ?: throw IllegalArgumentException("missing_options")
        require(options.size == 4) { "wrong_options_count" }
        require(options.count { it.esCorrecta } == 1) { "wrong_correct_options" }
        require(options.all { it.texto.isNotBlank() && it.explicacion.isNotBlank() }) { "blank_option" }
        require(options.map { normalizeIdentity(it.texto) }.toSet().size == options.size) { "duplicate_option_text" }
    }

    private fun sentenceLikeSegments(value: String): Int = value
        .split(Regex("[.!?]+|(?i)(?=\\b(?:situación|tarea|acción|resultado)\\s*:)") )
        .count { it.trim().length >= 12 }

    private fun starComponents() = listOf("situacion", "tarea", "accion", "resultado")

    private fun normalizeIdentity(value: String): String = normalize(value).replace(Regex("\\s+"), " ").trim()

    private fun normalize(value: String): String = value.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
}
