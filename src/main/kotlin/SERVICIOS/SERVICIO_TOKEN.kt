package SERVICIOS

import CONFIGURACION.ConfiguracionJwt
import CONFIGURACION.DIAS_VIGENCIA_REFRESH_TOKEN
import CONFIGURACION.TTL_TOKEN_ACCESO_SEGUNDOS
import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorProhibido
import ERRORES.ErrorValidacion
import MODELOS.LectorUsuarioSesion
import MODELOS.RepositorioRefreshToken
import com.auth0.jwt.JWT
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

private const val BYTES_REFRESH_TOKEN = 32

data class ParTokens(val tokenAcceso: String, val tokenRefresco: String)

/**
 * Emite y rota tokens de sesión:
 * - access token JWT corto con el rol del usuario;
 * - refresh token opaco, guardado solo como hash SHA-256 y rotado en cada uso.
 */
class ServicioToken(
    private val repositorio: RepositorioRefreshToken,
    private val usuarios: LectorUsuarioSesion,
    private val jwt: ConfiguracionJwt,
    private val reloj: Clock = Clock.systemUTC()
) {
    private val generadorAleatorio = SecureRandom()

    suspend fun emitirPar(usuarioId: UUID, rol: String): ParTokens {
        val ahora = reloj.instant()
        val tokenRefresco = generarTokenOpaco()
        repositorio.guardar(
            usuarioId = usuarioId,
            hashToken = hashear(tokenRefresco),
            emitidoEn = ahora,
            expiraEn = ahora.plus(DIAS_VIGENCIA_REFRESH_TOKEN, ChronoUnit.DAYS)
        )
        return ParTokens(tokenAcceso = firmarTokenAcceso(usuarioId, rol), tokenRefresco = tokenRefresco)
    }

    /**
     * Cambia un refresh token válido por un par nuevo. El rol se vuelve a leer de la BD
     * para que un cambio de rol o una desactivación se apliquen en la siguiente renovación.
     */
    suspend fun rotar(tokenRefresco: String): ParTokens {
        val registro = repositorio.buscarPorHash(hashear(exigirToken(tokenRefresco)))
            ?: throw refreshInvalido()

        if (registro.estaRevocado) {
            // Un token ya rotado que vuelve a usarse indica robo: se cierran todas las sesiones del usuario.
            repositorio.revocarTodosDelUsuario(registro.usuarioId)
            throw refreshInvalido()
        }
        if (!repositorio.revocarSiActivo(registro.id, reloj.instant())) throw refreshInvalido()

        val usuario = usuarios.buscarSesionPorId(registro.usuarioId) ?: throw refreshInvalido()
        if (!usuario.estaActivo) throw ErrorProhibido("inactive_user", "La cuenta no está activa")

        return emitirPar(usuario.id, usuario.rol)
    }

    /** Cierra la sesión del refresh token. Es idempotente: un token desconocido no es error. */
    suspend fun revocar(tokenRefresco: String) {
        val registro = repositorio.buscarPorHash(hashear(exigirToken(tokenRefresco))) ?: return
        repositorio.revocarSiActivo(registro.id, reloj.instant())
    }

    /** Tras restablecer la contraseña o desactivar la cuenta: ningún refresh emitido antes sirve. */
    suspend fun cerrarTodasLasSesiones(usuarioId: UUID) {
        repositorio.revocarTodosDelUsuario(usuarioId)
    }

    private fun exigirToken(tokenRefresco: String): String =
        tokenRefresco.trim().ifEmpty { throw ErrorValidacion("missing_refresh", "Falta el refresh token") }

    private fun refreshInvalido() =
        ErrorNoAutorizado("invalid_refresh", "El refresh token no es válido o ya expiró")

    private fun firmarTokenAcceso(usuarioId: UUID, rol: String): String {
        val ahora = reloj.instant()
        return JWT.create()
            .withIssuer(jwt.emisor)
            .withAudience(jwt.audiencia)
            .withSubject(usuarioId.toString())
            .withClaim("role", rol)
            .withIssuedAt(Date.from(ahora))
            .withExpiresAt(Date.from(ahora.plusSeconds(TTL_TOKEN_ACCESO_SEGUNDOS.toLong())))
            .sign(jwt.algoritmo)
    }

    private fun generarTokenOpaco(): String {
        val bytes = ByteArray(BYTES_REFRESH_TOKEN).also(generadorAleatorio::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashear(token: String): String {
        val resumen = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(resumen)
    }
}
