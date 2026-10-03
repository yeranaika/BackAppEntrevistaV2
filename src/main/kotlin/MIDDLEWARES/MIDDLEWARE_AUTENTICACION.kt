package MIDDLEWARES

import CONFIGURACION.configuracion
import com.auth0.jwt.JWT
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val URL_AUTORIZACION_GOOGLE = "https://accounts.google.com/o/oauth2/v2/auth"
private const val URL_TOKEN_GOOGLE = "https://oauth2.googleapis.com/token"

/** Estado del flujo OAuth web entre la redirección a Google y el callback. */
@Serializable
data class SesionOAuth(val state: String = "")

/**
 * Esquemas de autenticación usados por los controladores:
 * - "auth-jwt": access token propio (Authorization: Bearer).
 * - "google-oauth": flujo web Authorization Code + OIDC de Google.
 */
fun Application.configurarSeguridad() {
    val configuracion = configuracion()
    val jwt = configuracion.jwt
    val google = configuracion.google

    // Canjea el "code" de Google por tokens en el flujo web.
    val clienteOAuth = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    monitor.subscribe(ApplicationStopped) { clienteOAuth.close() }

    install(Sessions) {
        cookie<SesionOAuth>("oauth_session")
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "app-entrevista"
            verifier(
                JWT.require(jwt.algoritmo)
                    .withIssuer(jwt.emisor)
                    .withAudience(jwt.audiencia)
                    .build()
            )
            validate { credencial -> if (credencial.subject != null) JWTPrincipal(credencial.payload) else null }
        }

        oauth("google-oauth") {
            // Debe coincidir EXACTAMENTE con el Redirect URI configurado en Google Cloud
            urlProvider = { google.redirectUri }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = URL_AUTORIZACION_GOOGLE,
                    accessTokenUrl = URL_TOKEN_GOOGLE,
                    requestMethod = HttpMethod.Post,
                    clientId = google.clientId,
                    clientSecret = google.clientSecret,
                    // "openid" hace que Google devuelva id_token en el callback
                    defaultScopes = listOf("openid", "email", "profile")
                )
            }
            client = clienteOAuth
        }
    }
}
