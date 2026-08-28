package data.tables.usuarios

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object RefreshTokenTable : Table("refresh_token") {
    val refreshId = uuid("refresh_id")
    val usuarioId = uuid("usuario_id").index()
    val tokenHash = text("token_hash").index()
    val issuedAt  = timestamp("issued_at")
    val expiresAt = timestamp("expires_at")
    val revoked   = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(refreshId)
}
