package SERVICIOS

import CONFIGURACION.ResultadoPrediccionDivlara
import ESQUEMAS.SolicitudPrediccionVision

/**
 * Lo que el controlador de visión necesita: convertir una solicitud (features o ventana de landmarks) en las
 * predicciones del modelo. El backend lo implementa con un cliente HTTP al motor; el motor, con el modelo cargado.
 */
fun interface PredictorVision {
    suspend fun predecir(solicitud: SolicitudPrediccionVision): ResultadoPrediccionDivlara
}
