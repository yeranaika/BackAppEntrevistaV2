package CONTROLADORES

import CONFIGURACION.EstadoFreemiumReadiness
import CONFIGURACION.EstadoModeloDivlara
import CONFIGURACION.JSON_API
import CONFIGURACION.ProveedorEstadoModeloDivlara
import ESQUEMAS.EstadoModeloDivlaraSalud
import ESQUEMAS.RespuestaSalud
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Liveness: un fallo de una dependencia (incluido el modelo DivLara) no debe reiniciar el proceso. */
fun Route.controladorSalud(
    disponibilidad: EstadoFreemiumReadiness,
    modeloDivlara: ProveedorEstadoModeloDivlara = ProveedorEstadoModeloDivlara { EstadoModeloDivlara.noDisponible() }
) {
    get("/health") {
        val estadoModelo = modeloDivlara.estado()
        val cuerpo = JSON_API.encodeToString(
            RespuestaSalud.serializer(),
            RespuestaSalud(
                status = "OK",
                modeloDivlara = EstadoModeloDivlaraSalud(
                    version = estadoModelo.version,
                    cargado = estadoModelo.cargado,
                    error = estadoModelo.error
                )
            )
        )
        call.respondText(cuerpo, ContentType.Application.Json, HttpStatusCode.OK)
    }

    get("/ready") {
        val listo = disponibilidad.estaListo()
        val estado = if (listo) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        val cuerpo = if (listo) """{"status":"ready"}""" else """{"status":"warming"}"""
        call.respondText(cuerpo, ContentType.Application.Json, estado)
    }
}
