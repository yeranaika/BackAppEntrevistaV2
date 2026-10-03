package SERVICIOS

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Evaluación de una respuesta abierta. Puntajes de 0 a 100. */
data class EvaluacionTexto(
    val puntaje: Double,
    val coberturaPalabrasClave: Double,
    val similitud: Double,
    val palabrasEncontradas: List<String>,
    val palabrasFaltantes: List<String>,
    val feedback: String
)

/** Corrige respuestas abiertas. Hoy: motor determinista sin costo; en premium se puede cambiar por un LLM. */
fun interface EvaluadorRespuesta {
    fun evaluar(texto: String, respuestaIdeal: String, palabrasClave: List<String>): EvaluacionTexto
}

private const val PESO_SIMILITUD = 0.40
private const val PESO_PALABRAS_CLAVE = 0.40
private const val PESO_LARGO = 0.20
private const val UMBRAL_EXCELENTE = 80.0
private const val UMBRAL_BUENA = 60.0
private const val TAMANO_NGRAMA = 3
private const val PALABRAS_CLAVE_EN_FEEDBACK = 4

/**
 * Motor freemium (sin consumo de IA): similitud por trigramas con la respuesta ideal (40 %),
 * cobertura de palabras clave (40 %) y largo de la respuesta (20 %).
 * Sin palabras clave, el peso de esas se reparte entre similitud y largo (antes contaba como 100 % y
 * cualquier respuesta corta sacaba al menos 40 puntos).
 */
class EvaluadorRespuestaFreemium : EvaluadorRespuesta {

    override fun evaluar(texto: String, respuestaIdeal: String, palabrasClave: List<String>): EvaluacionTexto {
        if (texto.isBlank()) {
            return EvaluacionTexto(0.0, 0.0, 0.0, emptyList(), palabrasClave, "No se ingresó ninguna respuesta.")
        }
        val usuario = limpiar(texto)
        val encontradas = palabrasClave.filter { usuario.contains(limpiar(it)) }
        val faltantes = palabrasClave - encontradas.toSet()
        val cobertura = if (palabrasClave.isEmpty()) 0.0 else encontradas.size * 100.0 / palabrasClave.size
        val similitud = similitudCoseno(usuario, limpiar(respuestaIdeal)) * 100.0
        val largo = (contarPalabras(texto).toDouble() / contarPalabras(respuestaIdeal).coerceAtLeast(1)).coerceIn(0.0, 1.0) * 100.0

        val puntaje = if (palabrasClave.isEmpty()) {
            similitud * (PESO_SIMILITUD + PESO_PALABRAS_CLAVE) + largo * PESO_LARGO
        } else {
            similitud * PESO_SIMILITUD + cobertura * PESO_PALABRAS_CLAVE + largo * PESO_LARGO
        }
        return EvaluacionTexto(
            puntaje = redondear(puntaje),
            coberturaPalabrasClave = redondear(cobertura),
            similitud = redondear(similitud),
            palabrasEncontradas = encontradas,
            palabrasFaltantes = faltantes,
            feedback = feedback(redondear(puntaje), faltantes)
        )
    }

    private fun feedback(puntaje: Double, faltantes: List<String>): String {
        val conceptos = faltantes.take(PALABRAS_CLAVE_EN_FEEDBACK).joinToString(", ")
        return when {
            puntaje >= UMBRAL_EXCELENTE -> "Excelente respuesta. Cubres los conceptos fundamentales con claridad y precisión."
            puntaje >= UMBRAL_BUENA && faltantes.isNotEmpty() -> "Buena aproximación. Para completarla mejor, profundiza en: $conceptos."
            puntaje >= UMBRAL_BUENA -> "Buena respuesta, aunque podrías detallar más los fundamentos."
            faltantes.isNotEmpty() -> "Respuesta incompleta. Te recomendamos repasar estos conceptos clave: $conceptos."
            else -> "Respuesta muy breve o poco relacionada con lo que se pregunta."
        }
    }

    private fun limpiar(texto: String) = texto.lowercase()
        .replace(Regex("[^a-záéíóúüñ0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun contarPalabras(texto: String) = texto.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun redondear(valor: Double) = (valor * 10).roundToInt() / 10.0

    private fun similitudCoseno(a: String, b: String): Double {
        if (a.length < TAMANO_NGRAMA || b.length < TAMANO_NGRAMA) return similitudPorPalabras(a, b)
        val ngramasA = a.windowed(TAMANO_NGRAMA).groupingBy { it }.eachCount()
        val ngramasB = b.windowed(TAMANO_NGRAMA).groupingBy { it }.eachCount()
        val producto = ngramasA.entries.sumOf { (ngrama, cantidad) -> cantidad.toDouble() * (ngramasB[ngrama] ?: 0) }
        val normaA = sqrt(ngramasA.values.sumOf { it.toDouble() * it })
        val normaB = sqrt(ngramasB.values.sumOf { it.toDouble() * it })
        return if (normaA == 0.0 || normaB == 0.0) 0.0 else producto / (normaA * normaB)
    }

    private fun similitudPorPalabras(a: String, b: String): Double {
        val palabrasA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val palabrasB = b.split(" ").filter { it.isNotBlank() }.toSet()
        val union = palabrasA.union(palabrasB).size
        return if (union == 0) 0.0 else palabrasA.intersect(palabrasB).size.toDouble() / union
    }
}

/** Verbos de los nombres de criterio que no son conceptos (ej: "menciona_inmutabilidad" → "inmutabilidad"). */
private val PALABRAS_DE_CRITERIO = setOf(
    "menciona", "explica", "define", "describe", "distingue", "ejemplo", "uso", "usa", "vs", "con", "sin", "del", "los", "las", "para"
)
private const val LARGO_MINIMO_PALABRA_CLAVE = 4

/**
 * Palabras clave de la rúbrica de una pregunta: "palabras_clave" si existe; si no, los conceptos que
 * nombran sus "criterios".
 */
fun palabrasClaveDe(rubrica: JsonObject?): List<String> {
    if (rubrica == null) return emptyList()
    val explicitas = textosDe(rubrica["palabras_clave"])
    if (explicitas.isNotEmpty()) return explicitas
    return textosDe(rubrica["criterios"])
        .flatMap { it.split('_', ' ') }
        .map { it.lowercase() }
        .filter { it.length >= LARGO_MINIMO_PALABRA_CLAVE && it !in PALABRAS_DE_CRITERIO }
        .distinct()
}

private fun textosDe(elemento: Any?): List<String> =
    (elemento as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
