package SERVICIOS

import CONFIGURACION.HORAS_MAXIMAS_SESION_ENTREVISTA
import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import CONFIGURACION.LARGO_MAXIMO_URL_VIDEO
import CONFIGURACION.METRICAS_VIDEO_MAXIMAS_POR_LOTE
import CONFIGURACION.PREGUNTAS_ENTREVISTA_MAXIMO
import CONFIGURACION.PREGUNTAS_ENTREVISTA_MINIMO
import CONFIGURACION.PREGUNTAS_ENTREVISTA_POR_DEFECTO
import CONFIGURACION.PUNTAJE_MAXIMO_RESPUESTA
import CONFIGURACION.SESIONES_SIN_REPETIR_PREGUNTAS
import CONFIGURACION.TAMANO_PAGINA_ENTREVISTAS_MAXIMO
import CONFIGURACION.TAMANO_PAGINA_ENTREVISTAS_POR_DEFECTO
import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.Cargo
import MODELOS.EXPRESIONES_VALIDAS
import MODELOS.EstadoSesionEntrevista
import MODELOS.LectorMercado
import MODELOS.MetricaVideo
import MODELOS.NivelExperiencia
import MODELOS.NuevaSesionEntrevista
import MODELOS.PaginaSesionesEntrevista
import MODELOS.PreguntaSesion
import MODELOS.RepositorioMetricaVideo
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioPerfil
import MODELOS.RepositorioSesionEntrevista
import MODELOS.RespuestaRegistrada
import MODELOS.ResultadoRegistroRespuestas
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.UUID

private const val LARGO_MAXIMO_NOMBRE_CARGO = 120

/** Qué entrevista quiere el usuario. Lo que venga en null se completa con su perfil y objetivo. */
data class PedidoEntrevista(
    val cargoId: String? = null,
    val nombreCargo: String? = null,
    val nivel: String? = null,
    val cantidadPreguntas: Int? = null
)

/** Respuesta del usuario a una pregunta de la sesión, sin validar. */
data class RespuestaUsuario(
    val preguntaSesionId: UUID,
    val texto: String? = null,
    val opcionId: String? = null,
    val videoClipUrl: String? = null
)

/**
 * Simulación de entrevista: arma la sesión con preguntas del banco, recibe respuestas y métricas
 * de video, y controla los estados en_progreso → finalizada | cancelada.
 * Un usuario solo ve sus propias sesiones (las ajenas responden 404).
 */
