package security.billing

import com.google.auth.oauth2.GoogleCredentials
import data.models.billing.VerifyPurchaseReq
import data.repository.billing.SuscripcionRepository
import data.repository.usuarios.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID

data class GooglePlayVerificationResult(
    val isValid: Boolean,
    val isActive: Boolean,
    val expiryTimeMillis: Long?
)

data class BillingResult(
    val success: Boolean,
    val errorMessage: String? = null
)

class GooglePlayBillingService(
    private val httpClient: HttpClient,
    private val userRepo: UserRepository,
    private val suscripcionRepo: SuscripcionRepository,
    private val packageName: String,
    serviceAccountJsonBase64: String,
    private val useMock: Boolean
) {

    companion object {
        private const val ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
    }

    private val credentialsMutex = Mutex()

    private val credentials: GoogleCredentials? =
        if (useMock) {
            null
        } else {
            val decoded = Base64.getDecoder().decode(serviceAccountJsonBase64.trim())
            GoogleCredentials.fromStream(ByteArrayInputStream(decoded))
                .createScoped(ANDROID_PUBLISHER_SCOPE)
        }

    constructor(
        userRepo: UserRepository,
        suscripcionRepo: SuscripcionRepository,
        packageName: String,
        serviceAccountJsonBase64: String,
        useMock: Boolean
    ) : this(
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        },
        userRepo,
        suscripcionRepo,
        packageName,
        serviceAccountJsonBase64,
        useMock
    )

    suspend fun verifyAndStore(req: VerifyPurchaseReq, usuarioId: UUID): BillingResult {
        val googleRes = if (useMock) {
            mockVerification()
        } else {
            verifyWithGoogle(req.productId, req.purchaseToken)
        }

        if (!googleRes.isValid || !googleRes.isActive) {
            return BillingResult(success = false, errorMessage = "Compra no válida o no activa")
        }

        suscripcionRepo.upsertSubscription(
            usuarioId = usuarioId,
            plan = req.productId,
            proveedor = "google_play",
            estado = "activa",
            fechaExpiracionMillis = googleRes.expiryTimeMillis
        )

        return BillingResult(success = true)
    }

    private fun mockVerification(): GooglePlayVerificationResult =
        GooglePlayVerificationResult(
            isValid = true,
            isActive = true,
            expiryTimeMillis = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )

    private suspend fun verifyWithGoogle(
        productId: String,
        purchaseToken: String
    ): GooglePlayVerificationResult {
        val url =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$packageName/purchases/subscriptions/$productId/tokens/$purchaseToken"

        val response = try {
            httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer ${fetchAccessToken()}")
            }.body<GoogleSubscriptionResponse>()
        } catch (_: ClientRequestException) {
            return GooglePlayVerificationResult(isValid = false, isActive = false, expiryTimeMillis = null)
        } catch (_: ServerResponseException) {
            return GooglePlayVerificationResult(isValid = false, isActive = false, expiryTimeMillis = null)
        } catch (_: Exception) {
            return GooglePlayVerificationResult(isValid = false, isActive = false, expiryTimeMillis = null)
        }

        val expiryMillis = response.expiryTimeMillis?.toLongOrNull()
        val acknowledged = response.acknowledgementState == 1
        val paymentOk = response.paymentState == null || response.paymentState == 1 || response.paymentState == 2
        val notCanceled = response.cancelReason == null || response.cancelReason == 0
        val now = System.currentTimeMillis()

        val isActive = paymentOk && acknowledged && notCanceled && expiryMillis?.let { it > now } == true

        return GooglePlayVerificationResult(
            isValid = true,
            isActive = isActive,
            expiryTimeMillis = expiryMillis
        )
    }

    private suspend fun fetchAccessToken(): String = credentialsMutex.withLock {
        val creds = credentials ?: error("Credenciales de Google Play no configuradas")
        creds.refreshIfExpired()
        return creds.accessToken?.tokenValue
            ?: throw IllegalStateException("No se pudo obtener token de acceso de Google Play")
    }
}

@Serializable
private data class GoogleSubscriptionResponse(
    @SerialName("expiryTimeMillis")
    val expiryTimeMillis: String? = null,
    @SerialName("paymentState")
    val paymentState: Int? = null,
    @SerialName("acknowledgementState")
    val acknowledgementState: Int? = null,
    @SerialName("cancelReason")
    val cancelReason: Int? = null
)
