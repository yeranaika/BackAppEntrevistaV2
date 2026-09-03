package routes.legal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LegalRoutesTest {

    @Test
    fun testDocumentosLegalesExistenYNoEstanVacios() {
        val eulaFile = File("docs/legal/EULA.md")
        assertTrue(eulaFile.exists(), "El archivo EULA.md debe existir en docs/legal/")
        assertTrue(eulaFile.readText().contains("ACUERDO DE LICENCIA DE USUARIO FINAL"), "EULA.md debe contener el título correspondiente")

        val terminosFile = File("docs/legal/TERMINOS_DE_SERVICIO.md")
        assertTrue(terminosFile.exists(), "El archivo TERMINOS_DE_SERVICIO.md debe existir en docs/legal/")
        assertTrue(terminosFile.readText().contains("TÉRMINOS Y CONDICIONES"), "TERMINOS_DE_SERVICIO.md debe contener los términos")

        val privacidadFile = File("docs/legal/POLITICA_DE_PRIVACIDAD.md")
        assertTrue(privacidadFile.exists(), "El archivo POLITICA_DE_PRIVACIDAD.md debe existir en docs/legal/")
        assertTrue(privacidadFile.readText().contains("POLÍTICA DE PRIVACIDAD"), "POLITICA_DE_PRIVACIDAD.md debe contener la política")
    }
}
