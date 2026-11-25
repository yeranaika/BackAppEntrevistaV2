package SERVICIOS

import CONFIGURACION.AnalizadorManoCara
import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.ExtractorFeaturesDivlara
import CONFIGURACION.MotorInferenciaDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VentanaLandmarksDivlara
import ESQUEMAS.SolicitudPrediccionVision
import ESQUEMAS.aVentanaLandmarks

/** Límite superior defensivo de frames por ventana para evitar consumo abusivo de RAM (nominal: 20 frames @ 10 FPS). */
const val FRAMES_MAXIMOS_VENTANA_DIVLARA = 120

/** Límite superior defensivo de landmarks por frame (MediaPipe Pose 33 + Face Mesh 478 = 511). */
const val LANDMARKS_MAXIMOS_POR_FRAME = 600

/**
 * Valida la entrada numérica (v1) o de landmarks MediaPipe (P-1), extrae las 343 features cuando llega
 * una ventana de landmarks y delega la inferencia al modelo ONNX de visión DivLara (se ejecuta dentro del motor).
 * Sin persistencia ni inferencia de emociones (RNF10 / P-1).
 */
class ServicioVision(
    private val motor: MotorInferenciaDivlara,
    private val extractor: ExtractorFeaturesDivlara = ExtractorFeaturesDivlara()
) {
    fun predecir(solicitud: SolicitudPrediccionVision): ResultadoPrediccionDivlara {
        ValidadorEntradaVision.validarSolicitud(solicitud)
        return if (solicitud.features != null) predecir(solicitud.features) else predecir(solicitud.aVentanaLandmarks())
    }

    fun predecir(ventana: VentanaLandmarksDivlara): ResultadoPrediccionDivlara {
        ValidadorEntradaVision.validarVentana(ventana)
        // Si solo llega pose33 sin iris/cara, las columnas ausentes se imputan con scaler.mean (igual que live.py).
        val mediasFallback = motor.mediasScaler()?.takeIf { it.size == CANTIDAD_FEATURES_DIVLARA }
        val vector = extractor.extraerVector(ventana, mediasFallback = mediasFallback)
        val detalleManoCara = AnalizadorManoCara.analizar(ventana)
        return predecir(vector.toList()).copy(
            manoCaraUna = detalleManoCara.manoCaraUna,
            manoCaraDos = detalleManoCara.manoCaraDos,
            manoCaraConteo = detalleManoCara.manoCaraConteo
        )
    }

    fun predecir(features: List<Double>): ResultadoPrediccionDivlara {
        ValidadorEntradaVision.validarFeatures(features, motor.cantidadFeaturesEsperadas() ?: CANTIDAD_FEATURES_DIVLARA)
        return motor.predecir(features)
    }
}
