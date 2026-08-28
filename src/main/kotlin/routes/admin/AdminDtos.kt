package routes.admin

import kotlinx.serialization.Serializable

// ============================================
// REQUEST DTOs
// ============================================

@Serializable
data class UpdateRoleRequest(
    val nuevoRol: String
)

@Serializable
data class ResetPasswordRequest(
    val nuevaContrasena: String
)

// ============================================
// RESPONSE DTOs
// ============================================

@Serializable
data class UserListResponse(
    val usuarioId: String,
    val correo: String,
    val nombre: String?,
    val rol: String,
    val estado: String,
    val idioma: String,
    val fechaCreacion: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class SuccessResponse(
    val message: String
)

@Serializable
data class UsuariosDashboardStats(
    val total: Long,
    val administradores: Long,
    val regulares: Long
)
