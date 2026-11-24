package SERVICIOS

import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import ERRORES.ErrorValidacion
import ESQUEMAS.RespuestaRevisadaApp
import ESQUEMAS.ResultadoRevisionSubida
import MODELOS.EstadoPregunta
import MODELOS.FiltroPreguntas
import MODELOS.Pregunta
import MODELOS.RepositorioPregunta
import MODELOS.RepositorioRevisionTexto
import MODELOS.RevisionTexto
import MODELOS.TipoPregunta
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs

/** Cambia cuando cambia la fórmula del revisor (pesos, normalización, similitud...). Igual en la app. */
const val VERSION_REVISOR = 1

private const val PREGUNTAS_POR_DEFECTO = 20
private const val PREGUNTAS_MAXIMO = 50
private const val RESPUESTAS_MAXIMAS_POR_LOTE = 100
private const val LARGO_MAXIMO_ID_LOCAL = 64
private const val DIFERENCIA_PARIDAD = 0.01

/**
 * Descarga de preguntas abiertas para revisar sin conexión y subida de lo respondido (T3.13). Sin IA: el servidor repite
 * la revisión léxica con la misma fórmula de la app. Solo texto.
 */
class ServicioRevisionLocal(
    private val preguntas: RepositorioPregunta,
    private val revisiones: RepositorioRevisionTexto,
    private val configuracion: ConfiguracionRevisor = ConfiguracionRevisor()
) {

    /** Preguntas abiertas aprobadas, en orden aleatorio, con su respuesta ideal y su rúbrica. */
    suspend fun descargar(nivel: String?, categoria: String?, cantidad: Int?): List<Pregunta> {
        val total = cantidad ?: PREGUNTAS_POR_DEFECTO
        if (total !in 1..PREGUNTAS_MAXIMO) {
            throw ErrorValidacion("cantidad_invalida", "La cantidad debe estar entre 1 y $PREGUNTAS_MAXIMO")
        }
        val filtro = FiltroPreguntas(
            estado = EstadoPregunta.APROBADA,
            tipo = TipoPregunta.ABIERTA_TEXTO,
            categoria = categoria?.takeIf { it.isNotBlank() }?.let { leerCategoria(it) },
            nivel = nivel?.takeIf { it.isNotBlank() }?.let { leerNivel(it) }
        )
        return preguntas.listarAlAzar(filtro, total)
    }

    /**
     * Guarda las respuestas revisadas en el teléfono. Lo malformado rechaza el lote entero (400); una respuesta cuya
     * pregunta ya no existe se rechaza sola, para que la app no la reintente eternamente.
     */
    suspend fun subir(usuarioId: UUID, lote: List<RespuestaRevisadaApp>): List<ResultadoRevisionSubida> {
        if (lote.isEmpty()) throw ErrorValidacion("lote_vacio", "No hay respuestas que subir")
        if (lote.size > RESPUESTAS_MAXIMAS_POR_LOTE) {
            throw ErrorValidacion("lote_demasiado_grande", "Se pueden subir hasta $RESPUESTAS_MAXIMAS_POR_LOTE respuestas por vez")
        }
        lote.forEach { validar(it) }
        if (lote.map { it.idLocal }.distinct().size != lote.size) {
            throw ErrorValidacion("id_local_repetido", "El lote trae el mismo idLocal más de una vez")
        }
        return lote.map { subirUna(usuarioId, it) }
    }

    private suspend fun subirUna(usuarioId: UUID, r: RespuestaRevisadaApp): ResultadoRevisionSubida {
        val previa = revisiones.buscar(usuarioId, r.idLocal)
        if (previa != null) return resultado(r, "ya_recibida", previa.scoreServidor, previa.bandaServidor)

        val preguntaId = UUID.fromString(r.preguntaId)
        val pregunta = preguntas.buscarPorId(preguntaId)
        if (pregunta == null || pregunta.estado != EstadoPregunta.APROBADA || pregunta.tipo != TipoPregunta.ABIERTA_TEXTO) {
            return ResultadoRevisionSubida(r.idLocal, "rechazada", null, null, null, "La pregunta ya no está disponible para revisión")
        }
        val revision = RevisorTextoLibre.revisar(r.texto, pregunta.respuestaIdeal, RubricaRevision.desdeJson(pregunta.rubrica), configuracion)
        val guardada = revisiones.registrar(
            usuarioId,
            RevisionTexto(
                idLocal = r.idLocal, preguntaId = preguntaId, texto = r.texto,
                scoreDispositivo = r.scoreLocal, bandaDispositivo = r.bandaLocal,
                scoreServidor = revision.score, bandaServidor = revision.banda.name, versionRevisor = VERSION_REVISOR,
                revisadoEn = r.revisadoEnMs?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }
            )
        )
        return resultado(r, if (guardada) "guardada" else "ya_recibida", revision.score, revision.banda.name)
    }

    private fun resultado(r: RespuestaRevisadaApp, estado: String, score: Double, banda: String) =
        ResultadoRevisionSubida(
            idLocal = r.idLocal, estado = estado, scoreServidor = score, bandaServidor = banda,
            coincide = r.scoreLocal?.let { abs(it - score) < DIFERENCIA_PARIDAD && (r.bandaLocal == null || r.bandaLocal == banda) },
            motivo = null
        )

    private fun validar(r: RespuestaRevisadaApp) {
        if (r.idLocal.isBlank() || r.idLocal.length > LARGO_MAXIMO_ID_LOCAL) {
            throw ErrorValidacion("id_local_invalido", "idLocal debe tener entre 1 y $LARGO_MAXIMO_ID_LOCAL caracteres")
        }
        runCatching { UUID.fromString(r.preguntaId) }.getOrElse { throw ErrorValidacion("pregunta_invalida", "preguntaId no es un UUID") }
        if (r.texto.isBlank()) throw ErrorValidacion("texto_vacio", "La respuesta no puede estar vacía")
        if (r.texto.length > LARGO_MAXIMO_RESPUESTA_ENTREVISTA) {
            throw ErrorValidacion("texto_demasiado_largo", "La respuesta no puede superar $LARGO_MAXIMO_RESPUESTA_ENTREVISTA caracteres")
        }
        val local = r.scoreLocal
        if (local != null && (local.isNaN() || local !in 0.0..100.0)) {
            throw ErrorValidacion("puntaje_invalido", "scoreLocal debe estar entre 0 y 100")
        }
    }
}
