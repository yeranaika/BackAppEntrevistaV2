package ESQUEMAS

import kotlinx.serialization.Serializable

/** Estado del modelo de visión DivLara expuesto en `/health`. Sin defaults en campos obligatorios (`encodeDefaults = false`). */
@Serializable
data class EstadoModeloDivlaraSalud(
    val version: String,
    val cargado: Boolean,
    val error: String? = null
)
