package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// API /api/v1/practicas, /api/v1/nivelacion y /api/v1/pruebas.

@Serializable
data class SolicitudIniciarPractica(
    /** Practicar una skill puntual; si no viene, se practica el cargo. */
    val skillId: String? = null,
    val cargoId: String? = null,
    val cargo: String? = null,
    /** tecnica | blanda */
    val categoria: String? = null,
    /** opcion_multiple | abierta_texto | mixto */
    val modo: String? = null,
    /** junior | semisenior | senior (también jr | mid | sr) */
    val nivel: String? = null,
    val cantidadPreguntas: Int? = null
)

@Serializable
data class SolicitudResponderPrueba(
    /** Id de la pregunta dentro de la prueba. */
    val preguntaId: String,
    val opcionId: String? = null,
    val texto: String? = null,
    val tiempoRespuestaMs: Int? = null
)

@Serializable
data class SolicitudResponderVarias(
    val respuestas: List<SolicitudResponderPrueba>
)

@Serializable
data class RespuestaOpcionPrueba(
    val opcionId: String,
    val texto: String
)

/** Corrección de una respuesta (feedback inmediato). */
@Serializable
data class RespuestaCorreccion(
    val opcionId: String? = null,
    val texto: String? = null,
    val correcta: Boolean,
    /** 0 a 100 */
    val puntaje: Double,
    val feedback: String? = null,
    val opcionCorrectaId: String? = null,
    val respuestaIdeal: String? = null
)

@Serializable
data class RespuestaPreguntaServida(
    val preguntaId: String,
    val orden: Int,
    val enunciado: String,
    /** opcion_multiple | abierta_texto */
    val tipo: String,
    /** tecnica | blanda */
    val categoria: String,
    val nivel: String,
    val opciones: List<RespuestaOpcionPrueba> = emptyList(),
    val respondida: Boolean,
    /** Solo en las preguntas ya respondidas. */
    val correccion: RespuestaCorreccion? = null
)

@Serializable
data class RespuestaSesionPracticaApi(
    val sesionId: String,
    val skillId: String? = null,
    val cargoId: String? = null,
    val cargo: String? = null,
    val modo: String,
    val categoria: String,
    val nivel: String,
    /** en_progreso | finalizada | abandonada */
    val estado: String,
    /** 0 a 100, al finalizar */
    val puntaje: Double? = null,
    val totalPreguntas: Int,
    val respondidas: Int,
    val correctas: Int,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val preguntas: List<RespuestaPreguntaServida> = emptyList()
)

@Serializable
data class RespuestaResumenPrueba(
    val id: String,
    /** entrevista | practica | nivelacion */
    val tipo: String,
    val cargo: String? = null,
    val nivel: String? = null,
    val estado: String,
    val puntaje: Int? = null,
    val puntajeTotal: Int,
    val fechaInicio: String,
    val fechaFin: String? = null
)

// ---------- Nivelación ----------

@Serializable
data class SolicitudIniciarNivelacion(
    val cargoId: String? = null,
    val cargo: String? = null
)

@Serializable
data class RespuestaEvaluacionSkill(
    val skillId: String,
    val nombre: String,
    val nivelActual: String,
    val nivelRequerido: String? = null,
    val puntaje: Double,
    /** Niveles que faltan para el requerido */
    val brecha: Int,
    /** alta | media */
    val prioridad: String? = null
)

@Serializable
data class RespuestaResultadoNivelacion(
    val intentoId: String,
    val cargoId: String? = null,
    val nivel: String,
    val puntajeGlobal: Double,
    val brechas: List<RespuestaEvaluacionSkill>,
    val skillsOk: List<RespuestaEvaluacionSkill>,
    val resumen: String? = null,
    val fecha: String
)

@Serializable
data class RespuestaIntentoNivelacion(
    val intentoId: String,
    val cargoId: String? = null,
    val cargo: String? = null,
    /** en_progreso | finalizada */
    val estado: String,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val totalPreguntas: Int,
    val preguntas: List<RespuestaPreguntaServida>,
    val resultado: RespuestaResultadoNivelacion? = null
)

@Serializable
data class RespuestaNivelSkill(
    val skillId: String,
    val nombre: String,
    val nivel: String? = null,
    val puntaje: Double,
    val evaluaciones: Int,
    val fecha: String
)

@Serializable
data class SolicitudTestNivelacion(
    val titulo: String,
    val cargoId: String? = null,
    val area: String,
    /** junior | semisenior | senior | mixto */
    val nivelObjetivo: String? = null,
    val descripcion: String? = null,
    val preguntasIds: List<String>
)

@Serializable
data class RespuestaTestNivelacion(
    val testId: String,
    val titulo: String,
    val cargoId: String? = null,
    val area: String,
    val nivelObjetivo: String,
    val descripcion: String? = null,
    val preguntasIds: List<String>,
    val activo: Boolean
)

// ---------- Sincronización offline y evaluación freemium (mismo JSON que antes) ----------

@Serializable
data class RespuestaOfflineApp(
    val preguntaId: String,
    val enunciado: String,
    val opcionElegidaId: String? = null,
    val respuestaTexto: String? = null,
    val esCorrecta: Boolean = false,
    /** 0 a 10 */
    val puntaje: Double = 0.0,
    val feedbackTexto: String? = null,
    val tiempoRespuestaMs: Int? = null,
    val orden: Int = 1
)

@Serializable
data class IntentoOfflineApp(
    val localAttemptId: String,
    val skillId: String,
    val cargoId: String? = null,
    /** opcion_multiple | abierta_texto */
    val modo: String,
    val categoria: String = "tecnica",
    val nivelPreguntas: String = "junior",
    val puntajeTotal: Double = 0.0,
    val fechaCreacionIso: String? = null,
    val respuestas: List<RespuestaOfflineApp> = emptyList()
)

@Serializable
data class SolicitudSincronizacion(
    @SerialName("attempts") val intentos: List<IntentoOfflineApp>
)

@Serializable
data class IdSincronizado(
    val localAttemptId: String,
    val serverAttemptId: String
)

@Serializable
data class RespuestaSincronizacionApp(
    val success: Boolean,
    val syncedCount: Int,
    val mappings: List<IdSincronizado>
)

@Serializable
data class SolicitudEvaluacionFreemium(
    val preguntaId: String? = null,
    val userText: String,
    val idealText: String,
    val expectedKeywords: List<String> = emptyList()
)

@Serializable
data class RespuestaEvaluacionFreemium(
    val score: Double,
    val keywordMatchPercentage: Double,
    val similarityPercentage: Double,
    val matchedKeywords: List<String>,
    val missingKeywords: List<String>,
    val feedbackSummary: String
)
