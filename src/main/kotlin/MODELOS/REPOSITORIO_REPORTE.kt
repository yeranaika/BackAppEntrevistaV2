package MODELOS

import CONFIGURACION.CODIGO_GENERACION_INTERRUMPIDA
import UTILIDADES.transaccion
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.time.Instant
import java.util.UUID

private const val LARGO_MAXIMO_ERROR = 500

interface RepositorioReporte {
    /** Crea el reporte en 'generando' si la entrevista aún no tiene uno. */
    suspend fun crearPendiente(sesionId: UUID, fecha: Instant)

    suspend fun buscarPorSesion(sesionId: UUID): ReporteEntrevista?

    /**
     * Toma el reporte para generarlo: el primer intento (recién creado) o un reintento desde 'error'
     * mientras no supere [maximoIntentos]. Suma un intento. false si otro ya lo tomó o no corresponde:
     * así dos generaciones de la misma entrevista nunca corren a la vez. [fecha] marca el inicio del intento: el
     * rescate la usa para saber si alguien lo sigue generando (D20). [intento] identifica este intento: las escrituras
     * de la generación lo pasan, así una generación que el rescate dio por abandonada no pisa a un reintento posterior.
     */
    suspend fun tomarParaGenerar(sesionId: UUID, maximoIntentos: Int, fecha: Instant, intento: UUID = UUID.randomUUID()): Boolean

    /** Guarda el reporte, su detalle por skill y la corrección de cada respuesta abierta, todo junto. */
    suspend fun guardarResultado(sesionId: UUID, resultado: ResultadoReporte, intento: UUID? = null): Boolean

    /** Deja el reporte en 'error'. Si tenía un cupo de IA reservado lo devuelve (modo freemium): no se entregó IA. */
    suspend fun marcarError(sesionId: UUID, detalle: String, fecha: Instant, intento: UUID? = null)

    /**
     * Deja en 'error' reintentable un reporte que se estaba generando cuando se apagó el servidor (D20): devuelve el
     * cupo de IA y el intento no cuenta. Solo actúa si sigue en 'generando'.
     */
    suspend fun marcarInterrumpido(sesionId: UUID, fecha: Instant, intento: UUID? = null)

    /**
     * Rescata lo que un apagado sin aviso (SIGKILL, caída) dejó a medias (D20): reportes en 'generando' tomados antes de
     * [limite] y entrevistas finalizadas en [desdeSesiones, limite) que no llegaron a tener reporte (como mucho
     * [maximoSesiones]). Todos quedan en 'error' reintentable. Devuelve cuántos rescató.
     */
    suspend fun rescatarInterrumpidos(limite: Instant, desdeSesiones: Instant, fecha: Instant, maximoSesiones: Int): Int

    /** Reportes listos y evaluados con IA de [usuarioId] cuya fecha de generación está en [desde, hasta). */
    suspend fun contarEvaluacionesIa(usuarioId: UUID, desde: Instant, hasta: Instant): Long

    /**
     * Reserva un cupo del tope mensual de IA (D17). Si el usuario tiene menos de [tope] evaluaciones con IA en
     * [desde, hasta) —listas, o reservadas en reportes que siguen generando desde [vigentesDesde], sin contar este—,
     * marca el reporte de [sesionId] (que debe estar en 'generando') con modo ia y fecha [fecha] y devuelve true.
     * Bloquea la fila del usuario (D08) para que dos reservas simultáneas no superen el tope. El modo definitivo lo
     * escribe después guardarResultado (freemium si la IA cayó al respaldo) o marcarError.
     */
    suspend fun reservarEvaluacionIa(
        sesionId: UUID,
        usuarioId: UUID,
        tope: Int,
        desde: Instant,
        hasta: Instant,
        vigentesDesde: Instant,
        fecha: Instant
    ): Boolean

    suspend fun listarDeUsuario(usuarioId: UUID, limite: Int): List<ResumenReporte>

    /** Puntaje por skill en cada entrevista con reporte listo, del más antiguo al más reciente. */
    suspend fun progresoPorSkill(usuarioId: UUID, limite: Int): List<PuntoProgresoSkill>
}

class RepositorioReporteExposed : RepositorioReporte {

