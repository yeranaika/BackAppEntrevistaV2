package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Error de /auth/register: Android lo decodifica con un Json estricto que falla ante cualquier campo extra.
 * Quitar cuando la app use ignoreUnknownKeys.
 */
@Serializable
data class RespuestaErrorLegado(
    val error: String
)

/**
 * Éxito sin recurso que devolver, con texto para el usuario.
 * `mensaje` es el campo estándar; `message` se mantiene porque Android lo lee en los flujos de contraseña.
 */
@Serializable
data class RespuestaMensaje(
    val mensaje: String,
    val message: String
) {
    constructor(mensaje: String) : this(mensaje, message = mensaje)
}

/** Éxito sin recurso. `ok` sin valor por defecto: con encodeDefaults=false un `true` por defecto no viajaría. */
@Serializable
data class RespuestaOk(
    val ok: Boolean,
    val mensaje: String
) {
    constructor(mensaje: String) : this(ok = true, mensaje = mensaje)
}
