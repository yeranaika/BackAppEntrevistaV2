package routes.legal

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class LegalDocumentResponse(
    val title: String,
    val version: String,
    val type: String, // "eula", "terms", "privacy"
    val contentMarkdown: String
)

fun Route.legalRoutes() {
    route("/api/v1/legal") {

        get("/eula") {
            val file = File("docs/legal/EULA.md")
            val content = if (file.exists()) file.readText() else "EULA no disponible"
            call.respond(
                HttpStatusCode.OK,
                LegalDocumentResponse(
                    title = "Acuerdo de Licencia de Usuario Final (EULA)",
                    version = "1.0.0",
                    type = "eula",
                    contentMarkdown = content
                )
            )
        }

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
    }
}
