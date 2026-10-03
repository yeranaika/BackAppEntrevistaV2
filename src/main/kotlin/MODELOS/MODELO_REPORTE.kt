package MODELOS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EstadoReporte(val valorBd: String) {
    GENERANDO("generando"),
    LISTO("listo"),
    ERROR("error");

    companion object {
        fun desdeBd(valor: String): EstadoReporte = entries.first { it.valorBd == valor }
    }
}

/** Con qué se corrigieron las respuestas abiertas. */
enum class ModoEvaluacion(val valorBd: String) {
    FREEMIUM("freemium"),
    IA("ia");

    companion object {
        fun desdeBd(valor: String): ModoEvaluacion = entries.firstOrNull { it.valorBd == valor } ?: FREEMIUM
    }
}

/** Skill del cargo que conviene reforzar (reporte_entrevista.recomendaciones_skills). */
@Serializable
data class RecomendacionSkill(
    @SerialName("skill_id") val skillId: String,
    val nombre: String,
    /** Puntaje 0-100 en esta entrevista */
    val puntaje: Double,
    @SerialName("nivel_requerido") val nivelRequerido: String? = null,
    /** alta | media */
    val prioridad: String
)

/** Una fila del radar: cómo le fue en una skill (o en un grupo general) en la entrevista. */
data class DetalleSkillReporte(
    val skillId: UUID?,
    val nombre: String,
    val categoria: CategoriaHabilidad,
    /** 0-100 */
    val puntaje: BigDecimal,
    val nivelEvaluado: NivelExperiencia?,
    val observacion: String?,
    val preguntasRespondidas: Int
)

/** Corrección de una respuesta abierta de la entrevista (va a sesion_pregunta_respuesta). */
data class EvaluacionPreguntaSesion(
    val preguntaSesionId: UUID,
    val categoria: CategoriaHabilidad,
    /** 0-100 */
    val puntaje: BigDecimal,
    val observacion: String,
    val mejoras: List<String>,
    val modo: ModoEvaluacion
)

/** Uso del LLM al generar un reporte (null si se usó el motor freemium). */
data class UsoLlm(
    val modelo: String,
    val tokensEntrada: Int?,
    val tokensSalida: Int?,
    val costoUsd: Double
)

/** Todo lo que produce la generación, listo para guardar de una vez. */
data class ResultadoReporte(
    val puntajeGlobal: BigDecimal,
    val puntajeTecnico: BigDecimal,
    val puntajeBlando: BigDecimal,
    val puntajeLenguajeCorporal: BigDecimal,
    val fortalezas: List<String>,
    val areasMejora: List<String>,
    val recomendaciones: List<RecomendacionSkill>,
    val resumen: String,
    val modo: ModoEvaluacion,
    val uso: UsoLlm?,
    val detalles: List<DetalleSkillReporte>,
    val evaluaciones: List<EvaluacionPreguntaSesion>,
    val fecha: Instant
)

data class ReporteEntrevista(
    val id: UUID,
    val sesionId: UUID,
    val estado: EstadoReporte,
    val puntajeGlobal: BigDecimal,
    val puntajeTecnico: BigDecimal,
    val puntajeBlando: BigDecimal,
    val puntajeLenguajeCorporal: BigDecimal,
    val fortalezas: List<String>,
    val areasMejora: List<String>,
    val recomendaciones: List<RecomendacionSkill>,
    val resumen: String?,
    val errorDetalle: String?,
    val modo: ModoEvaluacion,
    val uso: UsoLlm?,
    val intentos: Int,
    val detalles: List<DetalleSkillReporte>,
    val fecha: Instant
)

/** Para el historial: el reporte con los datos de su entrevista. */
data class ResumenReporte(
    val sesionId: UUID,
    val cargoObjetivo: String,
    val nivel: NivelExperiencia,
    val fechaEntrevista: Instant,
    val estado: EstadoReporte,
    val puntajeGlobal: BigDecimal,
    val fecha: Instant
)

/** Un punto de la evolución de una skill: el puntaje que sacó en una entrevista. */
data class PuntoProgresoSkill(
    val skillId: UUID,
    val sesionId: UUID,
    val puntaje: BigDecimal,
    val fecha: Instant
)
