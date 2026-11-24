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

    // ---------- Revisión adversarial de D40 ----------

    @Test
    fun `marca corta y palabra completa juntas quedan como una sola marca`() {
        assertEquals(
            "S: El servidor cayó. T: Restaurarlo antes del lunes. A: Revisé logs e hice rollback. R: Servicio arriba en 2 horas.",
            FormatoRespuestaIdeal.normalizar("S: Situación: El servidor cayó. T: Tarea: Restaurarlo antes del lunes. A: Acción: Revisé logs e hice rollback. R: Resultado: Servicio arriba en 2 horas.")
        )
    }

    @Test
    fun `negritas y vinetas de Markdown alrededor de las marcas se quitan`() {
        val esperado = "S: Hubo un incidente. T: Debía resolverlo. A: Hice rollback. R: Se restauró."
        assertEquals(esperado, FormatoRespuestaIdeal.normalizar("**Situación:** Hubo un incidente. **Tarea:** Debía resolverlo. **Acción:** Hice rollback. **Resultado:** Se restauró."))
        assertEquals(esperado, FormatoRespuestaIdeal.normalizar("**S:** Hubo un incidente. **T:** Debía resolverlo. **A:** Hice rollback. **R:** Se restauró."))
        assertEquals(
            "S: Hubo un incidente.\nT: Debía resolverlo.\nA: Hice rollback.\nR: Se restauró.",
            FormatoRespuestaIdeal.normalizar("- S: Hubo un incidente.\n- T: Debía resolverlo.\n- A: Hice rollback.\n- R: Se restauró.")
        )
    }

    @Test
    fun `variantes del encabezado se quitan y un comienzo normal no`() {
        assertEquals("S: a b c. T: d e f. A: g h i. R: j k l.", FormatoRespuestaIdeal.normalizar("Respuesta ideal (STAR): S: a b c. T: d e f. A: g h i. R: j k l."))
        assertEquals("S: a b c. T: d e f. A: g h i. R: j k l.", FormatoRespuestaIdeal.normalizar("**Respuesta ideal usando STAR:** S: a b c. T: d e f. A: g h i. R: j k l."))
        assertEquals("Propuesta de valor: algo.", FormatoRespuestaIdeal.normalizar("Propuesta de valor: algo."))
    }

    @Test
    fun `una R o S de mas dentro del texto no cuenta como STAR valido`() {
        assertFalse(FormatoRespuestaIdeal.tieneStar("S: Informes lentos. T: Acelerarlos. A: 1) Migré los informes a R: usé ggplot. 2) Automaticé el envío. R: Tiempo a la mitad."))
    }

    @Test
    fun `los numeros se quitan solo en los bloques con dos o mas pasos`() {
        assertEquals(
            "Hubo caída. Restaurar. Hice rollback 2) veces. Restaurado.",
            FormatoRespuestaIdeal.sinMarcas("S: Hubo caída. T: Restaurar. A: Hice rollback 2) veces. R: Restaurado.")
        )
    }
}

