package SERVICIOS

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.Normalizer

/*
 * Revisor de texto libre SIN IA ni API externa (T3.13).
 *
 * Es una función pura de (texto, respuesta ideal, rúbrica) → puntaje 0-100 + banda. La MISMA fórmula vive en el backend
 * (este archivo) y en la app Android (core/revision/REVISOR_TEXTO_LIBRE.kt): ambos archivos son idénticos salvo el
 * `package`. La paridad la comprueba el archivo de casos dorados `revisor_casos_dorados.json`, copiado byte a byte en
 * los dos repositorios: el mismo caso debe dar el mismo puntaje en ambos lados. Si cambias algo aquí, cámbialo allá y
 * regenera los casos dorados.
 *
 * Pasos:
 *  1. Normalización: minúsculas → sin tildes (NFD + quitar marcas) → solo [a-z0-9] → sin stopwords ES → plural simple.
 *  2. Tres componentes (cada uno 0..1; si la rúbrica no trae lo necesario, ese componente no existe):
 *     A. cobertura de `palabras_clave`  B. cobertura de `criterios` (STAR u otros)  C. similitud con la respuesta ideal.
 *     Puntaje = Σ peso·componente / Σ pesos de los componentes disponibles (así una rúbrica sin palabras clave, como las
 *     de las semillas, no se castiga: su peso se reparte entre B y C).
 *  3. Bandas: ≥ aprobada → APROBADA; ≥ cercana → CERCANA; menos → REVISAR. Nunca "reprobada": la banda intermedia y la
 *     baja las calibra una persona.
 *  4. Informativo (no cambia el puntaje): longitud de la respuesta frente al tiempo esperado.
 */

enum class BandaRevision { APROBADA, CERCANA, REVISAR }

/** Cómo se mide el componente C. Se eligió por correlación en PruebaRevisorTextoLibre (ver su comentario). */
enum class MetodoSimilitud { JACCARD, LEVENSHTEIN }

/** Pesos de los componentes A, B y C (aprobados: 0,45 / 0,20 / 0,35). */
data class PesosRevisor(
    val palabrasClave: Double = 0.45,
    val criterios: Double = 0.20,
    val similitud: Double = 0.35
)

/** Umbrales sobre el puntaje 0-100. PROVISIONALES hasta calibrar con evaluaciones reales (ver docs). */
data class UmbralesRevisor(
    val aprobada: Double = 70.0,
    val cercana: Double = 40.0
)

data class ConfiguracionRevisor(
    val pesos: PesosRevisor = PesosRevisor(),
    val umbrales: UmbralesRevisor = UmbralesRevisor(),
    val metodoSimilitud: MetodoSimilitud = MetodoSimilitud.LEVENSHTEIN
)

/** Un criterio de la rúbrica. Con [terminos] explícitos se usan tal cual; si no, se derivan del nombre (snake_case). */
data class CriterioRubrica(val nombre: String, val terminos: List<String>? = null)

