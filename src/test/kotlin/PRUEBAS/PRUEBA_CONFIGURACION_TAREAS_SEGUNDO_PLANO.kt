package PRUEBAS

import CONFIGURACION.tareasSegundoPlanoActivas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TAREAS_SEGUNDO_PLANO (D43): encendidas por defecto, solo "false" las apaga y nunca en Railway. */
class PruebaConfiguracionTareasSegundoPlano {
    private val local: (String) -> String? = { null }
    private val railway: (String) -> String? = { if (it == "RAILWAY_PROJECT_ID") "proyecto" else null }

    @Test
    fun `encendidas salvo con el literal false`() {
        assertTrue(tareasSegundoPlanoActivas(null, local))
        assertTrue(tareasSegundoPlanoActivas("", local))
        assertTrue(tareasSegundoPlanoActivas("true", local))
        assertTrue(tareasSegundoPlanoActivas("0", local))
        assertTrue(tareasSegundoPlanoActivas("no", local))
        assertFalse(tareasSegundoPlanoActivas("false", local))
        assertFalse(tareasSegundoPlanoActivas(" FALSE ", local))
    }

    @Test
    fun `en Railway apagarlas impide arrancar y encendidas no molesta`() {
        val error = assertFailsWith<IllegalStateException> { tareasSegundoPlanoActivas("false", railway) }
        assertTrue(error.message!!.contains("Railway"))
        assertTrue(tareasSegundoPlanoActivas(null, railway))
        assertTrue(tareasSegundoPlanoActivas("true", railway))
        assertFailsWith<IllegalStateException> { tareasSegundoPlanoActivas("false") { if (it == "RAILWAY_ENVIRONMENT_ID") "x" else null } }
    }

    /** Una fusión no debe volver a arrancar las tareas sin mirar la variable. */
    @Test
    fun `el arranque inicia las tareas solo con la condicion`() {
        val arranque = File("src/main/kotlin/Application.kt").readText()
        val condicion = arranque.indexOf("if (configuracion.tareasSegundoPlanoActivas)")
        val inicio = arranque.indexOf("dependencias.iniciarTareasSegundoPlano()")
        assertTrue(condicion >= 0, "Application.kt debe preguntar por tareasSegundoPlanoActivas")
        assertTrue(inicio > condicion, "iniciarTareasSegundoPlano() debe quedar dentro de la condición")
        assertTrue(arranque.indexOf("dependencias.iniciarTareasSegundoPlano()", inicio + 1) < 0, "se llama una sola vez")
    }
}
