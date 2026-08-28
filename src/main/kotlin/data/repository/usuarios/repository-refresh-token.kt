package data.repository.usuarios

// tabla refresh_token
import data.tables.usuarios.RefreshTokenTable

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and                // ✅ ESTE es el correcto
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

data class RefreshTokenRow(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revoked: Boolean
)

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class RefreshTokenRepository {

    suspend fun insert(
        userId: UUID,
        tokenHash: String,
        issuedAt: Instant,
        expiresAt: Instant
    ): UUID = dbTx {
        val rid = UUID.randomUUID()                      // ✅ generamos el ID
        RefreshTokenTable.insert {
            it[refreshId]  = rid
            it[usuarioId]  = userId
            it[RefreshTokenTable.tokenHash] = tokenHash
            it[RefreshTokenTable.issuedAt]  = issuedAt
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[revoked]    = false
        }
        rid
    }

    suspend fun findActiveByHash(
        hash: String,
        now: Instant = Instant.now()
    ): RefreshTokenRow? = dbTx {
        RefreshTokenTable
            .selectAll()
            .where {
                (RefreshTokenTable.tokenHash eq hash) and
                (RefreshTokenTable.revoked eq false) and
                (RefreshTokenTable.expiresAt greater now)
            }
            .limit(1)
            .firstOrNull()
            ?.toRow()
    }

    suspend fun revoke(id: UUID): Int = dbTx {
        RefreshTokenTable.update(where = { RefreshTokenTable.refreshId eq id }) {
            it[revoked] = true
        }
    }

    suspend fun revokeAllForUser(userId: UUID): Int = dbTx {
        RefreshTokenTable.update(where = { RefreshTokenTable.usuarioId eq userId }) {
            it[revoked] = true
        }
    }

    private fun ResultRow.toRow() = RefreshTokenRow(
        id        = this[RefreshTokenTable.refreshId],
        userId    = this[RefreshTokenTable.usuarioId],
        tokenHash = this[RefreshTokenTable.tokenHash],
        issuedAt  = this[RefreshTokenTable.issuedAt],
        expiresAt = this[RefreshTokenTable.expiresAt],
        revoked   = this[RefreshTokenTable.revoked]
    )
}
