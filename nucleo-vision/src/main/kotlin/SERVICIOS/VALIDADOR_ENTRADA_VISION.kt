package SERVICIOS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.LandmarkMediaPipe
import CONFIGURACION.VentanaLandmarksDivlara
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudPrediccionVision

/**
 * Reglas de entrada de `POST /api/v1/vision/predecir`, sin modelo ni red: las usan tanto el backend (rechaza rápido
 * antes de llamar al motor) como el motor (que las vuelve a aplicar: no confía en quien lo llama).
 */
object ValidadorEntradaVision {

    /** Exige exactamente una entrada (`features` o `frames`). */
    fun validarSolicitud(solicitud: SolicitudPrediccionVision) {
        if (solicitud.features != null && solicitud.frames != null) {
            throw ErrorValidacion(
                "entrada_vision_ambigua",
                "Envía solo 'features' o solo 'frames', no ambos en la misma solicitud"
            )
        }
        if (solicitud.features == null && solicitud.frames == null) {
            throw ErrorValidacion(
                "entrada_vision_requerida",
                "Debes enviar 'features' (343 características) o 'frames' (ventana de landmarks)"
            )
        }
    }

    /** [esperadas]: las que declara el modelo cargado; sin modelo, las del contrato (343). */
    fun validarFeatures(features: List<Double>, esperadas: Int = CANTIDAD_FEATURES_DIVLARA) {
        if (features.size != esperadas) {
            throw ErrorValidacion(
                "cantidad_features_invalida",
                "Se requieren exactamente $esperadas características (se recibieron ${features.size})"
            )
        }
        if (features.any { !it.isFinite() }) {
            throw ErrorValidacion(
                "features_invalidas",
                "Todas las características deben ser números finitos"
            )
        }
    }

    fun validarVentana(ventana: VentanaLandmarksDivlara) {
        if (ventana.frames.isEmpty() || ventana.frames.size > FRAMES_MAXIMOS_VENTANA_DIVLARA) {
            throw ErrorValidacion(
                "ventana_landmarks_invalida",
                "Envía entre 1 y $FRAMES_MAXIMOS_VENTANA_DIVLARA frames por ventana (se recibieron ${ventana.frames.size})"
            )
        }
        if (!ventana.fps.isFinite() || ventana.fps <= 0.0) {
            throw ErrorValidacion(
                "fps_invalido",
                "Los FPS de la ventana deben ser un número finito mayor a cero"
            )
        }
        if (ventana.totalFramesMuestreados != null && ventana.totalFramesMuestreados <= 0) {
            throw ErrorValidacion(
                "ventana_landmarks_invalida",
                "totalFramesMuestreados debe ser mayor a cero"
            )
        }
        val ratio = ventana.ratioDeteccionPose
        if (ratio != null && (!ratio.isFinite() || ratio !in 0.0..1.0)) {
            throw ErrorValidacion(
                "ventana_landmarks_invalida",
                "ratioDeteccionPose debe ser un número finito entre 0.0 y 1.0"
            )
        }
        for (frame in ventana.frames) {
            if (frame.indiceFrame < 0) {
                throw ErrorValidacion(
                    "ventana_landmarks_invalida",
                    "El índice de frame debe ser mayor o igual a cero"
                )
            }
            if (frame.landmarks.size > LANDMARKS_MAXIMOS_POR_FRAME) {
                throw ErrorValidacion(
                    "landmarks_invalidos",
                    "Cada frame admite como máximo $LANDMARKS_MAXIMOS_POR_FRAME landmarks"
                )
            }
            for (lm in frame.landmarks) {
                validarLandmark(lm)
            }
        }
    }

    private fun validarLandmark(lm: LandmarkMediaPipe) {
        if (lm.id < 0) {
            throw ErrorValidacion(
                "landmarks_invalidos",
                "Los identificadores de landmarks deben ser mayores o iguales a cero"
            )
        }
        val esFinito = lm.x.isFinite() &&
            lm.y.isFinite() &&
            lm.z.isFinite() &&
            lm.visibility.isFinite() &&
            lm.worldX.isFinite() &&
            lm.worldY.isFinite() &&
            lm.worldZ.isFinite()
        if (!esFinito) {
            throw ErrorValidacion(
                "landmarks_invalidos",
                "Todas las coordenadas y visibilidad de los landmarks deben ser números finitos"
            )
        }
    }
}
