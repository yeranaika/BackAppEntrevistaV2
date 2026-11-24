package MODELOS

/** Valor de una variable del modelo de visión (escala 0-8) en el instante [ts] (ms desde el inicio de la simulación). */
data class PrediccionSimulacion(
    val ts: Long,
    val variable: String,
    val valor: Double,
    val nivel: Int
)

/** Tramo continuo de una misma acción en la línea de tiempo del reporte; [finMs] es exclusivo. */
data class TramoSimulacion(val inicioMs: Long, val finMs: Long, val accion: String) {
    val duracionMs: Long get() = finMs - inicioMs
}

/** Totales de una acción en todo el reporte (veces = tramos); el máximo y el promedio son por tramo. */
data class ResumenAccionSimulacion(
    val accion: String,
    val veces: Int,
    val totalMs: Long,
    val maximoMs: Long
) {
    val promedioMs: Double get() = totalMs.toDouble() / veces
}

/** Reporte de una simulación listo para darle formato; [predicciones] cuenta instantes, no variables. */
data class ReporteSimulacion(
    val sesionId: java.util.UUID,
    val duracionMs: Long,
    val predicciones: Int,
    val tramos: List<TramoSimulacion>,
    val resumen: List<ResumenAccionSimulacion>
)
