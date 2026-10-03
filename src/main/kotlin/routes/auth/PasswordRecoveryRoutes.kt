package routes.auth

import CONFIGURACION.LARGO_MINIMO_CONTRASENA
import data.models.auth.ForgotPasswordReq
import data.models.auth.ForgotPasswordRes
import data.models.auth.ResetPasswordReq
import data.models.auth.ResetPasswordRes
import data.models.auth.ChangePasswordReq
import data.repository.usuarios.PasswordResetRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import security.hashPassword
import services.EmailService
import java.util.UUID

fun Route.passwordRecoveryRoutes(
    passwordResetRepo: PasswordResetRepository,
    emailService: EmailService,
    users: UserRepository,
    oauthRepo: UsuariosOAuthRepository
) {
    // ============================
    // POST /auth/forgot-password
    // ============================
    post("/auth/forgot-password") {
        val body = runCatching { call.receive<ForgotPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

        val correo = body.correo.trim().lowercase()
        if (correo.isBlank() || !correo.contains("@")) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Correo electrónico inválido")
            )
        }

        try {
            val resetInfo = passwordResetRepo.createForEmail(correo)

            if (resetInfo == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordRes(
                        message = "No existe ningún usuario registrado con ese correo"
                    )
                )
            }

            if (oauthRepo.isGoogleUser(resetInfo.userId)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordRes(
                        message = "Esta cuenta fue creada con Google. " +
                                  "Por favor, inicia sesión con Google."
                    )
                )
            }

            try {
                emailService.sendRecoveryCode(
                    correo,
                    resetInfo.code
                )
            } catch (emailEx: Exception) {
                call.application.environment.log.error(
                    "Error al enviar email a $correo: ${emailEx.message}"
                )
            }

            call.respond(
                HttpStatusCode.OK,
                ForgotPasswordRes(
                    message = "Te enviamos un código a tu correo"
                )
            )
        } catch (ex: Exception) {
            call.application.environment.log.error(
                "Error en forgot-password: ${ex.message}"
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }

    // ============================
    // POST /auth/reset-password
    // ============================
    post("/auth/reset-password") {
        val body = runCatching { call.receive<ResetPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

        val correo = body.correo.trim().lowercase()
        val codigo = body.codigo.trim()
        val nuevaContrasena = body.nuevaContrasena

        if (correo.isBlank() || !correo.contains("@")) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Correo electrónico inválido")
            )
        }

        if (codigo.length != 6 || !codigo.all { it.isDigit() }) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Código inválido")
            )
        }

        if (nuevaContrasena.length < LARGO_MINIMO_CONTRASENA) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "La contraseña debe tener al menos $LARGO_MINIMO_CONTRASENA caracteres")
            )
        }

        try {
            val usuarioId = passwordResetRepo.consumeByEmail(correo, codigo)

            if (usuarioId == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResetPasswordRes(
                        message = "Código inválido o expirado"
                    )
                )
            }

            if (oauthRepo.isGoogleUser(usuarioId)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResetPasswordRes(
                        message = "Esta cuenta fue creada con Google. " +
                                  "Por favor, inicia sesión con Google."
                    )
                )
            }

            val updated = users.updatePasswordHash(usuarioId, hashPassword(nuevaContrasena))

            if (updated == 0) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al actualizar la contraseña")
                )
            }

            call.respond(
                HttpStatusCode.OK,
                ResetPasswordRes(
                    message = "Contraseña actualizada exitosamente"
                )
            )
        } catch (ex: Exception) {
            call.application.environment.log.error(
                "Error en reset-password: ${ex.message}"
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }

    // ============================
    // POST /auth/change-password
    // (perfil: token + NUEVA contraseña)
    // ============================
    authenticate("auth-jwt") {
        post("/auth/change-password") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val payload = principal.payload

            // Tomamos SIEMPRE el usuario desde subject/sub (UUID del usuario)
            val userIdStr =
                payload.getClaim("sub").asString()
                    ?: payload.subject
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("message" to "Token inválido: falta subject")
                    )

            val usuarioId = try {
                UUID.fromString(userIdStr)
            } catch (e: IllegalArgumentException) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Token inválido: subject no es un UUID válido")
                )
            }

            val body = runCatching { call.receive<ChangePasswordReq>() }
                .getOrElse {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "JSON inválido")
                    )
                }

            val nuevaContrasena = body.nuevaContrasena

            if (nuevaContrasena.length < LARGO_MINIMO_CONTRASENA) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "La contraseña debe tener al menos $LARGO_MINIMO_CONTRASENA caracteres")
                )
            }

            try {
                // No dejamos cambiar pass si es usuario Google
                if (oauthRepo.isGoogleUser(usuarioId)) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "message" to "Esta cuenta fue creada con Google. " +
                                         "No puedes cambiar la contraseña aquí."
                        )
                    )
                }

                val updated = users.updatePasswordHash(usuarioId, hashPassword(nuevaContrasena))

                if (updated == 0) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("message" to "Error al actualizar la contraseña (usuario no encontrado)")
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Contraseña cambiada correctamente")
                )
            } catch (ex: Exception) {
                call.application.environment.log.error(
                    "Error en change-password: ${ex.message}"
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Error al procesar la solicitud")
                )
            }
        }
    }
}
