package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.RefreshTokenRepository
import io.ktor.server.routing.*


// Registro, login y login con Google viven en controllers.AuthController.
// Aquí queda refresh y logout hasta la Fase 1 (Login).
fun Route.authRoutes(
    refreshRepo: RefreshTokenRepository,
    issuer: String,
    audience: String,
    algorithm: Algorithm
): Route = route("/auth") {
    refreshRoutes(refreshRepo, issuer, audience, algorithm)
    logoutRoutes(refreshRepo)
}
