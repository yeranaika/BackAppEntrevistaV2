package PRUEBAS.DOBLES

import CONFIGURACION.ConfiguracionLimites
import CONFIGURACION.configurarErrores
import CONFIGURACION.configurarLimiteSolicitudes
import CONFIGURACION.configurarSerializacion
import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorConsentimiento
import CONTROLADORES.controladorMercado
import CONTROLADORES.controladorRecordatorio
import CONTROLADORES.controladorEntrevista
import CONTROLADORES.controladorNivelacion
import CONTROLADORES.controladorPractica
import CONTROLADORES.controladorPruebaPractica
import CONTROLADORES.controladorSalud
import CONTROLADORES.controladorSuscripcion
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorPregunta
import CONTROLADORES.controladorUsuario
import INTEGRACIONES.ContadorIntentosEnMemoria
import INTEGRACIONES.FuenteDocumentosLegalesArchivo
import INTEGRACIONES.TipoProveedorIa
import MODELOS.RepositorioConsentimientoExposed
import MODELOS.RepositorioMercadoExposed
import MODELOS.RepositorioRecordatorioExposed
import MODELOS.RepositorioSuscripcionExposed
import MODELOS.RepositorioTextoConsentimientoExposed
import MODELOS.TablaCargo
import MODELOS.TablaCargoSkill
import MODELOS.TablaCodigoSuscripcion
import MODELOS.TablaConsentimiento
import MODELOS.TablaRecordatorio
import MODELOS.TablaSkill
import MODELOS.TablaSkillTendencia
import MODELOS.TablaSuscripcion
import MODELOS.TablaTextoConsentimiento
import INTEGRACIONES.VerificadorIdentidadGoogle
import MODELOS.LectorCatalogoExposed
import MODELOS.RepositorioGeneracionPreguntaIaExposed
import MODELOS.RepositorioPreguntaExposed
import MODELOS.TablaGeneracionPreguntaIa
import MODELOS.TablaOpcionPregunta
import MODELOS.TablaPregunta
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
import SERVICIOS.ServicioConsentimiento
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioMercado
import SERVICIOS.ServicioRecordatorio
import SERVICIOS.ServicioRequisitosCargo
import SERVICIOS.ServicioSuscripcion
import SERVICIOS.ServicioTendenciasSkill
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioOnboarding
import SERVICIOS.ServicioPregunta
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import com.auth0.jwt.JWT
import MODELOS.RepositorioMetricaVideoExposed
import MODELOS.RepositorioSesionEntrevistaExposed
import MODELOS.RepositorioNivelSkillExposed
import MODELOS.RepositorioNivelacionExposed
import MODELOS.RepositorioPracticaExposed
import MODELOS.RepositorioTestNivelacionExposed
import MODELOS.TablaIntentoTest
import MODELOS.TablaMetricaVideo
import MODELOS.TablaNivelSkillUsuario
import MODELOS.TablaRespuestaPractica
import MODELOS.TablaResultadoNivelacion
import MODELOS.TablaSesionPractica
import MODELOS.TablaTestNivelacion
import SERVICIOS.CorrectorRespuestas
import SERVICIOS.EvaluadorRespuestaFreemium
import SERVICIOS.ServicioNivelacion
import SERVICIOS.ServicioPractica
import SERVICIOS.ServicioPruebasApp
import SERVICIOS.ServicioTestNivelacion
import MODELOS.TablaSesionEntrevista
import MODELOS.TablaSesionPreguntaRespuesta
import SERVICIOS.ResolutorContextoPrueba
import SERVICIOS.SelectorPreguntas
import SERVICIOS.ServicioEntrevista
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
import org.jetbrains.exposed.sql.insert
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
            listOf(
                TablaUsuario, TablaPerfil, TablaObjetivoCarrera, TablaRefreshToken, TablaCuentaOAuth, TablaRecuperacionContrasena,
                TablaCargo, TablaSkill, TablaSkillTendencia, TablaCargoSkill, TablaPregunta, TablaOpcionPregunta, TablaGeneracionPreguntaIa,
                TablaTextoConsentimiento, TablaConsentimiento, TablaRecordatorio, TablaCodigoSuscripcion, TablaSuscripcion,
                TablaSesionEntrevista, TablaSesionPreguntaRespuesta, TablaMetricaVideo,
                TablaTestNivelacion, TablaIntentoTest, TablaResultadoNivelacion, TablaSesionPractica, TablaRespuestaPractica,
                TablaNivelSkillUsuario
            )
                .forEach { SchemaUtils.createMissingTablesAndColumns(it) }
        }
        return db
    }
}

/**
 * Servicios y repositorios reales sobre H2, con correo y Google falsos.
 * Los correos se "envían" en el mismo hilo (Dispatchers.Unconfined) para poder leerlos al instante.
 */
