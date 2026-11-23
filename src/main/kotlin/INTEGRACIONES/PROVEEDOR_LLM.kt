package INTEGRACIONES

import ERRORES.ErrorAplicacion
import ERRORES.ErrorRespuestaExterna
import ERRORES.ErrorServicioExterno
import UTILIDADES.PoliticaResiliencia
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TIMEOUT_LLM_MS = 60_000L
private const val TEMPERATURA_OPENAI = 0.2
private const val MAXIMO_TOKENS_ANTHROPIC = 8192
private const val VERSION_API_ANTHROPIC = "2023-06-01"
private const val URL_OPENAI = "https://api.openai.com/v1/chat/completions"
private const val URL_ANTHROPIC = "https://api.anthropic.com/v1/messages"
/** API de pago por uso de Xiaomi MiMo, compatible con Chat Completions de OpenAI (D30). */
const val URL_MIMO_POR_DEFECTO = "https://api.xiaomimimo.com/v1/chat/completions"
/** MiMo no limita la salida por esquema: se le da margen para que un lote de 10 preguntas no quede cortado. */
private const val MAXIMO_TOKENS_MIMO = 8192

private val jsonEstricto = Json { ignoreUnknownKeys = false; isLenient = false }

enum class TipoProveedorIa { OPENAI, ANTHROPIC, MIMO }

/** Modelo barato de cada proveedor, el que se usa por defecto. */
const val MODELO_OPENAI_POR_DEFECTO = "gpt-4o-mini"
const val MODELO_ANTHROPIC_POR_DEFECTO = "claude-haiku-4-5"
const val MODELO_MIMO_POR_DEFECTO = "mimo-v2.6-flash"
const val MODELO_MIMO_PRO = "mimo-v2.6-pro"

/**
 * USD por token (entrada, salida). MiMo: precio en USD de la API de pago por uso, tiempo real y entrada sin caché
 * (mimo.mi.com, precios de noviembre de 2026). Con caché la entrada sale más barata, así que el costo queda estimado
 * hacia arriba, que es el lado seguro para el tope de gasto (D23).
 */
private val COSTO_POR_TOKEN = mapOf(
    MODELO_OPENAI_POR_DEFECTO to (0.00000015 to 0.00000060),
    MODELO_ANTHROPIC_POR_DEFECTO to (0.000001 to 0.000005),
    MODELO_MIMO_POR_DEFECTO to (0.00000014 to 0.00000028),
    MODELO_MIMO_PRO to (0.000000435 to 0.00000087)
)

/**
 * Token Plan de MiMo (D31): Credits por token (entrada sin caché, salida), según la tabla oficial de
 * mimo.mi.com (Token Plan → Subscription, noviembre de 2026). La entrada con caché cuesta mucho menos (2,5 y 2), pero
 * la respuesta no dice qué parte vino de caché: se cuenta todo sin caché, así lo gastado queda estimado hacia arriba.
 * Un modelo mimo-* que no esté en la tabla se cuenta con la tarifa del más caro (pro).
 */
private val CREDITOS_POR_TOKEN_MIMO = mapOf(
    MODELO_MIMO_POR_DEFECTO to (100L to 200L),
    MODELO_MIMO_PRO to (300L to 600L)
)

/** Credits del plan Lite (US$6/mes): se asume si la clave es de Token Plan (tp-) y no se definió MIMO_TOKEN_PLAN_CREDITOS. */
const val CREDITOS_TOKEN_PLAN_LITE = 4_100_000_000L

fun esModeloMimo(modelo: String): Boolean = modelo.startsWith("mimo")

/** Credits del Token Plan que consumió una llamada; 0 si no es un modelo de MiMo. */
fun creditosTokenPlanMimo(modelo: String, tokensEntrada: Int?, tokensSalida: Int?): Long {
    if (!esModeloMimo(modelo)) return 0
    val (porEntrada, porSalida) = CREDITOS_POR_TOKEN_MIMO[modelo] ?: CREDITOS_POR_TOKEN_MIMO.getValue(MODELO_MIMO_PRO)
    return (tokensEntrada ?: 0).coerceAtLeast(0) * porEntrada + (tokensSalida ?: 0).coerceAtLeast(0) * porSalida
}

