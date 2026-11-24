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
    fun `una palabra clave con simbolos no coincide con cualquier letra`() {
        val sinMencion = evaluador.evaluar("Uso corutinas para la concurrencia", idealDeadlock, listOf("C#", "C++", ".NET"))
        assertEquals(listOf("C#", "C++", ".NET"), sinMencion.palabrasFaltantes)

        val conMencion = evaluador.evaluar("Lo hago en C# sobre .NET con buenas practicas", idealDeadlock, listOf("C#", "C++", ".NET"))
        assertEquals(listOf("C#", ".NET"), conMencion.palabrasEncontradas)
        assertEquals(listOf("C++"), conMencion.palabrasFaltantes)
    }

    @Test
    fun `una palabra clave corta debe ser una palabra completa y las largas admiten plurales`() {
        val corta = evaluador.evaluar("Hay algo que no entiendo de esto", idealDeadlock, listOf("go", "sql"))
        assertEquals(listOf("go", "sql"), corta.palabrasFaltantes)

        val completa = evaluador.evaluar("Escribo servicios en Go con SQL", idealDeadlock, listOf("go", "sql"))
        assertEquals(listOf("go", "sql"), completa.palabrasEncontradas)

        val plural = evaluador.evaluar("Los procesos se bloquean", idealDeadlock, listOf("proceso"))
        assertEquals(listOf("proceso"), plural.palabrasEncontradas)
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

    /** Las marcas de formato de la app ("S:", "1)") no son contenido: no cambian el puntaje (D40). */
    @Test
    fun `las marcas S T A R y los pasos numerados no cambian el puntaje`() {
        val contenido = listOf(
            "Un sistema crítico cayó en producción con usuarios afectados.",
            "Debía diagnosticarlo y restaurarlo en el menor tiempo.",
            "Revisé los logs.", "Identifiqué el último despliegue.", "Ejecuté el rollback.",
            "El sistema quedó restaurado y documenté la lección."
        )
        val enParrafo = contenido.joinToString(" ")
        val conFormato = "S: ${contenido[0]} T: ${contenido[1]} A: 1) ${contenido[2]} 2) ${contenido[3]} 3) ${contenido[4]} R: ${contenido[5]}"
        val texto = "Cuando cayó producción revisé logs, vi el último despliegue, hice rollback y dejé todo documentado."
        val claves = listOf("logs", "rollback", "despliegue")

        assertEquals(evaluador.evaluar(texto, enParrafo, claves), evaluador.evaluar(texto, conFormato, claves))
    }

    @Test
    fun `quien copia el formato de la tarjeta no pierde puntos por las marcas`() {
        val ideal = "S: Hubo caída en producción. T: Restaurar el servicio. A: 1) Revisé logs. 2) Hice rollback. R: Servicio restaurado."
        val claves = listOf("logs", "rollback")
        val conMarcas = evaluador.evaluar(ideal, ideal, claves)
        val sinMarcas = evaluador.evaluar("Hubo caída en producción. Restaurar el servicio. Revisé logs. Hice rollback. Servicio restaurado.", ideal, claves)
        assertEquals(sinMarcas.puntaje, conMarcas.puntaje)
    }
}

