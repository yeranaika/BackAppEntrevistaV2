package routes.billing

import data.models.billing.BillingStatusRes
import data.models.billing.VerifyPurchaseReq
import data.models.billing.RedeemCodeReq
import data.models.billing.CreateSubscriptionCodeReq
import data.models.billing.SubscriptionCodeRes
import data.repository.billing.InvalidSubscriptionCodeException
import data.repository.billing.SuscripcionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.billing.GooglePlayBillingService
import security.isAdmin
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

fun Route.billingRoutes(
    billingService: GooglePlayBillingService,
    suscripcionRepo: SuscripcionRepository
) {

    authenticate("auth-jwt") {

        route("/billing") {

            // --------------------------
            // POST /billing/google/verify
            // --------------------------
            post("/google/verify") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val usuarioIdStr = principal.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (_: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "userId inválido en token"
                    )
                }

                val req = call.receive<VerifyPurchaseReq>()
                val result = billingService.verifyAndStore(req, usuarioId)

                if (result.success) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("status" to "premium_active")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (result.errorMessage ?: "billing_failed"))
                    )
                }
            }

            // --------------------------
            // GET /billing/status
            // --------------------------
            get("/status") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val usuarioIdStr = principal.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (_: IllegalArgumentException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "userId inválido en token"
                    )
                }

                val status = suscripcionRepo.getCurrentStatus(usuarioId)

                val source = when (status.provider?.lowercase()) {
                    "google", "google_play" -> "google"
                    null -> null
                    else -> "interna"
                }

                call.respond(
                    BillingStatusRes(
                        isPremium = status.isPremium,
                        plan = status.plan,
                        source = source,
                        status = status.status,
                        startAt = status.startAt,
                        expiresAt = status.expiresAt
                    )
                )
            }

            // --------------------------
            // POST /billing/code/redeem
            // (usuario normal)
            // --------------------------
            post("/code/redeem") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val usuarioIdStr = principal.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (_: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "userId inválido en token"
                    )
                }

                val req = call.receive<RedeemCodeReq>()

                val status = try {
                    suscripcionRepo.redeemCode(usuarioId, req.code)
                } catch (e: InvalidSubscriptionCodeException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "codigo_invalido_o_expirado")
                    )
                }

                val source = when (status.provider?.lowercase()) {
                    "google", "google_play" -> "google"
                    null -> null
                    else -> "interna"
                }

                call.respond(
                    BillingStatusRes(
                        isPremium = status.isPremium,
                        plan = status.plan,
                        source = source,
                        status = status.status,
                        startAt = status.startAt,
                        expiresAt = status.expiresAt
                    )
                )
            }

            // --------------------------
            // POST /billing/admin/codes
            // (SOLO ADMIN)
            // --------------------------
            post("/admin/codes") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                // ✅ mismo patrón que en AdminUserCreateRoutes
                if (!principal.isAdmin()) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")
                }

                // Leer body de forma segura
                val req = runCatching { call.receive<CreateSubscriptionCodeReq>() }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "JSON inválido"
                        )
                    }

                if (req.days <= 0 && req.expiresAt == null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "debes definir days > 0 o expires_at"
                    )
                }

                // normalizamos y validamos el tipo de licencia
                val licenseTypeUpper = req.licenseType.uppercase()
                val licenseType = when (licenseTypeUpper) {
                    "PROM", "INST", "GOOG" -> licenseTypeUpper
                    else -> {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "licenseType debe ser PROM, INST o GOOG"
                        )
                    }
                }

                val explicitExpMillis: Long? = req.expiresAt?.let { raw ->
                    try {
                        Instant.parse(raw).toEpochMilli()
                    } catch (_: DateTimeParseException) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "expires_at debe venir en formato ISO-8601 (ej: 2026-01-31T23:59:59Z)"
                        )
                    }
                }

                val code = suscripcionRepo.createCode(
                    days = req.days,
                    label = req.label,
                    maxUses = req.maxUses,
                    licenseType = licenseType,
                    explicitExpirationMillis = explicitExpMillis
                )

                val res = SubscriptionCodeRes(
                    code = code.code,
                    expiresAt = code.expiresAt?.let { Instant.ofEpochMilli(it).toString() },
                    maxUses = code.maxUses,
                    licenseType = code.licenseType
                )

                call.respond(HttpStatusCode.Created, res)
            }
        }
    }
}
