package CONFIGURACION

import INTEGRACIONES.CacheRedis
import INTEGRACIONES.ClienteMotorVision
import INTEGRACIONES.ClienteCorreoSmtp
import INTEGRACIONES.ClienteGooglePlay
import INTEGRACIONES.ClienteGoogleIdentidad
import INTEGRACIONES.ClienteMercadoLaboralHttp
import INTEGRACIONES.OfertasEnCache
import INTEGRACIONES.ContadorIntentosCache
import INTEGRACIONES.EnviadorCorreo
import INTEGRACIONES.FuenteDocumentosLegalesArchivo
import INTEGRACIONES.ProveedorAnthropic
import INTEGRACIONES.ProveedorPreguntasIa
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
import MODELOS.RepositorioFeedbackGenericoExposed
import MODELOS.RepositorioGeneracionPreguntaIaExposed
import MODELOS.RepositorioMercadoExposed
import MODELOS.LectorCoberturaPreguntasExposed
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
import SERVICIOS.ServicioFeedbackGenerico
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.PlanTokenMimo
import SERVICIOS.ServicioCreditosTokenPlan
import SERVICIOS.ServicioTopeGeneracionIa
import SERVICIOS.TopeGastoGeneracionIa
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioMercado
import SERVICIOS.ServicioContextoMercado
import SERVICIOS.ServicioCoberturaPreguntas
import SERVICIOS.ServicioOnboarding
import SERVICIOS.ServicioPregunta
import SERVICIOS.ServicioRecordatorio
import SERVICIOS.ServicioRequisitosCargo
import SERVICIOS.ServicioSuscripcion
import SERVICIOS.ServicioTendenciasSkill
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import MODELOS.BorradorDatosCamaraExposed
import MODELOS.RepositorioMetricaVideoExposed
import MODELOS.RepositorioLlamadaExposed
import MODELOS.RepositorioPrediccionSimulacionExposed
import MODELOS.RepositorioRevisionTextoExposed
import MODELOS.RepositorioSesionEntrevistaExposed
import MODELOS.RepositorioNivelSkillExposed
import MODELOS.RepositorioNivelacionExposed
import MODELOS.RepositorioPracticaExposed
import MODELOS.RepositorioTestNivelacionExposed
import SERVICIOS.CorrectorRespuestas
import SERVICIOS.EvaluadorRespuesta
import SERVICIOS.EvaluadorRespuestaFreemium
import SERVICIOS.EvaluadorEntrevista
import SERVICIOS.EvaluadorEntrevistaFreemium
import SERVICIOS.EvaluadorEntrevistaIa
import SERVICIOS.ResolutorContextoPrueba
import SERVICIOS.ServicioReporteEntrevista
import SERVICIOS.ServicioCupoIa
import SERVICIOS.VerificadorPremium
import MODELOS.RepositorioReporteExposed
import INTEGRACIONES.MODELO_ANTHROPIC_POR_DEFECTO
import INTEGRACIONES.MODELO_OPENAI_POR_DEFECTO
import INTEGRACIONES.MODELO_MIMO_POR_DEFECTO
import INTEGRACIONES.ProveedorMimo
import SERVICIOS.ServicioNivelacion
import SERVICIOS.ServicioPractica
import SERVICIOS.ServicioPruebasApp
import SERVICIOS.ServicioTestNivelacion
import SERVICIOS.SelectorPreguntas
import SERVICIOS.ServicioEntrevista
import SERVICIOS.ServicioLlamada
import SERVICIOS.ServicioRevisionLocal
import SERVICIOS.ServicioSimulacion
import MODELOS.LectorCostosIaExposed
import SERVICIOS.ServicioCostosIa
import SERVICIOS.TareaPurgaConsentimientos
import SERVICIOS.TareaRescateReportes
import SERVICIOS.TareaSincronizacionMercado
import UTILIDADES.TareasSegundoPlano
import kotlin.time.Duration

private const val PREFIJO_INTENTOS_LOGIN = "login:fallos:"

/**
 * Único lugar donde se construyen repositorios, integraciones y servicios.
 * Cada instancia se crea una vez y se inyecta en las rutas por constructor.
 */
