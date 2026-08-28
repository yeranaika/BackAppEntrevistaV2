// dto/consent/CreateConsentReq.kt
package routes.consent

import kotlinx.serialization.Serializable

@Serializable
data class CreateConsentReq(
    val version: String,
    val alcances: Map<String, Boolean>
)
