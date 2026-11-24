package CONTROLADORES

import ERRORES.ErrorProhibido
import ESQUEMAS.SolicitudPrediccionVision
import SERVICIOS.PredictorVision
import SERVICIOS.ServicioSimulacion
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuesta
import VISTAS.aTexto
import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Visión computacional — inferencia del modelo guía DivLara E14 (requiere sesión):
 * POST /api/v1/vision/predecir          (reenvía al motor de inferencia, T3.11) { features: [343 floats] } | { frames: [...], fps? }, más { sesionId?, timestampMs? }
 *                                       → predicciones observables en escala 0-8 (con sesionId se guardan para el reporte)
 * GET  /api/v1/vision/reporte/{sesion}  reporte de la simulación en texto tabulado (text/plain); solo el dueño de la sesión
 */
fun Route.controladorVision(servicio: PredictorVision, simulacion: ServicioSimulacion) {
    authenticate("auth-jwt") {
        route("/api/v1/vision") {
            post("/predecir") {
                val usuarioId = call.usuarioIdDesdeJwt()
                // Sin el permiso de cámara no se procesan los puntos del cuerpo ni de los ojos (D38).
                if (!simulacion.permiteCamara(usuarioId)) {
                    throw ErrorProhibido("permiso_camara_requerido", "Activa el permiso de análisis con cámara para medir conductas")
                }
                val solicitud = call.receive<SolicitudPrediccionVision>()
                val resultado = servicio.predecir(solicitud)
                simulacion.registrar(usuarioId, solicitud.sesionId?.let(::uuidDeTexto), solicitud.timestampMs, resultado)
                call.responderConMensaje(resultado.aRespuesta(), "Predicción generada")
            }

            get("/reporte/{sesion}") {
                val reporte = simulacion.reporte(call.usuarioIdDesdeJwt(), call.uuidDeParametro("sesion"))
                call.respondText(reporte.aTexto(), ContentType.Text.Plain)
            }
        }
    }
}

private fun uuidDeTexto(texto: String): UUID =
    runCatching { UUID.fromString(texto) }
        .getOrElse { throw ERRORES.ErrorValidacion("id_invalido", "sesionId no es un UUID válido") }
