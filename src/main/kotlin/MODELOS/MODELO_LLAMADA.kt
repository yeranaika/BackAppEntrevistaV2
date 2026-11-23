package MODELOS

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Totales de una acción reconocida en la llamada (veces = tramos; el máximo es por tramo). */
@Serializable
data class AccionLlamada(val accion: String, val veces: Int, val totalMs: Long, val maximoMs: Long)

/** Tramo continuo de una acción en la línea de tiempo; [finMs] es exclusivo. */
@Serializable
data class TramoLlamada(val inicioMs: Long, val finMs: Long, val accion: String)

/** Resumen del reporte tabulado de la simulación (T3.10). Solo números derivados: nunca landmarks ni video. */
@Serializable
data class ResumenLlamada(
    val acciones: List<AccionLlamada> = emptyList(),
    val linea: List<TramoLlamada> = emptyList()
) {
    /** Ocurrencias de acciones reconocidas en toda la llamada. */
    val totalOcurrencias: Int get() = acciones.sumOf { it.veces }
}

/** Una llamada en vivo ya terminada. [idLocal] es el id con el que la app la guardó en el teléfono. */
data class Llamada(
    val id: UUID,
    val idLocal: String,
    val sesionId: UUID?,
    val fechaInicio: Instant,
    val duracionSeg: Int,
    val preguntasCnt: Int,
    val resumen: ResumenLlamada
)

data class PaginaLlamadas(val elementos: List<Llamada>, val total: Long, val pagina: Int, val tamano: Int)
