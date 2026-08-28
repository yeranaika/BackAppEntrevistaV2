package routes.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.hashPassword
import java.util.UUID

fun Route.resetRoutes() {

    // Solicitar reset (devuelve code/token SOLO para dev)
    post("/request-reset") {
        try {
            val req = call.receive<RequestResetReq>()
            val info = AuthDeps.resets.createForEmail(req.email)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorRes("email_not_found"))

            call.respond(HttpStatusCode.Created, RequestResetOk(token = info.token.toString(), code = info.code))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Request-reset failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }

    // Confirmar reset (consume token+code, setea nueva pass)
    post("/confirm-reset") {
        try {
            val req = call.receive<ConfirmResetReq>()
            if (req.newPassword.length < 8) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("weak_password"))
            }

            val userId = AuthDeps.resets.consume(UUID.fromString(req.token), req.code)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_or_expired"))

            AuthDeps.users.updatePassword(userId, hashPassword(req.newPassword))
            call.respond(OkRes())
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Confirm-reset failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
