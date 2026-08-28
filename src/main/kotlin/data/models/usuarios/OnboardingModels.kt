package data.models.usuarios

import kotlinx.serialization.Serializable

@Serializable
data class UpdateObjetivoReq(
    val area: String,
    val metaCargo: String,
    val nivel: String
)
