package CONTROLADORES

import ERRORES.ErrorValidacion
import ESQUEMAS.RespuestaPreguntaPractica
import ESQUEMAS.SolicitudCrearPruebaPractica
import ESQUEMAS.SolicitudEnviarRespuestasPractica
import SERVICIOS.PedidoEntrevista
import SERVICIOS.RespuestaUsuario
import SERVICIOS.ServicioEntrevista
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.TIPO_PRUEBA_ENTREVISTA
import VISTAS.aPruebaPractica
import VISTAS.aResultadoPractica
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Tipos con los que la app pide la simulación de entrevista. */
private val TIPOS_PRUEBA_ENTREVISTA = setOf(TIPO_PRUEBA_ENTREVISTA, "MIX", "SIM")

/**
 * Contrato que ya usa la app Android para la entrevista (rinde todo de una vez, sin video):
 * POST /api/prueba-practica/front                  crea la entrevista (tipoPrueba ENT | MIX | SIM)
 * POST /api/prueba-practica/{pruebaId}/respuestas  guarda todas las respuestas y la finaliza
 * Por debajo usa la misma sesión de entrevista que /api/v1/entrevistas.
 * Los tipos PR, NV y BL (práctica y nivelación) llegan en la Fase 6.
 */
fun Route.controladorPruebaPractica(servicio: ServicioEntrevista) {
    authenticate("auth-jwt") {
        route("/api/prueba-practica") {
            post("/front") {
                val solicitud = call.receive<SolicitudCrearPruebaPractica>()
                val tipo = solicitud.tipoPrueba?.trim()?.uppercase() ?: TIPO_PRUEBA_ENTREVISTA
                if (tipo !in TIPOS_PRUEBA_ENTREVISTA) {
                    throw ErrorValidacion("tipo_prueba_no_soportado", "Por ahora solo está disponible la simulación de entrevista (ENT)")
                }
                val cantidad = listOfNotNull(solicitud.cantidadPR, solicitud.cantidadNV, solicitud.cantidadBL).sum().takeIf { it > 0 }
                val pedido = PedidoEntrevista(nombreCargo = solicitud.metaCargo, nivel = solicitud.nivel, cantidadPreguntas = cantidad)
                // La app no retoma entrevistas: si quedó una a medias, se reemplaza.
                val sesion = servicio.iniciar(call.usuarioIdDesdeJwt(), pedido, reemplazarEnProgreso = true)
                call.respond(HttpStatusCode.Created, sesion.aPruebaPractica(solicitud.sector?.trim().orEmpty()))
            }

            post("/{pruebaId}/respuestas") {
                val respuestas = call.receive<SolicitudEnviarRespuestasPractica>().respuestas.mapNotNull { it.aRespuestaUsuario() }
                val sesion = servicio.responderYFinalizar(call.usuarioIdDesdeJwt(), call.uuidDeParametro("pruebaId"), respuestas)
                call.respond(sesion.aResultadoPractica())
            }
        }
    }
}

/** La app envía todas las preguntas, incluso las que dejó en blanco: esas se omiten. */
private fun RespuestaPreguntaPractica.aRespuestaUsuario(): RespuestaUsuario? {
    val opcion = opcionesSeleccionadas?.firstOrNull { it.isNotBlank() }
    val texto = respuestaAbierta?.takeIf { it.isNotBlank() }
    if (opcion == null && texto == null) return null
    return RespuestaUsuario(preguntaSesionId = uuidDelCuerpo(preguntaId), texto = texto, opcionId = opcion)
}
