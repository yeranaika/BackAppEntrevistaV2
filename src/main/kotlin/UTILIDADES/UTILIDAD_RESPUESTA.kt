package UTILIDADES

import CONFIGURACION.JSON_API
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

const val CAMPO_MENSAJE = "mensaje"

/**
 * Responde el recurso agregando `mensaje`: texto en español que el cliente puede mostrar cuando la operación salió bien.
 * Se agrega como campo extra (no envuelve el recurso) para no romper a los clientes que ya leen los demás campos.
 */
suspend inline fun <reified T> ApplicationCall.responderConMensaje(
    cuerpo: T,
    mensaje: String,
    estado: HttpStatusCode = HttpStatusCode.OK
) {
    val campos = JSON_API.encodeToJsonElement(cuerpo).jsonObject
    respond(estado, JsonObject(campos + (CAMPO_MENSAJE to JsonPrimitive(mensaje))))
}
