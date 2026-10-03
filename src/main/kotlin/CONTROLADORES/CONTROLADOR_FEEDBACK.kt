package CONTROLADORES

import SERVICIOS.ServicioReporteEntrevista
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aReporte
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Feedback (requiere sesión; solo de las entrevistas propias):
 * GET  /api/v1/entrevistas/{id}/reporte              reporte de la entrevista (generando | listo | error)
 * POST /api/v1/entrevistas/{id}/reporte/reintentar   vuelve a generar un reporte que terminó con error (202)
 * GET  /api/v1/reportes                              historial de reportes
 * GET  /api/v1/me/progreso                           nivel y evolución por skill
 */
fun Route.controladorFeedback(servicio: ServicioReporteEntrevista) {
    authenticate("auth-jwt") {
        route("/api/v1/entrevistas/{id}/reporte") {
            get {
                val (sesion, reporte) = servicio.obtener(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"))
                call.respond(sesion.aReporte(reporte))
            }

            post("/reintentar") {
                val usuario = call.usuarioIdDesdeJwt()
                val sesionId = call.uuidDeParametro("id")
                val reporte = servicio.reintentar(usuario, sesionId)
                val (sesion, _) = servicio.obtener(usuario, sesionId)
                call.responderConMensaje(sesion.aReporte(reporte), "Reporte solicitado de nuevo; estará listo en unos momentos", HttpStatusCode.Accepted)
            }
        }

        get("/api/v1/reportes") {
            call.respond(servicio.historial(call.usuarioIdDesdeJwt()).map { it.aRespuesta() })
        }

        get("/api/v1/me/progreso") {
            call.respond(servicio.progreso(call.usuarioIdDesdeJwt()).map { it.aRespuesta() })
        }
    }
}
