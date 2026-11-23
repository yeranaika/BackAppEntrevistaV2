package ESQUEMAS

import kotlinx.serialization.Serializable

/** Respuesta de liveness de `GET /health`. */
@Serializable
data class RespuestaSalud(
    val status: String,
    val modeloDivlara: EstadoModeloDivlaraSalud
)
