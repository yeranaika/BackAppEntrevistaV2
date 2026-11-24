package PRUEBAS

import SERVICIOS.FormatoRespuestaIdeal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** El formato de la respuesta ideal que ordena la app (D40) y cómo se limpia para comparar. */
class PruebaFormatoRespuestaIdeal {
    private val star = "S: Sistema caído en producción. T: Restaurarlo rápido. A: 1) Revisé logs. 2) Hice rollback. R: Sistema restaurado."

    @Test
    fun `reconoce STAR completo y en orden como la app`() {
        assertTrue(FormatoRespuestaIdeal.tieneStar(star))
        assertFalse(FormatoRespuestaIdeal.tieneStar("S: algo. A: otra cosa. T: tarde. R: fin."), "fuera de orden")
        assertFalse(FormatoRespuestaIdeal.tieneStar("S: algo. T: tarea. A: acción."), "le falta R")
        assertFalse(FormatoRespuestaIdeal.tieneStar("LAS: algo. HT: tarea. PA: acción. ER: fin."), "letras pegadas a otra palabra")
    }

    @Test
    fun `normalizar quita el encabezado y cambia las palabras STAR por letras`() {
        assertEquals(
            "S: Hubo un incidente. T: Debía resolverlo. A: Hice rollback. R: Se restauró.",
            FormatoRespuestaIdeal.normalizar("Respuesta STAR ideal: Situación: Hubo un incidente. Tarea: Debía resolverlo. Acción: Hice rollback. Resultado: Se restauró.")
        )
        assertEquals("usar Redis como caché.", FormatoRespuestaIdeal.normalizar("Propuesta ideal: usar Redis como caché."))
        val tecnica = "La tarea: indexar. El resultado: rápido."
        assertEquals(tecnica, FormatoRespuestaIdeal.normalizar(tecnica), "sin las cuatro partes en orden no toca nada")
    }

    @Test
    fun `sin marcas deja solo el contenido para comparar`() {
        assertEquals(
            "Sistema caído en producción. Restaurarlo rápido. Revisé logs. Hice rollback. Sistema restaurado.",
            FormatoRespuestaIdeal.sinMarcas(star)
        )
        assertEquals("Resumen. Paso uno. Paso dos.", FormatoRespuestaIdeal.sinMarcas("Resumen. 1) Paso uno. 2) Paso dos."))
        assertEquals("Usa la versión 2) del contrato.", FormatoRespuestaIdeal.sinMarcas("Usa la versión 2) del contrato."), "un solo número no es una lista")
        assertEquals("clave A: valor", FormatoRespuestaIdeal.sinMarcas("clave A: valor"), "una marca suelta no es STAR")
    }
}
