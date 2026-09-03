package routes.skills

import data.models.skills.CargoLevelItem
import data.models.skills.CargoSkillsMatrixResponse
import data.models.skills.TrendingSkillsResponse
import data.repository.skills.CargoSkillRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import services.cache.RedisCacheService
import java.util.UUID

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val logger = LoggerFactory.getLogger("SkillRoutes")

fun Route.skillRoutes(
    cargoSkillRepository: CargoSkillRepository,
    redisCacheService: RedisCacheService
) {
    route("/api/v1") {

        // ==========================================
        // 1. GET /api/v1/cargos (TTL 6 horas en Redis)
        // ==========================================
        get("/cargos") {
            val cacheKey = "cargos:lista"
            val ttl6Hours = 6 * 3600L // 21,600 segundos

            // 1. Intentar servir desde Redis (Cache-Aside)
            val cachedJson = redisCacheService.get(cacheKey)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedCargos = json.decodeFromString<List<CargoLevelItem>>(cachedJson)
                    logger.debug("Cache HIT para clave '{}'", cacheKey)
                    return@get call.respond(HttpStatusCode.OK, cachedCargos)
                } catch (e: Exception) {
                    logger.warn("Error deserializando cache para '{}': {}", cacheKey, e.message)
                }
            }

            // 2. Cache MISS o Redis inactivo -> Consultar base de datos
            val cargos = cargoSkillRepository.getCargosList()

            // 3. Guardar en Redis en segundo plano
            if (cargos.isNotEmpty()) {
                try {
                    val jsonToCache = json.encodeToString(cargos)
                    redisCacheService.set(cacheKey, jsonToCache, ttl6Hours)
                } catch (e: Exception) {
                    logger.debug("No se pudo cachear en Redis: {}", e.message)
                }
            }

            call.respond(HttpStatusCode.OK, cargos)
        }

        // ==========================================
        // 2. GET /api/v1/cargos/{id}/skills (TTL 12 horas en Redis)
        // ==========================================
        get("/cargos/{id}/skills") {
            val idParam = call.parameters["id"]
            if (idParam.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "invalid_id", "message" to "El ID del cargo es requerido")
                )
            }

            val cargoUuid = try {
                UUID.fromString(idParam)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "invalid_uuid", "message" to "El ID proporcionado no es un UUID válido")
                )
            }

            val cacheKey = "cargo:$idParam:skills"
            val ttl12Hours = 12 * 3600L // 43,200 segundos

            // 1. Intentar servir desde Redis (Cache-Aside)
            val cachedJson = redisCacheService.get(cacheKey)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedMatrix = json.decodeFromString<CargoSkillsMatrixResponse>(cachedJson)
                    logger.debug("Cache HIT para clave '{}'", cacheKey)
                    return@get call.respond(HttpStatusCode.OK, cachedMatrix)
                } catch (e: Exception) {
                    logger.warn("Error deserializando cache para '{}': {}", cacheKey, e.message)
                }
            }

            // 2. Cache MISS o Redis inactivo -> Consultar base de datos
            val matrix = cargoSkillRepository.getCargoSkillsMatrix(cargoUuid)
            if (matrix == null) {
                return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "cargo_not_found", "message" to "No se encontró el cargo especificado")
                )
            }

            // 3. Guardar en Redis
            try {
                val jsonToCache = json.encodeToString(matrix)
                redisCacheService.set(cacheKey, jsonToCache, ttl12Hours)
            } catch (e: Exception) {
                logger.debug("No se pudo cachear en Redis: {}", e.message)
            }

            call.respond(HttpStatusCode.OK, matrix)
        }

        // ==========================================
        // 3. GET /api/v1/skills/trending
        // ==========================================
        get("/skills/trending") {
            val categoria = call.request.queryParameters["categoria"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val cacheKey = "skills:trending:${categoria?.lowercase() ?: "all"}:$limit"
            val ttl1Hour = 3600L

            // 1. Intentar servir desde Redis
            val cachedJson = redisCacheService.get(cacheKey)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cachedTrending = json.decodeFromString<TrendingSkillsResponse>(cachedJson)
                    logger.debug("Cache HIT para clave '{}'", cacheKey)
                    return@get call.respond(HttpStatusCode.OK, cachedTrending)
                } catch (e: Exception) {
                    logger.warn("Error deserializando cache para '{}': {}", cacheKey, e.message)
                }
            }

            // 2. Cache MISS -> Consultar base de datos
            val trending = cargoSkillRepository.getTrendingSkills(categoria, limit)

            // 3. Guardar en Redis
            try {
                val jsonToCache = json.encodeToString(trending)
                redisCacheService.set(cacheKey, jsonToCache, ttl1Hour)
            } catch (e: Exception) {
                logger.debug("No se pudo cachear en Redis: {}", e.message)
            }

            call.respond(HttpStatusCode.OK, trending)
        }
    }
}
