package CONFIGURACION

import ERRORES.ErrorAplicacion
import ERRORES.estadoHttp
import ERRORES.ErrorServicioExterno
import ESQUEMAS.RespuestaError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*

private const val MENSAJE_JSON_INVALIDO = "El cuerpo de la solicitud no es JSON válido o le faltan campos"
private const val MENSAJE_DEMASIADAS_SOLICITUDES = "Hiciste demasiadas solicitudes. Espera unos minutos e inténtalo de nuevo"
private const val MENSAJE_ERROR_INTERNO = "Ocurrió un error inesperado. Inténtalo nuevamente"

/** Punto único donde los errores se convierten en respuesta HTTP. */
fun Application.configurarErrores() {
    install(StatusPages) {
        exception<ErrorAplicacion> { call, error ->
            if (error is ErrorServicioExterno) {
                // Sin la traza: el mensaje de la causa (SMTP, HTTP) puede traer correos, URLs o cuerpos de respuesta.
                call.application.log.warn("Servicio externo no disponible: ${error.codigo} (${error.cause?.javaClass?.simpleName ?: "sin causa"})")
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
        // El plugin RateLimit responde 429 sin cuerpo: se completa con el formato único de error.
        status(HttpStatusCode.TooManyRequests) { call, estado ->
            call.respond(estado, RespuestaError.conMensaje("demasiadas_solicitudes", MENSAJE_DEMASIADAS_SOLICITUDES))
        }
        exception<Throwable> { call, causa ->
            call.application.log.error("Error no controlado en ${call.request.path()}", causa)
            call.respond(HttpStatusCode.InternalServerError, RespuestaError.conMensaje("server_error", MENSAJE_ERROR_INTERNO))
        }
    }
}

private fun estadoHttpDe(error: ErrorAplicacion): HttpStatusCode = HttpStatusCode.fromValue(error.estadoHttp())