    override suspend fun crearPendiente(sesionId: UUID, fecha: Instant) {
        transaccion {
            // El índice único por sesión hace que crear dos veces no duplique (ON CONFLICT DO NOTHING).
            TablaReporteEntrevista.insertIgnore {
                it[reporteId] = UUID.randomUUID()
                it[TablaReporteEntrevista.sesionId] = sesionId
                it[estado] = EstadoReporte.GENERANDO.valorBd
                it[fechaGeneracion] = fecha
            }
        }
    }

    override suspend fun buscarPorSesion(sesionId: UUID): ReporteEntrevista? = transaccion {
        TablaReporteEntrevista.selectAll().where { TablaReporteEntrevista.sesionId eq sesionId }.firstOrNull()?.aReporte()
    }

    override suspend fun tomarParaGenerar(sesionId: UUID, maximoIntentos: Int, fecha: Instant, intento: UUID): Boolean = transaccion {
        val primerIntento = (TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd) and (TablaReporteEntrevista.intentos eq 0)
        val reintento = (TablaReporteEntrevista.estado eq EstadoReporte.ERROR.valorBd) and (TablaReporteEntrevista.intentos less maximoIntentos.toShort())
        TablaReporteEntrevista.update({ (TablaReporteEntrevista.sesionId eq sesionId) and (primerIntento or reintento) }) {
            it[estado] = EstadoReporte.GENERANDO.valorBd
            it[errorDetalle] = null
            it[intentos] = intentos + 1
            it[fechaGeneracion] = fecha
            it[intentoId] = intento
        } > 0
    }

    // Solo guarda si el reporte sigue en 'generando': una generación que el rescate ya dio por abandonada no pisa un
    // reintento posterior (D20). false si no guardó nada.
    override suspend fun guardarResultado(sesionId: UUID, resultado: ResultadoReporte, intento: UUID?): Boolean = transaccion {
            val reporteId = TablaReporteEntrevista.select(TablaReporteEntrevista.reporteId)
                .where { TablaReporteEntrevista.sesionId eq sesionId }
                .single()[TablaReporteEntrevista.reporteId]
            val actualizados = TablaReporteEntrevista.update({
                (TablaReporteEntrevista.reporteId eq reporteId) and delIntento(intento)
            }) {
                it[puntajeGlobal] = escala(resultado.puntajeGlobal)
                it[puntajeTecnico] = escala(resultado.puntajeTecnico)
                it[puntajeBlando] = escala(resultado.puntajeBlando)
                it[puntajeLenguajeCorporal] = escala(resultado.puntajeLenguajeCorporal)
                it[fortalezas] = resultado.fortalezas
                it[areasMejora] = resultado.areasMejora
                it[recomendaciones] = resultado.recomendaciones
                it[resumen] = resultado.resumen
                it[estado] = EstadoReporte.LISTO.valorBd
                it[errorDetalle] = null
                it[modoEvaluacion] = resultado.modo.valorBd
                it[modeloLlm] = resultado.uso?.modelo
                it[tokensEntrada] = resultado.uso?.tokensEntrada
                it[tokensSalida] = resultado.uso?.tokensSalida
                it[costoUsd] = resultado.uso?.costoUsd?.let { costo -> BigDecimal.valueOf(costo).setScale(6, RoundingMode.HALF_UP) }
                it[fechaGeneracion] = resultado.fecha
            }
            if (actualizados == 0) return@transaccion false
            TablaReporteSkillDetalle.deleteWhere { TablaReporteSkillDetalle.reporteId eq reporteId }
            TablaReporteSkillDetalle.batchInsert(resultado.detalles, shouldReturnGeneratedValues = false) { detalle ->
                this[TablaReporteSkillDetalle.detalleId] = UUID.randomUUID()
                this[TablaReporteSkillDetalle.reporteId] = reporteId
                this[TablaReporteSkillDetalle.skillId] = detalle.skillId
                this[TablaReporteSkillDetalle.nombreSkill] = detalle.nombre.take(100)
                this[TablaReporteSkillDetalle.categoria] = detalle.categoria.valorBd
                this[TablaReporteSkillDetalle.puntaje] = escala(detalle.puntaje)
                this[TablaReporteSkillDetalle.nivelEvaluado] = detalle.nivelEvaluado?.valorBd
                this[TablaReporteSkillDetalle.observacion] = detalle.observacion
                this[TablaReporteSkillDetalle.preguntasRespondidas] = detalle.preguntasRespondidas
            }
            resultado.evaluaciones.forEach { evaluacion ->
                val feedback = buildJsonObject {
                    put("puntaje", evaluacion.puntaje.toDouble())
                    put("observacion", evaluacion.observacion)
                    putJsonArray("mejoras") { evaluacion.mejoras.forEach { add(JsonPrimitive(it)) } }
                    put("modo", evaluacion.modo.valorBd)
                }
                TablaSesionPreguntaRespuesta.update({
                    (TablaSesionPreguntaRespuesta.respuestaId eq evaluacion.preguntaSesionId) and (TablaSesionPreguntaRespuesta.sesionId eq sesionId)
                }) {
                    it[puntaje] = escala(evaluacion.puntaje)
                    if (evaluacion.categoria == CategoriaHabilidad.BLANDA) it[feedbackBlando] = feedback else it[feedbackTecnico] = feedback
                }
            }
            true
    }

