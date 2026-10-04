package CONTROLADORES

import ERRORES.ErrorNoEncontrado
import ESQUEMAS.RespuestaEstado
import ESQUEMAS.RespuestaEstadoOnboarding
import ESQUEMAS.RespuestaGuardarOnboarding
import ESQUEMAS.RespuestaOk
import ESQUEMAS.SolicitudObjetivo
import ESQUEMAS.SolicitudObjetivoPerfil
import ESQUEMAS.SolicitudOnboarding
import SERVICIOS.ServicioOnboarding
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aRespuesta
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * PUT    /perfil/objetivo     área, cargo meta y nivel (onboarding de la app Android)
 * POST   /onboarding          lo mismo, con descripción del objetivo
 * GET    /onboarding
 * GET    /onboarding/status
 * GET    /me/objetivo         cargo meta activo
 * PUT    /me/objetivo
 * DELETE /me/objetivo
 */
fun Route.controladorOnboarding(servicio: ServicioOnboarding) {
    authenticate("auth-jwt") {
        put("/perfil/objetivo") {
            val solicitud = call.receive<SolicitudObjetivoPerfil>()
            servicio.guardarOnboarding(call.usuarioIdDesdeJwt(), solicitud.area, solicitud.nivel, solicitud.metaCargo)
            call.responderConMensaje(RespuestaEstado("ok"), "Objetivo guardado")
        }

        route("/onboarding") {
            post {
                val solicitud = call.receive<SolicitudOnboarding>()
                val resumen = servicio.guardarOnboarding(
                    call.usuarioIdDesdeJwt(),
                    solicitud.area,
                    solicitud.nivelExperiencia,
                    solicitud.nombreCargo,
                    solicitud.descripcionObjetivo
                )
                val mensaje = "Información de onboarding guardada exitosamente"
                call.responderConMensaje(RespuestaGuardarOnboarding(esExitoso = true, mensaje = mensaje, datos = resumen.aRespuesta()), mensaje)
            }

            get {
                val resumen = servicio.obtenerOnboarding(call.usuarioIdDesdeJwt())
                    ?: throw ErrorNoEncontrado("onboarding_not_found", "No se encontró información de onboarding")
                call.respond(resumen.aRespuesta())
            }

            get("/status") {
                val resumen = servicio.obtenerOnboarding(call.usuarioIdDesdeJwt())
                call.respond(RespuestaEstadoOnboarding(estaCompleto = resumen != null, datos = resumen?.aRespuesta()))
            }
        }

        route("/me/objetivo") {
            get {
                call.respond(servicio.obtenerObjetivo(call.usuarioIdDesdeJwt()).aRespuesta())
            }

            put {
                val solicitud = call.receive<SolicitudObjetivo>()
                val objetivo = servicio.guardarObjetivo(call.usuarioIdDesdeJwt(), solicitud.nombreCargo, solicitud.sector)
                call.responderConMensaje(objetivo.aRespuesta(), "Cargo objetivo guardado")
            }

            delete {
                servicio.eliminarObjetivo(call.usuarioIdDesdeJwt())
                call.respond(RespuestaOk("Cargo objetivo eliminado"))
            }
        }
    }
}
