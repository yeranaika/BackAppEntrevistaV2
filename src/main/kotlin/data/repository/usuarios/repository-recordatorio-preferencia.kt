package data.repository.usuarios

import data.models.usuarios.RecordatorioPreferencias
import data.models.usuarios.toRecordatorioPreferencias
import data.tables.usuarios.RecordatorioPreferenciaTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

// Helper de transacción suspendida (mismo patrón que otros repos)
private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class RecordatorioPreferenciaRepository {

    suspend fun getByUsuario(usuarioId: UUID): RecordatorioPreferencias? = dbTx {
        RecordatorioPreferenciaTable
            .selectAll()
            .where { RecordatorioPreferenciaTable.usuarioId eq usuarioId }
            .singleOrNull()
            ?.toRecordatorioPreferencias()
    }

    /**
     * Upsert:
     *  - Si ya existe fila para ese usuario → UPDATE
     *  - Si no existe → INSERT
     *
     * Devuelve lo que quedó finalmente en BD.
     */
    suspend fun upsert(
        usuarioId: UUID,
        prefs: RecordatorioPreferencias
    ): RecordatorioPreferencias = dbTx {
        val dias = prefs.diasSemana.joinToString(",")

        // 1) Intentar actualizar
        val filasActualizadas = RecordatorioPreferenciaTable.update({
            RecordatorioPreferenciaTable.usuarioId eq usuarioId
        }) {
            it[diasSemana]   = dias
            it[hora]         = prefs.hora
            it[tipoPractica] = prefs.tipoPractica
            it[habilitado]   = prefs.habilitado
        }

        // 2) Si no había registro → insertar uno nuevo
        if (filasActualizadas == 0) {
            RecordatorioPreferenciaTable.insert {
                it[this.usuarioId] = usuarioId
                it[diasSemana]     = dias
                it[hora]           = prefs.hora
                it[tipoPractica]   = prefs.tipoPractica
                it[habilitado]     = prefs.habilitado
            }
        }

        // 3) Devolver lo que quedó realmente guardado en la BD
        RecordatorioPreferenciaTable
            .selectAll()
            .where { RecordatorioPreferenciaTable.usuarioId eq usuarioId }
            .single()
            .toRecordatorioPreferencias()
    }
}
