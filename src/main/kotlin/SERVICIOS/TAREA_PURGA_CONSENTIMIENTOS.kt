package SERVICIOS

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
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Al arrancar y una vez al día: borra los registros de consentimiento de cuentas eliminadas hace más de
 * [PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS] años (D38). Es seguro con varias instancias: cada pasada es un DELETE
 * condicional.
 */
class TareaPurgaConsentimientos(
    private val servicio: ServicioConsentimiento,
    private val reloj: Clock = Clock.systemUTC(),
    private val intervalo: Duration = 24.hours,
    private val alcance: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(TareaPurgaConsentimientos::class.java)
    private var trabajo: Job? = null

    @Synchronized
    fun iniciar() {
        if (trabajo != null) return
        trabajo = alcance.launch {
            while (isActive) {
                try {
                    servicio.purgarRegistrosVencidos(reloj.instant())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Se registra y se intenta en la próxima pasada: un fallo no debe detener la tarea.
                    log.error("Falló la purga de consentimientos de cuentas eliminadas", e)
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
