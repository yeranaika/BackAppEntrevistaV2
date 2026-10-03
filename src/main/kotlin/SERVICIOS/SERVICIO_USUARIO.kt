package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudActualizarCuenta
import ESQUEMAS.SolicitudActualizarPerfil
import ESQUEMAS.SolicitudRegistro
import MODELOS.Cambio
import MODELOS.CambiosCuenta
import MODELOS.CambiosPerfil
import MODELOS.IDIOMA_POR_DEFECTO
import MODELOS.NuevoUsuario
import MODELOS.ObjetivoCarrera
import MODELOS.Perfil
import MODELOS.ROL_USUARIO
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioPerfil
import MODELOS.RepositorioUsuario
import MODELOS.Usuario
import UTILIDADES.generarHashContrasena
import UTILIDADES.sinVacios
import UTILIDADES.validarArea
import UTILIDADES.validarCorreo
import UTILIDADES.validarFechaNacimiento
import UTILIDADES.validarGenero
import UTILIDADES.validarIdioma
import UTILIDADES.validarNivel
import UTILIDADES.validarNombre
import UTILIDADES.validarNuevaContrasena
import UTILIDADES.validarPais
import UTILIDADES.validarTelefono
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

private const val PALABRA_CONFIRMACION_BORRADO = "eliminar"

data class CuentaCompleta(val usuario: Usuario, val perfil: Perfil?, val objetivo: ObjetivoCarrera?)

/** Registro, datos de la cuenta, perfil y borrado de la cuenta propia. */
class ServicioUsuario(
    private val usuarios: RepositorioUsuario,
    private val perfiles: RepositorioPerfil,
    private val objetivos: RepositorioObjetivoCarrera,
    private val tokens: ServicioToken,
    private val reloj: Clock = Clock.systemDefaultZone()
) {

    suspend fun registrar(solicitud: SolicitudRegistro): ParTokens {
        // Códigos invalid_country / invalid_birthdate: los que muestra la pantalla de registro de Android.
        val correo = validarCorreo(solicitud.correo)
        validarNuevaContrasena(solicitud.contrasena)
        val nuevo = NuevoUsuario(
            correo = correo,
            hashContrasena = generarHashContrasena(solicitud.contrasena),
            nombre = solicitud.nombre.sinVacios()?.let(::validarNombre),
            idioma = solicitud.idioma.sinVacios()?.let(::validarIdioma) ?: IDIOMA_POR_DEFECTO,
            telefono = solicitud.telefono.sinVacios()?.let(::validarTelefono),
            fechaNacimiento = solicitud.fechaNacimiento.sinVacios()?.let {
                validarFechaNacimiento(it, hoy(), codigoFormato = "invalid_birthdate", codigoRango = "invalid_birthdate")
            },
            genero = solicitud.genero.sinVacios()?.let(::validarGenero)
        )
        val perfil = CambiosPerfil(
            nivelExperiencia = solicitud.nivelExperiencia.sinVacios()?.let(::validarNivel),
            area = solicitud.area.sinVacios()?.let(::validarArea),
            pais = solicitud.pais.sinVacios()?.let { validarPais(it, codigo = "invalid_country") },
            notaObjetivos = solicitud.notaObjetivos.sinVacios(),
            flagsAccesibilidad = solicitud.flagsAccesibilidad
        )
        if (usuarios.existeCorreo(correo)) throw ErrorConflicto("email_in_use", "Ese correo ya está registrado")

        val usuarioId = usuarios.crear(nuevo, perfil)
        return tokens.emitirPar(usuarioId, ROL_USUARIO)
    }

    suspend fun obtenerCuenta(usuarioId: UUID): CuentaCompleta {
        val usuario = usuarios.buscarPorId(usuarioId) ?: throw usuarioNoEncontrado()
        return CuentaCompleta(usuario, perfiles.buscarPorUsuario(usuarioId), objetivos.buscarActivo(usuarioId))
    }

    suspend fun actualizarCuenta(usuarioId: UUID, solicitud: SolicitudActualizarCuenta) {
        val cambios = CambiosCuenta(
            nombre = solicitud.nombre?.let(::validarNombre),
            idioma = solicitud.idioma?.let(::validarIdioma),
            telefono = solicitud.telefono?.let { Cambio(it.sinVacios()?.let(::validarTelefono)) },
            fechaNacimiento = solicitud.fechaNacimiento?.let { Cambio(it.sinVacios()?.let { f -> validarFechaNacimiento(f, hoy()) }) },
            genero = solicitud.genero?.let { Cambio(it.sinVacios()?.let(::validarGenero)) }
        )
        if (cambios.estaVacio) throw ErrorValidacion("nothing_to_update", "No se envió ningún dato para actualizar")
        if (!usuarios.actualizarCuenta(usuarioId, cambios)) throw usuarioNoEncontrado()
    }

    suspend fun obtenerPerfil(usuarioId: UUID): Perfil =
        perfiles.buscarPorUsuario(usuarioId)
            ?: throw ErrorNoEncontrado("profile_not_found", "El usuario aún no tiene perfil")

    suspend fun actualizarPerfil(usuarioId: UUID, solicitud: SolicitudActualizarPerfil) {
        val cambios = CambiosPerfil(
            nivelExperiencia = solicitud.nivelExperiencia.sinVacios()?.let(::validarNivel),
            area = solicitud.area.sinVacios()?.let(::validarArea),
            pais = solicitud.pais.sinVacios()?.let { validarPais(it) },
            notaObjetivos = solicitud.notaObjetivos,
            flagsAccesibilidad = solicitud.flagsAccesibilidad
        )
        perfiles.guardar(usuarioId, cambios)
    }

    /** Derecho al olvido: el usuario debe escribir "eliminar"; la BD borra en cascada sus datos. */
    suspend fun eliminarCuenta(usuarioId: UUID, confirmacion: String) {
        if (confirmacion.trim().lowercase() != PALABRA_CONFIRMACION_BORRADO) {
            throw ErrorValidacion("must_type_eliminar", "Escribe \"$PALABRA_CONFIRMACION_BORRADO\" para confirmar")
        }
        if (!usuarios.eliminar(usuarioId)) throw usuarioNoEncontrado()
    }

    private fun hoy(): LocalDate = LocalDate.now(reloj)

    private fun usuarioNoEncontrado() = ErrorNoEncontrado("user_not_found", "Usuario no encontrado")
}
