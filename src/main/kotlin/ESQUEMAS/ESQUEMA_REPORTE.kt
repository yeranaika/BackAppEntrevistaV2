package ESQUEMAS

import kotlinx.serialization.Serializable

// Reporte de feedback de la entrevista y progreso por skill (/api/v1).

@Serializable
data class RespuestaSkillReporte(
    /** null en los grupos generales (preguntas sin skill) */
    val skillId: String? = null,
    val nombre: String,
    /** tecnica | blanda */
    val categoria: String,
    /** 0 a 100 */
    val puntaje: Double,
    val nivelEvaluado: String? = null,
    val observacion: String? = null,
    val preguntasRespondidas: Int
)

@Serializable
data class RespuestaRecomendacionSkill(
    val skillId: String,
    val nombre: String,
    val puntaje: Double,
    val nivelRequerido: String? = null,
    /** alta | media */
    val prioridad: String
)

/** Feedback de cada pregunta de la entrevista. */
@Serializable
data class RespuestaFeedbackPregunta(
    val preguntaSesionId: String,
    val orden: Int,
    val enunciado: String,
    val tipo: String,
    val categoria: String,
    val respondida: Boolean,
    val respuesta: String? = null,
    /** 0 a 100; null si no se pudo evaluar (ej: clip sin transcripción) */
    val puntaje: Double? = null,
    val esCorrecta: Boolean? = null,
    val observacion: String? = null,
    val mejoras: List<String> = emptyList(),
    val respuestaIdeal: String? = null
)

@Serializable
data class RespuestaReporteEntrevista(
    val sesionId: String,
    /** generando | listo | error */
    val estado: String,
    val cargo: String,
    val nivel: String,
    val fechaEntrevista: String,
    /** Puntajes 0 a 100: solo cuando el reporte está listo. */
    val puntajeGlobal: Double? = null,
    val puntajeTecnico: Double? = null,
    val puntajeBlando: Double? = null,
    val puntajeLenguajeCorporal: Double? = null,
    val fortalezas: List<String> = emptyList(),
    val areasMejora: List<String> = emptyList(),
    val recomendaciones: List<RespuestaRecomendacionSkill> = emptyList(),
    val resumen: String? = null,
    /** freemium | ia */
    val modoEvaluacion: String? = null,
    /** Mensaje para el usuario si el reporte terminó con error. */
    val error: String? = null,
    // Sin valor por defecto para que siempre viaje en el JSON (encodeDefaults=false omitiría el false).
    val puedeReintentar: Boolean,
    val skills: List<RespuestaSkillReporte> = emptyList(),
    val preguntas: List<RespuestaFeedbackPregunta> = emptyList(),
    val fechaGeneracion: String? = null
)

@Serializable
data class RespuestaResumenReporte(
    val sesionId: String,
    val cargo: String,
    val nivel: String,
    val fechaEntrevista: String,
    val estado: String,
    val puntajeGlobal: Double? = null,
    val fechaGeneracion: String
)

@Serializable
data class RespuestaPuntoProgreso(
    val sesionId: String,
    val puntaje: Double,
    val fecha: String
)

@Serializable
data class RespuestaProgresoSkill(
    val skillId: String,
    val nombre: String,
    val nivel: String? = null,
    /** Promedio acumulado 0 a 100 (nivelación, práctica y entrevistas) */
    val puntaje: Double,
    val evaluaciones: Int,
    /** Puntaje en cada entrevista, del más antiguo al más reciente */
    val historial: List<RespuestaPuntoProgreso>
)
