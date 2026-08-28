// src/main/kotlin/data/tables/billing/table-suscripcion.kt
package data.tables.billing

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import data.tables.usuarios.UsuarioTable

/**
 * Tabla SUSCRIPCION
 *
 * CREATE TABLE suscripcion (
 *   suscripcion_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *   usuario_id       UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
 *   plan             VARCHAR(100) NOT NULL DEFAULT 'free',
 *   proveedor        VARCHAR(50),
 *   estado           VARCHAR(20) NOT NULL DEFAULT 'inactiva',
 *   fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
 *   fecha_renovacion TIMESTAMPTZ,
 *   fecha_expiracion TIMESTAMPTZ,
 *   codigo_id        UUID NULL REFERENCES codigo_suscripcion(codigo_id)
 * );
 */
object SuscripcionTable : UUIDTable("suscripcion", "suscripcion_id") {

    // FK al usuario
    val usuarioId = reference("usuario_id", UsuarioTable.usuarioId)

    // Plan del usuario: "free", "premium_mensual", etc.
    val plan = varchar("plan", length = 100)

    // Proveedor de la suscripción: "google", "codigo", etc. (puede ser null)
    val proveedor = varchar("proveedor", length = 50).nullable()

    // Estados esperados: "activa", "inactiva", "cancelada", "suspendida", "vencida"
    val estado = varchar("estado", length = 20)

    // Fechas en TIMESTAMPTZ -> mapeadas a java.time.Instant
    val fechaInicio = timestamp("fecha_inicio")
    val fechaRenovacion = timestamp("fecha_renovacion").nullable()
    val fechaExpiracion = timestamp("fecha_expiracion").nullable()

    // Nuevo: código con el que se activó/renovó (si viene de codigo_suscripcion)
    val codigoId = uuid("codigo_id").nullable()
}
