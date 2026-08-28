package data.tables.usuarios

import org.jetbrains.exposed.sql.Table

object RecordatorioPreferenciaTable : Table("recordatorio_preferencia") {

    // Columna UUID simple; la FK la define el SQL (CREATE TABLE ... REFERENCES usuario(id))
    val usuarioId = uuid("usuario_id")

    // Ej: "LUNES,MARTES,VIERNES"
    val diasSemana = varchar("dias_semana", length = 50)

    // "HH:mm" (ej: "20:30")
    val hora = varchar("hora", length = 5)

    // Ej: "TEST", "ENTREVISTA", "REPASO"
    val tipoPractica = varchar("tipo_practica", length = 32)

    val habilitado = bool("habilitado").default(true)

    override val primaryKey = PrimaryKey(usuarioId)
}
