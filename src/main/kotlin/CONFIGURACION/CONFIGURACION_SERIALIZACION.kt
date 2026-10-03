package CONFIGURACION

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Formato JSON de toda la API (también lo usa responderConMensaje para no divergir). */
val JSON_API = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
}

fun Application.configurarSerializacion() {
    install(ContentNegotiation) {
        json(JSON_API)
    }
}
