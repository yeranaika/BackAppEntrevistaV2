package data.repository.usuarios

import data.tables.usuarios.ObjetivoCarreraTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ObjetivoCarreraRepository {

    fun findByUser(userId: UUID): ObjetivoCarreraRow? = transaction {
        ObjetivoCarreraTable
            .selectAll()
            .where { (ObjetivoCarreraTable.usuarioId eq userId) and (ObjetivoCarreraTable.activo eq true) }
            .singleOrNull()
            ?.let { toRow(it) }
    }

    /**
     * Crea o actualiza el objetivo de carrera del usuario.
     * Si ya existe un objetivo activo, lo desactiva y crea uno nuevo.
     */
    fun upsert(userId: UUID, nombreCargo: String, sector: String?): UUID = transaction {
        // Desactivar objetivos anteriores del usuario
        ObjetivoCarreraTable.update({
            (ObjetivoCarreraTable.usuarioId eq userId) and (ObjetivoCarreraTable.activo eq true)
        }) {
            it[activo] = false
        }

        // Crear nuevo objetivo activo
        ObjetivoCarreraTable.insertAndGetId {
            it[this.usuarioId] = userId
            it[this.nombreCargo] = nombreCargo
            it[this.sector] = sector
            it[activo] = true
        }.value
    }

    /**
     * Actualiza el objetivo de carrera existente del usuario.
     * Si no existe, crea uno nuevo.
     */
    fun update(userId: UUID, nombreCargo: String, sector: String?): Int = transaction {
        val existing = findByUser(userId)

        if (existing != null) {
            // Actualizar el existente
            ObjetivoCarreraTable.update({ ObjetivoCarreraTable.id eq existing.id }) {
                it[this.nombreCargo] = nombreCargo
                it[this.sector] = sector
            }
        } else {
            // Si no existe, crear uno nuevo
            upsert(userId, nombreCargo, sector)
            1
        }
    }

    /**
     * Elimina (desactiva) el objetivo de carrera del usuario
     */
    fun delete(userId: UUID): Int = transaction {
        ObjetivoCarreraTable.update({
            (ObjetivoCarreraTable.usuarioId eq userId) and (ObjetivoCarreraTable.activo eq true)
        }) {
            it[activo] = false
        }
    }

    private fun toRow(row: ResultRow): ObjetivoCarreraRow {
        return ObjetivoCarreraRow(
            id = row[ObjetivoCarreraTable.id].value,
            usuarioId = row[ObjetivoCarreraTable.usuarioId],
            nombreCargo = row[ObjetivoCarreraTable.nombreCargo],
            sector = row[ObjetivoCarreraTable.sector],
            activo = row[ObjetivoCarreraTable.activo]
        )
    }
}

data class ObjetivoCarreraRow(
    val id: UUID,
    val usuarioId: UUID,
    val nombreCargo: String,
    val sector: String?,
    val activo: Boolean
)
