package routes.market

import data.repository.market.SkillMarketRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import services.market.SkillTrendWorker
import java.util.UUID

fun Route.marketRoutes(
    skillMarketRepository: SkillMarketRepository,
    skillTrendWorker: SkillTrendWorker
) {
    route("/market") {
        // Listar catálogo de skills ordenadas por demanda
        get("/skills") {
            try {
                val skills = skillMarketRepository.getAllActiveSkills()
                call.respond(HttpStatusCode.OK, skills)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al obtener skills")))
            }
        }

        // Historial semanal de tendencias de una skill
        get("/skills/{id}/tendencias") {
            val idParam = call.parameters["id"]
            if (idParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID de skill requerido"))
                return@get
            }
            val skillUuid = try {
                UUID.fromString(idParam)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Formato de UUID inválido"))
                return@get
            }

            try {
                val trends = skillMarketRepository.getSkillTrendsHistory(skillUuid)
                call.respond(HttpStatusCode.OK, trends)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al consultar historial")))
            }
        }
    }

    // Ruta administrativa para sincronizar tendencias bajo demanda
    route("/admin/market") {
        post("/sync-trends") {
            try {
                val result = skillTrendWorker.syncMarketTrends()
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al sincronizar tendencias")))
            }
        }
    }
}
