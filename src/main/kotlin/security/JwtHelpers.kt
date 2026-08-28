package security

import io.ktor.server.auth.jwt.*

fun JWTPrincipal.userIdOrNull(): String? = this.subject

fun JWTPrincipal.isAdmin(): Boolean =
    this.payload.getClaim("role")?.asString()?.equals("admin", ignoreCase = true) == true
