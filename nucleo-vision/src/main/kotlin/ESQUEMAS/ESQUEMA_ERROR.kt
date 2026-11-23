package ESQUEMAS

import kotlinx.serialization.Serializable

/**
 * Cuerpo único de error de toda la API.
 * `error` mantiene el nombre de campo que ya consume Android; `mensaje` es opcional y legible.
 */
@Serializable
data class RespuestaError(
    val error: String,
    val mensaje: String? = null,
    /** Legado: la app Android lee `message` en los flujos de contraseña. Quitar cuando lea `mensaje`. */
    val message: String? = null
) {
    companion object {
        /** Error con texto legible, duplicado en `message` para la app Android. */
        fun conMensaje(codigo: String, mensaje: String) = RespuestaError(codigo, mensaje, message = mensaje)
    }
}
