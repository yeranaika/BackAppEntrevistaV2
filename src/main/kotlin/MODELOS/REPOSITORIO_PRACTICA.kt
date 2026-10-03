package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

private val DIEZ = BigDecimal.TEN

data class IntentoGuardado(val id: UUID, val esNuevo: Boolean)

interface RepositorioPractica {
    /** Crea la sesión; las que el usuario tenía en progreso quedan abandonadas. */
    suspend fun crear(nueva: NuevaSesionPractica): SesionPractica

    suspend fun buscar(id: UUID): SesionPractica?

    /** Guarda todas o ninguna: la sesión debe seguir en progreso y ninguna pregunta puede estar respondida. */
    suspend fun registrarRespuestas(sesionId: UUID, respuestas: List<RespuestaPractica>): ResultadoRegistroRespuestas

    /** Pasa de en_progreso a finalizada con su puntaje (0-100). false si ya no estaba en progreso. */
    suspend fun finalizar(sesionId: UUID, puntaje: BigDecimal, fin: Instant): Boolean

    /**
     * Guarda un intento hecho sin conexión, ya finalizado. Idempotente: si ese id local ya se
     * sincronizó, devuelve el id existente sin duplicar (esNuevo = false).
     */
    suspend fun guardarOffline(intento: IntentoOffline, puntaje: BigDecimal): IntentoGuardado

    suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba>

    /** Preguntas del banco que el usuario practicó en sus últimas [sesiones] sesiones. */
    suspend fun preguntasRecientes(usuarioId: UUID, sesiones: Int): Set<UUID>
}

class RepositorioPracticaExposed : RepositorioPractica {

    override suspend fun crear(nueva: NuevaSesionPractica): SesionPractica {
        val id = UUID.randomUUID()
        transaccion {
            bloquearUsuario(nueva.usuarioId)
            TablaSesionPractica.update({
                (TablaSesionPractica.usuarioId eq nueva.usuarioId) and (TablaSesionPractica.estado eq EstadoPractica.EN_PROGRESO.valorBd)
            }) {
                it[estado] = EstadoPractica.ABANDONADA.valorBd
                it[fechaFin] = nueva.inicio
            }
            insertarSesion(id, nueva, EstadoPractica.EN_PROGRESO, idLocal = null, puntaje = null, fin = null)
        }
        return buscar(id)!!
    }

    override suspend fun buscar(id: UUID): SesionPractica? = transaccion {
        TablaSesionPractica.selectAll().where { TablaSesionPractica.sesionId eq id }.firstOrNull()?.let(::conRespuestas)
    }

    override suspend fun registrarRespuestas(sesionId: UUID, respuestas: List<RespuestaPractica>): ResultadoRegistroRespuestas = transaccion {
        // El bloqueo evita que la sesión se finalice mientras se guardan las respuestas.
        val sesion = TablaSesionPractica.selectAll().where { TablaSesionPractica.sesionId eq sesionId }.forUpdate().firstOrNull()
        if (sesion == null || sesion[TablaSesionPractica.estado] != EstadoPractica.EN_PROGRESO.valorBd) {
            return@transaccion ResultadoRegistroRespuestas.SESION_NO_ACTIVA
        }
        val ordenes = respuestas.map { it.orden.toShort() }
        val yaRespondidas = TablaRespuestaPractica.selectAll()
            .where { (TablaRespuestaPractica.sesionId eq sesionId) and (TablaRespuestaPractica.orden inList ordenes) }
            .count()
        if (yaRespondidas > 0) return@transaccion ResultadoRegistroRespuestas.YA_RESPONDIDA
        insertarRespuestas(sesionId, sesion[TablaSesionPractica.preguntas], respuestas)
        ResultadoRegistroRespuestas.REGISTRADAS
    }

    override suspend fun finalizar(sesionId: UUID, puntaje: BigDecimal, fin: Instant): Boolean = transaccion {
        val respuestas = TablaRespuestaPractica.selectAll().where { TablaRespuestaPractica.sesionId eq sesionId }.toList()
        TablaSesionPractica.update({
            (TablaSesionPractica.sesionId eq sesionId) and (TablaSesionPractica.estado eq EstadoPractica.EN_PROGRESO.valorBd)
        }) {
            it[estado] = EstadoPractica.FINALIZADA.valorBd
            it[puntajeSesion] = puntaje.setScale(2, RoundingMode.HALF_UP)
            it[totalPreguntas] = respuestas.size.toShort()
            it[correctas] = respuestas.count { fila -> fila[TablaRespuestaPractica.esCorrecta] == true }.toShort()
            it[fechaFin] = fin
        } > 0
    }

