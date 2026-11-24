package SERVICIOS

/**
 * Formato de la respuesta ideal que la app ordena en la tarjeta "Respuesta ideal" (D40):
 *  - blandas: método STAR con marcas "S: … T: … A: … R: …" (letra mayúscula, dos puntos y un espacio, en ese orden);
 *  - pasos: "1) … 2) … 3) …", dentro de la acción o, en las técnicas, después de una frase de resumen.
 * Las marcas son las mismas que reconoce la app (RESPUESTA_IDEAL.kt): una letra o un número pegado a otra palabra no
 * cuenta como marca.
 */
object FormatoRespuestaIdeal {
    private const val ORDEN_STAR = "STAR"
    private val MARCA_STAR = Regex("(?<![\\p{L}\\p{N}])([STAR]):\\s")
    private val NUMERO_PASO = Regex("(?<![\\p{L}\\p{N}])\\d{1,2}\\)\\s")
    private val ESPACIOS = Regex("\\s+")

    /**
     * Encabezado al inicio que la tarjeta de la app ya pone como título: "Respuesta ideal:", "Respuesta STAR ideal:",
     * "Respuesta ideal (STAR):", "**Propuesta ideal:**". Exige la palabra "ideal" para no borrar "Propuesta de valor: …".
     */
    private val ENCABEZADO = Regex(
        "^[*_#\\s]*(respuesta|propuesta)\\b[^:\\n]{0,40}?\\bideal\\b[^:\\n]{0,30}:[*_\\s]*",
        RegexOption.IGNORE_CASE
    )

    /** Viñeta de Markdown al inicio de línea delante de una marca: "- S:", "* Situación:", "• **T:**". */
    private val VINETA_DE_MARCA = Regex(
        "(?m)^[ \\t]*[-*•][ \\t]+(?=[*_]{0,2}(?:[STAR]|situaci[oó]n|tarea|acci[oó]n|resultado)[*_]{0,2}\\s*:)",
        RegexOption.IGNORE_CASE
    )

    /** Marca corta con énfasis de Markdown: "**S:**", "**S**:", "_T:_" → "S: ". */
    private val MARCA_CON_ENFASIS = Regex("(?<![\\p{L}\\p{N}])[*_]{0,2}([STAR])[*_]{0,2}:[*_]{0,2}\\s*")

    /**
     * "Situación:", "Tarea:", "Acción:" y "Resultado:" con la palabra completa, con o sin énfasis, y con la marca corta
     * delante si la IA puso las dos ("S: Situación:"): todo se reemplaza por una sola marca.
     */
    private val ETIQUETA_LARGA = Regex(
        "(?<![\\p{L}\\p{N}])[*_]{0,2}(?:[STAR]:\\s*[*_]{0,2})?(situaci[oó]n|tarea|acci[oó]n|resultado)[*_]{0,2}\\s*:[*_]{0,2}\\s*",
        RegexOption.IGNORE_CASE
    )

    /**
     * Deja la respuesta ideal en el formato de la app sin cambiar su contenido: quita el encabezado y, si es una respuesta
     * STAR (con las palabras completas, con Markdown o con las marcas cortas), la deja con "S:", "T:", "A:" y "R:" limpias.
     * Si después de limpiar no queda un STAR completo, devuelve el texto solo sin el encabezado.
     */
    fun normalizar(texto: String): String {
        val sinEncabezado = texto.trim().replaceFirst(ENCABEZADO, "").trim()
        var candidato = VINETA_DE_MARCA.replace(sinEncabezado, "")
        val letrasLargas = ETIQUETA_LARGA.findAll(candidato).map { inicial(it.groupValues[1]) }.joinToString("")
        if (letrasLargas == ORDEN_STAR) {
            candidato = ETIQUETA_LARGA.replace(candidato) { "${inicial(it.groupValues[1])}: " }
        }
        candidato = MARCA_CON_ENFASIS.replace(candidato) { "${it.groupValues[1]}: " }.trim()
        return if (tieneStar(candidato)) candidato else sinEncabezado
    }

    /**
     * Las cuatro marcas S, T, A y R en ese orden y una sola vez cada una (como pide la guía del equipo): una "R:" de más
     * dentro del texto, como "el lenguaje R: …", partiría mal la tarjeta.
     */
    fun tieneStar(texto: String): Boolean =
        marcasEnOrden(texto).size == ORDEN_STAR.length && MARCA_STAR.findAll(texto).count() == ORDEN_STAR.length

    /** Al menos dos pasos numerados "1) … 2) …". */
    fun tienePasos(texto: String): Boolean = NUMERO_PASO.findAll(texto).count() >= 2

    /**
     * El texto sin las marcas de formato, para compararlo: así "S:" o "1)" no cuentan como palabras ni alargan el texto.
     * Igual que la app: las marcas STAR solo si hay al menos S, T y A en orden, y los números de paso bloque por bloque,
     * solo en los bloques con dos o más pasos ("la versión 2) del SDK" suelta no se toca).
     */
    fun sinMarcas(texto: String): String {
        val marcas = marcasEnOrden(texto)
        val bloques = if (marcas.size >= 3) {
            buildList {
                add(texto.substring(0, marcas.first().range.first))
                marcas.forEachIndexed { i, marca ->
                    add(texto.substring(marca.range.last + 1, marcas.getOrNull(i + 1)?.range?.first ?: texto.length))
                }
            }
        } else {
            listOf(texto)
        }
        return bloques.joinToString(" ") { if (tienePasos(it)) NUMERO_PASO.replace(it, " ") else it }
            .replace(ESPACIOS, " ").trim()
    }

    /** Igual que la app: recorre las marcas y se queda con las que siguen el orden S, T, A, R. */
    private fun marcasEnOrden(texto: String): List<MatchResult> {
        val marcas = mutableListOf<MatchResult>()
        for (m in MARCA_STAR.findAll(texto)) {
            val esperada = ORDEN_STAR.getOrNull(marcas.size) ?: break
            if (m.groupValues[1][0] == esperada) marcas += m
        }
        return marcas
    }

    private fun inicial(etiqueta: String): Char = when (etiqueta.lowercase().first()) {
        's' -> 'S'
        't' -> 'T'
        'a' -> 'A'
        else -> 'R'
    }
}
