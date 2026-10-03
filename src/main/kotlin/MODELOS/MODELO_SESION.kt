package MODELOS

import java.time.Instant
import java.util.UUID

const val ROL_USUARIO = "user"
const val ROL_ADMIN = "admin"
const val ESTADO_ACTIVO = "activo"

/** Lo mínimo de un usuario que necesita el inicio de sesión. */
data class UsuarioSesion(
    val id: UUID,
    val correo: String,
    val hashContrasena: String,
    val rol: String,
    val estaActivo: Boolean
)

data class RefreshToken(
    val id: UUID,
    val usuarioId: UUID,
    val estaRevocado: Boolean,
    val expiraEn: Instant
)
