package SERVICIOS

import CONFIGURACION.EVALUACIONES_IA_MENSUALES_POR_DEFECTO
import CONFIGURACION.HISTORIAL_PRUEBAS_LIMITE
import CONFIGURACION.INTENTOS_MAXIMOS_REPORTE
import CONFIGURACION.LIMITE_RESCATE_SESIONES
import CONFIGURACION.PESO_PUNTAJE_BLANDO
import CONFIGURACION.PESO_PUNTAJE_CORPORAL
import CONFIGURACION.PESO_PUNTAJE_TECNICO
import CONFIGURACION.PUNTOS_PROGRESO_LIMITE
import CONFIGURACION.UMBRAL_FORTALEZA
import CONFIGURACION.UMBRAL_NIVEL_LOGRADO
import CONFIGURACION.VENTANA_RESCATE_SESIONES
import CONFIGURACION.VIGENCIA_RESERVA_IA
import CONFIGURACION.ZONA_CORTE_CUOTA_IA
import ERRORES.ErrorAplicacion
import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import MODELOS.CategoriaHabilidad
import MODELOS.DetalleSkillReporte
import MODELOS.EstadoReporte
import MODELOS.EstadoSesionEntrevista
import MODELOS.EvaluacionNivelSkill
import MODELOS.EvaluacionPreguntaSesion
import MODELOS.LectorMercado
import MODELOS.ModoEvaluacion
import MODELOS.NivelExperiencia
import MODELOS.NivelSkillUsuario
import MODELOS.PreguntaSesion
import MODELOS.PuntoProgresoSkill
import MODELOS.RecomendacionSkill
import MODELOS.ReporteEntrevista
import MODELOS.RepositorioMetricaVideo
import MODELOS.RepositorioNivelSkill
import MODELOS.RepositorioPregunta
import MODELOS.RepositorioReporte
import MODELOS.RepositorioSesionEntrevista
import MODELOS.ResultadoReporte
import MODELOS.ResumenMetricasVideo
import MODELOS.ResumenReporte
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** ¿El usuario tiene premium? (decide si sus respuestas se evalúan con IA). */
fun interface VerificadorPremium {
    suspend fun esPremium(usuarioId: UUID): Boolean
}

/** Progreso de una skill: nivel acumulado y su puntaje en cada entrevista. */
data class ProgresoSkill(
    val nivel: NivelSkillUsuario,
    val nombre: String,
    val historial: List<PuntoProgresoSkill>
)

private const val NOMBRE_TECNICAS_GENERALES = "Conocimientos técnicos generales"
private const val NOMBRE_BLANDAS_GENERALES = "Habilidades blandas"
private const val ITEMS_MAXIMOS_REPORTE = 6

/**
 * Reporte de feedback de la entrevista (Fase 7). Se genera en segundo plano al finalizar:
 * corrige las respuestas abiertas (motor freemium, o IA si el usuario es premium y le queda cupo en el mes, D17), calcula los puntajes
 * técnico, blando y de lenguaje corporal, arma el detalle por skill (radar), fortalezas, áreas de mejora
 * y recomendaciones, y acumula el puntaje de cada skill en nivel_skill_usuario.
 * Estado: generando → listo | error, con reintento manual (hasta [INTENTOS_MAXIMOS_REPORTE] intentos).
 */
