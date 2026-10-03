package MODELOS

import ERRORES.ErrorConflicto
import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ResultadoRegistroRespuestas { REGISTRADAS, SESION_NO_ACTIVA, YA_RESPONDIDA }

interface RepositorioSesionEntrevista {
    /**
     * Crea la sesión con un slot por pregunta (en ese orden) y suma un uso a cada pregunta.
     * ErrorConflicto("entrevista_en_progreso") si el usuario ya tiene una sesión en progreso.
     */
    suspend fun crear(nueva: NuevaSesionEntrevista, preguntas: List<Pregunta>): SesionEntrevista

    suspend fun buscar(id: UUID): SesionEntrevista?
    suspend fun buscarEnProgreso(usuarioId: UUID): SesionEntrevista?
    suspend fun listarDeUsuario(usuarioId: UUID, pagina: Int, tamano: Int): PaginaSesionesEntrevista

    /** Guarda todas o ninguna: la sesión debe seguir en progreso y ningún slot puede estar respondido. */
    suspend fun registrarRespuestas(sesionId: UUID, respuestas: List<RespuestaRegistrada>, fecha: Instant): ResultadoRegistroRespuestas

    /** Pasa la sesión de en_progreso a [estado]. false si ya no estaba en progreso. */
    suspend fun cerrar(sesionId: UUID, estado: EstadoSesionEntrevista, fecha: Instant): Boolean

    /** Preguntas del banco que el usuario vio en sus últimas [sesiones] sesiones. */
    suspend fun preguntasRecientes(usuarioId: UUID, sesiones: Int): Set<UUID>

    /** Para el historial de la app: puntaje = alternativas correctas sobre alternativas servidas. */
    suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba>
}

class RepositorioSesionEntrevistaExposed : RepositorioSesionEntrevista {

    override suspend fun crear(nueva: NuevaSesionEntrevista, preguntas: List<Pregunta>): SesionEntrevista {
        val id = UUID.randomUUID()
        try {
            transaccion {
                // Bloquear al usuario serializa dos "iniciar" simultáneos (en Postgres además lo impide el índice único).
                TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq nueva.usuarioId }.forUpdate().toList()
                if (existeEnProgreso(nueva.usuarioId)) throw entrevistaEnProgreso()
                insertarSesion(id, nueva)
                insertarPreguntas(id, preguntas)
                TablaPregunta.update({ TablaPregunta.preguntaId inList preguntas.map { it.id } }) {
                    it.update(vecesUsada, vecesUsada + 1)
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == SQLSTATE_VALOR_DUPLICADO) throw entrevistaEnProgreso()
            throw e
        }
        return buscar(id)!!
    }

    override suspend fun buscar(id: UUID): SesionEntrevista? = transaccion {
        TablaSesionEntrevista.selectAll().where { TablaSesionEntrevista.sesionId eq id }.firstOrNull()?.let(::conPreguntas)
    }

    override suspend fun buscarEnProgreso(usuarioId: UUID): SesionEntrevista? = transaccion {
        TablaSesionEntrevista.selectAll()
            .where { (TablaSesionEntrevista.usuarioId eq usuarioId) and (TablaSesionEntrevista.estado eq EstadoSesionEntrevista.EN_PROGRESO.valorBd) }
            .firstOrNull()?.let(::conPreguntas)
    }

    override suspend fun listarDeUsuario(usuarioId: UUID, pagina: Int, tamano: Int): PaginaSesionesEntrevista = transaccion {
        val consulta = TablaSesionEntrevista.selectAll().where { TablaSesionEntrevista.usuarioId eq usuarioId }
        val total = consulta.count()
        val filas = consulta
            .orderBy(TablaSesionEntrevista.fechaInicio to SortOrder.DESC)
            .limit(tamano, offset = (pagina - 1).toLong() * tamano)
            .toList()
        val conteos = contarPreguntas(filas.map { it[TablaSesionEntrevista.sesionId] })
        val elementos = filas.map { fila ->
            val (total, respondidas) = conteos[fila[TablaSesionEntrevista.sesionId]] ?: (0 to 0)
            ResumenSesionEntrevista(
                id = fila[TablaSesionEntrevista.sesionId],
                cargoId = fila[TablaSesionEntrevista.cargoId],
                cargoObjetivo = fila[TablaSesionEntrevista.cargoObjetivo],
                nivel = nivelDe(fila),
                estado = EstadoSesionEntrevista.desdeBd(fila[TablaSesionEntrevista.estado]),
                fechaInicio = fila[TablaSesionEntrevista.fechaInicio],
                fechaFin = fila[TablaSesionEntrevista.fechaFin],
                totalPreguntas = total,
                respondidas = respondidas
            )
        }
        PaginaSesionesEntrevista(elementos, total, pagina, tamano)
    }

