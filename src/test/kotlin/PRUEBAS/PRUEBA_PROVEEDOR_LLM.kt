package PRUEBAS

import ERRORES.ErrorRespuestaExterna
import ERRORES.ErrorServicioExterno
import ESQUEMAS.EsquemaJsonLoteLlm
import INTEGRACIONES.ProveedorAnthropic
import INTEGRACIONES.ProveedorOpenAi
import INTEGRACIONES.SolicitudProveedorIa
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.fallaCon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Contrato HTTP con OpenAI y Anthropic, sin red (MockEngine). */
class PruebaProveedorLlm {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    private fun solicitud(cantidad: Int = 1, tipo: TipoPregunta = TipoPregunta.ABIERTA_TEXTO, modelo: String = "gpt-4o-mini") =
        SolicitudProveedorIa(
            prompt = "prompt de prueba",
            modelo = modelo,
            esquemaJson = EsquemaJsonLoteLlm.construir(cantidad, tipo),
            instruccionUsuario = "Genera las preguntas ahora."
        )

    /** Cliente que responde [cuerpo] y deja en [capturado] lo que se envió. */
    private fun cliente(cuerpo: String, estado: HttpStatusCode = HttpStatusCode.OK, capturado: StringBuilder = StringBuilder()) =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json(json) }
            engine {
                addHandler { request ->
                    capturado.append((request.body as TextContent).text)
                    respond(cuerpo, estado, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }
        }

    private fun clienteQueFalla(mensaje: String) = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(json) }
        engine { addHandler { throw IOException(mensaje) } }
    }

    private fun JsonObject.preguntasDelEsquema(): JsonObject =
        this["properties"]!!.jsonObject["preguntas"]!!.jsonObject

    @Test
    fun `OpenAI recibe un json_schema estricto y se leen los tokens`() = runBlocking<Unit> {
        val enviado = StringBuilder()
        val respuestaOpenAi = """{"choices":[{"finish_reason":"stop","message":{"content":"{\"preguntas\":[]}"}}],"usage":{"prompt_tokens":12,"completion_tokens":34}}"""
        val respuesta = ProveedorOpenAi("clave", cliente(respuestaOpenAi, capturado = enviado))
            .generar(solicitud(10, TipoPregunta.OPCION_MULTIPLE))

        val cuerpo = json.parseToJsonElement(enviado.toString()).jsonObject
        val formato = cuerpo["response_format"]!!.jsonObject["json_schema"]!!.jsonObject
        assertEquals("true", formato["strict"]!!.jsonPrimitive.content)
        val preguntas = formato["schema"]!!.jsonObject.preguntasDelEsquema()
        assertEquals(10, preguntas["minItems"]!!.jsonPrimitive.content.toInt())
        assertEquals(10, preguntas["maxItems"]!!.jsonPrimitive.content.toInt())
        val opciones = preguntas["items"]!!.jsonObject["properties"]!!.jsonObject["opciones"]!!.jsonObject
        assertEquals(4, opciones["minItems"]!!.jsonPrimitive.content.toInt())

        assertEquals("{\"preguntas\":[]}", respuesta.contenido)
        assertEquals(12, respuesta.tokensEntrada)
        assertEquals(34, respuesta.tokensSalida)
    }

    @Test
    fun `Anthropic recibe output_config con el schema y abierta exige opciones null`() = runBlocking<Unit> {
        val enviado = StringBuilder()
        val respuestaAnthropic = """{"content":[{"type":"text","text":"{\"preguntas\":[]}"}],"stop_reason":"end_turn","usage":{"input_tokens":7,"output_tokens":9}}"""
        val respuesta = ProveedorAnthropic("clave", cliente(respuestaAnthropic, capturado = enviado))
            .generar(solicitud(3, modelo = "claude-haiku-4-5"))

        val cuerpo = json.parseToJsonElement(enviado.toString()).jsonObject
        assertEquals("claude-haiku-4-5", cuerpo["model"]!!.jsonPrimitive.content)
        val esquema = cuerpo["output_config"]!!.jsonObject["format"]!!.jsonObject["schema"]!!.jsonObject
        val opciones = esquema.preguntasDelEsquema()["items"]!!.jsonObject["properties"]!!.jsonObject["opciones"]!!.jsonObject
        assertEquals("null", opciones["type"]!!.jsonPrimitive.content)
        assertEquals(7, respuesta.tokensEntrada)
        assertEquals(9, respuesta.tokensSalida)
    }

    @Test
    fun `sin API key responde provider_not_configured sin llamar a la red`() {
        val enviado = StringBuilder()
        fallaCon<ErrorServicioExterno>("provider_not_configured") {
            ProveedorOpenAi("", cliente("{}", capturado = enviado)).generar(solicitud())
        }
        assertTrue(enviado.isEmpty())
    }

    @Test
    fun `respuesta truncada y rechazo del modelo son errores tipados`() {
        fallaCon<ErrorRespuestaExterna>("provider_truncated") {
            ProveedorOpenAi("clave", cliente("""{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}""")).generar(solicitud())
        }
        fallaCon<ErrorRespuestaExterna>("provider_refusal") {
            ProveedorAnthropic("clave", cliente("""{"content":[{"type":"text","text":"no"}],"stop_reason":"refusal"}"""))
                .generar(solicitud(modelo = "claude-haiku-4-5"))
        }
    }

    @Test
    fun `fallas de red y respuestas HTTP de error no filtran mensajes internos`() {
        val deRed = assertFailsWith<ErrorRespuestaExterna> {
            runBlocking { ProveedorOpenAi("clave", clienteQueFalla("socket secreto")).generar(solicitud()) }
        }
        assertEquals("provider_http_error", deRed.codigo)
        assertFalse(deRed.message!!.contains("secreto"))

        fallaCon<ErrorRespuestaExterna>("provider_http_error") {
            ProveedorAnthropic("clave", cliente("""{"error":"cuota secreta"}""", HttpStatusCode.TooManyRequests))
                .generar(solicitud(modelo = "claude-haiku-4-5"))
        }
    }

    @Test
    fun `sobres malformados o con otra forma son provider_invalid_response`() {
        val malformado = assertFailsWith<ErrorRespuestaExterna> {
            runBlocking { ProveedorOpenAi("clave", cliente("no es json raw-secreto")).generar(solicitud()) }
        }
        assertEquals("provider_invalid_response", malformado.codigo)
        assertFalse(malformado.message!!.contains("raw-secreto"))

        fallaCon<ErrorRespuestaExterna>("provider_invalid_response") {
            ProveedorOpenAi("clave", cliente("""{"choices":[]}""")).generar(solicitud())
        }
        fallaCon<ErrorRespuestaExterna>("provider_missing_content") {
            ProveedorAnthropic("clave", cliente("""{"content":[]}""")).generar(solicitud(modelo = "claude-haiku-4-5"))
        }
    }
}
