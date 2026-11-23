package CONFIGURACION

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** FPS de análisis estándar del pipeline DivLara (`configs/dataset_builder.yaml`). */
const val FPS_ANALISIS_DIVLARA = 10.0

/** Duración nominal de la ventana de entrada en segundos (`MANIFIESTO.json`). */
const val VENTANA_SEGUNDOS_DIVLARA = 2

/** Cantidad nominal de frames por ventana (`2 s @ 10 FPS`). */
const val FRAMES_VENTANA_DIVLARA = 20

/** Factor alfa por defecto del suavizado exponencial EMA en `visual_features.py`. */
const val ALFA_EMA_DIVLARA = 0.3

/** Ángulo de profundidad de torso neutral de referencia cuando no hay calibración previa. */
const val ANGULO_REFERENCIA_NEUTRAL_DIVLARA = -15.0

// Índices MediaPipe Pose (33 landmarks; los de tren inferior 25..32 se excluyen en features visuales).
private const val LM_NARIZ = 0
private const val LM_OREJA_IZQ = 7
private const val LM_OREJA_DER = 8
private const val LM_HOMBRO_IZQ = 11
private const val LM_HOMBRO_DER = 12
private const val LM_CODO_IZQ = 13
private const val LM_CODO_DER = 14
private const val LM_MUNECA_IZQ = 15
private const val LM_MUNECA_DER = 16
private const val LM_CADERA_IZQ = 23
private const val LM_CADERA_DER = 24
private const val LM_MAX_VISUAL = 24
private const val LM_MAX_POSE = 32
private const val LM_IRIS_IZQ = 468
private const val LM_IRIS_DER = 473

private const val EPSILON_ESCALA = 1e-4
private const val EPSILON_SEGMENTO = 1e-8
private const val DURACION_MINIMA_SEG = 0.1
private const val UMBRAL_CAMBIO_POSTURA_GRADOS = 8.5
private const val UMBRAL_CRUCE_INSTANTANEO = 0.65
private const val DURACION_MIN_CRUCE_SEG = 0.8
private const val TOPE_CRUCE_CORTO = 0.45
private const val UMBRAL_MESA_HORIZ = 0.70
private const val UMBRAL_MESA_FRAME = 0.6
private const val DURACION_MIN_MESA_SEG = 1.0
private const val UMBRAL_CONFIANZA_TORSO_FB = 0.60
private const val MIN_FRAMES_TORSO_FB = 3
private const val UMBRAL_SCORE_TORSO_FB = 0.20
private const val DIVISOR_TANH_TORSO = 15.0
private const val UMBRAL_ORIENTACION_FRONTAL = 0.70
private const val UMBRAL_ORIENTACION_PARCIAL = 0.40
private const val UMBRAL_PICO_GESTO = 1.0

private val IDS_LANDMARKS_ESTADISTICAS: List<Int> = (0..LM_MAX_POSE).toList() + listOf(LM_IRIS_IZQ, LM_IRIS_DER)
private val CONJUNTO_IDS_LANDMARKS_ESTADISTICAS: Set<Int> = IDS_LANDMARKS_ESTADISTICAS.toSet()

/**
 * Orden exacto de las 343 columnas de entrada del modelo guía E14 (`meta.json -> feature_columns`, `include_speech=False`).
 * Cualquier cambio de orden invalidaría la alineación con `scaler.json` y `modelo.onnx`.
 */
val COLUMNAS_FEATURES_DIVLARA: List<String> = buildList(CANTIDAD_FEATURES_DIVLARA) {
    addAll(
        listOf(
            "has_pose",
            "pose_detection_ratio",
            "body_center_offset_x",
            "body_center_offset_y",
            "body_center_offset_abs",
            "shoulder_angle",
            "shoulder_angle_mean",
            "shoulder_angle_std",
            "shoulder_angle_min",
            "shoulder_angle_max",
            "shoulder_angle_range",
            "shoulder_distance",
            "shoulder_height_asymmetry",
            "torso_angle",
            "torso_variance",
            "torso_lateral_angle_mean",
            "torso_lateral_angle_std",
            "torso_lateral_angle_range",
            "torso_lateral_lean_score",
            "torso_depth_angle_mean",
            "torso_depth_angle_std",
            "torso_forward_backward_score",
            "torso_forward_backward_confidence",
            "torso_depth_delta_from_reference",
            "torso_forward_backward_class",
            "torso_forward_backward_valid",
            "elbow_distance_normalized",
            "wrist_distance_normalized",
            "left_wrist_to_torso_distance",
            "right_wrist_to_torso_distance",
            "left_elbow_to_torso_distance",
            "right_elbow_to_torso_distance",
            "left_arm_openness",
            "right_arm_openness",
            "upper_body_openness_score",
            "arm_crossing_score",
            "arm_crossing_duration_seconds",
            "left_elbow_height",
            "right_elbow_height",
            "elbow_height_asymmetry",
            "left_wrist_height",
            "right_wrist_height",
            "wrist_height_asymmetry",
            "arm_height_asymmetry",
            "left_hand_relative_height",
            "right_hand_relative_height",
            "hands_above_shoulders_ratio",
            "hands_chest_level_ratio",
            "hands_lower_torso_ratio",
            "left_hand_visibility_ratio",
            "right_hand_visibility_ratio",
            "hand_visibility_ratio",
            "gesture_reliability",
            "left_forearm_angle",
            "right_forearm_angle",
            "left_forearm_horizontal_score",
            "right_forearm_horizontal_score",
            "forearm_horizontal_score",
            "possible_left_arm_table_rest",
            "possible_right_arm_table_rest",
            "possible_table_rest_score",
            "arm_position_stability",
            "hand_position_stability",
            "head_displacement",
            "head_displacement_mean",
            "head_displacement_std",
            "head_velocity",
            "head_velocity_mean",
            "head_velocity_std",
            "head_velocity_max",
            "head_acceleration_mean",
            "head_movement_variability",
            "camera_orientation_geom",
            "body_displacement",
            "body_velocity_mean",
            "body_velocity_std",
            "body_velocity_max",
            "body_acceleration_mean",
            "body_acceleration_std",
            "movement_variability_score",
            "movement_variance",
            "left_wrist_velocity",
            "left_wrist_velocity_mean",
            "right_wrist_velocity",
            "right_wrist_velocity_mean",
            "average_wrist_velocity",
            "left_elbow_velocity_mean",
            "right_elbow_velocity_mean",
            "gesture_velocity_mean",
            "gesture_velocity_std",
            "gesture_amplitude",
            "gesture_frequency",
            "posture_change_count",
            "posture_changes_per_minute",
            "visibility_ratio",
            "reliability_score",
            "lm_num_frames"
        )
    )
    for (id in IDS_LANDMARKS_ESTADISTICAS) {
        add("lm_${id}_x_mean")
        add("lm_${id}_x_std")
        add("lm_${id}_y_mean")
        add("lm_${id}_y_std")
        add("lm_${id}_z_mean")
        add("lm_${id}_z_std")
        add("lm_${id}_vis_mean")
    }
    add("lm_vis_global")
}

