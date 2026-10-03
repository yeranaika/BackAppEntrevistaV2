package SERVICIOS

import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorProhibido
import INTEGRACIONES.VerificadorIdentidadGoogle
import MODELOS.LectorUsuarioSesion
import MODELOS.RepositorioCuentaOAuth
import MODELOS.UsuarioSesion
import UTILIDADES.generarHashContrasena
import UTILIDADES.verificarContrasena
import java.util.UUID

/** Inicio, renovación y cierre de sesión, con correo/contraseña o con Google. */
class ServicioLogin(
    private val usuarios: LectorUsuarioSesion,
    private val cuentasOAuth: RepositorioCuentaOAuth,
    private val verificadorGoogle: VerificadorIdentidadGoogle,
    private val tokens: ServicioToken
) {
    // Se verifica contra este hash cuando el correo no existe, para que la respuesta tarde
    // lo mismo y no se pueda descubrir qué correos están registrados midiendo tiempos.
    private val hashSinUsuario by lazy { generarHashContrasena(UUID.randomUUID().toString()) }

    suspend fun iniciarSesion(correo: String, contrasena: String): ParTokens {
        val usuario = usuarios.buscarPorCorreo(normalizarCorreo(correo))
        val esContrasenaCorrecta = verificarContrasena(contrasena, usuario?.hashContrasena ?: hashSinUsuario)
        if (usuario == null || !esContrasenaCorrecta) {
            throw ErrorNoAutorizado("bad_credentials", "Correo o contraseña incorrectos")
        }
        // Se revisa después de la contraseña: sin ella no se revela el estado de la cuenta.
        return abrirSesion(usuario)
    }

    /** Sirve al flujo móvil (idToken) y al web (callback OAuth). Crea la cuenta si no existe. */
    suspend fun iniciarSesionConGoogle(idToken: String): ParTokens {
        val identidad = verificadorGoogle.verificar(idToken)
            ?: throw ErrorNoAutorizado("invalid_google_token", "El token de Google no es válido")
        val correo = identidad.correo
        if (!identidad.correoVerificado || correo.isNullOrBlank()) {
            throw ErrorNoAutorizado("google_email_not_verified", "El correo de Google no está verificado")
        }

        val usuarioId = cuentasOAuth.vincularOCrearDesdeGoogle(identidad.subject, normalizarCorreo(correo))
        val usuario = usuarios.buscarSesionPorId(usuarioId)
            ?: error("Usuario $usuarioId vinculado a Google no existe")
        return abrirSesion(usuario)
    }

    suspend fun renovarSesion(tokenRefresco: String): ParTokens = tokens.rotar(tokenRefresco)

    suspend fun cerrarSesion(tokenRefresco: String) = tokens.revocar(tokenRefresco)

    private suspend fun abrirSesion(usuario: UsuarioSesion): ParTokens {
        if (!usuario.estaActivo) throw ErrorProhibido("inactive_user", "La cuenta no está activa")
        usuarios.registrarUltimoLogin(usuario.id)
        return tokens.emitirPar(usuario.id, usuario.rol)
    }

    private fun normalizarCorreo(correo: String) = correo.trim().lowercase()
}
