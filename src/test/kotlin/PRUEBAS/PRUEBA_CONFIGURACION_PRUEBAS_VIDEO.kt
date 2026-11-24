package PRUEBAS

import CONFIGURACION.pruebasVideoHabilitadas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HABILITAR_PRUEBAS_VIDEO (D39): apagada por defecto, solo "true" la activa y nunca en Railway. */
class PruebaConfiguracionPruebasVideo {
    private val local: (String) -> String? = { null }
    private val railway: (String) -> String? = { if (it == "RAILWAY_PROJECT_ID") "proyecto" else null }

    @Test
    fun `apagada salvo con el literal true`() {
        assertFalse(pruebasVideoHabilitadas(null, local))
        assertFalse(pruebasVideoHabilitadas("", local))
        assertFalse(pruebasVideoHabilitadas("false", local))
        assertFalse(pruebasVideoHabilitadas("1", local))
        assertFalse(pruebasVideoHabilitadas("yes", local))
        assertTrue(pruebasVideoHabilitadas("true", local))
        assertTrue(pruebasVideoHabilitadas(" TRUE ", local))
    }

    @Test
    fun `en Railway activarla impide arrancar y apagada no molesta`() {
        val error = assertFailsWith<IllegalStateException> { pruebasVideoHabilitadas("true", railway) }
        assertTrue(error.message!!.contains("Railway"))
        assertFalse(pruebasVideoHabilitadas(null, railway))
        assertFailsWith<IllegalStateException> { pruebasVideoHabilitadas("true") { if (it == "RAILWAY_ENVIRONMENT_ID") "x" else null } }
    }

    /** Una fusión con `main` no debe volver a montar las rutas sin condición (pasó una vez: CONFIGURACION_RUTAS.kt). */
    @Test
    fun `las rutas de produccion montan el video de prueba solo con la condicion`() {
        val rutas = File("src/main/kotlin/CONFIGURACION/CONFIGURACION_RUTAS.kt").readText()
        assertEquals(0, Regex("""controladorPruebaVideo\(""").findAll(rutas).count(), "solo vía controladorPruebaVideoSiHabilitado")
        assertTrue(rutas.contains("controladorPruebaVideoSiHabilitado(pruebasVideo)"))
    }
}