    override suspend fun guardarOffline(intento: IntentoOffline, puntaje: BigDecimal): IntentoGuardado = transaccion {
        val usuarioId = intento.sesion.usuarioId
        bloquearUsuario(usuarioId)
        val existente = TablaSesionPractica.select(TablaSesionPractica.sesionId)
            .where { (TablaSesionPractica.usuarioId eq usuarioId) and (TablaSesionPractica.idLocal eq intento.idLocal) }
            .firstOrNull()?.get(TablaSesionPractica.sesionId)
        if (existente != null) return@transaccion IntentoGuardado(existente, esNuevo = false)

        val id = UUID.randomUUID()
        insertarSesion(id, intento.sesion, EstadoPractica.FINALIZADA, intento.idLocal, puntaje, intento.fin)
        insertarRespuestas(id, intento.sesion.preguntas, intento.respuestas)
        TablaSesionPractica.update({ TablaSesionPractica.sesionId eq id }) {
            it[totalPreguntas] = intento.respuestas.size.toShort()
            it[correctas] = intento.respuestas.count { r -> r.esCorrecta }.toShort()
        }
        IntentoGuardado(id, esNuevo = true)
    }

    override suspend fun listarResumenes(usuarioId: UUID, limite: Int): List<ResumenPrueba> = transaccion {
        TablaSesionPractica.selectAll()
            .where { TablaSesionPractica.usuarioId eq usuarioId }
            .orderBy(TablaSesionPractica.fechaInicio to SortOrder.DESC)
            .limit(limite)
            .map { fila ->
                val estado = EstadoPractica.desdeBd(fila[TablaSesionPractica.estado])
                ResumenPrueba(
                    id = fila[TablaSesionPractica.sesionId],
                    tipo = "practica",
                    cargoObjetivo = fila[TablaSesionPractica.cargoObjetivo],
                    nivel = NivelExperiencia.desdeBd(fila[TablaSesionPractica.nivelPreguntas]),
                    estado = estado.valorBd,
                    puntaje = if (estado == EstadoPractica.FINALIZADA) fila[TablaSesionPractica.correctas].toInt() else null,
                    puntajeTotal = fila[TablaSesionPractica.preguntas].size,
                    fechaInicio = fila[TablaSesionPractica.fechaInicio],
                    fechaFin = fila[TablaSesionPractica.fechaFin]
                )
            }
    }

    override suspend fun preguntasRecientes(usuarioId: UUID, sesiones: Int): Set<UUID> = transaccion {
        TablaSesionPractica.select(TablaSesionPractica.preguntas)
            .where { TablaSesionPractica.usuarioId eq usuarioId }
            .orderBy(TablaSesionPractica.fechaInicio to SortOrder.DESC)
            .limit(sesiones)
            .flatMap { fila -> fila[TablaSesionPractica.preguntas].mapNotNull { it.preguntaId?.let(UUID::fromString) } }
            .toSet()
    }

    // ---------- Apoyo (dentro de una transacción) ----------

