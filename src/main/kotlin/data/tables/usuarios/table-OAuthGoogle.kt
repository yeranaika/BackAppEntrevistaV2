package data.tables.usuarios

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object OauthAccountTable : Table(name = "app.oauth_account") {
    val oauthId       = uuid("oauth_id")
    val provider      = varchar("provider", 20)
    val subject       = text("subject")
    val email         = varchar("email", 320).nullable()
    val emailVerified = bool("email_verified").default(false)
    val usuarioId     = uuid("usuario_id")
        .references(UsuarioTable.usuarioId, onDelete = ReferenceOption.CASCADE)

    init { index(true, provider, subject) }          // UNIQUE(provider,subject)
    override val primaryKey = PrimaryKey(oauthId)    // ✅ así se declara en Exposed
}
