package PRUEBAS

import SERVICIOS.BandaRevision
import SERVICIOS.ConfiguracionRevisor
import SERVICIOS.MetodoSimilitud
import SERVICIOS.RevisorTextoLibre
import SERVICIOS.RubricaRevision
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Revisor de texto libre sin IA (T3.13): comportamiento, paridad con Android y calibración.
 *
 * Los casos viven en `src/test/resources/revisor_casos_dorados.json`, el MISMO archivo (byte a byte) que usa la app
 * Android. Cada caso trae el `esperado` que calcula esta fórmula; la app debe obtener lo mismo. Para regenerar el
 * `esperado` tras cambiar la fórmula: `REGENERAR_CASOS_DORADOS=1 ./gradlew :test --tests "*PruebaRevisorTextoLibre*"` y
 * copiar el archivo a la app (allá hay una prueba que falla si los puntajes no coinciden).
 *
 * Calibración PROVISIONAL: la tabla sesion_pregunta_respuesta no tenía evaluaciones previas, así que `notaHumana` es
 * el juicio de quien redactó los casos, sobre las preguntas de Seeds.sql.
 */
class PruebaRevisorTextoLibre {

    private class Caso(
        val id: String,
        val ideal: String?,
        val rubrica: JsonObject,
        val texto: String,
        val nota: Double?,
        val original: JsonObject
    )

    private val archivo = File("src/test/resources/revisor_casos_dorados.json")

    private fun casos(): List<Caso> =
        Json.parseToJsonElement(archivo.readText(Charsets.UTF_8)).jsonObject["casos"]!!.jsonArray.map {
            val o = it.jsonObject
            Caso(
                id = o["id"]!!.jsonPrimitive.content,
                ideal = o["respuestaIdeal"]?.takeIf { v -> v !is JsonNull }?.jsonPrimitive?.content,
                rubrica = o["rubrica"]!!.jsonObject,
                texto = o["texto"]!!.jsonPrimitive.content,
                nota = o["notaHumana"]?.takeIf { v -> v !is JsonNull }?.jsonPrimitive?.doubleOrNull,
                original = o
            )
        }

    /** Con [metodo] null usa la configuración por defecto del revisor (la que corre en producción). */
    private fun revisar(c: Caso, metodo: MetodoSimilitud? = null) =
        RevisorTextoLibre.revisar(
            c.texto, c.ideal, RubricaRevision.desdeJson(c.rubrica),
            if (metodo == null) ConfiguracionRevisor() else ConfiguracionRevisor(metodoSimilitud = metodo)
        )

    private fun caso(id: String) = casos().first { it.id == id }

    private fun puntaje(id: String) = revisar(caso(id)).score

    private fun pearson(x: List<Double>, y: List<Double>): Double {
        val mx = x.average()
        val my = y.average()
        val cov = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
        val vx = x.sumOf { (it - mx) * (it - mx) }
        val vy = y.sumOf { (it - my) * (it - my) }
        return cov / sqrt(vx * vy)
    }

    private fun json(texto: String) = Json.parseToJsonElement(texto).jsonObject

    // ---------- Paridad con Android ----------

    @Test
    fun `regenera el esperado de los casos dorados solo si se pide`() {
        if (System.getenv("REGENERAR_CASOS_DORADOS") != "1") return
        val raiz = Json.parseToJsonElement(archivo.readText(Charsets.UTF_8)).jsonObject
        val nuevos = casos().map { c ->
            val d = revisar(c)
            val j = revisar(c, MetodoSimilitud.JACCARD)
            val esperado = buildJsonObject {
                put("score", d.score)
                put("banda", d.banda.name)
                put("evaluable", d.evaluable)
                put("coberturaPalabrasClave", d.coberturaPalabrasClave)
                put("coberturaCriterios", d.coberturaCriterios)
                put("similitud", d.similitud)
                put("palabras", d.palabras)
                put("relacionLongitud", d.relacionLongitud)
                put("palabrasFaltantes", JsonArray(d.palabrasFaltantes.map { JsonPrimitive(it) }))
                put("criteriosFaltantes", JsonArray(d.criteriosFaltantes.map { JsonPrimitive(it) }))
                put("scoreJaccard", j.score)
                put("similitudJaccard", j.similitud)
                put("bandaJaccard", j.banda.name)
            }
            JsonObject(c.original + ("esperado" to esperado))
        }
        val salida = JsonObject(raiz + ("casos" to JsonArray(nuevos)))
        archivo.writeText(Json { prettyPrint = true; prettyPrintIndent = " " }.encodeToString(JsonObject.serializer(), salida), Charsets.UTF_8)
    }

