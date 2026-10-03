package MODELOS

import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime
import java.util.UUID

/** Valores que acepta el CHECK de pregunta.tipo_pregunta. */
enum class TipoPregunta(val valorBd: String) {
    OPCION_MULTIPLE("opcion_multiple"),
    ABIERTA_TEXTO("abierta_texto"),
    SIMULACION_VIDEO("simulacion_video");

    companion object {
        fun desdeBd(valor: String): TipoPregunta? = entries.firstOrNull { it.valorBd == valor }
    }
}

enum class CategoriaHabilidad(val valorBd: String) {
    TECNICA("tecnica"),
    BLANDA("blanda");

    companion object {
        fun desdeBd(valor: String): CategoriaHabilidad? = entries.firstOrNull { it.valorBd == valor }
    }
}

/** Solo las preguntas APROBADA se sirven a los usuarios. */
enum class EstadoPregunta(val valorBd: String, val valorTrazabilidad: String) {
    PENDIENTE("pendiente", "pendiente_revision"),
    APROBADA("aprobada", "aprobada"),
    RECHAZADA("rechazada", "rechazada");

    companion object {
        fun desdeBd(valor: String): EstadoPregunta? = entries.firstOrNull { it.valorBd == valor }
    }
}

data class OpcionPregunta(
    val id: UUID,
    val texto: String,
    val esCorrecta: Boolean,
    val explicacion: String?,
    val orden: Int
)

data class Pregunta(
    val id: UUID,
    val skillId: UUID?,
    val cargoId: UUID?,
    val tipo: TipoPregunta,
    val categoria: CategoriaHabilidad,
    /** El nivel de dificultad usa la misma escala que el nivel de experiencia. */
    val nivel: NivelExperiencia,
    val enunciado: String,
    val respuestaIdeal: String?,
    val rubrica: JsonObject?,
    val generadaPorIa: Boolean,
    val estado: EstadoPregunta,
    val motivoRechazo: String?,
    val vecesUsada: Int,
    val fechaCreacion: OffsetDateTime,
    val opciones: List<OpcionPregunta>
)

data class NuevaOpcion(
    val texto: String,
    val esCorrecta: Boolean,
    val explicacion: String?
)

/** Contenido de una pregunta al crearla o editarla. */
data class ContenidoPregunta(
    val skillId: UUID?,
    val cargoId: UUID?,
    val tipo: TipoPregunta,
    val categoria: CategoriaHabilidad,
    val nivel: NivelExperiencia,
    val enunciado: String,
    val respuestaIdeal: String?,
    val rubrica: JsonObject?,
    val opciones: List<NuevaOpcion>
)

data class FiltroPreguntas(
    val estado: EstadoPregunta? = null,
    val tipo: TipoPregunta? = null,
    val categoria: CategoriaHabilidad? = null,
    val nivel: NivelExperiencia? = null,
    val skillId: UUID? = null,
    val cargoId: UUID? = null,
    val generadaPorIa: Boolean? = null
)

data class PaginaPreguntas(
    val elementos: List<Pregunta>,
    val total: Long,
    val pagina: Int,
    val tamano: Int
)
