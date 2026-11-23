package MODELOS

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

/** Llamadas en vivo terminadas (migrations/027: llamada). */
object TablaLlamada : Table("llamada") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id")
    /** Id que le puso la app al guardarla en el teléfono: junto con el usuario hace idempotente el reenvío. */
    val idLocal = varchar("id_local", 64)
    val sesionId = uuid("sesion_id").nullable()
    val fechaInicio = timestamp("fecha_inicio")
    val duracionSeg = integer("duracion_seg")
    val preguntasCnt = integer("preguntas_cnt")
    val resumen = jsonb("resumen", Json, ResumenLlamada.serializer())
    val recibidaEn = timestamp("recibida_en")

    override val primaryKey = PrimaryKey(id)
}