    @Test
    fun `cada caso dorado da el puntaje esperado con ambos metodos`() {
        val lista = casos()
        assertTrue(lista.isNotEmpty())
        for (c in lista) {
            val e = c.original["esperado"]?.jsonObject ?: error("El caso ${c.id} no tiene 'esperado': regeneralo (ver el comentario de la clase)")
            val d = revisar(c)
            assertEquals(e["score"]!!.jsonPrimitive.double, d.score, 1e-9, "score de ${c.id}")
            assertEquals(e["banda"]!!.jsonPrimitive.content, d.banda.name, "banda de ${c.id}")
            assertEquals(e["evaluable"]!!.jsonPrimitive.content.toBoolean(), d.evaluable, "evaluable de ${c.id}")
            assertEquals(e["coberturaPalabrasClave"]!!.jsonPrimitive.doubleOrNull, d.coberturaPalabrasClave, "A de ${c.id}")
            assertEquals(e["coberturaCriterios"]!!.jsonPrimitive.doubleOrNull, d.coberturaCriterios, "B de ${c.id}")
            assertEquals(e["similitud"]!!.jsonPrimitive.doubleOrNull, d.similitud, "C de ${c.id}")
            assertEquals(e["palabras"]!!.jsonPrimitive.content.toInt(), d.palabras, "palabras de ${c.id}")
            assertEquals(e["relacionLongitud"]!!.jsonPrimitive.doubleOrNull, d.relacionLongitud, "longitud de ${c.id}")
            assertEquals(e["palabrasFaltantes"]!!.jsonArray.map { it.jsonPrimitive.content }, d.palabrasFaltantes, "palabras faltantes de ${c.id}")
            assertEquals(e["criteriosFaltantes"]!!.jsonArray.map { it.jsonPrimitive.content }, d.criteriosFaltantes, "criterios faltantes de ${c.id}")
            val j = revisar(c, MetodoSimilitud.JACCARD)
            assertEquals(e["scoreJaccard"]!!.jsonPrimitive.double, j.score, 1e-9, "score Jaccard de ${c.id}")
            assertEquals(e["similitudJaccard"]!!.jsonPrimitive.doubleOrNull, j.similitud, "similitud Jaccard de ${c.id}")
            assertEquals(e["bandaJaccard"]!!.jsonPrimitive.content, j.banda.name, "banda Jaccard de ${c.id}")
        }
    }

    // ---------- Comportamiento ----------

    @Test
    fun `una respuesta identica a la ideal con rubrica completa saca casi 100 y aprueba`() {
        for (id in listOf("capas_identica_a_la_ideal", "capas_ideal_mayusculas_sin_tildes", "borde_solo_ideal")) {
            val r = revisar(caso(id))
            assertTrue(r.score >= 90.0, "$id: ${r.score}")
            assertEquals(BandaRevision.APROBADA, r.banda, id)
        }
        assertEquals(100.0, puntaje("borde_solo_ideal"))
    }

    @Test
    fun `mayusculas y tildes no cambian el puntaje`() {
        assertEquals(puntaje("capas_identica_a_la_ideal"), puntaje("capas_ideal_mayusculas_sin_tildes"), 1e-9)
    }

    @Test
    fun `un texto al azar o vacio queda en REVISAR con puntaje bajo`() {
        for (id in casos().map { it.id }.filter { it.endsWith("fuera_de_tema") }) {
            val r = revisar(caso(id))
            assertEquals(BandaRevision.REVISAR, r.banda, id)
            assertTrue(r.score < 15.0, "$id: ${r.score}")
        }
        val vacio = revisar(caso("borde_texto_vacio"))
        assertEquals(0.0, vacio.score)
        assertEquals(BandaRevision.REVISAR, vacio.banda)
    }

    @Test
    fun `una respuesta parcial cae en la banda CERCANA y no existe una banda reprobada`() {
        // Con las semillas (rúbrica sin palabras clave) una respuesta muy buena redactada con otras palabras queda CERCANA.
        assertEquals(BandaRevision.CERCANA, revisar(caso("api_excelente")).banda)
        assertEquals(BandaRevision.CERCANA, revisar(caso("indices_excelente")).banda)
        assertEquals(setOf("APROBADA", "CERCANA", "REVISAR"), BandaRevision.entries.map { it.name }.toSet())
    }

    @Test
    fun `en cada pregunta de las semillas el orden del puntaje respeta el juicio humano`() {
        for (pregunta in listOf("cache", "indices", "api", "capas", "conflicto", "produccion")) {
            val excelente = puntaje("${pregunta}_excelente")
            val buena = puntaje("${pregunta}_buena")
            val malas = casos().filter { it.id.startsWith("${pregunta}_") && !it.id.contains("palabras_clave") && !it.id.contains("lista_de_palabras") && it.nota!! <= 4.0 }.map { revisar(it).score }
            assertTrue(excelente > buena, "$pregunta: excelente $excelente > buena $buena")
            assertTrue(buena > malas.max(), "$pregunta: buena $buena > lo malo ${malas.max()}")
        }
    }

