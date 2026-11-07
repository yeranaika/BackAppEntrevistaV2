package SERVICIOS

import CONFIGURACION.INTERVALO_RESCATE_REPORTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * Al arrancar y cada [intervalo]: deja reintentables los reportes que un apagado sin aviso (SIGKILL, caída, Railway sin
 * tiempo de drenado) dejó en 'generando' (D20). Es seguro con varias instancias: cada pasada es un UPDATE condicional.
 */
class TareaRescateReportes(
    private val servicio: ServicioReporteEntrevista,
    private val intervalo: Duration = INTERVALO_RESCATE_REPORTES.toKotlinDuration(),
    private val alcance: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(TareaRescateReportes::class.java)
    private var trabajo: Job? = null

    @Synchronized
    fun iniciar() {
        if (trabajo != null) return
        trabajo = alcance.launch {
            while (isActive) {
                try {
                    servicio.rescatarInterrumpidos()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Se registra y se intenta en la próxima pasada: un fallo no debe detener la tarea.
                    log.error("Falló el rescate de reportes interrumpidos", e)
                }
                delay(intervalo)
            }
        }
    }

    /** Para la tarea y espera (con tope) a que termine una pasada en curso. Se llama en ApplicationStopping. */
    suspend fun detener() {
        withTimeoutOrNull(5.seconds) { trabajo?.cancelAndJoin() }
    }

    override fun close() = alcance.cancel()
}
