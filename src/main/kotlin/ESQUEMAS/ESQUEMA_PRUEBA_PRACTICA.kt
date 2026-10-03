package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Contrato que ya usa la app Android (EntrevistaTestDtos) en /api/prueba-practica para la entrevista (ENT).
// No renombrar campos: la app los lee tal cual.

@Serializable
data class SolicitudCrearPruebaPractica(
    val sector: String? = null,
    /** jr | mid | sr (también junior | semisenior | senior). */
    val nivel: String? = null,
    val metaCargo: String? = null,
    /** ENT | MIX | SIM: simulación de entrevista. PR, NV y BL llegan en la Fase 6. */
    val tipoPrueba: String? = null,
    val cantidadPR: Int? = null,
    val cantidadNV: Int? = null,
    val cantidadBL: Int? = null
)

@Serializable
data class OpcionPruebaPractica(
    val id: String,
    val texto: String
)

/** Se envía como el JsonElement configRespuesta. */
@Serializable
data class ConfigRespuestaPruebaPractica(
    val opciones: List<OpcionPruebaPractica>? = null,
    @SerialName("min_caracteres") val minCaracteres: Int? = null,
    @SerialName("max_caracteres") val maxCaracteres: Int? = null,
    val formato: String? = null,
    val tipo: String
)

@Serializable
data class PreguntaPruebaPractica(
    /** Id de la pregunta dentro de la sesión (se devuelve tal cual al responder). */
    val preguntaId: String,
    val texto: String,
    /** PR técnica, BL blanda */
    val tipoBanco: String,
    val sector: String,
    /** jr | mid | sr */
    val nivel: String,
    /** opcion_multiple | abierta */
    val tipoPregunta: String,
    val configRespuesta: ConfigRespuestaPruebaPractica,
    val orden: Int
)

@Serializable
data class RespuestaCrearPruebaPractica(
    val pruebaId: String,
    val tipoPrueba: String,
    // Sin valor por defecto a propósito: la app los declara sin default y falla si no vienen en el JSON.
    val area: String?,
    val nivel: String?,
    val metadata: Map<String, String>?,
    val preguntas: List<PreguntaPruebaPractica>
)

@Serializable
data class RespuestaPreguntaPractica(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String>? = null,
    val respuestaAbierta: String? = null
)

@Serializable
data class SolicitudEnviarRespuestasPractica(
    val pruebaId: String? = null,
    val respuestas: List<RespuestaPreguntaPractica>
)

@Serializable
data class ResultadoPreguntaPractica(
    val preguntaId: String,
    val correcta: Boolean,
    val claveCorrecta: String? = null,
    val seleccionadas: List<String> = emptyList()
)

@Serializable
data class RespuestaEnviarRespuestasPractica(
    val ok: Boolean,
    /** Correctas entre las preguntas que se corrigen al instante (opción múltiple). */
    val puntaje: Int,
    /** Preguntas corregidas al instante; la app muestra "puntaje / totalPreguntas". */
    val totalPreguntas: Int,
    val respondidas: Int,
    val correctas: Int,
    val nivelDetectado: String? = null,
    val detalle: List<ResultadoPreguntaPractica>,
    val feedbackGeneral: String? = null
)
