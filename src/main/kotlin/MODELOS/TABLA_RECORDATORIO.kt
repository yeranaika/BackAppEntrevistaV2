package MODELOS

import org.jetbrains.exposed.sql.Table

/** Preferencias de recordatorios de práctica (una fila por usuario). */
object TablaRecordatorio : Table("recordatorio_preferencia") {
    val usuarioId = uuid("usuario_id")
    /** Días separados por coma, ej: "LUN,MIE,VIE". */
    val diasSemana = varchar("dias_semana", length = 50)
    /** "HH:mm" */
    val hora = varchar("hora", length = 5)
    val tipoPractica = varchar("tipo_practica", length = 32)
    val habilitado = bool("habilitado").default(true)

    override val primaryKey = PrimaryKey(usuarioId)
}
