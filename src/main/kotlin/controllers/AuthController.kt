package controllers

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ESQUEMAS.RespuestaError
import models.GoogleLoginReq
import models.LoginOk
import models.LoginReq
import ESQUEMAS.RespuestaOk
import models.RegisterReq
import models.TokenPair
import models.UpdateProfileReq
import security.userIdFromJwt
import services.AuthService

/**
 * Controller de autenticación: capa delgada (routing + parseo + mapeo de errores).
 * Toda la lógica de negocio vive en services.AuthService.
 *
 * Endpoints:
 *  - POST /auth/register        registro local
 *  - POST /auth/login           login local
 *  - POST /auth/google          login/autoregistro con Google (móvil, idToken)
 *  - GET  /auth/google/start    login con Google (web, redirect)
 *  - GET  /auth/google/callback login con Google (web, callback OAuth)
 *  - PUT  /me                   actualizar perfil básico del usuario logueado
 */
fun Route.authController(
    authService: AuthService,
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    route("/auth") {
        post("/register") {
            try {
                val req = call.receiveJsonOrNull<RegisterReq>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
                val tokens = authService.register(req, issuer, audience, algorithm)
                call.respond(HttpStatusCode.Created, LoginOk(tokens.accessToken, tokens.refreshToken))
            } catch (e: AuthService.EmailInUseException) {
                call.respond(HttpStatusCode.Conflict, RespuestaError(e.publicCode))
            } catch (e: AuthService.AuthException) {
                call.respond(HttpStatusCode.UnprocessableEntity, RespuestaError(e.publicCode))
            } catch (t: Throwable) {
                call.application.environment.log.error("Register failed", t)
                call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
            }
        }

        post("/login") {
            try {
                val req = call.receiveJsonOrNull<LoginReq>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
                val tokens = authService.login(req, issuer, audience, algorithm)
                call.respond(LoginOk(tokens.accessToken, tokens.refreshToken))
            } catch (e: AuthService.InactiveUserException) {
                call.respond(HttpStatusCode.Forbidden, RespuestaError(e.publicCode))
            } catch (e: AuthService.BadCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, RespuestaError(e.publicCode))
            } catch (t: Throwable) {
                call.application.environment.log.error("Login failed", t)
                call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
            }
        }

        // Flujo MÓVIL (Android): recibe { idToken }, autoregistra/enlaza y devuelve LoginOk
        post("/google") {
            try {
                val req = call.receiveJsonOrNull<GoogleLoginReq>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
                val tokens = authService.loginWithGoogle(req.idToken, issuer, audience, algorithm)
                call.respond(HttpStatusCode.OK, LoginOk(tokens.accessToken, tokens.refreshToken))
            } catch (e: AuthService.GoogleTokenInvalidException) {
                call.respond(HttpStatusCode.Unauthorized, RespuestaError(e.publicCode))
            } catch (e: AuthService.GoogleEmailNotVerifiedException) {
                call.respond(HttpStatusCode.Unauthorized, RespuestaError(e.publicCode))
            } catch (t: Throwable) {
                call.application.environment.log.error("Google login failed", t)
                call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
            }
        }

        // Flujo WEB (navegador)
        authenticate("google-oauth") {
            route("/google") {
                get("/start") {
                    // El provider OAuth de Ktor ya hace la redirección; normalmente no se llama directo.
                    call.respond(HttpStatusCode.BadRequest, RespuestaError("use_configured_oauth_flow"))
                }

                get("/callback") {
                    val log = call.application.environment.log
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                        ?: run {
                            log.error("OAuth principal NULL → fallo al canjear el 'code' con Google")
                            return@get call.respond(HttpStatusCode.Unauthorized, RespuestaError("google_exchange_failed"))
                        }

                    val idToken = principal.extraParameters["id_token"]
                        ?: run {
                            log.error("Falta id_token en la respuesta de Google")
                            return@get call.respond(HttpStatusCode.Unauthorized, RespuestaError("missing_id_token"))
                        }

                    try {
                        val tokens = authService.loginWithGoogle(idToken, issuer, audience, algorithm)
                        // Flujo web: mantiene el formato TokenPair (snake_case) ya usado antes
                        call.respond(TokenPair(access_token = tokens.accessToken, refresh_token = tokens.refreshToken))
                    } catch (e: AuthService.GoogleTokenInvalidException) {
                        call.respond(HttpStatusCode.Unauthorized, RespuestaError(e.publicCode))
                    } catch (e: AuthService.GoogleEmailNotVerifiedException) {
                        call.respond(HttpStatusCode.Unauthorized, RespuestaError(e.publicCode))
                    } catch (t: Throwable) {
                        log.error("Error procesando callback Google", t)
                        call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
                    }
                }
            }
        }
    }

    // Actualización del perfil básico del usuario logueado
    route("/me") {
        authenticate("auth-jwt") {
            put {
                try {
                    val userId = call.userIdFromJwt()
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
