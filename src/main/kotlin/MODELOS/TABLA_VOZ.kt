package MODELOS

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

/** Transcripciones por pregunta de la llamada (migrations/030: transcripcion_respuesta). */
object TablaTranscripcionRespuesta : Table("transcripcion_respuesta") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id")
    val sesionId = uuid("sesion_id")
    val preguntaId = uuid("pregunta_id")
    val inicioMs = long("inicio_ms")
    val finMs = long("fin_ms")
    val texto = text("texto")
    val idioma = varchar("idioma", 10)
    val confianza = float("confianza").nullable()
    val parcial = bool("parcial")
    val recibidaEn = timestamp("recibida_en")

    override val primaryKey = PrimaryKey(id)
}

/** Paquetes de terminología por área (migrations/030: diccionario_terminologia). */
object TablaDiccionarioTerminologia : Table("diccionario_terminologia") {
    val area = varchar("area", 40)
    val nombre = varchar("nombre", 80)
    val version = integer("version")
    val activo = bool("activo")
    val terminos = jsonb("terminos", Json, ListSerializer(String.serializer()))
    val correcciones = jsonb("correcciones", Json, ListSerializer(CorreccionTerminologia.serializer()))
    val actualizadoEn = timestamp("actualizado_en")

    override val primaryKey = PrimaryKey(area)
}
