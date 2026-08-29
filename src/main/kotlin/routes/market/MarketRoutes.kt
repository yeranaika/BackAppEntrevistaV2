package routes.market

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

fun Route.marketRoutes(
    skillMarketRepository: SkillMarketRepository,
    cargoRepository: CargoRepository,
    cargoSkillGenerator: CargoSkillGeneratorService,
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

        // Listar todas las carreras / cargos
        get("/cargos") {
            try {
                val cargos = cargoRepository.getAllCargos()
                call.respond(HttpStatusCode.OK, cargos)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al consultar cargos")))
            }
        }

        // Obtener skills y pesos exigidos por el mercado para una carrera específica
        get("/cargos/{id}/skills") {
            val idParam = call.parameters["id"]
            if (idParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID de cargo requerido"))
                return@get
            }
            val cargoUuid = try {
                UUID.fromString(idParam)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Formato de UUID inválido"))
                return@get
            }

            try {
                val skills = cargoRepository.getCargoSkillsWithDetails(cargoUuid)
                call.respond(HttpStatusCode.OK, skills)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al consultar skills del cargo")))
            }
        }
    }

    // Rutas administrativas
    route("/admin/market") {
        // Sincronizar tendencias generales de skills
        post("/sync-trends") {
            try {
                val result = skillTrendWorker.syncMarketTrends()
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al sincronizar tendencias")))
            }
        }

        // Crear una nueva carrera / cargo y generar automáticamente sus skills desde el mercado
        post("/cargos") {
            val req = try {
                call.receive<CreateCargoReq>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de solicitud inválido"))
                return@post
            }

            try {
                val createdCargo = cargoRepository.createCargo(
                    nombre = req.nombre,
                    area = req.area,
                    descripcion = req.descripcion,
                    nivelBase = req.nivelBase
                )

                if (req.autoGenerateSkills) {
                    val cargoUuid = UUID.fromString(createdCargo.cargoId)
                    val genResult = cargoSkillGenerator.generateRequirementsForCargo(cargoUuid)
                    call.respond(HttpStatusCode.Created, CreateCargoResponse(
                        cargo = createdCargo,
                        generatedSkills = genResult
                    ))
                } else {
                    call.respond(HttpStatusCode.Created, CreateCargoResponse(
                        cargo = createdCargo,
                        generatedSkills = null
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al crear cargo")))
            }
        }

        // Regenerar / actualizar requisitos para un cargo específico
        post("/cargos/{id}/generate-requirements") {
            val idParam = call.parameters["id"]
            if (idParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID de cargo requerido"))
                return@post
            }
            val cargoUuid = try {
                UUID.fromString(idParam)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Formato de UUID inválido"))
                return@post
            }

            try {
                val result = cargoSkillGenerator.generateRequirementsForCargo(cargoUuid)
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error al generar requisitos")))
            }
        }

        // Generar / actualizar requisitos para todas las carreras registradas
        post("/cargos/generate-all") {
            try {
                val results = cargoSkillGenerator.generateRequirementsForAllCargos()
                call.respond(HttpStatusCode.OK, BulkCargoGenerationResponse(
                    totalCargosProcessed = results.size,
                    results = results
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error en generación masiva")))
            }
        }
    }
}
