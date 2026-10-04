package CONTROLADORES

import ESQUEMAS.SolicitudRecordatorio
import SERVICIOS.ServicioRecordatorio
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aRespuesta
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * GET /recordatorios/preferencias   404 si el usuario no configuró nada
 * PUT /recordatorios/preferencias
 */
fun Route.controladorRecordatorio(servicio: ServicioRecordatorio) {
    authenticate("auth-jwt") {
        route("/recordatorios/preferencias") {
            get {
                call.respond(servicio.obtener(call.usuarioIdDesdeJwt()).aRespuesta())
            }

            put {
                val solicitud = call.receive<SolicitudRecordatorio>()
                val guardado = servicio.guardar(
                    call.usuarioIdDesdeJwt(), solicitud.diasSemana, solicitud.hora, solicitud.tipoPractica, solicitud.habilitado
                )
                call.responderConMensaje(guardado.aRespuesta(), "Preferencias de recordatorio guardadas")
            }
        }
    }
}
