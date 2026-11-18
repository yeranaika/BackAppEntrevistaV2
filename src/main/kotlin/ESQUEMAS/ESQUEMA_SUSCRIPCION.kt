package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// snake_case: contrato que ya usa la app Android (/billing/status y /billing/code/redeem).

@Serializable
data class SolicitudVerificarCompra(
    @SerialName("product_id") val productoId: String,
    @SerialName("purchase_token") val tokenCompra: String,
    @SerialName("purchase_time") val fechaCompra: Long
)

@Serializable
data class RespuestaEstadoSuscripcion(
    @SerialName("is_premium") val esPremium: Boolean,
    @SerialName("plan") val plan: String?,
    /** "google" | "interna" | null */
    @SerialName("source") val origen: String?,
    @SerialName("status") val estado: String?,
    /** Milisegundos desde epoch. */
    @SerialName("start_at") val inicio: Long?,
    @SerialName("expires_at") val expiracion: Long?,
    // Cupo del mes de reportes con IA (D29). Campos aditivos: los tres llegan juntos solo para un premium con IA activa
    // y límite > 0; si no, se omiten (con valor por defecto y sin encodeDefaults no viajan). Llevan valor por defecto a
    // propósito: la respuesta de un usuario gratis queda igual que antes.
    @SerialName("reportes_ia_limite") val limiteReportesIa: Int? = null,
    /** Entre 0 y reportes_ia_limite. */
    @SerialName("reportes_ia_restantes") val reportesIaRestantes: Int? = null,
    /** Milisegundos desde epoch: inicio del mes siguiente (00:00 UTC), cuando el cupo vuelve al límite. */
    @SerialName("reportes_ia_renovacion") val renovacionReportesIa: Long? = null
)

@Serializable
data class SolicitudCanjearCodigo(
    @SerialName("code") val codigo: String
)

@Serializable
data class SolicitudCrearCodigo(
    @SerialName("days") val dias: Int,
    @SerialName("label") val etiqueta: String? = null,
    @SerialName("max_uses") val maxUsos: Int = 1,
    /** PROM, INST o GOOG */
    @SerialName("license_type") val tipoLicencia: String,
    /** ISO-8601, ej: 2026-01-31T23:59:59Z */
    @SerialName("expires_at") val expiracion: String? = null
)

@Serializable
data class RespuestaCodigoCreado(
    @SerialName("code") val codigo: String,
    @SerialName("expires_at") val expiracion: String?,
    @SerialName("max_uses") val maxUsos: Int,
    @SerialName("license_type") val tipoLicencia: String
)

@Serializable
data class RespuestaCompraVerificada(
    val status: String
)
