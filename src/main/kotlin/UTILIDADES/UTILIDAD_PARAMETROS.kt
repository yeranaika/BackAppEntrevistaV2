package UTILIDADES

import ERRORES.ErrorValidacion
import io.ktor.server.application.ApplicationCall
import java.time.LocalDate
import java.util.UUID

/** Lee un UUID de la ruta (ej: /usuarios/{usuarioId}) o responde 400. */
fun ApplicationCall.uuidDeParametro(nombre: String): UUID {
    val valor = parameters[nombre]
    if (valor.isNullOrBlank()) throw ErrorValidacion("id_requerido", "Falta el parámetro $nombre")
    return runCatching { UUID.fromString(valor) }
        .getOrElse { throw ErrorValidacion("id_invalido", "Formato de UUID inválido") }
}

/** Query param entero opcional (ej: ?pagina=2); responde 400 si no es número. */
fun ApplicationCall.enteroDeConsulta(nombre: String): Int? {
    val valor = request.queryParameters[nombre]?.takeIf { it.isNotBlank() } ?: return null
    return valor.toIntOrNull() ?: throw ErrorValidacion("parametro_invalido", "El parámetro $nombre debe ser un número")
}

/** Query param booleano opcional (true/false); responde 400 con cualquier otro valor. */
fun ApplicationCall.booleanoDeConsulta(nombre: String): Boolean? {
    val valor = request.queryParameters[nombre]?.takeIf { it.isNotBlank() } ?: return null
    return valor.lowercase().toBooleanStrictOrNull()
        ?: throw ErrorValidacion("parametro_invalido", "El parámetro $nombre debe ser true o false")
}

/** Query param UUID opcional (ej: ?cargoId=...); responde 400 si no tiene formato de UUID. */
fun ApplicationCall.uuidDeConsulta(nombre: String): UUID? {
    val valor = request.queryParameters[nombre]?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { UUID.fromString(valor) }
        .getOrElse { throw ErrorValidacion("id_invalido", "El parámetro $nombre no tiene formato de UUID") }
}

/** Query param de fecha opcional en formato AAAA-MM-DD (ej: ?desde=2026-11-01); responde 400 con otro formato. */
fun ApplicationCall.fechaDeConsulta(nombre: String): LocalDate? {
    val valor = request.queryParameters[nombre]?.takeIf { it.isNotBlank() } ?: return null
    val fecha = runCatching { LocalDate.parse(valor.trim()) }.getOrNull()
    // ISO acepta años extremos (+999999999) que después desbordan los cálculos: solo años razonables.
    if (fecha == null || fecha.year !in 2000..2100) {
        throw ErrorValidacion("parametro_invalido", "El parámetro $nombre debe ser una fecha AAAA-MM-DD")
    }
    return fecha
}
