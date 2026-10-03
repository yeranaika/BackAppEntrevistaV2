package MODELOS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EstadoPractica(val valorBd: String) {
    EN_PROGRESO("en_progreso"),
    FINALIZADA("finalizada"),
    ABANDONADA("abandonada");

    companion object {
        fun desdeBd(valor: String): EstadoPractica = entries.first { it.valorBd == valor }
    }
}

/** Qué tipo de preguntas pide el usuario al practicar. */
enum class ModoPractica(val valorBd: String, val tipos: Set<TipoPregunta>) {
    OPCION_MULTIPLE("opcion_multiple", setOf(TipoPregunta.OPCION_MULTIPLE)),
    ABIERTA_TEXTO("abierta_texto", setOf(TipoPregunta.ABIERTA_TEXTO)),
    // La práctica es escrita: nunca incluye preguntas de video.
    MIXTO("mixto", setOf(TipoPregunta.OPCION_MULTIPLE, TipoPregunta.ABIERTA_TEXTO));

    companion object {
        fun desdeBd(valor: String): ModoPractica? = entries.firstOrNull { it.valorBd == valor }
    }
}

const val TIPO_TEST_NIVELACION = "nivelacion"

/**
 * Pregunta tal como se sirvió (snapshot): editar o borrar el banco no cambia una prueba rendida.
 * Se guarda como JSON en sesion_practica.preguntas_snap y en intento_test.respuestas_detalle.
 */
@Serializable
data class PreguntaServida(
    /** Id de la pregunta dentro de la prueba (el que usa el cliente para responder). */
    val id: String,
    @SerialName("pregunta_id") val preguntaId: String? = null,
    val orden: Int,
    val enunciado: String,
    /** valorBd de TipoPregunta */
    val tipo: String,
    /** valorBd de CategoriaHabilidad */
    val categoria: String,
    /** valorBd de NivelExperiencia */
    val nivel: String,
    @SerialName("skill_id") val skillId: String? = null,
    val opciones: List<OpcionSnapshot> = emptyList(),
    @SerialName("respuesta_ideal") val respuestaIdeal: String? = null,
    @SerialName("palabras_clave") val palabrasClave: List<String> = emptyList()
) {
    val tipoPregunta: TipoPregunta get() = TipoPregunta.desdeBd(tipo) ?: TipoPregunta.ABIERTA_TEXTO
    val categoriaHabilidad: CategoriaHabilidad get() = CategoriaHabilidad.desdeBd(categoria) ?: CategoriaHabilidad.TECNICA
    val nivelExperiencia: NivelExperiencia get() = NivelExperiencia.desdeBd(nivel) ?: NivelExperiencia.JUNIOR
    val opcionCorrecta: OpcionSnapshot? get() = opciones.firstOrNull { it.esCorrecta }
}

/** Respuesta ya corregida. Puntaje de 0 a 100. */
@Serializable
data class RespuestaCorregida(
    @SerialName("opcion_id") val opcionId: String? = null,
    @SerialName("respuesta_texto") val texto: String? = null,
    val correcta: Boolean,
    val puntaje: Double,
    val feedback: String? = null
)

/** Elemento de intento_test.respuestas_detalle: la pregunta servida y, si la respondió, su corrección. */
@Serializable
data class DetalleIntento(
    val pregunta: PreguntaServida,
    val respuesta: RespuestaCorregida? = null
)

/** Resultado de una skill en la nivelación, comparado con lo que pide el cargo. */
@Serializable
data class EvaluacionSkill(
    @SerialName("skill_id") val skillId: String,
    val nombre: String,
    @SerialName("nivel_actual") val nivelActual: String,
    @SerialName("nivel_requerido") val nivelRequerido: String? = null,
    @SerialName("puntaje_obtenido") val puntaje: Double,
    /** Niveles que le faltan (0 si cumple). */
    val brecha: Int = 0,
    /** alta | media | baja (solo en las brechas) */
    val prioridad: String? = null
)

// ─── Nivelación ──────────────────────────────────────────────────────────────

