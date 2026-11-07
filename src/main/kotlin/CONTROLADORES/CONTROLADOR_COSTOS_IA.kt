package CONTROLADORES

import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioCostosIa
import UTILIDADES.fechaDeConsulta
import VISTAS.aRespuesta
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Solo admin (401 sin token, 403 sin rol):
 * GET /api/v1/admin/costos-ia?desde=AAAA-MM-DD&hasta=AAAA-MM-DD
 *     Gasto en IA por día (UTC) y por modelo: reportes de entrevista con IA y generación de preguntas.
 *     Sin fechas: los últimos 30 días. Máximo 92 días (400 rango_invalido). Fecha mal escrita: 400 parametro_invalido.
 */
fun Route.controladorCostosIa(servicio: ServicioCostosIa) {
    route("/api/v1/admin") {
        soloAdmin {
            get("/costos-ia") {
                call.respond(servicio.resumen(call.fechaDeConsulta("desde"), call.fechaDeConsulta("hasta")).aRespuesta())
            }
        }
    }
}
