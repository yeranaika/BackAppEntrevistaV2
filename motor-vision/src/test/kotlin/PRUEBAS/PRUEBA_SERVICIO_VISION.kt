package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.COLUMNAS_FEATURES_DIVLARA
import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.ExtractorFeaturesDivlara
import CONFIGURACION.FRAMES_VENTANA_DIVLARA
import CONFIGURACION.FrameLandmarksDivlara
import CONFIGURACION.LandmarkMediaPipe
import CONFIGURACION.MotorInferenciaDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.VentanaLandmarksDivlara
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.FrameLandmarksEntradaVision
import ESQUEMAS.SolicitudPrediccionVision
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.FRAMES_MAXIMOS_VENTANA_DIVLARA
import SERVICIOS.LANDMARKS_MAXIMOS_POR_FRAME
import SERVICIOS.ServicioVision
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PruebaServicioVision {

    @Serializable
    private data class GoldenDivlara(
        val features: List<Double>,
        val expected: Map<String, Double>,
        val tolerancia: Double
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
        val fps: Double,
        val frames: List<FrameGoldenJson>,
        @SerialName("expected_predicciones_onnx")
        val expectedPrediccionesOnnx: Map<String, Double>,
        @SerialName("tolerancia_onnx")
        val toleranciaOnnx: Double
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun cargarGoldenFeatures(): GoldenFeaturesJson {
        val ruta = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden_features.json")
        return json.decodeFromString(Files.readString(ruta))
    }

    private fun GoldenFeaturesJson.aVentana(): VentanaLandmarksDivlara = VentanaLandmarksDivlara(
        frames = frames.map { frame ->
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
        fps = fps
    )

    private fun crearFramePoseBasico(indiceFrame: Int, incluirIris: Boolean = false): FrameLandmarksDivlara {
        val lms = mutableListOf(
            LandmarkMediaPipe(0, 0.50, 0.25, -0.20, 0.95, 0.0, -0.45, -0.20),
            LandmarkMediaPipe(7, 0.45, 0.25, -0.15, 0.95, -0.08, -0.45, -0.15),
            LandmarkMediaPipe(8, 0.55, 0.25, -0.15, 0.95, 0.08, -0.45, -0.15),
            LandmarkMediaPipe(11, 0.40, 0.45, -0.10, 0.95, -0.18, -0.25, -0.10),
            LandmarkMediaPipe(12, 0.60, 0.45, -0.10, 0.95, 0.18, -0.25, -0.10),
            LandmarkMediaPipe(23, 0.43, 0.80, -0.02, 0.95, -0.12, 0.15, 0.0),
            LandmarkMediaPipe(24, 0.57, 0.80, -0.02, 0.95, 0.12, 0.15, 0.0)
        )
        if (incluirIris) {
            lms.add(LandmarkMediaPipe(468, 0.47, 0.23, -0.01, 0.95))
            lms.add(LandmarkMediaPipe(473, 0.53, 0.23, -0.01, 0.95))
        }
        return FrameLandmarksDivlara(indiceFrame = indiceFrame, landmarks = lms, timestampMs = indiceFrame * 100L)
    }

    @Test
    fun `rechaza longitud distinta de 343 features`() {
        val dirVacio = Files.createTempDirectory("vision-svc-empty-")
        try {
            val cargador = CargadorModeloDivlara(directorioBase = dirVacio).apply { cargarSiDisponible() }
            val servicio = ServicioVision(cargador)

            fallaCon<ErrorValidacion>("cantidad_features_invalida") { servicio.predecir(emptyList()) }
            fallaCon<ErrorValidacion>("cantidad_features_invalida") { servicio.predecir(List(CANTIDAD_FEATURES_DIVLARA - 1) { 0.0 }) }
            fallaCon<ErrorValidacion>("cantidad_features_invalida") { servicio.predecir(List(CANTIDAD_FEATURES_DIVLARA + 1) { 0.0 }) }
        } finally {
            dirVacio.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rechaza valores NaN o infinitos`() {
        val dirVacio = Files.createTempDirectory("vision-svc-nan-")
        try {
            val cargador = CargadorModeloDivlara(directorioBase = dirVacio).apply { cargarSiDisponible() }
            val servicio = ServicioVision(cargador)

            val conNan = MutableList(CANTIDAD_FEATURES_DIVLARA) { 0.0 }.apply { this[10] = Double.NaN }
            val conInf = MutableList(CANTIDAD_FEATURES_DIVLARA) { 0.0 }.apply { this[20] = Double.POSITIVE_INFINITY }

            fallaCon<ErrorValidacion>("features_invalidas") { servicio.predecir(conNan) }
            fallaCon<ErrorValidacion>("features_invalidas") { servicio.predecir(conInf) }
        } finally {
            dirVacio.toFile().deleteRecursively()
        }
    }

    @Test
    fun `responde modelo_no_disponible cuando faltan artefactos`() {
        val dirVacio = Files.createTempDirectory("vision-svc-missing-")
        try {
            val cargador = CargadorModeloDivlara(directorioBase = dirVacio).apply { cargarSiDisponible() }
            val servicio = ServicioVision(cargador)

            fallaCon<ErrorServicioExterno>("modelo_no_disponible") {
                servicio.predecir(List(CANTIDAD_FEATURES_DIVLARA) { 0.0 })
            }
        } finally {
            dirVacio.toFile().deleteRecursively()
        }
    }

    @Test
    fun `paridad contra golden json con modelo ONNX E14`() {
        val goldenPath = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden.json")
        val golden = json.decodeFromString<GoldenDivlara>(Files.readString(goldenPath))

        CargadorModeloDivlara().use { cargador ->
            cargador.cargarSiDisponible()
            val servicio = ServicioVision(cargador)
            val resultado = servicio.predecir(golden.features)

            assertEquals(VERSION_MODELO_DIVLARA, resultado.versionModelo)
            assertEquals(ESCALA_MODELO_DIVLARA, resultado.escala)
            assertEquals(golden.expected.keys, resultado.predicciones.keys)

            golden.expected.forEach { (variable, esperado) ->
                val actual = assertNotNull(resultado.predicciones[variable])
                val delta = abs(actual - esperado)
                assertTrue(delta <= golden.tolerancia, "$variable: esperado=$esperado, actual=$actual, delta=$delta")
            }
        }
    }

    @Test
    fun `paridad end-to-end con ventana P-1 de golden_features json`() {
        val golden = cargarGoldenFeatures()
        val ventana = golden.aVentana()

        CargadorModeloDivlara().use { cargador ->
            assertTrue(cargador.cargarSiDisponible().cargado)
            val servicio = ServicioVision(cargador, ExtractorFeaturesDivlara())
            val resultado = servicio.predecir(ventana)

            assertEquals(VERSION_MODELO_DIVLARA, resultado.versionModelo)
            assertEquals(ESCALA_MODELO_DIVLARA, resultado.escala)
            assertEquals(golden.expectedPrediccionesOnnx.keys, resultado.predicciones.keys)

            golden.expectedPrediccionesOnnx.forEach { (variable, esperado) ->
                val actual = assertNotNull(resultado.predicciones[variable], "Falta $variable")
                val delta = abs(actual - esperado)
                assertTrue(
                    delta <= golden.toleranciaOnnx,
                    "$variable: esperado=$esperado, actual=$actual, delta=$delta"
                )
            }
        }
    }

    @Test
    fun `aplica mediasFallback del scaler cuando solo llega pose33 sin iris y conserva iris reales cuando llegan`() {
        val mediasPrueba = DoubleArray(CANTIDAD_FEATURES_DIVLARA) { idx -> 10.0 + idx * 0.01 }
        var ultimoVectorCapturado: List<Double>? = null
        val motorEspia = object : MotorInferenciaDivlara {
            override fun cantidadFeaturesEsperadas(): Int = CANTIDAD_FEATURES_DIVLARA
            override fun mediasScaler(): DoubleArray = mediasPrueba.copyOf()
            override fun predecir(features: List<Double>): ResultadoPrediccionDivlara {
                ultimoVectorCapturado = features
                return ResultadoPrediccionDivlara(VERSION_MODELO_DIVLARA, mapOf("posture_stability" to 5.0))
            }
        }
        val servicio = ServicioVision(motorEspia, ExtractorFeaturesDivlara())

        // 1) Solo pose33 (sin 468/473): las columnas de iris deben tomar exactamente mediasPrueba (scaler.mean).
        val ventanaSoloPose = VentanaLandmarksDivlara((0 until FRAMES_VENTANA_DIVLARA).map { crearFramePoseBasico(it, incluirIris = false) })
        servicio.predecir(ventanaSoloPose)
        val vectorSoloPose = assertNotNull(ultimoVectorCapturado)
        assertEquals(CANTIDAD_FEATURES_DIVLARA, vectorSoloPose.size)
        for (columna in listOf("lm_468_x_mean", "lm_468_y_mean", "lm_468_vis_mean", "lm_473_x_mean", "lm_473_vis_mean")) {
            val idx = COLUMNAS_FEATURES_DIVLARA.indexOf(columna)
            assertEquals(mediasPrueba[idx], vectorSoloPose[idx], 1e-12, "Debe usar mediasFallback en $columna")
        }

        // 2) Pose33 + iris (468/473): las columnas de iris deben reflejar el valor real, no el fallback.
        val ventanaConIris = VentanaLandmarksDivlara((0 until FRAMES_VENTANA_DIVLARA).map { crearFramePoseBasico(it, incluirIris = true) })
        servicio.predecir(ventanaConIris)
        val vectorConIris = assertNotNull(ultimoVectorCapturado)
        val idxIrisIzqX = COLUMNAS_FEATURES_DIVLARA.indexOf("lm_468_x_mean")
        val idxIrisDerX = COLUMNAS_FEATURES_DIVLARA.indexOf("lm_473_x_mean")
        assertEquals(0.47, vectorConIris[idxIrisIzqX], 1e-9)
        assertEquals(0.53, vectorConIris[idxIrisDerX], 1e-9)
        assertNotEquals(mediasPrueba[idxIrisIzqX], vectorConIris[idxIrisIzqX])
    }

    @Test
    fun `valida entrada requerida ambigua y restricciones de ventana P-1`() {
        val dirVacio = Files.createTempDirectory("vision-svc-p1-val-")
        try {
            val cargador = CargadorModeloDivlara(directorioBase = dirVacio).apply { cargarSiDisponible() }
            val servicio = ServicioVision(cargador, ExtractorFeaturesDivlara())

            // Sin features ni frames
            fallaCon<ErrorValidacion>("entrada_vision_requerida") {
                servicio.predecir(SolicitudPrediccionVision())
            }
            // Ambas entradas a la vez
            fallaCon<ErrorValidacion>("entrada_vision_ambigua") {
                servicio.predecir(
                    SolicitudPrediccionVision(
                        features = List(CANTIDAD_FEATURES_DIVLARA) { 0.0 },
                        frames = listOf(FrameLandmarksEntradaVision(indiceFrame = 0))
                    )
                )
            }
            // Ventana vacía y excedida
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") {
                servicio.predecir(VentanaLandmarksDivlara(emptyList()))
            }
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") {
                val muchosFrames = (0..FRAMES_MAXIMOS_VENTANA_DIVLARA).map { FrameLandmarksDivlara(it) }
                servicio.predecir(VentanaLandmarksDivlara(muchosFrames))
            }
            // FPS inválido
            fallaCon<ErrorValidacion>("fps_invalido") {
                servicio.predecir(VentanaLandmarksDivlara(listOf(crearFramePoseBasico(0)), fps = 0.0))
            }
            fallaCon<ErrorValidacion>("fps_invalido") {
                servicio.predecir(VentanaLandmarksDivlara(listOf(crearFramePoseBasico(0)), fps = Double.NaN))
            }
            // Metadatos de ventana inválidos
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") {
                servicio.predecir(VentanaLandmarksDivlara(listOf(crearFramePoseBasico(0)), totalFramesMuestreados = 0))
            }
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") {
                servicio.predecir(VentanaLandmarksDivlara(listOf(crearFramePoseBasico(0)), ratioDeteccionPose = 1.5))
            }
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") {
                servicio.predecir(VentanaLandmarksDivlara(listOf(FrameLandmarksDivlara(indiceFrame = -1))))
            }
            // Landmarks inválidos
            fallaCon<ErrorValidacion>("landmarks_invalidos") {
                val frameIdNegativo = FrameLandmarksDivlara(0, listOf(LandmarkMediaPipe(-1, 0.5, 0.5, 0.0)))
                servicio.predecir(VentanaLandmarksDivlara(listOf(frameIdNegativo)))
            }
            fallaCon<ErrorValidacion>("landmarks_invalidos") {
                val frameNaN = FrameLandmarksDivlara(0, listOf(LandmarkMediaPipe(0, Double.NaN, 0.5, 0.0)))
                servicio.predecir(VentanaLandmarksDivlara(listOf(frameNaN)))
            }
            fallaCon<ErrorValidacion>("landmarks_invalidos") {
                val muchosLms = (0..LANDMARKS_MAXIMOS_POR_FRAME).map { LandmarkMediaPipe(it, 0.5, 0.5, 0.0) }
                servicio.predecir(VentanaLandmarksDivlara(listOf(FrameLandmarksDivlara(0, muchosLms))))
            }
        } finally {
            dirVacio.toFile().deleteRecursively()
        }
    }

    @Test
    fun `responde modelo_no_disponible con ventana P-1 valida cuando faltan artefactos`() {
        val dirVacio = Files.createTempDirectory("vision-svc-p1-missing-")
        try {
            val cargador = CargadorModeloDivlara(directorioBase = dirVacio).apply { cargarSiDisponible() }
            val servicio = ServicioVision(cargador, ExtractorFeaturesDivlara())
            val ventana = VentanaLandmarksDivlara((0 until FRAMES_VENTANA_DIVLARA).map { crearFramePoseBasico(it) })

            fallaCon<ErrorServicioExterno>("modelo_no_disponible") {
                servicio.predecir(ventana)
            }
        } finally {
            dirVacio.toFile().deleteRecursively()
        }
    }
}
