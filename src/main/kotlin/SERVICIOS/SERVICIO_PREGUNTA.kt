package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.ConsultaPreguntas
import ESQUEMAS.SolicitudOpcion
import ESQUEMAS.SolicitudPregunta
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.FiltroPreguntas
import MODELOS.LectorCatalogo
import MODELOS.NivelExperiencia
import MODELOS.NuevaOpcion
import MODELOS.PaginaPreguntas
import MODELOS.Pregunta
import MODELOS.RepositorioPregunta
import MODELOS.TipoPregunta
import UTILIDADES.sinVacios
import java.util.UUID

private const val LARGO_MAXIMO_ENUNCIADO = 2000
private const val LARGO_MAXIMO_MOTIVO_RECHAZO = 500
private const val OPCIONES_MINIMAS = 2
private const val OPCIONES_MAXIMAS = 6
private const val TAMANO_PAGINA_POR_DEFECTO = 20
private const val TAMANO_PAGINA_MAXIMO = 100
private const val PREGUNTAS_USUARIO_POR_DEFECTO = 10
private const val PREGUNTAS_USUARIO_MAXIMO = 20

/**
 * Banco de preguntas: el admin crea, edita y revisa; los usuarios solo reciben preguntas aprobadas.
 * Las preguntas que escribe un admin nacen aprobadas; las de IA nacen pendientes (ver ServicioGeneracionPregunta).
 */
class ServicioPregunta(
    private val preguntas: RepositorioPregunta,
    private val catalogo: LectorCatalogo
) {

    suspend fun crear(solicitud: SolicitudPregunta): Pregunta =
        preguntas.crear(validarContenido(solicitud), EstadoPregunta.APROBADA)

    suspend fun obtener(id: UUID): Pregunta = preguntas.buscarPorId(id) ?: throw preguntaNoEncontrada()

    suspend fun listar(consulta: ConsultaPreguntas): PaginaPreguntas {
        val pagina = consulta.pagina ?: 1
        val tamano = consulta.tamano ?: TAMANO_PAGINA_POR_DEFECTO
        if (pagina < 1) throw ErrorValidacion("pagina_invalida", "La página empieza en 1")
        if (tamano !in 1..TAMANO_PAGINA_MAXIMO) {
            throw ErrorValidacion("tamano_invalido", "El tamaño de página debe estar entre 1 y $TAMANO_PAGINA_MAXIMO")
        }
        return preguntas.listar(filtroDesde(consulta), pagina, tamano)
    }

    /** Lo que ve un usuario: siempre aprobadas, en orden aleatorio. */
    suspend fun listarParaUsuario(consulta: ConsultaPreguntas, cantidad: Int?): List<Pregunta> {
        val total = cantidad ?: PREGUNTAS_USUARIO_POR_DEFECTO
        if (total !in 1..PREGUNTAS_USUARIO_MAXIMO) {
            throw ErrorValidacion("cantidad_invalida", "La cantidad debe estar entre 1 y $PREGUNTAS_USUARIO_MAXIMO")
        }
        return preguntas.listarAlAzar(filtroDesde(consulta).copy(estado = EstadoPregunta.APROBADA, generadaPorIa = null), total)
    }

    /** Editar devuelve la pregunta a revisión: el contenido nuevo no está aprobado. */
    suspend fun editar(id: UUID, solicitud: SolicitudPregunta): Pregunta {
        if (!preguntas.reemplazarContenido(id, validarContenido(solicitud))) throw preguntaNoEncontrada()
        return obtener(id)
    }

    suspend fun aprobar(id: UUID, adminId: UUID): Pregunta {
        if (!preguntas.cambiarEstado(id, EstadoPregunta.APROBADA, motivoRechazo = null, revisadoPor = adminId)) {
            throw preguntaNoEncontrada()
        }
        return obtener(id)
    }

    suspend fun rechazar(id: UUID, motivo: String, adminId: UUID): Pregunta {
        val motivoLimpio = motivo.trim()
        if (motivoLimpio.isEmpty()) throw ErrorValidacion("motivo_requerido", "Indica por qué se rechaza la pregunta")
        if (motivoLimpio.length > LARGO_MAXIMO_MOTIVO_RECHAZO) {
            throw ErrorValidacion("motivo_invalido", "El motivo no puede superar $LARGO_MAXIMO_MOTIVO_RECHAZO caracteres")
        }
        if (!preguntas.cambiarEstado(id, EstadoPregunta.RECHAZADA, motivoLimpio, adminId)) throw preguntaNoEncontrada()
        return obtener(id)
    }

    /** Una pregunta ya usada en prácticas o entrevistas no se borra: se rechaza para conservar el historial. */
    suspend fun eliminar(id: UUID) {
        val pregunta = obtener(id)
        if (pregunta.vecesUsada > 0) {
            throw ErrorConflicto("pregunta_en_uso", "La pregunta ya se usó; recházala en vez de eliminarla")
        }
        preguntas.eliminar(id)
    }

    // ---------- Validación ----------

    private suspend fun validarContenido(solicitud: SolicitudPregunta): ContenidoPregunta {
        val tipo = leerTipo(solicitud.tipo)
        val (cargoId, skillId) = validarContexto(solicitud.cargoId, solicitud.skillId, catalogo)
        val enunciado = solicitud.enunciado.trim()
        if (enunciado.isEmpty()) throw ErrorValidacion("enunciado_requerido", "El enunciado es obligatorio")
        if (enunciado.length > LARGO_MAXIMO_ENUNCIADO) {
            throw ErrorValidacion("enunciado_invalido", "El enunciado no puede superar $LARGO_MAXIMO_ENUNCIADO caracteres")
        }
        val respuestaIdeal = solicitud.respuestaIdeal.sinVacios()?.trim()
        val opciones = if (tipo == TipoPregunta.OPCION_MULTIPLE) {
            validarOpciones(solicitud.opciones)
        } else {
            validarPreguntaAbierta(solicitud, respuestaIdeal)
        }
        return ContenidoPregunta(
            skillId = skillId,
            cargoId = cargoId,
            tipo = tipo,
            categoria = leerCategoria(solicitud.categoria),
            nivel = leerNivel(solicitud.nivel),
            enunciado = enunciado,
            respuestaIdeal = respuestaIdeal,
            rubrica = solicitud.rubrica,
            opciones = opciones
        )
    }

    private fun validarOpciones(opciones: List<SolicitudOpcion>): List<NuevaOpcion> {
        if (opciones.size !in OPCIONES_MINIMAS..OPCIONES_MAXIMAS) {
            throw ErrorValidacion("opciones_invalidas", "Una pregunta de opción múltiple lleva entre $OPCIONES_MINIMAS y $OPCIONES_MAXIMAS opciones")
        }
        if (opciones.any { it.texto.isBlank() }) throw ErrorValidacion("opcion_vacia", "Todas las opciones necesitan texto")
        if (opciones.count { it.esCorrecta } != 1) {
            throw ErrorValidacion("debe_haber_una_correcta", "Debe haber exactamente una opción correcta")
        }
        if (opciones.map { it.texto.trim().lowercase() }.toSet().size != opciones.size) {
            throw ErrorValidacion("opcion_duplicada", "Hay opciones repetidas")
        }
        return opciones.map { NuevaOpcion(it.texto.trim(), it.esCorrecta, it.explicacion.sinVacios()?.trim()) }
    }

    /** Sin respuesta ideal ni rúbrica no hay cómo evaluar una respuesta abierta. */
    private fun validarPreguntaAbierta(solicitud: SolicitudPregunta, respuestaIdeal: String?): List<NuevaOpcion> {
        if (solicitud.opciones.isNotEmpty()) {
            throw ErrorValidacion("opciones_no_permitidas", "Solo las preguntas de opción múltiple llevan opciones")
        }
        if (respuestaIdeal == null && solicitud.rubrica == null) {
            throw ErrorValidacion("respuesta_ideal_o_rubrica_requerida", "Indica una respuesta ideal o una rúbrica para evaluarla")
        }
        return emptyList()
    }

    private fun filtroDesde(consulta: ConsultaPreguntas) = FiltroPreguntas(
        estado = consulta.estado.sinVacios()?.let { leerEstado(it) },
        tipo = consulta.tipo.sinVacios()?.let { leerTipo(it) },
        categoria = consulta.categoria.sinVacios()?.let { leerCategoria(it) },
        nivel = consulta.nivel.sinVacios()?.let { leerNivel(it) },
        skillId = consulta.skillId.sinVacios()?.let(::leerUuid),
        cargoId = consulta.cargoId.sinVacios()?.let(::leerUuid),
        generadaPorIa = consulta.generadaPorIa
    )

    private fun leerEstado(texto: String) = EstadoPregunta.desdeBd(texto.trim().lowercase())
        ?: throw ErrorValidacion("estado_invalido", "Estado debe ser pendiente, aprobada o rechazada")

    private fun preguntaNoEncontrada() = ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta no existe")
}

