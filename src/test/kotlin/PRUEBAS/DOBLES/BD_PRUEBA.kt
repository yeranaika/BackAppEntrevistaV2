package PRUEBAS.DOBLES

import CONFIGURACION.configurarErrores
import CONFIGURACION.configurarSerializacion
import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorUsuario
import INTEGRACIONES.VerificadorIdentidadGoogle
import MODELOS.RepositorioCuentaOAuthExposed
import MODELOS.RepositorioObjetivoCarreraExposed
import MODELOS.RepositorioPerfilExposed
import MODELOS.RepositorioRecuperacionContrasenaExposed
import MODELOS.RepositorioRefreshTokenExposed
import MODELOS.RepositorioUsuarioExposed
import MODELOS.TablaCuentaOAuth
import MODELOS.TablaObjetivoCarrera
import MODELOS.TablaPerfil
import MODELOS.TablaRecuperacionContrasena
import MODELOS.TablaRefreshToken
import MODELOS.TablaUsuario
import SERVICIOS.ServicioAdminUsuario
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioOnboarding
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import com.auth0.jwt.JWT
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/** BD H2 en memoria (modo PostgreSQL) con las tablas de usuarios y sesión. Una nueva por prueba. */
object BdPrueba {
    fun conectar(): Database {
        val db = Database.connect(
            // Igual que producción (currentSchema=app): todas las tablas en el esquema APP
            url = "jdbc:h2:mem:prueba_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL;" +
                "INIT=CREATE SCHEMA IF NOT EXISTS APP\\;SET SCHEMA APP",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = db
        transaction(db) {
            // Una tabla por llamada: en H2 crear TablaUsuario junto a una tabla que la referencia
            // repite el ALTER ... ADD CONSTRAINT usuario_correo_unique
            listOf(TablaUsuario, TablaPerfil, TablaObjetivoCarrera, TablaRefreshToken, TablaCuentaOAuth, TablaRecuperacionContrasena)
                .forEach { SchemaUtils.createMissingTablesAndColumns(it) }
        }
        return db
    }
}

/**
 * Servicios y repositorios reales sobre H2, con correo y Google falsos.
 * Los correos se "envían" en el mismo hilo (Dispatchers.Unconfined) para poder leerlos al instante.
 */
class SistemaPrueba(google: VerificadorIdentidadGoogle = GoogleEnMemoria()) {
    val db = BdPrueba.conectar()
    val usuarios = RepositorioUsuarioExposed()
    val perfiles = RepositorioPerfilExposed()
    val objetivos = RepositorioObjetivoCarreraExposed()
    val refreshTokens = RepositorioRefreshTokenExposed()
    val cuentasOAuth = RepositorioCuentaOAuthExposed()
    val codigos = RepositorioRecuperacionContrasenaExposed()
    val correo = CorreoEnMemoria()

    val tokens = ServicioToken(refreshTokens, usuarios, JWT_PRUEBA)
    val login = ServicioLogin(usuarios, cuentasOAuth, google, tokens)
    val usuario = ServicioUsuario(usuarios, perfiles, objetivos, tokens)
    val onboarding = ServicioOnboarding(perfiles, objetivos)
    val contrasena = ServicioContrasena(
        usuarios, usuarios, cuentasOAuth, codigos, correo, tokens, CoroutineScope(Dispatchers.Unconfined)
    )
    val admin = ServicioAdminUsuario(usuarios, contrasena, tokens)

    /** Mismos plugins y controladores que producción, con autenticación de prueba. */
    fun montar(app: Application) = with(app) {
        // La misma serialización que producción: el contrato JSON depende de ella.
        configurarSerializacion()
        configurarErrores()
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(JWT_PRUEBA.algoritmo).withIssuer(JWT_PRUEBA.emisor).withAudience(JWT_PRUEBA.audiencia).build())
                validate { cred -> if (cred.subject != null) JWTPrincipal(cred.payload) else null }
            }
            // Proveedor OAuth de mentira: solo para que authenticate("google-oauth") exista
            oauth("google-oauth") {
                urlProvider = { "http://localhost/auth/google/callback" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "google",
                        authorizeUrl = "http://localhost/authorize",
                        accessTokenUrl = "http://localhost/token",
                        requestMethod = HttpMethod.Post,
                        clientId = "test",
                        clientSecret = "test"
                    )
                }
                client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
            }
        }
        routing {
            controladorLogin(login)
            controladorUsuario(usuario)
            controladorContrasena(contrasena)
            controladorOnboarding(onboarding)
            controladorAdminUsuario(admin)
        }
    }
}
