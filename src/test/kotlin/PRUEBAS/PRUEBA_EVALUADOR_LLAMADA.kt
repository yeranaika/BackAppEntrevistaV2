package PRUEBAS

import MODELOS.AccionLlamada
import MODELOS.ResumenLlamada
import MODELOS.SegmentoEventoLlamada
import MODELOS.TramoLlamada
import SERVICIOS.EJE_APOYO_MESA
import SERVICIOS.EJE_MANOS_CARA
import SERVICIOS.EJE_MIRADA
import SERVICIOS.EJE_PRESENCIA_CUERPO
import SERVICIOS.EvaluadorLlamada
import SERVICIOS.RESUMEN_FEEDBACK_LLAMADA_CORTA
import SERVICIOS.RESUMEN_FEEDBACK_POSITIVO
import SERVICIOS.VEREDICTO_BIEN
import SERVICIOS.VEREDICTO_PRIORIDAD
import SERVICIOS.VEREDICTO_SIN_EVALUAR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Feedback por reglas de la llamada en vivo (N1): fórmula por eje, llamada corta, sin eventos y prioridad de frases. */
class PruebaEvaluadorLlamada {
    private val soloLinea = ResumenLlamada(linea = listOf(TramoLlamada(0, 120_000, "sin_accion")))

    private fun seg(id: String, inicioS: Int, finS: Int) = SegmentoEventoLlamada(id, inicioS * 1_000L, finS * 1_000L, 6.0)

    @Test
    fun `los cuatro ejes salen en orden de prioridad con score, segundos y porcentaje`() {
        // Vídeo de calibración: 24 s de toques en 109 s ≈ 22 % → 4/10.
        val feedback = EvaluadorLlamada.evaluar(109, soloLinea, listOf(seg("hand_to_face_adaptors", 10, 34)))!!
        assertEquals(listOf(EJE_MANOS_CARA, EJE_MIRADA, EJE_PRESENCIA_CUERPO, EJE_APOYO_MESA), feedback.ejes.map { it.id })
        val manos = feedback.ejes.first()
        assertEquals(4, manos.score)
        assertEquals(24.0, manos.segundos)
        assertEquals(22.0, manos.pctLlamada)
        assertEquals(VEREDICTO_PRIORIDAD, manos.veredicto)
        feedback.ejes.drop(1).forEach {
            assertEquals(10, it.score)
            assertEquals(0.0, it.segundos)
            assertEquals(VEREDICTO_BIEN, it.veredicto)
        }
        assertEquals(EJE_MANOS_CARA, feedback.peorEje)
        assertTrue(feedback.resumen.contains("Apoya las manos"), feedback.resumen)
    }

    @Test
    fun `el score se recorta a 0 y la union de intervalos no cuenta dos veces un eje`() {
        val segmentos = listOf(
            seg("gaze_direction", 0, 60), seg("gaze_alignment", 30, 90), // se solapan 30 s: el eje suma 90 s, no 120
            seg("hand_to_face_adaptors", 0, 500) // más largo que la llamada: se recorta a 100 %
        )
        val feedback = EvaluadorLlamada.evaluar(100, soloLinea, segmentos)!!
        val mirada = feedback.ejes.single { it.id == EJE_MIRADA }
        assertEquals(90.0, mirada.segundos)
        assertEquals(90.0, mirada.pctLlamada)
        val manos = feedback.ejes.single { it.id == EJE_MANOS_CARA }
        assertEquals(0, manos.score)
        assertEquals(100.0, manos.pctLlamada)
    }

    @Test
    fun `sin eventos manda el mensaje positivo por defecto`() {
        val feedback = EvaluadorLlamada.evaluar(120, soloLinea, emptyList())!!
        assertNull(feedback.peorEje)
        assertEquals(RESUMEN_FEEDBACK_POSITIVO, feedback.resumen)
        assertTrue(feedback.ejes.all { it.score == 10 && it.veredicto == VEREDICTO_BIEN })
    }

