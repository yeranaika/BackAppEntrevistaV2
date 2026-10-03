package CONFIGURACION

import data.repository.AppAndroid.OnboardingRepository
import data.repository.admin.AdminUserRepository
import data.repository.billing.SuscripcionRepository
import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import data.repository.skills.CargoSkillRepository
import data.repository.sync.SyncRepository
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.usuarios.PasswordResetRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.RecordatorioPreferenciaRepository
import data.repository.usuarios.UserRepository
import INTEGRACIONES.ClienteGoogleIdentidad
import MODELOS.LectorUsuarioSesionExposed
import MODELOS.RepositorioCuentaOAuth
import MODELOS.RepositorioCuentaOAuthExposed
import MODELOS.RepositorioRefreshToken
import MODELOS.RepositorioRefreshTokenExposed
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioToken
import org.jetbrains.exposed.sql.Database
import security.billing.GooglePlayBillingService
import services.AiQuestionGeneratorService
import services.AuthService
import services.EmailService
import services.cache.RedisCacheService
import services.market.CargoSkillGeneratorService
import services.market.JobMarketClient
import services.market.SkillTrendWorker

/**
 * Único lugar donde se construyen repositorios, integraciones y servicios.
 * Cada instancia se crea una vez y se inyecta en las rutas por constructor.
 */
class ContenedorDependencias(
    configuracion: ConfiguracionGeneral,
    db: Database
) : AutoCloseable {

    // ---------- Repositorios ----------
    val repositorioUsuario = UserRepository()
    val repositorioPerfil = ProfileRepository()
    val repositorioObjetivo = ObjetivoCarreraRepository()
    val repositorioOnboarding = OnboardingRepository()
    val lectorUsuarioSesion = LectorUsuarioSesionExposed(repositorioUsuario)
    val repositorioRefreshToken: RepositorioRefreshToken = RepositorioRefreshTokenExposed()
    val repositorioCuentaOAuth: RepositorioCuentaOAuth = RepositorioCuentaOAuthExposed()
    val repositorioRecuperacion = PasswordResetRepository(repositorioUsuario)
    val repositorioConsentimiento = ConsentimientoRepository()
    val repositorioTextoConsentimiento = ConsentTextRepository(db)
    val repositorioRecordatorio = RecordatorioPreferenciaRepository()
    val repositorioSuscripcion = SuscripcionRepository()
    val repositorioSincronizacion = SyncRepository()
    val repositorioAdminUsuario = AdminUserRepository(db)
    val repositorioMercadoSkill = SkillMarketRepository(db)
    val repositorioCargo = CargoRepository(db)
    val repositorioCargoSkill = CargoSkillRepository(db)

    // ---------- Integraciones externas ----------
    val cache = RedisCacheService(
        host = configuracion.redis.host,
        port = configuracion.redis.puerto,
        password = configuracion.redis.contrasena
    )

    val servicioCorreo = EmailService(
        smtpHost = configuracion.correo.hostSmtp,
        smtpPort = configuracion.correo.puertoSmtp,
        username = configuracion.correo.usuario,
        password = configuracion.correo.contrasena,
        fromEmail = configuracion.correo.usuario
    )

    val clienteMercadoLaboral = JobMarketClient(
        rapidApiKey = configuracion.mercadoLaboral.apiKey,
        rapidApiHost = configuracion.mercadoLaboral.apiHost
    )

    // Siempre montado: sin keys responde 503 provider_not_configured.
    val servicioGeneracionPreguntas = AiQuestionGeneratorService(
        openAiKey = configuracion.llm.openAiApiKey,
        anthropicKey = configuracion.llm.anthropicApiKey
    )

    val servicioFacturacion = GooglePlayBillingService(
        userRepo = repositorioUsuario,
        suscripcionRepo = repositorioSuscripcion,
        packageName = configuracion.googlePlay.paquete,
        serviceAccountJsonBase64 = configuracion.googlePlay.cuentaServicioJsonBase64,
        useMock = configuracion.googlePlay.esSimulado
    )

    val verificadorGoogle = ClienteGoogleIdentidad(configuracion.google.clientId)

    // ---------- Servicios de negocio ----------
    val servicioToken = ServicioToken(
        repositorio = repositorioRefreshToken,
        usuarios = lectorUsuarioSesion,
        jwt = configuracion.jwt
    )

    val servicioLogin = ServicioLogin(
        usuarios = lectorUsuarioSesion,
        cuentasOAuth = repositorioCuentaOAuth,
        verificadorGoogle = verificadorGoogle,
        tokens = servicioToken
    )

    val servicioAuth = AuthService(
        users = repositorioUsuario,
        profiles = repositorioPerfil,
        tokens = servicioToken
    )

    val generadorSkillsCargo = CargoSkillGeneratorService(
        cargoRepository = repositorioCargo,
        skillMarketRepository = repositorioMercadoSkill,
        jobMarketClient = clienteMercadoLaboral
    )

    val workerTendencias = SkillTrendWorker(
        repository = repositorioMercadoSkill,
        jobMarketClient = clienteMercadoLaboral,
        cargoSkillGenerator = generadorSkillsCargo
    )

    fun iniciarTareasSegundoPlano() {
        workerTendencias.start()
    }

    override fun close() {
        workerTendencias.stop()
        clienteMercadoLaboral.close()
        servicioGeneracionPreguntas.close()
        cache.close()
    }
}
