package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.*


// Registro, login y login con Google viven ahora en controllers.AuthController
// (routes/services/models por capas). Aquí solo queda lo que no migró todavía:
// recuperación de contraseña, refresh y logout.
fun Route.authRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
): Route = route("/auth") {
    resetRoutes()
    refreshRoutes(issuer, audience, algorithm)
    logoutRoutes()
}
