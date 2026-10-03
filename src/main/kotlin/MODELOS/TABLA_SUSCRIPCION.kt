package MODELOS

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object TablaSuscripcion : UUIDTable("suscripcion", "suscripcion_id") {
    val usuarioId = uuid("usuario_id")
    /** "premium", o el id de producto de Google Play. */
    val plan = varchar("plan", length = 100)
    /** "google_play", "codigo"… */
    val proveedor = varchar("proveedor", length = 50).nullable()
    /** activa, inactiva, cancelada, suspendida, vencida */
    val estado = varchar("estado", length = 20)
    val fechaInicio = timestamp("fecha_inicio")
    val fechaRenovacion = timestamp("fecha_renovacion").nullable()
    val fechaExpiracion = timestamp("fecha_expiracion").nullable()
    /** Código con el que se activó o renovó, si vino de codigo_suscripcion. */
    val codigoId = uuid("codigo_id").nullable()
    /** SHA-256 del purchase token de Google Play: un token no puede activar dos cuentas (migrations/015). */
    val hashTokenCompra = text("token_compra_hash").nullable()
}

object TablaCodigoSuscripcion : UUIDTable("codigo_suscripcion", "codigo_id") {
    /** Lo que escribe el usuario, ej: "PROM-AB12CD34". */
    val codigo = varchar("codigo", length = 32).uniqueIndex()
    val etiqueta = varchar("label", length = 80).nullable()
    val duracionDias = integer("duracion_dias")
    val maxUsos = integer("max_usos").default(1)
    val usosRealizados = integer("usos_realizados").default(0)
    val fechaCreacion = timestamp("fecha_creacion")
    val fechaExpiracion = timestamp("fecha_expiracion").nullable()
    val activo = bool("activo").default(true)
}
