package INTEGRACIONES

import CONFIGURACION.ConfiguracionMercadoLaboral
import CONFIGURACION.OFERTAS_CONTINGENCIA
import ERRORES.ErrorServicioExterno
import kotlinx.coroutines.CancellationException
import UTILIDADES.PoliticaResiliencia
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT_HTTP_MS = 15_000L
private const val URL_REMOTIVE = "https://remotive.com/api/remote-jobs"
private const val URL_ARBEITNOW = "https://www.arbeitnow.com/api/job-board-api"
private const val OFERTAS_REMOTIVE = "50"
private val CONSULTAS_JSEARCH_GENERALES = listOf(
    "Software Engineer", "Backend Developer", "Frontend Developer", "Data Engineer", "Android Developer", "DevOps"
)
private val ETIQUETA_HTML = Regex("<[^>]*>")

data class OfertaLaboral(
    val titulo: String,
    val descripcion: String,
    val fuente: String
) {
    val textoCompleto: String get() = "$titulo $descripcion"
}

interface ClienteMercadoLaboral {
    /**
     * Ofertas reales para [consulta] (o generales si es null). Prueba JSearch → Remotive → Arbeitnow y,
     * si ninguna responde, devuelve un dataset de contingencia: nunca falla.
     */
    suspend fun buscarOfertas(consulta: String?): List<OfertaLaboral>
}

/** Respuesta HTTP 5xx o 429: vale la pena reintentar. */
private class FallaTemporalHttp(estado: HttpStatusCode) : IOException("HTTP ${estado.value}")

class ClienteMercadoLaboralHttp(
    private val config: ConfiguracionMercadoLaboral,
    private val cliente: HttpClient = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_HTTP_MS } }
) : ClienteMercadoLaboral, AutoCloseable {

    private val log = LoggerFactory.getLogger(ClienteMercadoLaboralHttp::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Un cortocircuito por fuente: si una se cae, las demás siguen respondiendo.
    private val politicaJsearch = politica("jsearch")
    private val politicaRemotive = politica("remotive")
    private val politicaArbeitnow = politica("arbeitnow")

    private val tieneClaveJsearch: Boolean
        get() = !config.apiKey.isNullOrBlank() && !config.apiKey.startsWith("your_")

    override suspend fun buscarOfertas(consulta: String?): List<OfertaLaboral> {
        val fuentes = listOfNotNull(
            if (tieneClaveJsearch) "JSearch" to suspend { buscarEnJsearch(consulta) } else null,
            "Remotive" to suspend { buscarEnRemotive(consulta) },
            "Arbeitnow" to suspend { buscarEnArbeitnow(consulta) }
        )
        for ((nombre, buscar) in fuentes) {
            try {
                val ofertas = buscar()
                if (ofertas.isNotEmpty()) {
                    log.info("{} devolvió {} ofertas", nombre, ofertas.size)
                    return ofertas
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorServicioExterno) {
                log.warn("{} no disponible ({}); se prueba la siguiente fuente", nombre, e.codigo)
            } catch (e: Exception) {
                // Una fuente que responde basura (JSON roto, otro formato) no debe impedir probar las demás.
                log.warn("{} respondió algo inesperado ({}); se prueba la siguiente fuente", nombre, e.javaClass.simpleName)
            }
        }
        log.warn("Ninguna API de empleo respondió; se usa el dataset de contingencia")
        return contingencia(consulta)
    }

    private suspend fun buscarEnJsearch(consulta: String?): List<OfertaLaboral> =
        (consulta?.let { listOf(it) } ?: CONSULTAS_JSEARCH_GENERALES).flatMap { termino ->
            val datos = obtenerJson(politicaJsearch, "https://${config.apiHost}/search") {
                header("X-RapidAPI-Key", config.apiKey)
                header("X-RapidAPI-Host", config.apiHost)
                parameter("query", termino)
                parameter("page", "1")
                parameter("num_pages", "1")
            }
            datos["data"].comoArreglo().map { oferta ->
                val requisitos = oferta.jsonObject["job_highlights"]?.jsonObject?.get("Qualifications").comoArreglo()
                    .joinToString(" ") { it.jsonPrimitive.content }
                OfertaLaboral(
                    titulo = oferta.texto("job_title"),
                    descripcion = limpiarHtml("${oferta.texto("job_description")} $requisitos"),
                    fuente = "JSearch (LinkedIn/Indeed/Glassdoor)"
                )
            }
        }.filter { it.textoCompleto.isNotBlank() }

    private suspend fun buscarEnRemotive(consulta: String?): List<OfertaLaboral> {
        val datos = obtenerJson(politicaRemotive, URL_REMOTIVE) {
            parameter("category", "software-dev")
            consulta?.let { parameter("search", it) }
            parameter("limit", OFERTAS_REMOTIVE)
        }
        return datos["jobs"].comoArreglo().map { ofertaConEtiquetas(it, "Remotive API") }.filter { it.textoCompleto.isNotBlank() }
    }

    private suspend fun buscarEnArbeitnow(consulta: String?): List<OfertaLaboral> {
        val datos = obtenerJson(politicaArbeitnow, URL_ARBEITNOW) { consulta?.let { parameter("search", it) } }
        return datos["data"].comoArreglo().map { ofertaConEtiquetas(it, "Arbeitnow API") }.filter { it.textoCompleto.isNotBlank() }
    }

    /** GET con la política de la fuente: reintenta red caída, 429 y 5xx; un 4xx se toma como fuente sin datos. */
    private suspend fun obtenerJson(politica: PoliticaResiliencia, url: String, configurar: HttpRequestBuilder.() -> Unit): JsonObject =
        politica.ejecutar {
            val respuesta = cliente.get(url, configurar)
            when {
                respuesta.status.isSuccess() -> json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
                respuesta.status == HttpStatusCode.TooManyRequests || respuesta.status.value >= 500 -> throw FallaTemporalHttp(respuesta.status)
                else -> JsonObject(emptyMap())
            }
        }

    private fun ofertaConEtiquetas(elemento: kotlinx.serialization.json.JsonElement, fuente: String): OfertaLaboral {
        val etiquetas = elemento.jsonObject["tags"].comoArreglo().joinToString(" ") { it.jsonPrimitive.content }
        return OfertaLaboral(elemento.texto("title"), limpiarHtml("${elemento.texto("description")} $etiquetas"), fuente)
    }

    private fun contingencia(consulta: String?): List<OfertaLaboral> {
        if (consulta.isNullOrBlank()) return OFERTAS_CONTINGENCIA
        val filtradas = OFERTAS_CONTINGENCIA.filter { it.textoCompleto.contains(consulta, ignoreCase = true) }
        return filtradas.ifEmpty { OFERTAS_CONTINGENCIA }
    }

    private fun limpiarHtml(texto: String) = texto.replace(ETIQUETA_HTML, " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

    private fun kotlinx.serialization.json.JsonElement?.comoArreglo(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())

    private fun kotlinx.serialization.json.JsonElement.texto(campo: String): String =
        runCatching { jsonObject[campo]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    override fun close() = cliente.close()

    private fun politica(nombre: String) = PoliticaResiliencia(
        nombre = nombre,
        tiempoMaximo = 20.seconds,
        intentos = 2,
        fallosParaAbrir = 3,
        tiempoAbierto = 5.minutes
    )
}
