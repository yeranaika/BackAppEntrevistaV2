package CONFIGURACION

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

fun Application.configurarSerializacion() {
    install(ContentNegotiation) {
        json(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
}