data class TestNivelacion(
    val id: UUID,
    val titulo: String,
    val cargoId: UUID?,
    val area: String,
    val nivelObjetivo: String,
    val descripcion: String?,
    val preguntasIds: List<UUID>,
    val estaActivo: Boolean
)

data class NuevoTestNivelacion(
    val titulo: String,
    val cargoId: UUID?,
    val area: String,
    val nivelObjetivo: String,
    val descripcion: String?,
    val preguntasIds: List<UUID>
)

data class IntentoNivelacion(
    val id: UUID,
    val usuarioId: UUID,
    val testId: UUID?,
    val cargoId: UUID?,
    val cargoObjetivo: String?,
    val puntaje: BigDecimal?,
    val nivelAsignado: NivelExperiencia?,
    val detalle: List<DetalleIntento>,
    val fechaInicio: Instant,
    val fechaFin: Instant?
) {
    val estaTerminado: Boolean get() = fechaFin != null
}

data class ResultadoNivelacion(
    val intentoId: UUID,
    val cargoId: UUID?,
    val nivelGlobal: NivelExperiencia,
    val puntajeGlobal: BigDecimal,
    val skillsBrecha: List<EvaluacionSkill>,
    val skillsOk: List<EvaluacionSkill>,
    val resumen: String?,
    val fecha: Instant
)

// ─── Práctica ────────────────────────────────────────────────────────────────

data class RespuestaPractica(
    val preguntaServidaId: String,
    val orden: Int,
    val opcionElegidaId: String?,
    val texto: String?,
    val esCorrecta: Boolean,
    /** 0 a 100 */
    val puntaje: BigDecimal,
    val feedback: String?,
    val tiempoRespuestaMs: Int?,
    val fecha: Instant
)

data class SesionPractica(
    val id: UUID,
    val usuarioId: UUID,
    val skillId: UUID?,
    val cargoId: UUID?,
    val cargoObjetivo: String?,
    val modo: ModoPractica,
    val categoria: CategoriaHabilidad,
    val nivel: NivelExperiencia,
    val estado: EstadoPractica,
    /** 0 a 100, al finalizar */
    val puntaje: BigDecimal?,
    val preguntas: List<PreguntaServida>,
    val respuestas: List<RespuestaPractica>,
    val fechaInicio: Instant,
    val fechaFin: Instant?
) {
    val correctas: Int get() = respuestas.count { it.esCorrecta }
    fun respuestaDe(preguntaServidaId: String) = respuestas.firstOrNull { it.preguntaServidaId == preguntaServidaId }
}

data class NuevaSesionPractica(
    val usuarioId: UUID,
    val skillId: UUID?,
    val cargoId: UUID?,
    val cargoObjetivo: String?,
    val modo: ModoPractica,
    val categoria: CategoriaHabilidad,
    val nivel: NivelExperiencia,
    val preguntas: List<PreguntaServida>,
    val inicio: Instant
)

/** Un intento hecho sin conexión, ya corregido, listo para guardar de una vez. */
data class IntentoOffline(
    val idLocal: String,
    val sesion: NuevaSesionPractica,
    val respuestas: List<RespuestaPractica>,
    val fin: Instant
)

/** Resumen de cualquier prueba (entrevista, práctica o nivelación) para el historial. */
data class ResumenPrueba(
    val id: UUID,
    /** entrevista | practica | nivelacion */
    val tipo: String,
    val cargoObjetivo: String?,
    val nivel: NivelExperiencia?,
    val estado: String,
    val puntaje: Int?,
    val puntajeTotal: Int,
    val fechaInicio: Instant,
    val fechaFin: Instant?
)

/** Nivel acumulado del usuario en una skill. */
data class NivelSkillUsuario(
    val skillId: UUID,
    val nivel: NivelExperiencia?,
    val puntaje: BigDecimal,
    val evaluaciones: Int,
    val fecha: Instant
)

/** Una evaluación nueva de una skill (0 a 100) para acumular en nivel_skill_usuario. */
data class EvaluacionNivelSkill(
    val skillId: UUID,
    val puntaje: BigDecimal,
    /** null = no cambia el nivel registrado */
    val nivel: NivelExperiencia?
)