    override suspend fun registrarRespuestas(
        sesionId: UUID,
        respuestas: List<RespuestaRegistrada>,
        fecha: Instant
    ): ResultadoRegistroRespuestas = transaccion {
        // El bloqueo evita que la sesión se finalice o cancele mientras se guardan las respuestas.
        val sesion = TablaSesionEntrevista.selectAll().where { TablaSesionEntrevista.sesionId eq sesionId }.forUpdate().firstOrNull()
        if (sesion == null || sesion[TablaSesionEntrevista.estado] != EstadoSesionEntrevista.EN_PROGRESO.valorBd) {
            return@transaccion ResultadoRegistroRespuestas.SESION_NO_ACTIVA
        }
        val ids = respuestas.map { it.preguntaSesionId }
        val yaRespondidas = TablaSesionPreguntaRespuesta.selectAll()
            .where { (TablaSesionPreguntaRespuesta.respuestaId inList ids) and TablaSesionPreguntaRespuesta.fechaRespuesta.isNotNull() }
            .count()
        if (yaRespondidas > 0) return@transaccion ResultadoRegistroRespuestas.YA_RESPONDIDA

        respuestas.forEach { respuesta ->
            TablaSesionPreguntaRespuesta.update({
                (TablaSesionPreguntaRespuesta.respuestaId eq respuesta.preguntaSesionId) and (TablaSesionPreguntaRespuesta.sesionId eq sesionId)
            }) {
                it[transcripcion] = respuesta.transcripcion
                it[videoClipUrl] = respuesta.videoClipUrl
                it[opcionElegidaId] = respuesta.opcionElegidaId?.let(UUID::fromString)
                it[puntaje] = respuesta.puntaje
                it[fechaRespuesta] = fecha
            }
        }
        ResultadoRegistroRespuestas.REGISTRADAS
    }

    override suspend fun cerrar(sesionId: UUID, estado: EstadoSesionEntrevista, fecha: Instant): Boolean = transaccion {
        TablaSesionEntrevista.update({
            (TablaSesionEntrevista.sesionId eq sesionId) and (TablaSesionEntrevista.estado eq EstadoSesionEntrevista.EN_PROGRESO.valorBd)
        }) {
            it[TablaSesionEntrevista.estado] = estado.valorBd
            it[fechaFin] = fecha
        } > 0
    }

    override suspend fun preguntasRecientes(usuarioId: UUID, sesiones: Int): Set<UUID> = transaccion {
        val recientes = TablaSesionEntrevista.select(TablaSesionEntrevista.sesionId)
            .where { TablaSesionEntrevista.usuarioId eq usuarioId }
            .orderBy(TablaSesionEntrevista.fechaInicio to SortOrder.DESC)
            .limit(sesiones)
            .map { it[TablaSesionEntrevista.sesionId] }
        if (recientes.isEmpty()) return@transaccion emptySet()
        TablaSesionPreguntaRespuesta.select(TablaSesionPreguntaRespuesta.preguntaId)
            .where { TablaSesionPreguntaRespuesta.sesionId inList recientes }
            .mapNotNull { it[TablaSesionPreguntaRespuesta.preguntaId] }
            .toSet()
    }

