package UTILIDADES

import ERRORES.ErrorNoAutorizado
import MODELOS.ROL_ADMIN
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

/** Id del usuario dueño del token (claim "sub"). */
fun JWTPrincipal.usuarioIdDesdeJwt(): UUID {
    val subject = subject ?: throw ErrorNoAutorizado("invalid_token", "El token no trae subject")
    return runCatching { UUID.fromString(subject) }
        .getOrElse { throw ErrorNoAutorizado("invalid_token", "El subject del token no es un UUID") }
}

/** Para rutas dentro de authenticate("auth-jwt"). */
fun ApplicationCall.usuarioIdDesdeJwt(): UUID =
    principal<JWTPrincipal>()?.usuarioIdDesdeJwt()
        ?: throw ErrorNoAutorizado("unauthorized", "Falta el token de acceso")

fun JWTPrincipal.esAdmin(): Boolean =
    payload.getClaim("role")?.asString()?.equals(ROL_ADMIN, ignoreCase = true) == true
