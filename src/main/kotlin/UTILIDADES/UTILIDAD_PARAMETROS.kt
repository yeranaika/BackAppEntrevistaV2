package UTILIDADES

import ERRORES.ErrorValidacion
import io.ktor.server.application.ApplicationCall
import java.util.UUID

/** Lee un UUID de la ruta (ej: /usuarios/{usuarioId}) o responde 400. */
fun ApplicationCall.uuidDeParametro(nombre: String): UUID {
    val valor = parameters[nombre]
    if (valor.isNullOrBlank()) throw ErrorValidacion("id_requerido", "Falta el parámetro $nombre")
    return runCatching { UUID.fromString(valor) }
        .getOrElse { throw ErrorValidacion("id_invalido", "Formato de UUID inválido") }
}
