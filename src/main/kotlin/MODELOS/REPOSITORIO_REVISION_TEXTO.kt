package MODELOS

import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/** Una respuesta de texto libre revisada: lo que dijo el teléfono y lo que calculó el servidor. */
data class RevisionTexto(
    val idLocal: String,
    val preguntaId: UUID?,
    val texto: String,
    val scoreDispositivo: Double?,
    val bandaDispositivo: String?,
    val scoreServidor: Double,
    val bandaServidor: String,
    val versionRevisor: Int,
    val revisadoEn: OffsetDateTime?
)

interface RepositorioRevisionTexto {
    /** Guarda la revisión. Idempotente por (usuario, idLocal): si ya estaba no la toca y devuelve false. */
    suspend fun registrar(usuarioId: UUID, revision: RevisionTexto): Boolean

    suspend fun buscar(usuarioId: UUID, idLocal: String): RevisionTexto?
}

class RepositorioRevisionTextoExposed : RepositorioRevisionTexto {

    override suspend fun registrar(usuarioId: UUID, revision: RevisionTexto): Boolean {
        if (buscar(usuarioId, revision.idLocal) != null) return false
        return try {
            transaccion {
                TablaRevisionTexto.insert {
                    it[id] = UUID.randomUUID()
                    it[TablaRevisionTexto.usuarioId] = usuarioId
                    it[idLocal] = revision.idLocal
                    it[preguntaId] = revision.preguntaId
                    it[texto] = revision.texto
                    it[scoreDispositivo] = revision.scoreDispositivo?.let { v -> BigDecimal.valueOf(v) }
                    it[bandaDispositivo] = revision.bandaDispositivo
                    it[scoreServidor] = BigDecimal.valueOf(revision.scoreServidor)
                    it[bandaServidor] = revision.bandaServidor
                    it[versionRevisor] = revision.versionRevisor.toShort()
                    it[revisadoEn] = revision.revisadoEn
                    it[recibidoEn] = OffsetDateTime.now()
                }
                true
            }
        } catch (e: ExposedSQLException) {
            // Otro envío del mismo lote entró entre la lectura y la inserción: es un reenvío, no un error.
            if (e.sqlState != SQLSTATE_VALOR_DUPLICADO) throw e
            false
        }
    }

    override suspend fun buscar(usuarioId: UUID, idLocal: String): RevisionTexto? = transaccion {
        TablaRevisionTexto.selectAll()
            .where { (TablaRevisionTexto.usuarioId eq usuarioId) and (TablaRevisionTexto.idLocal eq idLocal) }
            .limit(1).firstOrNull()?.aRevision()
    }

    private fun ResultRow.aRevision() = RevisionTexto(
        idLocal = this[TablaRevisionTexto.idLocal],
        preguntaId = this[TablaRevisionTexto.preguntaId],
        texto = this[TablaRevisionTexto.texto],
        scoreDispositivo = this[TablaRevisionTexto.scoreDispositivo]?.toDouble(),
        bandaDispositivo = this[TablaRevisionTexto.bandaDispositivo],
        scoreServidor = this[TablaRevisionTexto.scoreServidor].toDouble(),
        bandaServidor = this[TablaRevisionTexto.bandaServidor],
        versionRevisor = this[TablaRevisionTexto.versionRevisor].toInt(),
        revisadoEn = this[TablaRevisionTexto.revisadoEn]
    )
}