    private fun bloquearUsuario(usuarioId: UUID) {
        TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq usuarioId }.forUpdate().toList()
    }

    private fun insertarSesion(
        id: UUID,
        nueva: NuevaSesionPractica,
        estado: EstadoPractica,
        idLocal: String?,
        puntaje: BigDecimal?,
        fin: Instant?
    ) {
        TablaSesionPractica.insert {
            it[sesionId] = id
            it[usuarioId] = nueva.usuarioId
            it[skillId] = nueva.skillId
            it[cargoId] = nueva.cargoId
            it[cargoObjetivo] = nueva.cargoObjetivo
            it[modo] = nueva.modo.valorBd
            it[categoria] = nueva.categoria.valorBd
            it[nivelPreguntas] = nueva.nivel.valorBd
            it[TablaSesionPractica.estado] = estado.valorBd
            it[preguntas] = nueva.preguntas
            it[TablaSesionPractica.idLocal] = idLocal
            it[puntajeSesion] = puntaje?.setScale(2, RoundingMode.HALF_UP)
            it[fechaInicio] = nueva.inicio
            it[fechaFin] = fin
        }
    }

    private fun insertarRespuestas(sesionId: UUID, preguntas: List<PreguntaServida>, respuestas: List<RespuestaPractica>) {
        // opcion_elegida_id tiene FK: si la opción se borró del banco después de servirla, se guarda sin ella.
        val opcionesVigentes = TablaOpcionPregunta.select(TablaOpcionPregunta.opcionId)
            .where { TablaOpcionPregunta.opcionId inList respuestas.mapNotNull { it.opcionElegidaId?.let(UUID::fromString) } }
            .map { it[TablaOpcionPregunta.opcionId] }
            .toSet()
        val porOrden = preguntas.associateBy { it.orden }
        respuestas.forEach { respuesta ->
            val pregunta = porOrden.getValue(respuesta.orden)
            TablaRespuestaPractica.insert {
                it[respuestaId] = UUID.randomUUID()
                it[TablaRespuestaPractica.sesionId] = sesionId
                it[preguntaId] = pregunta.preguntaId?.let(UUID::fromString)
                it[enunciado] = pregunta.enunciado
                it[opcionElegidaId] = respuesta.opcionElegidaId?.let(UUID::fromString)?.takeIf { id -> id in opcionesVigentes }
                it[respuestaTexto] = respuesta.texto
                it[esCorrecta] = respuesta.esCorrecta
                it[puntaje] = respuesta.puntaje.divide(DIEZ, 2, RoundingMode.HALF_UP)
                it[feedbackTexto] = respuesta.feedback
                it[tiempoRespuestaMs] = respuesta.tiempoRespuestaMs
                it[orden] = respuesta.orden.toShort()
                it[fechaRespuesta] = respuesta.fecha
            }
        }
    }

    private fun conRespuestas(fila: ResultRow): SesionPractica {
        val id = fila[TablaSesionPractica.sesionId]
        val preguntas = fila[TablaSesionPractica.preguntas]
        val porOrden = preguntas.associateBy { it.orden }
        val respuestas = TablaRespuestaPractica.selectAll()
            .where { TablaRespuestaPractica.sesionId eq id }
            .orderBy(TablaRespuestaPractica.orden to SortOrder.ASC)
            .mapNotNull { r ->
                val orden = r[TablaRespuestaPractica.orden].toInt()
                val pregunta = porOrden[orden] ?: return@mapNotNull null
                RespuestaPractica(
                    preguntaServidaId = pregunta.id,
                    orden = orden,
                    opcionElegidaId = r[TablaRespuestaPractica.opcionElegidaId]?.toString(),
                    texto = r[TablaRespuestaPractica.respuestaTexto],
                    esCorrecta = r[TablaRespuestaPractica.esCorrecta] == true,
                    puntaje = (r[TablaRespuestaPractica.puntaje] ?: BigDecimal.ZERO).multiply(DIEZ),
                    feedback = r[TablaRespuestaPractica.feedbackTexto],
                    tiempoRespuestaMs = r[TablaRespuestaPractica.tiempoRespuestaMs],
                    fecha = r[TablaRespuestaPractica.fechaRespuesta]
                )
            }
        return SesionPractica(
            id = id,
            usuarioId = fila[TablaSesionPractica.usuarioId],
            skillId = fila[TablaSesionPractica.skillId],
            cargoId = fila[TablaSesionPractica.cargoId],
            cargoObjetivo = fila[TablaSesionPractica.cargoObjetivo],
            modo = ModoPractica.desdeBd(fila[TablaSesionPractica.modo]) ?: ModoPractica.MIXTO,
            categoria = CategoriaHabilidad.desdeBd(fila[TablaSesionPractica.categoria]) ?: CategoriaHabilidad.TECNICA,
            nivel = NivelExperiencia.desdeBd(fila[TablaSesionPractica.nivelPreguntas]) ?: NivelExperiencia.JUNIOR,
            estado = EstadoPractica.desdeBd(fila[TablaSesionPractica.estado]),
            puntaje = fila[TablaSesionPractica.puntajeSesion],
            preguntas = preguntas,
            respuestas = respuestas,
            fechaInicio = fila[TablaSesionPractica.fechaInicio],
            fechaFin = fila[TablaSesionPractica.fechaFin]
        )
    }
}
