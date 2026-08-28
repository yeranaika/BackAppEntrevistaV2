// src/main/kotlin/routes/consent/ConsentRoutes.kt
package routes.consent

import data.mapper.toConsentRes
import data.models.usuarios.ConsentTextRes
import data.models.usuarios.CreateConsentReq
import data.models.usuarios.CreateConsentTextReq
import data.models.usuarios.RevokeRes
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.ConsentTextRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.ConsentRoutes(
    consentRepo: ConsentimientoRepository,
    consentTextRepo: ConsentTextRepository
) {

    // Texto vigente del consentimiento
    route("/consent") {
        get("/current") {
            val current = consentTextRepo.getCurrent()
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    "No hay texto de consentimiento vigente"
                )

            call.respond(
                ConsentTextRes(
                    version = current.version,
                    title = current.title,
                    body = current.body
                )
            )
        }
    }

    authenticate("auth-jwt") {

        // SOLO ADMIN: definir/actualizar texto
        route("/admin/consent") {

            post("/text") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val role = principal.getClaim("role", String::class) ?: "user"
                if (role != "admin") {
                    return@post call.respond(HttpStatusCode.Forbidden, "Solo administradores")
                }

                val body = runCatching { call.receive<CreateConsentTextReq>() }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "JSON inválido"
                        )
                    }

                val saved = consentTextRepo.createOrUpdate(body)

                call.respond(
                    HttpStatusCode.Created,
                    ConsentTextRes(
                        version = saved.version,
                        title = saved.title,
                        body = saved.body
                    )
                )
            }
        }

        // Consentimiento por usuario
        route("/me/consent") {

            post {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val body = runCatching { call.receive<CreateConsentReq>() }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "JSON inválido"
                        )
                    }

                if (body.version.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "version requerida"
                    )
                }
                if (body.alcances.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "alcances requerido"
                    )
                }

                val row = consentRepo.create(userId, body.version, body.alcances)
                call.respond(HttpStatusCode.Created, row.toConsentRes())
            }

            get("/latest") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val row = consentRepo.latestVigente(userId)
                if (row == null) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(row.toConsentRes())
                }
            }

            post("/revoke") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val updated = consentRepo.revokeLatest(userId)
                if (updated == 0) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        "No hay consentimiento vigente"
                    )
                } else {
                    // OJO: sin nombre de parámetro, para que funcione con revoked
                    call.respond(RevokeRes(true))
                }
            }
        }
    }
}

private fun JWTPrincipal.userIdOrNull(): UUID? =
    runCatching { UUID.fromString(this.subject) }.getOrNull()
