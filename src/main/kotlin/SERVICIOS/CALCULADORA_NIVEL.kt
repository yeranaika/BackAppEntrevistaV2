package SERVICIOS

import CONFIGURACION.UMBRAL_NIVEL_LOGRADO
import MODELOS.NivelExperiencia

/**
 * Nivel logrado según los puntajes (0-100) obtenidos en preguntas de cada nivel.
 *
 * Se sube de nivel en orden (junior → semisenior → senior) mientras el promedio del nivel llegue a
 * [UMBRAL_NIVEL_LOGRADO]. Un nivel sin preguntas corta la subida: no hay evidencia para saltarlo.
 * Si no logra ni junior, igual queda en junior (es el mínimo que se puede guardar), pero [alcanzaNivelMinimo]
 * dice que no lo alcanzó y [brecha] lo cuenta como un nivel que falta (D42).
 */
object CalculadoraNivel {

    fun nivelLogrado(puntajesPorNivel: Map<NivelExperiencia, List<Double>>): NivelExperiencia {
        var logrado = NivelExperiencia.JUNIOR
        for (nivel in NivelExperiencia.entries) {
            val puntajes = puntajesPorNivel[nivel].orEmpty()
            if (puntajes.isEmpty() || puntajes.average() < UMBRAL_NIVEL_LOGRADO) break
            logrado = nivel
        }
        return logrado
    }

    /**
     * Si hay evidencia de que llega al menos a junior: algún nivel con preguntas cuyo promedio llega al umbral
     * (aprobar uno más difícil también cuenta). Sin preguntas, o fallando todas las que tuvo, no la hay (D42).
     */
    fun alcanzaNivelMinimo(puntajesPorNivel: Map<NivelExperiencia, List<Double>>): Boolean =
        puntajesPorNivel.values.any { it.isNotEmpty() && it.average() >= UMBRAL_NIVEL_LOGRADO }

    /**
     * Cuántos niveles faltan para llegar al requerido (0 si ya lo cumple). Si no alcanza ni el mínimo, falta uno
     * más: así quien no demuestra junior nunca "cumple" un cargo junior (D42).
     */
    fun brecha(actual: NivelExperiencia, requerido: NivelExperiencia, alcanzaMinimo: Boolean = true): Int =
        (requerido.ordinal - actual.ordinal).coerceAtLeast(0) + if (alcanzaMinimo) 0 else 1
}
