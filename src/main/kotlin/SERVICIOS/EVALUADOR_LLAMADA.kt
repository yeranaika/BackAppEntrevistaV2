package SERVICIOS

import CONFIGURACION.DURACION_MINIMA_FEEDBACK_SEG
import CONFIGURACION.K_FEEDBACK_APOYO_MESA
import CONFIGURACION.K_FEEDBACK_MANOS_CARA
import CONFIGURACION.K_FEEDBACK_MIRADA
import CONFIGURACION.K_FEEDBACK_PRESENCIA_CUERPO
import CONFIGURACION.SCORE_FEEDBACK_BIEN
import CONFIGURACION.SCORE_FEEDBACK_MAXIMO
import CONFIGURACION.SCORE_FEEDBACK_MEJORABLE
import MODELOS.EjeFeedbackLlamada
import MODELOS.FeedbackLlamada
import MODELOS.ResumenLlamada
import MODELOS.SegmentoEventoLlamada
import kotlin.math.roundToInt

const val EJE_MANOS_CARA = "manos_cara"
const val EJE_MIRADA = "mirada"
const val EJE_PRESENCIA_CUERPO = "presencia_cuerpo"
const val EJE_APOYO_MESA = "apoyo_mesa"

const val VEREDICTO_BIEN = "Bien"
const val VEREDICTO_MEJORABLE = "Puede mejorar"
const val VEREDICTO_PRIORIDAD = "Prioridad de mejora"
const val VEREDICTO_SIN_EVALUAR = "Sin evaluar"

const val RESUMEN_FEEDBACK_POSITIVO =
    "Sin señales relevantes de las 6 conductas observadas: buen trabajo. Mantén esta postura y tu mirada en la cámara."
const val RESUMEN_FEEDBACK_LLAMADA_CORTA =
    "La llamada duró menos de $DURACION_MINIMA_FEEDBACK_SEG s: no alcanza para darte una guía, solo te mostramos lo detectado."

private const val MS_POR_SEGUNDO = 1_000.0
private const val PORCENTAJE = 100.0

/** Un eje agrupa eventos de la app; [frases] son las acciones concretas que se sugieren si es el peor eje. */
private class EjeConducta(
    val id: String,
    val nombre: String,
    val eventos: Set<String>,
    val k: Double,
    val frases: List<String>
)

/** El orden es la prioridad: con el mismo score manda el primero (lo más visible para quien entrevista va antes). */
private val EJES = listOf(
    EjeConducta(
        EJE_MANOS_CARA, "Manos y cara", setOf("hand_to_face_adaptors"), K_FEEDBACK_MANOS_CARA,
        listOf(
            "Apoya las manos en la mesa o sobre tus piernas cuando no las uses para explicar algo.",
            "Si sientes el impulso de tocarte la cara, respira y retoma la idea con las manos quietas."
        )
    ),
    EjeConducta(
        EJE_MIRADA, "Mirada", setOf("gaze_direction", "gaze_alignment"), K_FEEDBACK_MIRADA,
        listOf(
            "Intenta sostener la mirada en la cámara mientras respondes.",
            "Si necesitas pensar, está bien mirar a un lado un momento y luego volver a la cámara."
        )
    ),
    EjeConducta(
        EJE_PRESENCIA_CUERPO, "Presencia", setOf("torso_lean", "arm_crossing", "table_rest", "manos_juntas"), K_FEEDBACK_PRESENCIA_CUERPO,
        listOf(
            "Mantén el tronco erguido y los brazos abiertos, con las manos a la vista.",
            "Apoyar la espalda en la silla te ayuda a sostener una postura estable."
        )
    ),
    EjeConducta(
        EJE_APOYO_MESA, "Apoyo en la mesa", setOf("table_rest"), K_FEEDBACK_APOYO_MESA,
        listOf("Apoya las manos con suavidad, sin cargar el peso del cuerpo sobre la mesa.")
    )
)

/**
 * Feedback por reglas de una llamada en vivo (N1, sin LLM). Por eje: `tasa = segundos del evento / duración` y
 * `score = clamp(0..10, 10 − k × tasa)`. Los segundos salen de los segmentos por evento de la app (unión de intervalos, para
 * no contar dos veces un eje con varios eventos a la vez); sin segmentos, de los totales por acción del reporte tabulado.
 * Solo usa eventos derivados: nunca video ni landmarks.
 */