/** Costo de una llamada; 0 si el modelo no tiene precio cargado. */
fun costoLlmUsd(modelo: String, tokensEntrada: Int?, tokensSalida: Int?): Double {
    val (precioEntrada, precioSalida) = COSTO_POR_TOKEN[modelo] ?: (0.0 to 0.0)
    return (tokensEntrada ?: 0) * precioEntrada + (tokensSalida ?: 0) * precioSalida
}

data class SolicitudProveedorIa(
    val prompt: String,
    val modelo: String,
    val esquemaJson: JsonObject,
    val instruccionUsuario: String
)

data class RespuestaProveedorIa(
    val contenido: String,
    val tokensEntrada: Int?,
    val tokensSalida: Int?
)

/**
 * Un LLM que devuelve preguntas en el JSON pedido.
 * Errores: 503 provider_not_configured (sin API key), 502 si responde algo inutilizable.
 */
interface ProveedorPreguntasIa {
    suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa
}

/**
 * Agrega reintentos y cortocircuito a un proveedor. Solo se reintenta cuando el proveedor no respondió o
 * respondió con error HTTP; una respuesta con formato inválido no mejora reintentando y cuesta tokens, y un tiempo
 * agotado (provider_timeout) tampoco se reintenta: el proveedor pudo cobrar esa llamada.
 */
class ProveedorConResiliencia(
    private val interno: ProveedorPreguntasIa,
    nombre: String
) : ProveedorPreguntasIa {
    private val politica = PoliticaResiliencia(nombre = nombre, tiempoMaximo = (TIMEOUT_LLM_MS + 10_000).milliseconds, intentos = 2)

    override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa =
        politica.ejecutar(esReintentable = { it is ErrorRespuestaExterna && it.codigo == "provider_http_error" }) {
            interno.generar(solicitud)
        }
}

/** Cliente HTTP compartido por los proveedores; se cierra al apagar la aplicación. */
fun crearClienteHttpLlm(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; isLenient = false }) }
    install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_LLM_MS }
}

class ProveedorOpenAi(
    private val apiKey: String,
    private val cliente: HttpClient,
    private val url: String = URL_OPENAI
) : ProveedorPreguntasIa {

    override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa {
        if (apiKey.isBlank()) throw proveedorNoConfigurado()

        val respuesta = enviar {
            cliente.post(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(cuerpo(solicitud))
            }
        }
        val sobre = leerSobre(respuesta)
        // Los tokens se leen antes: un rechazo o un corte también se cobran y se auditan con su costo.
        val tokens = leerTokens(sobre, "prompt_tokens", "completion_tokens")
        val contenido = extraer(tokens) {
            val eleccion = sobre["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw respuestaInvalida(tokens = tokens)
            if (eleccion["finish_reason"]?.jsonPrimitive?.content == "length") throw respuestaTruncada(tokens)
            val mensaje = eleccion["message"]?.jsonObject ?: throw respuestaInvalida(tokens = tokens)
            // contentOrNull: OpenAI manda "refusal": null en toda respuesta normal, y JsonNull.content es el texto "null".
            if (!mensaje["refusal"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) throw rechazoDelModelo(tokens)
            mensaje["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw respuestaInvalida("provider_missing_content", tokens)
        }
        return RespuestaProveedorIa(contenido = contenido, tokensEntrada = tokens.entrada, tokensSalida = tokens.salida)
    }

    private fun cuerpo(solicitud: SolicitudProveedorIa) = buildJsonObject {
        put("model", solicitud.modelo)
        put("temperature", TEMPERATURA_OPENAI)
        put("response_format", buildJsonObject {
            put("type", "json_schema")
            put("json_schema", buildJsonObject {
                put("name", "ai_questions_batch")
                put("strict", true)
                put("schema", solicitud.esquemaJson)
            })
        })
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", solicitud.prompt) })
            add(buildJsonObject { put("role", "user"); put("content", solicitud.instruccionUsuario) })
        })
    }
}

class ProveedorAnthropic(
    private val apiKey: String,
    private val cliente: HttpClient,
    private val url: String = URL_ANTHROPIC
) : ProveedorPreguntasIa {

    override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa {
        if (apiKey.isBlank()) throw proveedorNoConfigurado()

        val respuesta = enviar {
            cliente.post(url) {
                header("x-api-key", apiKey)
                header("anthropic-version", VERSION_API_ANTHROPIC)
                contentType(ContentType.Application.Json)
                setBody(cuerpo(solicitud))
            }
        }
        val sobre = leerSobre(respuesta)
        // Los tokens se leen antes: un rechazo o un corte también se cobran y se auditan con su costo.
        val tokens = leerTokens(sobre, "input_tokens", "output_tokens")
        val texto = extraer(tokens) {
            when (sobre["stop_reason"]?.jsonPrimitive?.content) {
                "refusal" -> throw rechazoDelModelo(tokens)
                "max_tokens", "model_context_window_exceeded" -> throw respuestaTruncada(tokens)
            }
            sobre["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: throw respuestaInvalida("provider_missing_content", tokens)
        }
        return RespuestaProveedorIa(contenido = texto, tokensEntrada = tokens.entrada, tokensSalida = tokens.salida)
    }

    private fun cuerpo(solicitud: SolicitudProveedorIa) = buildJsonObject {
        put("model", solicitud.modelo)
        put("max_tokens", MAXIMO_TOKENS_ANTHROPIC)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "${solicitud.prompt}\n\n${solicitud.instruccionUsuario}")
            })
        })
        put("output_config", buildJsonObject {
            put("format", buildJsonObject {
                put("type", "json_schema")
                put("schema", solicitud.esquemaJson)
            })
        })
    }
}

