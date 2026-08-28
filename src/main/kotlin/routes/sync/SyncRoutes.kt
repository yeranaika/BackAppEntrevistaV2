package routes.sync

import data.models.sync.FreemiumEvaluateRequest
import data.models.sync.SyncAttemptBatchRequest
import data.models.sync.SyncResponse
import data.repository.sync.SyncRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

fun Route.syncRoutes(syncRepo: SyncRepository) {

    route("/api/v1") {

        // Evaluación de texto Freemium (cero consumo de tokens de IA)
        post("/practice/evaluate-freemium") {
            val req = call.receive<FreemiumEvaluateRequest>()
            val result = syncRepo.evaluateFreemiumText(req)
            call.respond(HttpStatusCode.OK, result)
        }

        // Sincronización de intentos realizados offline (Requiere autenticación)
        authenticate("auth-jwt") {
            post("/sync/attempts") {
                val userId = call.userIdFromJwt()
                val req = call.receive<SyncAttemptBatchRequest>()

                val mappings = syncRepo.syncOfflineAttempts(userId, req.attempts)

                call.respond(
                    HttpStatusCode.OK,
                    SyncResponse(
                        success = true,
                        syncedCount = mappings.size,
                        mappings = mappings
                    )
                )
            }
        }
    }
}

