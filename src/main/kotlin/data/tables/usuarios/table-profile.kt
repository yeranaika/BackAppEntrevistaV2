package data.tables.usuarios

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.json

object ProfileTable : Table("perfil_usuario") {
    val perfilId           = uuid("perfil_id")
    val usuarioId          = uuid("usuario_id").index()
    val nivelExperiencia   = varchar("nivel_experiencia", 40).nullable()
    val area               = varchar("area", 50).nullable()
    val flagsAccesibilidad = json<JsonElement>("flags_accesibilidad", Json, JsonElement.serializer()).nullable()
    val notaObjetivos      = text("nota_objetivos").nullable()
    val pais               = varchar("pais", 2).nullable()

    // 👇 Esto en Exposed es Column<OffsetDateTime>
    val fechaActualizacion = timestampWithTimeZone("fecha_actualizacion")

    override val primaryKey = PrimaryKey(perfilId)
}
