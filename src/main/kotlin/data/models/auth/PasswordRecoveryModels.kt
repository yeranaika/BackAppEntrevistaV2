package data.models.auth

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordReq(
    val correo: String
)

@Serializable
data class ForgotPasswordRes(
    val message: String
)

@Serializable
data class ResetPasswordReq(
    val correo: String,
    val codigo: String,
    val nuevaContrasena: String
)

@Serializable
data class ResetPasswordRes(
    val message: String
)

@Serializable
data class ChangePasswordReq(
    val contrasenaActual: String,
    val nuevaContrasena: String
)
