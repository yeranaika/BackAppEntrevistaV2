package com.example

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*  
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        // Ejemplos opcionales:
        // filter { call -> call.request.path().startsWith("/api") }
        // format { call -> "→ ${call.request.httpMethod.value} ${call.request.path()}" }
    }
}
