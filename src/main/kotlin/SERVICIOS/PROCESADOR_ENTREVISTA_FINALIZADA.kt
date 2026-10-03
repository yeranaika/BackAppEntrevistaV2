package SERVICIOS

import MODELOS.SesionEntrevista
import org.slf4j.LoggerFactory

/**
 * Lo que ocurre después de finalizar una entrevista (en segundo plano, no bloquea la respuesta HTTP).
 * La Fase 7 lo implementa generando el reporte de feedback.
 */
fun interface ProcesadorEntrevistaFinalizada {
    suspend fun procesar(sesion: SesionEntrevista)
}

/** Mientras no exista el reporte (Fase 7) solo se deja constancia. */
class ProcesadorEntrevistaSinReporte : ProcesadorEntrevistaFinalizada {
    private val log = LoggerFactory.getLogger(ProcesadorEntrevistaSinReporte::class.java)

    override suspend fun procesar(sesion: SesionEntrevista) {
        log.info("Entrevista {} finalizada con {}/{} respuestas; reporte pendiente", sesion.id, sesion.respondidas, sesion.preguntas.size)
    }
}