    override suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba> = transaccion {
        val filas = TablaSesionEntrevista.selectAll()
            .where { TablaSesionEntrevista.usuarioId eq usuarioId }
            .orderBy(TablaSesionEntrevista.fechaInicio to SortOrder.DESC)
            .limit(limite)
            .toList()
        val alternativas = TablaSesionPreguntaRespuesta
            .select(TablaSesionPreguntaRespuesta.sesionId, TablaSesionPreguntaRespuesta.puntaje)
            .where {
                (TablaSesionPreguntaRespuesta.sesionId inList filas.map { it[TablaSesionEntrevista.sesionId] }) and
                    (TablaSesionPreguntaRespuesta.tipoPregunta eq TipoPregunta.OPCION_MULTIPLE.valorBd)
            }
            .groupBy { it[TablaSesionPreguntaRespuesta.sesionId] }
        filas.map { fila ->
            val id = fila[TablaSesionEntrevista.sesionId]
            val estado = EstadoSesionEntrevista.desdeBd(fila[TablaSesionEntrevista.estado])
            val delaSesion = alternativas[id].orEmpty()
            val correctas = delaSesion.count { (it[TablaSesionPreguntaRespuesta.puntaje] ?: BigDecimal.ZERO) >= BigDecimal(100) }
            ResumenPrueba(
                id = id,
                tipo = "entrevista",
                cargoObjetivo = fila[TablaSesionEntrevista.cargoObjetivo],
                nivel = nivelDe(fila),
                estado = estado.valorBd,
                puntaje = if (estado == EstadoSesionEntrevista.FINALIZADA) correctas else null,
                puntajeTotal = delaSesion.size,
                fechaInicio = fila[TablaSesionEntrevista.fechaInicio],
                fechaFin = fila[TablaSesionEntrevista.fechaFin]
            )
        }
    }

    // ---------- Apoyo (dentro de una transacción) ----------

    private fun existeEnProgreso(usuarioId: UUID): Boolean =
        TablaSesionEntrevista.selectAll()
            .where { (TablaSesionEntrevista.usuarioId eq usuarioId) and (TablaSesionEntrevista.estado eq EstadoSesionEntrevista.EN_PROGRESO.valorBd) }
            .empty().not()

    private fun insertarSesion(id: UUID, nueva: NuevaSesionEntrevista) {
        TablaSesionEntrevista.insert {
            it[sesionId] = id
            it[usuarioId] = nueva.usuarioId
            it[cargoId] = nueva.cargoId
            it[cargoObjetivo] = nueva.cargoObjetivo
            it[nivelDificultad] = nueva.nivel.valorBd
            it[estado] = EstadoSesionEntrevista.EN_PROGRESO.valorBd
            it[fechaInicio] = nueva.inicio
        }
    }

    private fun insertarPreguntas(sesionId: UUID, preguntas: List<Pregunta>) {
        TablaSesionPreguntaRespuesta.batchInsert(preguntas.withIndex()) { (indice, pregunta) ->
            this[TablaSesionPreguntaRespuesta.respuestaId] = UUID.randomUUID()
            this[TablaSesionPreguntaRespuesta.sesionId] = sesionId
            this[TablaSesionPreguntaRespuesta.preguntaId] = pregunta.id
            this[TablaSesionPreguntaRespuesta.enunciado] = pregunta.enunciado
            this[TablaSesionPreguntaRespuesta.respuestaIdeal] = pregunta.respuestaIdeal
            this[TablaSesionPreguntaRespuesta.tipoPregunta] = pregunta.tipo.valorBd
            this[TablaSesionPreguntaRespuesta.categoriaHabilidad] = pregunta.categoria.valorBd
            this[TablaSesionPreguntaRespuesta.skillId] = pregunta.skillId
            this[TablaSesionPreguntaRespuesta.opciones] = pregunta.opciones.map { OpcionSnapshot(it.id.toString(), it.texto, it.esCorrecta, it.explicacion) }
            this[TablaSesionPreguntaRespuesta.orden] = (indice + 1).toShort()
        }
    }

    private fun contarPreguntas(sesiones: List<UUID>): Map<UUID, Pair<Int, Int>> {
        if (sesiones.isEmpty()) return emptyMap()
        return TablaSesionPreguntaRespuesta
            .select(TablaSesionPreguntaRespuesta.sesionId, TablaSesionPreguntaRespuesta.fechaRespuesta)
            .where { TablaSesionPreguntaRespuesta.sesionId inList sesiones }
            .groupBy { it[TablaSesionPreguntaRespuesta.sesionId] }
            .mapValues { (_, filas) -> filas.size to filas.count { it[TablaSesionPreguntaRespuesta.fechaRespuesta] != null } }
    }

