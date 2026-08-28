// data/tables/table-consentimiento.kt
package data.tables.usuarios

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.util.UUID

object ConsentimientoTable : Table("consentimiento") {
    val id            = uuid("consentimiento_id").clientDefault { UUID.randomUUID() }
    val usuarioId     = uuid("usuario_id").index()
    val version       = varchar("version", 20)
    val alcances      = jsonb<Map<String, Boolean>>(
        "alcances",
        Json,
        MapSerializer(String.serializer(), Boolean.serializer())
    )
    val fechaOtorgado = timestamp("fecha_otorgado")
    val fechaRevocado = timestamp("fecha_revocado").nullable()

    override val primaryKey = PrimaryKey(id)
}
