package CONFIGURACION

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level

fun Application.configurarMonitoreo() {
    install(CallLogging) {
        level = Level.INFO
    }
}