/**
 * Xiaomi MiMo por su API compatible con OpenAI (D30). Diferencias con [ProveedorOpenAi]:
 * - La clave va en la cabecera `api-key` (la forma de la documentación oficial).
 * - No acepta `json_schema`: se pide `json_object` (JSON válido garantizado) y el esquema va escrito en el prompt.
 *   Lo que devuelva se valida igual que con los demás proveedores; si no cumple, es provider_invalid_response.
 * - El razonamiento («thinking») viene encendido en la serie v2.6: se apaga, porque cobra tokens y suma latencia sin
 *   aportar a preguntas que ya traen su rúbrica.
 * [url] se puede cambiar con MIMO_BASE_URL (por ejemplo, el endpoint del plan de tokens).
 */
class ProveedorMimo(
    private val apiKey: String,
    private val cliente: HttpClient,
    private val url: String = URL_MIMO_POR_DEFECTO
) : ProveedorPreguntasIa {

    override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa {
        if (apiKey.isBlank()) throw proveedorNoConfigurado()

        val respuesta = enviar {
            cliente.post(url) {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(cuerpo(solicitud))
            }
        }
        val sobre = leerSobre(respuesta)
        // Los tokens se leen antes: un rechazo o un corte también se cobran y se auditan con su costo.
        val tokens = leerTokens(sobre, "prompt_tokens", "completion_tokens")
        val contenido = extraer(tokens) {
            val eleccion = sobre["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw respuestaInvalida(tokens = tokens)
            when (eleccion["finish_reason"]?.jsonPrimitive?.contentOrNull) {
                null, "stop" -> Unit
                "content_filter" -> throw rechazoDelModelo(tokens)
                else -> throw respuestaTruncada(tokens) // length, repetition_truncation u otro corte
            }
            val mensaje = eleccion["message"]?.jsonObject ?: throw respuestaInvalida(tokens = tokens)
            val texto = mensaje["content"]?.jsonPrimitive?.contentOrNull?.let(::sinCercoDeCodigo)?.takeIf { it.isNotBlank() }
                ?: throw respuestaInvalida("provider_missing_content", tokens)
            texto
        }
        return RespuestaProveedorIa(contenido = contenido, tokensEntrada = tokens.entrada, tokensSalida = tokens.salida)
    }

    private fun cuerpo(solicitud: SolicitudProveedorIa) = buildJsonObject {
        put("model", solicitud.modelo)
        put("temperature", TEMPERATURA_OPENAI)
        put("max_completion_tokens", MAXIMO_TOKENS_MIMO)
        put("thinking", buildJsonObject { put("type", "disabled") })
        put("response_format", buildJsonObject { put("type", "json_object") })
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", promptConEsquema(solicitud)) })
            add(buildJsonObject { put("role", "user"); put("content", solicitud.instruccionUsuario) })
        })
    }
}

