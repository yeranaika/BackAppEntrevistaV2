package routes.auth

import data.models.usuarios.UpdateObjetivoReq
import data.repository.AppAndroid.OnboardingRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.userIdFromJwt

fun Route.profileRoutes(
    onboardingRepo: OnboardingRepository
) {
    authenticate("auth-jwt") {

        // PUT /perfil/objetivo  → solo guarda área/meta/nivel (update)
        put("/perfil/objetivo") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.userIdFromJwt()
            val body = call.receive<UpdateObjetivoReq>()

            onboardingRepo.guardarObjetivo(
                usuarioId = userId,
                area = body.area,
                metaCargo = body.metaCargo,
                nivel = body.nivel
            )

            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}
