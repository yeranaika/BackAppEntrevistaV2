package data.repository.auth

import data.tables.auth.RecoveryCodeTable
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random

class RecoveryCodeRepository(private val db: Database) {

    /**
     * Genera un código de 6 dígitos aleatorio
     */
    private fun generateCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    /**
     * Crea un nuevo código de recuperación para un usuario
     * Retorna el código generado o null si el usuario no existe
     */
    suspend fun createRecoveryCode(correo: String): String? = newSuspendedTransaction(db = db) {
        // Buscar usuario por correo
        val usuario = UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq correo }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction null

        val usuarioId = usuario[UsuarioTable.usuarioId]

        // Invalidar códigos anteriores del mismo usuario (marcarlos como usados)
        RecoveryCodeTable.update({
            (RecoveryCodeTable.usuarioId eq usuarioId) and
            (RecoveryCodeTable.usado eq false)
        }) { st ->
            st[usado] = true
        }

        // Generar nuevo código
        val codigo = generateCode()
        val now = OffsetDateTime.now()
        val expiration = now.plusMinutes(15)

        // Insertar nuevo código
        RecoveryCodeTable.insert { st ->
            st[id] = UUID.randomUUID()
            st[RecoveryCodeTable.usuarioId] = usuarioId
            st[RecoveryCodeTable.codigo] = codigo
            st[fechaExpiracion] = expiration
            st[usado] = false
            st[fechaCreacion] = now
        }

        codigo
    }

    /**
     * Valida un código de recuperación
     * Retorna el ID del usuario si el código es válido, null en caso contrario
     */
    suspend fun validateCode(correo: String, codigo: String): UUID? = newSuspendedTransaction(db = db) {
        // Buscar usuario por correo
        val usuario = UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq correo }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction null

        val usuarioId = usuario[UsuarioTable.usuarioId]
        val now = OffsetDateTime.now()

        // Buscar código válido
        RecoveryCodeTable
            .selectAll()
            .where {
                (RecoveryCodeTable.usuarioId eq usuarioId) and
                (RecoveryCodeTable.codigo eq codigo) and
                (RecoveryCodeTable.usado eq false) and
                (RecoveryCodeTable.fechaExpiracion greater now)
            }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction null

        usuarioId
    }

    /**
     * Marca un código como usado
     */
    suspend fun markCodeAsUsed(correo: String, codigo: String): Boolean = newSuspendedTransaction(db = db) {
        // Buscar usuario por correo
        val usuario = UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq correo }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction false

        val usuarioId = usuario[UsuarioTable.usuarioId]

        val updated = RecoveryCodeTable.update({
            (RecoveryCodeTable.usuarioId eq usuarioId) and
            (RecoveryCodeTable.codigo eq codigo) and
            (RecoveryCodeTable.usado eq false)
        }) { st ->
            st[usado] = true
        }

        updated > 0
    }

    /**
     * Limpia códigos expirados (tarea de mantenimiento)
     */
    suspend fun cleanExpiredCodes(): Int = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now()

        RecoveryCodeTable.deleteWhere {
            (fechaExpiracion less now) or (usado eq true)
        }
    }
}
