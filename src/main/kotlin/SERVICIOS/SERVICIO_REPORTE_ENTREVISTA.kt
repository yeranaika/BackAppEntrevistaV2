package SERVICIOS

import CONFIGURACION.HISTORIAL_PRUEBAS_LIMITE
import CONFIGURACION.INTENTOS_MAXIMOS_REPORTE
import CONFIGURACION.PESO_PUNTAJE_BLANDO
import CONFIGURACION.PESO_PUNTAJE_CORPORAL
import CONFIGURACION.PESO_PUNTAJE_TECNICO
import CONFIGURACION.PUNTOS_PROGRESO_LIMITE
import CONFIGURACION.UMBRAL_FORTALEZA
import CONFIGURACION.UMBRAL_NIVEL_LOGRADO
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
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
private val EXPRESIONES_A_TRABAJAR = setOf("nervioso", "distraido", "confuso")

/**
 * Reporte de feedback de la entrevista (Fase 7). Se genera en segundo plano al finalizar:
 * corrige las respuestas abiertas (motor freemium, o IA si el usuario es premium), calcula los puntajes
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
    private val reloj: Clock = Clock.systemUTC()
) : ProcesadorEntrevistaFinalizada {
    private val log = LoggerFactory.getLogger(ServicioReporteEntrevista::class.java)

    /** Se llama al finalizar la entrevista (ya en segundo plano). */
    override suspend fun procesar(sesion: SesionEntrevista) {
        reportes.crearPendiente(sesion.id, reloj.instant())
        if (reportes.tomarParaGenerar(sesion.id, INTENTOS_MAXIMOS_REPORTE)) generar(sesion.id)
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
        if (!reportes.tomarParaGenerar(sesionId, INTENTOS_MAXIMOS_REPORTE)) {
            throw ErrorConflicto("reporte_no_reintentable", "El reporte ya se está generando")
        }
        tareasSegundoPlano.launch { generar(sesionId) }
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

    /** Nunca lanza: si algo falla, el reporte queda en error con un código (sin detalles internos). */
    private suspend fun generar(sesionId: UUID) {
        try {
            val sesion = sesiones.buscar(sesionId) ?: return
            val resultado = calcular(sesion)
            reportes.guardarResultado(sesionId, resultado)
            acumularNiveles(sesion, resultado.detalles)
        } catch (e: Exception) {
            log.error("No se pudo generar el reporte de la entrevista {}", sesionId, e)
            val codigo = (e as? ErrorAplicacion)?.codigo ?: "error_interno"
            runCatching { reportes.marcarError(sesionId, codigo, reloj.instant()) }
                .onFailure { log.error("Tampoco se pudo marcar el reporte {} con error", sesionId, it) }
        }
    }

    /** Si falla, el reporte igual queda listo: el puntaje por skill se puede volver a acumular en la próxima entrevista. */
    private suspend fun acumularNiveles(sesion: SesionEntrevista, detalles: List<DetalleSkillReporte>) {
        val evaluaciones = detalles.mapNotNull { d -> d.skillId?.let { EvaluacionNivelSkill(it, d.puntaje, nivel = null) } }
        runCatching { niveles.acumular(sesion.usuarioId, evaluaciones, reloj.instant()) }
            .onFailure { log.error("No se pudo acumular el nivel por skill de la entrevista {}", sesion.id, it) }
    }

    private suspend fun calcular(sesion: SesionEntrevista): ResultadoReporte {
        val abiertas = sesion.preguntas.filter { it.tipo != TipoPregunta.OPCION_MULTIPLE && !it.transcripcion.isNullOrBlank() }
        val evaluador = if (evaluadorIa != null && premium.esPremium(sesion.usuarioId)) evaluadorIa else evaluadorGratis
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
            puntajeLenguajeCorporal = decimal(corporal ?: 0.0),
            fortalezas = fortalezas(evaluacion, detalles, blando, corporal),
            areasMejora = areasMejora(evaluacion, sesion, detalles, corporal, video, sinTranscripcion.size),
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
            fecha = reloj.instant()
        )
    }

    private suspend fun aEvaluar(pregunta: PreguntaSesion): RespuestaAbiertaAEvaluar {
        // Las palabras clave están en la rúbrica del banco; si la pregunta se borró, se evalúa sin ellas.
        val rubrica = pregunta.preguntaId?.let { preguntas.buscarPorId(it)?.rubrica }
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
        video: ResumenMetricasVideo?,
        sinTranscripcion: Int
    ): List<String> {
        val porReglas = detalles.filter { it.puntaje.toDouble() < UMBRAL_NIVEL_LOGRADO }.map { "Reforzar ${it.nombre}" }
        val sinResponder = sesion.preguntas.count { !it.estaRespondida }
        val siempre = listOfNotNull(
            "Respondiste ${sesion.respondidas} de ${sesion.preguntas.size} preguntas: intenta responder todas".takeIf { sinResponder > 0 },
            "Trabaja el contacto visual y la postura frente a la cámara".takeIf { corporal != null && corporal < UMBRAL_NIVEL_LOGRADO },
            "Se te notó ${video?.expresionMasFrecuente} durante buena parte de la entrevista: practica para responder con más calma"
                .takeIf { video?.expresionMasFrecuente in EXPRESIONES_A_TRABAJAR },
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
