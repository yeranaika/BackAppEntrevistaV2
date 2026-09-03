package routes.legal

import data.models.usuarios.CreateConsentTextReq
import data.repository.usuarios.ConsentTextRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class LegalDocumentResponse(
    val title: String,
    val version: String,
    val type: String, // "eula", "terms", "privacy"
    val contentMarkdown: String,
    val vigente: Boolean = true,
    val fechaPublicacion: String? = null
)

@Serializable
data class EulaVersionItem(
    val version: String,
    val title: String,
    val vigente: Boolean,
    val fechaPublicacion: String? = null
)

fun Route.legalRoutes(consentTextRepo: ConsentTextRepository) {
    route("/api/v1/legal") {

        // =========================================================================
        // 1. GET /api/v1/legal/eula -> EULA VIGENTE DESDE LA BASE DE DATOS
        //    (Sirve el EULA actual a la aplicación de Android)
        // =========================================================================
        get("/eula") {
            val currentEula = consentTextRepo.getCurrent()
            call.respond(
                HttpStatusCode.OK,
                LegalDocumentResponse(
                    title = currentEula.title,
                    version = currentEula.version,
                    type = "eula",
                    contentMarkdown = currentEula.body,
                    vigente = currentEula.vigente,
                    fechaPublicacion = currentEula.fechaPublicacion
                )
            )
        }

        // =========================================================================
        // 2. GET /api/v1/legal/versions -> HISTORIAL DE VERSIONES (Panel Administrador)
        // =========================================================================
        get("/versions") {
            val versions = consentTextRepo.getAllVersions().map {
                EulaVersionItem(
                    version = it.version,
                    title = it.title,
                    vigente = it.vigente,
                    fechaPublicacion = it.fechaPublicacion
                )
            }
            call.respond(HttpStatusCode.OK, versions)
        }

        // =========================================================================
        // 3. GET /api/v1/legal/terms
        // =========================================================================
        get("/terms") {
            val file = File("docs/legal/TERMINOS_DE_SERVICIO.md")
            val content = if (file.exists()) file.readText() else "Términos no disponibles"
            call.respond(
                HttpStatusCode.OK,
                LegalDocumentResponse(
                    title = "Términos y Condiciones de Servicio",
                    version = "1.0.0",
                    type = "terms",
                    contentMarkdown = content
                )
            )
        }

        // =========================================================================
        // 4. GET /api/v1/legal/privacy
        // =========================================================================
        get("/privacy") {
            val file = File("docs/legal/POLITICA_DE_PRIVACIDAD.md")
            val content = if (file.exists()) file.readText() else "Política de privacidad no disponible"
            call.respond(
                HttpStatusCode.OK,
                LegalDocumentResponse(
                    title = "Política de Privacidad y Protección de Datos",
                    version = "1.0.0",
                    type = "privacy",
                    contentMarkdown = content
                )
            )
        }

        // =========================================================================
        // 5. POST /api/v1/legal/admin/eula -> PUBLICAR NUEVA VERSIÓN (Panel Administrador)
        // =========================================================================
        authenticate("auth-jwt") {
            post("/admin/eula") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val role = principal.getClaim("role", String::class) ?: "user"
                if (role != "admin") {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Solo administradores pueden publicar nuevas versiones del EULA"))
                }

                val body = runCatching { call.receive<CreateConsentTextReq>() }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "invalid_json", "message" to "JSON inválido")
                        )
                    }

                if (body.version.isBlank() || body.body.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "missing_fields", "message" to "version y body son obligatorios")
                    )
                }

                val saved = consentTextRepo.createOrUpdate(body)

                call.respond(
                    HttpStatusCode.Created,
                    LegalDocumentResponse(
                        title = saved.title,
                        version = saved.version,
                        type = "eula",
                        contentMarkdown = saved.body,
                        vigente = true
                    )
                )
            }
        }
    }
}
