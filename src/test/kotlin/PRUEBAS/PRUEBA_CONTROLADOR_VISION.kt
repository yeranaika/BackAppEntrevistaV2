package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.FRAMES_VENTANA_DIVLARA
import ESQUEMAS.FrameLandmarksEntradaVision
import ESQUEMAS.LandmarkEntradaVision
import ESQUEMAS.SolicitudPrediccionVision
import ESQUEMAS.SolicitudRegistro
import PRUEBAS.DOBLES.SistemaPrueba
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato HTTP de `POST /api/v1/vision/predecir` en el backend: sesión, validación y errores. La paridad numérica con el
 * modelo E14 (golden) se prueba en el motor (`:motor-vision`) y la cadena completa en PruebaVisionDesacoplada (T3.11).
 */
class PruebaControladorVision {

    private val json = Json { ignoreUnknownKeys = true }
    private val ruta = "/api/v1/vision/predecir"

    private fun tokenDe(sistema: SistemaPrueba, correo: String = "vision@ejemplo.com"): String =
        runBlocking { sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1")).tokenAcceso }

    private fun crearSolicitudPoseBasica(incluirIris: Boolean = false): SolicitudPrediccionVision {
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

    @Test
    fun `exige autenticacion JWT`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }

        val respuesta = client.post(ruta) {
            contentType(ContentType.Application.Json)
            setBody("""{"features":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, respuesta.status)
    }

    @Test
    fun `sin el permiso de camara responde 403 y no predice (D38)`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "sin-camara@ejemplo.com")
        runBlocking {
            val id = sistema.usuarios.buscarPorCorreo("sin-camara@ejemplo.com")!!.id
            sistema.consentimiento.publicar("v1.1", "Consentimiento", "Texto", listOf("estadisticas_uso", "marketing", "analisis_conducta_camara"))
            sistema.consentimiento.otorgar(id, "v1.1", mapOf("estadisticas_uso" to true, "analisis_conducta_camara" to false), null)
        }

        val respuesta = client.post(ruta) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SolicitudPrediccionVision(List(CANTIDAD_FEATURES_DIVLARA) { 0.0 })))
        }

        assertEquals(HttpStatusCode.Forbidden, respuesta.status)
        assertEquals("permiso_camara_requerido", json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `rechaza con 400 cuando features tiene longitud distinta de 343`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema)

        for (cantidad in listOf(0, 10, CANTIDAD_FEATURES_DIVLARA - 1, CANTIDAD_FEATURES_DIVLARA + 1)) {
            val cuerpo = json.encodeToString(SolicitudPrediccionVision(List(cantidad) { 0.0 }))
            val respuesta = client.post(ruta) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(cuerpo)
            }
            assertEquals(HttpStatusCode.BadRequest, respuesta.status, "Esperado 400 para longitud=$cantidad")
            val error = json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
            assertEquals("cantidad_features_invalida", error)
        }
    }

    @Test
    fun `rechaza con 400 cuando faltan features y frames o el cuerpo JSON es invalido`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema)

        val sinEntradas = client.post(ruta) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, sinEntradas.status)
        assertEquals(
            "entrada_vision_requerida",
            json.parseToJsonElement(sinEntradas.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )

        val jsonRoto = client.post(ruta) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{malformado""")
        }
        assertEquals(HttpStatusCode.BadRequest, jsonRoto.status)
        assertEquals(
            "invalid_json",
            json.parseToJsonElement(jsonRoto.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `rechaza con 400 solicitudes P-1 ambiguas vacias con fps o landmarks invalidos`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema)

        val casos = listOf(
            """{"features":[0.0],"frames":[{"landmarks":[]}]}""" to "entrada_vision_ambigua",
            """{"frames":[]}""" to "ventana_landmarks_invalida",
            """{"frames":[{"landmarks":[]}],"fps":0}""" to "fps_invalido",
            """{"frames":[{"landmarks":[{"id":-1,"x":0.5,"y":0.5,"z":0.0}]}]}""" to "landmarks_invalidos"
        )
        for ((payload, codigoEsperado) in casos) {
            val respuesta = client.post(ruta) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.BadRequest, respuesta.status, "Payload: $payload")
            val error = json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
            assertEquals(codigoEsperado, error, "Payload: $payload")
        }
    }

    @Test
    fun `responde 503 modelo_no_disponible cuando el modelo no esta cargado`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema)

        val cuerpoFeatures = json.encodeToString(SolicitudPrediccionVision(List(CANTIDAD_FEATURES_DIVLARA) { 0.0 }))
        val respFeatures = client.post(ruta) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cuerpoFeatures)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, respFeatures.status)
        assertEquals(
            "modelo_no_disponible",
            json.parseToJsonElement(respFeatures.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )

        val cuerpoFrames = json.encodeToString(crearSolicitudPoseBasica())
        val respFrames = client.post(ruta) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(cuerpoFrames)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, respFrames.status)
        assertEquals(
            "modelo_no_disponible",
            json.parseToJsonElement(respFrames.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        )
    }
}