    @Test
    fun `eventos leves que no bajan del umbral tambien dan el mensaje positivo`() {
        val feedback = EvaluadorLlamada.evaluar(100, soloLinea, listOf(seg("table_rest", 0, 5)))!!
        assertNull(feedback.peorEje)
        assertEquals(RESUMEN_FEEDBACK_POSITIVO, feedback.resumen)
    }

    @Test
    fun `una llamada corta no se puntua y nunca divide por cero`() {
        listOf(0, 5, 9).forEach { duracion ->
            val feedback = EvaluadorLlamada.evaluar(duracion, soloLinea, listOf(seg("hand_to_face_adaptors", 0, 3)))!!
            assertEquals(RESUMEN_FEEDBACK_LLAMADA_CORTA, feedback.resumen)
            assertNull(feedback.peorEje)
            assertTrue(feedback.ejes.all { it.score == null && it.pctLlamada == null && it.veredicto == VEREDICTO_SIN_EVALUAR })
            val manos = feedback.ejes.single { it.id == EJE_MANOS_CARA }
            assertEquals(if (duracion == 0) 3.0 else minOf(3, duracion).toDouble(), manos.segundos, "solo hechos")
        }
    }

    @Test
    fun `manda el peor eje y ante empate el de mayor prioridad`() {
        // Mirada 80 % → 0/10, manos 20 % → 5/10: manda la mirada aunque manos vaya primero en prioridad.
        val peorMirada = EvaluadorLlamada.evaluar(100, soloLinea, listOf(seg("gaze_direction", 0, 80), seg("hand_to_face_adaptors", 0, 20)))!!
        assertEquals(EJE_MIRADA, peorMirada.peorEje)
        assertTrue(peorMirada.resumen.startsWith("Mirada:"), peorMirada.resumen)
        assertTrue(peorMirada.resumen.contains("cámara"))

        // Ambos en 0/10: empate → manda manos y cara (primero en la prioridad).
        val empate = EvaluadorLlamada.evaluar(100, soloLinea, listOf(seg("gaze_direction", 0, 100), seg("hand_to_face_adaptors", 0, 100)))!!
        assertEquals(EJE_MANOS_CARA, empate.peorEje)
        assertTrue(empate.resumen.startsWith("Manos y cara:"), empate.resumen)
    }

    @Test
    fun `las frases son 1 o 2 y no culpan a la persona`() {
        val ids = listOf("hand_to_face_adaptors", "gaze_direction", "torso_lean", "table_rest")
        ids.forEach { id ->
            val feedback = EvaluadorLlamada.evaluar(100, soloLinea, listOf(seg(id, 0, 100)))!!
            val frases = feedback.resumen.substringAfter(": ").split(Regex("(?<=\\.) ")).filter { it.isNotBlank() }
            assertTrue(frases.size in 1..2, "$id → ${feedback.resumen}")
            listOf("fallaste", "mal ", "error").forEach { assertTrue(it !in feedback.resumen.lowercase(), feedback.resumen) }
        }
    }

    @Test
    fun `sin segmentos usa los totales por accion del reporte tabulado`() {
        val resumen = ResumenLlamada(acciones = listOf(AccionLlamada("hand_to_face_adaptors", 2, 30_000, 20_000)))
        val manos = EvaluadorLlamada.evaluar(100, resumen, emptyList())!!.ejes.single { it.id == EJE_MANOS_CARA }
        assertEquals(30.0, manos.segundos)
        assertEquals(2, manos.score)
    }

    @Test
    fun `sin ninguna observacion no hay feedback`() {
        assertNull(EvaluadorLlamada.evaluar(120, ResumenLlamada(), emptyList()))
        assertNotNull(EvaluadorLlamada.evaluar(120, ResumenLlamada(), listOf(seg("table_rest", 0, 1))))
    }
}
