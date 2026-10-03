package CONTROLADORES

import ERRORES.ErrorAplicacion
import ERRORES.ErrorConflicto
import ESQUEMAS.RespuestaErrorLegado
import ESQUEMAS.RespuestaMensaje
import ESQUEMAS.RespuestaOk
import ESQUEMAS.SolicitudActualizarCuenta
import ESQUEMAS.SolicitudActualizarPerfil
import ESQUEMAS.SolicitudEliminarCuenta
import ESQUEMAS.SolicitudRegistro
import ESQUEMAS.aRespuesta
import SERVICIOS.ServicioUsuario
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * POST   /auth/register   registro con correo y contraseña
 * GET    /me              cuenta + perfil + cargo meta
 * PUT    /me              datos básicos (nombre, idioma, teléfono, fecha de nacimiento, género)
 * GET    /me/perfil
 * PUT    /me/perfil
 * DELETE /cuenta          borrado definitivo, body { "confirmar": "eliminar" }
 */
fun Route.controladorUsuario(servicio: ServicioUsuario) {
    post("/auth/register") {
        val solicitud = call.receive<SolicitudRegistro>()
        val tokens = try {
            servicio.registrar(solicitud)
        } catch (error: ErrorAplicacion) {
            // Contrato legado de Android: cuerpo {"error"} y 409/422 para elegir el mensaje.
            val estado = if (error is ErrorConflicto) HttpStatusCode.Conflict else HttpStatusCode.UnprocessableEntity
            return@post call.respond(estado, RespuestaErrorLegado(error.codigo))
        }
        call.respond(HttpStatusCode.Created, tokens.aRespuesta())
    }

    authenticate("auth-jwt") {
        route("/me") {
            get {
                call.respond(servicio.obtenerCuenta(call.usuarioIdDesdeJwt()).aRespuesta())
            }

            put {
                servicio.actualizarCuenta(call.usuarioIdDesdeJwt(), call.receive<SolicitudActualizarCuenta>())
                call.respond(RespuestaOk())
            }

            get("/perfil") {
                call.respond(servicio.obtenerPerfil(call.usuarioIdDesdeJwt()).aRespuesta())
            }

            put("/perfil") {
                servicio.actualizarPerfil(call.usuarioIdDesdeJwt(), call.receive<SolicitudActualizarPerfil>())
                call.respond(RespuestaOk())
            }
        }

        delete("/cuenta") {
            val solicitud = call.receive<SolicitudEliminarCuenta>()
            servicio.eliminarCuenta(call.usuarioIdDesdeJwt(), solicitud.confirmar)
            call.respond(
                RespuestaMensaje("Cuenta eliminada exitosamente. Todos tus datos han sido borrados de forma permanente.")
            )
        }
    }
}
