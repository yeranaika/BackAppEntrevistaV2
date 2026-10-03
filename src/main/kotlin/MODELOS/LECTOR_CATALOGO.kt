package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Nombres de cargos y skills del catálogo de mercado (para validar referencias y armar prompts). */
interface LectorCatalogo {
    suspend fun nombreCargo(id: UUID): String?
    suspend fun nombreSkill(id: UUID): String?
}

class LectorCatalogoExposed : LectorCatalogo {
    override suspend fun nombreCargo(id: UUID): String? = transaccion {
        TablaCargo.selectAll().where { TablaCargo.cargoId eq id }.limit(1).firstOrNull()?.get(TablaCargo.nombre)
    }

    override suspend fun nombreSkill(id: UUID): String? = transaccion {
        TablaSkill.selectAll().where { TablaSkill.skillId eq id }.limit(1).firstOrNull()?.get(TablaSkill.nombre)
    }
}
