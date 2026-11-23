package SERVICIOS

import CONFIGURACION.TAMANO_PAGINA_ENTREVISTAS_MAXIMO
import CONFIGURACION.TAMANO_PAGINA_ENTREVISTAS_POR_DEFECTO
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistrarLlamada
import MODELOS.AccionLlamada
import MODELOS.Llamada
import MODELOS.PaginaLlamadas
import MODELOS.RepositorioLlamada
import MODELOS.ResumenLlamada
import MODELOS.TramoLlamada
import java.time.Instant
import java.util.UUID

private const val LARGO_MAXIMO_ID_LOCAL = 64
private const val DURACION_MAXIMA_SEG = 24 * 60 * 60
private const val PREGUNTAS_MAXIMAS = 1_000
private const val ELEMENTOS_MAXIMOS_RESUMEN = 5_000

/** Resultado de registrar: [creada] es false cuando era un reenvío (la llamada ya estaba). */
data class LlamadaRegistrada(val llamada: Llamada, val creada: Boolean)

/**
 * Historial de llamadas en vivo (T3.15). La app manda la llamada al cerrarla (o cuando vuelve la red); si trae `sesionId`
 * y no trae resumen, se deriva del reporte tabulado de esa sesión (T3.10). Solo el dueño la ve.
 */
class ServicioLlamada(
    private val llamadas: RepositorioLlamada,
    private val simulacion: ServicioSimulacion
) {
    suspend fun registrar(usuarioId: UUID, solicitud: SolicitudRegistrarLlamada): LlamadaRegistrada {
        validar(solicitud)
        llamadas.buscarPorIdLocal(usuarioId, solicitud.idLocal)?.let { return LlamadaRegistrada(it, false) }
        val sesionId = solicitud.sesionId?.let(::leerSesion)
        val llamada = Llamada(
            id = UUID.randomUUID(), idLocal = solicitud.idLocal, sesionId = sesionId,
            fechaInicio = Instant.ofEpochMilli(solicitud.fechaInicioMs),
            duracionSeg = solicitud.duracionSeg, preguntasCnt = solicitud.preguntasCnt,
            resumen = resumenDe(usuarioId, sesionId, solicitud)
        )
        if (llamadas.registrar(usuarioId, llamada)) return LlamadaRegistrada(llamada, true)
        // Perdió la carrera contra otro envío de la misma llamada: devolver la que quedó.
        val existente = llamadas.buscarPorIdLocal(usuarioId, solicitud.idLocal) ?: llamada
        return LlamadaRegistrada(existente, false)
    }

    suspend fun historial(usuarioId: UUID, pagina: Int?, tamano: Int?): PaginaLlamadas {
        val numeroPagina = pagina ?: 1
        val tamanoPagina = tamano ?: TAMANO_PAGINA_ENTREVISTAS_POR_DEFECTO
        if (numeroPagina < 1) throw ErrorValidacion("pagina_invalida", "La página empieza en 1")
        if (tamanoPagina !in 1..TAMANO_PAGINA_ENTREVISTAS_MAXIMO) {
            throw ErrorValidacion("tamano_invalido", "El tamaño de página debe estar entre 1 y $TAMANO_PAGINA_ENTREVISTAS_MAXIMO")
        }
        return llamadas.listarDeUsuario(usuarioId, numeroPagina, tamanoPagina)
    }

    suspend fun detalle(usuarioId: UUID, id: UUID): Llamada =
        llamadas.buscar(usuarioId, id) ?: throw ErrorNoEncontrado("llamada_no_encontrada", "La llamada no existe")

    private suspend fun resumenDe(usuarioId: UUID, sesionId: UUID?, s: SolicitudRegistrarLlamada): ResumenLlamada {
        s.resumen?.let { return ResumenLlamada(it.acciones, it.linea) }
        if (sesionId == null) return ResumenLlamada()
        val reporte = simulacion.reporte(usuarioId, sesionId)
        return ResumenLlamada(
            acciones = reporte.resumen.map { AccionLlamada(it.accion, it.veces, it.totalMs, it.maximoMs) },
            linea = reporte.tramos.map { TramoLlamada(it.inicioMs, it.finMs, it.accion) }
        )
    }

    private fun leerSesion(texto: String): UUID =
        runCatching { UUID.fromString(texto) }.getOrElse { throw ErrorValidacion("id_invalido", "sesionId no es un UUID válido") }

    private fun validar(s: SolicitudRegistrarLlamada) {
        if (s.idLocal.isBlank() || s.idLocal.length > LARGO_MAXIMO_ID_LOCAL) {
            throw ErrorValidacion("id_local_invalido", "idLocal debe tener entre 1 y $LARGO_MAXIMO_ID_LOCAL caracteres")
        }
        if (s.fechaInicioMs <= 0) throw ErrorValidacion("fecha_invalida", "fechaInicioMs debe ser una fecha válida")
        if (s.duracionSeg !in 0..DURACION_MAXIMA_SEG) {
            throw ErrorValidacion("duracion_invalida", "duracionSeg debe estar entre 0 y $DURACION_MAXIMA_SEG")
        }
        if (s.preguntasCnt !in 0..PREGUNTAS_MAXIMAS) {
            throw ErrorValidacion("preguntas_invalidas", "preguntasCnt debe estar entre 0 y $PREGUNTAS_MAXIMAS")
        }
        val resumen = s.resumen ?: return
        if (resumen.linea.size > ELEMENTOS_MAXIMOS_RESUMEN || resumen.acciones.size > ELEMENTOS_MAXIMOS_RESUMEN) {
            throw ErrorValidacion("resumen_demasiado_grande", "El resumen no puede superar $ELEMENTOS_MAXIMOS_RESUMEN elementos")
        }
        val malo = resumen.acciones.any { it.veces < 0 || it.totalMs < 0 || it.maximoMs < 0 } ||
            resumen.linea.any { it.inicioMs < 0 || it.finMs < it.inicioMs }
        if (malo) throw ErrorValidacion("resumen_invalido", "El resumen trae tiempos o conteos negativos")
    }
}
