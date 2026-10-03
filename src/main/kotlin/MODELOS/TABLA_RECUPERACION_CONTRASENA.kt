package MODELOS

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TablaRecuperacionContrasena : Table("password_reset") {
    val token = uuid("token")
    val usuarioId = uuid("usuario_id")
    val codigo = varchar("code", 12)
    val emitidoEn = timestamp("issued_at")
    val expiraEn = timestamp("expires_at")
    val usado = bool("used").default(false)
    // migrations/014_password_reset_intentos.sql
    val intentosFallidos = short("intentos_fallidos").default(0)

    override val primaryKey = PrimaryKey(token)
}
