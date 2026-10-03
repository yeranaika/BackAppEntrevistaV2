package MODELOS

import UTILIDADES.transaccion
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
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
     * así dos generaciones de la misma entrevista nunca corren a la vez.
     */
    suspend fun tomarParaGenerar(sesionId: UUID, maximoIntentos: Int): Boolean

    /** Guarda el reporte, su detalle por skill y la corrección de cada respuesta abierta, todo junto. */
    suspend fun guardarResultado(sesionId: UUID, resultado: ResultadoReporte)

    suspend fun marcarError(sesionId: UUID, detalle: String, fecha: Instant)

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

    override suspend fun tomarParaGenerar(sesionId: UUID, maximoIntentos: Int): Boolean = transaccion {
        val primerIntento = (TablaReporteEntrevista.estado eq EstadoReporte.GENERANDO.valorBd) and (TablaReporteEntrevista.intentos eq 0)
        val reintento = (TablaReporteEntrevista.estado eq EstadoReporte.ERROR.valorBd) and (TablaReporteEntrevista.intentos less maximoIntentos.toShort())
        TablaReporteEntrevista.update({ (TablaReporteEntrevista.sesionId eq sesionId) and (primerIntento or reintento) }) {
            it[estado] = EstadoReporte.GENERANDO.valorBd
            it[errorDetalle] = null
            it[intentos] = intentos + 1
        } > 0
    }

    override suspend fun guardarResultado(sesionId: UUID, resultado: ResultadoReporte) {
        transaccion {
            val reporteId = TablaReporteEntrevista.select(TablaReporteEntrevista.reporteId)
                .where { TablaReporteEntrevista.sesionId eq sesionId }
                .single()[TablaReporteEntrevista.reporteId]
            TablaReporteEntrevista.update({ TablaReporteEntrevista.reporteId eq reporteId }) {
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
        }
    }

    override suspend fun marcarError(sesionId: UUID, detalle: String, fecha: Instant) {
        transaccion {
            TablaReporteEntrevista.update({ TablaReporteEntrevista.sesionId eq sesionId }) {
                it[estado] = EstadoReporte.ERROR.valorBd
                it[errorDetalle] = detalle.take(LARGO_MAXIMO_ERROR)
                it[fechaGeneracion] = fecha
            }
        }
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
