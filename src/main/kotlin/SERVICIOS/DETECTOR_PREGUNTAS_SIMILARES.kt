package SERVICIOS

import MODELOS.GrupoPreguntasSimilares
import MODELOS.PreguntaExistente
import MODELOS.PreguntaSimilar
import kotlin.math.max

/**
 * Parecido entre enunciados de preguntas, sin IA y sin tokens: reutiliza [ComparadorTextos].
 * `similitud` mira la cobertura de la referencia, así que se toma el mayor de ambos sentidos.
 * shortcut: solo mide palabras compartidas; una paráfrasis con otro vocabulario no se detecta (haría falta embeddings).
 */
object DetectorPreguntasSimilares {
    /** Parecido (0 a 1) entre dos enunciados. */
    fun similitud(a: String, b: String): Double =
        max(ComparadorTextos.similitud(a, listOf(b)), ComparadorTextos.similitud(b, listOf(a)))

    /** Las [maximo] preguntas de [existentes] con parecido >= [umbral] a [texto], de mayor a menor. */
    fun masParecidas(texto: String, existentes: List<PreguntaExistente>, umbral: Double, maximo: Int = 5): List<PreguntaSimilar> =
        existentes.map { PreguntaSimilar(it.id, it.enunciado, it.estado, similitud(texto, it.enunciado)) }
            .filter { it.similitud >= umbral }
            .sortedByDescending { it.similitud }
            .take(maximo)

    /**
     * Para cada enunciado de [nuevos] devuelve con qué se parece, o null si se puede guardar. Cada uno también se compara
     * con los nuevos que sí se guardaron antes que él. [umbral] <= 0 apaga la detección.
     */
    fun separar(nuevos: List<String>, existentes: List<PreguntaExistente>, umbral: Double): List<PreguntaSimilar?> {
        if (umbral <= 0.0) return nuevos.map { null }
        val aceptados = mutableListOf<String>()
        return nuevos.map { texto ->
            val delBanco = masParecidas(texto, existentes, umbral, maximo = 1).firstOrNull()
            val delLote = aceptados.map { PreguntaSimilar(null, it, null, similitud(texto, it)) }
                .filter { it.similitud >= umbral }.maxByOrNull { it.similitud }
            val coincidencia = listOfNotNull(delBanco, delLote).maxByOrNull { it.similitud }
            if (coincidencia == null) aceptados += texto
            coincidencia
        }
    }

    /** Agrupa las preguntas que se parecen (directa o encadenadamente) con parecido >= [umbral]; solo grupos de 2 o más. */
    fun grupos(preguntas: List<PreguntaExistente>, umbral: Double): List<GrupoPreguntasSimilares> {
        val padre = IntArray(preguntas.size) { it }
        fun raiz(i: Int): Int { var r = i; while (padre[r] != r) r = padre[r]; return r }
        val mayorPorPar = mutableMapOf<Pair<Int, Int>, Double>()
        for (i in preguntas.indices) for (j in i + 1 until preguntas.size) {
            val s = similitud(preguntas[i].enunciado, preguntas[j].enunciado)
            if (s >= umbral) { padre[raiz(i)] = raiz(j); mayorPorPar[i to j] = s }
        }
        return preguntas.indices.groupBy(::raiz).values.filter { it.size > 1 }.map { indices ->
            val maxima = mayorPorPar.filterKeys { it.first in indices }.values.maxOrNull() ?: umbral
            GrupoPreguntasSimilares(indices.map { preguntas[it] }, maxima)
        }.sortedByDescending { it.similitudMaxima }
    }
}
