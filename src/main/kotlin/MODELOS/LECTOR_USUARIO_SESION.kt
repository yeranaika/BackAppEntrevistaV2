package MODELOS

import java.util.UUID

/**
 * Solo lo que el inicio de sesión necesita saber del usuario (segregación de interfaces).
 * La implementa RepositorioUsuarioExposed.
 */
interface LectorUsuarioSesion {
    suspend fun buscarPorCorreo(correo: String): UsuarioSesion?
    suspend fun buscarSesionPorId(id: UUID): UsuarioSesion?
    suspend fun registrarUltimoLogin(id: UUID)
}