class SistemaPrueba(
    google: VerificadorIdentidadGoogle = GoogleEnMemoria(),
    /** LLM falso: por defecto responde un lote válido de 1 pregunta abierta. */
    val proveedorIa: ProveedorIaGrabador = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto")),
    /** Por defecto holgados para que las pruebas no choquen con el límite; se bajan para probarlo. */
    private val limites: ConfiguracionLimites = ConfiguracionLimites(registrosPorIp = 1_000, recuperacionesPorIp = 1_000),
    val clienteMercado: ClienteMercadoLaboralFalso = ClienteMercadoLaboralFalso(emptyMap()),
    val verificadorCompras: VerificadorCompraFalso = VerificadorCompraFalso(),
    val procesadorEntrevista: ProcesadorEntrevistaGrabador = ProcesadorEntrevistaGrabador(),
    val reloj: RelojAjustable = RelojAjustable()
) {
    val db = BdPrueba.conectar()
    val usuarios = RepositorioUsuarioExposed()
    val perfiles = RepositorioPerfilExposed()
    val objetivos = RepositorioObjetivoCarreraExposed()
    val refreshTokens = RepositorioRefreshTokenExposed()
    val cuentasOAuth = RepositorioCuentaOAuthExposed()
    val codigos = RepositorioRecuperacionContrasenaExposed()
    val correo = CorreoEnMemoria()

    val tokens = ServicioToken(refreshTokens, usuarios, JWT_PRUEBA)
    val intentosLogin = ContadorIntentosEnMemoria()
    val login = ServicioLogin(usuarios, cuentasOAuth, google, tokens, intentosLogin)
    val usuario = ServicioUsuario(usuarios, perfiles, objetivos, tokens)
    val onboarding = ServicioOnboarding(perfiles, objetivos)
    val contrasena = ServicioContrasena(
        usuarios, usuarios, cuentasOAuth, codigos, correo, tokens, CoroutineScope(Dispatchers.Unconfined)
    )
    val admin = ServicioAdminUsuario(usuarios, contrasena, tokens)

    val catalogo = LectorCatalogoExposed()
    val preguntas = RepositorioPreguntaExposed()
    val generaciones = RepositorioGeneracionPreguntaIaExposed()
    val pregunta = ServicioPregunta(preguntas, catalogo)
    val generacion = ServicioGeneracionPregunta(
        mapOf(TipoProveedorIa.OPENAI to proveedorIa, TipoProveedorIa.ANTHROPIC to proveedorIa), generaciones, catalogo
    )

    val cache = CacheEnMemoria()
    val mercadoRepo = RepositorioMercadoExposed()
    val requisitos = ServicioRequisitosCargo(mercadoRepo, mercadoRepo, clienteMercado)
    val mercado = ServicioMercado(mercadoRepo, mercadoRepo, requisitos, cache)
    val tendencias = ServicioTendenciasSkill(mercadoRepo, mercadoRepo, clienteMercado, requisitos) { mercado.invalidarCache() }
    val documentos = FuenteDocumentosLegalesArchivo()
    val consentimiento = ServicioConsentimiento(RepositorioTextoConsentimientoExposed(), RepositorioConsentimientoExposed(), documentos)
    val recordatorio = ServicioRecordatorio(RepositorioRecordatorioExposed())
    val suscripciones = RepositorioSuscripcionExposed()
    val suscripcion = ServicioSuscripcion(suscripciones, verificadorCompras)

    val sesionesEntrevista = RepositorioSesionEntrevistaExposed()
    val metricasVideo = RepositorioMetricaVideoExposed()
    val selectorPreguntas = SelectorPreguntas(preguntas, mercadoRepo)
    val resolutorContexto = ResolutorContextoPrueba(mercadoRepo, perfiles, objetivos)
    val entrevista = ServicioEntrevista(
        sesiones = sesionesEntrevista,
        metricas = metricasVideo,
        selector = selectorPreguntas,
        contexto = resolutorContexto,
        procesador = procesadorEntrevista,
        tareasSegundoPlano = CoroutineScope(Dispatchers.Unconfined),
        reloj = reloj
    )

    val evaluador = EvaluadorRespuestaFreemium()
    val corrector = CorrectorRespuestas(evaluador)
    val practicasRepo = RepositorioPracticaExposed()
    val nivelacionesRepo = RepositorioNivelacionExposed()
    val testsNivelacionRepo = RepositorioTestNivelacionExposed()
    val nivelesSkill = RepositorioNivelSkillExposed()
    val practica = ServicioPractica(practicasRepo, preguntas, selectorPreguntas, resolutorContexto, mercadoRepo, nivelesSkill, corrector, reloj)
    val nivelacion = ServicioNivelacion(
        nivelacionesRepo, testsNivelacionRepo, preguntas, selectorPreguntas, resolutorContexto, mercadoRepo, nivelesSkill, corrector, reloj
    )
    val testsNivelacion = ServicioTestNivelacion(testsNivelacionRepo, preguntas, mercadoRepo)
    val pruebasApp = ServicioPruebasApp(entrevista, practica, nivelacion, sesionesEntrevista, practicasRepo, nivelacionesRepo)

    /** Inserta un cargo y una skill en el catálogo y devuelve sus ids. */
    fun crearCatalogo(nombreCargo: String = "Backend Developer", nombreSkill: String = "Kotlin"): Pair<UUID, UUID> {
        val cargoId = UUID.randomUUID()
        val skillId = UUID.randomUUID()
        transaction(db) {
            TablaCargo.insert { it[TablaCargo.cargoId] = cargoId; it[nombre] = nombreCargo; it[area] = "backend" }
            TablaSkill.insert { it[TablaSkill.skillId] = skillId; it[nombre] = nombreSkill; it[categoria] = "tecnica"; it[tipoArea] = "backend" }
        }
        return cargoId to skillId
    }

    /** Mismos plugins y controladores que producción, con autenticación de prueba. */
    fun montar(app: Application) = with(app) {
        // La misma serialización que producción: el contrato JSON depende de ella.
        configurarSerializacion()
        configurarErrores()
        configurarLimiteSolicitudes(limites)
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
            controladorPregunta(pregunta, generacion)
            controladorMercado(mercado, tendencias)
            controladorConsentimiento(consentimiento, documentos)
            controladorRecordatorio(recordatorio)
            controladorSuscripcion(suscripcion)
            controladorSalud()
            controladorEntrevista(entrevista)
            controladorPractica(practica, pruebasApp, evaluador)
            controladorNivelacion(nivelacion, testsNivelacion)
            controladorPruebaPractica(pruebasApp)
        }
    }
}
