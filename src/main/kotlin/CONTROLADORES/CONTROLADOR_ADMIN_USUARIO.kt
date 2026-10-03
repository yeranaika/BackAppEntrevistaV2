package CONTROLADORES

import ESQUEMAS.RespuestaMensaje
import ESQUEMAS.SolicitudCambioRol
import ESQUEMAS.SolicitudContrasenaAdmin
import ESQUEMAS.SolicitudCrearUsuarioAdmin
import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioAdminUsuario
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuestaAdmin
import VISTAS.aRespuestaCreado
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Solo admin (401 sin token, 403 sin rol):
 * GET    /admin/usuarios
 * POST   /admin/usuarios                      (también /admin/users, ruta antigua del panel)
 * PATCH  /admin/usuarios/{usuarioId}/rol
 * DELETE /admin/usuarios/{usuarioId}          desactiva (no borra)
 * PATCH  /admin/usuarios/{usuarioId}/activar
 * PATCH  /admin/usuarios/{usuarioId}/password
 */
fun Route.controladorAdminUsuario(servicio: ServicioAdminUsuario) {
    route("/admin") {
        soloAdmin {
            get("/usuarios") {
                call.respond(servicio.listar().map { it.aRespuestaAdmin() })
            }

            post("/usuarios") { crearUsuario(servicio) }
            post("/users") { crearUsuario(servicio) }

            patch("/usuarios/{usuarioId}/rol") {
                val solicitud = call.receive<SolicitudCambioRol>()
                servicio.cambiarRol(call.usuarioIdDesdeJwt(), call.uuidDeParametro("usuarioId"), solicitud.nuevoRol)
                call.respond(RespuestaMensaje("Rol actualizado exitosamente"))
            }

            delete("/usuarios/{usuarioId}") {
                servicio.desactivar(call.usuarioIdDesdeJwt(), call.uuidDeParametro("usuarioId"))
                call.respond(RespuestaMensaje("Usuario desactivado exitosamente"))
            }

            patch("/usuarios/{usuarioId}/activar") {
                servicio.activar(call.uuidDeParametro("usuarioId"))
                call.respond(RespuestaMensaje("Usuario activado exitosamente"))
            }

            patch("/usuarios/{usuarioId}/password") {
                val solicitud = call.receive<SolicitudContrasenaAdmin>()
                servicio.restablecerContrasena(call.uuidDeParametro("usuarioId"), solicitud.nuevaContrasena)
                call.respond(RespuestaMensaje("Contraseña actualizada exitosamente"))
            }
        }
    }
}

private suspend fun RoutingContext.crearUsuario(servicio: ServicioAdminUsuario) {
    val usuario = servicio.crear(call.receive<SolicitudCrearUsuarioAdmin>())
    call.respond(HttpStatusCode.Created, usuario.aRespuestaCreado())
}