/** Landmark individual de MediaPipe Pose (0..32) o Face Mesh / iris (468, 473). */
data class LandmarkMediaPipe(
    val id: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val visibility: Double = 1.0,
    val worldX: Double = 0.0,
    val worldY: Double = 0.0,
    val worldZ: Double = 0.0
)

/** Un frame muestreado dentro de la ventana temporal; `landmarks` vacío indica frame sin detección. */
data class FrameLandmarksDivlara(
    val indiceFrame: Int,
    val landmarks: List<LandmarkMediaPipe> = emptyList(),
    val timestampMs: Long? = null
)

/**
 * Ventana de landmarks enviada desde MediaPipe (contrato E14: 2 s @ 10 FPS = 20 frames).
 * Soporta ventanas incompletas o frames sin pose sin fallar.
 */
data class VentanaLandmarksDivlara(
    val frames: List<FrameLandmarksDivlara>,
    val fps: Double = FPS_ANALISIS_DIVLARA,
    val totalFramesMuestreados: Int? = null,
    val ratioDeteccionPose: Double? = null
)

/**
 * Replica en Kotlin puro el pipeline de extracción de características visuales y agregados `lm_*`
 * de DivLara (`visual_features.py` + `prepare_training_dataset.py` / `live.py`, con `include_speech=False`).
 *
 * Produce un `DoubleArray(343)` alineado 1:1 con `feature_columns` de `models/divlara-e14-mlp/meta.json`.
 */
