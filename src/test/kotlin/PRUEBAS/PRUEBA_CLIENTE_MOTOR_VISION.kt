package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.ConfiguracionMotorVision
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.FrameLandmarksEntradaVision
import ESQUEMAS.LandmarkEntradaVision
import ESQUEMAS.SolicitudPrediccionVision
import INTEGRACIONES.CODIGO_MOTOR_NO_DISPONIBLE
import INTEGRACIONES.ClienteMotorVision
import PRUEBAS.DOBLES.fallaCon
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cliente HTTP del motor (T3.11) sin red: MockEngine. Reintento, tiempo máximo, 503 y relevo de rechazos del motor. */
class PruebaClienteMotorVision {

    private val endpoint = "http://motor.test:8094/predict"
    private val cabecerasJson = headersOf(HttpHeaders.ContentType, "application/json")

    private val cuerpoOk =
        """{"versionModelo":"$VERSION_MODELO_DIVLARA","predicciones":{"head_nodding":3.5,"gesture_activity":1.25},"escala":"$ESCALA_MODELO_DIVLARA"}"""

    private fun MockRequestHandleScope.json(estado: HttpStatusCode, cuerpo: String): HttpResponseData =
        respond(cuerpo, estado, cabecerasJson)

    private fun solicitudFeatures() = SolicitudPrediccionVision(List(CANTIDAD_FEATURES_DIVLARA) { 0.5 })

    private fun solicitudVentana() = SolicitudPrediccionVision(
        frames = listOf(
            FrameLandmarksEntradaVision(
                indiceFrame = 0,
                landmarks = listOf(LandmarkEntradaVision(0, 0.5, 0.25, -0.2, 0.95, 0.0, -0.45, -0.2)),
                timestampMs = 0
            )
        ),
        fps = 10.0
    )

    private fun cliente(config: ConfiguracionMotorVision = ConfiguracionMotorVision(endpoint, tiempoMaximoMs = 1_000), manejador: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ClienteMotorVision(config, MockEngine { manejador(it) })

    @Test
    fun `reenvia la ventana al endpoint configurado y devuelve las predicciones sin cambios`() = runBlocking {
        val recibidas = CopyOnWriteArrayList<HttpRequestData>()
        val cuerpos = CopyOnWriteArrayList<String>()
        cliente { peticion ->
            recibidas += peticion
            cuerpos += String(peticion.body.toByteArray())
            json(HttpStatusCode.OK, cuerpoOk)
        }.use { motor ->
            val resultado = motor.predecir(solicitudVentana())

            assertEquals(VERSION_MODELO_DIVLARA, resultado.versionModelo)
            assertEquals(ESCALA_MODELO_DIVLARA, resultado.escala)
            assertEquals(mapOf("head_nodding" to 3.5, "gesture_activity" to 1.25), resultado.predicciones)

            assertEquals(1, recibidas.size)
            assertEquals(HttpMethod.Post, recibidas[0].method)
            assertEquals(endpoint, recibidas[0].url.toString())
            val enviado = Json.parseToJsonElement(cuerpos[0]).jsonObject
            assertEquals(1, enviado["frames"]?.jsonArray?.size, "La ventana viaja completa al motor")
        }
    }

    @Test
    fun `reintenta una vez ante un 500 y responde con el segundo intento`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente {
            if (llamadas.incrementAndGet() == 1) json(HttpStatusCode.InternalServerError, "{}") else json(HttpStatusCode.OK, cuerpoOk)
        }.use { motor ->
            assertEquals(3.5, motor.predecir(solicitudFeatures()).predicciones["head_nodding"])
            assertEquals(2, llamadas.get())
        }
    }

