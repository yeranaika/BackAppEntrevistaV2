package data.tables.auth

import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object RecoveryCodeTable : Table("app.recovery_code") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id").references(UsuarioTable.usuarioId)
    val codigo = varchar("codigo", 6)  // 6 dígitos
    val fechaExpiracion = timestampWithTimeZone("fecha_expiracion")
    val usado = bool("usado").default(false)
    val fechaCreacion = timestampWithTimeZone("fecha_creacion")

    override val primaryKey = PrimaryKey(id)
}
