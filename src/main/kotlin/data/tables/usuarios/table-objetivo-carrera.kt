package data.tables.usuarios

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object ObjetivoCarreraTable : UUIDTable("objetivo_carrera", "objetivo_id") {

    // Clave foránea a usuario(usuario_id)
    val usuarioId = reference(
        name = "usuario_id",
        refColumn = UsuarioTable.usuarioId,
        onDelete = ReferenceOption.CASCADE
    )

    // Texto largo para el cargo/meta (desde onboarding)
    val nombreCargo = varchar("nombre_cargo", length = 120)

    // Área / sector (TI, RRHH, etc.)
    val sector = varchar("sector", length = 50).nullable()

    // Objetivo activo (true = objetivo actual)
    val activo = bool("activo").default(true)
}
