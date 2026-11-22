package SERVICIOS

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

private const val PESO_COSENO = 0.40
private const val PESO_COBERTURA = 0.30
private const val PESO_JACCARD = 0.20
private const val PESO_ORDEN = 0.10
private const val LARGO_MAXIMO_ORDEN = 1500

private val PALABRAS_VACIAS = setOf(
    "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "y", "o", "u", "en", "a", "con", "por",
    "para", "que", "se", "es", "son", "su", "sus", "lo", "como", "mas", "pero"
)

/**
 * Cuánto se parece una respuesta a una o varias respuestas de referencia, sin IA y sin tokens (feedback freemium).
 * Cada comparación mezcla cuatro medidas (0 a 1):
 * - coseno TF-IDF (40 %): las palabras raras ("HDFS") pesan más que las comunes;
 * - cobertura (30 %): qué parte de las palabras de la referencia aparece en la respuesta;
 * - Jaccard (20 %): palabras en común sobre el total de palabras distintas;
 * - orden (10 %): parecido como cadena (Levenshtein), tolera errores de tipeo y cambios de orden.
 * Se queda con la mejor referencia: una pregunta puede tener varias formas correctas de responderse.
 * Los grupos de sinónimos hacen que "deadlock", "interbloqueo" y "bloqueo mutuo" cuenten como la misma palabra.
 */
object ComparadorTextos {

    /** Minúsculas, sin tildes, sin símbolos; "C#", "C++", "F#" y ".NET" se escriben con letras para no perder su significado. */
    fun normalizar(texto: String): String = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("c#", " csharp ").replace("f#", " fsharp ").replace("c++", " cpp ").replace(".net", " dotnet ")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Mejor parecido (0 a 1) de [respuesta] con alguna de las [referencias]. 0 si no hay con qué comparar. */
    fun similitud(respuesta: String, referencias: List<String>, sinonimos: List<List<String>> = emptyList()): Double {
        val frases = equivalencias(sinonimos)
        val respuestaTokens = tokens(respuesta, frases)
        val referenciasTokens = referencias.map { tokens(it, frases) }.filter { it.isNotEmpty() }
        if (respuestaTokens.isEmpty() || referenciasTokens.isEmpty()) return 0.0
        val documentos = referenciasTokens + listOf(respuestaTokens)
        val idf = documentos.flatten().distinct().associateWith { palabra ->
            ln((1.0 + documentos.size) / (1.0 + documentos.count { palabra in it })) + 1.0
        }
        return referenciasTokens.maxOf { puntuar(respuestaTokens, it, idf) }
    }

    /** Palabras de [texto] sin vacías y con el plural quitado, con los sinónimos reemplazados por su forma principal. */
    internal fun tokens(texto: String, equivalencias: List<Pair<Regex, String>> = emptyList()): List<String> {
        var limpio = normalizar(texto)
        equivalencias.forEach { (frase, principal) -> limpio = limpio.replace(frase, principal) }
        return limpio.split(" ").filter { it.isNotBlank() && it !in PALABRAS_VACIAS }.map(::sinPlural)
    }

    /** Cada término de un grupo se reemplaza por el primero (sin espacios); las frases largas primero. */
    internal fun equivalencias(grupos: List<List<String>>): List<Pair<Regex, String>> = grupos
        .map { grupo -> grupo.map(::normalizar).filter { it.isNotEmpty() } }
        .filter { it.size >= 2 }
        .flatMap { grupo ->
            val principal = grupo.first().replace(" ", "")
            grupo.map { termino -> termino to principal }
        }
        .sortedByDescending { it.first.length }
        .map { (termino, principal) -> Regex("(?<=^| )" + Regex.escape(termino) + "(?= |$)") to principal }

    private fun sinPlural(palabra: String) = when {
        palabra.length > 4 && palabra.endsWith("es") -> palabra.dropLast(2)
        palabra.length > 3 && palabra.endsWith("s") -> palabra.dropLast(1)
        else -> palabra
    }

    private fun puntuar(respuesta: List<String>, referencia: List<String>, idf: Map<String, Double>): Double {
        val conjuntoRespuesta = respuesta.toSet()
        val conjuntoReferencia = referencia.toSet()
        val comunes = conjuntoRespuesta.intersect(conjuntoReferencia).size.toDouble()
        val cobertura = comunes / conjuntoReferencia.size
        val jaccard = comunes / conjuntoRespuesta.union(conjuntoReferencia).size
        return PESO_COSENO * cosenoTfIdf(respuesta, referencia, idf) +
            PESO_COBERTURA * cobertura +
            PESO_JACCARD * jaccard +
            PESO_ORDEN * parecidoDeOrden(respuesta.joinToString(" "), referencia.joinToString(" "))
    }

    private fun cosenoTfIdf(a: List<String>, b: List<String>, idf: Map<String, Double>): Double {
        val va = a.groupingBy { it }.eachCount().mapValues { (palabra, veces) -> veces * (idf[palabra] ?: 1.0) }
        val vb = b.groupingBy { it }.eachCount().mapValues { (palabra, veces) -> veces * (idf[palabra] ?: 1.0) }
        val producto = va.entries.sumOf { (palabra, peso) -> peso * (vb[palabra] ?: 0.0) }
        val normaA = sqrt(va.values.sumOf { it * it })
        val normaB = sqrt(vb.values.sumOf { it * it })
        return if (normaA == 0.0 || normaB == 0.0) 0.0 else producto / (normaA * normaB)
    }

    /** 1 - distancia de Levenshtein / largo mayor, sobre los primeros [LARGO_MAXIMO_ORDEN] caracteres. */
    private fun parecidoDeOrden(a: String, b: String): Double {
        val x = a.take(LARGO_MAXIMO_ORDEN)
        val y = b.take(LARGO_MAXIMO_ORDEN)
        val mayor = maxOf(x.length, y.length)
        if (mayor == 0) return 0.0
        var anterior = IntArray(y.length + 1) { it }
        var actual = IntArray(y.length + 1)
        for (i in 1..x.length) {
            actual[0] = i
            for (j in 1..y.length) {
                val costo = if (x[i - 1] == y[j - 1]) 0 else 1
                actual[j] = min(min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo)
            }
            val intercambio = anterior; anterior = actual; actual = intercambio
        }
        return 1.0 - anterior[y.length].toDouble() / mayor
    }
}
