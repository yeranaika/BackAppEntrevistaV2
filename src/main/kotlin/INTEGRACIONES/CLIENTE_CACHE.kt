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
import redis.clients.jedis.exceptions.JedisDataException
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

    /**
     * Suma [cantidad] a un contador de ventana fija de [ventanaMs] y devuelve el total y los ms que le quedan a la ventana.
     * Es atómico: si la clave no vence (nueva, o huérfana sin TTL), la ventana empieza ahora; los incrementos posteriores
     * no la alargan. null si la caché no está disponible.
     */
    suspend fun contarEnVentana(clave: String, ventanaMs: Long, cantidad: Int = 1): ConteoVentana?
}

/** Los dos primeros segmentos de una clave ("limite:registro:<ip>" → "limite:registro"), sin el dato personal del final. */
internal fun prefijoParaLog(clave: String): String = clave.split(':').take(2).joinToString(":")

/** Conteo de una ventana fija y los milisegundos que le quedan. */
data class ConteoVentana(val conteo: Long, val restanteMs: Long)

// Atómico en Redis: suma y, si la clave no tiene vencimiento (nueva o huérfana), le pone la ventana.
private const val SCRIPT_CONTAR_EN_VENTANA = """
local conteo = redis.call('INCRBY', KEYS[1], ARGV[2])
local restante = redis.call('PTTL', KEYS[1])
if restante < 0 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
  restante = tonumber(ARGV[1])
end
return {conteo, restante}
"""

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

    // Antes era INCR y luego EXPIRE en dos pasos: si algo fallaba entre medio, la clave quedaba sin vencimiento.
    override suspend fun incrementar(clave: String, ttlSegundos: Long): Long? =
        contarEnVentana(clave, ttlSegundos * 1000)?.conteo

    override suspend fun contarEnVentana(clave: String, ventanaMs: Long, cantidad: Int): ConteoVentana? = usar(null) { jedis ->
        try {
            val respuesta = jedis.eval(SCRIPT_CONTAR_EN_VENTANA, listOf(clave), listOf(ventanaMs.toString(), cantidad.toString())) as List<*>
            ConteoVentana(respuesta[0] as Long, respuesta[1] as Long)
        } catch (e: JedisDataException) {
            // Error del script o tipo de clave incorrecto: Redis responde, así que no debe abrir el cortocircuito compartido.
            // Al log van solo los dos primeros segmentos ("limite:registro", "login:fallos"): lo que sigue es una IP o un
            // correo (datos personales). No sirve cortar en el último ':', porque una IPv6 lleva ':' por dentro.
            log.error("El contador en ventana falló para {}: {}", prefijoParaLog(clave), e.message)
            null
        }
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
