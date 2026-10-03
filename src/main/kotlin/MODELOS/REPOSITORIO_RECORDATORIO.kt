package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

private const val SEPARADOR_DIAS = ","

data class Recordatorio(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val estaHabilitado: Boolean
)

interface RepositorioRecordatorio {
    suspend fun buscar(usuarioId: UUID): Recordatorio?

    /** Crea o reemplaza las preferencias del usuario y devuelve lo guardado. */
    suspend fun guardar(usuarioId: UUID, recordatorio: Recordatorio): Recordatorio
}

class RepositorioRecordatorioExposed : RepositorioRecordatorio {

    override suspend fun buscar(usuarioId: UUID): Recordatorio? = transaccion {
        TablaRecordatorio.selectAll().where { TablaRecordatorio.usuarioId eq usuarioId }.limit(1).firstOrNull()?.aRecordatorio()
    }

    override suspend fun guardar(usuarioId: UUID, recordatorio: Recordatorio): Recordatorio = transaccion {
        val dias = recordatorio.diasSemana.joinToString(SEPARADOR_DIAS)
        val actualizadas = TablaRecordatorio.update({ TablaRecordatorio.usuarioId eq usuarioId }) {
            it[diasSemana] = dias
            it[hora] = recordatorio.hora
            it[tipoPractica] = recordatorio.tipoPractica
            it[habilitado] = recordatorio.estaHabilitado
        }
        if (actualizadas == 0) {
            TablaRecordatorio.insert {
                it[TablaRecordatorio.usuarioId] = usuarioId
                it[diasSemana] = dias
                it[hora] = recordatorio.hora
                it[tipoPractica] = recordatorio.tipoPractica
                it[habilitado] = recordatorio.estaHabilitado
            }
        }
        recordatorio
    }

    private fun ResultRow.aRecordatorio() = Recordatorio(
        diasSemana = this[TablaRecordatorio.diasSemana].split(SEPARADOR_DIAS).filter { it.isNotBlank() },
        hora = this[TablaRecordatorio.hora],
        tipoPractica = this[TablaRecordatorio.tipoPractica],
        estaHabilitado = this[TablaRecordatorio.habilitado]
    )
}
