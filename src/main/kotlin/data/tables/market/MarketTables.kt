package data.tables.market

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object SkillTable : Table("skill") {
    val skillId = uuid("skill_id")
    val nombre = varchar("nombre", 100).uniqueIndex("skill_nombre_unique")
    val categoria = varchar("categoria", 10)
    val tipoArea = varchar("tipo_area", 50)
    val descripcion = text("descripcion").nullable()
    val demandaScore = short("demanda_score").default(50)
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(skillId, name = "skill_pk")
}

object SkillTendenciaTable : Table("skill_tendencia") {
    val tendenciaId = uuid("tendencia_id")
    val skillId = uuid("skill_id").references(SkillTable.skillId)
    val frecuenciaOfertas = integer("frecuencia_ofertas").default(0)
    val nivelRequerido = varchar("nivel_requerido", 20).default("semisenior")
    val fechaActualizacion = datetime("fecha_actualizacion").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(tendenciaId, name = "skill_tendencia_pk")
}

object CargoTable : Table("cargo") {
    val cargoId = uuid("cargo_id")
    val nombre = varchar("nombre", 150).uniqueIndex("cargo_nombre_unique")
    val area = varchar("area", 50)
    val descripcion = text("descripcion").nullable()
    val nivelBase = varchar("nivel_base", 20).default("semisenior")
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(cargoId, name = "cargo_pk")
}

object CargoSkillTable : Table("cargo_skill") {
    val cargoSkillId = uuid("cargo_skill_id")
    val cargoId = uuid("cargo_id").references(CargoTable.cargoId)
    val skillId = uuid("skill_id").references(SkillTable.skillId)
    val nivelRequerido = varchar("nivel_requerido", 20).default("junior")
    val peso = short("peso").default(50)
    val obligatoria = bool("obligatoria").default(true)

    override val primaryKey = PrimaryKey(cargoSkillId, name = "cargo_skill_pk")
}
