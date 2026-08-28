package data.repository.usuarios

import data.tables.usuarios.PasswordResetTable
import data.tables.usuarios.OauthAccountTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// Helper para ejecutar transacciones suspend
private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

/**
 * Repositorio para manejo de reseteo de contraseña.
 *
 * - createForEmail(email): crea un registro de reset si el correo existe.
 *   Si el correo NO corresponde a ningún usuario, retorna null.
 *
 * - consume(token, code): valida un reset (token + código) y lo marca como usado.
 *
 * - consumeByEmail(email, code): versión que valida por correo + código,
 *   útil cuando el cliente no maneja el token.
 */
class PasswordResetRepository(
    private val users: UserRepository = UserRepository(),
    private val db: Database? = null
) {
    data class ResetInfo(
        val token: UUID,
        val code: String,
        val userId: UUID
    )

    private val rng = SecureRandom()

    /**
     * Crea un reset para un email EXISTENTE.
     *
     * @return ResetInfo (token + code + userId) si el usuario existe,
     *         o null si no existe ningún usuario con ese correo.
     */
    suspend fun createForEmail(email: String): ResetInfo? = dbTx {
        val normalizedEmail = email.trim().lowercase()

        // Buscar usuario por correo
        val user = users.findByEmail(normalizedEmail) ?: return@dbTx null

        val token = UUID.randomUUID()
        val code  = (100000 + rng.nextInt(900000)).toString() // 6 dígitos
        val now   = Instant.now()
        val exp   = now.plus(15, ChronoUnit.MINUTES)

        PasswordResetTable.insert {
            it[PasswordResetTable.token]     = token
            it[PasswordResetTable.usuarioId] = user.id
            it[PasswordResetTable.code]      = code
            it[PasswordResetTable.issuedAt]  = now
            it[PasswordResetTable.expiresAt] = exp
            it[PasswordResetTable.used]      = false
        }

        ResetInfo(token, code, user.id)
    }

    /**
     * Verifica y CONSUME un reset válido (token + code).
     */
    suspend fun consume(token: UUID, code: String): UUID? = dbTx {
        val row = PasswordResetTable
            .selectAll()
            .where {
                (PasswordResetTable.token eq token) and
                (PasswordResetTable.code eq code) and
                (PasswordResetTable.used eq false) and
                (PasswordResetTable.expiresAt greater Instant.now())
            }
            .limit(1)
            .firstOrNull()
            ?: return@dbTx null

        PasswordResetTable.update({ PasswordResetTable.token eq token }) {
            it[used] = true
        }

        row[PasswordResetTable.usuarioId]
    }

    /**
     * Versión pensada para tu API actual:
     * valida por CORREO + CÓDIGO (sin token), no usado y no expirado.
     *
     * @return userId si está OK y marca como usado, o null si no es válido.
     */
    suspend fun consumeByEmail(email: String, code: String): UUID? = dbTx {
        val normalized = email.trim().lowercase()
        val user = users.findByEmail(normalized) ?: return@dbTx null

        val row = PasswordResetTable
            .selectAll()
            .where {
                (PasswordResetTable.usuarioId eq user.id) and
                (PasswordResetTable.code eq code) and
                (PasswordResetTable.used eq false) and
                (PasswordResetTable.expiresAt greater Instant.now())
            }
            .limit(1)
            .firstOrNull()
            ?: return@dbTx null

        PasswordResetTable.update({
            (PasswordResetTable.usuarioId eq user.id) and
            (PasswordResetTable.code eq code)
        }) {
            it[used] = true
        }

        row[PasswordResetTable.usuarioId]
    }
}
