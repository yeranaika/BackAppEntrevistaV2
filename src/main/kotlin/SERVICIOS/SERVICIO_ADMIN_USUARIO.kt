package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearUsuarioAdmin
import MODELOS.ESTADO_ACTIVO
import MODELOS.ESTADO_INACTIVO
import MODELOS.IDIOMA_POR_DEFECTO
import MODELOS.NuevoUsuario
import MODELOS.ROL_ADMIN
import MODELOS.ROL_USUARIO
import MODELOS.RepositorioUsuario
import MODELOS.Usuario
import UTILIDADES.generarHashContrasena
import UTILIDADES.sinVacios
import UTILIDADES.validarCorreo
import UTILIDADES.validarIdioma
import UTILIDADES.validarNombre
import UTILIDADES.validarNuevaContrasena
import java.util.UUID

private val ROLES_VALIDOS = setOf(ROL_USUARIO, ROL_ADMIN)

/** Gestión de cuentas por un administrador. [adminId] es quien ejecuta la acción. */
class ServicioAdminUsuario(
    private val usuarios: RepositorioUsuario,
    private val contrasenas: ServicioContrasena,
    private val tokens: ServicioToken
) {

    suspend fun listar(): List<Usuario> = usuarios.listar()

    suspend fun crear(solicitud: SolicitudCrearUsuarioAdmin): Usuario {
        val correo = validarCorreo(solicitud.correo, codigo = "correo_invalido")
        validarNuevaContrasena(solicitud.contrasena)
        val rol = validarRol(solicitud.rol)
        if (usuarios.existeCorreo(correo)) throw ErrorConflicto("email_in_use", "Ese correo ya está registrado")

        val id = usuarios.crear(
            NuevoUsuario(
                correo = correo,
                hashContrasena = generarHashContrasena(solicitud.contrasena),
                nombre = solicitud.nombre.sinVacios()?.let(::validarNombre),
                idioma = solicitud.idioma.sinVacios()?.let(::validarIdioma) ?: IDIOMA_POR_DEFECTO,
                rol = rol
            )
        )
        return usuarios.buscarPorId(id) ?: error("Usuario $id recién creado no existe")
    }

    suspend fun cambiarRol(adminId: UUID, usuarioId: UUID, nuevoRol: String) {
        val rol = validarRol(nuevoRol)
        // Evita que el último admin se deje a sí mismo sin acceso al panel.
        if (adminId == usuarioId && rol != ROL_ADMIN) {
            throw ErrorValidacion("no_puede_quitarse_admin", "No puedes quitarte el rol de administrador a ti mismo")
        }
        if (!usuarios.actualizarRol(usuarioId, rol)) throw usuarioNoEncontrado()
    }

    /** Desactiva la cuenta y cierra sus sesiones; el login y el refresh la rechazan desde ya. */
    suspend fun desactivar(adminId: UUID, usuarioId: UUID) {
        if (adminId == usuarioId) {
            throw ErrorValidacion("no_puede_desactivarse", "No puedes desactivar tu propia cuenta")
        }
        if (!usuarios.actualizarEstado(usuarioId, ESTADO_INACTIVO)) throw usuarioNoEncontrado()
        tokens.cerrarTodasLasSesiones(usuarioId)
    }

    suspend fun activar(usuarioId: UUID) {
        if (!usuarios.actualizarEstado(usuarioId, ESTADO_ACTIVO)) throw usuarioNoEncontrado()
    }

    suspend fun restablecerContrasena(usuarioId: UUID, nuevaContrasena: String) =
        contrasenas.restablecerPorAdmin(usuarioId, nuevaContrasena)

    private fun validarRol(rol: String): String {
        val normalizado = rol.trim().lowercase()
        if (normalizado !in ROLES_VALIDOS) throw ErrorValidacion("rol_invalido", "El rol debe ser 'user' o 'admin'")
        return normalizado
    }

    private fun usuarioNoEncontrado() = ErrorNoEncontrado("user_not_found", "Usuario no encontrado")
}
