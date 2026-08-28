package routes.onboarding

import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

// ========== DTOs ==========

@Serializable
data class OnboardingRequest(
    val area: String,                    // "Desarrollo", "Analisis TI", "Administracion", "Ingenieria Informatica"
    val nivelExperiencia: String,        // "Junior" (jr), "Semi Senior" (mid), "Senior" (sr)
    val nombreCargo: String,             // "Desarrollador Full Stack", "Analista de Sistemas", etc.
    val descripcionObjetivo: String? = null
)

@Serializable
data class OnboardingStatusRes(
    val completed: Boolean,
    val data: OnboardingData? = null
)

@Serializable
data class OnboardingData(
    val area: String,
    val nivelExperiencia: String,
    val nombreCargo: String,
    val descripcionObjetivo: String?
)

@Serializable
data class OnboardingSaveResponse(
    val success: Boolean,
    val message: String,
    val data: OnboardingData
)

// ========== ROUTES ==========

fun Route.onboardingRoutes(
    profileRepo: ProfileRepository,
    objetivoRepo: ObjetivoCarreraRepository
) {
    authenticate("auth-jwt") {
        route("/onboarding") {

            // POST /onboarding
            // Guarda la información de onboarding del usuario
            post {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()
                val request = call.receive<OnboardingRequest>()

                // Validar datos
                if (request.area.isBlank() || request.nivelExperiencia.isBlank() || request.nombreCargo.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Faltan campos requeridos: area, nivelExperiencia, nombreCargo")
                    )
                }

                // Convertir nivel de experiencia a formato interno (jr, mid, sr)
                val nivelInterno = when (request.nivelExperiencia.lowercase()) {
                    "junior", "jr" -> "jr"
                    "semi senior", "mid", "ssr", "semisenior" -> "mid"
                    "senior", "sr" -> "sr"
                    else -> "jr"  // Por defecto Junior
                }

                try {
                    // 1. Guardar/actualizar perfil del usuario
                    val existingProfile = profileRepo.findByUser(userId)

                    if (existingProfile != null) {
                        // Actualizar perfil existente
                        profileRepo.updatePartial(
                            perfilId = existingProfile.perfilId,
                            area = request.area,
                            nivelExperiencia = nivelInterno,
                            notaObjetivos = request.descripcionObjetivo
                        )
                    } else {
                        // Crear nuevo perfil
                        profileRepo.create(
                            userId = userId,
                            area = request.area,
                            nivelExperiencia = nivelInterno,
                            notaObjetivos = request.descripcionObjetivo
                        )
                    }

                    // 2. Guardar/actualizar objetivo de carrera
                    objetivoRepo.upsert(
                        userId = userId,
                        nombreCargo = request.nombreCargo,
                        sector = request.area
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        OnboardingSaveResponse(
                            success = true,
                            message = "Información de onboarding guardada exitosamente",
                            data = OnboardingData(
                                area = request.area,
                                nivelExperiencia = nivelInterno,
                                nombreCargo = request.nombreCargo,
                                descripcionObjetivo = request.descripcionObjetivo
                            )
                        )
                    )

                } catch (e: Exception) {
                    call.application.environment.log.error("Error guardando onboarding", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error guardando información: ${e.message}")
                    )
                }
            }

            // GET /onboarding/status
            // Verifica si el usuario ha completado el onboarding
            get("/status") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()

                try {
                    val profile = profileRepo.findByUser(userId)
                    val objetivo = objetivoRepo.findByUser(userId)

                    // El onboarding está completo si tiene perfil CON área y nivel, Y objetivo
                    val completed = profile != null &&
                                   !profile.area.isNullOrBlank() &&
                                   !profile.nivelExperiencia.isNullOrBlank() &&
                                   objetivo != null

                    val data = if (completed && profile != null && objetivo != null) {
                        OnboardingData(
                            area = profile.area ?: "",
                            nivelExperiencia = profile.nivelExperiencia ?: "",
                            nombreCargo = objetivo.nombreCargo,
                            descripcionObjetivo = profile.notaObjetivos
                        )
                    } else null

                    call.respond(
                        HttpStatusCode.OK,
                        OnboardingStatusRes(
                            completed = completed,
                            data = data
                        )
                    )

                } catch (e: Exception) {
                    call.application.environment.log.error("Error obteniendo status de onboarding", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error obteniendo información: ${e.message}")
                    )
                }
            }

            // GET /onboarding
            // Obtiene la información de onboarding del usuario
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()

                try {
                    val profile = profileRepo.findByUser(userId)
                    val objetivo = objetivoRepo.findByUser(userId)

                    if (profile == null || objetivo == null) {
                        return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "No se encontró información de onboarding")
                        )
                    }

                    val data = OnboardingData(
                        area = profile.area ?: "",
                        nivelExperiencia = profile.nivelExperiencia ?: "",
                        nombreCargo = objetivo.nombreCargo,
                        descripcionObjetivo = profile.notaObjetivos
                    )

                    call.respond(HttpStatusCode.OK, data)

                } catch (e: Exception) {
                    call.application.environment.log.error("Error obteniendo onboarding", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error obteniendo información: ${e.message}")
                    )
                }
            }
        }
    }
}

// Helper para extraer userId del JWT
private fun JWTPrincipal.userIdFromJwt(): UUID {
    val sub = this.subject ?: error("No subject in JWT")
    return UUID.fromString(sub)
}