// ---------- Lectura de valores compartida con la generación por IA ----------

internal fun leerTipo(texto: String) = TipoPregunta.desdeBd(texto.trim().lowercase())
    ?: throw ErrorValidacion("tipo_invalido", "Tipo debe ser opcion_multiple, abierta_texto o simulacion_video")

internal fun leerCategoria(texto: String) = CategoriaHabilidad.desdeBd(texto.trim().lowercase())
    ?: throw ErrorValidacion("categoria_invalida", "Categoría debe ser tecnica o blanda")

/** Dificultad: junior, semisenior o senior (también acepta jr, mid, sr). */
internal fun leerNivel(texto: String) = NivelExperiencia.desdeTexto(texto)
    ?: throw ErrorValidacion("nivel_invalido", "Nivel debe ser junior, semisenior o senior")

internal fun leerUuid(texto: String): UUID = runCatching { UUID.fromString(texto.trim()) }
    .getOrElse { throw ErrorValidacion("invalid_uuid", "Identificador con formato inválido") }

/** Una pregunta siempre se refiere a un cargo o a una skill, y deben existir en el catálogo. */
internal suspend fun validarContexto(cargoTexto: String?, skillTexto: String?, catalogo: LectorCatalogo): Pair<UUID?, UUID?> {
    val cargoId = cargoTexto.sinVacios()?.let(::leerUuid)
    val skillId = skillTexto.sinVacios()?.let(::leerUuid)
    if (cargoId == null && skillId == null) {
        throw ErrorValidacion("contexto_requerido", "Indica el cargo o la skill que evalúa la pregunta")
    }
    if (cargoId != null && catalogo.nombreCargo(cargoId) == null) {
        throw ErrorNoEncontrado("cargo_no_encontrado", "El cargo no existe")
    }
    if (skillId != null && catalogo.nombreSkill(skillId) == null) {
        throw ErrorNoEncontrado("skill_no_encontrada", "La skill no existe")
    }
    return cargoId to skillId
}
