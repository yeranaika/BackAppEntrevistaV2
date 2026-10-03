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
import UTILIDADES.responderConMensaje
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
            call.responderConMensaje(servicioLogin.iniciarSesion(solicitud.correo, solicitud.contrasena).aRespuesta(), "Sesión iniciada")
        }

        post("/google") {
            val solicitud = call.receive<SolicitudLoginGoogle>()
            call.responderConMensaje(servicioLogin.iniciarSesionConGoogle(solicitud.idTokenGoogle).aRespuesta(), "Sesión iniciada con Google")
        }

        post("/refresh") {
            val solicitud = call.receive<SolicitudRefresh>()
            call.responderConMensaje(servicioLogin.renovarSesion(solicitud.tokenRefresco).aRespuesta(), "Sesión renovada")
        }

        post("/logout") {
            val solicitud = call.receive<SolicitudRefresh>()
            servicioLogin.cerrarSesion(solicitud.tokenRefresco)
            call.respond(RespuestaOk("Sesión cerrada"))
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
                call.responderConMensaje(servicioLogin.iniciarSesionConGoogle(idToken).aRespuestaWeb(), "Sesión iniciada con Google")
            }
        }
    }
}
