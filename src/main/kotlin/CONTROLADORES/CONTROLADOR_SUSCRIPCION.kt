package CONTROLADORES

import ESQUEMAS.RespuestaCompraVerificada
import ESQUEMAS.SolicitudCanjearCodigo
import ESQUEMAS.SolicitudCrearCodigo
import ESQUEMAS.SolicitudVerificarCompra
import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioSuscripcion
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * POST /billing/google/verify   activa premium con una compra de Google Play
 * GET  /billing/status          estado de la suscripción (Android)
 * POST /billing/code/redeem     canjea un código promocional (Android)
 * POST /billing/admin/codes     crea códigos (admin)
 */
fun Route.controladorSuscripcion(servicio: ServicioSuscripcion) {
    route("/billing") {
        authenticate("auth-jwt") {
            post("/google/verify") {
                val solicitud = call.receive<SolicitudVerificarCompra>()
                servicio.verificarCompraGoogle(call.usuarioIdDesdeJwt(), solicitud.productoId, solicitud.tokenCompra)
                call.responderConMensaje(RespuestaCompraVerificada(status = "premium_active"), "Compra verificada; tu cuenta ya es premium")
            }

            get("/status") {
                call.respond(servicio.estado(call.usuarioIdDesdeJwt()).aRespuesta())
            }

            post("/code/redeem") {
                val solicitud = call.receive<SolicitudCanjearCodigo>()
                call.responderConMensaje(servicio.canjearCodigo(call.usuarioIdDesdeJwt(), solicitud.codigo).aRespuesta(), "Código canjeado; tu cuenta ya es premium")
            }
        }

        route("/admin") {
            soloAdmin {
                post("/codes") {
                    call.responderConMensaje(
                        servicio.crearCodigo(call.receive<SolicitudCrearCodigo>()).aRespuesta(),
                        "Código promocional creado",
                        HttpStatusCode.Created
                    )
                }
            }
        }
    }
}