class ContenedorDependencias(configuracion: ConfiguracionGeneral) : AutoCloseable {

    /** Tareas que no deben bloquear la respuesta HTTP (reportes, correos). Al apagar se esperan: ver detenerTareas (D20). */
    private val tareasSegundoPlano = TareasSegundoPlano()

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

    // ---------- Integraciones externas (todas con tiempo máximo, reintentos y cortocircuito) ----------
    val cache = CacheRedis(configuracion.redis)
    val enviadorCorreo: EnviadorCorreo = ClienteCorreoSmtp(configuracion.correo)
    val clienteMercadoLaboral = ClienteMercadoLaboralHttp(configuracion.mercadoLaboral)
    /** Cada búsqueda de la sincronización queda guardada por consulta: la generación de preguntas la reutiliza (D26). */
    val ofertasEnCache = OfertasEnCache(clienteMercadoLaboral, cache)
    val verificadorGoogle = ClienteGoogleIdentidad(configuracion.google.clientId)
    private val clienteGooglePlay = if (configuracion.googlePlay.esSimulado) null else ClienteGooglePlay(configuracion.googlePlay)
    val verificadorCompras: VerificadorCompraGoogle = clienteGooglePlay ?: VerificadorCompraSimulado()
    val documentosLegales = FuenteDocumentosLegalesArchivo()
    /** Motor de inferencia de visión en otro proceso (T3.11): el backend no carga el modelo, lo consulta por HTTP. */
    val clienteMotorVision = ClienteMotorVision(configuracion.motorVision)

    private val clienteHttpLlm = crearClienteHttpLlm()

    // Solo los que tienen API key: sin la clave del modelo pedido, generate-ai responde 503 provider_not_configured
    // antes del tope y antes de gastar cuota de las APIs de empleo.
    val proveedoresIa: Map<TipoProveedorIa, ProveedorPreguntasIa> = buildMap {
        val llm = configuracion.llm
        if (llm.openAiApiKey.isNotBlank()) {
            put(TipoProveedorIa.OPENAI, ProveedorConResiliencia(ProveedorOpenAi(llm.openAiApiKey, clienteHttpLlm), "openai"))
        }
        if (llm.anthropicApiKey.isNotBlank()) {
            put(TipoProveedorIa.ANTHROPIC, ProveedorConResiliencia(ProveedorAnthropic(llm.anthropicApiKey, clienteHttpLlm), "anthropic"))
        }
        if (llm.mimoApiKey.isNotBlank()) {
            put(TipoProveedorIa.MIMO, ProveedorConResiliencia(ProveedorMimo(llm.mimoApiKey, clienteHttpLlm, llm.mimoUrl), "mimo"))
        }
    }

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

    /** Lo que piden las ofertas de empleo de un cargo, para el prompt de la generación (D26). */
    val servicioContextoMercado = ServicioContextoMercado(ofertasEnCache, repositorioMercado)

    /** Credits del Token Plan de MiMo (D31): los mismos para el 429 de generar-ia y para Costos IA. */
    private val creditosTokenPlanMimo = ServicioCreditosTokenPlan(
        LectorCostosIaExposed(),
        PlanTokenMimo(configuracion.llm.mimoTokenPlanCreditos, configuracion.llm.mimoTokenPlanDiaRenovacion)
    )

    /** Tope de gasto de la generación con IA (D23): una sola instancia para el 429 y para Costos IA (D28). */
    private val topeGeneracionIa = ServicioTopeGeneracionIa(
        repositorioGeneracionPregunta, TopeGastoGeneracionIa(usdMensual = configuracion.llm.gastoGeneracionIaMensualUsd),
        creditosTokenPlanMimo
    )

    val servicioGeneracionPregunta = ServicioGeneracionPregunta(
        proveedoresIa, repositorioGeneracionPregunta, lectorCatalogo, topeGeneracionIa, mercado = servicioContextoMercado
    )

    /** Completa el feedback genérico (correcto, medio, malo) de las preguntas del banco (D36), con el mismo tope de gasto. */
    val servicioFeedbackGenerico = ServicioFeedbackGenerico(proveedoresIa, RepositorioFeedbackGenericoExposed(), topeGeneracionIa)