    // Con [intento], solo si el reporte sigue en manos de ese intento: una generación abandonada no pasa a 'error' un
    // reporte que un reintento posterior ya dejó listo (D20).
    override suspend fun marcarError(sesionId: UUID, detalle: String, fecha: Instant, intento: UUID?) {
        transaccion {
            val condicion = (TablaReporteEntrevista.sesionId eq sesionId).let { if (intento == null) it else it and delIntento(intento) }
            TablaReporteEntrevista.update({ condicion }) {
                it[estado] = EstadoReporte.ERROR.valorBd
                it[errorDetalle] = detalle.take(LARGO_MAXIMO_ERROR)
                it[fechaGeneracion] = fecha
                // Un reporte con error no entregó IA: devuelve el cupo que pudo haber reservado.
                it[modoEvaluacion] = ModoEvaluacion.FREEMIUM.valorBd
            }
        }
    }

    override suspend fun marcarInterrumpido(sesionId: UUID, fecha: Instant, intento: UUID?) {
        transaccion { interrumpir((TablaReporteEntrevista.sesionId eq sesionId) and delIntento(intento), fecha) }
    }

    /** El reporte sigue en 'generando' y, si se indica [intento], es ese intento y no uno posterior (D20). */
    private fun delIntento(intento: UUID?): Op<Boolean> {
        val generando = TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd
        return if (intento == null) generando else generando and (TablaReporteEntrevista.intentoId eq intento)
    }

    override suspend fun rescatarInterrumpidos(limite: Instant, desdeSesiones: Instant, fecha: Instant, maximoSesiones: Int): Int = transaccion {
        val abandonados = (TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd) and
            (TablaReporteEntrevista.fechaGeneracion less limite)
        val reportes = interrumpir(abandonados, fecha)
        // Entrevistas que se finalizaron pero el servidor murió antes de crear siquiera el reporte.
        val sinReporte = TablaSesionEntrevista
            .join(TablaReporteEntrevista, JoinType.LEFT, TablaSesionEntrevista.sesionId, TablaReporteEntrevista.sesionId)
            .select(TablaSesionEntrevista.sesionId)
            .where {
                (TablaSesionEntrevista.estado eq EstadoSesionEntrevista.FINALIZADA.valorBd) and
                    (TablaSesionEntrevista.fechaFin less limite) and
                    (TablaSesionEntrevista.fechaFin greaterEq desdeSesiones) and
                    TablaReporteEntrevista.reporteId.isNull()
            }
            .limit(maximoSesiones)
            .map { it[TablaSesionEntrevista.sesionId] }
        val creados = sinReporte.sumOf { id ->
            TablaReporteEntrevista.insertIgnore {
                it[reporteId] = UUID.randomUUID()
                it[sesionId] = id
                it[estado] = EstadoReporte.ERROR.valorBd
                it[errorDetalle] = CODIGO_GENERACION_INTERRUMPIDA
                it[fechaGeneracion] = fecha
            }.insertedCount
        }
        reportes + creados
    }

