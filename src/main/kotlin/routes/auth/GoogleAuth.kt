package routes.auth

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.auth.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import data.repository.usuarios.UsuariosOAuthRepository
import security.auth.GoogleTokenVerifier
import security.tokens.TokenGoogleService
import security.AuthCtxKey
import java.util.UUID

// Para el flujo web (callback) seguiremos usando este tipo
@Serializable
data class TokenPair(
    val access_token: String,
    val refresh_token: String
)

/**
 * Rutas de autenticación con Google:
 *
 * - WEB:
 *   GET  /auth/google/start     (redirige a Google)
 *   GET  /auth/google/callback  (recibe 'code', canjea, devuelve TokenPair)
 *
 * - MÓVIL (Android):
 *   POST /auth/google           (recibe { idToken }, crea/enlaza usuario y devuelve LoginOk)
 */
fun Route.googleAuthRoutes(
    repo: UsuariosOAuthRepository,
    verifier: GoogleTokenVerifier
) {
    val log = application.log

    // ==========================
    // 1) Flujo WEB (navegador)
    // ==========================
    authenticate("google-oauth") {
        route("/auth/google") {
            // Normalmente ni hace falta implementar esto:
            get("/start") {
                // El provider OAuth de Ktor ya hace la redirección,
                // así que muchas veces este endpoint ni se llama directamente.
                call.respond(HttpStatusCode.BadRequest, "Usa el flujo OAuth configurado")
            }

            get("/callback") {
                val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    ?: run {
                        log.error("OAuth principal NULL → fallo al canjear el 'code' con Google (revisa client_id/secret y redirect_uri)")
                        return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            "No se pudo canjear el code con Google"
                        )
                    }

                // Útil para debug: ver si llegó 'id_token'
                log.debug("Google extraParameters names=" + principal.extraParameters.names().joinToString())

                val idToken = principal.extraParameters["id_token"]
                    ?: run {
                        log.error("Falta id_token → asegúrate de scope: openid email profile y que el client_secret es correcto")
                        return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            "Falta id_token"
                        )
                    }

                val payload = verifier.verify(idToken)
                    ?: run {
                        log.error("ID token inválido → 'aud' no coincide con tu client_id o 'iss' inválido")
                        return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            "ID token inválido"
                        )
                    }

                val sub = payload.subject
                val email = payload["email"] as? String
                val emailVerified = (payload["email_verified"] as? Boolean) ?: false
                if (!emailVerified || email.isNullOrBlank()) {
                    log.warn("Email no verificado o vacío. sub=$sub email=$email verified=$emailVerified")
                    return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        "Email no verificado"
                    )
                }

                try {
                    val userId: UUID = repo.linkOrCreateFromGoogle(sub, email)

                    // toma issuer/audience/algorithm desde el AuthCtx cargado en configureSecurity()
                    val ctx = call.application.attributes[AuthCtxKey]
                    val tokens = TokenGoogleService.issue(
                        userId = userId,
                        issuer = ctx.issuer,
                        audience = ctx.audience,
                        algorithm = ctx.algorithm
                    )

                    // Para el flujo web devolvemos TokenPair (snake_case) como ya tenías
                    call.respond(tokens)
                } catch (e: Exception) {
                    log.error("Error procesando callback Google", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error al completar el login"
                    )
                }
            }
        }
    }

    // ==========================================
    // 2) Flujo MÓVIL (Android) con idToken JSON
    // ==========================================
    route("/auth/google") {
        post {
            // 2.1) Leer el body { "idToken": "..." }
            val body = try {
                call.receive<GoogleLoginReq>()
            } catch (e: Exception) {
                log.error("Error leyendo JSON de GoogleLoginReq", e)
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Body inválido"
                )
            }

            // 2.2) Verificar el idToken con el mismo client_id que usas en Android
            val payload = verifier.verify(body.idToken)
                ?: run {
                    log.warn("ID token inválido en login móvil")
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        "ID token inválido"
                    )
                }

            val sub = payload.subject
            val email = payload["email"] as? String
            val emailVerified = (payload["email_verified"] as? Boolean) ?: false
            if (!emailVerified || email.isNullOrBlank()) {
                log.warn("Email no verificado o vacío (móvil). sub=$sub email=$email verified=$emailVerified")
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Email no verificado"
                )
            }

            try {
                // 2.3) Enlazar o crear usuario usando tu repositorio
                val userId: UUID = repo.linkOrCreateFromGoogle(sub, email)

                // 2.4) Emitir los mismos JWT que en login normal
                val ctx = call.application.attributes[AuthCtxKey]
                val tokens = TokenGoogleService.issue(
                    userId = userId,
                    issuer = ctx.issuer,
                    audience = ctx.audience,
                    algorithm = ctx.algorithm
                )

                // 2.5) Devolver LoginOk con camelCase para que case con /auth/login
                val res = LoginOk(
                    accessToken = tokens.access_token,
                    refreshToken = tokens.refresh_token
                )

                call.respond(HttpStatusCode.OK, res)
            } catch (e: Exception) {
                log.error("Error procesando login Google móvil", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Error al completar el login con Google"
                )
            }
        }
    }
}
