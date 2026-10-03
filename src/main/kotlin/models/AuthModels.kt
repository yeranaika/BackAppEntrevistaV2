package models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ====== Modelos (DTOs) del dominio de autenticación ======
// Agrupa lo que antes vivía disperso en routes/auth/AuthDtos.kt y routes/auth/GoogleAuth.kt.
// Usado por controllers.AuthController y services.AuthService.

@Serializable
data class RegisterReq(
    val email: String,
    val password: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null, // "YYYY-MM-DD"
    val genero: String? = null,
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class LoginReq(
    val email: String,
    val password: String
)

@Serializable
data class LoginOk(
    val accessToken: String,
    val refreshToken: String? = null
)

// 👇 DTO que usa la APP ANDROID (idToken de Google) — también sirve para el auto-registro
@Serializable
data class GoogleLoginReq(
    val idToken: String
)

// Respuesta del flujo WEB (callback), en snake_case por compatibilidad con lo ya existente
@Serializable
data class TokenPair(
    val access_token: String,
    val refresh_token: String
)

@Serializable
data class UpdateProfileReq(
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null, // "YYYY-MM-DD" o null
    val genero: String? = null
)

@Serializable
data class RefreshReq(
    val refreshToken: String
)

@Serializable
data class RefreshOk(
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class LogoutReq(
    val refreshToken: String
)

@Serializable
data class ConfirmarBorradoReq(
    val confirmar: String
)

@Serializable
data class DeleteAccountOk(
    val message: String
)
