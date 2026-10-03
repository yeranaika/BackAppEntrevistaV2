package CONTROLADORES

import CONFIGURACION.LIMITE_RECUPERACION
import ESQUEMAS.RespuestaMensaje
import ESQUEMAS.SolicitudCambioContrasena
import ESQUEMAS.SolicitudRecuperacion
import ESQUEMAS.SolicitudRestablecer
import SERVICIOS.ServicioContrasena
import UTILIDADES.usuarioIdDesdeJwt
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * POST /auth/forgot-password   envía un código al correo (misma respuesta exista o no la cuenta)
 * POST /auth/reset-password    cambia la contraseña con el código
 * POST /auth/change-password   cambio desde el perfil (JWT + contraseña actual)
 */
fun Route.controladorContrasena(servicio: ServicioContrasena) {
    route("/auth") {
        rateLimit(LIMITE_RECUPERACION) {
            post("/forgot-password") {
                servicio.solicitarRecuperacion(call.receive<SolicitudRecuperacion>().correo)
                call.respond(RespuestaMensaje("Si el correo está registrado, te enviamos un código para restablecer tu contraseña"))
            }
        }

        post("/reset-password") {
            val solicitud = call.receive<SolicitudRestablecer>()
            servicio.restablecer(solicitud.correo, solicitud.codigo, solicitud.nuevaContrasena)
            call.respond(RespuestaMensaje("Contraseña actualizada exitosamente"))
        }

        authenticate("auth-jwt") {
            post("/change-password") {
                val solicitud = call.receive<SolicitudCambioContrasena>()
                servicio.cambiar(call.usuarioIdDesdeJwt(), solicitud.contrasenaActual, solicitud.nuevaContrasena)
                call.respond(RespuestaMensaje("Contraseña cambiada correctamente"))
            }
        }
    }
}
