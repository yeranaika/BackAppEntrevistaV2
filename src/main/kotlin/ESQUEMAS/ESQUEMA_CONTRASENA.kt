package ESQUEMAS

import kotlinx.serialization.Serializable

@Serializable
data class SolicitudRecuperacion(
    val correo: String
)

@Serializable
data class SolicitudRestablecer(
    val correo: String,
    val codigo: String,
    val nuevaContrasena: String
)

@Serializable
data class SolicitudCambioContrasena(
    val contrasenaActual: String,
    val nuevaContrasena: String
)
