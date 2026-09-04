package security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.*
import java.util.UUID

/** Obtiene el userId (UUID) desde el claim 'sub' del JWT. Lanza si el token no tiene subject. */
fun JWTPrincipal.userIdFromJwt(): UUID {
    val sub = this.subject ?: error("No subject in JWT")
    return UUID.fromString(sub)
}

/** Extensión sobre ApplicationCall — reemplaza los helpers privados repetidos en cada Route. */
fun ApplicationCall.userIdFromJwt(): UUID =
    principal<JWTPrincipal>()?.userIdFromJwt() ?: error("No JWT principal")

/** Obtiene el userId o null si no viene subject. */
fun JWTPrincipal.userIdOrNull(): String? = this.subject

fun JWTPrincipal.isAdmin(): Boolean =
    this.payload.getClaim("role")?.asString()?.equals("admin", ignoreCase = true) == true
