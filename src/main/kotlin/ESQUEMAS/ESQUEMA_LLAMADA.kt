package ESQUEMAS

import MODELOS.AccionLlamada
import MODELOS.EventoLlamada
import MODELOS.FeedbackLlamada
import MODELOS.SegmentoEventoLlamada
import MODELOS.TramoLlamada
import kotlinx.serialization.Serializable

// ---------- Historial de llamadas en vivo (T3.15) ----------

/**
 * Reporte tabulado de la llamada. Si falta y viene `sesionId`, el servidor lo arma con las predicciones de esa sesión.
 * [segmentos] (opcional, la app lo manda desde T3.18) se usa para el feedback y para persistir el máximo por evento
 * (`eventos`, D46); los segmentos crudos no se guardan.
 */
@Serializable
data class ResumenLlamadaApp(
    val acciones: List<AccionLlamada> = emptyList(),
    val linea: List<TramoLlamada> = emptyList(),
    val segmentos: List<SegmentoEventoLlamada>? = null
)

@Serializable
data class SolicitudRegistrarLlamada(
    /** Id con el que la app guardó la llamada en el teléfono: hace idempotente el reenvío. */
    val idLocal: String,
    /** Milisegundos desde 1970 en que empezó la llamada. */
    val fechaInicioMs: Long,
    val duracionSeg: Int,
    val preguntasCnt: Int = 0,
    val sesionId: String? = null,
    val resumen: ResumenLlamadaApp? = null
)

/** Fila de la lista. Sin valores por defecto: el cliente lee todos los campos. */
@Serializable
data class RespuestaResumenLlamada(
    val id: String,
    val idLocal: String,
    val fechaInicioMs: Long,
    val duracionSeg: Int,
    val preguntasCnt: Int,
    /** Ocurrencias de acciones reconocidas. */
    val accionesCnt: Int
)

@Serializable
data class RespuestaHistorialLlamadas(
    val elementos: List<RespuestaResumenLlamada>,
    val total: Long,
    val pagina: Int,
    val tamano: Int
)

@Serializable
data class RespuestaDetalleLlamada(
    val id: String,
    val idLocal: String,
    val sesionId: String?,
    val fechaInicioMs: Long,
    val duracionSeg: Int,
    val preguntasCnt: Int,
    val accionesCnt: Int,
    val acciones: List<AccionLlamada>,
    val linea: List<TramoLlamada>,
    /** Campo agregado: ausente en llamadas guardadas antes del feedback (la app ignora claves que no conoce). */
    val feedback: FeedbackLlamada? = null,
    /** Campo agregado (D46): veces, total y duración máxima por evento de la app; vacío en llamadas antiguas. */
    val eventos: List<EventoLlamada> = emptyList()
)
