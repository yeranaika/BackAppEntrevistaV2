package CONFIGURACION

import INTEGRACIONES.ClienteCorreoSmtp
import INTEGRACIONES.ClienteGoogleIdentidad
import INTEGRACIONES.EnviadorCorreo
import INTEGRACIONES.ProveedorAnthropic
import INTEGRACIONES.ProveedorOpenAi
import INTEGRACIONES.TipoProveedorIa
import INTEGRACIONES.crearClienteHttpLlm
import MODELOS.LectorCatalogoExposed
import MODELOS.RepositorioGeneracionPreguntaIaExposed
import MODELOS.RepositorioPreguntaExposed
import MODELOS.RepositorioCuentaOAuth
import MODELOS.RepositorioCuentaOAuthExposed
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioObjetivoCarreraExposed
import MODELOS.RepositorioPerfil
import MODELOS.RepositorioPerfilExposed
import MODELOS.RepositorioRecuperacionContrasena
import MODELOS.RepositorioRecuperacionContrasenaExposed
import MODELOS.RepositorioRefreshToken
import MODELOS.RepositorioRefreshTokenExposed
import MODELOS.RepositorioUsuarioExposed
import SERVICIOS.ServicioAdminUsuario
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioOnboarding
import SERVICIOS.ServicioPregunta
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import data.repository.billing.SuscripcionRepository
import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import data.repository.skills.CargoSkillRepository
import data.repository.sync.SyncRepository
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.RecordatorioPreferenciaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.exposed.sql.Database
import security.billing.GooglePlayBillingService
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

    /** Tareas que no deben bloquear la respuesta HTTP (ej: enviar correos). Se cancelan al apagar. */
    private val tareasSegundoPlano = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------- Repositorios ----------
    // Una sola instancia sirve a RepositorioUsuario y a LectorUsuarioSesion.
    val repositorioUsuario = RepositorioUsuarioExposed()
    val repositorioPerfil: RepositorioPerfil = RepositorioPerfilExposed()
    val repositorioObjetivo: RepositorioObjetivoCarrera = RepositorioObjetivoCarreraExposed()
    val repositorioRecuperacion: RepositorioRecuperacionContrasena = RepositorioRecuperacionContrasenaExposed()
    val repositorioRefreshToken: RepositorioRefreshToken = RepositorioRefreshTokenExposed()
    val repositorioCuentaOAuth: RepositorioCuentaOAuth = RepositorioCuentaOAuthExposed()
    val repositorioConsentimiento = ConsentimientoRepository()
    val repositorioTextoConsentimiento = ConsentTextRepository(db)
    val repositorioRecordatorio = RecordatorioPreferenciaRepository()
    val repositorioSuscripcion = SuscripcionRepository()
    val repositorioSincronizacion = SyncRepository()
    val repositorioMercadoSkill = SkillMarketRepository(db)
    val repositorioCargo = CargoRepository(db)
    val repositorioCargoSkill = CargoSkillRepository(db)
    val repositorioPregunta = RepositorioPreguntaExposed()
    val repositorioGeneracionPregunta = RepositorioGeneracionPreguntaIaExposed()
    val lectorCatalogo = LectorCatalogoExposed()

    // ---------- Integraciones externas ----------
    val cache = RedisCacheService(
        host = configuracion.redis.host,
        port = configuracion.redis.puerto,
        password = configuracion.redis.contrasena
    )

    val enviadorCorreo: EnviadorCorreo = ClienteCorreoSmtp(configuracion.correo)

    val clienteMercadoLaboral = JobMarketClient(
        rapidApiKey = configuracion.mercadoLaboral.apiKey,
        rapidApiHost = configuracion.mercadoLaboral.apiHost
    )

    private val clienteHttpLlm = crearClienteHttpLlm()

    // Siempre montados: sin API key responden 503 provider_not_configured.
    val proveedoresIa = mapOf(
        TipoProveedorIa.OPENAI to ProveedorOpenAi(configuracion.llm.openAiApiKey, clienteHttpLlm),
        TipoProveedorIa.ANTHROPIC to ProveedorAnthropic(configuracion.llm.anthropicApiKey, clienteHttpLlm)
    )

    val servicioFacturacion = GooglePlayBillingService(
        suscripcionRepo = repositorioSuscripcion,
        packageName = configuracion.googlePlay.paquete,
        serviceAccountJsonBase64 = configuracion.googlePlay.cuentaServicioJsonBase64,
        useMock = configuracion.googlePlay.esSimulado
    )

    val verificadorGoogle = ClienteGoogleIdentidad(configuracion.google.clientId)

    // ---------- Servicios de negocio ----------
    val servicioToken = ServicioToken(
        repositorio = repositorioRefreshToken,
        usuarios = repositorioUsuario,
        jwt = configuracion.jwt
    )

    val servicioLogin = ServicioLogin(
        usuarios = repositorioUsuario,
        cuentasOAuth = repositorioCuentaOAuth,
        verificadorGoogle = verificadorGoogle,
        tokens = servicioToken
    )

    val servicioUsuario = ServicioUsuario(
        usuarios = repositorioUsuario,
        perfiles = repositorioPerfil,
        objetivos = repositorioObjetivo,
        tokens = servicioToken
    )

    val servicioOnboarding = ServicioOnboarding(
        perfiles = repositorioPerfil,
        objetivos = repositorioObjetivo
    )

    val servicioContrasena = ServicioContrasena(
        lectorUsuarios = repositorioUsuario,
        usuarios = repositorioUsuario,
        cuentasOAuth = repositorioCuentaOAuth,
        codigos = repositorioRecuperacion,
        correo = enviadorCorreo,
        tokens = servicioToken,
        tareasSegundoPlano = tareasSegundoPlano
    )

    val servicioAdminUsuario = ServicioAdminUsuario(
        usuarios = repositorioUsuario,
        contrasenas = servicioContrasena,
        tokens = servicioToken
    )

    val servicioPregunta = ServicioPregunta(repositorioPregunta, lectorCatalogo)

    val servicioGeneracionPregunta = ServicioGeneracionPregunta(
        proveedores = proveedoresIa,
        repositorio = repositorioGeneracionPregunta,
        catalogo = lectorCatalogo
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
        tareasSegundoPlano.cancel()
        workerTendencias.stop()
        clienteMercadoLaboral.close()
        clienteHttpLlm.close()
        cache.close()
    }
}