object EvaluadorLlamada {

    /** null si la llamada no trae ninguna observación (sin línea, acciones ni segmentos): no hay sobre qué opinar. */
    fun evaluar(duracionSeg: Int, resumen: ResumenLlamada, segmentos: List<SegmentoEventoLlamada>): FeedbackLlamada? {
        if (resumen.linea.isEmpty() && resumen.acciones.isEmpty() && segmentos.isEmpty()) return null
        val duracionMs = duracionSeg.coerceAtLeast(0) * MS_POR_SEGUNDO.toLong()
        val medidos = EJES.map { it to msDe(it, resumen, segmentos, duracionMs) }
        if (duracionSeg < DURACION_MINIMA_FEEDBACK_SEG) {
            val ejes = medidos.map { (eje, ms) -> EjeFeedbackLlamada(eje.id, segundos = segundos(ms), veredicto = VEREDICTO_SIN_EVALUAR) }
            return FeedbackLlamada(ejes, RESUMEN_FEEDBACK_LLAMADA_CORTA)
        }
        val puntuados = medidos.map { (eje, ms) -> eje to puntuar(eje, ms, duracionMs) }
        // minBy conserva el primero ante empate: el orden de EJES es la prioridad.
        val peor = puntuados.map { (eje, e) -> eje to (e.score ?: SCORE_FEEDBACK_MAXIMO) }
            .filter { (_, score) -> score < SCORE_FEEDBACK_BIEN }
            .minByOrNull { (_, score) -> score }?.first
        val resumenTexto = peor?.let { "${it.nombre}: ${it.frases.joinToString(" ")}" } ?: RESUMEN_FEEDBACK_POSITIVO
        return FeedbackLlamada(puntuados.map { it.second }, resumenTexto, peor?.id)
    }

    private fun puntuar(eje: EjeConducta, ms: Long, duracionMs: Long): EjeFeedbackLlamada {
        val tasa = ms.toDouble() / duracionMs
        val score = (SCORE_FEEDBACK_MAXIMO - eje.k * tasa).coerceIn(0.0, SCORE_FEEDBACK_MAXIMO.toDouble()).roundToInt()
        return EjeFeedbackLlamada(eje.id, score, segundos(ms), unDecimal(tasa * PORCENTAJE), veredictoDe(score))
    }

    private fun veredictoDe(score: Int) = when {
        score >= SCORE_FEEDBACK_BIEN -> VEREDICTO_BIEN
        score >= SCORE_FEEDBACK_MEJORABLE -> VEREDICTO_MEJORABLE
        else -> VEREDICTO_PRIORIDAD
    }

    /** Milisegundos del eje recortados a la llamada (si se conoce su duración): la tasa nunca pasa de 100 %. */
    private fun msDe(eje: EjeConducta, resumen: ResumenLlamada, segmentos: List<SegmentoEventoLlamada>, duracionMs: Long): Long {
        val total = if (segmentos.isNotEmpty()) {
            unionMs(segmentos.filter { it.idTarget in eje.eventos }, duracionMs)
        } else {
            // Las acciones del reporte tabulado son excluyentes en el tiempo (una por instante): se pueden sumar.
            resumen.acciones.filter { it.accion in eje.eventos }.sumOf { it.totalMs }
        }
        return if (duracionMs > 0) total.coerceIn(0, duracionMs) else total.coerceAtLeast(0)
    }

    private fun unionMs(segmentos: List<SegmentoEventoLlamada>, duracionMs: Long): Long {
        val limite = if (duracionMs > 0) duracionMs else Long.MAX_VALUE
        var total = 0L
        var finCubierto = 0L
        for (s in segmentos.sortedBy { it.inicioMs }) {
            val inicio = maxOf(s.inicioMs, finCubierto)
            val fin = minOf(s.finMs, limite)
            if (fin > inicio) total += fin - inicio
            finCubierto = maxOf(finCubierto, fin)
        }
        return total
    }

    private fun segundos(ms: Long) = unDecimal(ms / MS_POR_SEGUNDO)

    private fun unDecimal(valor: Double) = (valor * 10).roundToInt() / 10.0
}
