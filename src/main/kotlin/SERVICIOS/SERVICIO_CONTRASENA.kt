package SERVICIOS

import CONFIGURACION.INTENTOS_MAXIMOS_CODIGO_RECUPERACION
import CONFIGURACION.MINUTOS_VIGENCIA_CODIGO_RECUPERACION
import ERRORES.ErrorDemasiadosIntentos
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import INTEGRACIONES.EnviadorCorreo
import MODELOS.CodigoRecuperacion
import MODELOS.LectorUsuarioSesion
import MODELOS.RepositorioCuentaOAuth
import MODELOS.RepositorioRecuperacionContrasena
import MODELOS.RepositorioUsuario
import UTILIDADES.generarHashContrasena
import UTILIDADES.validarCodigoRecuperacion
import UTILIDADES.validarCorreo
import UTILIDADES.validarNuevaContrasena
import UTILIDADES.verificarContrasena
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val DIGITOS_CODIGO = 6

/** Recuperación por correo, cambio desde el perfil y restablecimiento por un administrador. */
class ServicioContrasena(
    private val lectorUsuarios: LectorUsuarioSesion,
    private val usuarios: RepositorioUsuario,
    private val cuentasOAuth: RepositorioCuentaOAuth,
    private val codigos: RepositorioRecuperacionContrasena,
    private val correo: EnviadorCorreo,
    private val tokens: ServicioToken,
    /** Donde se envían los correos sin hacer esperar la respuesta HTTP. */
    private val tareasSegundoPlano: CoroutineScope,
    private val reloj: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(ServicioContrasena::class.java)
    private val generadorAleatorio = SecureRandom()

    /**
     * Siempre termina igual, exista o no el correo: así no sirve para averiguar qué correos están registrados.
     * El correo se envía en segundo plano para que el tiempo de respuesta tampoco lo delate.
     */
    suspend fun solicitarRecuperacion(correoIngresado: String) {
        val correoNormalizado = validarCorreo(correoIngresado, codigo = "correo_invalido")
        val sesion = lectorUsuarios.buscarPorCorreo(correoNormalizado) ?: return
        val nombre = usuarios.buscarPorId(sesion.id)?.nombre

        if (cuentasOAuth.esUsuarioGoogle(sesion.id)) {
            enviarEnSegundoPlano { correo.enviarAvisoCuentaGoogle(correoNormalizado, nombre) }
            return
        }

        val ahora = reloj.instant()
        val codigo = generarCodigo()
        codigos.crear(sesion.id, codigo, ahora, ahora.plus(MINUTOS_VIGENCIA_CODIGO_RECUPERACION, ChronoUnit.MINUTES))
        enviarEnSegundoPlano { correo.enviarCodigoRecuperacion(correoNormalizado, codigo, nombre) }
    }

    /** Cambia la contraseña con el código del correo y cierra todas las sesiones abiertas. */
    suspend fun restablecer(correoIngresado: String, codigoIngresado: String, nuevaContrasena: String) {
        val correoNormalizado = validarCorreo(correoIngresado, codigo = "correo_invalido")
        val codigoLimpio = validarCodigoRecuperacion(codigoIngresado)
        validarNuevaContrasena(nuevaContrasena, codigo = "contrasena_debil")

        val sesion = lectorUsuarios.buscarPorCorreo(correoNormalizado) ?: throw codigoInvalido()
        val vigente = codigos.buscarVigente(sesion.id, reloj.instant()) ?: throw codigoInvalido()
        verificarCodigo(vigente, codigoLimpio)
        if (!codigos.consumir(vigente.token)) throw codigoInvalido()

        usuarios.actualizarHashContrasena(sesion.id, generarHashContrasena(nuevaContrasena))
        tokens.cerrarTodasLasSesiones(sesion.id)
    }

    /**
     * Cambio desde el perfil: exige la contraseña actual. Mantiene las sesiones abiertas
     * para no expulsar al usuario del dispositivo desde el que hace el cambio.
     */
    suspend fun cambiar(usuarioId: UUID, contrasenaActual: String, nuevaContrasena: String) {
        validarNuevaContrasena(nuevaContrasena, codigo = "contrasena_debil")
        val sesion = lectorUsuarios.buscarSesionPorId(usuarioId)
            ?: throw ErrorNoEncontrado("user_not_found", "Usuario no encontrado")
        if (cuentasOAuth.esUsuarioGoogle(usuarioId)) {
            throw ErrorValidacion("cuenta_google", "Esta cuenta fue creada con Google. No puedes cambiar la contraseña aquí.")
        }
        if (!verificarContrasena(contrasenaActual, sesion.hashContrasena)) {
            throw ErrorValidacion("contrasena_actual_incorrecta", "La contraseña actual es incorrecta")
        }
        if (contrasenaActual == nuevaContrasena) {
            throw ErrorValidacion("contrasena_repetida", "La nueva contraseña debe ser distinta de la actual")
        }
        usuarios.actualizarHashContrasena(usuarioId, generarHashContrasena(nuevaContrasena))
    }

    /** Un administrador fija la contraseña: se cierran las sesiones del usuario. */
    suspend fun restablecerPorAdmin(usuarioId: UUID, nuevaContrasena: String) {
        validarNuevaContrasena(nuevaContrasena)
        if (!usuarios.actualizarHashContrasena(usuarioId, generarHashContrasena(nuevaContrasena))) {
            throw ErrorNoEncontrado("user_not_found", "Usuario no encontrado")
        }
        tokens.cerrarTodasLasSesiones(usuarioId)
    }

    private suspend fun verificarCodigo(vigente: CodigoRecuperacion, codigoIngresado: String) {
        if (vigente.intentosFallidos >= INTENTOS_MAXIMOS_CODIGO_RECUPERACION) {
            codigos.invalidar(vigente.token)
            throw demasiadosIntentos()
        }
        // Comparación en tiempo constante: no revela cuántos dígitos acertó.
        if (MessageDigest.isEqual(vigente.codigo.toByteArray(), codigoIngresado.toByteArray())) return

        codigos.registrarIntentoFallido(vigente.token)
        if (vigente.intentosFallidos + 1 >= INTENTOS_MAXIMOS_CODIGO_RECUPERACION) {
            codigos.invalidar(vigente.token)
            throw demasiadosIntentos()
        }
        throw codigoInvalido()
    }

    private fun enviarEnSegundoPlano(envio: suspend () -> Unit) {
        tareasSegundoPlano.launch {
            runCatching { envio() }.onFailure { log.error("No se pudo enviar el correo de recuperación", it) }
        }
    }

    private fun generarCodigo(): String {
        val limite = Math.pow(10.0, DIGITOS_CODIGO.toDouble()).toInt()
        return generadorAleatorio.nextInt(limite).toString().padStart(DIGITOS_CODIGO, '0')
    }

    private fun codigoInvalido() = ErrorValidacion("codigo_invalido", "Código inválido o expirado")

    private fun demasiadosIntentos() =
        ErrorDemasiadosIntentos("demasiados_intentos", "Demasiados intentos. Solicita un nuevo código")
}
