package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.hashRefreshToken
import security.generateRefreshToken
import security.issueAccessToken

fun Route.refreshRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    post("/refresh") {
        try {
            val req = call.receive<RefreshReq>()
            val provided = req.refreshToken.trim()
            if (provided.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("missing_refresh"))
            }

            val hash = hashRefreshToken(provided)
            val found = AuthDeps.refreshRepo.findActiveByHash(hash)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("invalid_refresh"))

            // Rotación: revocamos el anterior y emitimos uno nuevo
            AuthDeps.refreshRepo.revoke(found.id)

            val newPlain = generateRefreshToken()
            issueNewRefresh(AuthDeps.refreshRepo, newPlain, found.userId)

            val newAccess = issueAccessToken(
                subject = found.userId.toString(),
                issuer = issuer,
                audience = audience,
                algorithm = algorithm,
                ttlSeconds = 15 * 60
                // (opcional) podrías consultar el rol en BD y volver a firmarlo
            )
            call.respond(RefreshOk(accessToken = newAccess, refreshToken = newPlain))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Refresh failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
