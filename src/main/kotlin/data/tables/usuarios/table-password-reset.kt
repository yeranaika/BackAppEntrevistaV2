package data.tables.usuarios

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object PasswordResetTable : Table("password_reset") {
    val token     = uuid("token")
    val usuarioId = uuid("usuario_id")
    val code      = varchar("code", 12)
    val issuedAt  = timestamp("issued_at")   // Column<Instant>
    val expiresAt = timestamp("expires_at")  // Column<Instant>
    val used      = bool("used").default(false)

    override val primaryKey = PrimaryKey(token)
}
