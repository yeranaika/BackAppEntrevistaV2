package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.ConfiguracionMotorVision
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ERRORES.ErrorServicioExterno
import ESQUEMAS.SolicitudPrediccionVision
import INTEGRACIONES.CODIGO_MOTOR_NO_DISPONIBLE
import INTEGRACIONES.ClienteMotorVision
import PRUEBAS.DOBLES.fallaCon
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/** D47: despertar bajo demanda (Wake-on-LAN) del motor, sin red real (MockEngine) y con esperas de 1 ms. */
class PruebaDespertarMotorVision {

    private val urlMotor = "http://motor.test:8094/predict"
    private val urlWol = "http://nuc.test:17474/wol"

    private fun solicitud() = SolicitudPrediccionVision(List(CANTIDAD_FEATURES_DIVLARA) { 0.5 })

    private fun config(wol: String) =
        ConfiguracionMotorVision(urlMotor, tiempoMaximoMs = 1_000, wolUrl = wol, wolToken = "secreto", esperasDespertarMs = listOf(1L, 1L, 1L))

    /** [motorArriba] decide si el motor responde; el WOL registra sus cabeceras y, si [alDespertar], levanta el motor. */
    private class Escenario(val motorArriba: AtomicBoolean, val alDespertar: Boolean) {
        val llamadasMotor = AtomicInteger()
        val tokensWol = CopyOnWriteArrayList<String?>()
        val motor = MockEngine { peticion ->
            if (peticion.url.host == "nuc.test") {
                tokensWol += peticion.headers["X-Wol-Token"]
                if (alDespertar) motorArriba.set(true)
                respond("ok", HttpStatusCode.OK)
            } else {
                llamadasMotor.incrementAndGet()
                if (!motorArriba.get()) throw IOException("sin ruta al motor")
                respond(
                    """{"versionModelo":"$VERSION_MODELO_DIVLARA","predicciones":{"head_nodding":3.5},"escala":"$ESCALA_MODELO_DIVLARA"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    }

    @Test
    fun `sin WOL_SERVICE_URL el comportamiento es el de siempre y no se envia nada`() = runBlocking {
        val e = Escenario(AtomicBoolean(false), alDespertar = true)
        ClienteMotorVision(config(""), e.motor).use { cliente ->
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { cliente.predecir(solicitud()) }
        }
        assertEquals(2, e.llamadasMotor.get(), "1 llamada + 1 reintento, como antes")
        assertEquals(0, e.tokensWol.size)
    }

    @Test
    fun `si el motor no se alcanza envia el WOL con el token y reintenta hasta que responde`() = runBlocking {
        val e = Escenario(AtomicBoolean(false), alDespertar = true)
        ClienteMotorVision(config(urlWol), e.motor).use { cliente ->
            assertEquals(3.5, cliente.predecir(solicitud()).predicciones["head_nodding"])
        }
        assertEquals(listOf<String?>("secreto"), e.tokensWol.toList())
    }

    @Test
    fun `si el WOL no despierta al motor agota los 3 reintentos y devuelve el error del motor`() = runBlocking {
        val e = Escenario(AtomicBoolean(false), alDespertar = false)
        ClienteMotorVision(config(urlWol), e.motor).use { cliente ->
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { cliente.predecir(solicitud()) }
        }
        assertEquals(1, e.tokensWol.size)
        assertEquals(2 + 3 * 2, e.llamadasMotor.get(), "intento inicial + 3 reintentos, cada uno con su reintento interno")
    }

    @Test
    fun `no reenvia el WOL si ya se envio hace menos de 60 s`() = runBlocking {
        val e = Escenario(AtomicBoolean(false), alDespertar = false)
        ClienteMotorVision(config(urlWol), e.motor, reloj = { 1_000_000L }).use { cliente ->
            repeat(2) { fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { cliente.predecir(solicitud()) } }
        }
        assertEquals(1, e.tokensWol.size)
    }

    @Test
    fun `un 5xx del motor encendido no dispara el WOL`() = runBlocking {
        val tokens = CopyOnWriteArrayList<String?>()
        val motor = MockEngine { peticion ->
            if (peticion.url.host == "nuc.test") tokens += peticion.headers["X-Wol-Token"]
            respond("{}", HttpStatusCode.InternalServerError)
        }
        ClienteMotorVision(config(urlWol), motor).use { cliente ->
            fallaCon<ErrorServicioExterno>(CODIGO_MOTOR_NO_DISPONIBLE) { cliente.predecir(solicitud()) }
        }
        assertEquals(0, tokens.size)
    }
}