class ServicioEntrevista(
    private val sesiones: RepositorioSesionEntrevista,
    private val metricas: RepositorioMetricaVideo,
    private val selector: SelectorPreguntasEntrevista,
    private val mercado: LectorMercado,
    private val perfiles: RepositorioPerfil,
    private val objetivos: RepositorioObjetivoCarrera,
    private val procesador: ProcesadorEntrevistaFinalizada,
    private val tareasSegundoPlano: CoroutineScope,
    private val reloj: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(ServicioEntrevista::class.java)

    /**
     * @param reemplazarEnProgreso true cancela la sesión en curso en vez de responder 409
     *   (la app Android rinde la entrevista de una vez y no puede retomarla).
     */
    suspend fun iniciar(usuarioId: UUID, pedido: PedidoEntrevista, reemplazarEnProgreso: Boolean = false): SesionEntrevista {
        val cantidad = pedido.cantidadPreguntas ?: PREGUNTAS_ENTREVISTA_POR_DEFECTO
        if (cantidad !in PREGUNTAS_ENTREVISTA_MINIMO..PREGUNTAS_ENTREVISTA_MAXIMO) {
            throw ErrorValidacion(
                "cantidad_invalida",
                "La entrevista debe tener entre $PREGUNTAS_ENTREVISTA_MINIMO y $PREGUNTAS_ENTREVISTA_MAXIMO preguntas"
            )
        }
        val (cargo, nombreCargo) = resolverCargo(usuarioId, pedido)
        val nivel = resolverNivel(usuarioId, pedido.nivel, cargo)

        liberarSesionAnterior(usuarioId, reemplazarEnProgreso)

        val recientes = sesiones.preguntasRecientes(usuarioId, SESIONES_SIN_REPETIR_PREGUNTAS)
        val preguntas = selector.seleccionar(cargo, nivel, cantidad, recientes)
        if (preguntas.size < PREGUNTAS_ENTREVISTA_MINIMO) {
            throw ErrorConflicto(
                "preguntas_insuficientes",
                "Aún no hay suficientes preguntas aprobadas para $nombreCargo (${nivel.valorBd}); prueba con otro nivel o cargo"
            )
        }
        return sesiones.crear(NuevaSesionEntrevista(usuarioId, cargo?.id, nombreCargo, nivel, reloj.instant()), preguntas)
    }

    /** La sesión en curso, o null. Una sesión abandonada hace más de 2 h se cancela y no cuenta. */
    suspend fun enProgreso(usuarioId: UUID): SesionEntrevista? {
        val sesion = sesiones.buscarEnProgreso(usuarioId) ?: return null
        if (!estaVencida(sesion)) return sesion
        sesiones.cerrar(sesion.id, EstadoSesionEntrevista.CANCELADA, reloj.instant())
        return null
    }

    suspend fun obtener(usuarioId: UUID, sesionId: UUID): SesionEntrevista {
        val sesion = sesiones.buscar(sesionId)
        if (sesion == null || sesion.usuarioId != usuarioId) {
            throw ErrorNoEncontrado("entrevista_no_encontrada", "La entrevista no existe")
        }
        return sesion
    }

    /** La primera pregunta sin responder, o null si ya respondió todas. */
    suspend fun siguientePregunta(usuarioId: UUID, sesionId: UUID): PreguntaSesion? =
        activa(usuarioId, sesionId).preguntas.firstOrNull { !it.estaRespondida }

    suspend fun responder(usuarioId: UUID, sesionId: UUID, respuesta: RespuestaUsuario): PreguntaSesion =
        responderVarias(usuarioId, sesionId, listOf(respuesta)).preguntas.first { it.id == respuesta.preguntaSesionId }

    /** Guarda varias respuestas a la vez: todas o ninguna. */
    suspend fun responderVarias(usuarioId: UUID, sesionId: UUID, respuestas: List<RespuestaUsuario>): SesionEntrevista {
        if (respuestas.isEmpty()) throw sinRespuestas()
        if (respuestas.map { it.preguntaSesionId }.toSet().size != respuestas.size) {
            throw ErrorValidacion("pregunta_repetida", "Cada pregunta se responde una sola vez")
        }
        val sesion = activa(usuarioId, sesionId)
        val validadas = respuestas.map { respuesta ->
            val pregunta = sesion.preguntas.firstOrNull { it.id == respuesta.preguntaSesionId }
                ?: throw ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta no pertenece a esta entrevista")
            validarRespuesta(pregunta, respuesta)
        }
        when (sesiones.registrarRespuestas(sesionId, validadas, reloj.instant())) {
            ResultadoRegistroRespuestas.REGISTRADAS -> Unit
            ResultadoRegistroRespuestas.SESION_NO_ACTIVA -> throw entrevistaNoActiva()
            ResultadoRegistroRespuestas.YA_RESPONDIDA ->
                throw ErrorConflicto("pregunta_ya_respondida", "Esa pregunta ya fue respondida")
        }
        return obtener(usuarioId, sesionId)
    }

    suspend fun registrarMetricas(usuarioId: UUID, sesionId: UUID, lote: List<MetricaVideo>): Int {
        if (lote.isEmpty() || lote.size > METRICAS_VIDEO_MAXIMAS_POR_LOTE) {
            throw ErrorValidacion("lote_invalido", "Envía entre 1 y $METRICAS_VIDEO_MAXIMAS_POR_LOTE métricas por solicitud")
        }
        lote.forEach(::validarMetrica)
        activa(usuarioId, sesionId)
        return metricas.insertarLote(sesionId, lote)
    }

    /** Cierra la sesión y lanza en segundo plano lo que sigue (reporte de feedback, Fase 7). */
    suspend fun finalizar(usuarioId: UUID, sesionId: UUID): SesionEntrevista {
        val sesion = activa(usuarioId, sesionId)
        if (sesion.respondidas == 0) throw sinRespuestas()
        if (!sesiones.cerrar(sesionId, EstadoSesionEntrevista.FINALIZADA, reloj.instant())) throw entrevistaNoActiva()
        val finalizada = obtener(usuarioId, sesionId)
        tareasSegundoPlano.launch {
            try {
                procesador.procesar(finalizada)
            } catch (e: Exception) {
                log.error("No se pudo procesar la entrevista finalizada {}", sesionId, e)
            }
        }
        return finalizada
    }

    /** Rendición de una sola vez (app Android): guarda las respuestas y finaliza. */
    suspend fun responderYFinalizar(usuarioId: UUID, sesionId: UUID, respuestas: List<RespuestaUsuario>): SesionEntrevista {
        responderVarias(usuarioId, sesionId, respuestas)
        return finalizar(usuarioId, sesionId)
    }

    suspend fun cancelar(usuarioId: UUID, sesionId: UUID): SesionEntrevista {
        activa(usuarioId, sesionId)
        if (!sesiones.cerrar(sesionId, EstadoSesionEntrevista.CANCELADA, reloj.instant())) throw entrevistaNoActiva()
        return obtener(usuarioId, sesionId)
    }

    suspend fun historial(usuarioId: UUID, pagina: Int?, tamano: Int?): PaginaSesionesEntrevista {
        val numeroPagina = pagina ?: 1
        val tamanoPagina = tamano ?: TAMANO_PAGINA_ENTREVISTAS_POR_DEFECTO
        if (numeroPagina < 1) throw ErrorValidacion("pagina_invalida", "La página empieza en 1")
        if (tamanoPagina !in 1..TAMANO_PAGINA_ENTREVISTAS_MAXIMO) {
            throw ErrorValidacion("tamano_invalido", "El tamaño de página debe estar entre 1 y $TAMANO_PAGINA_ENTREVISTAS_MAXIMO")
        }
        return sesiones.listarDeUsuario(usuarioId, numeroPagina, tamanoPagina)
    }

    // ---------- Reglas ----------

    /** Cargo pedido por id, por nombre o, si no viene ninguno, el objetivo activo del usuario. */
    private suspend fun resolverCargo(usuarioId: UUID, pedido: PedidoEntrevista): Pair<Cargo?, String> {
        pedido.cargoId?.let { texto ->
            val id = runCatching { UUID.fromString(texto) }.getOrNull()
                ?: throw ErrorValidacion("cargo_id_invalido", "El id del cargo no es válido")
            val cargo = mercado.buscarCargo(id)?.takeIf { it.estaActivo }
                ?: throw ErrorNoEncontrado("cargo_no_encontrado", "El cargo no existe")
            return cargo to cargo.nombre
        }
        val nombre = pedido.nombreCargo?.trim()?.takeIf { it.isNotEmpty() }
            ?: objetivos.buscarActivo(usuarioId)?.nombreCargo
            ?: throw ErrorValidacion("cargo_requerido", "Indica el cargo o define tu objetivo en el onboarding")
        if (nombre.length > LARGO_MAXIMO_NOMBRE_CARGO) {
            throw ErrorValidacion("cargo_invalido", "El cargo admite hasta $LARGO_MAXIMO_NOMBRE_CARGO caracteres")
        }
        // Un cargo que no está en el catálogo igual se puede practicar, con preguntas generales.
        val cargo = mercado.buscarCargoPorNombre(nombre)?.takeIf { it.estaActivo }
        return cargo to (cargo?.nombre ?: nombre)
    }

    private suspend fun resolverNivel(usuarioId: UUID, nivel: String?, cargo: Cargo?): NivelExperiencia {
        if (!nivel.isNullOrBlank()) {
            return NivelExperiencia.desdeTexto(nivel)
                ?: throw ErrorValidacion("nivel_invalido", "El nivel debe ser junior, semisenior o senior")
        }
        return perfiles.buscarPorUsuario(usuarioId)?.nivelExperiencia ?: cargo?.nivelBase ?: NivelExperiencia.JUNIOR
    }

    private suspend fun liberarSesionAnterior(usuarioId: UUID, reemplazar: Boolean) {
        val anterior = sesiones.buscarEnProgreso(usuarioId) ?: return
        if (!reemplazar && !estaVencida(anterior)) {
            throw ErrorConflicto(
                "entrevista_en_progreso",
                "Ya tienes una entrevista en curso: termínala o cancélala antes de iniciar otra"
            )
        }
        sesiones.cerrar(anterior.id, EstadoSesionEntrevista.CANCELADA, reloj.instant())
        log.info("Sesión de entrevista {} cancelada al iniciar una nueva", anterior.id)
    }

    private fun estaVencida(sesion: SesionEntrevista): Boolean =
        Duration.between(sesion.fechaInicio, reloj.instant()) > Duration.ofHours(HORAS_MAXIMAS_SESION_ENTREVISTA)

    /** La sesión propia y todavía en progreso. */
    private suspend fun activa(usuarioId: UUID, sesionId: UUID): SesionEntrevista {
        val sesion = obtener(usuarioId, sesionId)
        if (sesion.estado != EstadoSesionEntrevista.EN_PROGRESO) throw entrevistaNoActiva()
        return sesion
    }

    private fun validarRespuesta(pregunta: PreguntaSesion, respuesta: RespuestaUsuario): RespuestaRegistrada {
        if (pregunta.tipo == TipoPregunta.OPCION_MULTIPLE) {
            val opcion = pregunta.opciones.firstOrNull { it.id == respuesta.opcionId }
                ?: throw ErrorValidacion("opcion_invalida", "Elige una de las opciones de la pregunta")
            val puntaje = if (opcion.esCorrecta) PUNTAJE_MAXIMO_RESPUESTA else 0
            return RespuestaRegistrada(pregunta.id, null, null, opcion.id, BigDecimal(puntaje))
        }
        val texto = respuesta.texto?.trim()?.takeIf { it.isNotEmpty() }
        val video = respuesta.videoClipUrl?.trim()?.takeIf { it.isNotEmpty() }?.also(::validarUrlVideo)
        // Una pregunta de video puede responderse solo con el clip; una abierta necesita el texto.
        val tieneRespuesta = texto != null || (video != null && pregunta.tipo == TipoPregunta.SIMULACION_VIDEO)
        if (!tieneRespuesta) throw ErrorValidacion("respuesta_requerida", "Escribe tu respuesta")
        if (texto != null && texto.length > LARGO_MAXIMO_RESPUESTA_ENTREVISTA) {
            throw ErrorValidacion("respuesta_muy_larga", "La respuesta admite hasta $LARGO_MAXIMO_RESPUESTA_ENTREVISTA caracteres")
        }
        // Las respuestas abiertas las evalúa el reporte de feedback (Fase 7).
        return RespuestaRegistrada(pregunta.id, texto, video, null, null)
    }

    private fun validarUrlVideo(url: String) {
        val esValida = url.length <= LARGO_MAXIMO_URL_VIDEO &&
            runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() } == true
        if (!esValida) throw ErrorValidacion("video_url_invalida", "El clip debe ser una URL https de hasta $LARGO_MAXIMO_URL_VIDEO caracteres")
    }

    private fun validarMetrica(metrica: MetricaVideo) {
        if (metrica.timestampMs < 0) throw ErrorValidacion("metrica_invalida", "timestampMs no puede ser negativo")
        val fueraDeRango = listOfNotNull(metrica.contactoVisual, metrica.postura, metrica.confianza)
            .any { it < BigDecimal.ZERO || it > BigDecimal(PUNTAJE_MAXIMO_RESPUESTA) }
        if (fueraDeRango) throw ErrorValidacion("metrica_fuera_de_rango", "Los puntajes de video van de 0 a 100")
        if (metrica.expresionDominante != null && metrica.expresionDominante !in EXPRESIONES_VALIDAS) {
            throw ErrorValidacion("expresion_invalida", "La expresión debe ser una de: ${EXPRESIONES_VALIDAS.joinToString()}")
        }
    }

    private fun entrevistaNoActiva() = ErrorConflicto("entrevista_no_activa", "La entrevista ya fue finalizada o cancelada")
    private fun sinRespuestas() = ErrorValidacion("sin_respuestas", "Responde al menos una pregunta")
}
