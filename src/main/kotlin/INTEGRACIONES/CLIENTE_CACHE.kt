package INTEGRACIONES

import CONFIGURACION.ConfiguracionRedis
import ERRORES.ErrorServicioExterno
import UTILIDADES.PoliticaResiliencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.ScanParams
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val CONEXIONES_MAXIMAS = 32
private const val TIMEOUT_REDIS_MS = 500
private const val LOTE_SCAN = 200

/**
 * Caché compartida. Es opcional por diseño: si no responde, las lecturas devuelven null y las escrituras
 * se ignoran, y la aplicación sigue funcionando contra la base de datos.
 */
interface Cache {
    suspend fun obtener(clave: String): String?
    suspend fun guardar(clave: String, valor: String, ttlSegundos: Long)
    suspend fun borrarPorPrefijo(prefijo: String)

    /** Incrementa un contador que vence en [ttlSegundos]; null si la caché no está disponible. */
    suspend fun incrementar(clave: String, ttlSegundos: Long): Long?
    suspend fun borrar(clave: String)
}

/** Cache-aside: lee de la caché o calcula, guarda y devuelve. */
suspend fun <T> Cache.obtenerOCalcular(
    clave: String,
    ttlSegundos: Long,
    serializador: KSerializer<T>,
    calcular: suspend () -> T
): T {
    obtener(clave)?.let { guardado ->
        runCatching { return jsonCache.decodeFromString(serializador, guardado) }
    }
    val valor = calcular()
    guardar(clave, jsonCache.encodeToString(serializador, valor), ttlSegundos)
    return valor
}

private val jsonCache = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class CacheRedis(config: ConfiguracionRedis) : Cache, AutoCloseable {

    private val log = LoggerFactory.getLogger(CacheRedis::class.java)

    // Sin reintentos y con cortocircuito: una caché lenta es peor que no tener caché.
    private val politica = PoliticaResiliencia(
        nombre = "redis",
        tiempoMaximo = 1.seconds,
        intentos = 1,
        fallosParaAbrir = 3,
        tiempoAbierto = 30.seconds,
        esperaInicial = 0.milliseconds
    )

    private val pool = JedisPool(
        JedisPoolConfig().apply {
            maxTotal = CONEXIONES_MAXIMAS
            setMaxWait(Duration.ofMillis(TIMEOUT_REDIS_MS.toLong()))
            testWhileIdle = true
        },
        config.host,
        config.puerto,
        TIMEOUT_REDIS_MS,
        config.contrasena?.takeIf { it.isNotBlank() }
    )

    override suspend fun obtener(clave: String): String? = usar(null) { it.get(clave) }

    override suspend fun guardar(clave: String, valor: String, ttlSegundos: Long) {
        usar(Unit) { it.setex(clave, ttlSegundos, valor) }
    }

    override suspend fun borrar(clave: String) {
        usar(Unit) { it.del(clave) }
    }

    override suspend fun borrarPorPrefijo(prefijo: String) {
        usar(Unit) { jedis ->
            // SCAN en vez de KEYS: no bloquea Redis con muchas claves.
            val parametros = ScanParams().match("$prefijo*").count(LOTE_SCAN)
            var cursor = ScanParams.SCAN_POINTER_START
            do {
                val pagina = jedis.scan(cursor, parametros)
                if (pagina.result.isNotEmpty()) jedis.del(*pagina.result.toTypedArray())
                cursor = pagina.cursor
            } while (cursor != ScanParams.SCAN_POINTER_START)
        }
    }

    override suspend fun incrementar(clave: String, ttlSegundos: Long): Long? = usar(null) { jedis ->
        val valor = jedis.incr(clave)
        if (valor == 1L) jedis.expire(clave, ttlSegundos)
        valor
    }

    /** Jedis es bloqueante: corre en el pool de IO, con la política de resiliencia y sin propagar fallas. */
    private suspend fun <T> usar(siFalla: T, operacion: (Jedis) -> T): T = try {
        politica.ejecutar(esReintentable = { true }) {
            withContext(Dispatchers.IO) { pool.resource.use(operacion) }
        }
    } catch (e: ErrorServicioExterno) {
        log.debug("Redis no disponible: {}", e.message)
        siFalla
    }

    override fun close() = pool.close()
}