    /**
     * Pasa a 'error' reintentable los reportes de [condicion] (que debe exigir estado 'generando'): devuelve el cupo de IA
     * (modo freemium) y descuenta el intento cortado, que no fue culpa del usuario. Dos UPDATE para no dejar intentos
     * negativos; el primero ya saca la fila de 'generando', así que el segundo no la vuelve a tocar.
     */
    private fun interrumpir(condicion: Op<Boolean>, fecha: Instant): Int {
        val conIntentos = TablaReporteEntrevista.update({ condicion and (TablaReporteEntrevista.intentos greater 0.toShort()) }) {
            it[estado] = EstadoReporte.ERROR.valorBd
            it[errorDetalle] = CODIGO_GENERACION_INTERRUMPIDA
            it[modoEvaluacion] = ModoEvaluacion.FREEMIUM.valorBd
            it[intentos] = intentos - 1
            it[fechaGeneracion] = fecha
        }
        val sinIntentos = TablaReporteEntrevista.update({ condicion and (TablaReporteEntrevista.intentos eq 0.toShort()) }) {
            it[estado] = EstadoReporte.ERROR.valorBd
            it[errorDetalle] = CODIGO_GENERACION_INTERRUMPIDA
            it[modoEvaluacion] = ModoEvaluacion.FREEMIUM.valorBd
            it[fechaGeneracion] = fecha
        }
        return conIntentos + sinIntentos
    }

    override suspend fun contarEvaluacionesIa(usuarioId: UUID, desde: Instant, hasta: Instant): Long = transaccion {
        reporteConSesion().select(TablaReporteEntrevista.reporteId).where {
            (TablaSesionEntrevista.usuarioId eq usuarioId) and
                (TablaReporteEntrevista.modoEvaluacion eq ModoEvaluacion.IA.valorBd) and
                (TablaReporteEntrevista.estado eq EstadoReporte.LISTO.valorBd) and
                (TablaReporteEntrevista.fechaGeneracion greaterEq desde) and
                (TablaReporteEntrevista.fechaGeneracion less hasta)
        }.count()
    }

