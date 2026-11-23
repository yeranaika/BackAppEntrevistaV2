package CONFIGURACION

import ERRORES.ErrorAplicacion
import ERRORES.ErrorServicioExterno
import ERRORES.estadoHttp
import ESQUEMAS.RespuestaError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

/** Mismo formato JSON que el backend (lo lee `ClienteMotorVision`). */
val JSON_MOTOR = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun Application.configurarSerializacionMotor() {
    install(ContentNegotiation) { json(JSON_MOTOR) }
}

/** Errores de dominio con el mismo código HTTP y cuerpo `{error, mensaje}` que el backend; el resto, 500 sin detalles. */
fun Application.configurarErroresMotor() {
    install(StatusPages) {
        exception<ErrorAplicacion> { call, error ->
            if (error is ErrorServicioExterno) call.application.log.warn("Motor: ${error.codigo}")
            call.respond(
                HttpStatusCode.fromValue(error.estadoHttp()),
                RespuestaError.conMensaje(error.codigo, error.message ?: error.codigo)
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                RespuestaError.conMensaje("invalid_json", "El cuerpo de la solicitud no es JSON válido o le faltan campos")
            )
        }
        exception<Throwable> { call, causa ->
            call.application.log.error("Error no controlado en ${call.request.path()}", causa)
            call.respond(
                HttpStatusCode.InternalServerError,
                RespuestaError.conMensaje("server_error", "Ocurrió un error inesperado en el motor de visión")
            )
        }
    }
}
