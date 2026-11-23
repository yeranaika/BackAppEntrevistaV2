package PRUEBAS

import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.ConfiguracionMotorVision
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.configurarErroresMotor
import CONFIGURACION.configurarSerializacionMotor
import CONTROLADORES.controladorMotor
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.ClienteMotorVision
import PRUEBAS.DOBLES.SistemaPrueba
import SERVICIOS.ServicioVision
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * Cadena real de T3.11: app (HTTP) → backend (valida sesión) → HTTP → motor con el modelo E14 de verdad (Netty en un
 * puerto libre) → mismo JSON de siempre. Verifica que separar el modelo en otro proceso no cambió la paridad (golden).
 */
class PruebaVisionDesacoplada {

    @Serializable
    private data class GoldenDivlara(val features: List<Double>, val expected: Map<String, Double>, val tolerancia: Double)

    @Serializable
    private data class GoldenVentana(
        @SerialName("expected_predicciones_onnx") val esperado: Map<String, Double>,
        @SerialName("tolerancia_onnx") val tolerancia: Double
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val ruta = "/api/v1/vision/predecir"

    private fun levantarMotor(cargador: CargadorModeloDivlara): EmbeddedServer<NettyApplicationEngine, *> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            configurarSerializacionMotor()
            configurarErroresMotor()
            routing { controladorMotor(ServicioVision(cargador), cargador) }
        }.start(wait = false)

    private fun urlDe(motor: EmbeddedServer<NettyApplicationEngine, *>): String =
        "http://127.0.0.1:${runBlocking { motor.engine.resolvedConnectors().first().port }}/predict"

    private fun golden(archivo: String) = Files.readString(Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, archivo))

    private fun verificar(cuerpo: String, esperado: Map<String, Double>, tolerancia: Double) {
        val obj = json.parseToJsonElement(cuerpo).jsonObject
        assertEquals(VERSION_MODELO_DIVLARA, obj["versionModelo"]?.jsonPrimitive?.content)
        assertEquals(ESCALA_MODELO_DIVLARA, obj["escala"]?.jsonPrimitive?.content)
        assertTrue(!obj["mensaje"]?.jsonPrimitive?.content.isNullOrBlank(), "El backend sigue agregando 'mensaje'")
        val predicciones = assertNotNull(obj["predicciones"]?.jsonObject)
        assertEquals(esperado.keys, predicciones.keys)
        assertEquals(20, predicciones.size)
        esperado.forEach { (variable, valor) ->
            val real = assertNotNull(predicciones[variable]?.jsonPrimitive?.double, "Falta $variable")
            assertTrue(abs(real - valor) <= tolerancia, "$variable: esperado=$valor, real=$real")
        }
    }

    @Test
    fun `backend a motor real devuelve las 20 filas con paridad golden para features y para la ventana`() {
        val features = json.decodeFromString<GoldenDivlara>(golden("golden.json"))
        val ventanaTexto = golden("golden_features.json")
        val ventana = json.decodeFromString<GoldenVentana>(ventanaTexto)

        CargadorModeloDivlara().use { cargador ->
            assertTrue(cargador.cargarSiDisponible().cargado)
            val motor = levantarMotor(cargador)
            try {
                ClienteMotorVision(ConfiguracionMotorVision(urlDe(motor), tiempoMaximoMs = 10_000)).use { cliente ->
                    testApplication {
                        val sistema = SistemaPrueba(predictorVision = cliente, modeloDivlara = cliente)
                        application { sistema.montar(this) }
                        val token = runBlocking { sistema.usuario.registrar(SolicitudRegistro("cadena@ejemplo.com", "Clave-segura-1")).tokenAcceso }

                        val cuerpoFeatures = """{"features":[${features.features.joinToString(",")}]}"""
                        for ((etiqueta, payload, esperado, tolerancia) in listOf(
                            Caso("features", cuerpoFeatures, features.expected, features.tolerancia),
                            Caso("ventana", ventanaTexto, ventana.esperado, ventana.tolerancia)
                        )) {
                            val respuesta = client.post(ruta) {
                                bearerAuth(token)
                                contentType(ContentType.Application.Json)
                                setBody(payload)
                            }
                            assertEquals(HttpStatusCode.OK, respuesta.status, "$etiqueta: ${respuesta.bodyAsText()}")
                            verificar(respuesta.bodyAsText(), esperado, tolerancia)
                        }
                    }
                }
            } finally {
                motor.stop(0, 0)
            }
        }
    }

    private data class Caso(val etiqueta: String, val payload: String, val esperado: Map<String, Double>, val tolerancia: Double)

    @Test
    fun `con el motor apagado el backend responde 503 MOTOR_NO_DISPONIBLE y sigue vivo`() {
        CargadorModeloDivlara().use { cargador ->
            cargador.cargarSiDisponible()
            val motor = levantarMotor(cargador)
            val url = urlDe(motor)
            motor.stop(0, 0) // el puerto queda sin nadie escuchando

            ClienteMotorVision(ConfiguracionMotorVision(url, tiempoMaximoMs = 3_000)).use { cliente ->
                testApplication {
                    val sistema = SistemaPrueba(predictorVision = cliente, modeloDivlara = cliente)
                    application { sistema.montar(this) }
                    val token = runBlocking { sistema.usuario.registrar(SolicitudRegistro("caido@ejemplo.com", "Clave-segura-1")).tokenAcceso }

                    val respuesta = client.post(ruta) {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody("""{"features":[${List(343) { "0.0" }.joinToString(",")}]}""")
                    }
                    assertEquals(HttpStatusCode.ServiceUnavailable, respuesta.status, respuesta.bodyAsText())
                    assertEquals("MOTOR_NO_DISPONIBLE", json.parseToJsonElement(respuesta.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content)
                }
            }
        }
    }
}
