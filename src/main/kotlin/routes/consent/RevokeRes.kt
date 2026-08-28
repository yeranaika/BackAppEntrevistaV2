// dto/consent/RevokeRes.kt
package routes.consent



import kotlinx.serialization.Serializable
@Serializable
data class RevokeRes(val revoked: Boolean)
