package CONFIGURACION

import ERRORES.ErrorAplicacion
import ERRORES.ErrorConflicto
import ERRORES.ErrorDemasiadosIntentos
import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorProhibido
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.RespuestaError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

private const val MENSAJE_JSON_INVALIDO = "El cuerpo de la solicitud no es JSON válido o le faltan campos"
private const val MENSAJE_ERROR_INTERNO = "Ocurrió un error inesperado. Inténtalo nuevamente"

/** Punto único donde los errores se convierten en respuesta HTTP. */
fun Application.configurarErrores() {
    install(StatusPages) {
        exception<ErrorAplicacion> { call, error ->
            if (error is ErrorServicioExterno) {
                call.application.log.warn("Servicio externo no disponible: ${error.codigo}", error)
            }
            call.respond(estadoHttpDe(error), RespuestaError.conMensaje(error.codigo, error.message ?: error.codigo))
        }
        // Ktor 3 entrega el JSON malformado como BadRequestException; versiones previas como ContentTransformationException.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, RespuestaError.conMensaje("invalid_json", MENSAJE_JSON_INVALIDO))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, RespuestaError.conMensaje("invalid_json", MENSAJE_JSON_INVALIDO))
        }
        exception<Throwable> { call, causa ->
            call.application.log.error("Error no controlado en ${call.request.local.uri}", causa)
            call.respond(HttpStatusCode.InternalServerError, RespuestaError.conMensaje("server_error", MENSAJE_ERROR_INTERNO))
        }
    }
}

private fun estadoHttpDe(error: ErrorAplicacion): HttpStatusCode = when (error) {
    is ErrorValidacion -> HttpStatusCode.BadRequest
    is ErrorNoAutorizado -> HttpStatusCode.Unauthorized
    is ErrorProhibido -> HttpStatusCode.Forbidden
    is ErrorNoEncontrado -> HttpStatusCode.NotFound
    is ErrorConflicto -> HttpStatusCode.Conflict
    is ErrorDemasiadosIntentos -> HttpStatusCode.TooManyRequests
    is ErrorServicioExterno -> HttpStatusCode.ServiceUnavailable
}