    /** Skills que pide el mercado por cargo frente a las preguntas del banco (D26). */
    val servicioCoberturaPreguntas = ServicioCoberturaPreguntas(repositorioMercado, LectorCoberturaPreguntasExposed())
    /** Gasto en IA para el admin (reportes con IA y generación de preguntas) y gasto del mes frente al tope (D28). */
    val servicioCostosIa = ServicioCostosIa(LectorCostosIaExposed(), topeGeneracionIa, creditosMimo = creditosTokenPlanMimo)

    val servicioRequisitosCargo = ServicioRequisitosCargo(repositorioMercado, repositorioMercado, ofertasEnCache)

    val servicioMercado = ServicioMercado(repositorioMercado, repositorioMercado, servicioRequisitosCargo, cache)

    val servicioTendencias = ServicioTendenciasSkill(
        lector = repositorioMercado,
        escritor = repositorioMercado,
        clienteMercado = ofertasEnCache,
        requisitos = servicioRequisitosCargo,
        alActualizar = { servicioMercado.invalidarCache() }
    )

    val servicioConsentimiento = ServicioConsentimiento(
        RepositorioTextoConsentimientoExposed(), RepositorioConsentimientoExposed(), documentosLegales, BorradorDatosCamaraExposed()
    )

    val servicioRecordatorio = ServicioRecordatorio(RepositorioRecordatorioExposed())

    // Se crea antes que la suscripción: el cupo de reportes con IA que ve la app (D29) lo lee del mismo repositorio.
    private val repositorioReporte = RepositorioReporteExposed()
    /** Reportes con IA que le quedan al premium en el mes, con el mismo tope y la misma condición que la reserva (D17). */
    private val servicioCupoIa = ServicioCupoIa(
        repositorioReporte, configuracion.llm.evaluacionesIaMensuales, iaDisponible = configuracion.llm.tieneProveedor
    )

    val servicioSuscripcion = ServicioSuscripcion(RepositorioSuscripcionExposed(), verificadorCompras, cupoIa = servicioCupoIa)

    private val selectorPreguntas = SelectorPreguntas(repositorioPregunta, repositorioMercado)
    private val repositorioSesionEntrevista = RepositorioSesionEntrevistaExposed()
    private val repositorioTestNivelacion = RepositorioTestNivelacionExposed()
    private val resolutorContexto = ResolutorContextoPrueba(repositorioMercado, repositorioPerfil, repositorioObjetivo)

    // Corrección de respuestas abiertas: motor freemium para todos; en la entrevista, IA para premium si hay LLM.
    val evaluadorRespuesta: EvaluadorRespuesta = EvaluadorRespuestaFreemium()
    private val evaluadorEntrevistaGratis = EvaluadorEntrevistaFreemium(evaluadorRespuesta)
    private val evaluadorEntrevistaIa: EvaluadorEntrevista? = when {
        configuracion.llm.openAiApiKey.isNotBlank() ->
            EvaluadorEntrevistaIa(proveedoresIa.getValue(TipoProveedorIa.OPENAI), MODELO_OPENAI_POR_DEFECTO, evaluadorEntrevistaGratis)
        configuracion.llm.anthropicApiKey.isNotBlank() ->
            EvaluadorEntrevistaIa(proveedoresIa.getValue(TipoProveedorIa.ANTHROPIC), MODELO_ANTHROPIC_POR_DEFECTO, evaluadorEntrevistaGratis)
        configuracion.llm.mimoApiKey.isNotBlank() ->
            EvaluadorEntrevistaIa(proveedoresIa.getValue(TipoProveedorIa.MIMO), MODELO_MIMO_POR_DEFECTO, evaluadorEntrevistaGratis)
        else -> null
    }
    private val repositorioMetricaVideo = RepositorioMetricaVideoExposed()
    private val repositorioPrediccionSimulacion = RepositorioPrediccionSimulacionExposed()
    private val repositorioNivelSkill = RepositorioNivelSkillExposed()

