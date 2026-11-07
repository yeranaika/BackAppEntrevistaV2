package UTILIDADES

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tareas que no deben bloquear la respuesta HTTP (generar el reporte, enviar el correo de recuperación).
 * Al apagar, [esperarYCerrar] se llama en ApplicationStopping: Netty ya no atiende solicitudes, pero la BD y Redis
 * siguen abiertos (se cierran después, en ApplicationStopped). Es un CoroutineScope: los servicios lanzan con launch.
 */
class TareasSegundoPlano(contexto: CoroutineContext = Dispatchers.IO) : CoroutineScope {
    private val trabajo = SupervisorJob()
    override val coroutineContext: CoroutineContext = trabajo + contexto
    private val log = LoggerFactory.getLogger(TareasSegundoPlano::class.java)

    /** Tareas que siguen corriendo. */
    val enCurso: Int get() = trabajo.children.count { it.isActive }

    /**
     * Espera hasta [tiempoMaximo] a que terminen las tareas en curso, incluidas las que se lancen mientras tanto.
     * Después cancela las que queden y les da [tiempoLimpieza] para dejar su estado en la BD.
     * Devuelve cuántas se cancelaron. Es idempotente: una vez cerrado, launch ya no ejecuta el cuerpo de nuevas tareas.
     */
    suspend fun esperarYCerrar(tiempoMaximo: Duration, tiempoLimpieza: Duration = 5.seconds): Int {
        if (enCurso > 0) log.info("Apagado: esperando {} tareas en segundo plano (máx. {})", enCurso, tiempoMaximo)
        withTimeoutOrNull(tiempoMaximo) {
            while (true) {
                // En bucle: una tarea puede lanzar otra mientras se la espera (por ejemplo, un reintento).
                val activas = trabajo.children.filter { it.isActive }.toList()
                if (activas.isEmpty()) break
                activas.joinAll()
            }
        }
        val pendientes = enCurso
        trabajo.cancel(CancellationException("apagado del servidor"))
        withTimeoutOrNull(tiempoLimpieza) { trabajo.join() }
        if (pendientes > 0) log.warn("Apagado: se cancelaron {} tareas sin terminar (quedan reintentables)", pendientes)
        return pendientes
    }

    /** Respaldo si no se llegó a esperar (por ejemplo, en ApplicationStopped). Idempotente. */
    fun cancelar() = trabajo.cancel()
}
