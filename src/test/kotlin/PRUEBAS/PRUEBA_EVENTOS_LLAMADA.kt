package PRUEBAS

import MODELOS.EventoLlamada
import MODELOS.SegmentoEventoLlamada
import SERVICIOS.ServicioLlamada
import kotlin.test.Test
import kotlin.test.assertEquals

/** D46: la duración MÁXIMA por evento se persiste (segmento más largo; ni el último ni la suma). */
class PruebaEventosLlamada {
    private fun seg(id: String, inicio: Long, fin: Long) = SegmentoEventoLlamada(id, inicio, fin, 8.0)

    @Test
    fun `el maximo es el segmento mas largo, no el ultimo ni la suma`() {
        val eventos = ServicioLlamada.eventosDe(
            listOf(
                seg("toque_cara_apoyo", 0, 38_000),
                seg("toque_cara_apoyo", 50_000, 53_000),
                seg("arm_crossing", 10_000, 12_000)
            )
        )
        assertEquals(
            listOf(
                EventoLlamada("toque_cara_apoyo", 2, 41_000, 38_000),
                EventoLlamada("arm_crossing", 1, 2_000, 2_000)
            ),
            eventos
        )
    }

    @Test
    fun `sin segmentos no hay eventos`() {
        assertEquals(emptyList(), ServicioLlamada.eventosDe(emptyList()))
    }
}
