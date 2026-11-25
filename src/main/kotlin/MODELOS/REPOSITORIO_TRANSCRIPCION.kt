package MODELOS

import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

interface RepositorioTranscripcion {
    /**
     * Una fila por (usuario, sesión, pregunta). Reemplaza la anterior, salvo que lo guardado sea final y lo que llega sea
     * parcial: entonces conserva lo guardado y devuelve creada = false.
     */
    suspend fun guardar(usuarioId: UUID, nueva: TranscripcionRespuesta): TranscripcionGuardada

    /** En el orden en que se respondieron. Solo las del usuario. */
    suspend fun listarDeSesion(usuarioId: UUID, sesionId: UUID): List<TranscripcionRespuesta>
}

class RepositorioTranscripcionExposed : RepositorioTranscripcion {

    override suspend fun guardar(usuarioId: UUID, nueva: TranscripcionRespuesta): TranscripcionGuardada = try {
        guardarUnaVez(usuarioId, nueva)
    } catch (e: ExposedSQLException) {
        // Otro envío de la misma pregunta entró entre la lectura y la inserción: se reintenta y ahora la ve.
        if (e.sqlState != SQLSTATE_VALOR_DUPLICADO) throw e
        guardarUnaVez(usuarioId, nueva)
    }

    private suspend fun guardarUnaVez(usuarioId: UUID, nueva: TranscripcionRespuesta): TranscripcionGuardada = transaccion {
        val existente = TablaTranscripcionRespuesta.selectAll().where { delaPregunta(usuarioId, nueva) }
            .limit(1).firstOrNull()?.aTranscripcion()
        when {
            existente == null -> {
                TablaTranscripcionRespuesta.insert {
                    it[id] = nueva.id
                    it[TablaTranscripcionRespuesta.usuarioId] = usuarioId
                    it[sesionId] = nueva.sesionId
                    it[preguntaId] = nueva.preguntaId
                    it[inicioMs] = nueva.inicioMs
                    it[finMs] = nueva.finMs
                    it[texto] = nueva.texto
                    it[idioma] = nueva.idioma
                    it[confianza] = nueva.confianza
                    it[parcial] = nueva.parcial
                    it[recibidaEn] = Instant.now()
                }
                TranscripcionGuardada(nueva, true)
            }
            !existente.parcial && nueva.parcial -> TranscripcionGuardada(existente, false)
            else -> {
                TablaTranscripcionRespuesta.update({ delaPregunta(usuarioId, nueva) }) {
                    it[inicioMs] = nueva.inicioMs
                    it[finMs] = nueva.finMs
                    it[texto] = nueva.texto
                    it[idioma] = nueva.idioma
                    it[confianza] = nueva.confianza
                    it[parcial] = nueva.parcial
                    it[recibidaEn] = Instant.now()
                }
                TranscripcionGuardada(nueva.copy(id = existente.id), false)
            }
        }
    }

    override suspend fun listarDeSesion(usuarioId: UUID, sesionId: UUID): List<TranscripcionRespuesta> = transaccion {
        TablaTranscripcionRespuesta.selectAll()
            .where { (TablaTranscripcionRespuesta.usuarioId eq usuarioId) and (TablaTranscripcionRespuesta.sesionId eq sesionId) }
            .orderBy(TablaTranscripcionRespuesta.inicioMs to SortOrder.ASC, TablaTranscripcionRespuesta.id to SortOrder.ASC)
            .map { it.aTranscripcion() }
    }

    private fun delaPregunta(usuarioId: UUID, t: TranscripcionRespuesta) =
        (TablaTranscripcionRespuesta.usuarioId eq usuarioId) and (TablaTranscripcionRespuesta.sesionId eq t.sesionId) and
            (TablaTranscripcionRespuesta.preguntaId eq t.preguntaId)

    private fun ResultRow.aTranscripcion() = TranscripcionRespuesta(
        id = this[TablaTranscripcionRespuesta.id],
        sesionId = this[TablaTranscripcionRespuesta.sesionId],
        preguntaId = this[TablaTranscripcionRespuesta.preguntaId],
        inicioMs = this[TablaTranscripcionRespuesta.inicioMs],
        finMs = this[TablaTranscripcionRespuesta.finMs],
        texto = this[TablaTranscripcionRespuesta.texto],
        idioma = this[TablaTranscripcionRespuesta.idioma],
        confianza = this[TablaTranscripcionRespuesta.confianza],
        parcial = this[TablaTranscripcionRespuesta.parcial]
    )
}
