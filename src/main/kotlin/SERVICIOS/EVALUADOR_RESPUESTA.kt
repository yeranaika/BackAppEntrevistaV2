package SERVICIOS

import MODELOS.FeedbackGenerico
import MODELOS.NivelFeedbackGenerico
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
interface EvaluadorRespuesta {
    /**
     * [alternativas]: otras formas correctas de responder (se compara con la mejor). [sinonimos]: grupos de términos
     * equivalentes ("deadlock", "interbloqueo"), que cuentan como la misma palabra. [feedbackGenerico]: los textos propios
     * de la pregunta para respuesta correcta, a medias y mala (D36); si falta el del nivel se usa el texto estándar.
     */
    fun evaluar(
        texto: String,
        respuestaIdeal: String,
        palabrasClave: List<String>,
        alternativas: List<String> = emptyList(),
        sinonimos: List<List<String>> = emptyList(),
        feedbackGenerico: FeedbackGenerico = FeedbackGenerico()
    ): EvaluacionTexto
}

private const val PESO_SIMILITUD = 0.40
private const val PESO_PALABRAS_CLAVE = 0.40
private const val PESO_LARGO = 0.20
private const val UMBRAL_EXCELENTE = 80.0
private const val UMBRAL_BUENA = 60.0
private const val TAMANO_NGRAMA = 3
private const val PALABRAS_CLAVE_EN_FEEDBACK = 4
private const val LARGO_MAXIMO_CLAVE_EXACTA = 3

/**
 * Motor freemium (sin consumo de IA): similitud por trigramas con la respuesta ideal (40 %),
 * cobertura de palabras clave (40 %) y largo de la respuesta (20 %).
 * Sin palabras clave, el peso de esas se reparte entre similitud y largo (antes contaba como 100 % y
 * cualquier respuesta corta sacaba al menos 40 puntos).
 */
class EvaluadorRespuestaFreemium : EvaluadorRespuesta {

    override fun evaluar(
        texto: String,
        respuestaIdeal: String,
        palabrasClave: List<String>,
        alternativas: List<String>,
        sinonimos: List<List<String>>,
        feedbackGenerico: FeedbackGenerico
    ): EvaluacionTexto {
        if (texto.isBlank()) {
            return EvaluacionTexto(0.0, 0.0, 0.0, emptyList(), palabrasClave, "No se ingresó ninguna respuesta.")
        }
        val usuario = ComparadorTextos.normalizar(texto)
        val encontradas = palabrasClave.filter { clave -> formasDe(clave, sinonimos).any { aparece(usuario, it) } }
        val faltantes = palabrasClave - encontradas.toSet()
        val cobertura = if (palabrasClave.isEmpty()) 0.0 else encontradas.size * 100.0 / palabrasClave.size
        // Se queda con la mejor medida: los trigramas (parecido de letras con la ideal) o el comparador (varias referencias y sinónimos).
        val similitud = maxOf(
            similitudCoseno(usuario, ComparadorTextos.normalizar(respuestaIdeal)),
            ComparadorTextos.similitud(texto, listOf(respuestaIdeal) + alternativas, sinonimos)
        ) * 100.0
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
            feedback = feedback(redondear(puntaje), faltantes, feedbackGenerico)
        )
    }

    /** El texto propio de la pregunta para su nivel (D36) y, si no es correcta, los conceptos que faltaron. */
    private fun feedback(puntaje: Double, faltantes: List<String>, generico: FeedbackGenerico): String {
        val conceptos = faltantes.take(PALABRAS_CLAVE_EN_FEEDBACK).joinToString(", ")
        val nivel = nivelFeedbackGenerico(puntaje)
        val propio = generico.texto(nivel) ?: return feedbackEstandar(puntaje, faltantes, conceptos)
        return if (nivel == NivelFeedbackGenerico.CORRECTO || faltantes.isEmpty()) propio else "$propio Repasa: $conceptos."
    }

    private fun feedbackEstandar(puntaje: Double, faltantes: List<String>, conceptos: String): String {
        return when {
            puntaje >= UMBRAL_EXCELENTE -> "Excelente respuesta. Cubres los conceptos fundamentales con claridad y precisión."
            puntaje >= UMBRAL_BUENA && faltantes.isNotEmpty() -> "Buena aproximación. Para completarla mejor, profundiza en: $conceptos."
            puntaje >= UMBRAL_BUENA -> "Buena respuesta, aunque podrías detallar más los fundamentos."
            faltantes.isNotEmpty() -> "Respuesta incompleta. Te recomendamos repasar estos conceptos clave: $conceptos."
            else -> "Respuesta muy breve o poco relacionada con lo que se pregunta."
        }
    }

    /**
     * ¿La palabra clave (ya limpia) aparece en el texto (ya limpio)? Debe empezar en una palabra: "go" no está en "algo".
     * Las claves de [LARGO_MAXIMO_CLAVE_EXACTA] letras o menos (go, sql, r) deben coincidir con la palabra completa; las
     * más largas admiten plurales y conjugaciones ("proceso" aparece en "procesos").
     */
    private fun aparece(texto: String, forma: String): Boolean {
        val clave = ComparadorTextos.normalizar(forma)
        if (clave.isEmpty()) return false
        val final = if (clave.length <= LARGO_MAXIMO_CLAVE_EXACTA) "(?= |$)" else ""
        return Regex("(?<=^| )" + Regex.escape(clave) + final).containsMatchIn(texto)
    }

    /** La palabra clave y sus sinónimos: basta con que aparezca cualquiera. */
    private fun formasDe(clave: String, sinonimos: List<List<String>>): List<String> {
        val buscada = ComparadorTextos.normalizar(clave)
        val grupo = sinonimos.firstOrNull { g -> g.any { ComparadorTextos.normalizar(it) == buscada } }.orEmpty()
        return listOf(clave) + grupo
    }

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

/**
 * Nivel del feedback genérico (D36) según el puntaje de 0 a 100: correcto desde [UMBRAL_EXCELENTE], a medias desde
 * [UMBRAL_BUENA] y malo por debajo; los mismos cortes que el texto estándar.
 */
fun nivelFeedbackGenerico(puntaje: Double): NivelFeedbackGenerico = when {
    puntaje >= UMBRAL_EXCELENTE -> NivelFeedbackGenerico.CORRECTO
    puntaje >= UMBRAL_BUENA -> NivelFeedbackGenerico.MEDIO
    else -> NivelFeedbackGenerico.MALO
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

/** Otras formas correctas de responder la pregunta ("respuestas_alternativas" de la rúbrica). */
fun alternativasDe(rubrica: JsonObject?): List<String> = textosDe(rubrica?.get("respuestas_alternativas"))

/** Grupos de términos equivalentes ("sinonimos" de la rúbrica): [["deadlock", "interbloqueo"], ...]. Un grupo necesita 2 o más. */
fun sinonimosDe(rubrica: JsonObject?): List<List<String>> =
    (rubrica?.get("sinonimos") as? JsonArray).orEmpty().map { textosDe(it) }.filter { it.size >= 2 }

private fun textosDe(elemento: Any?): List<String> =
    (elemento as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
