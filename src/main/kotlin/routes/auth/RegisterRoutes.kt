package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.hashPassword
import security.issueAccessToken
import security.generateRefreshToken
import java.time.LocalDate
import java.time.format.DateTimeParseException

// Regex iguales a tus CHECKs de BD
private val EMAIL_RE = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PAIS_RE  = Regex("^[A-Za-z]{2}$")

fun Route.registerRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    post("/register") {
        try {
            val req = call.receive<RegisterReq>()
            val email = req.email.trim().lowercase()

            if (!EMAIL_RE.matches(email)) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRes("invalid_email")
                )
            }
            if (req.password.length < 8) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRes("weak_password")
                )
            }
            if (!req.pais.isNullOrBlank() && !PAIS_RE.matches(req.pais)) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRes("invalid_country")
                )
            }
            if (AuthDeps.users.existsByEmail(email)) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    ErrorRes("email_in_use")
                )
            }

            // Parseo opcional de fechaNacimiento (si viene)
            val fechaNacimientoParsed = try {
                req.fechaNacimiento
                    ?.takeIf { it.isNotBlank() }
                    ?.let { LocalDate.parse(it) }   // formato ISO: "YYYY-MM-DD"
            } catch (e: DateTimeParseException) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRes("invalid_birthdate")
                )
            }

            // Teléfono limpio (si viene vacío lo dejamos como null)
            val telefonoLimpio = req.telefono?.takeIf { it.isNotBlank() }

            val userId = AuthDeps.users.create(
                email = email,
                hash = hashPassword(req.password),
                nombre = req.nombre,
                idioma = req.idioma,
                // Nuevos campos
                telefono = telefonoLimpio,
                fechaNacimiento = fechaNacimientoParsed,
                genero = req.genero
                // rol = "user" por defecto
                // origenRegistro = "local" por defecto en el repo
            )

            val wantsProfile = sequenceOf(
                req.nivelExperiencia,
                req.area,
                req.pais,
                req.notaObjetivos,
                req.flagsAccesibilidad
            ).any { it != null }

            if (wantsProfile) {
                AuthDeps.profiles.create(
                    userId = userId,
                    nivelExperiencia = req.nivelExperiencia,
                    area = req.area,
                    pais = req.pais,
                    notaObjetivos = req.notaObjetivos,
                    flagsAccesibilidad = req.flagsAccesibilidad
                )
            }

            // Tokens
            val accessTtlSec = 15 * 60
            val access = issueAccessToken(
                subject = userId.toString(),
                issuer = issuer,
                audience = audience,
                algorithm = algorithm,
                ttlSeconds = accessTtlSec,
                extraClaims = mapOf("role" to "user") // default al registrarse
            )

            val refreshPlain = generateRefreshToken()
            issueNewRefresh(AuthDeps.refreshRepo, refreshPlain, userId)

            call.respond(
                HttpStatusCode.Created,
                LoginOk(accessToken = access, refreshToken = refreshPlain)
            )
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Register failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
