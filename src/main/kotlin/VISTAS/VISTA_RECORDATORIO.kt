package VISTAS

import ESQUEMAS.RespuestaRecordatorio
import MODELOS.Recordatorio

fun Recordatorio.aRespuesta() = RespuestaRecordatorio(diasSemana, hora, tipoPractica, estaHabilitado)
