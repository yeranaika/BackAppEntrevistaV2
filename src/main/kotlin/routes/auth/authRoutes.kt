package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.*


fun Route.authRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
): Route = route("/auth") {
    registerRoutes(issuer, audience, algorithm)
    loginRoutes(issuer, audience, algorithm)
    resetRoutes()
    refreshRoutes(issuer, audience, algorithm)
    logoutRoutes()
}
