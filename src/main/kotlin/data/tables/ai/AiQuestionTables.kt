package data.tables.ai

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// ─── Tabla pregunta (mapeada desde la BD existente) ─────────────────────────

object PreguntaTable : Table("pregunta") {
    val preguntaId           = uuid("pregunta_id").clientDefault { java.util.UUID.randomUUID() }
    val skillId              = uuid("skill_id").nullable()
    val cargoId              = uuid("cargo_id").nullable()
    val tipoPregunta         = varchar("tipo_pregunta", 20)
    val categoriaHabilidad   = varchar("categoria_habilidad", 10)
    val nivelDificultad      = varchar("nivel_dificultad", 20)
    val enunciado            = text("enunciado")
    val respuestaIdeal       = text("respuesta_ideal").nullable()
    val rubricaEvaluacion    = jsonb<JsonObject>("rubrica_evaluacion", Json, JsonObject.serializer()).nullable()
    val contextoEvaluacionIa = jsonb<JsonObject>("contexto_evaluacion_ia", Json, JsonObject.serializer()).nullable()
    val generadaPorIa        = bool("generada_por_ia").default(false)
    val estado               = varchar("estado", 20).default("pendiente")
    val motivoRechazo        = text("motivo_rechazo").nullable()
    val vecesUsada           = integer("veces_usada").default(0)
    val fechaCreacion        = timestampWithTimeZone("fecha_creacion")

    override val primaryKey = PrimaryKey(preguntaId)
}

object OpcionPreguntaTable : Table("opcion_pregunta") {
    val opcionId = uuid("opcion_id").clientDefault { java.util.UUID.randomUUID() }
    val preguntaId = uuid("pregunta_id")
    val textoOpcion = text("texto_opcion")
    val esCorrecta = bool("es_correcta").default(false)
    val explicacion = text("explicacion").nullable()
    val orden = short("orden").default(1)

    override val primaryKey = PrimaryKey(opcionId)
}

// ─── Tabla pregunta_generacion_ia (trazabilidad) ─────────────────────────────

object PreguntaGeneracionIaTable : Table("pregunta_generacion_ia") {
    val generacionId     = uuid("generacion_id").clientDefault { java.util.UUID.randomUUID() }
    val preguntaId       = uuid("pregunta_id").nullable()
    val cargoId          = uuid("cargo_id").nullable()
    val skillId          = uuid("skill_id").nullable()
    val nivelSolicitado  = varchar("nivel_solicitado", 20).nullable()
    val modeloLlm        = varchar("modelo_llm", 60)
    val promptEnviado    = text("prompt_enviado")
    val respuestaRaw     = text("respuesta_raw").nullable()
    val parseExitoso     = bool("parse_exitoso").default(false)
    val tokensInput      = integer("tokens_input").nullable()
    val tokensOutput     = integer("tokens_output").nullable()
    val costoUsd         = decimal("costo_usd", 8, 6).nullable()
    val estadoRevision   = varchar("estado_revision", 25).default("pendiente_revision")
    val revisadoPor      = uuid("revisado_por").nullable()
    val fechaGeneracion  = timestampWithTimeZone("fecha_generacion")

    override val primaryKey = PrimaryKey(generacionId)
}
