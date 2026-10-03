package SERVICIOS

import INTEGRACIONES.ClienteMercadoLaboral
import MODELOS.CATEGORIA_BLANDA
import MODELOS.EscritorMercado
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.Skill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinDuration
import java.time.LocalDateTime

// Las blandas parten de una base alta: se piden siempre aunque las ofertas no las nombren.
private const val DEMANDA_BASE_BLANDA = 75.0
private const val RANGO_BLANDA = 23.0
private const val DEMANDA_BASE_TECNICA = 35.0
private const val RANGO_TECNICA = 60.0
private const val TOP_RESUMEN = 10

data class DemandaSkill(val skill: Skill, val demanda: Short, val frecuenciaOfertas: Int, val nivelPredominante: NivelExperiencia)

data class ResultadoSincronizacion(val actualizadas: List<DemandaSkill>, val duracionMs: Long)

/**
 * Recalcula la demanda (1–100) de cada skill según cuántas veces aparece en ofertas reales,
 * deja historial en skill_tendencia y reajusta los pesos de los cargos. Luego regenera requisitos por cargo.
 */
class ServicioTendenciasSkill(
    private val lector: LectorMercado,
    private val escritor: EscritorMercado,
    private val clienteMercado: ClienteMercadoLaboral,
    private val requisitos: ServicioRequisitosCargo,
    /** Se llama al terminar para invalidar caché de consultas del mercado. */
    private val alActualizar: suspend () -> Unit = {}
) {
    private val log = LoggerFactory.getLogger(ServicioTendenciasSkill::class.java)

    /** Cuánto falta para que corresponda sincronizar (cero si ya toca). */
    suspend fun esperaHastaProximaSincronizacion(intervalo: Duration): Duration {
        val ultima = lector.fechaUltimaTendencia() ?: return Duration.ZERO
        val transcurrido = java.time.Duration.between(ultima, LocalDateTime.now()).toKotlinDuration()
        return (intervalo - transcurrido).coerceAtLeast(Duration.ZERO)
    }

    suspend fun sincronizar(): ResultadoSincronizacion {
        val inicio = System.currentTimeMillis()
        val skills = lector.listarSkillsActivas()
        if (skills.isEmpty()) return ResultadoSincronizacion(emptyList(), System.currentTimeMillis() - inicio)

        // Antes el texto analizado era " " y toda skill técnica quedaba con demanda 35.
        val estadisticas = NormalizadorSkill.analizarOfertas(clienteMercado.buscarOfertas(null))
        val maximo = (estadisticas.values.maxOfOrNull { it.menciones } ?: 1).coerceAtLeast(1)

        val actualizadas = skills.map { skill ->
            val estadistica = estadisticas[skill.nombre]
            val frecuencia = estadistica?.menciones ?: 0
            val proporcion = frecuencia.toDouble() / maximo
            val demanda = if (skill.categoria == CATEGORIA_BLANDA) {
                DEMANDA_BASE_BLANDA + proporcion * RANGO_BLANDA
            } else {
                DEMANDA_BASE_TECNICA + proporcion * RANGO_TECNICA
            }.roundToInt().coerceIn(1, 100).toShort()
            val nivel = estadistica?.nivelPredominante ?: NivelExperiencia.SEMISENIOR
            escritor.registrarDemanda(skill.id, demanda, frecuencia, nivel)
            DemandaSkill(skill, demanda, frecuencia, nivel)
        }

        requisitos.generarParaTodos()
        alActualizar()
        val duracion = System.currentTimeMillis() - inicio
        log.info("Tendencias sincronizadas: {} skills en {} ms", actualizadas.size, duracion)
        return ResultadoSincronizacion(actualizadas.sortedByDescending { it.demanda }.take(TOP_RESUMEN), duracion)
    }
}

/**
 * Ejecuta la sincronización de tendencias cada [intervalo]. Al arrancar solo sincroniza si ya pasó el intervalo
 * desde la última vez: reiniciar el servidor no debe gastar cuota de las APIs de empleo.
 */
class TareaSincronizacionMercado(
    private val servicio: ServicioTendenciasSkill,
    private val intervalo: Duration = 7.days,
    private val alcance: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(TareaSincronizacionMercado::class.java)
    private var trabajo: Job? = null

    @Synchronized
    fun iniciar() {
        if (trabajo != null) return
        trabajo = alcance.launch {
            while (isActive) {
                try {
                    delay(servicio.esperaHastaProximaSincronizacion(intervalo))
                    servicio.sincronizar()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Se registra y se espera al próximo ciclo: un fallo no debe detener la tarea.
                    log.error("Falló la sincronización programada de tendencias", e)
                    delay(intervalo)
                }
            }
        }
    }

    override fun close() {
        alcance.cancel()
    }
}
