package CONFIGURACION

import INTEGRACIONES.CacheRedis
import INTEGRACIONES.ClienteCorreoSmtp
import INTEGRACIONES.ClienteGooglePlay
import INTEGRACIONES.ClienteGoogleIdentidad
import INTEGRACIONES.ClienteMercadoLaboralHttp
import INTEGRACIONES.ContadorIntentosCache
import INTEGRACIONES.EnviadorCorreo
import INTEGRACIONES.FuenteDocumentosLegalesArchivo
import INTEGRACIONES.ProveedorAnthropic
import INTEGRACIONES.ProveedorConResiliencia
import INTEGRACIONES.ProveedorOpenAi
import INTEGRACIONES.TipoProveedorIa
import INTEGRACIONES.VerificadorCompraGoogle
import INTEGRACIONES.VerificadorCompraSimulado
import INTEGRACIONES.crearClienteHttpLlm
import MODELOS.LectorCatalogoExposed
import MODELOS.RepositorioConsentimientoExposed
import MODELOS.RepositorioCuentaOAuth
import MODELOS.RepositorioCuentaOAuthExposed
import MODELOS.RepositorioGeneracionPreguntaIaExposed
import MODELOS.RepositorioMercadoExposed
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioObjetivoCarreraExposed
import MODELOS.RepositorioPerfil
import MODELOS.RepositorioPerfilExposed
import MODELOS.RepositorioPreguntaExposed
import MODELOS.RepositorioRecordatorioExposed
import MODELOS.RepositorioRecuperacionContrasena
import MODELOS.RepositorioRecuperacionContrasenaExposed
import MODELOS.RepositorioRefreshToken
import MODELOS.RepositorioRefreshTokenExposed
import MODELOS.RepositorioSuscripcionExposed
import MODELOS.RepositorioTextoConsentimientoExposed
import MODELOS.RepositorioUsuarioExposed
import SERVICIOS.ServicioAdminUsuario
import SERVICIOS.ServicioConsentimiento
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioMercado
import SERVICIOS.ServicioOnboarding
import SERVICIOS.ServicioPregunta
import SERVICIOS.ServicioRecordatorio
import SERVICIOS.ServicioRequisitosCargo
import SERVICIOS.ServicioSuscripcion
import SERVICIOS.ServicioTendenciasSkill
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import SERVICIOS.TareaSincronizacionMercado
import data.repository.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val PREFIJO_INTENTOS_LOGIN = "login:fallos:"

/**
 * Único lugar donde se construyen repositorios, integraciones y servicios.
 * Cada instancia se crea una vez y se inyecta en las rutas por constructor.
 */
class ContenedorDependencias(configuracion: ConfiguracionGeneral) : AutoCloseable {

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
    val repositorioPregunta = RepositorioPreguntaExposed()
    val repositorioGeneracionPregunta = RepositorioGeneracionPreguntaIaExposed()
    val lectorCatalogo = LectorCatalogoExposed()
    // Una sola instancia sirve a LectorMercado y a EscritorMercado.
    val repositorioMercado = RepositorioMercadoExposed()
    val repositorioSincronizacion = SyncRepository()

    // ---------- Integraciones externas (todas con tiempo máximo, reintentos y cortocircuito) ----------
    val cache = CacheRedis(configuracion.redis)
    val enviadorCorreo: EnviadorCorreo = ClienteCorreoSmtp(configuracion.correo)
    val clienteMercadoLaboral = ClienteMercadoLaboralHttp(configuracion.mercadoLaboral)
    val verificadorGoogle = ClienteGoogleIdentidad(configuracion.google.clientId)
    private val clienteGooglePlay = if (configuracion.googlePlay.esSimulado) null else ClienteGooglePlay(configuracion.googlePlay)
    val verificadorCompras: VerificadorCompraGoogle = clienteGooglePlay ?: VerificadorCompraSimulado()
    val documentosLegales = FuenteDocumentosLegalesArchivo()

    private val clienteHttpLlm = crearClienteHttpLlm()

    // Siempre montados: sin API key responden 503 provider_not_configured.
    val proveedoresIa = mapOf(
        TipoProveedorIa.OPENAI to ProveedorConResiliencia(ProveedorOpenAi(configuracion.llm.openAiApiKey, clienteHttpLlm), "openai"),
        TipoProveedorIa.ANTHROPIC to ProveedorConResiliencia(ProveedorAnthropic(configuracion.llm.anthropicApiKey, clienteHttpLlm), "anthropic")
    )

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
        tokens = servicioToken,
        intentosFallidos = ContadorIntentosCache(cache, PREFIJO_INTENTOS_LOGIN, MINUTOS_BLOQUEO_LOGIN * 60)
    )

    val servicioUsuario = ServicioUsuario(repositorioUsuario, repositorioPerfil, repositorioObjetivo, servicioToken)

    val servicioOnboarding = ServicioOnboarding(repositorioPerfil, repositorioObjetivo)

    val servicioContrasena = ServicioContrasena(
        lectorUsuarios = repositorioUsuario,
        usuarios = repositorioUsuario,
        cuentasOAuth = repositorioCuentaOAuth,
        codigos = repositorioRecuperacion,
        correo = enviadorCorreo,
        tokens = servicioToken,
        tareasSegundoPlano = tareasSegundoPlano
    )

    val servicioAdminUsuario = ServicioAdminUsuario(repositorioUsuario, servicioContrasena, servicioToken)

    val servicioPregunta = ServicioPregunta(repositorioPregunta, lectorCatalogo)

    val servicioGeneracionPregunta = ServicioGeneracionPregunta(proveedoresIa, repositorioGeneracionPregunta, lectorCatalogo)

    val servicioRequisitosCargo = ServicioRequisitosCargo(repositorioMercado, repositorioMercado, clienteMercadoLaboral)

    val servicioMercado = ServicioMercado(repositorioMercado, repositorioMercado, servicioRequisitosCargo, cache)

    val servicioTendencias = ServicioTendenciasSkill(
        lector = repositorioMercado,
        escritor = repositorioMercado,
        clienteMercado = clienteMercadoLaboral,
        requisitos = servicioRequisitosCargo,
        alActualizar = { servicioMercado.invalidarCache() }
    )

    val servicioConsentimiento = ServicioConsentimiento(
        RepositorioTextoConsentimientoExposed(), RepositorioConsentimientoExposed(), documentosLegales
    )

    val servicioRecordatorio = ServicioRecordatorio(RepositorioRecordatorioExposed())

    val servicioSuscripcion = ServicioSuscripcion(RepositorioSuscripcionExposed(), verificadorCompras)

    private val tareaMercado = TareaSincronizacionMercado(servicioTendencias)

    fun iniciarTareasSegundoPlano() {
        tareaMercado.iniciar()
    }

    override fun close() {
        tareasSegundoPlano.cancel()
        tareaMercado.close()
        clienteMercadoLaboral.close()
        clienteGooglePlay?.close()
        clienteHttpLlm.close()
        cache.close()
    }
}
