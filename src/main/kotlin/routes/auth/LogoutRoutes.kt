package routes.auth

import data.repository.usuarios.RefreshTokenRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ESQUEMAS.RespuestaError
import models.LogoutReq
import ESQUEMAS.RespuestaOk
import security.hashRefreshToken

fun Route.logoutRoutes(refreshRepo: RefreshTokenRepository) {
    post("/logout") {
        try {
            val req = call.receive<LogoutReq>()
            val provided = req.refreshToken.trim()

            if (provided.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, RespuestaError("missing_refresh"))
            }

            val hash = hashRefreshToken(provided)
            val found = refreshRepo.findActiveByHash(hash)

            if (found == null) {
                // Token no encontrado o ya revocado - devolvemos éxito de todas formas por seguridad
                return@post call.respond(RespuestaOk())
            }

            // Revocar el refresh token
            refreshRepo.revoke(found.id)

            call.respond(RespuestaOk())
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Logout failed", t)
            call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
        }
    }
}
