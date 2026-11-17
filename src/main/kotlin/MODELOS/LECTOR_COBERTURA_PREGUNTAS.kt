package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import java.util.UUID

/** Cuántas preguntas hay de una skill, por cargo (null = sin cargo), nivel y estado. */
data class ConteoPreguntasSkill(
    val skillId: UUID,
    val cargoId: UUID?,
    val nivel: NivelExperiencia,
    val estado: EstadoPregunta,
    val cantidad: Int
)

/** Lectura agregada del banco para ver qué skills del mercado tienen pocas preguntas (D26). */
fun interface LectorCoberturaPreguntas {
    /** Solo aprobadas y pendientes (las rechazadas no cuentan). Vacío si [skillIds] está vacío. */
    suspend fun contarPorSkill(skillIds: Set<UUID>): List<ConteoPreguntasSkill>
}

class LectorCoberturaPreguntasExposed : LectorCoberturaPreguntas {
    private val estadosContados = listOf(EstadoPregunta.APROBADA.valorBd, EstadoPregunta.PENDIENTE.valorBd)

    override suspend fun contarPorSkill(skillIds: Set<UUID>): List<ConteoPreguntasSkill> {
        if (skillIds.isEmpty()) return emptyList()
        return transaccion {
            val cantidad = TablaPregunta.preguntaId.count()
            TablaPregunta
                .select(TablaPregunta.skillId, TablaPregunta.cargoId, TablaPregunta.nivelDificultad, TablaPregunta.estado, cantidad)
                .where { (TablaPregunta.skillId inList skillIds) and (TablaPregunta.estado inList estadosContados) }
                .groupBy(TablaPregunta.skillId, TablaPregunta.cargoId, TablaPregunta.nivelDificultad, TablaPregunta.estado)
                .mapNotNull { fila ->
                    val skillId = fila[TablaPregunta.skillId] ?: return@mapNotNull null
                    val nivel = NivelExperiencia.desdeBd(fila[TablaPregunta.nivelDificultad]) ?: return@mapNotNull null
                    val estado = EstadoPregunta.desdeBd(fila[TablaPregunta.estado]) ?: return@mapNotNull null
                    ConteoPreguntasSkill(skillId, fila[TablaPregunta.cargoId], nivel, estado, fila[cantidad].toInt())
                }
        }
    }
}
