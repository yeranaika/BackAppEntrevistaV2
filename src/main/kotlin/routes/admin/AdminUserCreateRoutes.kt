package routes.admin

import data.models.admin.AdminCreateUserReq
import data.repository.admin.AdminUserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.isAdmin

fun Route.AdminUserCreateRoutes(repo: AdminUserRepository) {
    authenticate("auth-jwt") {
        post("/admin/users") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val body = runCatching { call.receive<AdminCreateUserReq>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

            val res = runCatching { repo.createByAdmin(body) }
                .getOrElse { ex ->
                    val msg = ex.message ?: "No se pudo crear usuario"
                    return@post when {
                        msg.contains("correo ya registrado", true) ->
                            call.respond(HttpStatusCode.Conflict, msg)
                        msg.contains("rol inválido", true) ||
                        msg.contains("requerido", true) ->
                            call.respond(HttpStatusCode.BadRequest, msg)
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, msg)
                    }
                }

            call.respond(HttpStatusCode.Created, res)
        }
    }
}
