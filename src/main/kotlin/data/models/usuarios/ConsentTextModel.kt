// src/main/kotlin/data/models/usuarios/ConsentTextModel.kt
package data.models.usuarios

import kotlinx.serialization.Serializable

@Serializable
data class ConsentTextRes(
    val version: String,
    val title: String,
    val body: String
)

@Serializable
data class CreateConsentTextReq(
    val version: String,
    val title: String,
    val body: String
)

@Serializable
data class CreateConsentReq(
    val version: String,
    val alcances: Map<String, Boolean>
)

@Serializable
data class RevokeRes(
    val revoked: Boolean
)