class ServicioReporteEntrevista(
    private val reportes: RepositorioReporte,
    private val sesiones: RepositorioSesionEntrevista,
    private val metricas: RepositorioMetricaVideo,
    private val preguntas: RepositorioPregunta,
    private val mercado: LectorMercado,
    private val niveles: RepositorioNivelSkill,
    private val evaluadorGratis: EvaluadorEntrevista,
    /** null si no hay LLM configurado. */
    private val evaluadorIa: EvaluadorEntrevista?,
    private val premium: VerificadorPremium,
    private val tareasSegundoPlano: CoroutineScope,
    private val reloj: Clock = Clock.systemUTC(),
    /** Tope de evaluaciones con IA por usuario premium y mes calendario (D17); 0 = sin IA. */
    private val evaluacionesIaMensuales: Int = EVALUACIONES_IA_MENSUALES_POR_DEFECTO
) : ProcesadorEntrevistaFinalizada {
    private val log = LoggerFactory.getLogger(ServicioReporteEntrevista::class.java)

    init {
        log.info(
            "Reporte de entrevista: IA {} · tope mensual por usuario premium: {}",
            if (evaluadorIa == null) "desactivada (sin API key)" else "activa",
            evaluacionesIaMensuales
        )
    }

    /** Se llama al finalizar la entrevista (ya en segundo plano). */
    override suspend fun procesar(sesion: SesionEntrevista) {
        reportes.crearPendiente(sesion.id, reloj.instant())
        val intento = UUID.randomUUID()
        if (reportes.tomarParaGenerar(sesion.id, INTENTOS_MAXIMOS_REPORTE, reloj.instant(), intento)) generar(sesion.id, intento)
    }

    /** El reporte de una entrevista propia y finalizada. Si todavía no existe, se informa como "generando". */
    suspend fun obtener(usuarioId: UUID, sesionId: UUID): Pair<SesionEntrevista, ReporteEntrevista?> {
        val sesion = sesionFinalizada(usuarioId, sesionId)
        return sesion to reportes.buscarPorSesion(sesionId)
    }

    /** Vuelve a generar un reporte que quedó en error. La generación sigue en segundo plano. */
    suspend fun reintentar(usuarioId: UUID, sesionId: UUID): ReporteEntrevista {
        sesionFinalizada(usuarioId, sesionId)
        val actual = reportes.buscarPorSesion(sesionId)
        if (actual == null || actual.estado != EstadoReporte.ERROR) {
            throw ErrorConflicto("reporte_no_reintentable", "Solo se puede reintentar un reporte que terminó con error")
        }
        if (actual.intentos >= INTENTOS_MAXIMOS_REPORTE) {
            throw ErrorConflicto("reintentos_agotados", "Se alcanzó el máximo de $INTENTOS_MAXIMOS_REPORTE intentos para este reporte")
        }
        val intento = UUID.randomUUID()
        if (!reportes.tomarParaGenerar(sesionId, INTENTOS_MAXIMOS_REPORTE, reloj.instant(), intento)) {
            throw ErrorConflicto("reporte_no_reintentable", "El reporte ya se está generando")
        }
        tareasSegundoPlano.launch { generar(sesionId, intento) }
        return reportes.buscarPorSesion(sesionId)!!
    }

    suspend fun historial(usuarioId: UUID): List<ResumenReporte> = reportes.listarDeUsuario(usuarioId, HISTORIAL_PRUEBAS_LIMITE)

    /** Nivel acumulado de cada skill y su evolución en las entrevistas. */
    suspend fun progreso(usuarioId: UUID): List<ProgresoSkill> {
        val puntos = reportes.progresoPorSkill(usuarioId, PUNTOS_PROGRESO_LIMITE).groupBy { it.skillId }
        return niveles.listar(usuarioId)
            .map { nivel -> ProgresoSkill(nivel, mercado.buscarSkill(nivel.skillId)?.nombre ?: "Skill", puntos[nivel.skillId].orEmpty()) }
            .sortedByDescending { it.nivel.puntaje }
    }

    // ---------- Generación ----------

    /**
     * Si algo falla, el reporte queda en error con un código (sin detalles internos). Solo lanza la cancelación por
     * apagado, y antes deja el reporte reintentable (D20). Todas sus escrituras van ligadas a [intento].
     */
    private suspend fun generar(sesionId: UUID, intento: UUID) {
        try {
            val sesion = sesiones.buscar(sesionId) ?: return
            val resultado = calcular(sesion)
            if (!reportes.guardarResultado(sesionId, resultado, intento)) {
                log.warn("El reporte de la entrevista {} ya no estaba en generación (se dio por interrumpido): no se guarda", sesionId)
                return
            }
            acumularNiveles(sesion, resultado.detalles)
        } catch (e: Exception) {
            // Apagado del servidor (no un timeout interno, que llega con la corrutina todavía activa): queda reintentable.
            if (e is CancellationException && !currentCoroutineContext().isActive) {
                log.warn("Reporte de la entrevista {} interrumpido por el apagado: queda reintentable", sesionId)
                // NonCancellable para poder escribir aunque el trabajo esté cancelado; la BD sigue abierta porque esto
                // corre durante ApplicationStopping. Si no se puede, lo deja reintentable el rescate.
                withContext(NonCancellable) {
                    runCatching { reportes.marcarInterrumpido(sesionId, reloj.instant(), intento) }
                        .onFailure { log.warn("No se pudo marcar el reporte {} como interrumpido; lo hará el rescate", sesionId, it) }
                }
                throw e
            }
            log.error("No se pudo generar el reporte de la entrevista {}", sesionId, e)
            val codigo = (e as? ErrorAplicacion)?.codigo ?: "error_interno"
            runCatching { reportes.marcarError(sesionId, codigo, reloj.instant(), intento) }
                .onFailure { log.error("Tampoco se pudo marcar el reporte {} con error", sesionId, it) }
        }
    }

    /** Si falla, el reporte igual queda listo: el puntaje por skill se puede volver a acumular en la próxima entrevista. */
    private suspend fun acumularNiveles(sesion: SesionEntrevista, detalles: List<DetalleSkillReporte>) {
        val evaluaciones = detalles.mapNotNull { d -> d.skillId?.let { EvaluacionNivelSkill(it, d.puntaje, nivel = null) } }
        try {
            niveles.acumular(sesion.usuarioId, evaluaciones, reloj.instant())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("No se pudo acumular el nivel por skill de la entrevista {}", sesion.id, e)
        }
    }

    /**
     * Pasa a 'error' reintentable lo que un apagado sin aviso (SIGKILL, caída) dejó a medias (D20): reportes en
     * 'generando' tomados hace más de [VIGENCIA_RESERVA_IA] y entrevistas finalizadas sin reporte. Lo llama
     * TareaRescateReportes al arrancar y cada pocos minutos. No regenera nada (no gasta IA sin que el usuario lo pida).
     */
    suspend fun rescatarInterrumpidos(): Int {
        val ahora = reloj.instant()
        return reportes.rescatarInterrumpidos(
            limite = ahora.minus(VIGENCIA_RESERVA_IA),
            desdeSesiones = ahora.minus(VENTANA_RESCATE_SESIONES),
            fecha = ahora,
            maximoSesiones = LIMITE_RESCATE_SESIONES
        ).also { if (it > 0) log.warn("Se rescataron {} reportes interrumpidos: quedan en error, reintentables", it) }
    }

    private suspend fun calcular(sesion: SesionEntrevista): ResultadoReporte {
        val abiertas = sesion.preguntas.filter { it.tipo != TipoPregunta.OPCION_MULTIPLE && !it.transcripcion.isNullOrBlank() }
        // Primero lo barato; la reserva del cupo (que bloquea al usuario en la BD) va al final.
        val reservaIa = if (evaluadorIa != null && abiertas.isNotEmpty() && premium.esPremium(sesion.usuarioId)) reservarCupoIa(sesion) else null
        val evaluador = if (reservaIa != null) evaluadorIa!! else evaluadorGratis
        val evaluacion = evaluador.evaluar(ContextoEvaluacionEntrevista(sesion.cargoObjetivo, sesion.nivel, abiertas.map { aEvaluar(it) }))
        val porPregunta = evaluacion.evaluaciones.associateBy { it.preguntaSesionId }

        // Puntaje de cada pregunta. Las no respondidas cuentan 0; un clip sin transcripción no se puede evaluar y no cuenta.
        val sinTranscripcion = sesion.preguntas.filter { it.estaRespondida && it.tipo != TipoPregunta.OPCION_MULTIPLE && it.transcripcion.isNullOrBlank() }
        val puntajes = sesion.preguntas.filter { it !in sinTranscripcion }.associateWith { pregunta ->
            when {
                !pregunta.estaRespondida -> 0.0
                pregunta.tipo == TipoPregunta.OPCION_MULTIPLE -> pregunta.puntaje?.toDouble() ?: 0.0
                else -> porPregunta[pregunta.id]?.puntaje ?: 0.0
            }
        }
        val tecnico = promedio(puntajes.filterKeys { it.categoria == CategoriaHabilidad.TECNICA }.values)
        val blando = promedio(puntajes.filterKeys { it.categoria == CategoriaHabilidad.BLANDA }.values)
        val video = metricas.resumir(sesion.id)
        val corporal = video?.let { puntajeCorporal(it) }
        val detalles = detallesPorSkill(sesion, puntajes)
        val recomendaciones = recomendaciones(sesion, detalles)

        return ResultadoReporte(
            puntajeGlobal = decimal(global(tecnico, blando, corporal)),
            puntajeTecnico = decimal(tecnico ?: 0.0),
            puntajeBlando = decimal(blando ?: 0.0),
            // null si no hubo video o ninguna ventana tuvo evidencia: "sin medir", no un 0 de la persona (D22).
            puntajeLenguajeCorporal = corporal?.let(::decimal),
            fortalezas = fortalezas(evaluacion, detalles, blando, corporal),
            areasMejora = areasMejora(evaluacion, sesion, detalles, corporal, sinTranscripcion.size),
            recomendaciones = recomendaciones,
            resumen = evaluacion.resumen ?: resumen(sesion, global(tecnico, blando, corporal), detalles),
            modo = evaluacion.modo,
            uso = evaluacion.uso,
            detalles = detalles,
            evaluaciones = abiertas.mapNotNull { pregunta ->
                porPregunta[pregunta.id]?.let {
                    EvaluacionPreguntaSesion(pregunta.id, pregunta.categoria, decimal(it.puntaje), it.observacion, it.mejoras, evaluacion.modo)
                }
            },
            // Una evaluación con IA queda con la fecha de su reserva: así cuenta en el mes en que tomó el cupo aunque la
            // llamada al LLM termine pasada la medianoche del último día (si no, se cobraría en el mes siguiente).
            fecha = reservaIa?.takeIf { evaluacion.modo == ModoEvaluacion.IA } ?: reloj.instant()
        )
    }

    /**
     * Toma un cupo del tope mensual de IA (D17) y devuelve el instante de la reserva, que fija el mes al que se cobra.
     * null si el usuario ya lo agotó, si el tope es 0 o si la reserva falla: en todos esos casos el reporte sale igual,
     * con el motor freemium (D09).
     */
    private suspend fun reservarCupoIa(sesion: SesionEntrevista): Instant? {
        if (evaluacionesIaMensuales <= 0) return null
        val ahora = reloj.instant()
        val mes = YearMonth.from(ahora.atZone(ZONA_CORTE_CUOTA_IA))
        val desde = mes.atDay(1).atStartOfDay(ZONA_CORTE_CUOTA_IA).toInstant()
        val hasta = mes.plusMonths(1).atDay(1).atStartOfDay(ZONA_CORTE_CUOTA_IA).toInstant()
        val reservada = try {
            reportes.reservarEvaluacionIa(
                sesion.id, sesion.usuarioId, evaluacionesIaMensuales, desde, hasta, ahora.minus(VIGENCIA_RESERVA_IA), ahora
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("No se pudo reservar la evaluación con IA de la entrevista {}; se usa el motor freemium", sesion.id, e)
            return null
        }
        if (!reservada) {
            log.info(
                "Tope mensual de evaluaciones con IA alcanzado: usuario {} ({} por mes, {}); la entrevista {} se evalúa con el motor freemium",
                sesion.usuarioId, evaluacionesIaMensuales, mes, sesion.id
            )
            return null
        }
        return ahora
    }

    private suspend fun aEvaluar(pregunta: PreguntaSesion): RespuestaAbiertaAEvaluar {
        // La rúbrica es la copia tomada al servir la pregunta: editar el banco no cambia la evaluación (D24). Solo las
        // filas anteriores a la migración 022 no la tienen: para ellas se usa la del banco y, si la pregunta se borró,
        // se evalúa sin palabras clave.
        val rubrica = pregunta.rubrica ?: pregunta.preguntaId?.let { preguntas.buscarPorId(it)?.rubrica }
        return RespuestaAbiertaAEvaluar(
            pregunta.id, pregunta.categoria, pregunta.enunciado, pregunta.respuestaIdeal, palabrasClaveDe(rubrica), pregunta.transcripcion!!
        )
    }

    private suspend fun detallesPorSkill(sesion: SesionEntrevista, puntajes: Map<PreguntaSesion, Double>): List<DetalleSkillReporte> =
        puntajes.entries
            .groupBy { (pregunta, _) -> pregunta.skillId to (if (pregunta.skillId == null) pregunta.categoria else null) }
            .map { (_, filas) ->
                val skillId = filas.first().key.skillId
                val categoria = filas.first().key.categoria
                val puntaje = promedio(filas.map { it.value }) ?: 0.0
                DetalleSkillReporte(
                    skillId = skillId,
                    nombre = skillId?.let { mercado.buscarSkill(it)?.nombre }
                        ?: if (categoria == CategoriaHabilidad.BLANDA) NOMBRE_BLANDAS_GENERALES else NOMBRE_TECNICAS_GENERALES,
                    categoria = categoria,
                    puntaje = decimal(puntaje),
                    nivelEvaluado = if (puntaje >= UMBRAL_NIVEL_LOGRADO) sesion.nivel else null,
                    observacion = when {
                        puntaje >= UMBRAL_FORTALEZA -> "Buen dominio para el nivel ${nombreNivel(sesion.nivel)}."
                        puntaje >= UMBRAL_NIVEL_LOGRADO -> "Cumple lo esperado para el nivel ${nombreNivel(sesion.nivel)}."
                        else -> "Necesita refuerzo para el nivel ${nombreNivel(sesion.nivel)}."
                    },
                    preguntasRespondidas = filas.count { it.key.estaRespondida }
                )
            }
            .sortedByDescending { it.puntaje }

    private suspend fun recomendaciones(sesion: SesionEntrevista, detalles: List<DetalleSkillReporte>): List<RecomendacionSkill> {
        val requisitos = sesion.cargoId?.let { mercado.listarRequisitos(it) }.orEmpty().associateBy { it.skill.id }
        return detalles
            .filter { it.skillId != null && it.puntaje.toDouble() < UMBRAL_NIVEL_LOGRADO }
            .map { detalle ->
                val requisito = requisitos[detalle.skillId]
                RecomendacionSkill(
                    skillId = detalle.skillId.toString(),
                    nombre = detalle.nombre,
                    puntaje = detalle.puntaje.toDouble(),
                    nivelRequerido = requisito?.nivelRequerido?.valorBd,
                    prioridad = if (requisito?.esObligatoria == true) "alta" else "media"
                )
            }
            .sortedWith(compareBy({ it.prioridad != "alta" }, { it.puntaje }))
    }

    // ---------- Textos ----------

    private fun fortalezas(evaluacion: EvaluacionEntrevista, detalles: List<DetalleSkillReporte>, blando: Double?, corporal: Double?): List<String> {
        val porReglas = detalles.filter { it.skillId != null && it.puntaje.toDouble() >= UMBRAL_FORTALEZA }.map { "Dominio de ${it.nombre}" } +
            listOfNotNull("Respuestas de comportamiento bien estructuradas".takeIf { (blando ?: 0.0) >= UMBRAL_FORTALEZA })
        // La IA no ve las métricas de video: lo corporal siempre sale de las reglas.
        val corporales = listOfNotNull("Buen lenguaje corporal y contacto visual".takeIf { (corporal ?: 0.0) >= UMBRAL_FORTALEZA })
        return (evaluacion.fortalezas.ifEmpty { porReglas } + corporales).distinct().take(ITEMS_MAXIMOS_REPORTE)
    }

    private fun areasMejora(
        evaluacion: EvaluacionEntrevista,
        sesion: SesionEntrevista,
        detalles: List<DetalleSkillReporte>,
        corporal: Double?,
        sinTranscripcion: Int
    ): List<String> {
        val porReglas = detalles.filter { it.puntaje.toDouble() < UMBRAL_NIVEL_LOGRADO }.map { "Reforzar ${it.nombre}" }
        val sinResponder = sesion.preguntas.count { !it.estaRespondida }
        val siempre = listOfNotNull(
            "Respondiste ${sesion.respondidas} de ${sesion.preguntas.size} preguntas: intenta responder todas".takeIf { sinResponder > 0 },
            "Trabaja el contacto visual y la postura frente a la cámara".takeIf { corporal != null && corporal < UMBRAL_NIVEL_LOGRADO },
            "$sinTranscripcion respuesta(s) en video no tenían transcripción y no se pudieron evaluar".takeIf { sinTranscripcion > 0 }
        )
        return (evaluacion.areasMejora.ifEmpty { porReglas } + siempre).distinct().take(ITEMS_MAXIMOS_REPORTE)
    }

    private fun resumen(sesion: SesionEntrevista, global: Double, detalles: List<DetalleSkillReporte>): String {
        val base = "Obtuviste ${global.toInt()}/100 en tu entrevista para ${sesion.cargoObjetivo} (nivel ${nombreNivel(sesion.nivel)})."
        val mejor = detalles.firstOrNull()?.takeIf { it.puntaje.toDouble() >= UMBRAL_NIVEL_LOGRADO }?.let { " Tu mejor resultado fue en ${it.nombre}." }.orEmpty()
        val reforzar = detalles.filter { it.puntaje.toDouble() < UMBRAL_NIVEL_LOGRADO }.takeIf { it.isNotEmpty() }
            ?.let { lista -> " Conviene reforzar ${lista.take(3).joinToString(", ") { it.nombre }}." }.orEmpty()
        return base + mejor + reforzar
    }

    // ---------- Cálculos ----------

    /** Promedio de los indicadores de video presentes (0-100). */
    private fun puntajeCorporal(video: ResumenMetricasVideo): Double? =
        promedio(listOfNotNull(video.contactoVisual, video.postura, video.confianza).map { it.toDouble() })

    /** Técnico 50 %, blando 30 % y corporal 20 %, repartiendo el peso de lo que no se midió. */
    private fun global(tecnico: Double?, blando: Double?, corporal: Double?): Double {
        val partes = listOfNotNull(tecnico?.let { it to PESO_PUNTAJE_TECNICO }, blando?.let { it to PESO_PUNTAJE_BLANDO }, corporal?.let { it to PESO_PUNTAJE_CORPORAL })
        val pesos = partes.sumOf { it.second }
        return if (pesos == 0.0) 0.0 else partes.sumOf { (puntaje, peso) -> puntaje * peso } / pesos
    }

    private fun promedio(valores: Collection<Double>): Double? = valores.takeIf { it.isNotEmpty() }?.average()

    private fun decimal(valor: Double): BigDecimal = BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP)

    private suspend fun sesionFinalizada(usuarioId: UUID, sesionId: UUID): SesionEntrevista {
        val sesion = sesiones.buscar(sesionId)?.takeIf { it.usuarioId == usuarioId }
            ?: throw ErrorNoEncontrado("entrevista_no_encontrada", "La entrevista no existe")
        return when (sesion.estado) {
            EstadoSesionEntrevista.FINALIZADA -> sesion
            EstadoSesionEntrevista.EN_PROGRESO -> throw ErrorConflicto("entrevista_no_finalizada", "El reporte estará disponible cuando finalices la entrevista")
            EstadoSesionEntrevista.CANCELADA -> throw ErrorNoEncontrado("reporte_no_disponible", "La entrevista fue cancelada y no tiene reporte")
        }
    }
}
