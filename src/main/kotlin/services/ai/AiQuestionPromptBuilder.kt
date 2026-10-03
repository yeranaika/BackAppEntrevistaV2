package services.ai

object AiQuestionPromptBuilder {
    fun build(req: ValidatedAiQuestionRequest, cargoNombre: String?, skillNombre: String?): String {
        val contexto = buildString {
            if (cargoNombre != null) append("Cargo objetivo: $cargoNombre. ")
            if (skillNombre != null) append("Skill evaluada: $skillNombre. ")
        }.ifBlank { "Preguntas generales de ${req.categoria}." }

        return """
            Eres un experto en diseño de entrevistas y evaluación de competencias.
            Genera exactamente ${req.cantidad} preguntas válidas.

            CONTEXTO:
            - $contexto
            - Nivel: ${req.nivel}
            - Tipo: ${req.tipo}
            - Categoría: ${req.categoria}

            Reglas obligatorias:
            - Devuelve únicamente un objeto JSON que cumpla el schema estricto entregado.
            - No uses markdown ni texto fuera del JSON.
            - Cada respuesta_ideal debe ser completa y cubrir Situación, Tarea, Acción y Resultado.
            - La rúbrica debe usar metodo STAR y criterios explícitos para Situación, Tarea, Acción y Resultado.
            - palabras_clave debe contener términos significativos.
            - Para opcion_multiple entrega exactamente cuatro opciones y una sola correcta.
            - Para tipos no opcion_multiple, opciones debe ser null.
        """.trimIndent()
    }
}
