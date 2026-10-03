package services

import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.RefreshTokenRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import data.tables.usuarios.OauthAccountTable
import data.tables.usuarios.ProfileTable
import data.tables.usuarios.RefreshTokenTable
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import security.auth.GoogleTokenVerifier
import java.util.UUID

/** BD H2 en memoria (modo PostgreSQL) con las tablas que usa AuthService. Una nueva por test. */
object AuthTestDb {
    const val ISSUER = "test-issuer"
    const val AUDIENCE = "test-audience"
    val algorithm: Algorithm = Algorithm.HMAC512("test-secret")

    fun connect(): Database {
        val db = Database.connect(
            // Igual que producción (currentSchema=app): todas las tablas en el esquema APP
            url = "jdbc:h2:mem:testdb_auth_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
                "INIT=CREATE SCHEMA IF NOT EXISTS APP\\;SET SCHEMA APP",
            driver = "org.h2.Driver"
        )
        // Los repositorios usan la BD por defecto (sin pasar db explícita)
        TransactionManager.defaultDatabase = db
        transaction(db) {
            // Una tabla por llamada: en H2 crear UsuarioTable junto a la tabla que la referencia
            // repite el ALTER ... ADD CONSTRAINT usuario_correo_unique
            listOf(UsuarioTable, ProfileTable, RefreshTokenTable, OauthAccountTable)
                .forEach { SchemaUtils.createMissingTablesAndColumns(it) }
        }
        return db
    }

    fun service(users: UserRepository = UserRepository()) = AuthService(
        users = users,
        profiles = ProfileRepository(),
        refreshRepo = RefreshTokenRepository(),
        oauthRepo = UsuariosOAuthRepositoryImpl(),
        googleVerifier = GoogleTokenVerifier("test-client-id")
    )
}
