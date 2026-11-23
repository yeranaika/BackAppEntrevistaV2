package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.MotorInferenciaDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ESQUEMAS.SolicitudRegistro
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contrato HTTP de `GET /api/v1/vision/reporte/{sesion}` y del guardado desde `POST /api/v1/vision/predecir`. */
class PruebaControladorReporteSimulacion {

    /** Modelo de mentira: la primera característica elige qué variable "se reconoce" (0 = ninguna, 1 = asentir, 2 = gesticular). */
    private val motorGuionado = object : MotorInferenciaDivlara {
        override fun cantidadFeaturesEsperadas(): Int? = null
        override fun predecir(features: List<Double>) = ResultadoPrediccionDivlara(
            VERSION_MODELO_DIVLARA,
            mapOf(
                "head_nodding" to if (features[0] == 1.0) 7.0 else 1.0,
                "gesture_activity" to if (features[0] == 2.0) 6.0 else 0.4
            )
        )
    }

    private fun solicitud(sesion: UUID?, ts: Long, escena: Int): String {
        val features = List(CANTIDAD_FEATURES_DIVLARA) { if (it == 0) escena.toString() else "0" }.joinToString(",")
        val sesionJson = sesion?.let { ""","sesionId":"$it"""" }.orEmpty()
        return """{"features":[$features],"timestampMs":$ts$sesionJson}"""
    }

    private fun ApplicationTestBuilder.prediccion(sistema: SistemaPrueba, token: String, sesion: UUID?, ts: Long, escena: Int): HttpResponse =
        runBlocking {
            client.post("/api/v1/vision/predecir") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(solicitud(sesion, ts, escena))
            }
        }

    private fun ApplicationTestBuilder.reporte(token: String?, sesion: Any): HttpResponse = runBlocking {
        client.get("/api/v1/vision/reporte/$sesion") { token?.let { bearerAuth(it) } }
    }

    private fun sistema() = SistemaPrueba(motorVision = motorGuionado).apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }

    private fun SistemaPrueba.usuarioConSesion(correo: String): Pair<String, UUID> = runBlocking {
        val token = usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1")).tokenAcceso
        token to entrevista.iniciar(usuarios.buscarPorCorreo(correo)!!.id, PedidoEntrevista(nombreCargo = "Backend Developer")).id
    }

    @Test
    fun `predecir con sesion guarda y el reporte sale como texto plano tabulado`() = testApplication {
        val sistema = sistema()
        application { sistema.montar(this) }
        val (token, sesion) = sistema.usuarioConSesion("ana@ejemplo.com")

        listOf(3_000L to 1, 4_000L to 1, 5_000L to 2, 6_000L to 0, 7_000L to 1, 8_000L to 1)
            .forEach { (ts, escena) ->
                val respuesta = prediccion(sistema, token, sesion, ts, escena)
                assertEquals(HttpStatusCode.OK, respuesta.status)
            }

        val respuesta = reporte(token, sesion)

        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertTrue(respuesta.headers["Content-Type"]!!.startsWith("text/plain"), "debe ser text/plain: ${respuesta.headers["Content-Type"]}")
        val lineas = respuesta.bodyAsText().lines()
        assertEquals("Sesion: $sesion   Duracion: 00:00:09   Predicciones: 6", lineas[1])
        assertEquals("00:00:00       00:00:03       (esperando ventana)              3.0s", lineas[5])
        assertEquals("00:00:03       00:00:05       head_nodding                     2.0s", lineas[6])
        assertEquals("00:00:07       00:00:09       head_nodding                     2.0s", lineas[9])
        assertEquals("head_nodding                        2        4.0      2.0      2.0", lineas[13])
    }

    @Test
    fun `predecir sin sesion sigue funcionando igual y no guarda nada`() = testApplication {
        val sistema = sistema()
        application { sistema.montar(this) }
        val (token, sesion) = sistema.usuarioConSesion("ana@ejemplo.com")

        assertEquals(HttpStatusCode.OK, prediccion(sistema, token, null, 1_000, 1).status)

        assertTrue(reporte(token, sesion).bodyAsText().endsWith("sin predicciones registradas para esta sesion\n"))
    }

    @Test
    fun `sin datos el reporte responde 200 con un mensaje claro`() = testApplication {
        val sistema = sistema()
        application { sistema.montar(this) }
        val (token, sesion) = sistema.usuarioConSesion("ana@ejemplo.com")

        val respuesta = reporte(token, sesion)

        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals("== REPORTE DE SIMULACION ==\nSesion: $sesion\n\nsin predicciones registradas para esta sesion\n", respuesta.bodyAsText())
    }

    @Test
    fun `exige sesion, oculta las entrevistas ajenas y valida los ids`() = testApplication {
        val sistema = sistema()
        application { sistema.montar(this) }
        val (token, sesion) = sistema.usuarioConSesion("ana@ejemplo.com")
        val (tokenBeto, _) = sistema.usuarioConSesion("beto@ejemplo.com")

        assertEquals(HttpStatusCode.Unauthorized, reporte(null, sesion).status)
        assertEquals(HttpStatusCode.NotFound, reporte(tokenBeto, sesion).status, "la sesion de otra persona no existe para Beto")
        assertEquals(HttpStatusCode.NotFound, reporte(token, UUID.randomUUID()).status)
        assertEquals(HttpStatusCode.BadRequest, reporte(token, "no-es-uuid").status)
        assertEquals(HttpStatusCode.NotFound, prediccion(sistema, tokenBeto, sesion, 1_000, 1).status, "Beto no puede escribir en la sesion de Ana")
        assertEquals(HttpStatusCode.BadRequest, prediccion(sistema, token, sesion, -5, 1).status)
        assertTrue(reporte(token, sesion).bodyAsText().endsWith("sin predicciones registradas para esta sesion\n"))
    }
}
