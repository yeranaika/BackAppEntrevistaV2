package MODELOS

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object TablaCuentaOAuth : Table(name = "app.oauth_account") {
    val oauthId = uuid("oauth_id")
    val proveedor = varchar("provider", 20)
    val subject = text("subject")
    val correo = varchar("email", 320).nullable()
    val correoVerificado = bool("email_verified").default(false)
    val usuarioId = uuid("usuario_id")
        .references(TablaUsuario.usuarioId, onDelete = ReferenceOption.CASCADE)

    init { index(true, proveedor, subject) }
    override val primaryKey = PrimaryKey(oauthId)
}
