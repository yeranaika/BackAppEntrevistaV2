package CONTROLADORES

import ESQUEMAS.SolicitudTranscripcion
import SERVICIOS.ServicioTranscripcion
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeConsulta
import VISTAS.aRespuesta
import ERRORES.ErrorValidacion
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Transcripción de lo que la persona respondió en la llamada (VOZ-3/VOZ-4). Solo texto: el audio nunca sale del teléfono.
 * POST /api/v1/transcripciones                 { sesionId, preguntaId, inicioMs, finMs, texto, idioma?, confianza?, parcial? }
 *                                              201 la guarda; 200 si reemplazó una anterior o ya había una final y esta era parcial
 * GET  /api/v1/transcripciones?sesionId=       las de esa sesión, en el orden en que se respondieron
 */
fun Route.controladorTranscripcion(servicio: ServicioTranscripcion) {
    authenticate("auth-jwt") {
        route("/api/v1/transcripciones") {
            post {
                val guardada = servicio.guardar(call.usuarioIdDesdeJwt(), call.receive<SolicitudTranscripcion>())
                val estado = if (guardada.creada) HttpStatusCode.Created else HttpStatusCode.OK
                call.responderConMensaje(guardada.transcripcion.aRespuesta(), "Transcripción guardada", estado)
            }

            get {
                val sesionId = call.uuidDeConsulta("sesionId") ?: throw ErrorValidacion("id_requerido", "Falta el parámetro sesionId")
                call.respond(servicio.deSesion(call.usuarioIdDesdeJwt(), sesionId).aRespuesta())
            }
        }
    }
}
