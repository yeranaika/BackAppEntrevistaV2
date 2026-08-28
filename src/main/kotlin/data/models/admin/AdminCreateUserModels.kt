package data.models.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminCreateUserReq(
    val correo: String,
    val contrasena: String,
    val nombre: String? = null,
    val idioma: String? = "es",
    val rol: String = "user" // "user" | "admin"
)

@Serializable
data class AdminCreateUserRes(
    val id: String,
    val correo: String,
    val nombre: String?,
    val idioma: String,
    val rol: String
)
