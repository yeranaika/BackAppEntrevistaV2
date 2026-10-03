package CONFIGURACION

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import data.tables.usuarios.ConsentimientoTable
import MODELOS.TablaObjetivoCarrera
import MODELOS.TablaPerfil
import MODELOS.TablaUsuario
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

// Al levantar con docker compose, Postgres puede tardar en aceptar conexiones.
private const val ESPERA_MAXIMA_CONEXION_INICIAL_MS = 30_000L
private const val DRIVER_POSTGRES = "org.postgresql.Driver"

/**
 * Abre el pool de conexiones (Hikari) y lo registra como base de datos por defecto de Exposed.
 * El pool se cierra cuando la aplicación se detiene.
 */
fun Application.configurarBaseDatos(config: ConfiguracionBaseDatos): Database {
    val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.url
        username = config.usuario
        password = config.contrasena
        driverClassName = DRIVER_POSTGRES
        maximumPoolSize = config.tamanoMaximoPool
        initializationFailTimeout = ESPERA_MAXIMA_CONEXION_INICIAL_MS
    })
    monitor.subscribe(ApplicationStopped) { pool.close() }

    val db = Database.connect(pool)
    // Transitorio: el esquema real lo crea src/DB/*.sql. Se retira cuando se valide que no hay
    // diferencias entre las tablas Exposed y el SQL (ver PLAN_REFACTORIZACION, Fase 0).
    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(
            TablaUsuario,
            ConsentimientoTable,
            TablaPerfil,
            TablaObjetivoCarrera
        )
    }
    log.info("Base de datos conectada (pool máximo ${config.tamanoMaximoPool})")
    return db
}
