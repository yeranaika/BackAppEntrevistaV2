package routes.admin

import data.models.ai.AiErrorRes
import data.models.ai.GenerateQuestionsReq
import data.models.ai.GenerateQuestionsRes
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import UTILIDADES.esAdmin
import services.ai.AiPersistenceException
import services.ai.AiProviderHttpException
import services.ai.AiProviderInvalidOutputException
import services.ai.AiProviderInvalidResponseException
import services.ai.AiProviderNotConfiguredException
import services.ai.AiProviderRefusalException
import services.ai.AiProviderTruncatedException
import services.ai.AiQuestionGenerationUseCase
import services.ai.AiQuestionRequestValidator
import services.ai.AiRequestValidationException

fun Route.adminAiRoutes(aiService: AiQuestionGenerationUseCase) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/questions") {
            post("/generate-ai") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, AiErrorRes("unauthorized"))

                if (!principal.esAdmin()) {
                    return@post call.respond(HttpStatusCode.Forbidden, AiErrorRes("forbidden"))
                }

                val req = runCatching { call.receive<GenerateQuestionsReq>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, AiErrorRes("invalid_json"))
                }

                val expectedCount = try {
                    AiQuestionRequestValidator.validate(req).cantidad
                } catch (e: AiRequestValidationException) {
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, AiErrorRes(e.publicCode))
                }

                val result = try {
                    aiService.generate(req)
                } catch (e: AiRequestValidationException) {
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, AiErrorRes(e.publicCode))
                } catch (_: AiProviderNotConfiguredException) {
                    return@post call.respond(HttpStatusCode.ServiceUnavailable, AiErrorRes("provider_not_configured"))
                } catch (e: AiPersistenceException) {
                    call.application.environment.log.error("AI question persistence failed")
                    return@post call.respond(HttpStatusCode.InternalServerError, AiErrorRes(e.publicCode))
                } catch (e: AiProviderHttpException) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes(e.publicCode))
                } catch (e: AiProviderInvalidResponseException) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes(e.publicCode))
                } catch (e: AiProviderRefusalException) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes(e.publicCode))
                } catch (e: AiProviderTruncatedException) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes(e.publicCode))
                } catch (e: AiProviderInvalidOutputException) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes(e.publicCode))
                }

                if (result.errores != 0 || result.inserted.size != expectedCount) {
                    return@post call.respond(HttpStatusCode.BadGateway, AiErrorRes("batch_incomplete"))
                }

                call.respond(
                    HttpStatusCode.Created,
                    GenerateQuestionsRes(
                        preguntasGeneradas = result.inserted.size,
                        preguntasConError = 0,
                        preguntas = result.inserted
                    )
                )
            }
        }
    }
}
