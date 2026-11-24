package MODELOS

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Respuestas de texto libre revisadas sin conexión (migrations/026: revision_texto_local). Solo texto, nunca audio ni video. */
object TablaRevisionTexto : Table("revision_texto_local") {
    val id = uuid("id")
    val usuarioId = uuid("usuario_id")
    /** Id que le puso la app al guardarla en el teléfono: junto con el usuario hace idempotente el reenvío. */
    val idLocal = varchar("id_local", 64)
    val preguntaId = uuid("pregunta_id").nullable()
    val texto = text("texto")
    val scoreDispositivo = decimal("score_dispositivo", 5, 2).nullable()
    val bandaDispositivo = varchar("banda_dispositivo", 10).nullable()
    val scoreServidor = decimal("score_servidor", 5, 2)
    val bandaServidor = varchar("banda_servidor", 10)
    val versionRevisor = short("version_revisor").default(1)
    val revisadoEn = timestampWithTimeZone("revisado_en").nullable()
    val recibidoEn = timestampWithTimeZone("recibido_en")

    override val primaryKey = PrimaryKey(id)
}
