package MODELOS

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

private val listaDeTextos = ListSerializer(String.serializer())

/** Reporte de feedback de una entrevista (se genera en segundo plano al finalizarla). */
object TablaReporteEntrevista : Table("reporte_entrevista") {
    val reporteId = uuid("reporte_id")
    val sesionId = uuid("sesion_id").uniqueIndex("reporte_entrevista_sesion_id_key")
    val puntajeGlobal = decimal("puntaje_global", 5, 2).default(java.math.BigDecimal.ZERO)
    val puntajeTecnico = decimal("puntaje_tecnico", 5, 2).default(java.math.BigDecimal.ZERO)
    val puntajeBlando = decimal("puntaje_blando", 5, 2).default(java.math.BigDecimal.ZERO)
    val puntajeLenguajeCorporal = decimal("puntaje_lenguaje_corporal", 5, 2).default(java.math.BigDecimal.ZERO)
    val fortalezas = jsonb("fortalezas", Json, listaDeTextos).default(emptyList())
    val areasMejora = jsonb("areas_mejora", Json, listaDeTextos).default(emptyList())
    val recomendaciones = jsonb("recomendaciones_skills", Json, ListSerializer(RecomendacionSkill.serializer())).default(emptyList())
    val resumen = text("resumen_ia").nullable()
    val estado = varchar("estado_generacion", 15).default(EstadoReporte.GENERANDO.valorBd)
    val errorDetalle = text("error_detalle").nullable()
    val modoEvaluacion = varchar("modo_evaluacion", 10).default(ModoEvaluacion.FREEMIUM.valorBd)
    val modeloLlm = varchar("modelo_llm", 60).nullable()
    val tokensEntrada = integer("tokens_entrada").nullable()
    val tokensSalida = integer("tokens_salida").nullable()
    val costoUsd = decimal("costo_usd", 8, 6).nullable()
    val intentos = short("intentos_generacion").default(0)
    val fechaGeneracion = timestamp("fecha_generacion")

    override val primaryKey = PrimaryKey(reporteId)
}

/** Desglose del reporte por skill (gráfico de radar). */
object TablaReporteSkillDetalle : Table("reporte_skill_detalle") {
    val detalleId = uuid("detalle_id")
    val reporteId = uuid("reporte_id")
    val skillId = uuid("skill_id").nullable()
    val nombreSkill = varchar("nombre_skill", 100)
    val categoria = varchar("categoria", 10)
    val puntaje = decimal("puntaje", 5, 2)
    val nivelEvaluado = varchar("nivel_evaluado", 20).nullable()
    val observacion = text("observacion").nullable()
    val preguntasRespondidas = integer("preguntas_respondidas").default(0)

    override val primaryKey = PrimaryKey(detalleId)
}
