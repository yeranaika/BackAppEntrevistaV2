package CONTROLADORES

import ESQUEMAS.SolicitudRegistrarLlamada
import SERVICIOS.ServicioLlamada
import UTILIDADES.enteroDeConsulta
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aDetalle
import VISTAS.aRespuesta
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Historial de llamadas en vivo (T3.15). Requieren sesión; cada usuario solo ve las suyas.
 * POST /api/v1/llamadas        { idLocal, fechaInicioMs, duracionSeg, preguntasCnt?, sesionId?, resumen?:{acciones,linea} }
 *                              201 la guarda; 200 si ya estaba (idempotente por idLocal). Sin resumen y con sesionId, lo deriva del reporte T3.10
 * GET  /api/v1/llamadas        lista paginada, la más reciente primero (?pagina, ?tamano)
 * GET  /api/v1/llamadas/{id}   detalle con el reporte (acciones y línea de tiempo)
 */
fun Route.controladorLlamada(servicio: ServicioLlamada) {
    authenticate("auth-jwt") {
        route("/api/v1/llamadas") {
            post {
                val resultado = servicio.registrar(call.usuarioIdDesdeJwt(), call.receive<SolicitudRegistrarLlamada>())
                if (resultado.creada) {
                    call.responderConMensaje(resultado.llamada.aDetalle(), "Llamada guardada", HttpStatusCode.Created)
                } else {
                    call.responderConMensaje(resultado.llamada.aDetalle(), "La llamada ya estaba guardada")
                }
            }

            get {
                val pagina = servicio.historial(call.usuarioIdDesdeJwt(), call.enteroDeConsulta("pagina"), call.enteroDeConsulta("tamano"))
                call.respond(pagina.aRespuesta())
            }

            get("/{id}") {
                call.respond(servicio.detalle(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aDetalle())
            }
        }
    }
}
