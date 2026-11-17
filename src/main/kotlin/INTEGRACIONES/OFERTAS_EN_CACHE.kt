package INTEGRACIONES

import CONFIGURACION.FUENTE_CONTINGENCIA
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Prefijo propio: invalidar la caché del catálogo ("mercado:") no borra las ofertas guardadas. */
const val PREFIJO_CACHE_OFERTAS = "ofertas:"

// La sincronización es semanal (TareaSincronizacionMercado): con 8 días se renuevan antes de vencer.
private const val TTL_OFERTAS_SEGUNDOS = 8 * 24 * 3600L
// El dataset de respaldo no es mercado real: se vuelve a intentar con las APIs pronto.
private const val TTL_CONTINGENCIA_SEGUNDOS = 3600L
private const val MAXIMO_OFERTAS_GUARDADAS = 30
private const val LARGO_MAXIMO_TITULO = 200
// De una descripción larga se guarda el comienzo y el final: los clientes pegan al final los requisitos y los tags,
// que es lo que más sirve; el comienzo suele ser la presentación de la empresa. ~3 KB por oferta, ~90 KB por consulta.
private const val LARGO_INICIO_DESCRIPCION = 1000
private const val LARGO_FINAL_DESCRIPCION = 2000

/** Descripción compacta para la caché: entera si es corta; si no, el comienzo y el final. */
internal fun compactarDescripcion(texto: String): String =
    if (texto.length <= LARGO_INICIO_DESCRIPCION + LARGO_FINAL_DESCRIPCION) texto
    else texto.take(LARGO_INICIO_DESCRIPCION) + "\n…\n" + texto.takeLast(LARGO_FINAL_DESCRIPCION)

@Serializable
private data class OfertaGuardada(val titulo: String, val descripcion: String, val fuente: String)

private val serializadorOfertas = ListSerializer(OfertaGuardada.serializer())
private val jsonOfertas = Json { ignoreUnknownKeys = true }

/** Clave de la caché para una búsqueda: sin mayúsculas ni espacios repetidos; la búsqueda general es "general". */
internal fun claveOfertas(consulta: String?): String {
    val normalizada = consulta?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
    return PREFIJO_CACHE_OFERTAS + (normalizada?.let { "consulta:$it" } ?: "general")
}

/** true si las ofertas salieron del dataset de respaldo y no de una API de empleo. */
fun sonDeRespaldo(ofertas: List<OfertaLaboral>): Boolean = ofertas.isNotEmpty() && ofertas.all { it.fuente == FUENTE_CONTINGENCIA }

/**
 * Cliente de empleo que guarda en la caché cada búsqueda que hace. La sincronización semanal (tendencias y requisitos de
 * cada cargo) pasa por aquí, así que la generación de preguntas puede leer las ofertas de cada cargo sin gastar cuota
 * de las APIs de empleo. Si la caché no responde, todo sigue funcionando: solo no queda nada guardado.
 */
class OfertasEnCache(
    private val cliente: ClienteMercadoLaboral,
    private val cache: Cache
) : ClienteMercadoLaboral {

    private val log = LoggerFactory.getLogger(OfertasEnCache::class.java)

    /** Siempre consulta las APIs de empleo (la sincronización quiere datos frescos) y guarda lo que encontró. */
    override suspend fun buscarOfertas(consulta: String?): List<OfertaLaboral> {
        val ofertas = cliente.buscarOfertas(consulta)
        guardar(consulta, ofertas)
        return ofertas
    }

    /** Las ofertas guardadas de la última búsqueda con esta consulta, o null si no hay (o la caché no responde). */
    suspend fun guardadas(consulta: String?): List<OfertaLaboral>? {
        val texto = cache.obtener(claveOfertas(consulta)) ?: return null
        return runCatching { jsonOfertas.decodeFromString(serializadorOfertas, texto) }
            .onFailure { log.warn("Ofertas guardadas ilegibles para una consulta; se ignoran") }
            .getOrNull()
            ?.map { OfertaLaboral(it.titulo, it.descripcion, it.fuente) }
            ?.takeIf { it.isNotEmpty() }
    }

    private suspend fun guardar(consulta: String?, ofertas: List<OfertaLaboral>) {
        if (ofertas.isEmpty()) return
        val compactas = ofertas.take(MAXIMO_OFERTAS_GUARDADAS).map {
            OfertaGuardada(it.titulo.take(LARGO_MAXIMO_TITULO), compactarDescripcion(it.descripcion), it.fuente)
        }
        val ttl = if (sonDeRespaldo(ofertas)) TTL_CONTINGENCIA_SEGUNDOS else TTL_OFERTAS_SEGUNDOS
        cache.guardar(claveOfertas(consulta), jsonOfertas.encodeToString(serializadorOfertas, compactas), ttl)
    }
}
