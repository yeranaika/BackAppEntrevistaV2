package models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTOs de registro, perfil y borrado de cuenta. Se migran a ESQUEMAS en la Fase 2.
// Los de login/refresh/logout ya viven en ESQUEMAS/ESQUEMA_LOGIN.kt.

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
data class UpdateProfileReq(
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null, // "YYYY-MM-DD" o null
    val genero: String? = null
)

@Serializable
data class ConfirmarBorradoReq(
    val confirmar: String
)

@Serializable
data class DeleteAccountOk(
    val message: String
)
