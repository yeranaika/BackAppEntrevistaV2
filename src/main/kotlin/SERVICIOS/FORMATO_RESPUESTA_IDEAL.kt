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

    /** "Respuesta ideal:", "Respuesta STAR ideal:" o "Propuesta ideal:" al inicio: la tarjeta de la app ya pone el título. */
    private val ENCABEZADO = Regex("^(respuesta|propuesta)(\\s+star)?\\s+ideal\\s*:\\s*", RegexOption.IGNORE_CASE)

    /** "Situación:", "Tarea:", "Acción:" y "Resultado:" escritos con la palabra completa. */
    private val ETIQUETA_LARGA = Regex(
        "(?<![\\p{L}\\p{N}])(situaci[oó]n|tarea|acci[oó]n|resultado)\\s*:\\s*",
        RegexOption.IGNORE_CASE
    )

    /**
     * Deja la respuesta ideal en el formato de la app sin cambiar su contenido: quita el encabezado y, si trae las cuatro
     * partes STAR escritas con la palabra completa y en orden ("Situación: … Tarea: …"), las cambia por "S:", "T:", "A:"
     * y "R:".
     */
    fun normalizar(texto: String): String {
        val sinEncabezado = texto.trim().replaceFirst(ENCABEZADO, "")
        val etiquetas = ETIQUETA_LARGA.findAll(sinEncabezado).toList()
        val letras = etiquetas.map { inicial(it.groupValues[1]) }.joinToString("")
        if (letras != ORDEN_STAR) return sinEncabezado
        return ETIQUETA_LARGA.replace(sinEncabezado) { "${inicial(it.groupValues[1])}: " }
    }

    /** Las cuatro marcas S, T, A y R, en ese orden (como pide la guía del equipo). */
    fun tieneStar(texto: String): Boolean = marcasEnOrden(texto).size == ORDEN_STAR.length

    /** Al menos dos pasos numerados "1) … 2) …". */
    fun tienePasos(texto: String): Boolean = NUMERO_PASO.findAll(texto).count() >= 2

    /**
     * El texto sin las marcas de formato, para compararlo con lo que escribió la persona: así "S:" o "1)" no cuentan como
     * palabras ni alargan la respuesta ideal. Quita las marcas STAR solo si la app las tomaría como tales (al menos S, T y A
     * en orden) y los números de paso solo si hay dos o más.
     */
    fun sinMarcas(texto: String): String {
        val marcas = marcasEnOrden(texto)
        var limpio = if (marcas.size >= 3) {
            val rangos = marcas.map { it.range }
            buildString {
                var desde = 0
                rangos.forEach { r -> append(texto, desde, r.first); append(' '); desde = r.last + 1 }
                append(texto, desde, texto.length)
            }
        } else {
            texto
        }
        if (tienePasos(limpio)) limpio = NUMERO_PASO.replace(limpio, " ")
        return limpio.replace(Regex("\\s+"), " ").trim()
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
