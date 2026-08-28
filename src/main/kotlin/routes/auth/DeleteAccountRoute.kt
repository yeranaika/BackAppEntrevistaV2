package routes.auth

import data.repository.usuarios.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import kotlinx.serialization.Serializable


/**
 * Ruta para eliminar la cuenta del usuario autenticado.
 * Implementa el derecho al olvido según GDPR/LOPD.
 *
 * Endpoint: DELETE /cuenta
 * Autenticación: JWT requerido
 * Body: { "confirmar": "eliminar" }
 *
 * Al eliminar un usuario, se borran automáticamente en cascada:
 * - Tokens de sesión (refresh_token, password_reset)
 * - Perfil y datos personales (perfil_usuario, oauth_account)
 * - Consentimientos (consentimiento)
 * - Suscripciones y pagos (suscripcion, pago)
 * - Perfil, objetivo, consentimientos y preferencias asociadas
 * - Tickets de soporte (ticket)
 * - Membresías institucionales (institucion_miembro, licencia_asignacion)
 * - Cache offline (cache_offline)
 *
 * Los logs de auditoría se anonimizan (ON DELETE SET NULL).
 */
fun Route.deleteAccountRoute(usuarioRepo: UserRepository) {
    authenticate("auth-jwt") {
        delete("/cuenta") {
            try {
                // Obtener el usuario autenticado desde el JWT
                val principal = call.principal<JWTPrincipal>()
                    ?: return@delete call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorRes("unauthorized")
                    )

                val usuarioId = UUID.fromString(principal.payload.subject)

                // Validar el body de confirmación
                val body = try {
                    call.receive<ConfirmarBorradoReq>()
                } catch (_: ContentTransformationException) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("invalid_json")
                    )
                }

                // Verificar que el usuario escriba explícitamente "eliminar"
                if (body.confirmar.trim().lowercase() != "eliminar") {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("must_type_eliminar")
                    )
                }

                // Ejecutar el borrado (cascada automática en DB)
                val deleted = usuarioRepo.deleteById(usuarioId)

                if (!deleted) {
                    // El usuario no existe (podría haber sido eliminado en otra sesión)
                    return@delete call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("user_not_found")
                    )
                }

                // Borrado exitoso
                call.respond(
                    HttpStatusCode.OK,
                    DeleteAccountOk(
                        message = "Cuenta eliminada exitosamente. Todos tus datos han sido borrados de forma permanente."
                    )
                )

            } catch (e: IllegalArgumentException) {
                // UUID inválido en el JWT
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorRes("invalid_user_id")
                )
            } catch (t: Throwable) {
                // Error inesperado
                call.application.environment.log.error("Error al eliminar cuenta", t)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorRes("server_error")
                )
            }
        }
    }
}