    // READ COMMITTED explícito: después de esperar el bloqueo, el conteo tiene que ver la reserva que otra transacción
    // acaba de confirmar. Es el nivel por defecto de Postgres, pero en H2 (pruebas) el por defecto es más estricto.
    override suspend fun reservarEvaluacionIa(
        sesionId: UUID,
        usuarioId: UUID,
        tope: Int,
        desde: Instant,
        hasta: Instant,
        vigentesDesde: Instant,
        fecha: Instant
    ): Boolean = transaccion(Connection.TRANSACTION_READ_COMMITTED) {
        TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq usuarioId }.forUpdate().toList()
        val listas = TablaReporteEntrevista.estado eq EstadoReporte.LISTO.valorBd
        val reservadasVigentes = (TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd) and
            (TablaReporteEntrevista.fechaGeneracion greaterEq vigentesDesde)
        val usadas = reporteConSesion().select(TablaReporteEntrevista.reporteId).where {
            (TablaSesionEntrevista.usuarioId eq usuarioId) and
                (TablaReporteEntrevista.sesionId neq sesionId) and
                (TablaReporteEntrevista.modoEvaluacion eq ModoEvaluacion.IA.valorBd) and
                (TablaReporteEntrevista.fechaGeneracion greaterEq desde) and
                (TablaReporteEntrevista.fechaGeneracion less hasta) and
                (listas or reservadasVigentes)
        }.count()
        if (usadas >= tope) return@transaccion false
        TablaReporteEntrevista.update({
            (TablaReporteEntrevista.sesionId eq sesionId) and (TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd)
        }) {
            it[modoEvaluacion] = ModoEvaluacion.IA.valorBd
            it[fechaGeneracion] = fecha
        } > 0
    }

    override suspend fun listarDeUsuario(usuarioId: UUID, limite: Int): List<ResumenReporte> = transaccion {
        reporteConSesion().selectAll()
            .where { TablaSesionEntrevista.usuarioId eq usuarioId }
            .orderBy(TablaSesionEntrevista.fechaInicio to SortOrder.DESC)
            .limit(limite)
            .map {
                ResumenReporte(
                    sesionId = it[TablaSesionEntrevista.sesionId],
                    cargoObjetivo = it[TablaSesionEntrevista.cargoObjetivo],
                    nivel = NivelExperiencia.desdeBd(it[TablaSesionEntrevista.nivelDificultad]) ?: NivelExperiencia.JUNIOR,
                    fechaEntrevista = it[TablaSesionEntrevista.fechaInicio],
                    estado = EstadoReporte.desdeBd(it[TablaReporteEntrevista.estado]),
                    puntajeGlobal = it[TablaReporteEntrevista.puntajeGlobal],
                    fecha = it[TablaReporteEntrevista.fechaGeneracion]
                )
            }
    }

    override suspend fun progresoPorSkill(usuarioId: UUID, limite: Int): List<PuntoProgresoSkill> = transaccion {
        TablaReporteSkillDetalle.join(reporteConSesion(), JoinType.INNER, TablaReporteSkillDetalle.reporteId, TablaReporteEntrevista.reporteId).selectAll()
            .where {
                (TablaSesionEntrevista.usuarioId eq usuarioId) and
                    (TablaReporteEntrevista.estado eq EstadoReporte.LISTO.valorBd) and
                    TablaReporteSkillDetalle.skillId.isNotNull()
            }
            .orderBy(TablaSesionEntrevista.fechaInicio to SortOrder.DESC)
            .limit(limite)
            .map {
                PuntoProgresoSkill(
                    skillId = it[TablaReporteSkillDetalle.skillId]!!,
                    sesionId = it[TablaSesionEntrevista.sesionId],
                    puntaje = it[TablaReporteSkillDetalle.puntaje],
                    fecha = it[TablaSesionEntrevista.fechaInicio]
                )
            }
            .reversed()
    }

    // Las tablas no declaran sus FK en Exposed: los joins indican las columnas.
    private fun reporteConSesion() =
        TablaReporteEntrevista.join(TablaSesionEntrevista, JoinType.INNER, TablaReporteEntrevista.sesionId, TablaSesionEntrevista.sesionId)

    private fun escala(valor: BigDecimal) = valor.setScale(2, RoundingMode.HALF_UP)

    private fun ResultRow.aReporte(): ReporteEntrevista {
        val id = this[TablaReporteEntrevista.reporteId]
        val detalles = TablaReporteSkillDetalle.selectAll()
            .where { TablaReporteSkillDetalle.reporteId eq id }
            .orderBy(TablaReporteSkillDetalle.puntaje to SortOrder.DESC)
            .map {
                DetalleSkillReporte(
                    skillId = it[TablaReporteSkillDetalle.skillId],
                    nombre = it[TablaReporteSkillDetalle.nombreSkill],
                    categoria = CategoriaHabilidad.desdeBd(it[TablaReporteSkillDetalle.categoria]) ?: CategoriaHabilidad.TECNICA,
                    puntaje = it[TablaReporteSkillDetalle.puntaje],
                    nivelEvaluado = NivelExperiencia.desdeBd(it[TablaReporteSkillDetalle.nivelEvaluado]),
                    observacion = it[TablaReporteSkillDetalle.observacion],
                    preguntasRespondidas = it[TablaReporteSkillDetalle.preguntasRespondidas]
                )
            }
        val modelo = this[TablaReporteEntrevista.modeloLlm]
        return ReporteEntrevista(
            id = id,
            sesionId = this[TablaReporteEntrevista.sesionId],
            estado = EstadoReporte.desdeBd(this[TablaReporteEntrevista.estado]),
            puntajeGlobal = this[TablaReporteEntrevista.puntajeGlobal],
            puntajeTecnico = this[TablaReporteEntrevista.puntajeTecnico],
            puntajeBlando = this[TablaReporteEntrevista.puntajeBlando],
            puntajeLenguajeCorporal = this[TablaReporteEntrevista.puntajeLenguajeCorporal],
            fortalezas = this[TablaReporteEntrevista.fortalezas],
            areasMejora = this[TablaReporteEntrevista.areasMejora],
            recomendaciones = this[TablaReporteEntrevista.recomendaciones],
            resumen = this[TablaReporteEntrevista.resumen],
            errorDetalle = this[TablaReporteEntrevista.errorDetalle],
            modo = ModoEvaluacion.desdeBd(this[TablaReporteEntrevista.modoEvaluacion]),
            uso = modelo?.let {
                UsoLlm(it, this[TablaReporteEntrevista.tokensEntrada], this[TablaReporteEntrevista.tokensSalida], this[TablaReporteEntrevista.costoUsd]?.toDouble() ?: 0.0)
            },
            intentos = this[TablaReporteEntrevista.intentos].toInt(),
            detalles = detalles,
            fecha = this[TablaReporteEntrevista.fechaGeneracion]
        )
    }
}
