package ESQUEMAS

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// API /api/v1/entrevistas (simulación por sesión, pregunta a pregunta).

@Serializable
data class SolicitudIniciarEntrevista(
    /** Cargo del catálogo. Si no viene, se usa [cargo] o el objetivo del onboarding. */
    val cargoId: String? = null,
    /** Nombre libre del cargo (si no está en el catálogo se usan preguntas generales). */
    val cargo: String? = null,
    /** junior | semisenior | senior (también jr | mid | sr). Por defecto el del perfil. */
    val nivel: String? = null,
    val cantidadPreguntas: Int? = null
)

@Serializable
data class SolicitudResponderPregunta(
    val preguntaSesionId: String,
    /** Respuesta escrita o transcripción del audio. */
    val texto: String? = null,
    /** Para preguntas de opción múltiple. */
    val opcionId: String? = null,
    /** Clip de video (https) para preguntas de simulación en video. */
    val videoClipUrl: String? = null
)

@Serializable
data class MetricaVideoEntrada(
    val timestampMs: Long,
    val contactoVisual: Double? = null,
    val postura: Double? = null,
    val confianza: Double? = null,
    val gestos: JsonObject? = null,
    /** seguro | nervioso | distraido | neutral | confuso */
    val expresion: String? = null
)

@Serializable
data class SolicitudMetricasVideo(
    val metricas: List<MetricaVideoEntrada>
)

@Serializable
data class RespuestaMetricasRegistradas(
    val insertadas: Int
)

@Serializable
data class RespuestaOpcionEntrevista(
    val opcionId: String,
    val texto: String
)

/** Lo que el usuario respondió. */
@Serializable
data class RespuestaDadaEntrevista(
    val texto: String? = null,
    val opcionId: String? = null,
    val videoClipUrl: String? = null,
    val fechaRespuesta: String
)

/** Corrección visible solo cuando la entrevista terminó (para no revelar respuestas durante la sesión). */
@Serializable
data class CorreccionPreguntaEntrevista(
    val opcionCorrectaId: String? = null,
    val esCorrecta: Boolean? = null,
    val puntaje: Double? = null,
    val respuestaIdeal: String? = null
)

@Serializable
data class RespuestaPreguntaEntrevista(
    val preguntaSesionId: String,
    val orden: Int,
    val enunciado: String,
    /** opcion_multiple | abierta_texto | simulacion_video */
    val tipo: String,
    /** tecnica | blanda */
    val categoria: String,
    val opciones: List<RespuestaOpcionEntrevista> = emptyList(),
    val respondida: Boolean,
    val respuesta: RespuestaDadaEntrevista? = null,
    val correccion: CorreccionPreguntaEntrevista? = null
)

@Serializable
data class RespuestaSesionEntrevista(
    val sesionId: String,
    val cargoId: String? = null,
    val cargo: String,
    val nivel: String,
    /** en_progreso | finalizada | cancelada */
    val estado: String,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val totalPreguntas: Int,
    val respondidas: Int,
    val preguntas: List<RespuestaPreguntaEntrevista> = emptyList()
)

@Serializable
data class RespuestaHistorialEntrevistas(
    val elementos: List<RespuestaSesionEntrevista>,
    val total: Long,
    val pagina: Int,
    val tamano: Int
)
