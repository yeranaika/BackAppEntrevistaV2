package MODELOS

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** Clave de la rúbrica donde vive el feedback genérico de cada pregunta (D36). */
const val CLAVE_FEEDBACK_GENERICO = "feedback_generico"

/** Largo máximo de cada texto: es un mensaje corto para el usuario, no una explicación completa. */
const val LARGO_MAXIMO_FEEDBACK_GENERICO = 500

/** Los tres niveles de feedback genérico, con su nombre en la rúbrica. */
enum class NivelFeedbackGenerico(val clave: String) {
    CORRECTO("correcto"),
    MEDIO("medio"),
    MALO("malo")
}

/**
 * Feedback genérico de una pregunta (D36): un texto para cuando la respuesta está bien, otro para cuando está a medias y
 * otro para cuando está mal. Lo escribe la IA al generar la pregunta (o el admin) y lo usa el feedback gratuito, sin
 * gastar tokens al corregir. Puede estar incompleto: falta un nivel = null, y el método de completar lo rellena.
 */
@Serializable
data class FeedbackGenerico(
    val correcto: String? = null,
    val medio: String? = null,
    val malo: String? = null
) {
    val completo: Boolean get() = NivelFeedbackGenerico.entries.all { texto(it) != null }
    val vacio: Boolean get() = NivelFeedbackGenerico.entries.none { texto(it) != null }

    /** Niveles que faltan (o están en blanco). */
    val faltantes: List<NivelFeedbackGenerico> get() = NivelFeedbackGenerico.entries.filter { texto(it) == null }

    fun texto(nivel: NivelFeedbackGenerico): String? = when (nivel) {
        NivelFeedbackGenerico.CORRECTO -> correcto
        NivelFeedbackGenerico.MEDIO -> medio
        NivelFeedbackGenerico.MALO -> malo
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** Sin espacios sobrantes y sin textos en blanco. */
    fun limpio() = FeedbackGenerico(
        texto(NivelFeedbackGenerico.CORRECTO),
        texto(NivelFeedbackGenerico.MEDIO),
        texto(NivelFeedbackGenerico.MALO)
    )

    /** Sin los textos que pasan de [LARGO_MAXIMO_FEEDBACK_GENERICO]: lo que llega de la IA no se rechaza, se descarta. */
    fun sinTextosLargos(): FeedbackGenerico {
        fun corto(nivel: NivelFeedbackGenerico) = texto(nivel)?.takeIf { it.length <= LARGO_MAXIMO_FEEDBACK_GENERICO }
        return FeedbackGenerico(corto(NivelFeedbackGenerico.CORRECTO), corto(NivelFeedbackGenerico.MEDIO), corto(NivelFeedbackGenerico.MALO))
    }

    /** Completa con [nuevo] lo que falta; con [sobrescribir] gana [nuevo] en todo lo que trae. */
    fun combinar(nuevo: FeedbackGenerico, sobrescribir: Boolean): FeedbackGenerico {
        val (primero, segundo) = if (sobrescribir) nuevo to this else this to nuevo
        return FeedbackGenerico(
            primero.texto(NivelFeedbackGenerico.CORRECTO) ?: segundo.texto(NivelFeedbackGenerico.CORRECTO),
            primero.texto(NivelFeedbackGenerico.MEDIO) ?: segundo.texto(NivelFeedbackGenerico.MEDIO),
            primero.texto(NivelFeedbackGenerico.MALO) ?: segundo.texto(NivelFeedbackGenerico.MALO)
        )
    }

    fun aJson(): JsonObject = buildJsonObject {
        NivelFeedbackGenerico.entries.forEach { nivel -> texto(nivel)?.let { put(nivel.clave, JsonPrimitive(it)) } }
    }
}

/** Feedback genérico guardado en la rúbrica; vacío si no tiene o si no es un objeto. */
fun feedbackGenericoDe(rubrica: JsonObject?): FeedbackGenerico {
    val objeto = rubrica?.get(CLAVE_FEEDBACK_GENERICO) as? JsonObject ?: return FeedbackGenerico()
    fun leer(nivel: NivelFeedbackGenerico) = (objeto[nivel.clave] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    return FeedbackGenerico(
        leer(NivelFeedbackGenerico.CORRECTO),
        leer(NivelFeedbackGenerico.MEDIO),
        leer(NivelFeedbackGenerico.MALO)
    ).limpio()
}

/** La rúbrica con [feedback] en su clave (el resto de la rúbrica no cambia). Sin rúbrica se crea una solo con eso. */
fun rubricaConFeedbackGenerico(rubrica: JsonObject?, feedback: FeedbackGenerico): JsonObject =
    JsonObject((rubrica ?: JsonObject(emptyMap())) + (CLAVE_FEEDBACK_GENERICO to feedback.aJson()))
