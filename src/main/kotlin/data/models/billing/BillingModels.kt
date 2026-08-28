package data.models.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPurchaseReq(
    @SerialName("product_id")
    val productId: String,
    @SerialName("purchase_token")
    val purchaseToken: String,
    @SerialName("purchase_time")
    val purchaseTime: Long
)

/**
 * Respuesta que usa la app Android para mostrar la suscripción.
 */
@Serializable
data class BillingStatusRes(
    @SerialName("is_premium")
    val isPremium: Boolean,
    @SerialName("plan")
    val plan: String?,
    @SerialName("source")
    val source: String?,    // "google" | "interna" | null
    @SerialName("status")
    val status: String?,    // "activa", "inactiva", etc.
    @SerialName("start_at")
    val startAt: Long?,     // millis
    @SerialName("expires_at")
    val expiresAt: Long?    // millis
)

/**
 * Request de la app para canjear un código.
 */
@Serializable
data class RedeemCodeReq(
    @SerialName("code")
    val code: String
)

/**
 * Request para crear códigos desde un endpoint admin.
 */
@Serializable
data class CreateSubscriptionCodeReq(
    @SerialName("days")
    val days: Int,
    @SerialName("label")
    val label: String? = null,
    @SerialName("max_uses")
    val maxUses: Int = 1,
    @SerialName("license_type")
    val licenseType: String,              // 👈 OBLIGATORIO
    @SerialName("expires_at")
    val expiresAt: String? = null
)

/**
 * Respuesta cuando se crea un código (para verlo/copiarlo).
 */
@Serializable
data class SubscriptionCodeRes(
    @SerialName("code")
    val code: String,
    @SerialName("expires_at")
    val expiresAt: String?,
    @SerialName("max_uses")
    val maxUses: Int,
    @SerialName("license_type")
    val licenseType: String
)