package CONTROLADORES

import ERRORES.ErrorNoAutorizado
import ESQUEMAS.RespuestaError
import ESQUEMAS.RespuestaOk
import ESQUEMAS.SolicitudLogin
import ESQUEMAS.SolicitudLoginGoogle
import ESQUEMAS.SolicitudRefresh
import ESQUEMAS.aRespuesta
import ESQUEMAS.aRespuestaWeb
import SERVICIOS.ServicioLogin
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * POST /auth/login          correo y contraseña
 * POST /auth/google         idToken de Google (Android)
 * GET  /auth/google/start   inicio del flujo web (lo redirige el proveedor OAuth de Ktor)
 * GET  /auth/google/callback
 * POST /auth/refresh        rota el refresh token
 * POST /auth/logout         revoca el refresh token
 *
 * Los errores los traduce CONFIGURACION_ERRORES.
 */
fun Route.controladorLogin(servicioLogin: ServicioLogin) {
    route("/auth") {
        post("/login") {
            val solicitud = call.receive<SolicitudLogin>()
            call.respond(servicioLogin.iniciarSesion(solicitud.correo, solicitud.contrasena).aRespuesta())
        }

        post("/google") {
            val solicitud = call.receive<SolicitudLoginGoogle>()
            call.respond(servicioLogin.iniciarSesionConGoogle(solicitud.idTokenGoogle).aRespuesta())
        }

        post("/refresh") {
            val solicitud = call.receive<SolicitudRefresh>()
            call.respond(servicioLogin.renovarSesion(solicitud.tokenRefresco).aRespuesta())
        }

        post("/logout") {
            val solicitud = call.receive<SolicitudRefresh>()
            servicioLogin.cerrarSesion(solicitud.tokenRefresco)
            call.respond(RespuestaOk())
        }

        authenticate("google-oauth") {
            get("/google/start") {
                // Con authenticate("google-oauth") Ktor redirige a Google antes de llegar aquí.
                call.respond(HttpStatusCode.BadRequest, RespuestaError("use_configured_oauth_flow"))
            }

            get("/google/callback") {
                val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    ?: throw ErrorNoAutorizado("google_exchange_failed", "Google no entregó tokens para el código recibido")
                val idToken = principal.extraParameters["id_token"]
                    ?: throw ErrorNoAutorizado("missing_id_token", "La respuesta de Google no trae id_token")
                call.respond(servicioLogin.iniciarSesionConGoogle(idToken).aRespuestaWeb())
            }
        }
    }
}
