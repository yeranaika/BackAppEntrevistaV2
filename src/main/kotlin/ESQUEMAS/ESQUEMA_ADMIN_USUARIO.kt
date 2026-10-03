package ESQUEMAS

import kotlinx.serialization.Serializable

@Serializable
data class SolicitudCrearUsuarioAdmin(
    val correo: String,
    val contrasena: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val rol: String = "user"
)

@Serializable
data class RespuestaUsuarioCreado(
    val id: String,
    val correo: String,
    val nombre: String?,
    val idioma: String,
    val rol: String
)

@Serializable
data class RespuestaUsuarioAdmin(
    val usuarioId: String,
    val correo: String,
    val nombre: String?,
    val rol: String,
    val estado: String,
    val idioma: String,
    val fechaCreacion: String
)

@Serializable
data class SolicitudCambioRol(
    val nuevoRol: String
)

@Serializable
data class SolicitudContrasenaAdmin(
    val nuevaContrasena: String
)