data class RubricaRevision(
    val palabrasClave: List<String> = emptyList(),
    val criterios: List<CriterioRubrica> = emptyList(),
    val tiempoEsperadoSeg: Int? = null
) {
    companion object {
        /**
         * Lee la rúbrica del JSON de `pregunta.rubrica_evaluacion`:
         * `{"palabras_clave":[..], "criterios":["texto" | {"nombre":..,"terminos":[..]}], "tiempo_esperado_seg":90}`.
         * También acepta `duracion_esperada_seg` (así se llama en las semillas de simulación). Un JSON raro no falla:
         * lo que no se entiende se ignora.
         */
        fun desdeJson(json: JsonObject?): RubricaRevision {
            if (json == null) return RubricaRevision()
            val palabras = (json["palabras_clave"] as? JsonArray).textos()
            val criterios = (json["criterios"] as? JsonArray).orEmptyList().mapNotNull { elemento ->
                when (elemento) {
                    is JsonPrimitive -> elemento.contentOrNull?.takeIf { it.isNotBlank() }?.let { CriterioRubrica(it) }
                    is JsonObject -> {
                        val nombre = (elemento["nombre"] as? JsonPrimitive)?.contentOrNull
                            ?: (elemento["id"] as? JsonPrimitive)?.contentOrNull
                        val terminos = (elemento["terminos"] as? JsonArray).textos().takeIf { it.isNotEmpty() }
                        if (nombre.isNullOrBlank() && terminos == null) null else CriterioRubrica(nombre.orEmpty(), terminos)
                    }
                    else -> null
                }
            }
            val segundos = (json["tiempo_esperado_seg"] as? JsonPrimitive)?.doubleOrNull
                ?: (json["duracion_esperada_seg"] as? JsonPrimitive)?.doubleOrNull
            return RubricaRevision(palabras, criterios, segundos?.toInt()?.takeIf { it > 0 })
        }

        private fun JsonArray?.orEmptyList(): List<JsonElement> = this?.toList() ?: emptyList()

        private fun JsonArray?.textos(): List<String> =
            orEmptyList().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
    }
}

data class ResultadoRevision(
    /** 0-100 con 2 decimales. 0 si no hay con qué comparar ([evaluable] = false). */
    val score: Double,
    val banda: BandaRevision,
    /** false si la pregunta no trae palabras clave, criterios ni respuesta ideal: no hay base para puntuar. */
    val evaluable: Boolean,
    /** 0..1 (4 decimales), null si ese componente no existe para esta pregunta. */
    val coberturaPalabrasClave: Double?,
    val coberturaCriterios: Double?,
    val similitud: Double?,
    val palabrasFaltantes: List<String>,
    /** Criterios con menos de la mitad de sus términos presentes. */
    val criteriosFaltantes: List<String>,
    val palabras: Int,
    /** Palabras escritas / palabras que caben en el tiempo esperado. Solo informativo. */
    val relacionLongitud: Double?
)

object RevisorTextoLibre {
    /** ≈ 130 palabras por minuto hablando; el tiempo esperado de la rúbrica se traduce a palabras con esta tasa. */
    const val PALABRAS_POR_SEGUNDO = 130.0 / 60.0

    /** Tope de caracteres que mira la distancia de Levenshtein (cuadrática): protege al servidor y al teléfono. */
    private const val MAXIMO_CARACTERES_LEVENSHTEIN = 3000

    private val STOPWORDS = setOf(
        "a", "al", "algo", "ante", "aqui", "asi", "aun", "como", "con", "contra", "cual", "cuando", "cada", "de", "del",
        "desde", "donde", "e", "el", "ella", "ellos", "en", "entre", "era", "es", "esa", "ese", "eso", "esta", "estas",
        "este", "esto", "estos", "fue", "fui", "ha", "han", "hasta", "hay", "la", "las", "le", "les", "lo", "los", "mas",
        "me", "mi", "mis", "muy", "o", "para", "pero", "por", "porque", "que", "se", "ser", "si", "sobre", "son", "soy",
        "su", "sus", "tambien", "te", "tu", "un", "una", "unas", "uno", "unos", "y", "ya", "yo"
    )

    /** Verbos y adjetivos de relleno de los nombres de criterio ("menciona_TTL" → solo "ttl"). Ya normalizados. */
    private val RELLENO_DE_CRITERIO = setOf(
        "menciona", "mencionar", "mencionado", "describe", "describir", "da", "incluye", "propone", "considera",
        "demuestra", "demostrada", "correctamente", "clara", "claro", "adecuada", "especifico", "especifica"
    )

