package SERVICIOS

import CONFIGURACION.DURACION_MAXIMA_RESPUESTA_MS
import CONFIGURACION.LARGO_MAXIMO_IDIOMA
import CONFIGURACION.LARGO_MAXIMO_TRANSCRIPCION
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudTranscripcion
import MODELOS.RepositorioSesionEntrevista
import MODELOS.RepositorioTranscripcion
import MODELOS.TranscripcionGuardada
import MODELOS.TranscripcionRespuesta
import java.util.UUID

/**
 * Transcripción por pregunta de la llamada (VOZ-3/VOZ-4). Llega como texto, ya reconocido en el teléfono: el servidor no
 * recibe ni guarda audio. Solo el dueño de la sesión la guarda y la ve, y la pregunta debe ser una de las que se le sirvieron.
 */
class ServicioTranscripcion(
    private val transcripciones: RepositorioTranscripcion,
    private val sesiones: RepositorioSesionEntrevista
) {
    suspend fun guardar(usuarioId: UUID, solicitud: SolicitudTranscripcion): TranscripcionGuardada {
        val sesionId = leerUuid(solicitud.sesionId, "sesionId")
        val preguntaId = leerUuid(solicitud.preguntaId, "preguntaId")
        val texto = validar(solicitud)
        val sesion = sesiones.buscar(sesionId)?.takeIf { it.usuarioId == usuarioId }
            ?: throw ErrorNoEncontrado("sesion_no_encontrada", "La sesión no existe")
        if (sesion.preguntas.none { it.preguntaId == preguntaId }) {
            throw ErrorValidacion("pregunta_ajena", "La pregunta no es parte de esa sesión")
        }
        return transcripciones.guardar(
            usuarioId,
            TranscripcionRespuesta(
                id = UUID.randomUUID(), sesionId = sesionId, preguntaId = preguntaId, inicioMs = solicitud.inicioMs,
                finMs = solicitud.finMs, texto = texto, idioma = solicitud.idioma.trim().lowercase(),
                confianza = solicitud.confianza, parcial = solicitud.parcial
            )
        )
    }

    suspend fun deSesion(usuarioId: UUID, sesionId: UUID): List<TranscripcionRespuesta> {
        sesiones.buscar(sesionId)?.takeIf { it.usuarioId == usuarioId }
            ?: throw ErrorNoEncontrado("sesion_no_encontrada", "La sesión no existe")
        return transcripciones.listarDeSesion(usuarioId, sesionId)
    }

    private fun leerUuid(valor: String, campo: String): UUID =
        runCatching { UUID.fromString(valor) }.getOrElse { throw ErrorValidacion("id_invalido", "$campo no es un UUID válido") }

    /** Devuelve el texto sin espacios sobrantes. */
    private fun validar(s: SolicitudTranscripcion): String {
        val texto = s.texto.trim()
        if (texto.isEmpty() || texto.length > LARGO_MAXIMO_TRANSCRIPCION) {
            throw ErrorValidacion("texto_invalido", "El texto debe tener entre 1 y $LARGO_MAXIMO_TRANSCRIPCION caracteres")
        }
        if (s.inicioMs < 0 || s.finMs < s.inicioMs || s.finMs - s.inicioMs > DURACION_MAXIMA_RESPUESTA_MS) {
            throw ErrorValidacion("tiempos_invalidos", "inicioMs y finMs deben ser crecientes y la respuesta durar menos de una hora")
        }
        if (s.idioma.isBlank() || s.idioma.trim().length > LARGO_MAXIMO_IDIOMA) {
            throw ErrorValidacion("idioma_invalido", "El idioma debe tener entre 1 y $LARGO_MAXIMO_IDIOMA caracteres")
        }
        if (s.confianza != null && (s.confianza.isNaN() || s.confianza < 0f || s.confianza > 1f)) {
            throw ErrorValidacion("confianza_invalida", "La confianza debe estar entre 0 y 1")
        }
        return texto
    }
}
