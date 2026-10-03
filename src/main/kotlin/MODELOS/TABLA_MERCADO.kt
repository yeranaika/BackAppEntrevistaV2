package MODELOS

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/** Catálogo de habilidades del mercado (src/DB: skill). */
object TablaSkill : Table("skill") {
    val skillId = uuid("skill_id")
    val nombre = varchar("nombre", 100).uniqueIndex("skill_nombre_unique")
    val categoria = varchar("categoria", 10)
    val tipoArea = varchar("tipo_area", 50)
    val descripcion = text("descripcion").nullable()
    val demandaScore = short("demanda_score").default(50)
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(skillId, name = "skill_pk")
}

/** Historial semanal de demanda por skill. */
object TablaSkillTendencia : Table("skill_tendencia") {
    val tendenciaId = uuid("tendencia_id")
    val skillId = uuid("skill_id").references(TablaSkill.skillId)
    val frecuenciaOfertas = integer("frecuencia_ofertas").default(0)
    val nivelRequerido = varchar("nivel_requerido", 20).default("semisenior")
    val fechaActualizacion = datetime("fecha_actualizacion").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(tendenciaId, name = "skill_tendencia_pk")
}

object TablaCargo : Table("cargo") {
    val cargoId = uuid("cargo_id")
    val nombre = varchar("nombre", 150).uniqueIndex("cargo_nombre_unique")
    val area = varchar("area", 50)
    val descripcion = text("descripcion").nullable()
    val nivelBase = varchar("nivel_base", 20).default("semisenior")
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(cargoId, name = "cargo_pk")
}

/** Qué skills exige cada cargo, con nivel, peso (1–100) y obligatoriedad. */
object TablaCargoSkill : Table("cargo_skill") {
    val cargoSkillId = uuid("cargo_skill_id")
    val cargoId = uuid("cargo_id").references(TablaCargo.cargoId)
    val skillId = uuid("skill_id").references(TablaSkill.skillId)
    val nivelRequerido = varchar("nivel_requerido", 20).default("junior")
    val peso = short("peso").default(50)
    val obligatoria = bool("obligatoria").default(true)

    override val primaryKey = PrimaryKey(cargoSkillId, name = "cargo_skill_pk")
}
