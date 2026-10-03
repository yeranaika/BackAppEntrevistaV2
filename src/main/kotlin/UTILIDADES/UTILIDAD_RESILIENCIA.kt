package UTILIDADES

import ERRORES.ErrorAplicacion
import ERRORES.ErrorServicioExterno
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.random.Random

/** Fallas que suelen arreglarse solas al reintentar: red caída, timeout. */
fun esFallaTransitoria(error: Throwable): Boolean =
    error is IOException || error is TimeoutCancellationException

/**
 * Política común para hablar con un servicio externo:
 * - tiempo máximo por intento;
 * - reintentos con espera exponencial y variación aleatoria, solo si la falla es transitoria;
 * - cortocircuito: tras [fallosParaAbrir] fallas seguidas deja de llamar durante [tiempoAbierto]
 *   y responde de inmediato, para no dejar a cada usuario esperando un servicio caído.
 *
 * Si se agotan los intentos se relanza el error de dominio original, o un 503 `<nombre>_no_disponible`.
 */
class PoliticaResiliencia(
    val nombre: String,
    private val tiempoMaximo: Duration = 10.seconds,
    private val intentos: Int = 3,
    private val esperaInicial: Duration = 200.milliseconds,
    private val fallosParaAbrir: Int = 5,
    private val tiempoAbierto: Duration = 30.seconds,
    private val ahoraMs: () -> Long = System::currentTimeMillis,
    private val esperar: suspend (Duration) -> Unit = { delay(it) }
) {
    private val log = LoggerFactory.getLogger(PoliticaResiliencia::class.java)
    private val fallosSeguidos = AtomicInteger(0)
    private val abiertoHastaMs = AtomicLong(0)

    val estaAbierto: Boolean get() = ahoraMs() < abiertoHastaMs.get()

    suspend fun <T> ejecutar(
        esReintentable: (Throwable) -> Boolean = ::esFallaTransitoria,
        bloque: suspend () -> T
    ): T {
        if (estaAbierto) throw noDisponible(null)

        var ultimoError: Throwable? = null
        repeat(intentos) { intento ->
            try {
                val resultado = withTimeout(tiempoMaximo) { bloque() }
                fallosSeguidos.set(0)
                return resultado
            } catch (e: Throwable) {
                // Una cancelación real (el cliente se fue, la app se apaga) nunca se reintenta.
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (!esReintentable(e)) throw e
                ultimoError = e
                // La variación evita que muchos clientes reintenten todos en el mismo instante.
                if (intento < intentos - 1) esperar(esperaInicial * (1 shl intento) * (1.0 + Random.nextDouble(0.0, 0.5)))
            }
        }
        registrarFalla()
        val error = ultimoError
        throw if (error is ErrorAplicacion) error else noDisponible(error)
    }

    private fun registrarFalla() {
        if (fallosSeguidos.incrementAndGet() >= fallosParaAbrir) {
            abiertoHastaMs.set(ahoraMs() + tiempoAbierto.inWholeMilliseconds)
            fallosSeguidos.set(0)
            log.warn("Cortocircuito abierto para '{}' durante {}", nombre, tiempoAbierto)
        }
    }

    private fun noDisponible(causa: Throwable?) =
        ErrorServicioExterno("${nombre}_no_disponible", "El servicio $nombre no está disponible", causa)
}
