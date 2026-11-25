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

/** Segmento en que un evento estuvo activo según la app (ms desde que empezó la llamada) y el score máximo 0-8. */
@Serializable
data class SegmentoEventoLlamada(val idTarget: String, val inicioMs: Long, val finMs: Long, val valorMax: Double = 0.0)

/** Totales por evento de la app (toque en cara, etc.) calculados desde sus segmentos; [maximoMs] es el segmento MÁS LARGO (D46). */
@Serializable
data class EventoLlamada(val idTarget: String, val veces: Int, val totalMs: Long, val maximoMs: Long)

/**
 * Un eje del feedback (presencia, mirada, manos en la cara, apoyo en la mesa). [score] 0-10 es null en una llamada demasiado
 * corta para evaluar; [segundos] y [pctLlamada] viajan siempre junto al score para que nunca se lea sin el dato crudo.
 */
@Serializable
data class EjeFeedbackLlamada(
    val id: String,
    val score: Int? = null,
    val segundos: Double,
    val pctLlamada: Double? = null,
    val veredicto: String
)

/** Retroalimentación por reglas de la llamada: [peorEje] (null si nada supera el umbral) dicta las frases de [resumen]. */
@Serializable
data class FeedbackLlamada(
    val ejes: List<EjeFeedbackLlamada>,
    val resumen: String,
    val peorEje: String? = null
)

/**
 * Resumen del reporte tabulado de la simulación (T3.10). Solo números derivados: nunca landmarks ni video. [feedback] se
 * agregó después: las filas antiguas del JSONB no lo traen y quedan en null.
 */
@Serializable
data class ResumenLlamada(
    val acciones: List<AccionLlamada> = emptyList(),
    val linea: List<TramoLlamada> = emptyList(),
    val feedback: FeedbackLlamada? = null,
    /** D46: duración máxima persistida por evento; filas antiguas no lo traen (lista vacía). */
    val eventos: List<EventoLlamada> = emptyList()
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
