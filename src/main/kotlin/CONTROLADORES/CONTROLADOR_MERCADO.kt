package CONTROLADORES

import ESQUEMAS.RespuestaCargoCreado
import ESQUEMAS.RespuestaGeneracionMasiva
import ESQUEMAS.SolicitudCrearCargo
import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioMercado
import SERVICIOS.ServicioTendenciasSkill
import UTILIDADES.enteroDeConsulta
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Catálogo público:
 * GET /api/v1/cargos                  (también /market/cargos, ruta antigua del panel)
 * GET /api/v1/cargos/{id}/skills      matriz completa del cargo
 * GET /market/cargos/{id}/skills      solo la lista de requisitos (formato antiguo)
 * GET /api/v1/skills/trending         ?categoria=tecnica|blanda&limit=1..100
 * GET /market/skills                  skills activas por demanda
 * GET /market/skills/{id}/tendencias  historial semanal
 *
 * Solo admin (consumen cuota de las APIs de empleo):
 * POST /admin/market/sync-trends
 * POST /admin/market/cargos
 * POST /admin/market/cargos/{id}/generate-requirements
 * POST /admin/market/cargos/generate-all
 */
fun Route.controladorMercado(mercado: ServicioMercado, tendencias: ServicioTendenciasSkill) {
    get("/api/v1/cargos") { call.respond(mercado.listarCargos()) }
    get("/market/cargos") { call.respond(mercado.listarCargos()) }

    get("/api/v1/cargos/{id}/skills") {
        call.respond(mercado.matrizDeCargo(call.uuidDeParametro("id")))
    }
    get("/market/cargos/{id}/skills") {
        call.respond(mercado.matrizDeCargo(call.uuidDeParametro("id")).skills)
    }

    get("/api/v1/skills/trending") {
        call.respond(mercado.tendencias(call.request.queryParameters["categoria"], call.enteroDeConsulta("limit")))
    }
    get("/market/skills") {
        call.respond(mercado.listarSkills().map { it.aRespuesta() })
    }
    get("/market/skills/{id}/tendencias") {
        call.respond(mercado.historialDeSkill(call.uuidDeParametro("id")).map { it.aRespuesta() })
    }

    route("/admin/market") {
        soloAdmin {
            post("/sync-trends") {
                call.respond(tendencias.sincronizar().aRespuesta())
            }

            post("/cargos") {
                val (cargo, generados) = mercado.crearCargo(call.receive<SolicitudCrearCargo>())
                call.respond(HttpStatusCode.Created, RespuestaCargoCreado(cargo.aRespuesta(), generados?.aRespuesta()))
            }

            post("/cargos/{id}/generate-requirements") {
                call.respond(mercado.regenerarRequisitos(call.uuidDeParametro("id")).aRespuesta())
            }

            post("/cargos/generate-all") {
                val resultados = mercado.regenerarTodos()
                call.respond(RespuestaGeneracionMasiva(resultados.size, resultados.map { it.aRespuesta() }))
            }
        }
    }
}
