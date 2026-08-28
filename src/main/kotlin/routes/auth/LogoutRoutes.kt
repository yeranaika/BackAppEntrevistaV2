package routes.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.hashRefreshToken

fun Route.logoutRoutes() {
    post("/logout") {
        try {
            val req = call.receive<LogoutReq>()
            val provided = req.refreshToken.trim()

            if (provided.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("missing_refresh"))
            }

            val hash = hashRefreshToken(provided)
            val found = AuthDeps.refreshRepo.findActiveByHash(hash)

            if (found == null) {
                // Token no encontrado o ya revocado - devolvemos éxito de todas formas por seguridad
                return@post call.respond(OkRes())
            }

            // Revocar el refresh token
            AuthDeps.refreshRepo.revoke(found.id)

            call.respond(OkRes())
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Logout failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
