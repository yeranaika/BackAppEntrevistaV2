package INTEGRACIONES

import io.ktor.server.plugins.ratelimit.RateLimiter
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Clave de límite que se usa si una solicitud llega sin IP en su contexto (no debería pasar). */
const val IP_DESCONOCIDA = "desconocida"

/**
 * La IP (ya normalizada como clave de límite) de la solicitud en curso. Se pone en el contexto de la corrutina de la
 * llamada, igual que el MDC de los logs, para que un único [LimitadorVentanaCompartida] por límite sepa a quién contar.
 */
class IpDeLaLlamada(val ip: String) : AbstractCoroutineContextElement(Clave) {
    companion object Clave : CoroutineContext.Key<IpDeLaLlamada>
}

/**
 * RateLimiter de Ktor respaldado por la Cache compartida (Redis): todas las instancias cuentan sobre la misma clave
 * ([prefijo] + IP), en una ventana fija. Hay **uno solo por límite** (no uno por IP): el plugin de Ktor solo limpia su
 * registro cuando una solicitud se acepta, así que un limitador por IP dejaría en memoria, para siempre, cada IP que
 * una instancia vio por primera vez ya bloqueada (por ejemplo, porque agotó su cupo en otra instancia).
 * Si la caché no responde aplica el límite en memoria de esta instancia (el comportamiento anterior): nunca lo quita.
 */
class LimitadorVentanaCompartida(
    private val cache: Cache,
    private val prefijo: String,
    private val limite: Int,
    private val ventana: Duration,
    private val alFallarCache: () -> Unit = {},
    private val alAgotarse: (restante: Duration) -> Unit = {},
    private val ahoraMs: () -> Long = System::currentTimeMillis
) : RateLimiter {

    private class Respaldo(val limitador: RateLimiter, @Volatile var ultimoUsoMs: Long)

    /** Límite en memoria por IP mientras Redis no responde. Se purga solo: ver [respaldoDe]. */
    private val respaldos = ConcurrentHashMap<String, Respaldo>()

    /** Para pruebas: cuántas IPs tienen respaldo en memoria. */
    internal val ipsEnRespaldo: Int get() = respaldos.size

    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        val ip = coroutineContext[IpDeLaLlamada]?.ip ?: IP_DESCONOCIDA
        val ventanaMs = ventana.inWholeMilliseconds
        val cuenta = cache.contarEnVentana(prefijo + ip, ventanaMs, tokens)
            ?: return respaldoDe(ip).tryConsume(tokens).also { alFallarCache() }
        val restanteMs = cuenta.restanteMs.coerceIn(1, ventanaMs)
        if (cuenta.conteo > limite) {
            // Solo el primer rechazo de la ventana avisa, para no llenar el log en un ataque.
            if (cuenta.conteo - tokens <= limite) alAgotarse(restanteMs.milliseconds)
            // Redondeado hacia arriba: Retry-After nunca es "0" mientras siga bloqueado.
            return RateLimiter.State.Exhausted(((restanteMs + 999) / 1000).seconds)
        }
        // Hora real: el plugin programa con ella la limpieza de su registro (aquí, una sola entrada por límite).
        return RateLimiter.State.Available((limite - cuenta.conteo).toInt(), limite, ahoraMs() + restanteMs)
    }

    /**
     * El respaldo en memoria de [ip]. Una IP que no se usó durante una ventana completa ya tendría su cupo lleno, así
     * que se puede descartar sin cambiar el resultado: al crear una nueva se purgan las vencidas.
     */
    private fun respaldoDe(ip: String): RateLimiter {
        val ahora = ahoraMs()
        respaldos[ip]?.let { it.ultimoUsoMs = ahora; return it.limitador }
        val ventanaMs = ventana.inWholeMilliseconds
        respaldos.entries.removeIf { ahora - it.value.ultimoUsoMs >= ventanaMs }
        return respaldos.computeIfAbsent(ip) { Respaldo(RateLimiter.default(limite, ventana), ahora) }
            .also { it.ultimoUsoMs = ahora }.limitador
    }
}
