package ESQUEMAS

import kotlinx.serialization.Serializable

/**
 * Cuerpo único de error de toda la API.
 * `error` mantiene el nombre de campo que ya consume Android; `mensaje` es opcional y legible.
 */
@Serializable
data class RespuestaError(
    val error: String,
    val mensaje: String? = null
)

@Serializable
data class RespuestaOk(
    val ok: Boolean = true
)
