package PRUEBAS

import INTEGRACIONES.ProveedorPreguntasIa
import INTEGRACIONES.RespuestaProveedorIa
import INTEGRACIONES.SolicitudProveedorIa
import MODELOS.CategoriaHabilidad
import MODELOS.ModoEvaluacion
import MODELOS.NivelExperiencia
import SERVICIOS.ContextoEvaluacionEntrevista
import SERVICIOS.EvaluacionEntrevista
import SERVICIOS.EvaluadorEntrevista
import SERVICIOS.EvaluadorEntrevistaIa
import SERVICIOS.RespuestaAbiertaAEvaluar
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contrato D-evidencia/plan_estudio: la IA solo se acepta con cita literal y plan de 1 a 8 pasos; si no, respaldo freemium. */
class PruebaEvaluadorEntrevista {
    private val id = UUID.randomUUID()
    private val textoCandidato = "Un índice B-Tree acelera las búsquedas porque evita recorrer toda la tabla."
    private val contexto = ContextoEvaluacionEntrevista(
        cargo = "Backend", nivel = NivelExperiencia.JUNIOR,
        respuestas = listOf(
            RespuestaAbiertaAEvaluar(id, CategoriaHabilidad.TECNICA, "¿Qué es un índice?", "ideal", emptyList(), textoCandidato)
        )
    )

    private class ProveedorFalso(private val contenido: String) : ProveedorPreguntasIa {
        override suspend fun generar(solicitud: SolicitudProveedorIa) = RespuestaProveedorIa(contenido, 10, 10)
    }

    private val respaldo = EvaluadorEntrevista { EvaluacionEntrevista(evaluaciones = emptyList(), modo = ModoEvaluacion.FREEMIUM) }

    private fun json(evidencia: String?, plan: List<String>?): String {
        val campoEvidencia = evidencia?.let { ""","evidencia":"$it"""" }.orEmpty()
        val campoPlan = plan?.joinToString(",", prefix = ""","plan_estudio":[""", postfix = "]") { "\"$it\"" }.orEmpty()
        return """{"evaluaciones":[{"id":"$id","puntaje":80,"observacion":"Bien explicado.","mejoras":["Cita un ejemplo"]$campoEvidencia}],""" +
            """"fortalezas":["Claridad"],"areas_mejora":["Ejemplos"],"resumen":"Buen trabajo"$campoPlan}"""
    }

    private fun evaluar(contenido: String) = runBlocking {
        EvaluadorEntrevistaIa(ProveedorFalso(contenido), "modelo-falso", respaldo).evaluar(contexto)
    }

    private fun pasos(n: Int) = List(n) { "Paso $it" }

    @Test
    fun `evidencia literal y plan de 3 a 5 pasos se acepta en modo IA`() {
        // Con acentos y mayúsculas distintas: la comparación normaliza.
        val r = evaluar(json("un indice b-tree acelera las busquedas", pasos(4)))
        assertEquals(ModoEvaluacion.IA, r.modo)
        assertEquals("un indice b-tree acelera las busquedas", r.evaluaciones.single().evidencia)
        assertEquals(4, r.planEstudio.size)
    }

    @Test
    fun `evidencia alucinada cae al respaldo freemium`() {
        val r = evaluar(json("Siempre uso particionado horizontal", pasos(4)))
        assertEquals(ModoEvaluacion.FREEMIUM, r.modo)
        assertNull(r.resumen)
    }

    @Test
    fun `plan de estudio vacio o con 9 pasos cae al respaldo freemium`() {
        assertEquals(ModoEvaluacion.FREEMIUM, evaluar(json("evita recorrer toda la tabla", emptyList())).modo)
        assertEquals(ModoEvaluacion.FREEMIUM, evaluar(json("evita recorrer toda la tabla", pasos(9))).modo)
    }

    @Test
    fun `respuesta sin los campos nuevos cae al respaldo sin romper`() {
        assertEquals(ModoEvaluacion.FREEMIUM, evaluar(json(null, null)).modo)
    }
}
