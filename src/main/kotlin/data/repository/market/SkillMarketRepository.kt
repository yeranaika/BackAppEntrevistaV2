package data.repository.market

import data.models.market.SkillDto
import data.models.market.SkillTendenciaDto
import data.tables.market.CargoSkillTable
import data.tables.market.SkillTable
import data.tables.market.SkillTendenciaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class SkillMarketRepository(private val database: Database? = null) {

    private fun <T> dbExec(block: () -> T): T =
        if (database != null) transaction(database) { block() }
        else transaction { block() }

    fun getAllActiveSkills(): List<SkillDto> = dbExec {
        SkillTable.selectAll()
            .where { SkillTable.activo eq true }
            .orderBy(SkillTable.demandaScore, SortOrder.DESC)
            .map { row ->
                SkillDto(
                    skillId = row[SkillTable.skillId].toString(),
                    nombre = row[SkillTable.nombre],
                    categoria = row[SkillTable.categoria],
                    tipoArea = row[SkillTable.tipoArea],
                    descripcion = row[SkillTable.descripcion],
                    demandaScore = row[SkillTable.demandaScore],
                    activo = row[SkillTable.activo]
                )
            }
    }

    fun getSkillById(skillId: UUID): SkillDto? = dbExec {
        SkillTable.selectAll()
            .where { SkillTable.skillId eq skillId }
            .map { row ->
                SkillDto(
                    skillId = row[SkillTable.skillId].toString(),
                    nombre = row[SkillTable.nombre],
                    categoria = row[SkillTable.categoria],
                    tipoArea = row[SkillTable.tipoArea],
                    descripcion = row[SkillTable.descripcion],
                    demandaScore = row[SkillTable.demandaScore],
                    activo = row[SkillTable.activo]
                )
            }
            .singleOrNull()
    }

    fun updateSkillDemandScore(skillId: UUID, newScore: Short): Boolean = dbExec {
        val clampedScore = newScore.coerceIn(1, 100)
        val updated = SkillTable.update({ SkillTable.skillId eq skillId }) {
            it[demandaScore] = clampedScore
        }
        updated > 0
    }

    fun insertTendencia(
        skillId: UUID,
        frecuenciaOfertas: Int,
        nivelRequerido: String
    ): UUID = dbExec {
        val newId = UUID.randomUUID()
        val validNivel = when (nivelRequerido.lowercase()) {
            "junior", "jr" -> "junior"
            "senior", "sr" -> "senior"
            else -> "semisenior"
        }
        SkillTendenciaTable.insert {
            it[tendenciaId] = newId
            it[SkillTendenciaTable.skillId] = skillId
            it[SkillTendenciaTable.frecuenciaOfertas] = frecuenciaOfertas
            it[SkillTendenciaTable.nivelRequerido] = validNivel
            it[fechaActualizacion] = LocalDateTime.now()
        }
        newId
    }

    fun updateCargoSkillWeights(skillId: UUID, demandScore: Short): Int = dbExec {
        val clampedScore = demandScore.coerceIn(1, 100).toInt()
        val relations = CargoSkillTable.selectAll()
            .where { CargoSkillTable.skillId eq skillId }
            .toList()

        var count = 0
        for (rel in relations) {
            val currentWeight = rel[CargoSkillTable.peso].toInt()
            // Nueva ponderación equilibrando peso base (60%) y demanda del mercado (40%)
            val adjustedWeight = ((currentWeight * 0.6) + (clampedScore * 0.4)).toInt().coerceIn(1, 100).toShort()
            CargoSkillTable.update({ CargoSkillTable.cargoSkillId eq rel[CargoSkillTable.cargoSkillId] }) {
                it[peso] = adjustedWeight
            }
            count++
        }
        count
    }

    fun getSkillTrendsHistory(skillId: UUID, limit: Int = 10): List<SkillTendenciaDto> = dbExec {
        SkillTendenciaTable.selectAll()
            .where { SkillTendenciaTable.skillId eq skillId }
            .orderBy(SkillTendenciaTable.fechaActualizacion, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                SkillTendenciaDto(
                    tendenciaId = row[SkillTendenciaTable.tendenciaId].toString(),
                    skillId = row[SkillTendenciaTable.skillId].toString(),
                    frecuenciaOfertas = row[SkillTendenciaTable.frecuenciaOfertas],
                    nivelRequerido = row[SkillTendenciaTable.nivelRequerido],
                    fechaActualizacion = row[SkillTendenciaTable.fechaActualizacion].toString()
                )
            }
    }
}
