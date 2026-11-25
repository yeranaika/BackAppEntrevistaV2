package VISTAS

import CONFIGURACION.ResultadoPrediccionDivlara
import ESQUEMAS.RespuestaPrediccionVision

fun ResultadoPrediccionDivlara.aRespuesta() = RespuestaPrediccionVision(
    versionModelo = versionModelo,
    predicciones = predicciones,
    escala = escala,
    manoCaraUna = manoCaraUna,
    manoCaraDos = manoCaraDos,
    manoCaraConteo = manoCaraConteo
)
