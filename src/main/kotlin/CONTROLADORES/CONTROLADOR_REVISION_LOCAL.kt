package CONTROLADORES

import ESQUEMAS.RespuestaDescargaRevision
import ESQUEMAS.RespuestaSubidaRevisiones
import ESQUEMAS.SolicitudSubirRevisiones
import SERVICIOS.ServicioRevisionLocal
import SERVICIOS.VERSION_REVISOR
import UTILIDADES.enteroDeConsulta
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aRespuestaRevisable
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Revisión de texto libre sin conexión (T3.13), sin IA. Requieren sesión.
 * GET  /api/v1/revision-local/preguntas?nivel=&categoria=&cantidad=   preguntas abiertas aprobadas con respuestaIdeal y rubrica
 * POST /api/v1/revision-local/respuestas  { respuestas:[{idLocal, preguntaId, texto, scoreLocal?, bandaLocal?, revisadoEnMs?}] }
 *      El servidor repite la revisión con la misma fórmula; idempotente por idLocal. Solo texto: nunca audio ni video.
 */
fun Route.controladorRevisionLocal(servicio: ServicioRevisionLocal) {
    authenticate("auth-jwt") {
        route("/api/v1/revision-local") {
            get("/preguntas") {
                val preguntas = servicio.descargar(
                    nivel = call.request.queryParameters["nivel"],
                    categoria = call.request.queryParameters["categoria"],
                    cantidad = call.enteroDeConsulta("cantidad")
                )
                call.respond(RespuestaDescargaRevision(VERSION_REVISOR, preguntas.map { it.aRespuestaRevisable() }))
            }

            post("/respuestas") {
                val lote = call.receive<SolicitudSubirRevisiones>().respuestas
                val resultados = servicio.subir(call.usuarioIdDesdeJwt(), lote)
                val nuevas = resultados.count { it.estado == "guardada" }
                call.responderConMensaje(RespuestaSubidaRevisiones(nuevas, resultados), "Respuestas recibidas: $nuevas de ${resultados.size}")
            }
        }
    }
}
