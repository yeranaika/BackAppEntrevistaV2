package PRUEBAS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PruebaServicioRecordatorio {
    private val servicio = SistemaPrueba().recordatorio
    private val usuario = UUID.randomUUID()

    @Test
    fun `guarda los siete dias tal como los envia Android y en orden de la semana`() = runBlocking<Unit> {
        val guardado = servicio.guardar(usuario, listOf("DOM", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB"), "20:30", "entrevista", true)
        assertEquals(listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM"), guardado.diasSemana)
        assertEquals(guardado, servicio.obtener(usuario))
    }

    @Test
    fun `acepta nombres completos y los guarda como codigo`() = runBlocking<Unit> {
        val guardado = servicio.guardar(usuario, listOf("lunes", "Miércoles", "LUN"), "07:05", "test", false)
        assertEquals(listOf("LUN", "MIE"), guardado.diasSemana)
    }

    @Test
    fun `valida dias, hora y tipo de practica`() {
        fallaCon<ErrorValidacion>("dias_requeridos") { servicio.guardar(usuario, emptyList(), "20:30", "test", true) }
        fallaCon<ErrorValidacion>("dia_invalido") { servicio.guardar(usuario, listOf("FERIADO"), "20:30", "test", true) }
        fallaCon<ErrorValidacion>("hora_invalida") { servicio.guardar(usuario, listOf("LUN"), "25:00", "test", true) }
        fallaCon<ErrorValidacion>("hora_invalida") { servicio.guardar(usuario, listOf("LUN"), "8:30 pm", "test", true) }
        fallaCon<ErrorValidacion>("tipo_practica_invalido") { servicio.guardar(usuario, listOf("LUN"), "20:30", "x".repeat(33), true) }
    }

    @Test
    fun `sin preferencias responde 404`() {
        fallaCon<ErrorNoEncontrado>("recordatorio_no_configurado") { servicio.obtener(usuario) }
    }
}
