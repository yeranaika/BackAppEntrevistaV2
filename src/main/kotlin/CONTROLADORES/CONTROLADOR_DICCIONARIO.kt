package CONTROLADORES

import ESQUEMAS.RespuestaOk
import ESQUEMAS.SolicitudGuardarDiccionario
import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioDiccionario
import UTILIDADES.responderConMensaje
import VISTAS.aRespuesta
import VISTAS.aRespuestaLista
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Diccionarios de terminología por área (sesgan y corrigen la transcripción en la app).
 * GET    /api/v1/diccionarios                       manifiesto { elementos:[{area,nombre,version,...}] } solo activos (la app compara versiones)
 * GET    /api/v1/diccionarios/{area}                paquete completo { area,nombre,version,activo,terminos,correcciones }
 * GET    /api/v1/admin/diccionarios                 (admin) manifiesto con los desactivados
 * GET    /api/v1/admin/diccionarios/{area}          (admin) paquete, aunque esté desactivado
 * PUT    /api/v1/admin/diccionarios/{area}          (admin) { nombre, terminos, correcciones?, versionEsperada? }
 *                                                   201 si el área es nueva; 200 si la reemplaza (sube la versión y la reactiva);
 *                                                   409 diccionario_version_desfasada si otro editor guardó antes
 * DELETE /api/v1/admin/diccionarios/{area}          (admin) desactiva: la app deja de recibirlo
 */
fun Route.controladorDiccionario(servicio: ServicioDiccionario) {
    authenticate("auth-jwt") {
        route("/api/v1/diccionarios") {
            get { call.respond(servicio.listar(incluirInactivos = false).aRespuestaLista()) }
            get("/{area}") {
                call.respond(servicio.obtener(call.parameters["area"].orEmpty(), incluirInactivos = false).aRespuesta())
            }
        }
    }

    route("/api/v1/admin/diccionarios") {
        soloAdmin {
            get { call.respond(servicio.listar(incluirInactivos = true).aRespuestaLista()) }
            get("/{area}") {
                call.respond(servicio.obtener(call.parameters["area"].orEmpty(), incluirInactivos = true).aRespuesta())
            }
            put("/{area}") {
                val guardado = servicio.guardar(call.parameters["area"].orEmpty(), call.receive<SolicitudGuardarDiccionario>())
                val estado = if (guardado.creado) HttpStatusCode.Created else HttpStatusCode.OK
                val mensaje = if (guardado.creado) "Diccionario creado" else "Diccionario actualizado (versión ${guardado.diccionario.version})"
                call.responderConMensaje(guardado.diccionario.aRespuesta(), mensaje, estado)
            }
            delete("/{area}") {
                servicio.desactivar(call.parameters["area"].orEmpty())
                call.respond(RespuestaOk("Diccionario desactivado"))
            }
        }
    }
}