/** El prompt de MiMo lleva el esquema escrito: json_object solo garantiza JSON válido, no sus campos. */
internal fun promptConEsquema(solicitud: SolicitudProveedorIa): String = buildString {
    append(solicitud.prompt)
    append("\n\nResponde SOLO con un objeto JSON válido, sin texto antes ni después y sin bloques de código. ")
    append("Debe cumplir exactamente este JSON Schema (mismos nombres de campos, tipos y cantidades):\n")
    append(solicitud.esquemaJson.toString())
}

/**
 * Por si el modelo igual envuelve el JSON en un cerco (```json, ``` Json…) o le suma texto antes o después: se deja
 * desde la primera llave hasta la última, porque el esquema siempre pide un objeto. Sin llaves vuelve el texto
 * recortado, y el validador lo rechaza como siempre.
 */
internal fun sinCercoDeCodigo(texto: String): String {
    val limpio = texto.trim()
    val inicio = limpio.indexOf('{')
    val fin = limpio.lastIndexOf('}')
    return if (inicio >= 0 && fin > inicio) limpio.substring(inicio, fin + 1) else limpio
}

// ---------- Apoyo común: nunca se filtran mensajes internos del proveedor ----------

private suspend fun enviar(llamada: suspend () -> HttpResponse): HttpResponse = try {
    llamada()
} catch (e: ErrorAplicacion) {
    throw e
} catch (e: HttpRequestTimeoutException) {
    throw tiempoAgotado()
} catch (_: Exception) {
    throw ErrorRespuestaExterna("provider_http_error", "El proveedor de IA no respondió")
}

/**
 * El pedido ya se envió y el proveedor puede estar generando (y cobrando) la respuesta: no se reintenta, para no
 * pagar dos veces (ProveedorConResiliencia solo reintenta provider_http_error).
 */
private fun tiempoAgotado() =
    ErrorRespuestaExterna("provider_timeout", "El proveedor de IA tardó demasiado en responder")

private suspend fun leerSobre(respuesta: HttpResponse): JsonObject {
    val cuerpo = try {
        respuesta.bodyAsText()
    } catch (e: HttpRequestTimeoutException) {
        throw tiempoAgotado()
    } catch (_: Exception) {
        throw ErrorRespuestaExterna("provider_http_error", "El proveedor de IA no respondió")
    }
    if (respuesta.status != HttpStatusCode.OK) {
        throw ErrorRespuestaExterna("provider_http_error", "El proveedor de IA respondió ${respuesta.status.value}")
    }
    return try {
        jsonEstricto.parseToJsonElement(cuerpo).jsonObject
    } catch (_: Exception) {
        throw respuestaInvalida()
    }
}

private inline fun extraer(tokens: TokensLeidos, bloque: () -> String): String = try {
    bloque()
} catch (e: ErrorAplicacion) {
    throw e
} catch (_: Exception) {
    throw respuestaInvalida(tokens = tokens)
}

/** Tokens que el proveedor informó en el sobre (null si no vinieron). */
private data class TokensLeidos(val entrada: Int?, val salida: Int?)

private fun leerTokens(sobre: JsonObject, claveEntrada: String, claveSalida: String): TokensLeidos {
    val uso = runCatching { sobre["usage"]?.jsonObject }.getOrNull()
    return TokensLeidos(
        runCatching { uso?.get(claveEntrada)?.jsonPrimitive?.intOrNull }.getOrNull(),
        runCatching { uso?.get(claveSalida)?.jsonPrimitive?.intOrNull }.getOrNull()
    )
}

private fun proveedorNoConfigurado() =
    ErrorServicioExterno("provider_not_configured", "No hay API key configurada para ese proveedor de IA")

private fun respuestaInvalida(codigo: String = "provider_invalid_response", tokens: TokensLeidos? = null) =
    ErrorRespuestaExterna(codigo, "El proveedor de IA devolvió una respuesta con formato inesperado", tokens?.entrada, tokens?.salida)

private fun respuestaTruncada(tokens: TokensLeidos) =
    ErrorRespuestaExterna("provider_truncated", "La respuesta del proveedor de IA quedó incompleta", tokens.entrada, tokens.salida)

private fun rechazoDelModelo(tokens: TokensLeidos) =
    ErrorRespuestaExterna("provider_refusal", "El modelo se negó a generar las preguntas", tokens.entrada, tokens.salida)
