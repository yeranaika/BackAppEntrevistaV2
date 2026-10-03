package CONTROLADORES

import ERRORES.ErrorValidacion
import ESQUEMAS.MetricaVideoEntrada
import ESQUEMAS.RespuestaMetricasRegistradas
import ESQUEMAS.SolicitudIniciarEntrevista
import ESQUEMAS.SolicitudMetricasVideo
import ESQUEMAS.SolicitudResponderPregunta
import MODELOS.MetricaVideo
import SERVICIOS.PedidoEntrevista
import SERVICIOS.RespuestaUsuario
import SERVICIOS.ServicioEntrevista
import UTILIDADES.enteroDeConsulta
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Simulación de entrevista (todas requieren sesión; solo se ven las entrevistas propias):
 * POST /api/v1/entrevistas                      iniciar { cargoId? | cargo?, nivel?, cantidadPreguntas? }
 * GET  /api/v1/entrevistas                      historial (?pagina, ?tamano)
 * GET  /api/v1/entrevistas/actual               la entrevista en curso (204 si no hay)
 * GET  /api/v1/entrevistas/{id}                 detalle con preguntas y respuestas
 * GET  /api/v1/entrevistas/{id}/siguiente       siguiente pregunta sin responder (204 si no quedan)
 * POST /api/v1/entrevistas/{id}/respuestas      { preguntaSesionId, texto? | opcionId? | videoClipUrl? }
 * POST /api/v1/entrevistas/{id}/metricas        lote de métricas de video { metricas: [...] }
 * POST /api/v1/entrevistas/{id}/finalizar
 * POST /api/v1/entrevistas/{id}/cancelar
 */
fun Route.controladorEntrevista(servicio: ServicioEntrevista) {
    authenticate("auth-jwt") {
        route("/api/v1/entrevistas") {
            post {
                val solicitud = call.receive<SolicitudIniciarEntrevista>()
                val pedido = PedidoEntrevista(solicitud.cargoId, solicitud.cargo, solicitud.nivel, solicitud.cantidadPreguntas)
                call.responderConMensaje(servicio.iniciar(call.usuarioIdDesdeJwt(), pedido).aRespuesta(), "Entrevista iniciada", HttpStatusCode.Created)
            }

            get {
                val pagina = servicio.historial(call.usuarioIdDesdeJwt(), call.enteroDeConsulta("pagina"), call.enteroDeConsulta("tamano"))
                call.respond(pagina.aRespuesta())
            }

            get("/actual") {
                val sesion = servicio.enProgreso(call.usuarioIdDesdeJwt()) ?: return@get call.respond(HttpStatusCode.NoContent)
                call.respond(sesion.aRespuesta())
            }

            route("/{id}") {
                get {
                    call.respond(servicio.obtener(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aRespuesta())
                }

                get("/siguiente") {
                    val pregunta = servicio.siguientePregunta(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"))
                        ?: return@get call.respond(HttpStatusCode.NoContent)
                    call.respond(pregunta.aRespuesta())
                }

                post("/respuestas") {
                    val solicitud = call.receive<SolicitudResponderPregunta>()
                    val respuesta = RespuestaUsuario(
                        preguntaSesionId = uuidDelCuerpo(solicitud.preguntaSesionId),
                        texto = solicitud.texto,
                        opcionId = solicitud.opcionId,
                        videoClipUrl = solicitud.videoClipUrl
                    )
                    call.responderConMensaje(servicio.responder(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"), respuesta).aRespuesta(), "Respuesta guardada")
                }

                post("/metricas") {
                    val lote = call.receive<SolicitudMetricasVideo>().metricas.map { it.aModelo() }
                    val insertadas = servicio.registrarMetricas(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"), lote)
                    call.responderConMensaje(RespuestaMetricasRegistradas(insertadas), "Métricas de video registradas: $insertadas", HttpStatusCode.Created)
                }

                post("/finalizar") {
                    call.responderConMensaje(
                        servicio.finalizar(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aRespuesta(),
                        "Entrevista finalizada. El reporte de feedback se está generando"
                    )
                }

                post("/cancelar") {
                    call.responderConMensaje(servicio.cancelar(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aRespuesta(), "Entrevista cancelada")
                }
            }
        }
    }
}

/** Un id que llega en el cuerpo (no en la ruta). */
internal fun uuidDelCuerpo(valor: String): UUID =
    runCatching { UUID.fromString(valor) }.getOrNull()
        ?: throw ErrorValidacion("pregunta_sesion_id_invalido", "El id de la pregunta no es válido")

private fun MetricaVideoEntrada.aModelo() = MetricaVideo(
    timestampMs = timestampMs,
    contactoVisual = contactoVisual?.let(::aDecimal),
    postura = postura?.let(::aDecimal),
    confianza = confianza?.let(::aDecimal),
    gestos = gestos,
    expresionDominante = expresion?.trim()?.lowercase()
)

/** Las columnas son NUMERIC(5,2). */
private fun aDecimal(valor: Double): BigDecimal {
    if (!valor.isFinite()) throw ErrorValidacion("metrica_fuera_de_rango", "Los puntajes de video van de 0 a 100")
    return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP)
}
