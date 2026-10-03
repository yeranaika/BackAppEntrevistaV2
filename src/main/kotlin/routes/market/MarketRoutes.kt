package routes.market

import MIDDLEWARES.soloAdmin
import UTILIDADES.uuidDeParametro
import data.models.market.*
import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import services.market.CargoSkillGeneratorService
import services.market.SkillTrendWorker
import java.util.UUID

// Los errores no controlados los responde CONFIGURACION_ERRORES (500 sin exponer detalles internos).
fun Route.marketRoutes(
    skillMarketRepository: SkillMarketRepository,
    cargoRepository: CargoRepository,
    cargoSkillGenerator: CargoSkillGeneratorService,
    skillTrendWorker: SkillTrendWorker
) {
    route("/market") {
        // Listar catálogo de skills ordenadas por demanda
        get("/skills") {
            call.respond(HttpStatusCode.OK, skillMarketRepository.getAllActiveSkills())
        }

        // Historial semanal de tendencias de una skill
        get("/skills/{id}/tendencias") {
            val skillId = call.uuidDeParametro("id")
            call.respond(HttpStatusCode.OK, skillMarketRepository.getSkillTrendsHistory(skillId))
        }

        // Listar todas las carreras / cargos
        get("/cargos") {
            call.respond(HttpStatusCode.OK, cargoRepository.getAllCargos())
        }

        // Obtener skills y pesos exigidos por el mercado para una carrera específica
        get("/cargos/{id}/skills") {
            val cargoId = call.uuidDeParametro("id")
            call.respond(HttpStatusCode.OK, cargoRepository.getCargoSkillsWithDetails(cargoId))
        }
    }

    // Rutas administrativas: consumen cuota de la API de mercado laboral, solo admin.
    route("/admin/market") {
        soloAdmin {
            // Sincronizar tendencias generales de skills
            post("/sync-trends") {
                call.respond(HttpStatusCode.OK, skillTrendWorker.syncMarketTrends())
            }

            // Crear una nueva carrera / cargo y generar automáticamente sus skills desde el mercado
            post("/cargos") {
                val req = call.receive<CreateCargoReq>()
                val createdCargo = cargoRepository.createCargo(
                    nombre = req.nombre,
                    area = req.area,
                    descripcion = req.descripcion,
                    nivelBase = req.nivelBase
                )
                val generatedSkills = if (req.autoGenerateSkills) {
                    cargoSkillGenerator.generateRequirementsForCargo(UUID.fromString(createdCargo.cargoId))
                } else {
                    null
                }
                call.respond(
                    HttpStatusCode.Created,
                    CreateCargoResponse(cargo = createdCargo, generatedSkills = generatedSkills)
                )
            }

            // Regenerar / actualizar requisitos para un cargo específico
            post("/cargos/{id}/generate-requirements") {
                val cargoId = call.uuidDeParametro("id")
                call.respond(HttpStatusCode.OK, cargoSkillGenerator.generateRequirementsForCargo(cargoId))
            }

            // Generar / actualizar requisitos para todas las carreras registradas
            post("/cargos/generate-all") {
                val results = cargoSkillGenerator.generateRequirementsForAllCargos()
                call.respond(
                    HttpStatusCode.OK,
                    BulkCargoGenerationResponse(totalCargosProcessed = results.size, results = results)
                )
            }
        }
    }
}
