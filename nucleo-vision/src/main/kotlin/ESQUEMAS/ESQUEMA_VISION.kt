package ESQUEMAS

import CONFIGURACION.FPS_ANALISIS_DIVLARA
import CONFIGURACION.FrameLandmarksDivlara
import CONFIGURACION.LandmarkMediaPipe
import CONFIGURACION.VentanaLandmarksDivlara
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Landmark individual de MediaPipe Pose (0..32) o Face Mesh / iris (468, 473) dentro de un frame P-1.
 * Acepta tanto camelCase (`id`, `worldX`) como snake_case de DivLara (`landmark_id`, `world_x`).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LandmarkEntradaVision(
    @JsonNames("landmark_id", "landmarkId")
    val id: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val visibility: Double = 1.0,
    @JsonNames("world_x")
    val worldX: Double = 0.0,
    @JsonNames("world_y")
    val worldY: Double = 0.0,
    @JsonNames("world_z")
    val worldZ: Double = 0.0
)

/** Frame muestreado dentro de la ventana temporal P-1 (`landmarks` vacío indica frame sin detección). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FrameLandmarksEntradaVision(
    @JsonNames("frame_index", "frameIndex")
    val indiceFrame: Int? = null,
    val landmarks: List<LandmarkEntradaVision> = emptyList(),
    @JsonNames("timestamp_ms")
    val timestampMs: Long? = null
)

/**
 * Entrada de `POST /api/v1/vision/predecir` para inferencia de postura/conducta observable (modelo guía E14).
 * Acepta dos formatos excluyentes sin romper consumidores existentes:
 * - v1: `features` con las 343 características precalculadas.
 * - P-1: `frames` con la ventana de landmarks MediaPipe (nominalmente 20 frames @ 10 FPS).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SolicitudPrediccionVision(
    val features: List<Double>? = null,
    val frames: List<FrameLandmarksEntradaVision>? = null,
    val fps: Double? = null,
    @JsonNames("total_frames_muestreados", "ventana_frames")
    val totalFramesMuestreados: Int? = null,
    @JsonNames("ratio_deteccion_pose")
    val ratioDeteccionPose: Double? = null,
    /** Simulación (entrevista) a la que pertenece la predicción: si viene, se guarda para el reporte (T3.10). */
    @JsonNames("sesion_id", "sesion")
    val sesionId: String? = null,
    /** Milisegundos desde el inicio de la simulación; si falta se usa la hora del servidor. */
    @JsonNames("timestamp_ms")
    val timestampMs: Long? = null
)

/** Convierte una solicitud P-1 con `frames` al modelo de dominio usado por `ExtractorFeaturesDivlara`. */
fun SolicitudPrediccionVision.aVentanaLandmarks(): VentanaLandmarksDivlara {
    val listaFrames = requireNotNull(frames) { "La solicitud no contiene frames de landmarks" }
    return VentanaLandmarksDivlara(
        frames = listaFrames.mapIndexed { posicion, frame ->
            FrameLandmarksDivlara(
                indiceFrame = frame.indiceFrame ?: posicion,
                landmarks = frame.landmarks.map { lm ->
                    LandmarkMediaPipe(
                        id = lm.id,
                        x = lm.x,
                        y = lm.y,
                        z = lm.z,
                        visibility = lm.visibility,
                        worldX = lm.worldX,
                        worldY = lm.worldY,
                        worldZ = lm.worldZ
                    )
                },
                timestampMs = frame.timestampMs
            )
        },
        fps = fps ?: FPS_ANALISIS_DIVLARA,
        totalFramesMuestreados = totalFramesMuestreados,
        ratioDeteccionPose = ratioDeteccionPose
    )
}

/**
 * Respuesta de inferencia ONNX del modelo DivLara (campos base sin defaults por `encodeDefaults = false`).
 * Los campos `mano_cara_*` son aditivos y se informan cuando la solicitud llega con frames de landmarks (P-1),
 * sin modificar el mapa `predicciones` que consume el contrato Android.
 */
@Serializable
data class RespuestaPrediccionVision(
    val versionModelo: String,
    val predicciones: Map<String, Double>,
    val escala: String,
    @SerialName("mano_cara_una")
    val manoCaraUna: Double? = null,
    @SerialName("mano_cara_dos")
    val manoCaraDos: Double? = null,
    @SerialName("mano_cara_conteo")
    val manoCaraConteo: Int? = null
)
