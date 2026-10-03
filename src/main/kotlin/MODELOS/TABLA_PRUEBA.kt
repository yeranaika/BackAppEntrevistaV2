package MODELOS

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

private val listaDePreguntasServidas = ListSerializer(PreguntaServida.serializer())

// ─── Flujo 1: nivelación ────────────────────────────────────────────────────

/** Test de nivelación armado por un admin (lista de preguntas fija). */
object TablaTestNivelacion : Table("test_nivelacion") {
    val testId = uuid("test_id")
    val titulo = varchar("titulo", 150)
    val cargoId = uuid("cargo_id").nullable()
    val area = varchar("area", 50)
    val nivelObjetivo = varchar("nivel_objetivo", 20).default("mixto")
    val descripcion = text("descripcion").nullable()
    val preguntasIds = jsonb("preguntas_ids", Json, ListSerializer(String.serializer())).default(emptyList())
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(testId)
}

/** Un intento de nivelación: las preguntas servidas y, al terminar, las respuestas corregidas. */
object TablaIntentoTest : Table("intento_test") {
    val intentoId = uuid("intento_id")
    val usuarioId = uuid("usuario_id")
    val testId = uuid("test_id").nullable()
    val tipoTest = varchar("tipo_test", 25)
    val cargoId = uuid("cargo_id").nullable()
    val cargoObjetivo = varchar("cargo_objetivo", 120).nullable()
    val puntajeObtenido = decimal("puntaje_obtenido", 5, 2).nullable()
    val nivelAsignado = varchar("nivel_asignado", 20).nullable()
    val detalle = jsonb("respuestas_detalle", Json, ListSerializer(DetalleIntento.serializer())).default(emptyList())
    val fechaInicio = timestamp("fecha_inicio")
    val fechaFin = timestamp("fecha_fin").nullable()

    override val primaryKey = PrimaryKey(intentoId)
}

/** Brecha por skill contra lo que pide el cargo (gap analysis). */
object TablaResultadoNivelacion : Table("resultado_nivelacion") {
    val resultadoId = uuid("resultado_id")
    val intentoId = uuid("intento_id")
    val usuarioId = uuid("usuario_id")
    val cargoId = uuid("cargo_id").nullable()
    val nivelGlobal = varchar("nivel_global_asignado", 20)
    val puntajeGlobal = decimal("puntaje_global", 5, 2)
    val skillsGap = jsonb("skills_gap", Json, ListSerializer(EvaluacionSkill.serializer())).default(emptyList())
    val skillsOk = jsonb("skills_ok", Json, ListSerializer(EvaluacionSkill.serializer())).default(emptyList())
    val resumen = text("resumen_ia").nullable()
    val fechaGeneracion = timestamp("fecha_generacion")

    override val primaryKey = PrimaryKey(resultadoId)
}

// ─── Flujo 2: práctica ───────────────────────────────────────────────────────

object TablaSesionPractica : Table("sesion_practica") {
    val sesionId = uuid("sesion_practica_id")
    val usuarioId = uuid("usuario_id")
    val skillId = uuid("skill_id").nullable()
    val cargoId = uuid("cargo_id").nullable()
    val cargoObjetivo = varchar("cargo_objetivo", 120).nullable()
    val modo = varchar("modo", 20)
    val categoria = varchar("categoria", 10)
    val nivelPreguntas = varchar("nivel_preguntas", 20)
    val estado = varchar("estado", 15).default(EstadoPractica.EN_PROGRESO.valorBd)
    val puntajeSesion = decimal("puntaje_sesion", 5, 2).nullable()
    val totalPreguntas = short("total_preguntas").default(0)
    val correctas = short("correctas").default(0)
    val preguntas = jsonb("preguntas_snap", Json, listaDePreguntasServidas).default(emptyList())
    val idLocal = varchar("id_local", 64).nullable()
    val fechaInicio = timestamp("fecha_inicio")
    val fechaFin = timestamp("fecha_fin").nullable()

    override val primaryKey = PrimaryKey(sesionId)
}

object TablaRespuestaPractica : Table("respuesta_practica") {
    val respuestaId = uuid("respuesta_id")
    val sesionId = uuid("sesion_practica_id")
    val preguntaId = uuid("pregunta_id").nullable()
    val enunciado = text("enunciado_snap")
    val opcionElegidaId = uuid("opcion_elegida_id").nullable()
    val respuestaTexto = text("respuesta_texto").nullable()
    val esCorrecta = bool("es_correcta").nullable()
    /** 0 a 10 (así lo define el esquema). */
    val puntaje = decimal("puntaje", 4, 2).nullable()
    val feedbackTexto = text("feedback_texto").nullable()
    val feedbackIa = jsonb("feedback_ia", Json, JsonObject.serializer()).nullable()
    val tiempoRespuestaMs = integer("tiempo_respuesta_ms").nullable()
    val orden = short("orden")
    val fechaRespuesta = timestamp("fecha_respuesta")

    override val primaryKey = PrimaryKey(respuestaId)
}

/** Nivel y puntaje acumulado del usuario en cada skill (lo alimentan nivelación y práctica). */
object TablaNivelSkillUsuario : Table("nivel_skill_usuario") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id")
    val skillId = uuid("skill_id")
    val nivelEvaluado = varchar("nivel_evaluado", 20).nullable()
    val puntaje = decimal("puntaje", 5, 2).default(java.math.BigDecimal.ZERO)
    val numEvaluaciones = integer("num_evaluaciones").default(0)
    val fechaEvaluacion = timestamp("fecha_evaluacion")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("nivel_skill_usuario_usuario_id_skill_id_key", usuarioId, skillId)
    }
}
