package ESQUEMAS

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Revisión de texto libre sin conexión (T3.13) ----------

/** Pregunta abierta para responder y revisar en el teléfono. Lleva la respuesta ideal y la rúbrica: la revisión es local. */
@Serializable
data class RespuestaPreguntaRevisable(
    val id: String,
    val tipo: String,
    val categoria: String,
    val nivel: String,
    val enunciado: String,
    val respuestaIdeal: String?,
    val rubrica: JsonObject?
)

/** `versionRevisor` cambia cuando cambia la fórmula: la app puede avisar si su copia quedó vieja. */
@Serializable
data class RespuestaDescargaRevision(
    val versionRevisor: Int,
    val preguntas: List<RespuestaPreguntaRevisable>
)

/** Una respuesta revisada en el teléfono. Solo texto. `scoreLocal` y `bandaLocal` sirven para comprobar la paridad. */
@Serializable
data class RespuestaRevisadaApp(
    val idLocal: String,
    val preguntaId: String,
    val texto: String,
    val scoreLocal: Double? = null,
    val bandaLocal: String? = null,
    /** Milisegundos desde 1970 en que se revisó en el teléfono. */
    val revisadoEnMs: Long? = null
)

@Serializable
data class SolicitudSubirRevisiones(
    val respuestas: List<RespuestaRevisadaApp>
)

/**
 * estado: "guardada" (nueva), "ya_recibida" (reenvío, no duplica) o "rechazada" (la app debe descartarla: reintentar no
 * sirve). Con "rechazada" viene el [motivo]. Sin valores por defecto: el cliente lee todos los campos.
 */
@Serializable
data class ResultadoRevisionSubida(
    val idLocal: String,
    val estado: String,
    val scoreServidor: Double?,
    val bandaServidor: String?,
    /** true si el puntaje del teléfono y el del servidor son el mismo (paridad); null si el teléfono no mandó puntaje. */
    val coincide: Boolean?,
    val motivo: String?
)

@Serializable
data class RespuestaSubidaRevisiones(
    val recibidas: Int,
    val resultados: List<ResultadoRevisionSubida>
)
