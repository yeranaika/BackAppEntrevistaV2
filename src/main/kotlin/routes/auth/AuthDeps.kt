package routes.auth

import data.repository.usuarios.PasswordResetRepository
import data.repository.usuarios.RefreshTokenRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UsuariosOAuthRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl

// Repos compartidos por las rutas de /auth
object AuthDeps {
    val users       = UserRepository()
    val resets      = PasswordResetRepository()
    val refreshRepo = RefreshTokenRepository()
    val profiles    = ProfileRepository()
    val oauthRepo: UsuariosOAuthRepository = UsuariosOAuthRepositoryImpl()
}
