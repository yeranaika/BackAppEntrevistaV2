package MODELOS

import UTILIDADES.transaccion
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Nombres de cargos y skills del catálogo de mercado (para validar referencias y armar prompts). */
interface LectorCatalogo {
    suspend fun nombreCargo(id: UUID): String?
    suspend fun nombreSkill(id: UUID): String?
}

class LectorCatalogoExposed : LectorCatalogo {
    override suspend fun nombreCargo(id: UUID): String? = transaccion {
        CargoTable.selectAll().where { CargoTable.cargoId eq id }.limit(1).firstOrNull()?.get(CargoTable.nombre)
    }

    override suspend fun nombreSkill(id: UUID): String? = transaccion {
        SkillTable.selectAll().where { SkillTable.skillId eq id }.limit(1).firstOrNull()?.get(SkillTable.nombre)
    }
}
