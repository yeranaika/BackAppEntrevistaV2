package data.repository.skills

import data.models.skills.CargoLevelItem
import data.models.skills.CargoSkillMatrixItem
import data.models.skills.CargoSkillsMatrixResponse
import data.models.skills.TrendingSkillItem
import data.models.skills.TrendingSkillsResponse
import data.tables.market.CargoSkillTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class CargoSkillRepository(private val db: Database? = null) {

    private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
        if (db != null) newSuspendedTransaction(context = Dispatchers.IO, db = db, statement = block)
        else newSuspendedTransaction(context = Dispatchers.IO, statement = block)

    /**
     * Retorna la lista de cargos activos con sus niveles base (junior, semisenior, senior).
     */
    suspend fun getCargosList(): List<CargoLevelItem> = dbTx {
        CargoTable
            .selectAll()
            .where { CargoTable.activo eq true }
            .orderBy(CargoTable.nombre to SortOrder.ASC)
            .map { row ->
                CargoLevelItem(
                    cargoId = row[CargoTable.cargoId].toString(),
                    nombre = row[CargoTable.nombre],
                    area = row[CargoTable.area],
                    descripcion = row[CargoTable.descripcion],
                    nivelBase = row[CargoTable.nivelBase],
                    activo = row[CargoTable.activo]
                )
            }
    }

    /**
     * Retorna la matriz completa de skills asociadas a un cargo con sus pesos,
     * obligatoriedad y nivel requerido.
     */
    suspend fun getCargoSkillsMatrix(cargoId: UUID): CargoSkillsMatrixResponse? = dbTx {
        val cargoRow = CargoTable
            .selectAll()
            .where { (CargoTable.cargoId eq cargoId) and (CargoTable.activo eq true) }
            .singleOrNull() ?: return@dbTx null

        val skills = (CargoSkillTable innerJoin SkillTable)
            .selectAll()
            .where {
                (CargoSkillTable.cargoId eq cargoId) and
                (SkillTable.activo eq true)
            }
            .orderBy(CargoSkillTable.obligatoria to SortOrder.DESC)
            .orderBy(CargoSkillTable.peso to SortOrder.DESC)
            .map { row ->
                CargoSkillMatrixItem(
                    skillId = row[SkillTable.skillId].toString(),
                    nombre = row[SkillTable.nombre],
                    categoria = row[SkillTable.categoria],
                    tipoArea = row[SkillTable.tipoArea],
                    nivelRequerido = row[CargoSkillTable.nivelRequerido],
                    peso = row[CargoSkillTable.peso],
                    obligatoria = row[CargoSkillTable.obligatoria]
                )
            }

        CargoSkillsMatrixResponse(
            cargoId = cargoRow[CargoTable.cargoId].toString(),
            cargoNombre = cargoRow[CargoTable.nombre],
            area = cargoRow[CargoTable.area],
            nivelBase = cargoRow[CargoTable.nivelBase],
            totalSkills = skills.size,
            obligatoriasCount = skills.count { it.obligatoria },
            opcionalesCount = skills.count { !it.obligatoria },
            skills = skills
        )
    }

    /**
     * Retorna las top skills más demandadas del mercado, con filtro opcional por categoría.
     */
    suspend fun getTrendingSkills(categoria: String? = null, limit: Int = 20): TrendingSkillsResponse = dbTx {
        val query = SkillTable.selectAll().where { SkillTable.activo eq true }

        if (!categoria.isNullOrBlank()) {
            val catNormalizada = categoria.trim().lowercase()
            query.andWhere { SkillTable.categoria eq catNormalizada }
        }

        val clampedLimit = limit.coerceIn(1, 100)

        val skills = query
            .orderBy(SkillTable.demandaScore to SortOrder.DESC)
            .limit(clampedLimit)
            .map { row ->
                TrendingSkillItem(
                    skillId = row[SkillTable.skillId].toString(),
                    nombre = row[SkillTable.nombre],
                    categoria = row[SkillTable.categoria],
                    tipoArea = row[SkillTable.tipoArea],
                    demandaScore = row[SkillTable.demandaScore],
                    descripcion = row[SkillTable.descripcion]
                )
            }

        TrendingSkillsResponse(
            total = skills.size,
            categoriaFiltro = categoria?.trim()?.lowercase(),
            skills = skills
        )
    }
}
