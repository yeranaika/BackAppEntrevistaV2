package VISTAS

import ESQUEMAS.RespuestaDetalleLlamada
import ESQUEMAS.RespuestaHistorialLlamadas
import ESQUEMAS.RespuestaResumenLlamada
import MODELOS.Llamada
import MODELOS.PaginaLlamadas

fun Llamada.aResumen() = RespuestaResumenLlamada(
    id = id.toString(), idLocal = idLocal, fechaInicioMs = fechaInicio.toEpochMilli(),
    duracionSeg = duracionSeg, preguntasCnt = preguntasCnt, accionesCnt = resumen.totalOcurrencias
)

fun Llamada.aDetalle() = RespuestaDetalleLlamada(
    id = id.toString(), idLocal = idLocal, sesionId = sesionId?.toString(), fechaInicioMs = fechaInicio.toEpochMilli(),
    duracionSeg = duracionSeg, preguntasCnt = preguntasCnt, accionesCnt = resumen.totalOcurrencias,
    acciones = resumen.acciones, linea = resumen.linea
)

fun PaginaLlamadas.aRespuesta() = RespuestaHistorialLlamadas(elementos.map { it.aResumen() }, total, pagina, tamano)