    @Test
    fun `una lista de palabras clave sin explicar nada no aprueba (limitacion conocida, queda para revision humana)`() {
        assertTrue(revisar(caso("cache_lista_de_palabras")).banda != BandaRevision.APROBADA)
        assertTrue(revisar(caso("indices_lista_de_palabras_con_palabras_clave")).banda != BandaRevision.APROBADA)
    }

    @Test
    fun `sin respuesta ideal ni rubrica no hay base para puntuar`() {
        val r = revisar(caso("borde_sin_referencia"))
        assertEquals(false, r.evaluable)
        assertEquals(0.0, r.score)
        assertEquals(BandaRevision.REVISAR, r.banda)
    }

    @Test
    fun `la rubrica sin palabras clave reparte su peso entre los otros componentes`() {
        val r = revisar(caso("api_excelente"))
        assertNull(r.coberturaPalabrasClave)
        val esperado = (0.20 * r.coberturaCriterios!! + 0.35 * r.similitud!!) / 0.55 * 100.0
        assertEquals(esperado, r.score, 0.01)
    }

    @Test
    fun `el criterio con alternativas vale lo que valga la mejor (CameraX o WebRTC)`() {
        val rubrica = RubricaRevision.desdeJson(json("""{"criterios":["describe_CameraX_o_WebRTC"]}"""))
        assertEquals(1.0, RevisorTextoLibre.revisar("Uso WebRTC para el video", null, rubrica).coberturaCriterios)
        assertEquals(1.0, RevisorTextoLibre.revisar("Uso CameraX para el video", null, rubrica).coberturaCriterios)
        assertEquals(0.0, RevisorTextoLibre.revisar("Uso algo para el video", null, rubrica).coberturaCriterios)
    }

    @Test
    fun `B-Tree cubre btree y las tildes no importan en las palabras clave`() {
        val rubrica = RubricaRevision.desdeJson(json("""{"palabras_clave":["btree","reasignación"]}"""))
        val r = RevisorTextoLibre.revisar("Un índice B-Tree no permite la REASIGNACION", null, rubrica)
        assertEquals(1.0, r.coberturaPalabrasClave)
    }

    @Test
    fun `la rubrica con formato raro no revienta`() {
        val raro = json("""{"palabras_clave":"no es lista","criterios":[1,null,{"x":1},{"nombre":"a"}],"tiempo_esperado_seg":"x"}""")
        RevisorTextoLibre.revisar("texto", "otro texto", RubricaRevision.desdeJson(raro))
        RubricaRevision.desdeJson(null)
    }

    @Test
    fun `la longitud frente al tiempo esperado es solo informativa`() {
        val c = caso("valvar_rubrica_completa_identica")
        val corto = RevisorTextoLibre.revisar(c.texto, c.ideal, RubricaRevision.desdeJson(c.rubrica))
        val largo = RevisorTextoLibre.revisar(
            c.texto, c.ideal, RubricaRevision.desdeJson(JsonObject(c.rubrica + ("tiempo_esperado_seg" to JsonPrimitive(600))))
        )
        assertTrue(corto.relacionLongitud!! > largo.relacionLongitud!!)
        assertEquals(corto.score, largo.score)
    }

    // ---------- Calibración: ¿Jaccard o Levenshtein? ----------

    @Test
    fun `el metodo de similitud por defecto es el que mejor correlaciona con la nota humana`() {
        val con = casos().filter { it.nota != null }
        val notas = con.map { it.nota!! }
        val rJaccard = pearson(con.map { revisar(it, MetodoSimilitud.JACCARD).score }, notas)
        val rLevenshtein = pearson(con.map { revisar(it, MetodoSimilitud.LEVENSHTEIN).score }, notas)
        println("CALIBRACION (PROVISIONAL, n=${con.size}) r(Jaccard)=${"%.3f".format(rJaccard)} r(Levenshtein)=${"%.3f".format(rLevenshtein)}")
        if (System.getenv("INFORME_CALIBRACION") == "1") {
            for (c in con) {
                val rj = revisar(c, MetodoSimilitud.JACCARD)
                val rl = revisar(c, MetodoSimilitud.LEVENSHTEIN)
                println("CASO %-48s nota=%4.1f  J=%6.2f %-9s  L=%6.2f %-9s".format(c.id, c.nota, rj.score, rj.banda, rl.score, rl.banda))
            }
        }
        val mejor = if (rLevenshtein >= rJaccard) MetodoSimilitud.LEVENSHTEIN else MetodoSimilitud.JACCARD
        assertEquals(mejor, ConfiguracionRevisor().metodoSimilitud, "El método por defecto debe ser el de mayor correlación")
        assertTrue(maxOf(rJaccard, rLevenshtein) > 0.75, "La fórmula dejó de correlacionar con el juicio humano")
    }
}
