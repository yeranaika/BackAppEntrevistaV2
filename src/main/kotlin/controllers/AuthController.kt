package controllers

import ESQUEMAS.RespuestaError
import ESQUEMAS.RespuestaOk
import ESQUEMAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import models.RegisterReq
import models.UpdateProfileReq
import UTILIDADES.usuarioIdDesdeJwt
import services.AuthService

/**
 * Registro y actualización del perfil básico. Se migra a CONTROLADORES en la Fase 2 (usuarios);
 * login, Google, refresh y logout ya viven en CONTROLADORES.controladorLogin.
 *
 *  - POST /auth/register   registro local
 *  - PUT  /me              actualizar perfil básico del usuario logueado
 */
fun Route.authController(authService: AuthService) {
    route("/auth") {
        post("/register") {
            try {
                val req = call.receiveJsonOrNull<RegisterReq>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
                val tokens = authService.register(req)
                call.respond(HttpStatusCode.Created, tokens.aRespuesta())
            } catch (e: AuthService.EmailInUseException) {
                call.respond(HttpStatusCode.Conflict, RespuestaError(e.publicCode))
            } catch (e: AuthService.AuthException) {
                call.respond(HttpStatusCode.UnprocessableEntity, RespuestaError(e.publicCode))
            } catch (t: Throwable) {
                call.application.environment.log.error("Register failed", t)
                call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
            }
        }
    }

    // Actualización del perfil básico del usuario logueado
    route("/me") {
        authenticate("auth-jwt") {
            put {
                try {
                    val userId = call.usuarioIdDesdeJwt()
                    val req = call.receiveJsonOrNull<UpdateProfileReq>()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
                    authService.updateProfile(userId, req)
                    call.respond(RespuestaOk())
                } catch (e: AuthService.UserNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, RespuestaError(e.publicCode))
                } catch (e: AuthService.AuthException) {
                    call.respond(HttpStatusCode.BadRequest, RespuestaError(e.publicCode))
                } catch (t: Throwable) {
                    call.application.environment.log.error("Update profile failed", t)
                    call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
                }
            }
        }
    }
}

/**
 * Lee el body JSON o devuelve null si viene malformado o con tipos incorrectos.
 * En Ktor 3 un JSON inválido llega como BadRequestException (no ContentTransformationException),
 * así que ambos se traducen a 400 invalid_json en vez de caer al 500 genérico.
 */
private suspend inline fun <reified T : Any> ApplicationCall.receiveJsonOrNull(): T? =
    try {
        receive<T>()
    } catch (_: BadRequestException) {
        null
    } catch (_: ContentTransformationException) {
        null
    }