    @Test
    fun `si fallan el intento y el reintento responde 503 MOTOR_NO_DISPONIBLE`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente { llamadas.incrementAndGet(); json(HttpStatusCode.InternalServerError, "{}") }.use { motor ->
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) }
            assertEquals(2, llamadas.get(), "1 llamada + 1 reintento, no más")
            assertEquals("MOTOR_NO_DISPONIBLE", CODIGO_MOTOR_NO_DISPONIBLE)
        }
    }

    @Test
    fun `un motor que no contesta dentro del tiempo maximo se trata como no disponible`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente(ConfiguracionMotorVision(endpoint, tiempoMaximoMs = 150)) {
            llamadas.incrementAndGet()
            delay(2_000)
            json(HttpStatusCode.OK, cuerpoOk)
        }.use { motor ->
            val inicio = System.currentTimeMillis()
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) }
            assertEquals(2, llamadas.get())
            assertTrue(System.currentTimeMillis() - inicio < 1_500, "No espera a que el motor termine su respuesta lenta")
        }
    }

    @Test
    fun `una respuesta ilegible del motor cuenta como motor no disponible`() = runBlocking {
        cliente { json(HttpStatusCode.OK, "esto no es json") }.use { motor ->
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) }
        }
    }

    @Test
    fun `el rechazo 400 del motor se relaya con su codigo y no se reintenta`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente {
            llamadas.incrementAndGet()
            json(HttpStatusCode.BadRequest, """{"error":"cantidad_features_invalida","mensaje":"Se requieren exactamente 343 características"}""")
        }.use { motor ->
            fallaCon<ErrorValidacion>("cantidad_features_invalida") { motor.predecir(solicitudFeatures()) }
            assertEquals(1, llamadas.get())
        }
    }

    @Test
    fun `modelo_no_disponible del motor se relaya con su codigo y no se reintenta`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente {
            llamadas.incrementAndGet()
            json(HttpStatusCode.ServiceUnavailable, """{"error":"modelo_no_disponible","mensaje":"El modelo no está disponible"}""")
        }.use { motor ->
            fallaCon<ErrorServicioExterno>("modelo_no_disponible") { motor.predecir(solicitudFeatures()) }
            assertEquals(1, llamadas.get())
            assertEquals(false, motor.estado().cargado)
        }
    }

    @Test
    fun `una entrada invalida se rechaza con el mismo codigo sin llamar al motor`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente { llamadas.incrementAndGet(); json(HttpStatusCode.OK, cuerpoOk) }.use { motor ->
            fallaCon<ErrorValidacion>("entrada_vision_requerida") { motor.predecir(SolicitudPrediccionVision()) }
            fallaCon<ErrorValidacion>("cantidad_features_invalida") { motor.predecir(SolicitudPrediccionVision(listOf(1.0))) }
            fallaCon<ErrorValidacion>("ventana_landmarks_invalida") { motor.predecir(SolicitudPrediccionVision(frames = emptyList())) }
            assertEquals(0, llamadas.get())
        }
    }

    @Test
    fun `tras varias caidas seguidas el cortocircuito responde de inmediato sin llamar al motor`() = runBlocking {
        val llamadas = AtomicInteger()
        cliente { llamadas.incrementAndGet(); json(HttpStatusCode.BadGateway, "{}") }.use { motor ->
            repeat(5) { fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) } }
            val antes = llamadas.get()
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) }
            assertEquals(antes, llamadas.get(), "Con el circuito abierto ya no se llama al motor")
        }
    }

    @Test
    fun `el estado conocido pasa a cargado tras una prediccion y a error tras una caida`() = runBlocking {
        var responderBien = true
        cliente { if (responderBien) json(HttpStatusCode.OK, cuerpoOk) else json(HttpStatusCode.InternalServerError, "{}") }.use { motor ->
            motor.predecir(solicitudFeatures())
            assertTrue(motor.estado().cargado)
            assertEquals(VERSION_MODELO_DIVLARA, motor.estado().version)

            responderBien = false
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { motor.predecir(solicitudFeatures()) }
            assertEquals(false, motor.estado().cargado)
            assertEquals(CODIGO_MOTOR_NO_DISPONIBLE, motor.estado().error)
        }
    }

    @Test
    fun `la configuracion rechaza un endpoint que no es http`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConfiguracionMotorVision("localhost:8094/predict") }
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConfiguracionMotorVision("file:///etc/passwd") }
        ConfiguracionMotorVision("https://motor.ejemplo.com/predict")
    }
}
