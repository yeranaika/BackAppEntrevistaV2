package SERVICIOS

import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import CONFIGURACION.UMBRAL_RESPUESTA_CORRECTA
import ERRORES.ErrorValidacion
import MODELOS.FeedbackGenerico
import MODELOS.NivelFeedbackGenerico
import MODELOS.OpcionSnapshot
import MODELOS.Pregunta
import MODELOS.PreguntaServida
import MODELOS.RespuestaCorregida
import MODELOS.TipoPregunta
import MODELOS.feedbackGenericoDe
import java.util.UUID

private const val PUNTAJE_MAXIMO = 100.0

/** Snapshot de una pregunta del banco para servirla en una prueba. */
fun Pregunta.aServida(orden: Int) = PreguntaServida(
    id = UUID.randomUUID().toString(),
    preguntaId = id.toString(),
    orden = orden,
    enunciado = enunciado,
    tipo = tipo.valorBd,
    categoria = categoria.valorBd,
    nivel = nivel.valorBd,
    skillId = skillId?.toString(),
    opciones = opciones.map { OpcionSnapshot(it.id.toString(), it.texto, it.esCorrecta, it.explicacion) },
    respuestaIdeal = respuestaIdeal,
    palabrasClave = palabrasClaveDe(rubrica),
    feedbackGenerico = feedbackGenericoDe(rubrica).takeUnless { it.vacio }
)

/**
 * Corrige al instante cualquier respuesta: la opción múltiple contra su opción correcta y la abierta
 * con el [EvaluadorRespuesta]. Una abierta cuenta como correcta desde [UMBRAL_RESPUESTA_CORRECTA] puntos.
 */
class CorrectorRespuestas(private val evaluador: EvaluadorRespuesta) {

    fun corregir(pregunta: PreguntaServida, opcionId: String?, texto: String?): RespuestaCorregida {
        if (pregunta.tipoPregunta == TipoPregunta.OPCION_MULTIPLE) {
            val opcion = pregunta.opciones.firstOrNull { it.id == opcionId?.trim() }
                ?: throw ErrorValidacion("opcion_invalida", "Elige una de las opciones de la pregunta")
            return RespuestaCorregida(
                opcionId = opcion.id,
                correcta = opcion.esCorrecta,
                puntaje = if (opcion.esCorrecta) PUNTAJE_MAXIMO else 0.0,
                feedback = feedbackOpcion(pregunta, opcion.esCorrecta, opcion.explicacion ?: pregunta.opcionCorrecta?.explicacion)
            )
        }
        val limpio = texto?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ErrorValidacion("respuesta_requerida", "Escribe tu respuesta")
        if (limpio.length > LARGO_MAXIMO_RESPUESTA_ENTREVISTA) {
            throw ErrorValidacion("respuesta_muy_larga", "La respuesta admite hasta $LARGO_MAXIMO_RESPUESTA_ENTREVISTA caracteres")
        }
        val evaluacion = evaluador.evaluar(
            limpio, pregunta.respuestaIdeal.orEmpty(), pregunta.palabrasClave,
            feedbackGenerico = pregunta.feedbackGenerico ?: FeedbackGenerico()
        )
        return RespuestaCorregida(
            texto = limpio,
            correcta = evaluacion.puntaje >= UMBRAL_RESPUESTA_CORRECTA,
            puntaje = evaluacion.puntaje,
            feedback = evaluacion.feedback
        )
    }

    /** Opción múltiple: el texto propio de la pregunta (correcto o malo, D36) seguido de la explicación de la opción. */
    private fun feedbackOpcion(pregunta: PreguntaServida, correcta: Boolean, explicacion: String?): String? {
        val nivel = if (correcta) NivelFeedbackGenerico.CORRECTO else NivelFeedbackGenerico.MALO
        return listOfNotNull(pregunta.feedbackGenerico?.texto(nivel), explicacion?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ")
            .ifEmpty { null }
    }
}
