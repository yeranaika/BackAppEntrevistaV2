package PRUEBAS

import ESQUEMAS.EsquemaJsonLoteLlm
import ESQUEMAS.LoteLlm
import ESQUEMAS.PreguntaLlm
import ESQUEMAS.RubricaLlm
import MODELOS.CategoriaHabilidad
import MODELOS.TipoPregunta
import SERVICIOS.LoteInvalidoException
import SERVICIOS.ValidadorLotePreguntaIa
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** El método STAR solo se exige a las preguntas de comportamiento; las técnicas piden precisión, no formato. */
class PruebaValidadorLotePreguntaIa {
    private val json = Json { encodeDefaults = true }

    private val respuestaTecnica =
        "Un deadlock ocurre cuando dos hilos esperan recursos que el otro retiene. Se evita ordenando la toma de bloqueos."
    private val criteriosTecnicos = listOf("define_deadlock", "menciona_recursos", "explica_causa", "propone_prevencion")

    private fun lote(
        metodo: String,
        respuestaIdeal: String,
        criterios: List<String>,
        palabrasClave: List<String> = listOf("deadlock", "hilos", "bloqueos")
    ) = json.encodeToString(
        LoteLlm(
            listOf(
                PreguntaLlm(
                    enunciado = "Explica qué es un deadlock y cómo se evita en una aplicación concurrente",
                    tipoPregunta = "abierta_texto",
                    respuestaIdeal = respuestaIdeal,
                    rubrica = RubricaLlm(metodo, criterios, palabrasClave, 120)
                )
            )
        )
    )

    private fun validar(json: String, categoria: CategoriaHabilidad) =
        ValidadorLotePreguntaIa.parsearYValidar(json, 1, TipoPregunta.ABIERTA_TEXTO, categoria)

    @Test
    fun `una pregunta tecnica no necesita STAR en su rubrica ni en su respuesta ideal`() {
        val tecnica = lote("TECNICA", respuestaTecnica, criteriosTecnicos)
        assertEquals(1, validar(tecnica, CategoriaHabilidad.TECNICA).size)
    }

    @Test
    fun `una pregunta de comportamiento sigue exigiendo STAR completo`() {
        val sinStar = lote("STAR", respuestaTecnica, criteriosTecnicos)
        val error = assertFailsWith<LoteInvalidoException> { validar(sinStar, CategoriaHabilidad.BLANDA) }
        assertTrue(error.message!!.startsWith("rubrica_sin_") || error.message!!.startsWith("respuesta_sin_"), error.message)

        val metodoIncorrecto = lote("TECNICA", "Situación: a. Tarea: b. Acción: c. Resultado: d. Fin de la respuesta.", listOf("Situación", "Tarea", "Acción", "Resultado"))
        assertFailsWith<LoteInvalidoException> { validar(metodoIncorrecto, CategoriaHabilidad.BLANDA) }
    }

    @Test
    fun `una pregunta tecnica sigue necesitando una respuesta de dos oraciones y criterios suficientes`() {
        assertFailsWith<LoteInvalidoException> {
            validar(lote("TECNICA", "Es un bloqueo entre hilos que esperan recursos", criteriosTecnicos), CategoriaHabilidad.TECNICA)
        }
        assertFailsWith<LoteInvalidoException> {
            validar(lote("TECNICA", respuestaTecnica, listOf("define_deadlock")), CategoriaHabilidad.TECNICA)
        }
    }

    @Test
    fun `las palabras clave de dos letras como Go o C# ya no invalidan el lote`() {
        val conTecnologias = lote("TECNICA", respuestaTecnica, criteriosTecnicos, listOf("Go", "C#", "deadlock"))
        assertEquals(1, validar(conTecnologias, CategoriaHabilidad.TECNICA).size)

        val unaLetra = lote("TECNICA", respuestaTecnica, criteriosTecnicos, listOf("R", "Go", "deadlock"))
        assertFailsWith<LoteInvalidoException> { validar(unaLetra, CategoriaHabilidad.TECNICA) }
    }

    @Test
    fun `el esquema pide STAR a las de comportamiento y TECNICA a las tecnicas`() {
        val blanda = EsquemaJsonLoteLlm.construir(1, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.BLANDA).toString()
        val tecnica = EsquemaJsonLoteLlm.construir(1, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.TECNICA).toString()
        assertTrue(blanda.contains("\"const\":\"STAR\"") && !blanda.contains("\"const\":\"TECNICA\""))
        assertTrue(tecnica.contains("\"const\":\"TECNICA\"") && !tecnica.contains("\"const\":\"STAR\""))
    }
}
