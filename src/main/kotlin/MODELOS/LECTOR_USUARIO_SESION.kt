package MODELOS

import data.repository.usuarios.UserRepository
import data.repository.usuarios.UserRow
import java.util.UUID

/** Solo lo que el inicio de sesión necesita saber del usuario (segregación de interfaces). */
interface LectorUsuarioSesion {
    suspend fun buscarPorCorreo(correo: String): UsuarioSesion?
    suspend fun buscarPorId(id: UUID): UsuarioSesion?
    suspend fun registrarUltimoLogin(id: UUID)
}

/** Adaptador sobre el repositorio de usuarios actual; se reemplaza al migrar usuarios (Fase 2). */
class LectorUsuarioSesionExposed(private val usuarios: UserRepository) : LectorUsuarioSesion {

    override suspend fun buscarPorCorreo(correo: String): UsuarioSesion? =
        usuarios.findByEmail(correo)?.aUsuarioSesion()

    override suspend fun buscarPorId(id: UUID): UsuarioSesion? =
        usuarios.findById(id)?.aUsuarioSesion()

    override suspend fun registrarUltimoLogin(id: UUID) {
        usuarios.touchUltimoLogin(id)
    }

    private fun UserRow.aUsuarioSesion() = UsuarioSesion(
        id = id,
        correo = email,
        hashContrasena = hash,
        rol = rol,
        estaActivo = estado == ESTADO_ACTIVO
    )
}
