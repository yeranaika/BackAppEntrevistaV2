package CONFIGURACION

import INTEGRACIONES.Cache
import INTEGRACIONES.IpDeLaLlamada
import INTEGRACIONES.LimitadorVentanaCompartida
import UTILIDADES.ClaveIpCliente
import UTILIDADES.claveLimiteIp
import UTILIDADES.ipCliente
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Registro de cuentas: frena la creación masiva desde una misma IP. */
val LIMITE_REGISTRO = RateLimitName("registro")

/** Solicitar código de recuperación: cada solicitud envía un correo. */
val LIMITE_RECUPERACION = RateLimitName("recuperacion")

/** Prefijo de los contadores en Redis: limite:<nombre>:<ip>. Nunca debe coincidir con otro (ver Redis.md). */
const val PREFIJO_LIMITE_IP = "limite:"

private val VENTANA_REGISTRO = 10.minutes
private val VENTANA_RECUPERACION = 15.minutes
private val log = LoggerFactory.getLogger("LimiteSolicitudes")

/** Como mucho un aviso por minuto cuando el límite cae a memoria por falta de Redis. */
private class AvisoEspaciado(private val cadaMs: Long = 60_000) {
    private val ultimo = AtomicLong(0)

    fun avisar() {
        val ahora = System.currentTimeMillis()
        val previo = ultimo.get()
        if (ahora - previo >= cadaMs && ultimo.compareAndSet(previo, ahora)) {
            log.warn("Límite por IP sin Redis: se aplica en la memoria de esta instancia")
        }
    }
}

/**
 * Límites por IP compartidos entre todas las instancias a través de la caché (Redis), con ventana fija. Si Redis no
 * responde, cada instancia limita en su memoria (nunca se quita el límite). El bloqueo de login por correo está en
 * ServicioLogin. Al exceder, CONFIGURACION_ERRORES responde 429 demasiadas_solicitudes.
 */
fun Application.configurarLimiteSolicitudes(limites: ConfiguracionLimites, cache: Cache) {
    val aviso = AvisoEspaciado()
    // La IP de cada solicitud se resuelve una sola vez: queda en los atributos de la llamada (ipDelCliente, por ejemplo
    // para el consentimiento) y viaja en el contexto de su corrutina hasta el limitador (uno solo por límite, no por IP).
    intercept(ApplicationCallPipeline.Plugins) {
        val ip = call.ipCliente(limites.cabeceraIpCliente)
        call.attributes.put(ClaveIpCliente, ip)
        withContext(IpDeLaLlamada(claveLimiteIp(ip))) { proceed() }
    }
    install(RateLimit) {
        limitePorIp(LIMITE_REGISTRO, "registro", limites.registrosPorIp, VENTANA_REGISTRO, cache, aviso)
        limitePorIp(LIMITE_RECUPERACION, "recuperacion", limites.recuperacionesPorIp, VENTANA_RECUPERACION, cache, aviso)
    }
    log.info(
        "Límite por IP: registro {} cada {}, recuperación {} cada {}; IP desde {}; contador compartido en la caché ({}*)",
        limites.registrosPorIp, VENTANA_REGISTRO, limites.recuperacionesPorIp, VENTANA_RECUPERACION,
        limites.cabeceraIpCliente?.let { "la cabecera $it" } ?: "la conexión", PREFIJO_LIMITE_IP
    )
}

private fun RateLimitConfig.limitePorIp(
    nombre: RateLimitName,
    clave: String,
    limite: Int,
    ventana: Duration,
    cache: Cache,
    aviso: AvisoEspaciado
) = register(nombre) {
    val limitador = LimitadorVentanaCompartida(
        cache, "$PREFIJO_LIMITE_IP$clave:", limite, ventana,
        alFallarCache = aviso::avisar,
        alAgotarse = { restante -> log.info("Límite '{}' agotado para una IP; se repone en {} s", clave, restante.inWholeSeconds) }
    )
    // Clave fija: el plugin guarda una sola entrada por límite. Cuando la limpia y la vuelve a pedir, recibe el mismo
    // limitador, que no pierde nada (el conteo está en Redis y el respaldo en memoria vive en el propio limitador).
    requestKey { clave }
    rateLimiter { _, _ -> limitador }
}