    fun revisar(
        texto: String,
        respuestaIdeal: String?,
        rubrica: RubricaRevision,
        configuracion: ConfiguracionRevisor = ConfiguracionRevisor()
    ): ResultadoRevision {
        val tokens = tokenizar(texto)
        val busqueda = conjuntoDeBusqueda(tokens)
        val palabras = contarPalabras(texto)

        // A. palabras clave
        val clavesBuscadas = rubrica.palabrasClave.map { it to tokenizar(it) }.filter { it.second.isNotEmpty() }
        val presentesA = clavesBuscadas.filter { (_, t) -> estaPresente(t, busqueda) }
        val a = if (clavesBuscadas.isEmpty()) null else presentesA.size.toDouble() / clavesBuscadas.size

        // B. criterios
        val puntajesCriterios = rubrica.criterios.mapNotNull { criterio ->
            val grupos = gruposDe(criterio)
            if (grupos.isEmpty()) null else criterio.nombre.ifBlank { criterio.terminos.orEmpty().joinToString(" ") } to
                grupos.maxOf { grupo -> grupo.count { it in busqueda }.toDouble() / grupo.size }
        }
        val b = if (puntajesCriterios.isEmpty()) null else puntajesCriterios.sumOf { it.second } / puntajesCriterios.size

        // C. similitud con la respuesta ideal
        val tokensIdeal = respuestaIdeal?.let { tokenizar(it) }.orEmpty()
        val c = if (tokensIdeal.isEmpty()) null else when (configuracion.metodoSimilitud) {
            MetodoSimilitud.JACCARD -> jaccard(tokens, tokensIdeal)
            MetodoSimilitud.LEVENSHTEIN -> levenshteinNormalizada(tokens, tokensIdeal)
        }

        val pesos = configuracion.pesos
        val componentes = listOf(a to pesos.palabrasClave, b to pesos.criterios, c to pesos.similitud)
            .mapNotNull { (valor, peso) -> valor?.let { it to peso } }
        val pesoTotal = componentes.sumOf { it.second }
        val evaluable = componentes.isNotEmpty() && pesoTotal > 0.0
        val score = if (!evaluable || tokens.isEmpty()) 0.0 else redondear(componentes.sumOf { it.first * it.second } / pesoTotal * 100.0, 2)

        val banda = when {
            !evaluable -> BandaRevision.REVISAR
            score >= configuracion.umbrales.aprobada -> BandaRevision.APROBADA
            score >= configuracion.umbrales.cercana -> BandaRevision.CERCANA
            else -> BandaRevision.REVISAR
        }

        val esperadas = rubrica.tiempoEsperadoSeg?.let { it * PALABRAS_POR_SEGUNDO }
        return ResultadoRevision(
            score = score,
            banda = banda,
            evaluable = evaluable,
            coberturaPalabrasClave = a?.let { redondear(it, 4) },
            coberturaCriterios = b?.let { redondear(it, 4) },
            similitud = c?.let { redondear(it, 4) },
            palabrasFaltantes = clavesBuscadas.filter { it !in presentesA }.map { it.first },
            criteriosFaltantes = puntajesCriterios.filter { it.second < 0.5 }.map { it.first },
            palabras = palabras,
            relacionLongitud = esperadas?.let { redondear(palabras / it, 2) }
        )
    }

    // ---------- Normalización ----------

    /** minúsculas → sin tildes → solo [a-z0-9] → sin stopwords → plural simple. Una "palabra" por elemento, con repetidos. */
    fun tokenizar(texto: String): List<String> =
        normalizarBase(texto).split(' ').filter { it.isNotEmpty() && it !in STOPWORDS }.map { plegarPlural(it) }

    /** Todas las palabras (con stopwords) del texto original: sirve para medir la longitud de lo escrito. */
    private fun contarPalabras(texto: String): Int = normalizarBase(texto).split(' ').count { it.isNotEmpty() }

