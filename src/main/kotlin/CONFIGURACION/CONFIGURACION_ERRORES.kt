package CONFIGURACION

import ERRORES.ErrorAplicacion
import ERRORES.ErrorConflicto
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

/** Punto único donde los errores se convierten en respuesta HTTP. */
fun Application.configurarErrores() {
    install(StatusPages) {
        exception<ErrorAplicacion> { call, error ->
            if (error is ErrorServicioExterno) {
                call.application.log.warn("Servicio externo no disponible: ${error.codigo}", error)
            }
            call.respond(estadoHttpDe(error), RespuestaError(error.codigo, error.message))
        }
        // Ktor 3 entrega el JSON malformado como BadRequestException; versiones previas como ContentTransformationException.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, RespuestaError("invalid_json"))
        }
        exception<Throwable> { call, causa ->
            call.application.log.error("Error no controlado en ${call.request.local.uri}", causa)
            call.respond(HttpStatusCode.InternalServerError, RespuestaError("server_error"))
        }
    }
}

private fun estadoHttpDe(error: ErrorAplicacion): HttpStatusCode = when (error) {
    is ErrorValidacion -> HttpStatusCode.BadRequest
    is ErrorNoAutorizado -> HttpStatusCode.Unauthorized
    is ErrorProhibido -> HttpStatusCode.Forbidden
    is ErrorNoEncontrado -> HttpStatusCode.NotFound
    is ErrorConflicto -> HttpStatusCode.Conflict
    is ErrorServicioExterno -> HttpStatusCode.ServiceUnavailable
}
