package MODELOS

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

/** Simulación de entrevista de un usuario (src/DB: sesion_entrevista). */
object TablaSesionEntrevista : Table("sesion_entrevista") {
    val sesionId = uuid("sesion_id")
    val usuarioId = uuid("usuario_id")
    val cargoId = uuid("cargo_id").nullable()
    val cargoObjetivo = varchar("cargo_objetivo", 120)
    val nivelDificultad = varchar("nivel_dificultad", 20)
    val estado = varchar("estado", 15).default(EstadoSesionEntrevista.EN_PROGRESO.valorBd)
    val fechaInicio = timestamp("fecha_inicio")
    val fechaFin = timestamp("fecha_fin").nullable()

    override val primaryKey = PrimaryKey(sesionId)
}

/**
 * Una pregunta de la sesión con su respuesta. Guarda un snapshot de la pregunta: editar o borrar
 * el banco no cambia una sesión ya rendida.
 */
object TablaSesionPreguntaRespuesta : Table("sesion_pregunta_respuesta") {
    val respuestaId = uuid("respuesta_id")
    val sesionId = uuid("sesion_id")
    val preguntaId = uuid("pregunta_id").nullable()
    val enunciado = text("enunciado_pregunta")
    val respuestaIdeal = text("respuesta_ideal_snap").nullable()
    val tipoPregunta = varchar("tipo_pregunta", 20).nullable()
    val categoriaHabilidad = varchar("categoria_habilidad", 10).nullable()
    val skillId = uuid("skill_id").nullable()
    val opciones = jsonb("opciones_snap", Json, ListSerializer(OpcionSnapshot.serializer())).nullable()
    /** Respuesta abierta: texto escrito o transcripción del audio (STT). */
    val transcripcion = text("transcripcion_audio").nullable()
    val videoClipUrl = varchar("video_clip_url", 500).nullable()
    val opcionElegidaId = uuid("opcion_elegida_id").nullable()
    val feedbackTecnico = jsonb("feedback_ia_tecnico", Json, JsonObject.serializer()).nullable()
    val feedbackBlando = jsonb("feedback_ia_blando", Json, JsonObject.serializer()).nullable()
    val puntaje = decimal("puntaje_respuesta", 5, 2).nullable()
    val orden = short("orden")
    val fechaRespuesta = timestamp("fecha_respuesta").nullable()

    override val primaryKey = PrimaryKey(respuestaId)
}

/** Métricas de visión por computador. Se insertan siempre en lote. */
object TablaMetricaVideo : Table("metrica_video") {
    val metricaId = uuid("metrica_id")
    val sesionId = uuid("sesion_id")
    val timestampMs = long("timestamp_ms")
    val contactoVisual = decimal("contacto_visual", 5, 2).nullable()
    val postura = decimal("postura_score", 5, 2).nullable()
    val confianza = decimal("confianza_score", 5, 2).nullable()
    val gestos = jsonb("gestos_detectados", Json, JsonObject.serializer()).nullable()
    val expresionDominante = varchar("expresion_dominante", 20).nullable()

    override val primaryKey = PrimaryKey(metricaId)
}
