// middlewares/AuthMiddleware.kt
package middlewares

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.sessions.*
import io.ktor.http.*

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import plugins.settings
import io.ktor.client.plugins.logging.*

/**
 * Middleware de autenticación: aquí vive todo lo transversal a "estar logueado" —
 * el esquema JWT y el esquema OAuth de Google que usan authenticate("auth-jwt")
 * y authenticate("google-oauth") en los controllers.
 */

// --- Contexto JWT compartido (issuer/audience/algorithm) ---
data class AuthCtx(val issuer: String, val audience: String, val algorithm: Algorithm)
val AuthCtxKey = AttributeKey<AuthCtx>("auth-ctx")

// Sesión mínima para manejar el estado del flujo OAuth
@kotlinx.serialization.Serializable
data class OAuthSession(val state: String = "")

fun Application.configureSecurity() {
    // ---------- Config JWT ----------
    val s = settings()
    val algorithm = Algorithm.HMAC512(s.jwtSecret)
    attributes.put(AuthCtxKey, AuthCtx(s.jwtIssuer, s.jwtAudience, algorithm))

    // ---------- Cliente HTTP para el intercambio de tokens con Google ----------
    val oauthHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }
    }

    // ---------- Sesiones para el state del flujo OAuth ----------
    install(Sessions) {
        cookie<OAuthSession>("oauth_session")
    }

    // ---------- Autenticación: JWT + Google OAuth ----------
    install(Authentication) {

        // 1) Esquema JWT
        jwt("auth-jwt") {
            realm = "app-entrevista"
            verifier(
                JWT.require(algorithm)
                    .withIssuer(s.jwtIssuer)
                    .withAudience(s.jwtAudience)
                    .build()
            )
            validate { cred -> if (cred.subject != null) JWTPrincipal(cred.payload) else null }
        }

        // 2) Esquema OAuth de Google (Authorization Code + OIDC) — usado por el flujo web
        oauth("google-oauth") {
            // Debe coincidir EXACTAMENTE con el Redirect URI configurado en Google Cloud
            val redirectUri = s.googleRedirectUri

            urlProvider = { redirectUri }

            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl   = "https://accounts.google.com/o/oauth2/v2/auth",
                    accessTokenUrl = "https://oauth2.googleapis.com/token",
                    requestMethod  = HttpMethod.Post,
                    clientId       = s.googleClientId,
                    clientSecret   = s.googleClientSecret,
                    // Pedimos OIDC para recibir id_token en el callback
                    defaultScopes  = listOf("openid", "email", "profile")
                )
            }
            // HttpClient usado para canjear el "code" por tokens
            client = oauthHttpClient
        }
    }
}