    private fun conPreguntas(fila: ResultRow): SesionEntrevista {
        val id = fila[TablaSesionEntrevista.sesionId]
        val preguntas = TablaSesionPreguntaRespuesta.selectAll()
            .where { TablaSesionPreguntaRespuesta.sesionId eq id }
            .orderBy(TablaSesionPreguntaRespuesta.orden to SortOrder.ASC)
            .map { it.aPreguntaSesion() }
        return SesionEntrevista(
            id = id,
            usuarioId = fila[TablaSesionEntrevista.usuarioId],
            cargoId = fila[TablaSesionEntrevista.cargoId],
            cargoObjetivo = fila[TablaSesionEntrevista.cargoObjetivo],
            nivel = nivelDe(fila),
            estado = EstadoSesionEntrevista.desdeBd(fila[TablaSesionEntrevista.estado]),
            fechaInicio = fila[TablaSesionEntrevista.fechaInicio],
            fechaFin = fila[TablaSesionEntrevista.fechaFin],
            preguntas = preguntas
        )
    }

    private fun nivelDe(fila: ResultRow) =
        NivelExperiencia.desdeBd(fila[TablaSesionEntrevista.nivelDificultad]) ?: error("nivel_dificultad desconocido")

    private fun ResultRow.aPreguntaSesion() = PreguntaSesion(
        id = this[TablaSesionPreguntaRespuesta.respuestaId],
        preguntaId = this[TablaSesionPreguntaRespuesta.preguntaId],
        orden = this[TablaSesionPreguntaRespuesta.orden].toInt(),
        enunciado = this[TablaSesionPreguntaRespuesta.enunciado],
        respuestaIdeal = this[TablaSesionPreguntaRespuesta.respuestaIdeal],
        // Filas anteriores a la migración 016 no tienen snapshot de tipo: eran preguntas abiertas.
        tipo = this[TablaSesionPreguntaRespuesta.tipoPregunta]?.let(TipoPregunta::desdeBd) ?: TipoPregunta.ABIERTA_TEXTO,
        categoria = this[TablaSesionPreguntaRespuesta.categoriaHabilidad]?.let(CategoriaHabilidad::desdeBd) ?: CategoriaHabilidad.TECNICA,
        skillId = this[TablaSesionPreguntaRespuesta.skillId],
        opciones = this[TablaSesionPreguntaRespuesta.opciones].orEmpty(),
        transcripcion = this[TablaSesionPreguntaRespuesta.transcripcion],
        videoClipUrl = this[TablaSesionPreguntaRespuesta.videoClipUrl],
        opcionElegidaId = this[TablaSesionPreguntaRespuesta.opcionElegidaId]?.toString(),
        puntaje = this[TablaSesionPreguntaRespuesta.puntaje],
        fechaRespuesta = this[TablaSesionPreguntaRespuesta.fechaRespuesta]
    )

    private fun entrevistaEnProgreso() =
        ErrorConflicto("entrevista_en_progreso", "Ya tienes una entrevista en curso: termínala o cancélala antes de iniciar otra")
}

interface RepositorioMetricaVideo {
    /** Inserta el lote completo en una sola operación. Devuelve cuántas filas guardó. */
    suspend fun insertarLote(sesionId: UUID, metricas: List<MetricaVideo>): Int
}

class RepositorioMetricaVideoExposed : RepositorioMetricaVideo {
    override suspend fun insertarLote(sesionId: UUID, metricas: List<MetricaVideo>): Int = transaccion {
        TablaMetricaVideo.batchInsert(metricas, shouldReturnGeneratedValues = false) { metrica ->
            this[TablaMetricaVideo.metricaId] = UUID.randomUUID()
            this[TablaMetricaVideo.sesionId] = sesionId
            this[TablaMetricaVideo.timestampMs] = metrica.timestampMs
            this[TablaMetricaVideo.contactoVisual] = metrica.contactoVisual
            this[TablaMetricaVideo.postura] = metrica.postura
            this[TablaMetricaVideo.confianza] = metrica.confianza
            this[TablaMetricaVideo.gestos] = metrica.gestos
            this[TablaMetricaVideo.expresionDominante] = metrica.expresionDominante
        }
        metricas.size
    }
}
