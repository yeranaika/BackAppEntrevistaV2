package CONFIGURACION

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

// Al levantar con docker compose, Postgres puede tardar en aceptar conexiones.
private const val ESPERA_MAXIMA_CONEXION_INICIAL_MS = 30_000L
private const val ESPERA_MAXIMA_CONEXION_MS = 5_000L
private const val DRIVER_POSTGRES = "org.postgresql.Driver"

/**
 * Abre el pool de conexiones (Hikari) y lo registra como base de datos por defecto de Exposed.
 * El esquema lo crean los SQL de src/DB y de migrations: la aplicación no crea ni altera tablas.
 */
fun Application.configurarBaseDatos(config: ConfiguracionBaseDatos): Database {
    val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.url
        username = config.usuario
        password = config.contrasena
        driverClassName = DRIVER_POSTGRES
        maximumPoolSize = config.tamanoMaximoPool
        initializationFailTimeout = ESPERA_MAXIMA_CONEXION_INICIAL_MS
        // Nunca esperar una conexión para siempre: con la BD caída la solicitud falla rápido (503).
        connectionTimeout = ESPERA_MAXIMA_CONEXION_MS
    })
    monitor.subscribe(ApplicationStopped) { pool.close() }
    log.info("Base de datos conectada (pool máximo ${config.tamanoMaximoPool})")
    return Database.connect(pool)
}
