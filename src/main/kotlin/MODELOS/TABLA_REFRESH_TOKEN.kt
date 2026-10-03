package MODELOS

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TablaRefreshToken : Table("refresh_token") {
    val refreshId = uuid("refresh_id")
    val usuarioId = uuid("usuario_id").index()
    val tokenHash = text("token_hash").index()
    val emitidoEn = timestamp("issued_at")
    val expiraEn = timestamp("expires_at")
    val revocado = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(refreshId)
}
