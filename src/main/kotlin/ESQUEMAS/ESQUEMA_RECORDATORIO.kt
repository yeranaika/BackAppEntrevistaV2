package ESQUEMAS

import kotlinx.serialization.Serializable

/** Mismo JSON que usa la app Android (ReminderDtos). Días: LUN, MAR, MIE, JUE, VIE, SAB, DOM. */
@Serializable
data class SolicitudRecordatorio(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val habilitado: Boolean = true
)

@Serializable
data class RespuestaRecordatorio(
    val diasSemana: List<String>,
    val hora: String,
    val tipoPractica: String,
    val habilitado: Boolean
)
