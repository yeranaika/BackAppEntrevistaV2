// src/main/kotlin/data/tables/billing/table-codigo-suscripcion.kt
package data.tables.billing

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Tabla CODIGO_SUSCRIPCION
 *
 * CREATE TABLE codigo_suscripcion (
 *   codigo_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *   codigo           VARCHAR(32)  NOT NULL UNIQUE,
 *   label            VARCHAR(80),
 *   duracion_dias    INTEGER      NOT NULL,
 *   max_usos         INTEGER      NOT NULL DEFAULT 1,
 *   usos_realizados  INTEGER      NOT NULL DEFAULT 0,
 *   fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
 *   fecha_expiracion TIMESTAMPTZ,
 *   activo           BOOLEAN      NOT NULL DEFAULT TRUE
 * );
 */
object CodigoSuscripcionTable : UUIDTable(
    name = "codigo_suscripcion",
    columnName = "codigo_id"
) {
    // Código que verá el usuario, ej: "PREM-ABC123XYZ"
    val codigo = varchar("codigo", length = 32).uniqueIndex()

    // Descripción opcional (campaña, demo, etc.)
    val label = varchar("label", length = 80).nullable()

    // Cuántos días suma a la suscripción
    val duracionDias = integer("duracion_dias")

    // Cuántas veces puede usarse este código
    val maxUsos = integer("max_usos").default(1)

    // Cuántas veces se ha usado
    val usosRealizados = integer("usos_realizados").default(0)

    // Fechas en TIMESTAMPTZ -> java.time.Instant
    val fechaCreacion = timestamp("fecha_creacion")
    val fechaExpiracion = timestamp("fecha_expiracion").nullable()

    // Si el código está activo o no
    val activo = bool("activo").default(true)
}
