package SERVICIOS

import MODELOS.SesionEntrevista

/**
 * Lo que ocurre después de finalizar una entrevista (en segundo plano, no bloquea la respuesta HTTP).
 * Lo implementa ServicioReporteEntrevista (genera el reporte de feedback).
 */
fun interface ProcesadorEntrevistaFinalizada {
    suspend fun procesar(sesion: SesionEntrevista)
}
