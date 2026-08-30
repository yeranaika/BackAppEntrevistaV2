package services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.system.measureTimeMillis

class FreemiumTextEvaluatorTest {

    @Test
    fun testScoreAltoConKeywordsYSimilitud() {
        val userText = "Un deadlock o interbloqueo ocurre cuando dos o más procesos compiten por recursos y cada uno espera que el otro libere un bloqueo, causando un bloqueo mutuo permanente."
        val idealText = "Un interbloqueo o deadlock es una situación donde dos hilos o procesos se bloquean mutuamente porque cada uno retiene un recurso que el otro necesita."
        val keywords = listOf("deadlock", "interbloqueo", "procesos", "recursos", "bloqueo mutuo")

        val result = FreemiumTextEvaluator.evaluate(userText, idealText, keywords)

        assertTrue(result.score >= 75.0, "El score debe ser alto (>= 75)")
        assertTrue(result.keywordMatchPercentage >= 80.0, "Debe coincidir con la mayoría de keywords")
        assertTrue(result.matchedKeywords.contains("deadlock"))
        assertTrue(result.matchedKeywords.contains("interbloqueo"))
        assertTrue(result.similarityPercentage > 50.0)
        assertTrue(result.feedbackSummary.contains("Excelente") || result.feedbackSummary.contains("Buena"))
    }

    @Test
    fun testKeywordsFaltantesYFeedback() {
        val userText = "Es cuando se traba el sistema por falta de memoria."
        val idealText = "Un deadlock ocurre cuando múltiples procesos quedan bloqueados esperando recursos retenidos entre sí."
        val keywords = listOf("deadlock", "procesos", "recursos", "bloqueo mutuo")

        val result = FreemiumTextEvaluator.evaluate(userText, idealText, keywords)

        assertTrue(result.score < 50.0, "Score debe ser bajo por falta de conceptos clave")
        assertTrue(result.missingKeywords.contains("deadlock"))
        assertTrue(result.missingKeywords.contains("procesos"))
        assertTrue(result.missingKeywords.contains("recursos"))
        assertTrue(result.missingKeywords.contains("bloqueo mutuo"))
        assertTrue(result.feedbackSummary.contains("repasar los siguientes conceptos clave") || result.feedbackSummary.contains("Respuesta"))
    }

    @Test
    fun testRespuestaVacia() {
        val result = FreemiumTextEvaluator.evaluate("", "Respuesta ideal", listOf("keyword1", "keyword2"))

        assertEquals(0.0, result.score)
        assertEquals(0.0, result.keywordMatchPercentage)
        assertEquals(0.0, result.similarityPercentage)
        assertTrue(result.matchedKeywords.isEmpty())
        assertEquals(2, result.missingKeywords.size)
        assertEquals("No se ingresó ninguna respuesta.", result.feedbackSummary)
    }

    @Test
    fun testLatenciaMenorA5Milisegundos() {
        val userText = "Las coroutines en Kotlin son hilos ligeros que permiten programación asíncrona no bloqueante utilizando suspend functions y dispatchers."
        val idealText = "Las corrutinas son subprocesos livianos para concurrencia estructurada asíncrona sin bloquear hilos mediante funciones de suspensión."
        val keywords = listOf("coroutines", "asíncrona", "no bloqueante", "suspend", "dispatchers")

        // Calentamiento JIT
        repeat(50) {
            FreemiumTextEvaluator.evaluate(userText, idealText, keywords)
        }

        // Medición
        val elapsedMs = measureTimeMillis {
            val result = FreemiumTextEvaluator.evaluate(userText, idealText, keywords)
            assertNotNull(result)
        }

        println("Tiempo de evaluación Freemium: ${elapsedMs}ms")
        assertTrue(elapsedMs < 5, "La evaluación debe ejecutarse en menos de 5ms (tomó ${elapsedMs}ms)")
    }
}
