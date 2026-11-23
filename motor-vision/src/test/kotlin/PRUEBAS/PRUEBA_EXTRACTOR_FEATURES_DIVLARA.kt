package PRUEBAS

import CONFIGURACION.ARCHIVO_META_DIVLARA
import CONFIGURACION.ARCHIVO_SCALER_DIVLARA
import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.COLUMNAS_FEATURES_DIVLARA
import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.EstadoCargaModeloDivlara
import CONFIGURACION.ExtractorFeaturesDivlara
import CONFIGURACION.FRAMES_VENTANA_DIVLARA
import CONFIGURACION.FrameLandmarksDivlara
import CONFIGURACION.LandmarkMediaPipe
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.VentanaLandmarksDivlara
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PruebaExtractorFeaturesDivlara {

    private val json = Json { ignoreUnknownKeys = true }
    private val extractor = ExtractorFeaturesDivlara()

    @Serializable
    private data class MetaJsonPrueba(
        @SerialName("feature_columns")
        val featureColumns: List<String>
    )

    @Serializable
    private data class ScalerJsonPrueba(
        val mean: List<Double>,
        val scale: List<Double>
    )

    @Serializable
    private data class LandmarkGoldenJson(
        @SerialName("landmark_id")
        val landmarkId: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        val visibility: Double,
        @SerialName("world_x")
        val worldX: Double,
        @SerialName("world_y")
        val worldY: Double,
        @SerialName("world_z")
        val worldZ: Double
    )

    @Serializable
    private data class FrameGoldenJson(
        @SerialName("frame_index")
        val frameIndex: Int,
        @SerialName("timestamp_ms")
        val timestampMs: Long,
        val landmarks: List<LandmarkGoldenJson>
    )

    @Serializable
    private data class GoldenFeaturesJson(
        @SerialName("clip_id")
        val clipId: String,
        val split: String,
        val fps: Double,
        @SerialName("ventana_frames")
        val ventanaFrames: Int,
        @SerialName("include_speech")
        val includeSpeech: Boolean,
        @SerialName("feature_columns")
        val featureColumns: List<String>,
        val frames: List<FrameGoldenJson>,
        @SerialName("expected_features")
        val expectedFeatures: List<Double>,
        @SerialName("expected_features_sin_scaler_fallback")
        val expectedFeaturesSinScalerFallback: List<Double>,
        @SerialName("expected_predicciones_onnx")
        val expectedPrediccionesOnnx: Map<String, Double>,
        val tolerancia: Double,
        @SerialName("tolerancia_onnx")
        val toleranciaOnnx: Double
    )

    private fun cargarGolden(): GoldenFeaturesJson {
        val ruta = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden_features.json")
        assertTrue(Files.isRegularFile(ruta), "Debe existir $ruta")
        return json.decodeFromString(Files.readString(ruta))
    }

    private fun cargarMediasScaler(): DoubleArray {
        val ruta = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, ARCHIVO_SCALER_DIVLARA)
        val scaler = json.decodeFromString<ScalerJsonPrueba>(Files.readString(ruta))
        return scaler.mean.toDoubleArray()
    }

    private fun indiceColumna(nombre: String): Int {
        val idx = COLUMNAS_FEATURES_DIVLARA.indexOf(nombre)
        assertTrue(idx >= 0, "Columna no encontrada en COLUMNAS_FEATURES_DIVLARA: $nombre")
        return idx
    }

    private fun crearFramePoseBasico(
        indiceFrame: Int,
        visibilidad: Double = 0.95,
        desplazamientoX: Double = 0.0,
        incluirIris: Boolean = false
    ): FrameLandmarksDivlara {
        val lms = mutableListOf(
            LandmarkMediaPipe(0, 0.50 + desplazamientoX, 0.25, -0.20, visibilidad, 0.0, -0.45, -0.20),
            LandmarkMediaPipe(7, 0.45 + desplazamientoX, 0.25, -0.15, visibilidad, -0.08, -0.45, -0.15),
            LandmarkMediaPipe(8, 0.55 + desplazamientoX, 0.25, -0.15, visibilidad, 0.08, -0.45, -0.15),
            LandmarkMediaPipe(11, 0.40 + desplazamientoX, 0.45, -0.10, visibilidad, -0.18, -0.25, -0.10),
            LandmarkMediaPipe(12, 0.60 + desplazamientoX, 0.45, -0.10, visibilidad, 0.18, -0.25, -0.10),
            LandmarkMediaPipe(13, 0.35 + desplazamientoX, 0.62, -0.12, visibilidad, -0.24, -0.05, -0.12),
            LandmarkMediaPipe(14, 0.65 + desplazamientoX, 0.62, -0.12, visibilidad, 0.24, -0.05, -0.12),
            LandmarkMediaPipe(15, 0.38 + desplazamientoX, 0.72, -0.18, visibilidad, -0.20, 0.05, -0.18),
            LandmarkMediaPipe(16, 0.62 + desplazamientoX, 0.72, -0.18, visibilidad, 0.20, 0.05, -0.18),
            LandmarkMediaPipe(23, 0.43 + desplazamientoX, 0.80, -0.02, visibilidad, -0.12, 0.15, 0.0),
            LandmarkMediaPipe(24, 0.57 + desplazamientoX, 0.80, -0.02, visibilidad, 0.12, 0.15, 0.0)
        )
        if (incluirIris) {
            lms.add(LandmarkMediaPipe(468, 0.47 + desplazamientoX, 0.23, -0.01, visibilidad))
            lms.add(LandmarkMediaPipe(473, 0.53 + desplazamientoX, 0.23, -0.01, visibilidad))
        }
        return FrameLandmarksDivlara(
            indiceFrame = indiceFrame,
            landmarks = lms,
            timestampMs = indiceFrame * 100L
        )
    }

    @Test
    fun `columnas declaradas coinciden 1 a 1 en orden exacto con meta json de E14`() {
        val rutaMeta = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, ARCHIVO_META_DIVLARA)
        val meta = json.decodeFromString<MetaJsonPrueba>(Files.readString(rutaMeta))

        assertEquals(CANTIDAD_FEATURES_DIVLARA, COLUMNAS_FEATURES_DIVLARA.size)
        assertEquals(meta.featureColumns, COLUMNAS_FEATURES_DIVLARA)
    }

    @Test
    fun `paridad exacta de las 343 features contra golden real del split test de DivLara`() {
        val golden = cargarGolden()
        assertEquals("test", golden.split)
        assertEquals(FRAMES_VENTANA_DIVLARA, golden.ventanaFrames)
        assertEquals(false, golden.includeSpeech)
        assertEquals(COLUMNAS_FEATURES_DIVLARA, golden.featureColumns)
        assertEquals(CANTIDAD_FEATURES_DIVLARA, golden.expectedFeatures.size)
        assertEquals(CANTIDAD_FEATURES_DIVLARA, golden.expectedFeaturesSinScalerFallback.size)

        val ventana = VentanaLandmarksDivlara(
            frames = golden.frames.map { frame ->
                FrameLandmarksDivlara(
                    indiceFrame = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    landmarks = frame.landmarks.map { lm ->
                        LandmarkMediaPipe(
                            id = lm.landmarkId,
                            x = lm.x,
                            y = lm.y,
                            z = lm.z,
                            visibility = lm.visibility,
                            worldX = lm.worldX,
                            worldY = lm.worldY,
                            worldZ = lm.worldZ
                        )
                    }
                )
            },
            fps = golden.fps
        )

        // 1. Paridad sin fallback de scaler (faltantes = 0.0)
        val sinFallback = extractor.extraerVector(ventana)
        assertEquals(CANTIDAD_FEATURES_DIVLARA, sinFallback.size)
        for (i in 0 until CANTIDAD_FEATURES_DIVLARA) {
            val esperado = golden.expectedFeaturesSinScalerFallback[i]
            val real = sinFallback[i]
            val delta = abs(real - esperado)
            assertTrue(
                delta <= golden.tolerancia,
                "Divergencia sin fallback en [${i}] ${COLUMNAS_FEATURES_DIVLARA[i]}: esperado=$esperado, real=$real, delta=$delta"
            )
        }

        // 2. Paridad con fallback de scaler.mean (equivalente exacto a live.py)
        val mediasScaler = cargarMediasScaler()
        val conFallback = extractor.extraerVector(ventana, mediasFallback = mediasScaler)
        for (i in 0 until CANTIDAD_FEATURES_DIVLARA) {
            val esperado = golden.expectedFeatures[i]
            val real = conFallback[i]
            val delta = abs(real - esperado)
            assertTrue(
                delta <= golden.tolerancia,
                "Divergencia con scaler.mean en [${i}] ${COLUMNAS_FEATURES_DIVLARA[i]}: esperado=$esperado, real=$real, delta=$delta"
            )
        }

        // 3. Paridad end-to-end: ventana de landmarks -> 343 features -> scaler + ONNX E14 -> 20 salidas
        CargadorModeloDivlara().use { cargador ->
            val estado = cargador.cargarSiDisponible()
            assertEquals(EstadoCargaModeloDivlara.CARGADO, estado.estado, "Error cargando E14: ${estado.error}")
            val resultado = cargador.predecir(conFallback.toList())
            assertEquals(golden.expectedPrediccionesOnnx.keys, resultado.predicciones.keys)
            golden.expectedPrediccionesOnnx.forEach { (variable, esperado) ->
                val real = assertNotNull(resultado.predicciones[variable])
                val delta = abs(real - esperado)
                assertTrue(
                    delta <= golden.toleranciaOnnx,
                    "Paridad ONNX end-to-end fuera de tolerancia en $variable: esperado=$esperado, real=$real, delta=$delta"
                )
            }
        }
    }

    @Test
    fun `ventana con landmarks de iris 468 y 473 calcula estadisticas muestrales ddof 1 y visibilidad global`() {
        val frames = listOf(
            crearFramePoseBasico(0, visibilidad = 0.9, desplazamientoX = 0.00, incluirIris = true),
            crearFramePoseBasico(1, visibilidad = 0.9, desplazamientoX = 0.02, incluirIris = true),
            crearFramePoseBasico(2, visibilidad = 0.9, desplazamientoX = 0.04, incluirIris = true)
        )
        val vector = extractor.extraerVector(VentanaLandmarksDivlara(frames))

        assertEquals(1.0, vector[indiceColumna("has_pose")])
        assertEquals(3.0, vector[indiceColumna("lm_num_frames")])
        assertEquals(0.49, vector[indiceColumna("lm_468_x_mean")], 1e-9)
        // std muestral (ddof=1) de [0.47, 0.49, 0.51] es sqrt(((0.02^2 + 0 + 0.02^2) / 2)) = 0.02
        assertEquals(0.02, vector[indiceColumna("lm_468_x_std")], 1e-9)
        assertEquals(0.55, vector[indiceColumna("lm_473_x_mean")], 1e-9)
        assertEquals(0.02, vector[indiceColumna("lm_473_x_std")], 1e-9)
        assertEquals(0.9, vector[indiceColumna("lm_468_vis_mean")], 1e-9)
        assertEquals(0.9, vector[indiceColumna("lm_vis_global")], 1e-9)
        assertTrue(vector.all { it.isFinite() })
    }

    @Test
    fun `caso borde ventana vacia produce has_pose 0 y vector finito sin fallar`() {
        val vector = extractor.extraerVector(VentanaLandmarksDivlara(emptyList()))

        assertEquals(CANTIDAD_FEATURES_DIVLARA, vector.size)
        assertEquals(0.0, vector[indiceColumna("has_pose")])
        assertEquals(0.0, vector[indiceColumna("pose_detection_ratio")])
        assertEquals(0.0, vector[indiceColumna("lm_num_frames")])
        assertEquals(0.0, vector[indiceColumna("lm_vis_global")])
        assertTrue(vector.all { it.isFinite() })
    }

    @Test
    fun `caso borde frames sin pose dejan has_pose 0 y pose_detection_ratio 0`() {
        val framesSinPose = (0 until FRAMES_VENTANA_DIVLARA).map { i ->
            FrameLandmarksDivlara(indiceFrame = i, landmarks = emptyList(), timestampMs = i * 100L)
        }
        val vector = extractor.extraerVector(VentanaLandmarksDivlara(framesSinPose))

        assertEquals(0.0, vector[indiceColumna("has_pose")])
        assertEquals(0.0, vector[indiceColumna("pose_detection_ratio")])
        assertEquals(0.0, vector[indiceColumna("lm_num_frames")])
        assertTrue(vector.all { it.isFinite() })

        // También cuando solo hay landmarks de iris (468/473) sin ningún landmark de pose (0..32)
        val framesSoloIris = (0 until 5).map { i ->
            FrameLandmarksDivlara(
                indiceFrame = i,
                landmarks = listOf(
                    LandmarkMediaPipe(468, 0.45, 0.25, 0.0, 0.95),
                    LandmarkMediaPipe(473, 0.55, 0.25, 0.0, 0.95)
                )
            )
        }
        val vectorSoloIris = extractor.extraerVector(VentanaLandmarksDivlara(framesSoloIris))
        assertEquals(0.0, vectorSoloIris[indiceColumna("has_pose")])
        assertEquals(0.0, vectorSoloIris[indiceColumna("pose_detection_ratio")])
        assertEquals(5.0, vectorSoloIris[indiceColumna("lm_num_frames")])
        assertEquals(0.45, vectorSoloIris[indiceColumna("lm_468_x_mean")], 1e-9)
        assertTrue(vectorSoloIris.all { it.isFinite() })
    }

    @Test
    fun `caso borde visibilidad baja invalida torso forward backward y reduce fiabilidad`() {
        val framesBajaVis = (0 until FRAMES_VENTANA_DIVLARA).map { i ->
            crearFramePoseBasico(i, visibilidad = 0.15)
        }
        val framesAltaVis = (0 until FRAMES_VENTANA_DIVLARA).map { i ->
            crearFramePoseBasico(i, visibilidad = 0.95)
        }

        val baja = extractor.extraerVector(VentanaLandmarksDivlara(framesBajaVis))
        val alta = extractor.extraerVector(VentanaLandmarksDivlara(framesAltaVis))

        assertEquals(1.0, baja[indiceColumna("has_pose")])
        assertEquals(0.15, baja[indiceColumna("visibility_ratio")], 1e-4)
        assertEquals(0.15, baja[indiceColumna("hand_visibility_ratio")], 1e-4)
        assertEquals(0.15, baja[indiceColumna("gesture_reliability")], 1e-4)
        assertEquals(0.15, baja[indiceColumna("torso_forward_backward_confidence")], 1e-3)
        assertEquals(0.0, baja[indiceColumna("torso_forward_backward_valid")])
        assertEquals(-1.0, baja[indiceColumna("torso_forward_backward_class")])
        assertEquals(0.15, baja[indiceColumna("lm_vis_global")], 1e-6)
        assertTrue(baja[indiceColumna("reliability_score")] < alta[indiceColumna("reliability_score")])
        assertEquals(1.0, alta[indiceColumna("torso_forward_backward_valid")])
        assertTrue(baja.all { it.isFinite() })
    }

    @Test
    fun `caso borde ventana incompleta con un frame o parcial calcula metricas estables y finitas`() {
        // 1 solo frame (degenerado: sin derivadas ni std muestral)
        val unFrame = extractor.extraerVector(
            VentanaLandmarksDivlara(
                frames = listOf(crearFramePoseBasico(0)),
                totalFramesMuestreados = FRAMES_VENTANA_DIVLARA
            )
        )
        assertEquals(1.0, unFrame[indiceColumna("has_pose")])
        assertEquals(0.05, unFrame[indiceColumna("pose_detection_ratio")], 1e-4)
        assertEquals(1.0, unFrame[indiceColumna("lm_num_frames")])
        assertEquals(0.0, unFrame[indiceColumna("head_velocity")])
        assertEquals(0.0, unFrame[indiceColumna("body_velocity_mean")])
        assertEquals(0.0, unFrame[indiceColumna("body_acceleration_mean")])
        assertEquals(0.0, unFrame[indiceColumna("lm_0_x_std")])
        assertEquals(0.0, unFrame[indiceColumna("torso_forward_backward_valid")])
        assertEquals(-1.0, unFrame[indiceColumna("torso_forward_backward_class")])
        assertTrue(unFrame.all { it.isFinite() })

        // Ventana de 20 slots con 15 frames con pose y 5 frames vacíos
        val mixta = (0 until FRAMES_VENTANA_DIVLARA).map { i ->
            if (i < 15) crearFramePoseBasico(i, desplazamientoX = i * 0.002)
            else FrameLandmarksDivlara(indiceFrame = i, landmarks = emptyList())
        }
        val vecMixto = extractor.extraerVector(VentanaLandmarksDivlara(mixta))
        assertEquals(1.0, vecMixto[indiceColumna("has_pose")])
        assertEquals(0.75, vecMixto[indiceColumna("pose_detection_ratio")], 1e-4)
        assertEquals(15.0, vecMixto[indiceColumna("lm_num_frames")])
        assertTrue(vecMixto[indiceColumna("body_displacement")] > 0.0)
        assertTrue(vecMixto.all { it.isFinite() })

        // Frame con nariz pero sin hombros (11/12 ausentes)
        val sinHombros = extractor.extraerVector(
            VentanaLandmarksDivlara(
                frames = listOf(
                    FrameLandmarksDivlara(
                        indiceFrame = 0,
                        landmarks = listOf(LandmarkMediaPipe(0, 0.5, 0.3, -0.1, 0.9))
                    )
                )
            )
        )
        assertEquals(1.0, sinHombros[indiceColumna("has_pose")])
        assertEquals(0.5, sinHombros[indiceColumna("upper_body_openness_score")])
        assertEquals(2.0, sinHombros[indiceColumna("camera_orientation_geom")])
        assertEquals(0.5, sinHombros[indiceColumna("lm_0_x_mean")], 1e-9)
        assertTrue(sinHombros.all { it.isFinite() })
    }

    @Test
    fun `valida argumentos invalidos y sanea coordenadas no finitas sin propagar NaN`() {
        assertFailsWith<IllegalArgumentException> {
            extractor.extraerVector(VentanaLandmarksDivlara(emptyList(), fps = 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            extractor.extraerVector(VentanaLandmarksDivlara(emptyList()), mediasFallback = DoubleArray(10))
        }

        val frameConNaN = FrameLandmarksDivlara(
            indiceFrame = 0,
            landmarks = listOf(
                LandmarkMediaPipe(0, Double.NaN, 0.3, 0.0, 0.9),
                LandmarkMediaPipe(11, 0.4, 0.5, 0.0, Double.POSITIVE_INFINITY),
                LandmarkMediaPipe(12, 0.6, 0.5, 0.0, 0.9)
            )
        )
        val vector = extractor.extraerVector(VentanaLandmarksDivlara(listOf(frameConNaN)))
        assertTrue(vector.all { it.isFinite() })
        // Verifica que la std muestral en un par de valores conocidos sea finita y exacta
        val par = listOf(
            FrameLandmarksDivlara(0, listOf(LandmarkMediaPipe(0, 0.4, 0.5, 0.0, 0.8))),
            FrameLandmarksDivlara(1, listOf(LandmarkMediaPipe(0, 0.6, 0.5, 0.0, 0.8)))
        )
        val vecPar = extractor.extraerVector(VentanaLandmarksDivlara(par))
        assertEquals(sqrt(0.02), vecPar[indiceColumna("lm_0_x_std")], 1e-9)
    }
}
