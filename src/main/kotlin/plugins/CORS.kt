package plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // Permitir el frontend React en desarrollo (múltiples puertos)
        allowHost("localhost:5173")
        allowHost("localhost:5174")
        allowHost("127.0.0.1:5173")
        allowHost("127.0.0.1:5174")

        // Permitir el frontend en producción (ajustar según sea necesario)
        // allowHost("tudominio.com")

        // Headers necesarios para JWT y JSON
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        // Métodos HTTP permitidos
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Head)

        // Permitir credenciales (importante para JWT)
        allowCredentials = true

        // Headers que el cliente puede leer en la respuesta
        exposeHeader(HttpHeaders.Authorization)

        // Tiempo en segundos que el navegador cachea la respuesta preflight
        maxAgeInSeconds = 3600
    }
}
