package CONTROLADORES

import CONFIGURACION.EstadoFreemiumReadiness
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Liveness: un fallo de una dependencia no debe reiniciar el proceso. */
fun Route.controladorSalud(disponibilidad: EstadoFreemiumReadiness) {
    get("/health") { call.respondText("OK") }

    get("/ready") {
        val listo = disponibilidad.estaListo()
        val estado = if (listo) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        val cuerpo = if (listo) """{"status":"ready"}""" else """{"status":"warming"}"""
        call.respondText(cuerpo, ContentType.Application.Json, estado)
    }
}
