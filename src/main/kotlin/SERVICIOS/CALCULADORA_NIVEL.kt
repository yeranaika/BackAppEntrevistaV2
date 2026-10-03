package SERVICIOS

import CONFIGURACION.UMBRAL_NIVEL_LOGRADO
import MODELOS.NivelExperiencia

/**
 * Nivel logrado según los puntajes (0-100) obtenidos en preguntas de cada nivel.
 *
 * Se sube de nivel en orden (junior → semisenior → senior) mientras el promedio del nivel llegue a
 * [UMBRAL_NIVEL_LOGRADO]. Un nivel sin preguntas corta la subida: no hay evidencia para saltarlo.
 * Si no logra ni junior, igual queda en junior (es el mínimo).
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

    /** Cuántos niveles faltan para llegar al requerido (0 si ya lo cumple). */
    fun brecha(actual: NivelExperiencia, requerido: NivelExperiencia): Int = (requerido.ordinal - actual.ordinal).coerceAtLeast(0)
}
