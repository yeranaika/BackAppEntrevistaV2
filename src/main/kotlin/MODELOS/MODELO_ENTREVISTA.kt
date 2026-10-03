package MODELOS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EstadoSesionEntrevista(val valorBd: String) {
    EN_PROGRESO("en_progreso"),
    FINALIZADA("finalizada"),
    CANCELADA("cancelada");

    companion object {
        fun desdeBd(valor: String): EstadoSesionEntrevista = entries.first { it.valorBd == valor }
    }
}

/** Expresiones que admite metrica_video.expresion_dominante. */
val EXPRESIONES_VALIDAS = setOf("seguro", "nervioso", "distraido", "neutral", "confuso")

/** Opción de una pregunta tal como estaba al armar la sesión (se guarda en opciones_snap). */
@Serializable
data class OpcionSnapshot(
    val id: String,
    val texto: String,
    @SerialName("es_correcta") val esCorrecta: Boolean,
    /** Por qué la opción es (o no) correcta: es el feedback inmediato de la práctica. */
    val explicacion: String? = null
)

/** Pregunta de una sesión (un "slot"): snapshot de la pregunta + la respuesta del usuario. */
data class PreguntaSesion(
    val id: UUID,
    val preguntaId: UUID?,
    val orden: Int,
    val enunciado: String,
    val respuestaIdeal: String?,
    val tipo: TipoPregunta,
    val categoria: CategoriaHabilidad,
    val skillId: UUID?,
    val opciones: List<OpcionSnapshot>,
    val transcripcion: String?,
    val videoClipUrl: String?,
    val opcionElegidaId: String?,
    val puntaje: BigDecimal?,
    val fechaRespuesta: Instant?,
    /** Corrección del reporte (feedback_ia_tecnico o feedback_ia_blando): { puntaje, observacion, mejoras, modo } */
    val feedback: JsonObject? = null
) {
    val estaRespondida: Boolean get() = fechaRespuesta != null
    val opcionCorrecta: OpcionSnapshot? get() = opciones.firstOrNull { it.esCorrecta }
}

data class SesionEntrevista(
    val id: UUID,
    val usuarioId: UUID,
    val cargoId: UUID?,
    val cargoObjetivo: String,
    val nivel: NivelExperiencia,
    val estado: EstadoSesionEntrevista,
    val fechaInicio: Instant,
    val fechaFin: Instant?,
    val preguntas: List<PreguntaSesion>
) {
    val respondidas: Int get() = preguntas.count { it.estaRespondida }
}

data class NuevaSesionEntrevista(
    val usuarioId: UUID,
    val cargoId: UUID?,
    val cargoObjetivo: String,
    val nivel: NivelExperiencia,
    val inicio: Instant
)

/** Respuesta ya validada por el servicio, lista para guardar. */
data class RespuestaRegistrada(
    val preguntaSesionId: UUID,
    val transcripcion: String?,
    val videoClipUrl: String?,
    val opcionElegidaId: String?,
    val puntaje: BigDecimal?
)

data class MetricaVideo(
    val timestampMs: Long,
    val contactoVisual: BigDecimal?,
    val postura: BigDecimal?,
    val confianza: BigDecimal?,
    val gestos: JsonObject?,
    val expresionDominante: String?
)

/** Resumen para el historial (sin las preguntas). */
data class ResumenSesionEntrevista(
    val id: UUID,
    val cargoId: UUID?,
    val cargoObjetivo: String,
    val nivel: NivelExperiencia,
    val estado: EstadoSesionEntrevista,
    val fechaInicio: Instant,
    val fechaFin: Instant?,
    val totalPreguntas: Int,
    val respondidas: Int
)

data class PaginaSesionesEntrevista(
    val elementos: List<ResumenSesionEntrevista>,
    val total: Long,
    val pagina: Int,
    val tamano: Int
)

/** Criterio para elegir preguntas aprobadas del banco. */
data class CriterioSeleccionPreguntas(
    val nivel: NivelExperiencia,
    val categoria: CategoriaHabilidad,
    /** Solo preguntas de este cargo. */
    val cargoId: UUID? = null,
    /** Solo preguntas de alguna de estas skills (vacío = sin filtro). */
    val skillIds: Set<UUID> = emptySet(),
    /** Solo preguntas que no son de ningún cargo. */
    val soloSinCargo: Boolean = false,
    /** Solo preguntas que no son de ninguna skill (generales). */
    val soloSinSkill: Boolean = false,
    /** Solo estos tipos (vacío = cualquiera). */
    val tipos: Set<TipoPregunta> = emptySet(),
    val excluir: Set<UUID> = emptySet()
)
