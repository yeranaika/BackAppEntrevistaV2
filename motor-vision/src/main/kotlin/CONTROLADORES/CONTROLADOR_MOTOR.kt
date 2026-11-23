package CONTROLADORES

import CONFIGURACION.ProveedorEstadoModeloDivlara
import ESQUEMAS.EstadoModeloDivlaraSalud
import ESQUEMAS.RespuestaPrediccionVision
import ESQUEMAS.SolicitudPrediccionVision
import SERVICIOS.ServicioVision
import VISTAS.aRespuesta
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
private data class RespuestaSaludMotor(val status: String, val modeloDivlara: EstadoModeloDivlaraSalud)

/**
 * API del motor (sin sesión: es un servicio interno, no debe quedar expuesto a internet):
 * POST /predict  mismo cuerpo que `/api/v1/vision/predecir` de la app → `{versionModelo, predicciones, escala}`
 * GET  /health   estado del modelo cargado
 */
fun Route.controladorMotor(servicio: ServicioVision, modelo: ProveedorEstadoModeloDivlara) {
    post("/predict") {
        val solicitud = call.receive<SolicitudPrediccionVision>()
        val respuesta: RespuestaPrediccionVision = servicio.predecir(solicitud).aRespuesta()
        call.respond(respuesta)
    }

    get("/health") {
        val estado = modelo.estado()
        call.respond(
            RespuestaSaludMotor(
                status = "OK",
                modeloDivlara = EstadoModeloDivlaraSalud(estado.version, estado.cargado, estado.error)
            )
        )
    }
}
