package data.models.usuarios

import data.tables.usuarios.RecordatorioPreferenciaTable
import org.jetbrains.exposed.sql.ResultRow

data class RecordatorioPreferencias(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val habilitado: Boolean
)

fun ResultRow.toRecordatorioPreferencias(): RecordatorioPreferencias =
    RecordatorioPreferencias(
        diasSemana = this[RecordatorioPreferenciaTable.diasSemana]
            .split(",")
            .filter { it.isNotBlank() },
        hora = this[RecordatorioPreferenciaTable.hora],
        tipoPractica = this[RecordatorioPreferenciaTable.tipoPractica],
        habilitado = this[RecordatorioPreferenciaTable.habilitado]
    )
