package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.EP_CPU
import CONFIGURACION.EP_CUDA
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.FRAMES_VENTANA_DIVLARA
import CONFIGURACION.LectorModeloOnnxDivlara
import CONFIGURACION.ProveedorEstadoModeloDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.configurarErroresMotor
import CONFIGURACION.configurarSerializacionMotor
import CONTROLADORES.controladorMotor
import ESQUEMAS.FrameLandmarksEntradaVision
import ESQUEMAS.LandmarkEntradaVision
import ESQUEMAS.SolicitudPrediccionVision
import SERVICIOS.ServicioVision
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * API del motor de inferencia (T3.11): `POST /predict` con el modelo E14 real. Es la misma paridad numérica (golden)
 * que antes se probaba dentro del backend: mismo código, otro proceso.
 */
class PruebaMotorVision {

    @Serializable
    private data class GoldenDivlara(
        val features: List<Double>,
        val expected: Map<String, Double>,
        val tolerancia: Double
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

    @Serializable
    private data class FrameGoldenJson(
        @SerialName("frame_index")
        val frameIndex: Int,
        @SerialName("timestamp_ms")
        val timestampMs: Long,
        val landmarks: List<LandmarkGoldenJson>
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

    private val json = Json { ignoreUnknownKeys = true }

    private fun ApplicationTestBuilder.montarMotor(cargador: CargadorModeloDivlara) {
        application {
            configurarSerializacionMotor()
            configurarErroresMotor()
            routing { controladorMotor(ServicioVision(cargador), cargador as ProveedorEstadoModeloDivlara) }
        }
    }

    private suspend fun ApplicationTestBuilder.predecir(cuerpo: String) = client.post("/predict") {
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    private fun codigoDeError(cuerpo: String): String? =
        json.parseToJsonElement(cuerpo).jsonObject["error"]?.jsonPrimitive?.content

    private fun solicitudPoseBasica(incluirIris: Boolean): SolicitudPrediccionVision {
        val frames = (0 until FRAMES_VENTANA_DIVLARA).map { idx ->
            val lms = mutableListOf(
                LandmarkEntradaVision(0, 0.50, 0.25, -0.20, 0.95, 0.0, -0.45, -0.20),
                LandmarkEntradaVision(7, 0.45, 0.25, -0.15, 0.95, -0.08, -0.45, -0.15),
                LandmarkEntradaVision(8, 0.55, 0.25, -0.15, 0.95, 0.08, -0.45, -0.15),
                LandmarkEntradaVision(11, 0.40, 0.45, -0.10, 0.95, -0.18, -0.25, -0.10),
                LandmarkEntradaVision(12, 0.60, 0.45, -0.10, 0.95, 0.18, -0.25, -0.10),
                LandmarkEntradaVision(23, 0.43, 0.80, -0.02, 0.95, -0.12, 0.15, 0.0),
                LandmarkEntradaVision(24, 0.57, 0.80, -0.02, 0.95, 0.12, 0.15, 0.0)
            )
            if (incluirIris) {
                lms.add(LandmarkEntradaVision(468, 0.47, 0.23, -0.01, 0.95))
                lms.add(LandmarkEntradaVision(473, 0.53, 0.23, -0.01, 0.95))
            }
            FrameLandmarksEntradaVision(indiceFrame = idx, landmarks = lms, timestampMs = idx * 100L)
        }
        return SolicitudPrediccionVision(frames = frames, fps = 10.0)
    }

    private fun verificarParidad(
        cuerpo: JsonObject,
        esperado: Map<String, Double>,
        tolerancia: Double,
        contexto: String
    ) {
        assertEquals(VERSION_MODELO_DIVLARA, cuerpo["versionModelo"]?.jsonPrimitive?.content)
        assertEquals(ESCALA_MODELO_DIVLARA, cuerpo["escala"]?.jsonPrimitive?.content)
        val predicciones = assertNotNull(cuerpo["predicciones"]?.jsonObject)
        assertEquals(esperado.keys, predicciones.keys)
        assertEquals(20, predicciones.size)
        esperado.forEach { (variable, valor) ->
            val real = assertNotNull(predicciones[variable]?.jsonPrimitive?.double, "Falta $variable")
            val delta = abs(real - valor)
            assertTrue(delta <= tolerancia, "$contexto: $variable esperado=$valor, real=$real, delta=$delta")
        }
    }

    @Test
    fun `predict con features de golden json da la paridad dentro de la tolerancia`() {
        val golden = json.decodeFromString<GoldenDivlara>(
            Files.readString(Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden.json"))
        )
        CargadorModeloDivlara().use { cargador ->
            assertTrue(cargador.cargarSiDisponible().cargado, "El modelo E14 debe estar cargado")
            testApplication {
                montarMotor(cargador)
                val respuesta = predecir(json.encodeToString(SolicitudPrediccionVision(golden.features)))
                assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
                verificarParidad(json.parseToJsonElement(respuesta.bodyAsText()).jsonObject, golden.expected, golden.tolerancia, "features")
            }
        }
    }

    @Test
    fun `predict con la ventana de golden_features json da la paridad ONNX en formato DivLara y canonico`() {
        val texto = Files.readString(Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden_features.json"))
        val golden = json.decodeFromString<GoldenFeaturesJson>(texto)
        val canonica = SolicitudPrediccionVision(
            frames = golden.frames.map { frame ->
                FrameLandmarksEntradaVision(
                    indiceFrame = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    landmarks = frame.landmarks.map {
                        LandmarkEntradaVision(it.landmarkId, it.x, it.y, it.z, it.visibility, it.worldX, it.worldY, it.worldZ)
                    }
                )
            },
            fps = golden.fps
        )
        CargadorModeloDivlara().use { cargador ->
            assertTrue(cargador.cargarSiDisponible().cargado, "El modelo E14 debe estar cargado")
            testApplication {
                montarMotor(cargador)
                for ((etiqueta, payload) in listOf("snake_case_divlara" to texto, "camelCase_canonico" to json.encodeToString(canonica))) {
                    val respuesta = predecir(payload)
                    assertEquals(HttpStatusCode.OK, respuesta.status, "$etiqueta: ${respuesta.bodyAsText()}")
                    verificarParidad(json.parseToJsonElement(respuesta.bodyAsText()).jsonObject, golden.expectedPrediccionesOnnx, golden.toleranciaOnnx, etiqueta)
                }
            }
        }
    }

    @Test
    fun `predict con pose33 e iris devuelve las 20 variables finitas`() {
        CargadorModeloDivlara().use { cargador ->
            assertTrue(cargador.cargarSiDisponible().cargado)
            testApplication {
                montarMotor(cargador)
                val respuesta = predecir(json.encodeToString(solicitudPoseBasica(incluirIris = true)))
                assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
                val predicciones = assertNotNull(json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["predicciones"]?.jsonObject)
                assertEquals(20, predicciones.size)
                assertTrue(predicciones.values.all { it.jsonPrimitive.double.isFinite() })
            }
        }
    }

    @Test
    fun `predict rechaza con 400 y el mismo codigo que el backend las entradas invalidas`() {
        CargadorModeloDivlara().use { cargador ->
            cargador.cargarSiDisponible()
            testApplication {
                montarMotor(cargador)
                val casos = listOf(
                    """{}""" to "entrada_vision_requerida",
                    """{"features":[0.0],"frames":[{"landmarks":[]}]}""" to "entrada_vision_ambigua",
                    """{"features":[0.0,1.0]}""" to "cantidad_features_invalida",
                    """{"frames":[]}""" to "ventana_landmarks_invalida",
                    """{"frames":[{"landmarks":[]}],"fps":0}""" to "fps_invalido",
                    """{"frames":[{"landmarks":[{"id":-1,"x":0.5,"y":0.5,"z":0.0}]}]}""" to "landmarks_invalidos",
                    """{malformado""" to "invalid_json"
                )
                for ((payload, esperado) in casos) {
                    val respuesta = predecir(payload)
                    assertEquals(HttpStatusCode.BadRequest, respuesta.status, "Payload: $payload")
                    assertEquals(esperado, codigoDeError(respuesta.bodyAsText()), "Payload: $payload")
                }
            }
        }
    }

    @Test
    fun `predict responde 503 modelo_no_disponible si el modelo no esta cargado y health lo informa`() {
        val dirVacio = Files.createTempDirectory("motor-sin-modelo")
        try {
            CargadorModeloDivlara(directorioBase = dirVacio).use { cargador ->
                cargador.cargarSiDisponible()
                testApplication {
                    montarMotor(cargador)
                    val respuesta = predecir(json.encodeToString(SolicitudPrediccionVision(List(CANTIDAD_FEATURES_DIVLARA) { 0.0 })))
                    assertEquals(HttpStatusCode.ServiceUnavailable, respuesta.status)
                    assertEquals("modelo_no_disponible", codigoDeError(respuesta.bodyAsText()))

                    val salud = json.parseToJsonElement(client.get("/health").bodyAsText()).jsonObject
                    assertEquals(false, salud["modeloDivlara"]?.jsonObject?.get("cargado")?.jsonPrimitive?.content?.toBoolean())
                    assertTrue("ep" !in salud, "Sin modelo cargado no debe informar ep")
                }
            }
        } finally {
            Files.deleteIfExists(dirVacio)
        }
    }

    @Test
    fun `health informa el modelo cargado y el proveedor de ejecucion activo`() {
        CargadorModeloDivlara().use { cargador ->
            cargador.cargarSiDisponible()
            testApplication {
                montarMotor(cargador)
                val salud = json.parseToJsonElement(client.get("/health").bodyAsText()).jsonObject
                assertEquals("OK", salud["status"]?.jsonPrimitive?.content)
                val ep = salud["ep"]?.jsonPrimitive?.content
                assertTrue(ep == EP_CUDA || ep == EP_CPU, "ep inesperado en /health: $ep")
                val modelo = assertNotNull(salud["modeloDivlara"]?.jsonObject)
                assertEquals(VERSION_MODELO_DIVLARA, modelo["version"]?.jsonPrimitive?.content)
                assertEquals("true", modelo["cargado"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `health informa ep cpu cuando se desactiva GPU`() {
        CargadorModeloDivlara(lector = LectorModeloOnnxDivlara(habilitarGpu = false)).use { cargador ->
            cargador.cargarSiDisponible()
            testApplication {
                montarMotor(cargador)
                val salud = json.parseToJsonElement(client.get("/health").bodyAsText()).jsonObject
                assertEquals("OK", salud["status"]?.jsonPrimitive?.content)
                assertEquals(EP_CPU, salud["ep"]?.jsonPrimitive?.content)
            }
        }
    }
}
