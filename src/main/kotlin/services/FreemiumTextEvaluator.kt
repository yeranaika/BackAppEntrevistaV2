package services

/**
 * Motor de Evaluación de Texto Freemium (Cero costo de API).
 *
 * Emplea un algoritmo determinista híbrido:
 *   1. Similitud Coseno por N-Grams (Tri-gramas) entre la respuesta del usuario y la respuesta ideal (40% peso).
 *   2. Cobertura de Palabras Clave y conceptos extraídos de la rúbrica (40% peso).
 *   3. Ratio de longitud y completitud (20% peso).
 */
object FreemiumTextEvaluator {

    data class EvaluationResult(
        val score: Double, // 0.0 a 100.0
        val keywordMatchPercentage: Double,
        val similarityPercentage: Double,
        val matchedKeywords: List<String>,
        val missingKeywords: List<String>,
        val feedbackSummary: String
    )

    fun evaluate(
        userText: String,
        idealText: String,
        expectedKeywords: List<String>
    ): EvaluationResult {
        if (userText.isBlank()) {
            return EvaluationResult(
                score = 0.0,
                keywordMatchPercentage = 0.0,
                similarityPercentage = 0.0,
                matchedKeywords = emptyList(),
                missingKeywords = expectedKeywords,
                feedbackSummary = "No se ingresó ninguna respuesta."
            )
        }

        val cleanUser = cleanText(userText)
        val cleanIdeal = cleanText(idealText)

        // 1. Cobertura de Palabras Clave / Conceptos
        val matched = expectedKeywords.filter { keyword ->
            cleanUser.contains(cleanText(keyword))
        }
        val missing = expectedKeywords.filterNot { cleanUser.contains(cleanText(it)) }
        val keywordScore = if (expectedKeywords.isNotEmpty()) {
            (matched.size.toDouble() / expectedKeywords.size.toDouble()) * 100.0
        } else 100.0

        // 2. Similitud Coseno por Tri-gramas
        val similarityScore = calculateNgramCosineSimilarity(cleanUser, cleanIdeal) * 100.0

        // 3. Ratio de Longitud y Completitud (castigo por respuestas mono-palabra)
        val userWordCount = userText.trim().split(Regex("\\s+")).size
        val idealWordCount = idealText.trim().split(Regex("\\s+")).size.coerceAtLeast(1)
        val lengthRatio = (userWordCount.toDouble() / idealWordCount.toDouble()).coerceIn(0.0, 1.0) * 100.0

        // Score ponderado final (0 - 100)
        val finalScore = (similarityScore * 0.40) + (keywordScore * 0.40) + (lengthRatio * 0.20)
        val roundedScore = Math.round(finalScore * 10.0) / 10.0

        val feedback = when {
            roundedScore >= 80 -> "Excelente respuesta. Cubres los conceptos fundamentales con claridad y precisión."
            roundedScore >= 60 -> if (missing.isNotEmpty()) {
                "Buena aproximación. Para completarla mejor, profundiza en: ${missing.take(4).joinToString(", ")}."
            } else {
                "Buena respuesta, aunque podrías detallar más los fundamentos técnicos."
            }
            else -> if (missing.isNotEmpty()) {
                "Respuesta incompleta. Te recomendamos repasar los siguientes conceptos clave: ${missing.take(4).joinToString(", ")}."
            } else {
                "Respuesta muy breve o con baja relación con el concepto esperado."
            }
        }

        return EvaluationResult(
            score = roundedScore,
            keywordMatchPercentage = Math.round(keywordScore * 10.0) / 10.0,
            similarityPercentage = Math.round(similarityScore * 10.0) / 10.0,
            matchedKeywords = matched,
            missingKeywords = missing,
            feedbackSummary = feedback
        )
    }

    private fun cleanText(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-záéíóúüñ0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun calculateNgramCosineSimilarity(s1: String, s2: String, n: Int = 3): Double {
        if (s1.length < n || s2.length < n) {
            // Si el texto es muy corto, comparar palabras directamente
            val words1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
            val words2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
            if (words1.isEmpty() || words2.isEmpty()) return 0.0
            val intersection = words1.intersect(words2).size
            val union = words1.union(words2).size
            return if (union > 0) intersection.toDouble() / union.toDouble() else 0.0
        }

        val ngrams1 = s1.windowed(n, 1).groupingBy { it }.eachCount()
        val ngrams2 = s2.windowed(n, 1).groupingBy { it }.eachCount()
        val allKeys = ngrams1.keys + ngrams2.keys

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (key in allKeys) {
            val v1 = ngrams1[key] ?: 0
            val v2 = ngrams2[key] ?: 0
            dotProduct += (v1 * v2)
            normA += (v1 * v1)
            normB += (v2 * v2)
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))
    }
}