    val servicioReporte = ServicioReporteEntrevista(
        reportes = repositorioReporte,
        sesiones = repositorioSesionEntrevista,
        metricas = repositorioMetricaVideo,
        preguntas = repositorioPregunta,
        mercado = repositorioMercado,
        niveles = repositorioNivelSkill,
        evaluadorGratis = evaluadorEntrevistaGratis,
        evaluadorIa = evaluadorEntrevistaIa,
        premium = VerificadorPremium { servicioSuscripcion.estado(it).esPremium },
        tareasSegundoPlano = tareasSegundoPlano,
        evaluacionesIaMensuales = configuracion.llm.evaluacionesIaMensuales
    )

    val servicioEntrevista = ServicioEntrevista(
        sesiones = repositorioSesionEntrevista,
        metricas = repositorioMetricaVideo,
        selector = selectorPreguntas,
        contexto = resolutorContexto,
        procesador = servicioReporte,
        tareasSegundoPlano = tareasSegundoPlano,
        permisoCamara = servicioConsentimiento
    )
    val servicioSimulacion = ServicioSimulacion(
        repositorioPrediccionSimulacion, repositorioSesionEntrevista, permisoCamara = servicioConsentimiento
    )
    val servicioLlamada = ServicioLlamada(RepositorioLlamadaExposed(), servicioSimulacion, servicioConsentimiento)
    val servicioRevisionLocal = ServicioRevisionLocal(repositorioPregunta, RepositorioRevisionTextoExposed())
    private val corrector = CorrectorRespuestas(evaluadorRespuesta)
    private val repositorioPractica = RepositorioPracticaExposed()
    private val repositorioNivelacion = RepositorioNivelacionExposed()

    val servicioPractica = ServicioPractica(
        practicas = repositorioPractica,
        preguntas = repositorioPregunta,
        selector = selectorPreguntas,
        contexto = resolutorContexto,
        mercado = repositorioMercado,
        niveles = repositorioNivelSkill,
        corrector = corrector
    )

    val servicioNivelacion = ServicioNivelacion(
        nivelaciones = repositorioNivelacion,
        tests = repositorioTestNivelacion,
        preguntas = repositorioPregunta,
        selector = selectorPreguntas,
        contexto = resolutorContexto,
        mercado = repositorioMercado,
        niveles = repositorioNivelSkill,
        corrector = corrector
    )

    val servicioTestNivelacion = ServicioTestNivelacion(repositorioTestNivelacion, repositorioPregunta, repositorioMercado)

    val servicioPruebasApp = ServicioPruebasApp(
        entrevistas = servicioEntrevista,
        practicas = servicioPractica,
        nivelaciones = servicioNivelacion,
        sesionesEntrevista = repositorioSesionEntrevista,
        repositorioPracticas = repositorioPractica,
        repositorioNivelaciones = repositorioNivelacion
    )

    private val tareaMercado = TareaSincronizacionMercado(servicioTendencias)
    private val tareaRescate = TareaRescateReportes(servicioReporte)
    private val tareaPurgaConsentimientos = TareaPurgaConsentimientos(servicioConsentimiento)

    fun iniciarTareasSegundoPlano() {
        tareaMercado.iniciar()
        tareaRescate.iniciar()
        tareaPurgaConsentimientos.iniciar()
    }

    /**
     * Apagado ordenado (D20). Se llama en ApplicationStopping: Netty ya no atiende solicitudes, pero la BD y Redis siguen
     * abiertos. Para las tareas periódicas y espera hasta [espera] a los reportes y correos en curso; lo que no termine
     * queda reintentable.
     */
    suspend fun detenerTareas(espera: Duration) {
        tareaMercado.detener()
        tareaRescate.detener()
        tareaPurgaConsentimientos.detener()
        tareasSegundoPlano.esperarYCerrar(espera)
    }

    override fun close() {
        tareasSegundoPlano.cancelar()
        tareaMercado.close()
        tareaRescate.close()
        tareaPurgaConsentimientos.close()
        servicioContextoMercado.close()
        clienteMercadoLaboral.close()
        clienteGooglePlay?.close()
        clienteHttpLlm.close()
        clienteMotorVision.close()
        cache.close()
    }
}
