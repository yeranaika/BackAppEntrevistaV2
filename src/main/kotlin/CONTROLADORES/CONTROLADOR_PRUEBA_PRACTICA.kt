package CONTROLADORES

import ESQUEMAS.RespuestaPreguntaPractica
import ESQUEMAS.SolicitudCrearPruebaPractica
import ESQUEMAS.SolicitudEnviarRespuestasPractica
import SERVICIOS.PedidoPruebaApp
import SERVICIOS.PruebaApp
import SERVICIOS.RespuestaPrueba
import SERVICIOS.ServicioPruebasApp
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aIntentoApp
import VISTAS.aPruebaPractica
import VISTAS.aResultadoPractica
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Contrato que ya usa la app Android (rinde cada prueba de una vez):
 * POST /api/prueba-practica/front                  crea la prueba según tipoPrueba:
 *                                                  ENT | MIX | SIM entrevista · PR práctica técnica · BL práctica blanda · NV nivelación
 * POST /api/prueba-practica/{pruebaId}/respuestas  guarda las respuestas y la cierra
 * GET  /api/prueba-practica/intentos               historial de todas las pruebas
 * Por debajo usa los mismos servicios que /api/v1/entrevistas, /api/v1/practicas y /api/v1/nivelacion.
 */
fun Route.controladorPruebaPractica(servicio: ServicioPruebasApp) {
    authenticate("auth-jwt") {
        route("/api/prueba-practica") {
            post("/front") {
                val solicitud = call.receive<SolicitudCrearPruebaPractica>()
                val cantidad = listOfNotNull(solicitud.cantidadPR, solicitud.cantidadNV, solicitud.cantidadBL).sum().takeIf { it > 0 }
                val pedido = PedidoPruebaApp(nombreCargo = solicitud.metaCargo, nivel = solicitud.nivel, cantidadPreguntas = cantidad)
                val sector = solicitud.sector?.trim().orEmpty()
                val respuesta = when (val prueba = servicio.crear(call.usuarioIdDesdeJwt(), solicitud.tipoPrueba, pedido)) {
                    is PruebaApp.Entrevista -> prueba.sesion.aPruebaPractica(sector)
                    is PruebaApp.Practica -> prueba.sesion.aPruebaPractica(sector)
                    is PruebaApp.Nivelacion -> prueba.intento.aPruebaPractica(sector)
                }
                call.responderConMensaje(respuesta, "Prueba creada", HttpStatusCode.Created)
            }

            post("/{pruebaId}/respuestas") {
                val respuestas = call.receive<SolicitudEnviarRespuestasPractica>().respuestas.mapNotNull { it.aRespuestaPrueba() }
                val respuesta = when (val prueba = servicio.responder(call.usuarioIdDesdeJwt(), call.uuidDeParametro("pruebaId"), respuestas)) {
                    is PruebaApp.Entrevista -> prueba.sesion.aResultadoPractica()
                    is PruebaApp.Practica -> prueba.sesion.aResultadoPractica()
                    is PruebaApp.Nivelacion -> prueba.intento.aResultadoPractica(prueba.resultado!!)
                }
                call.responderConMensaje(respuesta, "Respuestas enviadas; la prueba quedó terminada")
            }

            get("/intentos") {
                call.respond(servicio.historial(call.usuarioIdDesdeJwt()).map { it.aIntentoApp() })
            }
        }
    }
}

/** La app envía todas las preguntas, incluso las que dejó en blanco: esas se omiten. */
private fun RespuestaPreguntaPractica.aRespuestaPrueba(): RespuestaPrueba? {
    val opcion = opcionesSeleccionadas?.firstOrNull { it.isNotBlank() }
    val texto = (respuestaTexto ?: respuestaAbierta)?.takeIf { it.isNotBlank() }
    if (opcion == null && texto == null) return null
    return RespuestaPrueba(preguntaServidaId = preguntaId, opcionId = opcion, texto = texto)
}
