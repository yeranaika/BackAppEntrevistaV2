package services

import CONFIGURACION.ConfiguracionJwt
import INTEGRACIONES.VerificadorIdentidadGoogle
import MODELOS.LectorUsuarioSesionExposed
import MODELOS.RepositorioCuentaOAuthExposed
import MODELOS.RepositorioRefreshTokenExposed
import MODELOS.TablaCuentaOAuth
import MODELOS.TablaRefreshToken
import PRUEBAS.DOBLES.GoogleEnMemoria
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioToken
import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import data.tables.usuarios.ProfileTable
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/** BD H2 en memoria (modo PostgreSQL) con las tablas de usuarios y sesión. Una nueva por test. */
object AuthTestDb {
    const val ISSUER = "test-issuer"
    const val AUDIENCE = "test-audience"
    val jwt = ConfiguracionJwt(emisor = ISSUER, audiencia = AUDIENCE, secreto = "test-secret")
    val algorithm: Algorithm get() = jwt.algoritmo

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
            listOf(UsuarioTable, ProfileTable, TablaRefreshToken, TablaCuentaOAuth)
                .forEach { SchemaUtils.createMissingTablesAndColumns(it) }
        }
        return db
    }

    fun servicioToken(users: UserRepository = UserRepository()) =
        ServicioToken(RepositorioRefreshTokenExposed(), LectorUsuarioSesionExposed(users), jwt)

    fun service(users: UserRepository = UserRepository()) = AuthService(
        users = users,
        profiles = ProfileRepository(),
        tokens = servicioToken(users)
    )

    fun servicioLogin(
        users: UserRepository = UserRepository(),
        google: VerificadorIdentidadGoogle = GoogleEnMemoria()
    ) = ServicioLogin(
        usuarios = LectorUsuarioSesionExposed(users),
        cuentasOAuth = RepositorioCuentaOAuthExposed(),
        verificadorGoogle = google,
        tokens = servicioToken(users)
    )
}
