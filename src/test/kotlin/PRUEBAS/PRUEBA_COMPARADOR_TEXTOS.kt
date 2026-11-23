package PRUEBAS

import SERVICIOS.ComparadorTextos
import SERVICIOS.EvaluadorRespuestaFreemium
import SERVICIOS.alternativasDe
import SERVICIOS.sinonimosDe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Comparador del feedback gratuito: TF-IDF + cobertura + Jaccard + orden, varias referencias y sinónimos. Sin IA. */
class PruebaComparadorTextos {
    private val referencias = listOf(
        "Hadoop distribuye los datos en un clúster usando HDFS.",
        "HDFS reparte los archivos en bloques entre varios nodos.",
        "Los datos se dividen en bloques y se almacenan en distintos nodos."
    )
    private val sinonimos = listOf(
        listOf("datos", "informacion"),
        listOf("nodos", "maquinas"),
        listOf("almacenan", "guarda", "guardan")
    )
    private val evaluador = EvaluadorRespuestaFreemium()

    @Test
    fun `una respuesta que repite una referencia puntua alto y una de otro tema casi cero`() {
        val buena = ComparadorTextos.similitud("HDFS divide los archivos en bloques y los reparte entre nodos.", referencias)
        val otroTema = ComparadorTextos.similitud("Python es un lenguaje de programación muy usado.", referencias)
        val sinSaber = ComparadorTextos.similitud("no sé", referencias)

        assertTrue(buena > 0.6, "buena: $buena")
        assertTrue(otroTema < 0.1, "otro tema: $otroTema")
        assertTrue(sinSaber < 0.05, "no sé: $sinSaber")
    }

    @Test
    fun `una falta de ortografia y las tildes no restan`() {
        val conErrores = ComparadorTextos.similitud("Hdfs reparte los archivos en bloqes entre varios nodos.", referencias)
        val sinTildes = ComparadorTextos.similitud("Hadoop distribuye los datos en un cluster usando HDFS", referencias)
        val conTildes = ComparadorTextos.similitud("Hadoop distribuye los datos en un clúster usando HDFS", referencias)

        assertTrue(conErrores > 0.5, "con errores: $conErrores")
        assertEquals(conTildes, sinTildes, "clúster y cluster son lo mismo")
    }

    @Test
    fun `los sinonimos rescatan una respuesta correcta dicha con otras palabras`() {
        val respuesta = "Hadoop guarda la información dividida en bloques en varias máquinas."
        val sin = ComparadorTextos.similitud(respuesta, referencias)
        val con = ComparadorTextos.similitud(respuesta, referencias, sinonimos)

        assertTrue(con > sin + 0.1, "sin sinónimos $sin → con sinónimos $con")
    }

    @Test
    fun `se queda con la mejor de varias referencias`() {
        val unaSola = ComparadorTextos.similitud("HDFS reparte los archivos en bloques entre varios nodos", referencias.take(1))
        val todas = ComparadorTextos.similitud("HDFS reparte los archivos en bloques entre varios nodos", referencias)
        assertTrue(todas > unaSola, "con la referencia 2 en la lista sube: $unaSola → $todas")
    }

    @Test
    fun `sin referencias o sin texto no hay parecido`() {
        assertEquals(0.0, ComparadorTextos.similitud("algo", emptyList()))
        assertEquals(0.0, ComparadorTextos.similitud("el de la", referencias), "solo palabras vacías")
    }

    @Test
    fun `una frase de varias palabras puede ser sinonimo de una sola`() {
        val grupos = listOf(listOf("deadlock", "bloqueo mutuo"))
        val parecido = ComparadorTextos.similitud("hay un bloqueo mutuo entre hilos", listOf("hay un deadlock entre hilos"), grupos)
        assertTrue(parecido > 0.95, "$parecido")
    }

    @Test
    fun `el evaluador usa las alternativas de la rubrica y suma mas que sin ellas`() {
        val respuesta = "Hadoop guarda la información dividida en bloques en varias máquinas."
        val ideal = referencias.first()
        val sin = evaluador.evaluar(respuesta, ideal, emptyList())
        val con = evaluador.evaluar(
            respuesta, ideal, emptyList(),
            alternativas = listOf("Hadoop guarda la información dividida en bloques en varias máquinas del clúster.")
        )

        assertTrue(con.puntaje > sin.puntaje, "sin alternativas ${sin.puntaje} → con alternativas ${con.puntaje}")
        assertTrue(con.similitud > sin.similitud)
    }

    @Test
    fun `una palabra clave cuenta si aparece alguno de sus sinonimos`() {
        val grupos = listOf(listOf("deadlock", "interbloqueo", "bloqueo mutuo"))
        val sin = evaluador.evaluar("Es un bloqueo mutuo entre procesos", "ideal", listOf("deadlock"))
        val con = evaluador.evaluar("Es un bloqueo mutuo entre procesos", "ideal", listOf("deadlock"), sinonimos = grupos)

        assertEquals(listOf("deadlock"), sin.palabrasFaltantes)
        assertEquals(listOf("deadlock"), con.palabrasEncontradas)
        assertTrue(con.puntaje > sin.puntaje)
    }

    @Test
    fun `las alternativas y los sinonimos se leen de la rubrica y se ignoran los grupos de un solo termino`() {
        val rubrica = Json.parseToJsonElement(
            """{"respuestas_alternativas":["Una forma","  ","Otra forma"],"sinonimos":[["a","b"],["solo"],["x","y","z"]]}"""
        ).jsonObject
        assertEquals(listOf("Una forma", "Otra forma"), alternativasDe(rubrica))
        assertEquals(listOf(listOf("a", "b"), listOf("x", "y", "z")), sinonimosDe(rubrica))
        assertEquals(emptyList(), alternativasDe(null))
        assertEquals(emptyList(), sinonimosDe(Json.parseToJsonElement("""{"sinonimos":"no es lista"}""").jsonObject))
    }
}
