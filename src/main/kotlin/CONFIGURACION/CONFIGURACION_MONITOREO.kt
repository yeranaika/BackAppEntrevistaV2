package CONFIGURACION

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configurarMonitoreo() {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            val path = call.request.path().trimEnd('/')
            path != "/health" && path != "/ready" && path != "/api/v1/practice/evaluate-freemium"
        }
    }
}
