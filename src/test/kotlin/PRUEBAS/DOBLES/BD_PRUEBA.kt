package PRUEBAS.DOBLES

import CONFIGURACION.ConfiguracionLimites
import CONFIGURACION.configurarErrores
import CONFIGURACION.configurarLimiteSolicitudes
import CONFIGURACION.configurarSerializacion
import CONTROLADORES.controladorCostosIa
import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorConsentimiento
import CONTROLADORES.controladorMercado
import CONTROLADORES.controladorRecordatorio
import CONTROLADORES.controladorEntrevista
import CONTROLADORES.controladorFeedback
import CONTROLADORES.controladorNivelacion
import CONTROLADORES.controladorPractica
import CONTROLADORES.controladorPruebaPractica
import CONTROLADORES.controladorSalud
import CONTROLADORES.controladorSuscripcion
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorPregunta
import CONTROLADORES.controladorLlamada
import CONTROLADORES.controladorRevisionLocal
import CONTROLADORES.controladorUsuario
import CONTROLADORES.controladorVision
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
import MODELOS.RepositorioReporteExposed
import MODELOS.TablaIntentoTest
import MODELOS.TablaReporteEntrevista
import MODELOS.TablaReporteSkillDetalle
import SERVICIOS.EvaluadorEntrevistaFreemium
import SERVICIOS.EvaluadorEntrevistaIa
import SERVICIOS.ServicioReporteEntrevista
import MODELOS.TablaMetricaVideo
import MODELOS.TablaLlamada
import MODELOS.TablaPrediccionSimulacion
import MODELOS.TablaRevisionTexto
import MODELOS.RepositorioPrediccionSimulacionExposed
import SERVICIOS.ServicioSimulacion
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
                TablaSesionEntrevista, TablaSesionPreguntaRespuesta, TablaMetricaVideo, TablaPrediccionSimulacion, TablaLlamada, TablaRevisionTexto,
                TablaTestNivelacion, TablaIntentoTest, TablaResultadoNivelacion, TablaSesionPractica, TablaRespuestaPractica,
                TablaNivelSkillUsuario, TablaReporteEntrevista, TablaReporteSkillDetalle
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
    private val limites: ConfiguracionLimites = ConfiguracionLimites(registrosPorIp = 1_000, recuperacionesPorIp = 1_000, generacionesIaPorIp = 1_000),
    val clienteMercado: ClienteMercadoLaboralFalso = ClienteMercadoLaboralFalso(emptyMap()),
    val verificadorCompras: VerificadorCompraFalso = VerificadorCompraFalso(),
    val procesadorEntrevista: ProcesadorEntrevistaGrabador = ProcesadorEntrevistaGrabador(),
    val reloj: RelojAjustable = RelojAjustable(),
    /** Caché compartida (hace de Redis): los contadores del límite por IP usan el [reloj] de la prueba. */
    val cache: CacheEnMemoria = CacheEnMemoria { reloj.millis() },
    /**
     * Tope mensual de la generación con IA (D23). Usa el reloj del sistema, no [reloj]: la auditoría en H2 se fecha con
     * la hora real.
     */
    topeGastoGeneracionIaUsd: Double = CONFIGURACION.GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO,
    /**
     * Reloj del tope de generación (D23, D28). Solo para fijar el mes en pruebas que insertan filas a mano: no mezclarlo
     * con una generación real, que se audita con la hora real.
     */
    relojTopeGeneracionIa: java.time.Clock = java.time.Clock.systemUTC(),
    /** Estado del modelo de visión DivLara para `/health` (aislado del disco real por defecto). */
    val modeloDivlara: CONFIGURACION.ProveedorEstadoModeloDivlara =
        CONFIGURACION.ProveedorEstadoModeloDivlara { CONFIGURACION.EstadoModeloDivlara.noDisponible() },
    /** Motor de inferencia DivLara para `/api/v1/vision/predecir` (no disponible por defecto para aislar pruebas). */
    val motorVision: CONFIGURACION.MotorInferenciaDivlara = object : CONFIGURACION.MotorInferenciaDivlara {
        override fun cantidadFeaturesEsperadas(): Int? = null
        override fun predecir(features: List<Double>): CONFIGURACION.ResultadoPrediccionDivlara =
            throw ERRORES.ErrorServicioExterno(
                "modelo_no_disponible",
                "El modelo de visión (${CONFIGURACION.VERSION_MODELO_DIVLARA}) no está disponible en este momento"
            )
    },
    /**
     * Cómo llega la solicitud de visión al motor. null = el servicio de visión en proceso sobre [motorVision] (sin red);
     * una prueba de la cadena real pasa un `ClienteMotorVision` contra un motor levantado de verdad.
     */
    predictorVision: SERVICIOS.PredictorVision? = null,
    /** Token Plan de MiMo (D31); 0 = sin Token Plan, como por defecto. Usa [relojTopeGeneracionIa]. */
    creditosTokenPlanMimo: Long = 0,
    diaRenovacionTokenPlanMimo: Int = 1
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
    /** Como en producción, una sola instancia del tope para el 429 de generar-ia y para Costos IA (D28). */
    val creditosMimo = SERVICIOS.ServicioCreditosTokenPlan(
        MODELOS.LectorCostosIaExposed(),
        SERVICIOS.PlanTokenMimo(creditosTokenPlanMimo, diaRenovacionTokenPlanMimo, relojTopeGeneracionIa)
    )
    val topeGeneracionIa = SERVICIOS.ServicioTopeGeneracionIa(
        generaciones, SERVICIOS.TopeGastoGeneracionIa(topeGastoGeneracionIaUsd, relojTopeGeneracionIa), creditosMimo
    )
    val costosIa = SERVICIOS.ServicioCostosIa(MODELOS.LectorCostosIaExposed(), topeGeneracionIa, reloj, creditosMimo)
    val pregunta = ServicioPregunta(preguntas, catalogo)
    val mercadoRepo = RepositorioMercadoExposed()
    /** Como en producción: la sincronización deja las ofertas en la caché y la generación las reutiliza (D26). */
    val ofertas = INTEGRACIONES.OfertasEnCache(clienteMercado, cache)
    val contextoMercado = SERVICIOS.ServicioContextoMercado(ofertas, mercadoRepo)
    val generacion = ServicioGeneracionPregunta(
        mapOf(TipoProveedorIa.OPENAI to proveedorIa, TipoProveedorIa.ANTHROPIC to proveedorIa, TipoProveedorIa.MIMO to proveedorIa), generaciones, catalogo,
        topeGeneracionIa,
        mercado = contextoMercado
    )
    val cobertura = SERVICIOS.ServicioCoberturaPreguntas(mercadoRepo, MODELOS.LectorCoberturaPreguntasExposed())
    val feedbackGenerico = SERVICIOS.ServicioFeedbackGenerico(
        mapOf(TipoProveedorIa.OPENAI to proveedorIa, TipoProveedorIa.ANTHROPIC to proveedorIa, TipoProveedorIa.MIMO to proveedorIa),
        MODELOS.RepositorioFeedbackGenericoExposed(),
        topeGeneracionIa
    )

    val requisitos = ServicioRequisitosCargo(mercadoRepo, mercadoRepo, ofertas)
    val mercado = ServicioMercado(mercadoRepo, mercadoRepo, requisitos, cache)
    val tendencias = ServicioTendenciasSkill(mercadoRepo, mercadoRepo, ofertas, requisitos) { mercado.invalidarCache() }
    val documentos = FuenteDocumentosLegalesArchivo()
    /** Como en producción: el permiso de cámara decide si se guardan métricas y predicciones, y retirarlo las borra (D38). */
    val consentimiento = ServicioConsentimiento(
        RepositorioTextoConsentimientoExposed(), RepositorioConsentimientoExposed(), documentos, MODELOS.BorradorDatosCamaraExposed()
    )
    val recordatorio = ServicioRecordatorio(RepositorioRecordatorioExposed())
    val suscripciones = RepositorioSuscripcionExposed()
    // Antes que la suscripción, que lee de él el cupo de reportes con IA (D29): declarado después quedaría en null.
    val reportesRepo = RepositorioReporteExposed()
    /** Como en producción: el tope de IA del reporte y IA disponible (hay evaluador con IA), con el [reloj] de la prueba. */
    val cupoIa = SERVICIOS.ServicioCupoIa(reportesRepo, CONFIGURACION.EVALUACIONES_IA_MENSUALES_POR_DEFECTO, iaDisponible = true, reloj)
    val suscripcion = ServicioSuscripcion(suscripciones, verificadorCompras, cupoIa = cupoIa)

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
        reloj = reloj,
        permisoCamara = consentimiento
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
    val extractorFeaturesDivlara = CONFIGURACION.ExtractorFeaturesDivlara()
    private val servicioVisionLocal = SERVICIOS.ServicioVision(motorVision, extractorFeaturesDivlara)
    val vision: SERVICIOS.PredictorVision = predictorVision ?: SERVICIOS.PredictorVision { servicioVisionLocal.predecir(it) }
    val prediccionesSimulacion = RepositorioPrediccionSimulacionExposed()
    val revisionesTexto = MODELOS.RepositorioRevisionTextoExposed()
    val revisionLocal = SERVICIOS.ServicioRevisionLocal(preguntas, revisionesTexto)
    val simulacion = ServicioSimulacion(prediccionesSimulacion, sesionesEntrevista, reloj, consentimiento)
    val llamadasRepo = MODELOS.RepositorioLlamadaExposed()
    val llamadas = SERVICIOS.ServicioLlamada(llamadasRepo, simulacion, consentimiento)

    // Reporte de feedback (reportesRepo está más arriba). En las pruebas se llama a reporte.procesar(...) a mano para que
    // sea determinista.
    val premium = PremiumFalso()
    val evaluadorEntrevistaGratis = EvaluadorEntrevistaFreemium(evaluador)
    val evaluadorEntrevistaIa = EvaluadorEntrevistaIa(proveedorIa, "gpt-4o-mini", evaluadorEntrevistaGratis)
    val reporte = ServicioReporteEntrevista(
        reportesRepo, sesionesEntrevista, metricasVideo, preguntas, mercadoRepo, nivelesSkill,
        evaluadorEntrevistaGratis, evaluadorEntrevistaIa, premium, CoroutineScope(Dispatchers.Unconfined), reloj
    )

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
        configurarLimiteSolicitudes(limites, cache)
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
            controladorPregunta(pregunta, generacion, cobertura, feedbackGenerico)
            controladorCostosIa(costosIa)
            controladorMercado(mercado, tendencias)
            controladorConsentimiento(consentimiento, documentos)
            controladorRecordatorio(recordatorio)
            controladorSuscripcion(suscripcion)
            controladorSalud(CONFIGURACION.EstadoFreemiumReadiness(0), modeloDivlara)
            controladorEntrevista(entrevista)
            controladorVision(vision, simulacion)
            controladorLlamada(llamadas)
            controladorFeedback(reporte)
            controladorPractica(practica, pruebasApp, evaluador)
            controladorRevisionLocal(revisionLocal)
            controladorNivelacion(nivelacion, testsNivelacion)
            controladorPruebaPractica(pruebasApp)
        }
    }
}
