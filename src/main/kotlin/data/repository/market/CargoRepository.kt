package data.repository.market

import data.models.market.CargoDto
import data.models.market.CargoSkillDetailDto
import data.tables.market.CargoSkillTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class CargoRepository(private val database: Database? = null) {

    private fun <T> dbExec(block: () -> T): T =
        if (database != null) transaction(database) { block() }
        else transaction { block() }

    fun getAllCargos(): List<CargoDto> = dbExec {
        CargoTable.selectAll()
            .where { CargoTable.activo eq true }
            .orderBy(CargoTable.nombre, SortOrder.ASC)
            .map { row ->
                CargoDto(
                    cargoId = row[CargoTable.cargoId].toString(),
                    nombre = row[CargoTable.nombre],
                    area = row[CargoTable.area],
                    descripcion = row[CargoTable.descripcion],
                    nivelBase = row[CargoTable.nivelBase],
                    activo = row[CargoTable.activo]
                )
            }
    }

    fun getCargoById(cargoId: UUID): CargoDto? = dbExec {
        CargoTable.selectAll()
            .where { CargoTable.cargoId eq cargoId }
            .map { row ->
                CargoDto(
                    cargoId = row[CargoTable.cargoId].toString(),
                    nombre = row[CargoTable.nombre],
                    area = row[CargoTable.area],
                    descripcion = row[CargoTable.descripcion],
                    nivelBase = row[CargoTable.nivelBase],
                    activo = row[CargoTable.activo]
                )
            }
            .singleOrNull()
    }

    fun getCargoByName(nombre: String): CargoDto? = dbExec {
        CargoTable.selectAll()
            .where { CargoTable.nombre eq nombre }
            .map { row ->
                CargoDto(
                    cargoId = row[CargoTable.cargoId].toString(),
                    nombre = row[CargoTable.nombre],
                    area = row[CargoTable.area],
                    descripcion = row[CargoTable.descripcion],
                    nivelBase = row[CargoTable.nivelBase],
                    activo = row[CargoTable.activo]
                )
            }
            .singleOrNull()
    }

    fun createCargo(
        nombre: String,
        area: String,
        descripcion: String?,
        nivelBase: String = "semisenior"
    ): CargoDto = dbExec {
        val existing = getCargoByName(nombre)
        if (existing != null) return@dbExec existing

        val newId = UUID.randomUUID()
        val validNivel = when (nivelBase.lowercase()) {
            "junior", "jr" -> "junior"
            "senior", "sr" -> "senior"
            else -> "semisenior"
        }
        CargoTable.insert {
            it[cargoId] = newId
            it[CargoTable.nombre] = nombre
            it[CargoTable.area] = area
            it[CargoTable.descripcion] = descripcion
            it[CargoTable.nivelBase] = validNivel
            it[activo] = true
        }

        CargoDto(
            cargoId = newId.toString(),
            nombre = nombre,
            area = area,
            descripcion = descripcion,
            nivelBase = validNivel,
            activo = true
        )
    }

    fun getOrCreateSkill(
        nombre: String,
        categoria: String = "tecnica",
        tipoArea: String = "backend",
        descripcion: String? = null,
        demandaScore: Short = 50
    ): UUID = dbExec {
        val existing = SkillTable.selectAll()
            .where { SkillTable.nombre eq nombre }
            .singleOrNull()

        if (existing != null) {
            return@dbExec existing[SkillTable.skillId]
        }

        val newSkillId = UUID.randomUUID()
        SkillTable.insert {
            it[skillId] = newSkillId
            it[SkillTable.nombre] = nombre
            it[SkillTable.categoria] = if (categoria in listOf("tecnica", "blanda")) categoria else "tecnica"
            it[SkillTable.tipoArea] = tipoArea
            it[SkillTable.descripcion] = descripcion ?: "Tecnología / Habilidad detectada del mercado: "
            it[SkillTable.demandaScore] = demandaScore.coerceIn(1, 100)
            it[activo] = true
        }
        newSkillId
    }

    fun linkOrUpdateCargoSkill(
        cargoId: UUID,
        skillId: UUID,
        nivelRequerido: String,
        peso: Short,
        obligatoria: Boolean
    ): UUID = dbExec {
        val existing = CargoSkillTable.selectAll()
            .where { (CargoSkillTable.cargoId eq cargoId) and (CargoSkillTable.skillId eq skillId) }
            .singleOrNull()

        val validNivel = when (nivelRequerido.lowercase()) {
            "junior", "jr" -> "junior"
            "senior", "sr" -> "senior"
            else -> "semisenior"
        }
        val validPeso = peso.coerceIn(1, 100)

        if (existing != null) {
            val relationId = existing[CargoSkillTable.cargoSkillId]
            CargoSkillTable.update({ CargoSkillTable.cargoSkillId eq relationId }) {
                it[CargoSkillTable.nivelRequerido] = validNivel
                it[CargoSkillTable.peso] = validPeso
                it[CargoSkillTable.obligatoria] = obligatoria
            }
            relationId
        } else {
            val newRelationId = UUID.randomUUID()
            CargoSkillTable.insert {
                it[cargoSkillId] = newRelationId
                it[CargoSkillTable.cargoId] = cargoId
                it[CargoSkillTable.skillId] = skillId
                it[CargoSkillTable.nivelRequerido] = validNivel
                it[CargoSkillTable.peso] = validPeso
                it[CargoSkillTable.obligatoria] = obligatoria
            }
            newRelationId
        }
    }

    fun getCargoSkillsWithDetails(cargoId: UUID): List<CargoSkillDetailDto> = dbExec {
        (CargoSkillTable innerJoin SkillTable)
            .selectAll()
            .where { CargoSkillTable.cargoId eq cargoId }
            .orderBy(CargoSkillTable.peso, SortOrder.DESC)
            .map { row ->
                CargoSkillDetailDto(
                    skillId = row[SkillTable.skillId].toString(),
                    nombre = row[SkillTable.nombre],
                    categoria = row[SkillTable.categoria],
                    tipoArea = row[SkillTable.tipoArea],
                    nivelRequerido = row[CargoSkillTable.nivelRequerido],
                    peso = row[CargoSkillTable.peso],
                    obligatoria = row[CargoSkillTable.obligatoria]
                )
            }
    }
}
