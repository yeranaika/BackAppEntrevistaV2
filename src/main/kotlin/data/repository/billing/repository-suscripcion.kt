package data.repository.billing

import data.tables.billing.SuscripcionTable
import data.tables.billing.CodigoSuscripcionTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.time.Instant
import java.util.UUID

// ========================
// MODELOS DEL REPO
// ========================

data class SuscripcionStatus(
    val isPremium: Boolean,
    val plan: String?,
    val provider: String?,
    val status: String?,
    val startAt: Long?,
    val expiresAt: Long?
)

/**
 * Info del código generado (lado repositorio).
 * Luego en las rutas lo mapeamos a SubscriptionCodeRes para la API.
 */
data class SubscriptionCode(
    val code: String,
    val expiresAt: Long?,
    val maxUses: Int,
    val licenseType: String
)

class InvalidSubscriptionCodeException(message: String) : RuntimeException(message)

// ========================
// REPOSITORY
// ========================

/**
 * Repositorio para tabla SUSCRIPCION + CODIGO_SUSCRIPCION.
 */
class SuscripcionRepository {

    private suspend fun <T> tx(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction { block() }

    // =========================================================
    // Helpers internos (dentro de una misma Transaction)
    // =========================================================

    private fun Transaction.getCurrentStatusInternal(usuarioId: UUID): SuscripcionStatus {
        val row = SuscripcionTable
            .selectAll()
            .where { SuscripcionTable.usuarioId eq usuarioId }
            .orderBy(SuscripcionTable.fechaInicio to SortOrder.DESC)
            .limit(1)
            .singleOrNull()

        if (row == null) {
            return SuscripcionStatus(
                isPremium = false,
                plan = null,
                provider = null,
                status = null,
                startAt = null,
                expiresAt = null
            )
        }

        val plan = row[SuscripcionTable.plan]
        val proveedor = row[SuscripcionTable.proveedor]
        val estado = row[SuscripcionTable.estado]
        val inicioInstant: Instant = row[SuscripcionTable.fechaInicio]
        val expInstant: Instant? = row[SuscripcionTable.fechaExpiracion]

        val startMillis = inicioInstant.toEpochMilli()
        val expMillis = expInstant?.toEpochMilli()
        val nowMillis = System.currentTimeMillis()

        // premium si está activa, plan distinto de "free" y no vencida
        val isPremium =
            estado == "activa" &&
            plan.lowercase() != "free" &&
            (expMillis == null || expMillis > nowMillis)

        return SuscripcionStatus(
            isPremium = isPremium,
            plan = plan,
            provider = proveedor,
            status = estado,
            startAt = startMillis,
            expiresAt = expMillis
        )
    }

    private fun Transaction.upsertSubscriptionInternal(
        usuarioId: UUID,
        plan: String,
        proveedor: String?,
        estado: String,
        fechaExpiracionMillis: Long?,
        codigoId: UUID?
    ) {
        val fechaExp: Instant? = fechaExpiracionMillis?.let { Instant.ofEpochMilli(it) }
        val ahora: Instant = Instant.now()

        val existente = SuscripcionTable
            .selectAll()
            .where {
                (SuscripcionTable.usuarioId eq usuarioId) and
                (SuscripcionTable.plan eq plan)
            }
            .orderBy(SuscripcionTable.fechaInicio to SortOrder.DESC)
            .limit(1)
            .singleOrNull()

        if (existente == null) {
            SuscripcionTable.insert {
                it[SuscripcionTable.usuarioId] = usuarioId
                it[SuscripcionTable.plan] = plan
                it[SuscripcionTable.proveedor] = proveedor
                it[SuscripcionTable.estado] = estado
                it[SuscripcionTable.fechaInicio] = ahora
                it[SuscripcionTable.fechaRenovacion] = null
                it[SuscripcionTable.fechaExpiracion] = fechaExp
                it[SuscripcionTable.codigoId] = codigoId
            }
        } else {
            SuscripcionTable.update(
                where = {
                    (SuscripcionTable.usuarioId eq usuarioId) and
                    (SuscripcionTable.plan eq plan)
                }
            ) {
                it[SuscripcionTable.proveedor] = proveedor
                it[SuscripcionTable.estado] = estado
                it[SuscripcionTable.fechaRenovacion] = ahora
                it[SuscripcionTable.fechaExpiracion] = fechaExp
                it[SuscripcionTable.codigoId] = codigoId
            }
        }
    }

    // =========================================================
    // API pública usada por el resto del back
    // =========================================================

    suspend fun getCurrentStatus(usuarioId: UUID): SuscripcionStatus = tx {
        getCurrentStatusInternal(usuarioId)
    }

    /**
     * Usado por Google Play Billing.
     * proveedor normalmente será "google" y codigoId = null.
     */
    suspend fun upsertSubscription(
        usuarioId: UUID,
        plan: String,
        proveedor: String?,
        estado: String,
        fechaExpiracionMillis: Long?,
        codigoId: UUID? = null
    ) = tx {
        upsertSubscriptionInternal(
            usuarioId = usuarioId,
            plan = plan,
            proveedor = proveedor,
            estado = estado,
            fechaExpiracionMillis = fechaExpiracionMillis,
            codigoId = codigoId
        )
    }

    /**
     * Crea un nuevo código de suscripción.
     *
     * licenseType define el prefijo:
     *  - "PROM" / "PROMOCIONAL" -> PROM-XXXXXXXX
     *  - "INST" / "INSTITUCIONAL" -> INST-XXXXXXXX
     *  - "GOOG" / "GOOGLE" / "GOOGLE_PLAY" -> GOOG-XXXXXXXX
     *  - cualquier otro -> GEN-XXXXXXXX
     */
    suspend fun createCode(
        days: Int,
        label: String?,
        maxUses: Int,
        licenseType: String,
        explicitExpirationMillis: Long? = null
    ): SubscriptionCode = tx {
        val now = Instant.now()

        val expiresInstant: Instant? = explicitExpirationMillis?.let {
            Instant.ofEpochMilli(it)
        } ?: if (days > 0) {
            now.plus(Duration.ofDays(days.toLong()))
        } else {
            null
        }

        val normalizedLicense = normalizeLicenseType(licenseType)
        val code = generateCode(normalizedLicense)

        CodigoSuscripcionTable.insert {
            it[codigo] = code
            it[CodigoSuscripcionTable.label] = label
            it[duracionDias] = days
            it[CodigoSuscripcionTable.maxUsos] = maxUses
            it[usosRealizados] = 0
            it[fechaCreacion] = now
            it[fechaExpiracion] = expiresInstant
            it[activo] = true
        }

        SubscriptionCode(
            code = code,
            expiresAt = expiresInstant?.toEpochMilli(),
            maxUses = maxUses,
            licenseType = normalizedLicense
        )
    }

    /**
     * Canjea un código y extiende/crea la suscripción.
     */
    suspend fun redeemCode(
        usuarioId: UUID,
        rawCode: String,
        plan: String = "premium",
        proveedor: String = "codigo"
    ): SuscripcionStatus = tx {
        val now = Instant.now()
        val normalizedCode = rawCode.trim().uppercase()

        val row = CodigoSuscripcionTable
            .selectAll()
            .where {
                (CodigoSuscripcionTable.codigo eq normalizedCode) and
                (CodigoSuscripcionTable.activo eq true) and
                (CodigoSuscripcionTable.usosRealizados less CodigoSuscripcionTable.maxUsos) and
                (
                    CodigoSuscripcionTable.fechaExpiracion.isNull() or
                    (CodigoSuscripcionTable.fechaExpiracion greaterEq now)
                )
            }
            .limit(1)
            .singleOrNull()
            ?: throw InvalidSubscriptionCodeException("codigo_invalido_o_expirado")

        val duracion = row[CodigoSuscripcionTable.duracionDias]
        val codigoId = row[CodigoSuscripcionTable.id].value

        // Estado actual del usuario (dentro de la misma tx)
        val current = getCurrentStatusInternal(usuarioId)
        val nowMillis = now.toEpochMilli()
        val baseMillis = current.expiresAt?.takeIf { it > nowMillis } ?: nowMillis
        val newExpMillis =
            baseMillis + duracion.toLong() * 24L * 60L * 60L * 1000L

        // Actualizar/crear suscripción con proveedor "codigo"
        upsertSubscriptionInternal(
            usuarioId = usuarioId,
            plan = plan,
            proveedor = proveedor,
            estado = "activa",
            fechaExpiracionMillis = newExpMillis,
            codigoId = codigoId
        )

        // Marcar uso del código
        val nuevosUsos = row[CodigoSuscripcionTable.usosRealizados] + 1
        CodigoSuscripcionTable.update(
            where = { CodigoSuscripcionTable.id eq row[CodigoSuscripcionTable.id] }
        ) {
            it[usosRealizados] = nuevosUsos
            if (nuevosUsos >= row[CodigoSuscripcionTable.maxUsos]) {
                it[activo] = false
            }
        }

        // Devolvemos el nuevo estado de la suscripción
        getCurrentStatusInternal(usuarioId)
    }

    // =========================================================
    // Helpers para generación de código
    // =========================================================

    private fun normalizeLicenseType(raw: String): String =
        when (raw.trim().uppercase()) {
            "PROM", "PROMOCIONAL" -> "PROM"
            "INST", "INSTITUCIONAL", "INSTITUTO" -> "INST"
            "GOOG", "GOOGLE", "GOOGLE_PLAY" -> "GOOG"
            else -> "GEN"
        }

    private fun generateCode(normalizedLicense: String): String {
        val randomPart = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(8)
            .uppercase()

        return "$normalizedLicense-$randomPart"
    }
}
