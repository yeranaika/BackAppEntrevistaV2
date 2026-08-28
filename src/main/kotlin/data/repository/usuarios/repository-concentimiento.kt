// src/main/kotlin/data/repository/repository-consentimiento.kt
package data.repository.usuarios

import data.tables.usuarios.ConsentimientoTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

class ConsentimientoRepository {

    private suspend fun <T> tx(block: Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(
        usuarioId: UUID,
        version: String,
        alcances: Map<String, Boolean>
    ): ResultRow = tx {
        // Primero, revocar cualquier consentimiento activo previo
        ConsentimientoTable.update(
            where = {
                (ConsentimientoTable.usuarioId eq usuarioId) and
                ConsentimientoTable.fechaRevocado.isNull()
            }
        ) {
            it[fechaRevocado] = Instant.now()
        }

        // Crear el nuevo consentimiento
        ConsentimientoTable.insert {
            it[ConsentimientoTable.usuarioId] = usuarioId
            it[ConsentimientoTable.version]   = version
            it[ConsentimientoTable.alcances]  = alcances
            it[ConsentimientoTable.fechaOtorgado] = Instant.now()
        }

        ConsentimientoTable
            .selectAll()
            .where { ConsentimientoTable.usuarioId eq usuarioId }
            .orderBy(ConsentimientoTable.fechaOtorgado, SortOrder.DESC)
            .limit(1)
            .first()
    }

    suspend fun latestVigente(usuarioId: UUID): ResultRow? = tx {
        ConsentimientoTable
            .selectAll()
            .where {
                (ConsentimientoTable.usuarioId eq usuarioId) and
                ConsentimientoTable.fechaRevocado.isNull()
            }
            .orderBy(ConsentimientoTable.fechaOtorgado, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
    }

    suspend fun revokeLatest(usuarioId: UUID): Int = tx {
        val row = ConsentimientoTable
            .selectAll()
            .where {
                (ConsentimientoTable.usuarioId eq usuarioId) and
                ConsentimientoTable.fechaRevocado.isNull()
            }
            .orderBy(ConsentimientoTable.fechaOtorgado, SortOrder.DESC)
            .limit(1)
            .firstOrNull()

        if (row == null) 0
        else ConsentimientoTable.update(
            where = { ConsentimientoTable.id eq row[ConsentimientoTable.id] }
        ) {
            it[fechaRevocado] = Instant.now()
        }
    }
}
