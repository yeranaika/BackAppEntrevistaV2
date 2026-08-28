package routes.usuario.recordatorios

import data.models.usuarios.RecordatorioPreferencias
import data.repository.usuarios.RecordatorioPreferenciaRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import security.userIdOrNull   // extensión que ya tienes para sacar el userId del JWT
import java.util.UUID

@Serializable
data class PreferenciasRecordatorioReq(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val habilitado: Boolean = true
)

@Serializable
data class PreferenciasRecordatorioRes(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val habilitado: Boolean
)

private fun RecordatorioPreferencias.toRes() = PreferenciasRecordatorioRes(
    diasSemana = diasSemana,
    hora = hora,
    tipoPractica = tipoPractica,
    habilitado = habilitado
)

fun Route.recordatorioRoutes(
    recordatorioRepo: RecordatorioPreferenciaRepository
) {
    authenticate("auth-jwt") {
        route("/recordatorios") {

            // GET /recordatorios/preferencias
            get("/preferencias") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdOrNull()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val prefs = recordatorioRepo.getByUsuario(UUID.fromString(userId))

                if (prefs == null) {
                    // 404 si aún no tiene nada configurado
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("message" to "El usuario no tiene preferencias de recordatorios configuradas")
                    )
                } else {
                    call.respond(prefs.toRes())
                }
            }

            // PUT /recordatorios/preferencias
            put("/preferencias") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdOrNull()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val body = call.receive<PreferenciasRecordatorioReq>()

                val saved = recordatorioRepo.upsert(
                    UUID.fromString(userId),
                    RecordatorioPreferencias(
                        diasSemana = body.diasSemana,
                        hora = body.hora,
                        tipoPractica = body.tipoPractica,
                        habilitado = body.habilitado
                    )
                )

                call.respond(saved.toRes())
            }
        }
    }
}
