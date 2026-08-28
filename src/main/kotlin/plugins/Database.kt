// plugins/Database.kt
package plugins

import data.tables.usuarios.UsuarioTable
import data.tables.usuarios.ConsentimientoTable
import data.tables.usuarios.ProfileTable
import data.tables.auth.RecoveryCodeTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// instancia de la base de datos para usar en application
object DatabaseFactory {
    @Volatile private var _db: Database? = null
    val db: Database
        get() = _db ?: error("Database not initialized. Call configureDatabase() first.")
    internal fun set(database: Database) { _db = database }
}

fun Application.configureDatabase() {
    val s = settings()
    var last: Throwable? = null
    repeat(30) { attempt ->
        try {
            // 1) CONECTA y GUARDA
            val db = Database.connect(
                url = s.dbUrl,
                driver = "org.postgresql.Driver",
                user = s.dbUser,
                password = s.dbPass
            )
            DatabaseFactory.set(db)   // ← ← ← IMPORTANTE

            // 2) Migraciones ligeras / createMissing...
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(
                    UsuarioTable,
                    ConsentimientoTable,
                    ProfileTable, // ← Tabla de perfil de usuario
                    RecoveryCodeTable, // ← Tabla de códigos de recuperación
                    data.tables.usuarios.ObjetivoCarreraTable
                )
            }
            log.info("✅ DB conectada en intento ${attempt + 1}")
            return
        } catch (e: Throwable) {
            last = e
            log.warn("DB no lista aún (intento ${attempt + 1}): ${e.message}")
            Thread.sleep(1_000)
        }
    }
    error("No se pudo conectar a la DB: ${last?.message}")
}