class ExtractorFeaturesDivlara(
    private val suavizadoEmaActivo: Boolean = true,
    private val alfaEma: Double = ALFA_EMA_DIVLARA,
    private val anguloReferenciaNeutral: Double? = null
) {
    init {
        require(alfaEma.isFinite() && alfaEma in 0.0..1.0) {
            "El factor alfa EMA debe estar en [0.0, 1.0]: $alfaEma"
        }
    }

    /**
     * Extrae el vector de 343 features en el orden exacto de [COLUMNAS_FEATURES_DIVLARA].
     *
     * @param ventana Ventana de landmarks MediaPipe (nominalmente 20 frames a 10 FPS).
     * @param mediasFallback Opcional (`scaler.mean`, longitud 343): si se entrega, cualquier feature
     *   ausente en la ventana (ej. iris 468/473 cuando solo llega pose33) se imputa con su media del
     *   scaler, replicando `live.py` para que el z-score normalizado quede en 0.0; si es `null`, se usa `0.0`.
     */
    fun extraerVector(
        ventana: VentanaLandmarksDivlara,
        mediasFallback: DoubleArray? = null
    ): DoubleArray {
        require(ventana.fps.isFinite() && ventana.fps > 0.0) {
            "Los FPS de la ventana deben ser finitos y mayores que cero: ${ventana.fps}"
        }
        if (mediasFallback != null) {
            require(mediasFallback.size == CANTIDAD_FEATURES_DIVLARA) {
                "mediasFallback debe tener exactamente $CANTIDAD_FEATURES_DIVLARA elementos (recibidos ${mediasFallback.size})"
            }
        }

        val mapa = extraerMapaFeatures(ventana)
        return DoubleArray(CANTIDAD_FEATURES_DIVLARA) { indice ->
            val columna = COLUMNAS_FEATURES_DIVLARA[indice]
            val valor = mapa[columna]
            if (valor != null && valor.isFinite()) {
                valor
            } else {
                val fallback = mediasFallback?.get(indice)
                if (fallback != null && fallback.isFinite()) fallback else 0.0
            }
        }
    }

    /**
     * Calcula el mapa `nombreColumna -> valor` antes de la imputación de columnas ausentes.
     * Útil para inspección y depuración sin acoplar al orden posicional del scaler.
     */
    fun extraerMapaFeatures(ventana: VentanaLandmarksDivlara): Map<String, Double> {
        require(ventana.fps.isFinite() && ventana.fps > 0.0) {
            "Los FPS de la ventana deben ser finitos y mayores que cero: ${ventana.fps}"
        }

        val framesConPose = ventana.frames.count { frame ->
            frame.landmarks.any { it.id in 0..LM_MAX_POSE }
        }
        val tienePose = framesConPose > 0
        val ratioPose = calcularRatioDeteccionPose(ventana, framesConPose)

        val datosCrudos = extraerDatosCrudosPorFrame(ventana.frames)
        val framesOrdenados = datosCrudos.keys.sorted()
        val datosVisuales = if (suavizadoEmaActivo && framesOrdenados.size > 1) {
            aplicarSuavizadoEma(datosCrudos, framesOrdenados, alfaEma)
        } else {
            datosCrudos
        }

        val metricasVisuales = if (framesOrdenados.isNotEmpty()) {
            calcularMetricasVisuales(datosVisuales, framesOrdenados, ventana.fps)
        } else {
            emptyMap()
        }

        val estadisticasLm = calcularEstadisticasLandmarks(ventana.frames)

        val combinado = LinkedHashMap<String, Double>(CANTIDAD_FEATURES_DIVLARA + 4)
        combinado.putAll(metricasVisuales)
        combinado.putAll(estadisticasLm)
        combinado["has_pose"] = if (tienePose) 1.0 else 0.0
        combinado["pose_detection_ratio"] = redondear(ratioPose, 4)
        return combinado
    }

    private fun calcularRatioDeteccionPose(ventana: VentanaLandmarksDivlara, framesConPose: Int): Double {
        ventana.ratioDeteccionPose?.let { ratio ->
            if (ratio.isFinite()) return ratio.coerceIn(0.0, 1.0)
        }
        val denominador = ventana.totalFramesMuestreados?.takeIf { it > 0 } ?: ventana.frames.size
        if (denominador <= 0) return 0.0
        return (framesConPose.toDouble() / denominador.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun extraerDatosCrudosPorFrame(
        frames: List<FrameLandmarksDivlara>
    ): Map<Int, MutableMap<Int, PuntoLandmark>> {
        val porFrame = HashMap<Int, MutableMap<Int, PuntoLandmark>>()
        for (frame in frames) {
            for (lm in frame.landmarks) {
                // Replica extract_raw_frame_data: excluye piernas (id > 24) en el cálculo visual.
                if (lm.id !in 0..LM_MAX_VISUAL) continue
                if (!lm.x.isFinite() || !lm.y.isFinite() || !lm.z.isFinite()) continue
                val mapaFrame = porFrame.getOrPut(frame.indiceFrame) { HashMap() }
                mapaFrame[lm.id] = PuntoLandmark(
                    x = lm.x,
                    y = lm.y,
                    z = lm.z,
                    vis = if (lm.visibility.isFinite()) lm.visibility else 0.0,
                    wx = if (lm.worldX.isFinite()) lm.worldX else 0.0,
                    wy = if (lm.worldY.isFinite()) lm.worldY else 0.0,
                    wz = if (lm.worldZ.isFinite()) lm.worldZ else 0.0
                )
            }
        }
        return porFrame
    }

    private fun aplicarSuavizadoEma(
        datos: Map<Int, Map<Int, PuntoLandmark>>,
        framesOrdenados: List<Int>,
        alfa: Double
    ): Map<Int, Map<Int, PuntoLandmark>> {
        val suavizados = HashMap<Int, Map<Int, PuntoLandmark>>(framesOrdenados.size)
        val previos = HashMap<Int, PuntoLandmark>()
        val beta = 1.0 - alfa

        for (indiceFrame in framesOrdenados) {
            val lmsActuales = datos[indiceFrame] ?: continue
            val mapaFrame = HashMap<Int, PuntoLandmark>(lmsActuales.size)
            for ((id, actual) in lmsActuales) {
                val previo = previos[id]
                val nuevo = if (previo == null) {
                    actual
                } else {
                    PuntoLandmark(
                        x = alfa * actual.x + beta * previo.x,
                        y = alfa * actual.y + beta * previo.y,
                        z = alfa * actual.z + beta * previo.z,
                        vis = actual.vis,
                        wx = alfa * actual.wx + beta * previo.wx,
                        wy = alfa * actual.wy + beta * previo.wy,
                        wz = alfa * actual.wz + beta * previo.wz
                    )
                }
                mapaFrame[id] = nuevo
                previos[id] = nuevo
            }
            suavizados[indiceFrame] = mapaFrame
        }
        return suavizados
    }

    private fun calcularMetricasVisuales(
        datosPorFrame: Map<Int, Map<Int, PuntoLandmark>>,
        framesOrdenados: List<Int>,
        fps: Double
    ): Map<String, Double> {
        val acc = AcumuladoresVisuales()
        for (indiceFrame in framesOrdenados) {
            val lms = datosPorFrame[indiceFrame] ?: continue
            procesarFrameVisual(lms, acc)
        }
        return construirMapaMetricasVisuales(acc, framesOrdenados.size, fps)
    }

    private fun procesarFrameVisual(lms: Map<Int, PuntoLandmark>, acc: AcumuladoresVisuales) {
        val lsVal = lms[LM_HOMBRO_IZQ] ?: return
        val rsVal = lms[LM_HOMBRO_DER] ?: return

        val ls = Punto2D(lsVal.x, lsVal.y)
        val rs = Punto2D(rsVal.x, rsVal.y)
        val centro = Punto2D((ls.x + rs.x) / 2.0, (ls.y + rs.y) / 2.0)
        val escala = max(EPSILON_ESCALA, distancia2D(ls, rs))

        acc.escalasHombros.add(escala)
        acc.posicionesCentroHombros.add(centro)

        val offX = centro.x - 0.5
        val offY = centro.y - 0.5
        acc.offsetsCentroX.add(offX)
        acc.offsetsCentroY.add(offY)
        acc.offsetsCentroAbs.add(sqrt(offX * offX + offY * offY))

        val anguloHombros = Math.toDegrees(atan2(rs.y - ls.y, rs.x - ls.x))
        acc.angulosHombros.add(anguloHombros)
        acc.asimetriasAlturaHombros.add(abs(ls.y - rs.y) / escala)

        val visHombros = (lsVal.vis + rsVal.vis) / 2.0
        acc.visibilidadesHombros.add(visHombros)

        val midCadera = procesarTorsoYCaderas(lms, lsVal, rsVal, centro, escala, visHombros, acc)
        procesarBrazosYManos(lms, centro, midCadera, escala, acc)
        procesarCabezaYCamara(lms, centro, escala, acc)

        if (lms.isNotEmpty()) {
            var sumaVis = 0.0
            for (p in lms.values) sumaVis += p.vis
            acc.visibilidadesFrames.add(sumaVis / lms.size)
        } else {
            acc.visibilidadesFrames.add(0.0)
        }
    }

    private fun procesarTorsoYCaderas(
        lms: Map<Int, PuntoLandmark>,
        lsVal: PuntoLandmark,
        rsVal: PuntoLandmark,
        centro: Punto2D,
        escala: Double,
        visHombros: Double,
        acc: AcumuladoresVisuales
    ): Punto2D {
        val lhVal = lms[LM_CADERA_IZQ]
        val rhVal = lms[LM_CADERA_DER]
        if (lhVal == null || rhVal == null) {
            return Punto2D(centro.x, centro.y + escala * 1.5)
        }

        val midCadera = Punto2D((lhVal.x + rhVal.x) / 2.0, (lhVal.y + rhVal.y) / 2.0)
        val visCaderas = (lhVal.vis + rhVal.vis) / 2.0
        acc.visibilidadesCaderas.add(visCaderas)

        val torsoVx = centro.x - midCadera.x
        val torsoVy = centro.y - midCadera.y
        acc.angulosLateralesTorso.add(Math.toDegrees(atan2(torsoVx, -torsoVy)))

        val scWy = (lsVal.wy + rsVal.wy) / 2.0
        val scWz = (lsVal.wz + rsVal.wz) / 2.0
        val hcWy = (lhVal.wy + rhVal.wy) / 2.0
        val hcWz = (lhVal.wz + rhVal.wz) / 2.0
        val dz = scWz - hcWz
        val dyW = scWy - hcWy
        acc.angulosProfundidadTorso.add(Math.toDegrees(atan2(dz, max(EPSILON_ESCALA, abs(dyW)))))
        acc.confianzasProfundidadTorso.add(min(visHombros, visCaderas))
        return midCadera
    }

    private fun procesarBrazosYManos(
        lms: Map<Int, PuntoLandmark>,
        centro: Punto2D,
        midCadera: Punto2D,
        escala: Double,
        acc: AcumuladoresVisuales
    ) {
        val lwLm = lms[LM_MUNECA_IZQ]
        val rwLm = lms[LM_MUNECA_DER]
        val leLm = lms[LM_CODO_IZQ]
        val reLm = lms[LM_CODO_DER]

        acc.visibilidadesManoIzq.add(lwLm?.vis ?: 0.0)
        acc.visibilidadesManoDer.add(rwLm?.vis ?: 0.0)

        var lwPt: Punto2D? = null
        var lwH = -1.0
        if (lwLm != null) {
            val pt = Punto2D(lwLm.x, lwLm.y)
            lwPt = pt
            acc.posicionesMunecaIzq.add(Punto2D((pt.x - centro.x) / escala, (pt.y - centro.y) / escala))
            acc.distanciasMunecaIzqTorso.add(distanciaPuntoASegmento(pt, centro, midCadera) / escala)
            lwH = (centro.y - pt.y) / escala
            acc.alturasMunecaIzq.add(lwH)
            // Replica visual_features.py líneas 304-306: las razones de zona de manos usan la muñeca izquierda.
            acc.manosSobreHombros.add(if (pt.y < centro.y) 1.0 else 0.0)
            acc.manosNivelPecho.add(if (pt.y >= centro.y && pt.y <= midCadera.y) 1.0 else 0.0)
            acc.manosTorsoInferior.add(if (pt.y > midCadera.y) 1.0 else 0.0)
        }

        var rwPt: Punto2D? = null
        var rwH = -1.0
        if (rwLm != null) {
            val pt = Punto2D(rwLm.x, rwLm.y)
            rwPt = pt
            acc.posicionesMunecaDer.add(Punto2D((pt.x - centro.x) / escala, (pt.y - centro.y) / escala))
            acc.distanciasMunecaDerTorso.add(distanciaPuntoASegmento(pt, centro, midCadera) / escala)
            rwH = (centro.y - pt.y) / escala
            acc.alturasMunecaDer.add(rwH)
        }

        var wDist = 0.0
        if (lwPt != null && rwPt != null) {
            wDist = distancia2D(lwPt, rwPt) / escala
            acc.distanciasMunecasNorm.add(wDist)
            acc.asimetriasAlturaMunecas.add(abs(lwH - rwH))
        }

        var lePt: Punto2D? = null
        var leH = -0.5
        if (leLm != null) {
            val pt = Punto2D(leLm.x, leLm.y)
            lePt = pt
            acc.posicionesCodoIzq.add(Punto2D((pt.x - centro.x) / escala, (pt.y - centro.y) / escala))
            acc.distanciasCodoIzqTorso.add(distanciaPuntoASegmento(pt, centro, midCadera) / escala)
            leH = (centro.y - pt.y) / escala
            acc.alturasCodoIzq.add(leH)
        }

        var rePt: Punto2D? = null
        var reH = -0.5
        if (reLm != null) {
            val pt = Punto2D(reLm.x, reLm.y)
            rePt = pt
            acc.posicionesCodoDer.add(Punto2D((pt.x - centro.x) / escala, (pt.y - centro.y) / escala))
            acc.distanciasCodoDerTorso.add(distanciaPuntoASegmento(pt, centro, midCadera) / escala)
            reH = (centro.y - pt.y) / escala
            acc.alturasCodoDer.add(reH)
        }

        var eDist = 0.0
        if (lePt != null && rePt != null) {
            eDist = distancia2D(lePt, rePt) / escala
            acc.distanciasCodosNorm.add(eDist)
            acc.asimetriasAlturaCodos.add(abs(leH - reH))
        }

        if (lePt != null && rePt != null && lwPt != null && rwPt != null) {
            val armHL = (leH + lwH) / 2.0
            val armHR = (reH + rwH) / 2.0
            acc.asimetriasAlturaBrazos.add(abs(armHL - armHR))

            acc.puntajesAperturaTorso.add(((eDist + wDist) / 3.0).coerceIn(0.0, 1.0))

            val midlineX = centro.x
            val lwCross = if (lwPt.x < midlineX + 0.1 * escala) 1.0 else 0.0
            val rwCross = if (rwPt.x > midlineX - 0.1 * escala) 1.0 else 0.0
            val wristsClose = if (wDist < 0.9) 1.0 else max(0.0, 1.0 - (wDist - 0.9))
            val elbowsClose = if (eDist < 1.3) 1.0 else max(0.0, 1.0 - (eDist - 1.3))
            val yMedioMunecas = (lwPt.y + rwPt.y) / 2.0
            val chestZone = if (yMedioMunecas >= centro.y - 0.2 * escala && yMedioMunecas <= midCadera.y) 1.0 else 0.5

            val crossingVal = (
                (lwCross + rwCross) * 0.3 +
                    wristsClose * 0.25 +
                    elbowsClose * 0.25 +
                    chestZone * 0.2
                ).coerceIn(0.0, 1.0)
            acc.puntajesCruceBrazos.add(crossingVal)
            acc.banderasCruceInstantaneo.add(crossingVal >= UMBRAL_CRUCE_INSTANTANEO)
        }

        if (lePt != null && lwPt != null) {
            val anguloL = Math.toDegrees(atan2(lwPt.y - lePt.y, lwPt.x - lePt.x))
            acc.angulosAntebrazoIzq.add(anguloL)
            val horizL = max(0.0, 1.0 - abs(sin(Math.toRadians(anguloL))))
            acc.horizAntebrazoIzq.add(horizL)
            val mesaL = if (lwPt.y > centro.y + 0.5 * escala && horizL > UMBRAL_MESA_HORIZ) 1.0 else 0.0
            acc.apoyoMesaIzq.add(mesaL)
        }

        if (rePt != null && rwPt != null) {
            val anguloR = Math.toDegrees(atan2(rwPt.y - rePt.y, rwPt.x - rePt.x))
            acc.angulosAntebrazoDer.add(anguloR)
            val horizR = max(0.0, 1.0 - abs(sin(Math.toRadians(anguloR))))
            acc.horizAntebrazoDer.add(horizR)
            val mesaR = if (rwPt.y > centro.y + 0.5 * escala && horizR > UMBRAL_MESA_HORIZ) 1.0 else 0.0
            acc.apoyoMesaDer.add(mesaR)
        }

        if (lePt != null && lwPt != null && rePt != null && rwPt != null) {
            acc.apoyoMesaAmbos.add((acc.apoyoMesaIzq.last() + acc.apoyoMesaDer.last()) / 2.0)
        }
    }

    private fun procesarCabezaYCamara(
        lms: Map<Int, PuntoLandmark>,
        centro: Punto2D,
        escala: Double,
        acc: AcumuladoresVisuales
    ) {
        val narizLm = lms[LM_NARIZ] ?: return
        val nPt = Punto2D(narizLm.x, narizLm.y)
        acc.posicionesNariz.add(Punto2D((nPt.x - centro.x) / escala, (nPt.y - centro.y) / escala))

        val orejaIzq = lms[LM_OREJA_IZQ] ?: return
        val orejaDer = lms[LM_OREJA_DER] ?: return
        val dIzq = distancia2D(nPt, Punto2D(orejaIzq.x, orejaIzq.y))
        val dDer = distancia2D(nPt, Punto2D(orejaDer.x, orejaDer.y))
        val simetria = min(dIzq, dDer) / max(EPSILON_ESCALA, max(dIzq, dDer))
        val orientacion = when {
            simetria > UMBRAL_ORIENTACION_FRONTAL -> 2.0
            simetria > UMBRAL_ORIENTACION_PARCIAL -> 1.0
            else -> 0.0
        }
        acc.orientacionesCamaraGeom.add(orientacion)
    }

    private fun construirMapaMetricasVisuales(
        acc: AcumuladoresVisuales,
        cantidadFrames: Int,
        fps: Double
    ): Map<String, Double> {
        val cabeza = calcularCinematica(acc.posicionesNariz, fps)
        val cuerpo = calcularCinematica(acc.posicionesCentroHombros, fps)
        val munecaIzq = calcularCinematica(acc.posicionesMunecaIzq, fps)
        val munecaDer = calcularCinematica(acc.posicionesMunecaDer, fps)
        val codoIzq = calcularCinematica(acc.posicionesCodoIzq, fps)
        val codoDer = calcularCinematica(acc.posicionesCodoDer, fps)

        val (bodyAccMean, bodyAccStd) = calcularAceleracion(acc.posicionesCentroHombros, fps)
        val (headAccMean, _) = calcularAceleracion(acc.posicionesNariz, fps)

        val durSeg = max(DURACION_MINIMA_SEG, cantidadFrames / fps)
        val cambiosPostura = contarCambiosPostura(acc.angulosLateralesTorso, fps)
        val cambiosPorMinuto = redondear(cambiosPostura * (60.0 / durSeg), 2)

        val (armCrossingScore, armCrossingDurS) = calcularPersistenciaCruceBrazos(acc, fps)
        val tableRestScore = calcularPersistenciaApoyoMesa(acc.apoyoMesaAmbos, fps)

        val torsoDepthMean = mediaOZero(acc.angulosProfundidadTorso)
        val torsoDepthStd = desvEstandarPoblacional(acc.angulosProfundidadTorso)
        val fbConf = mediaOZero(acc.confianzasProfundidadTorso)
        val refAngle = anguloReferenciaNeutral ?: ANGULO_REFERENCIA_NEUTRAL_DIVLARA
        val torsoDepthDelta = -(torsoDepthMean - refAngle)
        val fbScore = tanh(torsoDepthDelta / DIVISOR_TANH_TORSO)
        val fbValid = fbConf >= UMBRAL_CONFIANZA_TORSO_FB && acc.angulosProfundidadTorso.size >= MIN_FRAMES_TORSO_FB
        val fbClass = when {
            !fbValid -> -1.0
            fbScore > UMBRAL_SCORE_TORSO_FB -> 2.0
            fbScore < -UMBRAL_SCORE_TORSO_FB -> 0.0
            else -> 1.0
        }

        val avgWristVel = (munecaIzq.velocidadMedia + munecaDer.velocidadMedia) / 2.0
        val avgWristStd = (munecaIzq.velocidadStd + munecaDer.velocidadStd) / 2.0
        val visManosTodas = acc.visibilidadesManoIzq + acc.visibilidadesManoDer
        val handVis = mediaOZero(visManosTodas)
        val gestureAmp = max(munecaIzq.desplazamientoTotal, munecaDer.desplazamientoTotal)
        val gestureFreq = calcularFrecuenciaGestos(acc.posicionesMunecaIzq, fps, durSeg)

        val meanVis = mediaOZero(acc.visibilidadesFrames)
        val shVisMean = mediaOZero(acc.visibilidadesHombros)
        val relScore = (
            0.35 * (acc.posicionesCentroHombros.size.toDouble() / max(1, cantidadFrames)) +
                0.25 * meanVis +
                0.20 * shVisMean +
                0.20 * handVis
            ).coerceIn(0.0, 1.0)

        val shMean = mediaOZero(acc.angulosHombros)
        val shStd = desvEstandarPoblacional(acc.angulosHombros)
        val shMin = acc.angulosHombros.minOrNull() ?: 0.0
        val shMax = acc.angulosHombros.maxOrNull() ?: 0.0

        val torsoVar = varianzaPoblacional(acc.angulosLateralesTorso)
        val tLatMean = mediaOZero(acc.angulosLateralesTorso)
        val tLatStd = desvEstandarPoblacional(acc.angulosLateralesTorso)
        val tLatRange = if (acc.angulosLateralesTorso.isNotEmpty()) {
            acc.angulosLateralesTorso.max() - acc.angulosLateralesTorso.min()
        } else {
            0.0
        }

        val aperturaBrazoIzq = calcularAperturaPromedioBrazo(acc.distanciasMunecaIzqTorso, acc.distanciasCodoIzqTorso)
        val aperturaBrazoDer = calcularAperturaPromedioBrazo(acc.distanciasMunecaDerTorso, acc.distanciasCodoDerTorso)
        val horizTodos = acc.horizAntebrazoIzq + acc.horizAntebrazoDer
        val camOrientGeom = if (acc.orientacionesCamaraGeom.isNotEmpty()) {
            redondear(mediaOZero(acc.orientacionesCamaraGeom), 0)
        } else {
            2.0
        }

        return linkedMapOf(
            "body_center_offset_x" to redondear(mediaOZero(acc.offsetsCentroX), 4),
            "body_center_offset_y" to redondear(mediaOZero(acc.offsetsCentroY), 4),
            "body_center_offset_abs" to redondear(mediaOZero(acc.offsetsCentroAbs), 4),

            "shoulder_angle" to redondear(shMean, 2),
            "shoulder_angle_mean" to redondear(shMean, 2),
            "shoulder_angle_std" to redondear(shStd, 2),
            "shoulder_angle_min" to redondear(shMin, 2),
            "shoulder_angle_max" to redondear(shMax, 2),
            "shoulder_angle_range" to redondear(shMax - shMin, 2),
            "shoulder_distance" to redondear(mediaOZero(acc.escalasHombros), 4),
            "shoulder_height_asymmetry" to redondear(mediaOZero(acc.asimetriasAlturaHombros), 4),

            "torso_angle" to redondear(tLatMean, 2),
            "torso_variance" to redondear(torsoVar, 4),
            "torso_lateral_angle_mean" to redondear(tLatMean, 2),
            "torso_lateral_angle_std" to redondear(tLatStd, 2),
            "torso_lateral_angle_range" to redondear(tLatRange, 2),
            "torso_lateral_lean_score" to redondear(tanh(tLatMean / DIVISOR_TANH_TORSO), 4),

            "torso_depth_angle_mean" to redondear(torsoDepthMean, 2),
            "torso_depth_angle_std" to redondear(torsoDepthStd, 2),
            "torso_forward_backward_score" to redondear(fbScore, 4),
            "torso_forward_backward_confidence" to redondear(fbConf, 3),
            "torso_depth_delta_from_reference" to redondear(torsoDepthDelta, 2),
            "torso_forward_backward_class" to fbClass,
            "torso_forward_backward_valid" to if (fbValid) 1.0 else 0.0,

            "elbow_distance_normalized" to redondear(mediaOZero(acc.distanciasCodosNorm), 4),
            "wrist_distance_normalized" to redondear(mediaOZero(acc.distanciasMunecasNorm), 4),
            "left_wrist_to_torso_distance" to redondear(mediaOZero(acc.distanciasMunecaIzqTorso), 4),
            "right_wrist_to_torso_distance" to redondear(mediaOZero(acc.distanciasMunecaDerTorso), 4),
            "left_elbow_to_torso_distance" to redondear(mediaOZero(acc.distanciasCodoIzqTorso), 4),
            "right_elbow_to_torso_distance" to redondear(mediaOZero(acc.distanciasCodoDerTorso), 4),
            "left_arm_openness" to redondear(aperturaBrazoIzq, 4),
            "right_arm_openness" to redondear(aperturaBrazoDer, 4),
            "upper_body_openness_score" to if (acc.puntajesAperturaTorso.isNotEmpty()) {
                redondear(mediaOZero(acc.puntajesAperturaTorso), 4)
            } else {
                0.5
            },

            "arm_crossing_score" to armCrossingScore,
            "arm_crossing_duration_seconds" to armCrossingDurS,

            "left_elbow_height" to redondear(mediaOZero(acc.alturasCodoIzq), 4),
            "right_elbow_height" to redondear(mediaOZero(acc.alturasCodoDer), 4),
            "elbow_height_asymmetry" to redondear(mediaOZero(acc.asimetriasAlturaCodos), 4),
            "left_wrist_height" to redondear(mediaOZero(acc.alturasMunecaIzq), 4),
            "right_wrist_height" to redondear(mediaOZero(acc.alturasMunecaDer), 4),
            "wrist_height_asymmetry" to redondear(mediaOZero(acc.asimetriasAlturaMunecas), 4),
            "arm_height_asymmetry" to redondear(mediaOZero(acc.asimetriasAlturaBrazos), 4),

            "left_hand_relative_height" to redondear(mediaOZero(acc.alturasMunecaIzq), 4),
            "right_hand_relative_height" to redondear(mediaOZero(acc.alturasMunecaDer), 4),
            "hands_above_shoulders_ratio" to redondear(mediaOZero(acc.manosSobreHombros), 4),
            "hands_chest_level_ratio" to redondear(mediaOZero(acc.manosNivelPecho), 4),
            "hands_lower_torso_ratio" to redondear(mediaOZero(acc.manosTorsoInferior), 4),
            "left_hand_visibility_ratio" to redondear(mediaOZero(acc.visibilidadesManoIzq), 4),
            "right_hand_visibility_ratio" to redondear(mediaOZero(acc.visibilidadesManoDer), 4),
            "hand_visibility_ratio" to redondear(handVis, 4),
            "gesture_reliability" to redondear(handVis, 4),

            "left_forearm_angle" to redondear(mediaOZero(acc.angulosAntebrazoIzq), 2),
            "right_forearm_angle" to redondear(mediaOZero(acc.angulosAntebrazoDer), 2),
            "left_forearm_horizontal_score" to redondear(mediaOZero(acc.horizAntebrazoIzq), 4),
            "right_forearm_horizontal_score" to redondear(mediaOZero(acc.horizAntebrazoDer), 4),
            "forearm_horizontal_score" to redondear(mediaOZero(horizTodos), 4),
            "possible_left_arm_table_rest" to redondear(mediaOZero(acc.apoyoMesaIzq), 4),
            "possible_right_arm_table_rest" to redondear(mediaOZero(acc.apoyoMesaDer), 4),
            "possible_table_rest_score" to tableRestScore,

            "arm_position_stability" to redondear(1.0 - min(1.0, (codoIzq.velocidadMedia + codoDer.velocidadMedia) * 0.5), 4),
            "hand_position_stability" to redondear(1.0 - min(1.0, avgWristVel * 0.5), 4),

            "head_displacement" to redondear(cabeza.desplazamientoTotal, 4),
            "head_displacement_mean" to redondear(cabeza.desplazamientoTotal, 4),
            "head_displacement_std" to redondear(cabeza.velocidadStd / fps, 4),
            "head_velocity" to redondear(cabeza.velocidadMedia, 4),
            "head_velocity_mean" to redondear(cabeza.velocidadMedia, 4),
            "head_velocity_std" to redondear(cabeza.velocidadStd, 4),
            "head_velocity_max" to redondear(cabeza.velocidadMax, 4),
            "head_acceleration_mean" to redondear(headAccMean, 4),
            "head_movement_variability" to redondear(cabeza.velocidadStd, 4),

            "camera_orientation_geom" to camOrientGeom,

            "body_displacement" to redondear(cuerpo.desplazamientoTotal, 4),
            "body_velocity_mean" to redondear(cuerpo.velocidadMedia, 4),
            "body_velocity_std" to redondear(cuerpo.velocidadStd, 4),
            "body_velocity_max" to redondear(cuerpo.velocidadMax, 4),
            "body_acceleration_mean" to redondear(bodyAccMean, 4),
            "body_acceleration_std" to redondear(bodyAccStd, 4),
            "movement_variability_score" to redondear(cuerpo.velocidadStd, 4),
            "movement_variance" to redondear(cuerpo.velocidadStd * cuerpo.velocidadStd, 6),

            "left_wrist_velocity" to redondear(munecaIzq.velocidadMedia, 4),
            "left_wrist_velocity_mean" to redondear(munecaIzq.velocidadMedia, 4),
            "right_wrist_velocity" to redondear(munecaDer.velocidadMedia, 4),
            "right_wrist_velocity_mean" to redondear(munecaDer.velocidadMedia, 4),
            "average_wrist_velocity" to redondear(avgWristVel, 4),
            "left_elbow_velocity_mean" to redondear(codoIzq.velocidadMedia, 4),
            "right_elbow_velocity_mean" to redondear(codoDer.velocidadMedia, 4),
            "gesture_velocity_mean" to redondear(avgWristVel, 4),
            "gesture_velocity_std" to redondear(avgWristStd, 4),
            "gesture_amplitude" to redondear(gestureAmp, 4),
            "gesture_frequency" to gestureFreq,

            "posture_change_count" to cambiosPostura.toDouble(),
            "posture_changes_per_minute" to cambiosPorMinuto,

            "visibility_ratio" to redondear(meanVis, 4),
            "reliability_score" to redondear(relScore, 4)
        )
    }

    /**
     * Replica `TrainingDatasetPreparer.extraer_features_landmark` / `live.extract_lm_features_from_df`
     * sobre los landmarks crudos (sin suavizado EMA), usando desviación estándar muestral (`ddof=1`, igual que pandas).
     */
    private fun calcularEstadisticasLandmarks(frames: List<FrameLandmarksDivlara>): Map<String, Double> {
        val indicesFramesConDatos = HashSet<Int>()
        var sumaVisGlobal = 0.0
        var conteoVisGlobal = 0
        val gruposPermitidos = HashMap<Int, AcumuladorLandmark>()

        for (frame in frames) {
            var frameConDatoValido = false
            for (lm in frame.landmarks) {
                if (!lm.x.isFinite() || !lm.y.isFinite() || !lm.z.isFinite()) continue
                frameConDatoValido = true
                val vis = if (lm.visibility.isFinite()) lm.visibility else 0.0
                sumaVisGlobal += vis
                conteoVisGlobal++
                if (lm.id in CONJUNTO_IDS_LANDMARKS_ESTADISTICAS) {
                    val acc = gruposPermitidos.getOrPut(lm.id) { AcumuladorLandmark() }
                    acc.xs.add(lm.x)
                    acc.ys.add(lm.y)
                    acc.zs.add(lm.z)
                    acc.visibilidades.add(vis)
                }
            }
            if (frameConDatoValido) {
                indicesFramesConDatos.add(frame.indiceFrame)
            }
        }

        if (conteoVisGlobal == 0) {
            return mapOf(
                "lm_num_frames" to 0.0,
                "lm_vis_global" to 0.0
            )
        }

        val salida = LinkedHashMap<String, Double>(gruposPermitidos.size * 7 + 2)
        salida["lm_num_frames"] = indicesFramesConDatos.size.toDouble()

        for (id in IDS_LANDMARKS_ESTADISTICAS) {
            val acc = gruposPermitidos[id] ?: continue
            salida["lm_${id}_x_mean"] = mediaOZero(acc.xs)
            salida["lm_${id}_x_std"] = desvEstandarMuestral(acc.xs)
            salida["lm_${id}_y_mean"] = mediaOZero(acc.ys)
            salida["lm_${id}_y_std"] = desvEstandarMuestral(acc.ys)
            salida["lm_${id}_z_mean"] = mediaOZero(acc.zs)
            salida["lm_${id}_z_std"] = desvEstandarMuestral(acc.zs)
            salida["lm_${id}_vis_mean"] = mediaOZero(acc.visibilidades)
        }
        salida["lm_vis_global"] = sumaVisGlobal / conteoVisGlobal.toDouble()
        return salida
    }

    private fun calcularCinematica(posiciones: List<Punto2D>, fps: Double): EstadisticasCinematica {
        if (posiciones.size < 2) return EstadisticasCinematica(0.0, 0.0, 0.0, 0.0)
        val desplazamientos = ArrayList<Double>(posiciones.size - 1)
        val velocidades = ArrayList<Double>(posiciones.size - 1)
        var total = 0.0
        var velMax = 0.0

        for (i in 1 until posiciones.size) {
            val d = distancia2D(posiciones[i], posiciones[i - 1])
            desplazamientos.add(d)
            total += d
            val v = d * fps
            velocidades.add(v)
            if (v > velMax) velMax = v
        }
        return EstadisticasCinematica(
            desplazamientoTotal = total,
            velocidadMedia = mediaOZero(velocidades),
            velocidadStd = desvEstandarPoblacional(velocidades),
            velocidadMax = velMax
        )
    }

    private fun calcularAceleracion(posiciones: List<Punto2D>, fps: Double): Pair<Double, Double> {
        if (posiciones.size < 3) return 0.0 to 0.0
        val vels = DoubleArray(posiciones.size - 1) { i ->
            distancia2D(posiciones[i + 1], posiciones[i]) * fps
        }
        val accs = ArrayList<Double>(vels.size - 1)
        for (i in 1 until vels.size) {
            accs.add(abs(vels[i] - vels[i - 1]) * fps)
        }
        return mediaOZero(accs) to desvEstandarPoblacional(accs)
    }

    private fun contarCambiosPostura(angulosLaterales: List<Double>, fps: Double): Int {
        if (angulosLaterales.size < 6) return 0
        val largoVentana = max(2, (0.6 * fps).toInt())
        val mediasCorridas = ArrayList<Double>()
        var inicio = 0
        while (inicio <= angulosLaterales.size - largoVentana) {
            var suma = 0.0
            for (j in inicio until inicio + largoVentana) suma += angulosLaterales[j]
            mediasCorridas.add(suma / largoVentana)
            inicio += largoVentana
        }
        var cambios = 0
        for (i in 1 until mediasCorridas.size) {
            if (abs(mediasCorridas[i] - mediasCorridas[i - 1]) > UMBRAL_CAMBIO_POSTURA_GRADOS) {
                cambios++
            }
        }
        return cambios
    }

    private fun calcularPersistenciaCruceBrazos(acc: AcumuladoresVisuales, fps: Double): Pair<Double, Double> {
        var racha = 0
        var maxRacha = 0
        for (bandera in acc.banderasCruceInstantaneo) {
            if (bandera) {
                racha++
                if (racha > maxRacha) maxRacha = racha
            } else {
                racha = 0
            }
        }
        val durSeg = redondear(maxRacha / fps, 2)
        var puntaje = if (acc.puntajesCruceBrazos.isNotEmpty()) {
            redondear(mediaOZero(acc.puntajesCruceBrazos), 4)
        } else {
            0.0
        }
        if (durSeg < DURACION_MIN_CRUCE_SEG) {
            puntaje = min(puntaje, TOPE_CRUCE_CORTO)
        }
        return puntaje to durSeg
    }

    private fun calcularPersistenciaApoyoMesa(valoresMesa: List<Double>, fps: Double): Double {
        if (valoresMesa.isEmpty()) return 0.0
        var racha = 0
        var maxRacha = 0
        for (valor in valoresMesa) {
            if (valor > UMBRAL_MESA_FRAME) {
                racha++
                if (racha > maxRacha) maxRacha = racha
            } else {
                racha = 0
            }
        }
        val durSeg = maxRacha / fps
        return if (durSeg >= DURACION_MIN_MESA_SEG) redondear(mediaOZero(valoresMesa), 4) else 0.0
    }

    private fun calcularFrecuenciaGestos(posicionesMunecaIzq: List<Punto2D>, fps: Double, durSeg: Double): Double {
        if (posicionesMunecaIzq.size < 5) return 0.0
        val vels = DoubleArray(posicionesMunecaIzq.size - 1) { i ->
            distancia2D(posicionesMunecaIzq[i + 1], posicionesMunecaIzq[i]) * fps
        }
        var picos = 0
        for (i in 1 until vels.size - 1) {
            if (vels[i] > vels[i - 1] && vels[i] > vels[i + 1] && vels[i] > UMBRAL_PICO_GESTO) {
                picos++
            }
        }
        return redondear(picos * (60.0 / durSeg), 1)
    }

    private fun calcularAperturaPromedioBrazo(distMuneca: List<Double>, distCodo: List<Double>): Double {
        val n = min(distMuneca.size, distCodo.size)
        if (n == 0) return 0.0
        var suma = 0.0
        for (i in 0 until n) {
            suma += (distMuneca[i] + distCodo[i]) / 2.0
        }
        return suma / n.toDouble()
    }

    private data class Punto2D(val x: Double, val y: Double)

    private data class PuntoLandmark(
        val x: Double,
        val y: Double,
        val z: Double,
        val vis: Double,
        val wx: Double,
        val wy: Double,
        val wz: Double
    )

    private data class EstadisticasCinematica(
        val desplazamientoTotal: Double,
        val velocidadMedia: Double,
        val velocidadStd: Double,
        val velocidadMax: Double
    )

    private class AcumuladorLandmark {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        val zs = ArrayList<Double>()
        val visibilidades = ArrayList<Double>()
    }

    private class AcumuladoresVisuales {
        val offsetsCentroX = ArrayList<Double>()
        val offsetsCentroY = ArrayList<Double>()
        val offsetsCentroAbs = ArrayList<Double>()

        val angulosHombros = ArrayList<Double>()
        val escalasHombros = ArrayList<Double>()
        val asimetriasAlturaHombros = ArrayList<Double>()

        val angulosLateralesTorso = ArrayList<Double>()
        val angulosProfundidadTorso = ArrayList<Double>()
        val confianzasProfundidadTorso = ArrayList<Double>()

        val distanciasCodosNorm = ArrayList<Double>()
        val distanciasMunecasNorm = ArrayList<Double>()
        val distanciasMunecaIzqTorso = ArrayList<Double>()
        val distanciasMunecaDerTorso = ArrayList<Double>()
        val distanciasCodoIzqTorso = ArrayList<Double>()
        val distanciasCodoDerTorso = ArrayList<Double>()
        val puntajesAperturaTorso = ArrayList<Double>()

        val puntajesCruceBrazos = ArrayList<Double>()
        val banderasCruceInstantaneo = ArrayList<Boolean>()

        val alturasCodoIzq = ArrayList<Double>()
        val alturasCodoDer = ArrayList<Double>()
        val asimetriasAlturaCodos = ArrayList<Double>()
        val alturasMunecaIzq = ArrayList<Double>()
        val alturasMunecaDer = ArrayList<Double>()
        val asimetriasAlturaMunecas = ArrayList<Double>()
        val asimetriasAlturaBrazos = ArrayList<Double>()

        val manosSobreHombros = ArrayList<Double>()
        val manosNivelPecho = ArrayList<Double>()
        val manosTorsoInferior = ArrayList<Double>()

        val angulosAntebrazoIzq = ArrayList<Double>()
        val angulosAntebrazoDer = ArrayList<Double>()
        val horizAntebrazoIzq = ArrayList<Double>()
        val horizAntebrazoDer = ArrayList<Double>()

        val apoyoMesaAmbos = ArrayList<Double>()
        val apoyoMesaIzq = ArrayList<Double>()
        val apoyoMesaDer = ArrayList<Double>()

        val orientacionesCamaraGeom = ArrayList<Double>()

        val posicionesCentroHombros = ArrayList<Punto2D>()
        val posicionesNariz = ArrayList<Punto2D>()
        val posicionesMunecaIzq = ArrayList<Punto2D>()
        val posicionesMunecaDer = ArrayList<Punto2D>()
        val posicionesCodoIzq = ArrayList<Punto2D>()
        val posicionesCodoDer = ArrayList<Punto2D>()

        val visibilidadesFrames = ArrayList<Double>()
        val visibilidadesHombros = ArrayList<Double>()
        val visibilidadesCaderas = ArrayList<Double>()
        val visibilidadesManoIzq = ArrayList<Double>()
        val visibilidadesManoDer = ArrayList<Double>()
    }

    private fun distancia2D(a: Punto2D, b: Punto2D): Double = hypot(a.x - b.x, a.y - b.y)

    private fun distanciaPuntoASegmento(p: Punto2D, a: Punto2D, b: Punto2D): Double {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val abLenSq = abX * abX + abY * abY
        if (abLenSq < EPSILON_SEGMENTO) {
            return distancia2D(p, a)
        }
        val apX = p.x - a.x
        val apY = p.y - a.y
        val t = ((apX * abX + apY * abY) / abLenSq).coerceIn(0.0, 1.0)
        val closestX = a.x + t * abX
        val closestY = a.y + t * abY
        return hypot(p.x - closestX, p.y - closestY)
    }

    private fun mediaOZero(valores: List<Double>): Double {
        if (valores.isEmpty()) return 0.0
        var suma = 0.0
        for (v in valores) suma += v
        return suma / valores.size.toDouble()
    }

    /** Varianza poblacional (`ddof=0`), equivalente a `numpy.var`. */
    private fun varianzaPoblacional(valores: List<Double>): Double {
        if (valores.isEmpty()) return 0.0
        val media = mediaOZero(valores)
        var suma = 0.0
        for (v in valores) {
            val d = v - media
            suma += d * d
        }
        return suma / valores.size.toDouble()
    }

    /** Desviación estándar poblacional (`ddof=0`), equivalente a `numpy.std`. */
    private fun desvEstandarPoblacional(valores: List<Double>): Double = sqrt(varianzaPoblacional(valores))

    /** Desviación estándar muestral (`ddof=1`), equivalente a `pandas.Series.std()`. */
    private fun desvEstandarMuestral(valores: List<Double>): Double {
        if (valores.size <= 1) return 0.0
        val media = mediaOZero(valores)
        var suma = 0.0
        for (v in valores) {
            val d = v - media
            suma += d * d
        }
        return sqrt(suma / (valores.size - 1).toDouble())
    }

    /** Redondeo bancario (`HALF_EVEN`) equivalente a `round(x, ndigits)` de Python y `np.round`. */
    private fun redondear(valor: Double, decimales: Int): Double {
        if (!valor.isFinite()) return 0.0
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_EVEN).toDouble()
    }
}
