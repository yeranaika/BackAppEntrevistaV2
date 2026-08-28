package data.repository.usuarios

import at.favre.lib.crypto.bcrypt.BCrypt
import data.tables.usuarios.OauthAccountTable
import data.tables.usuarios.UsuarioTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

interface UsuariosOAuthRepository {
    suspend fun linkOrCreateFromGoogle(sub: String, email: String): UUID
    suspend fun isGoogleUser(userId: UUID): Boolean
}

class UsuariosOAuthRepositoryImpl : UsuariosOAuthRepository {

    // hash para contraseña aleatoria
    private fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    override suspend fun isGoogleUser(userId: UUID): Boolean = db {
        OauthAccountTable
            .selectAll()
            .where {
                (OauthAccountTable.usuarioId eq userId) and
                (OauthAccountTable.provider eq "google")
            }
            .limit(1)
            .firstOrNull() != null
    }

    override suspend fun linkOrCreateFromGoogle(sub: String, email: String): UUID = db {
        // 1) ¿Ya existe vínculo (google, sub)?
        OauthAccountTable
            .selectAll()
            .where { (OauthAccountTable.provider eq "google") and (OauthAccountTable.subject eq sub) }
            .limit(1)
            .firstOrNull()
            ?.let { return@db it[OauthAccountTable.usuarioId] }

        // 2) ¿Existe usuario por correo?
        val userId = UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .firstOrNull()
            ?.get(UsuarioTable.usuarioId)
            ?: run {
                val id = UUID.randomUUID()

                // contraseña random solo para cumplir NOT NULL
                val randomPassword = UUID.randomUUID().toString()
                val passwordHash   = hash(randomPassword)

                UsuarioTable.insert {
                    it[usuarioId]       = id
                    it[correo]          = email
                    it[nombre]          = email.substringBefore("@")
                    it[contrasenaHash]  = passwordHash      // ⬅️ usa aquí el campo de tu tabla
                    it[idioma]          = "es"              // si tienes default puedes omitir
                    it[estado]          = "activo"          // idem
                    it[rol]             = "user"
                    // fecha_creacion la pone el DEFAULT now()
                }
                id
            }

        // 3) Crear vínculo OAuth
        OauthAccountTable.insert {
            it[oauthId]       = UUID.randomUUID()
            it[provider]      = "google"
            it[subject]       = sub
            it[OauthAccountTable.email] = email
            it[emailVerified] = true
            it[usuarioId]     = userId
        }

        userId
    }
}

private suspend fun <T> db(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
