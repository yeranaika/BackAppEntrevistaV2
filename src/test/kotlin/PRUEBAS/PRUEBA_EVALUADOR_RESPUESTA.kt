package PRUEBAS

import SERVICIOS.EvaluadorRespuestaFreemium
import SERVICIOS.palabrasClaveDe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruebaEvaluadorRespuesta {
    private val evaluador = EvaluadorRespuestaFreemium()
    private val idealDeadlock =
        "Un interbloqueo o deadlock es una situación donde dos hilos o procesos se bloquean mutuamente porque cada uno retiene un recurso que el otro necesita."

    @Test
    fun `una respuesta completa saca puntaje alto y encuentra las palabras clave`() {
        val resultado = evaluador.evaluar(
            "Un deadlock o interbloqueo ocurre cuando dos o más procesos compiten por recursos y cada uno espera que el otro libere un bloqueo, causando un bloqueo mutuo permanente.",
            idealDeadlock,
            listOf("deadlock", "interbloqueo", "procesos", "recursos", "bloqueo mutuo")
        )
        assertTrue(resultado.puntaje >= 75.0, resultado.toString())
        assertTrue(resultado.coberturaPalabrasClave >= 80.0)
        assertTrue(resultado.palabrasEncontradas.containsAll(listOf("deadlock", "interbloqueo")))
        assertTrue(resultado.similitud > 50.0)
        assertTrue(resultado.feedback.startsWith("Excelente") || resultado.feedback.startsWith("Buena"))
    }

    @Test
    fun `una respuesta sin los conceptos saca puntaje bajo y dice que repasar`() {
        val resultado = evaluador.evaluar(
            "Es cuando se traba el sistema por falta de memoria.",
            "Un deadlock ocurre cuando múltiples procesos quedan bloqueados esperando recursos retenidos entre sí.",
            listOf("deadlock", "procesos", "recursos", "bloqueo mutuo")
        )
        assertTrue(resultado.puntaje < 50.0)
        assertEquals(listOf("deadlock", "procesos", "recursos", "bloqueo mutuo"), resultado.palabrasFaltantes)
        assertTrue(resultado.feedback.contains("repasar"))
    }

    @Test
    fun `una respuesta vacia saca cero`() {
        val resultado = evaluador.evaluar("  ", "Respuesta ideal", listOf("a", "b"))
        assertEquals(0.0, resultado.puntaje)
        assertEquals(listOf("a", "b"), resultado.palabrasFaltantes)
    }

    @Test
    fun `sin palabras clave una respuesta corta e irrelevante ya no regala 40 puntos`() {
        // Antes la cobertura contaba como 100 % cuando no había palabras clave.
        val resultado = evaluador.evaluar("no sé", idealDeadlock, emptyList())
        assertTrue(resultado.puntaje < 20.0, resultado.toString())
    }

    @Test
    fun `las palabras clave salen de la rubrica o de sus criterios`() {
        val conPalabras = Json.parseToJsonElement("""{"criterios":["x"],"palabras_clave":["Coroutines","suspend"]}""").jsonObject
        assertEquals(listOf("Coroutines", "suspend"), palabrasClaveDe(conPalabras))

        val soloCriterios = Json.parseToJsonElement(
            """{"criterios":["menciona_inmutabilidad","distingue_referencia_vs_estado","menciona_Job_vs_Deferred"],"puntaje_maximo":10}"""
        ).jsonObject
        assertEquals(listOf("inmutabilidad", "referencia", "estado", "deferred"), palabrasClaveDe(soloCriterios))
        assertEquals(emptyList(), palabrasClaveDe(null))
    }
}
