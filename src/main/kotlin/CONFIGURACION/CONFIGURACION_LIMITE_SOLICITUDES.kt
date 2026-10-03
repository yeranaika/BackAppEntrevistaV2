package CONFIGURACION

import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

/** Registro de cuentas: frena la creación masiva desde una misma IP. */
val LIMITE_REGISTRO = RateLimitName("registro")

/** Solicitar código de recuperación: cada solicitud envía un correo. */
val LIMITE_RECUPERACION = RateLimitName("recuperacion")

/**
 * Límites por IP en memoria de cada instancia. El bloqueo de login por correo (que sí se comparte entre
 * instancias) está en ServicioLogin. Si se excede, CONFIGURACION_ERRORES responde 429 demasiadas_solicitudes.
 */
fun Application.configurarLimiteSolicitudes(limites: ConfiguracionLimites) {
    install(RateLimit) {
        register(LIMITE_REGISTRO) {
            rateLimiter(limit = limites.registrosPorIp, refillPeriod = 10.minutes)
            requestKey { it.request.origin.remoteHost }
        }
        register(LIMITE_RECUPERACION) {
            rateLimiter(limit = limites.recuperacionesPorIp, refillPeriod = 15.minutes)
            requestKey { it.request.origin.remoteHost }
        }
    }
}
