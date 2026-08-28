// dto/consent/ConsentRes.kt
package routes.consent

import kotlinx.serialization.Serializable

@Serializable
data class ConsentRes(
    val id: String,
    val version: String,
    val alcances: Map<String, Boolean>,
    val fechaOtorgado: String,
    val fechaRevocado: String? = null
)
