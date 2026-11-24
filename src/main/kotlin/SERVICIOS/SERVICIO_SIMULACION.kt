package SERVICIOS

import CONFIGURACION.ACCION_ESPERANDO_VENTANA
import CONFIGURACION.ACCION_SIN_ACCION
import CONFIGURACION.PASO_PREDICCION_POR_DEFECTO_MS
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.UMBRAL_ACCION_RECONOCIDA
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.PrediccionSimulacion
import MODELOS.RepositorioPrediccionSimulacion
import MODELOS.RepositorioSesionEntrevista
import MODELOS.ReporteSimulacion
import MODELOS.ResumenAccionSimulacion
import MODELOS.SesionEntrevista
import MODELOS.TramoSimulacion
import java.time.Clock
import java.util.UUID
import kotlin.math.roundToInt

private const val NIVEL_MAXIMO_ESCALA = 8

/**
 * Guarda las predicciones de visión de una simulación (solo números 0-8, nunca landmarks ni video) y arma con ellas
 * el reporte de la línea de tiempo. Solo el dueño de la sesión puede escribir y leer.
 */
class ServicioSimulacion(
    private val predicciones: RepositorioPrediccionSimulacion,
    private val sesiones: RepositorioSesionEntrevista,
    private val reloj: Clock = Clock.systemUTC()
) {
    /**
     * Sin [sesionId] no hace nada (la predicción sigue siendo solo de consulta). [timestampMs] son los ms desde el
     * inicio de la simulación; si el cliente no lo manda se calcula con la hora del servidor.
     */
    suspend fun registrar(usuarioId: UUID, sesionId: UUID?, timestampMs: Long?, resultado: ResultadoPrediccionDivlara) {
        if (sesionId == null) return
        if (timestampMs != null && timestampMs < 0) {
            throw ErrorValidacion("timestamp_invalido", "timestampMs debe ser mayor o igual a cero")
        }
        val sesion = buscarPropia(usuarioId, sesionId)
        val ts = timestampMs ?: (reloj.millis() - sesion.fechaInicio.toEpochMilli()).coerceAtLeast(0)
        val variables = resultado.predicciones.map { (variable, valor) ->
            PrediccionSimulacion(ts, variable, valor, nivelDe(valor))
        }
        predicciones.registrar(sesionId, ts, variables)
    }

    suspend fun reporte(usuarioId: UUID, sesionId: UUID): ReporteSimulacion {
        buscarPropia(usuarioId, sesionId)
        return ConstructorReporteSimulacion.construir(sesionId, predicciones.listar(sesionId))
    }

    private suspend fun buscarPropia(usuarioId: UUID, sesionId: UUID): SesionEntrevista =
        sesiones.buscar(sesionId)?.takeIf { it.usuarioId == usuarioId }
            ?: throw ErrorNoEncontrado("entrevista_no_encontrada", "La entrevista no existe")

    private fun nivelDe(valor: Double): Int =
        if (valor.isFinite()) valor.roundToInt().coerceIn(0, NIVEL_MAXIMO_ESCALA) else 0
}

/**
 * Convierte predicciones sueltas en tramos continuos. En cada instante la acción es la variable con mayor valor, si
 * llega a [UMBRAL_ACCION_RECONOCIDA]; un tramo dura hasta el siguiente instante (el último, lo que duró el paso anterior).
 */
object ConstructorReporteSimulacion {

    fun construir(sesionId: UUID, predicciones: List<PrediccionSimulacion>): ReporteSimulacion {
        val instantes = predicciones.groupBy { it.ts }.toSortedMap()
        if (instantes.isEmpty()) return ReporteSimulacion(sesionId, 0, 0, emptyList(), emptyList())
        val acciones = instantes.map { (ts, variables) -> ts to accionDe(variables) }
        val tramos = tramosDe(acciones)
        return ReporteSimulacion(sesionId, tramos.last().finMs, acciones.size, tramos, resumir(tramos))
    }

    private fun accionDe(variables: List<PrediccionSimulacion>): String =
        variables.sortedBy { it.variable }
            .maxByOrNull { it.valor }
            ?.takeIf { it.valor >= UMBRAL_ACCION_RECONOCIDA }
            ?.variable
            ?: ACCION_SIN_ACCION

    private fun tramosDe(acciones: List<Pair<Long, String>>): List<TramoSimulacion> {
        val ultimo = acciones.last().first
        val paso = if (acciones.size > 1) ultimo - acciones[acciones.size - 2].first else PASO_PREDICCION_POR_DEFECTO_MS
        val tramos = mutableListOf<TramoSimulacion>()
        // Antes de la primera predicción el modelo aún no tenía una ventana completa.
        if (acciones.first().first > 0) tramos += TramoSimulacion(0, acciones.first().first, ACCION_ESPERANDO_VENTANA)
        acciones.forEachIndexed { i, (ts, accion) ->
            val fin = acciones.getOrNull(i + 1)?.first ?: (ultimo + paso)
            val anterior = tramos.lastOrNull()
            if (anterior != null && anterior.accion == accion) {
                tramos[tramos.lastIndex] = anterior.copy(finMs = fin)
            } else {
                tramos += TramoSimulacion(ts, fin, accion)
            }
        }
        return tramos
    }

    /** Solo acciones reconocidas: los textos de espera y de "sin acción" son marcadores de la línea de tiempo. */
    private fun resumir(tramos: List<TramoSimulacion>): List<ResumenAccionSimulacion> =
        tramos.filter { it.accion != ACCION_ESPERANDO_VENTANA && it.accion != ACCION_SIN_ACCION }
            .groupBy { it.accion }
            .map { (accion, delaAccion) ->
                ResumenAccionSimulacion(accion, delaAccion.size, delaAccion.sumOf { it.duracionMs }, delaAccion.maxOf { it.duracionMs })
            }
            .sortedWith(compareByDescending<ResumenAccionSimulacion> { it.totalMs }.thenBy { it.accion })
}
