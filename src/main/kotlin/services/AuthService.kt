package services

import CONFIGURACION.LARGO_MINIMO_CONTRASENA
import CONFIGURACION.TTL_TOKEN_ACCESO_SEGUNDOS
import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.RefreshTokenRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepository
import models.RegisterReq
import models.LoginReq
import models.UpdateProfileReq
import routes.auth.issueNewRefresh
import security.auth.GoogleTokenVerifier
import security.generateRefreshToken
import security.hashPassword
import security.issueAccessToken
import security.verifyPassword
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Lógica de negocio del dominio de autenticación: registro, login (local y Google),
 * y actualización del perfil básico del usuario logueado.
 *
 * Los controllers son delgados: reciben el request, llaman aquí, y solo traducen
 * el resultado (o la excepción tipada) a una respuesta HTTP.
 */
class AuthService(
    private val users: UserRepository,
    private val profiles: ProfileRepository,
    private val refreshRepo: RefreshTokenRepository,
    private val oauthRepo: UsuariosOAuthRepository,
    private val googleVerifier: GoogleTokenVerifier
) {

    sealed class AuthException(val publicCode: String) : RuntimeException(publicCode)
    class InvalidEmailException : AuthException("invalid_email")
    class WeakPasswordException : AuthException("weak_password")
    class InvalidCountryException : AuthException("invalid_country")
    class InvalidBirthdateException : AuthException("invalid_birthdate")
    class EmailInUseException : AuthException("email_in_use")
    class BadCredentialsException : AuthException("bad_credentials")
    class InactiveUserException : AuthException("inactive_user")
    class GoogleTokenInvalidException : AuthException("invalid_google_token")
    class GoogleEmailNotVerifiedException : AuthException("google_email_not_verified")
    class UserNotFoundException : AuthException("user_not_found")
    class InvalidLanguageException : AuthException("idioma_invalido")
    class InvalidPhoneException : AuthException("telefono_invalido")
    class InvalidGenderException : AuthException("genero_invalido")
    class InvalidBirthdateFormatException : AuthException("fecha_nacimiento_invalida")
    class InvalidBirthdateRangeException : AuthException("fecha_nacimiento_fuera_de_rango")
    class NothingToUpdateException : AuthException("nothing_to_update")

    data class TokenResult(val accessToken: String, val refreshToken: String)

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val paisRegex = Regex("^[A-Za-z]{2}$")
    private val telefonoRegex = Regex("^\\+?[0-9]{7,20}$")
    private val idiomasValidos = setOf("es", "en", "pt", "fr", "de")
    private val generosValidos = setOf(
        "masculino", "femenino", "no_binario", "otro", "prefiere_no_decirlo"
    )

    /** Registro local: valida, crea usuario (+ perfil opcional) y emite tokens. */
    suspend fun register(
        req: RegisterReq,
        issuer: String,
        audience: String,
        algorithm: Algorithm
    ): TokenResult {
        val email = req.email.trim().lowercase()

        if (!emailRegex.matches(email)) throw InvalidEmailException()
        if (req.password.length < LARGO_MINIMO_CONTRASENA) throw WeakPasswordException()
        if (!req.pais.isNullOrBlank() && !paisRegex.matches(req.pais)) throw InvalidCountryException()
        if (users.existsByEmail(email)) throw EmailInUseException()

        val fechaNacimientoParsed = try {
            req.fechaNacimiento?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            throw InvalidBirthdateException()
        }

        val telefonoLimpio = req.telefono?.takeIf { it.isNotBlank() }

        val userId = users.create(
            email = email,
            hash = hashPassword(req.password),
            nombre = req.nombre,
            idioma = req.idioma,
            telefono = telefonoLimpio,
            fechaNacimiento = fechaNacimientoParsed,
            genero = req.genero
            // rol = "user" y origenRegistro = "local" por defecto en el repo
        )

        val wantsProfile = sequenceOf(
            req.nivelExperiencia, req.area, req.pais, req.notaObjetivos, req.flagsAccesibilidad
        ).any { it != null }

        if (wantsProfile) {
            profiles.create(
                userId = userId,
                nivelExperiencia = req.nivelExperiencia,
                area = req.area,
                pais = req.pais,
                notaObjetivos = req.notaObjetivos,
                flagsAccesibilidad = req.flagsAccesibilidad
            )
        }

        return issueTokens(userId, issuer, audience, algorithm, role = "user")
    }

    /** Login local: valida credenciales y emite tokens. */
    suspend fun login(
        req: LoginReq,
        issuer: String,
        audience: String,
        algorithm: Algorithm
    ): TokenResult {
        val email = req.email.trim().lowercase()

        val user = users.findByEmail(email) ?: throw BadCredentialsException()
        if (user.estado != "activo") throw InactiveUserException()
        if (!verifyPassword(req.password, user.hash)) throw BadCredentialsException()

        users.touchUltimoLogin(user.id)

        return issueTokens(userId = user.id, issuer = issuer, audience = audience, algorithm = algorithm, role = user.rol)
    }

    /**
     * Login con Google (idToken verificado del lado del servidor). Si el usuario no existe,
     * lo autoregistra (lo enlaza o lo crea) — cubre tanto el flujo móvil (POST /auth/google)
     * como el flujo web (callback OAuth), que comparten esta misma lógica.
     */
    suspend fun loginWithGoogle(
        idToken: String,
        issuer: String,
        audience: String,
        algorithm: Algorithm
    ): TokenResult {
        val payload = try {
            googleVerifier.verify(idToken)
        } catch (e: Exception) {
            // El verificador de Google lanza (no solo devuelve null) ante un idToken malformado
            null
        } ?: throw GoogleTokenInvalidException()

        val email = payload["email"] as? String
        val emailVerified = (payload["email_verified"] as? Boolean) ?: false
        if (!emailVerified || email.isNullOrBlank()) throw GoogleEmailNotVerifiedException()

        // Autoregistro: crea el usuario si no existía, o lo enlaza si ya tenía cuenta local
        val userId = oauthRepo.linkOrCreateFromGoogle(payload.subject, email)

        return issueTokens(userId, issuer, audience, algorithm, role = "user")
    }

    /** Actualiza los datos básicos del usuario logueado (nombre, idioma, teléfono, etc.). */
    suspend fun updateProfile(userId: UUID, req: UpdateProfileReq) {
        val user = users.findById(userId) ?: throw UserNotFoundException()

        if (req.idioma != null && req.idioma !in idiomasValidos) throw InvalidLanguageException()

        val telefonoLimpio = req.telefono?.trim()?.takeIf { it.isNotEmpty() }
        if (telefonoLimpio != null && !telefonoRegex.matches(telefonoLimpio)) throw InvalidPhoneException()

        val generoLimpio = req.genero?.trim()
        if (generoLimpio != null && generoLimpio !in generosValidos) throw InvalidGenderException()

        val fechaNacimientoParsed: LocalDate? = if (req.fechaNacimiento != null) {
            if (req.fechaNacimiento.isBlank()) {
                null // "" → borrar
            } else {
                val parsed = try {
                    LocalDate.parse(req.fechaNacimiento)
                } catch (e: DateTimeParseException) {
                    throw InvalidBirthdateFormatException()
                }
                val minDate = LocalDate.of(1900, 1, 1)
                val maxDate = LocalDate.now().minusYears(14)
                if (parsed.isBefore(minDate) || parsed.isAfter(maxDate)) throw InvalidBirthdateRangeException()
                parsed
            }
        } else {
            null // campo ausente → no tocar
        }

        val fechaFinal = if (req.fechaNacimiento == null) user.fechaNacimiento else fechaNacimientoParsed
        val generoFinal = if (req.genero == null) user.genero else generoLimpio

        var touched = 0
        if (req.nombre != null) touched += users.updateNombre(userId, req.nombre)
        if (req.idioma != null) touched += users.updateIdioma(userId, req.idioma)
        if (req.telefono != null) touched += users.updateTelefono(userId, telefonoLimpio)
        if (req.fechaNacimiento != null || req.genero != null) {
            touched += users.updateDatosDemograficos(userId = userId, fechaNacimiento = fechaFinal, genero = generoFinal)
        }

        if (touched == 0) throw NothingToUpdateException()
    }

    private suspend fun issueTokens(
        userId: UUID,
        issuer: String,
        audience: String,
        algorithm: Algorithm,
        role: String
    ): TokenResult {
        val access = issueAccessToken(
            subject = userId.toString(),
            issuer = issuer,
            audience = audience,
            algorithm = algorithm,
            ttlSeconds = TTL_TOKEN_ACCESO_SEGUNDOS,
            extraClaims = mapOf("role" to role)
        )

        val refreshPlain = generateRefreshToken()
        issueNewRefresh(refreshRepo, refreshPlain, userId)

        return TokenResult(accessToken = access, refreshToken = refreshPlain)
    }
}