    private fun normalizarBase(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** "sesiones" → "sesion", "capas" → "capa"; no toca "class" ni palabras de 3 letras o menos ("mes", "pros"→"pro"). */
    private fun plegarPlural(t: String): String = when {
        t.length > 5 && t.endsWith("ones") -> t.dropLast(2)
        t.length > 3 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
        else -> t
    }

    /**
     * Las palabras del texto más cada par de palabras contiguas pegadas: así "B-Tree" (b, tree) cubre el término
     * "btree" y "state flow" cubre "stateflow".
     */
    private fun conjuntoDeBusqueda(tokens: List<String>): Set<String> {
        val conjunto = tokens.toMutableSet()
        for (i in 0 until tokens.size - 1) conjunto += tokens[i] + tokens[i + 1]
        return conjunto
    }

    /** Un término (de una o varias palabras) está si están todas sus palabras, o todas pegadas ("b-tree" ↔ "btree"). */
    private fun estaPresente(termino: List<String>, busqueda: Set<String>): Boolean =
        termino.all { it in busqueda } || termino.joinToString("") in busqueda

    /**
     * Grupos de términos alternativos de un criterio: el criterio vale lo que valga su mejor grupo.
     *  - Con `terminos` explícitos hay un solo grupo (cuenta la fracción presente).
     *  - Con nombre snake_case, "_o_" separa alternativas: `describe_CameraX_o_WebRTC` → [camerax] | [webrtc].
     */
    private fun gruposDe(criterio: CriterioRubrica): List<List<String>> {
        criterio.terminos?.let { terminos ->
            val unico = terminos.flatMap { tokenizar(it) }.distinct()
            return if (unico.isEmpty()) emptyList() else listOf(unico)
        }
        val grupos = mutableListOf<MutableList<String>>(mutableListOf())
        for (segmento in criterio.nombre.split('_')) {
            if (segmento.equals("o", ignoreCase = true)) {
                grupos += mutableListOf<String>()
                continue
            }
            grupos.last() += tokenizar(segmento).filter { it !in RELLENO_DE_CRITERIO }
        }
        return grupos.map { it.distinct() }.filter { it.isNotEmpty() }
    }

    // ---------- Similitud con la respuesta ideal ----------

    /** |A ∩ B| / |A ∪ B| sobre conjuntos de palabras. */
    fun jaccard(a: List<String>, b: List<String>): Double {
        val conjuntoA = a.toSet()
        val conjuntoB = b.toSet()
        val union = (conjuntoA + conjuntoB).size
        if (union == 0) return 0.0
        return conjuntoA.intersect(conjuntoB).size.toDouble() / union
    }

    /** 1 − d / max(|a|, |b|) con d = distancia de Levenshtein entre los textos normalizados (palabras unidas por espacio). */
    fun levenshteinNormalizada(a: List<String>, b: List<String>): Double {
        val x = a.joinToString(" ").take(MAXIMO_CARACTERES_LEVENSHTEIN)
        val y = b.joinToString(" ").take(MAXIMO_CARACTERES_LEVENSHTEIN)
        val mayor = maxOf(x.length, y.length)
        if (mayor == 0) return 0.0
        return 1.0 - distanciaLevenshtein(x, y).toDouble() / mayor
    }

    private fun distanciaLevenshtein(x: String, y: String): Int {
        if (x.isEmpty()) return y.length
        if (y.isEmpty()) return x.length
        var anterior = IntArray(y.length + 1) { it }
        var actual = IntArray(y.length + 1)
        for (i in 1..x.length) {
            actual[0] = i
            for (j in 1..y.length) {
                val costo = if (x[i - 1] == y[j - 1]) 0 else 1
                actual[j] = minOf(anterior[j] + 1, actual[j - 1] + 1, anterior[j - 1] + costo)
            }
            val intercambio = anterior
            anterior = actual
            actual = intercambio
        }
        return anterior[y.length]
    }

    private fun redondear(valor: Double, decimales: Int): Double {
        val factor = Math.pow(10.0, decimales.toDouble())
        return Math.round(valor * factor) / factor
    }
}
