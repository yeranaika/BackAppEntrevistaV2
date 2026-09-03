package services.cache

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration

class RedisCacheService(
    host: String = "localhost",
    port: Int = 6379,
    password: String? = null,
    timeoutMs: Int = 2000
) {
    private val logger = LoggerFactory.getLogger(RedisCacheService::class.java)

    private val pool: JedisPool? = try {
        val poolConfig = JedisPoolConfig().apply {
            maxTotal = 32
            maxIdle = 16
            minIdle = 4
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            minEvictableIdleDuration = Duration.ofSeconds(60)
            timeBetweenEvictionRuns = Duration.ofSeconds(30)
            blockWhenExhausted = true
            setMaxWait(Duration.ofMillis(1000))
        }
        if (!password.isNullOrBlank()) {
            JedisPool(poolConfig, host, port, timeoutMs, password)
        } else {
            JedisPool(poolConfig, host, port, timeoutMs)
        }
    } catch (e: Exception) {
        logger.warn("No se pudo inicializar JedisPool para Redis: ${e.message}")
        null
    }

    /**
     * Verifica si la conexión con Redis responde al comando PING.
     */
    fun isAvailable(): Boolean {
        if (pool == null) return false
        return try {
            pool.resource.use { jedis ->
                jedis.ping() == "PONG"
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene una cadena almacenada en Redis. Retorna null en cache miss o si Redis no está disponible.
     */
    fun get(key: String): String? {
        if (pool == null) return null
        return try {
            pool.resource.use { jedis ->
                jedis.get(key)
            }
        } catch (e: Exception) {
            logger.debug("Cache miss o error al leer clave Redis '$key': ${e.message}")
            null
        }
    }

    /**
     * Guarda una cadena en Redis con tiempo de expiración (TTL en segundos).
     */
    fun set(key: String, value: String, ttlSeconds: Long): Boolean {
        if (pool == null) return false
        return try {
            pool.resource.use { jedis ->
                jedis.setex(key, ttlSeconds, value)
                true
            }
        } catch (e: Exception) {
            logger.debug("Error al escribir clave Redis '$key': ${e.message}")
            false
        }
    }

    /**
     * Elimina una clave de Redis.
     */
    fun delete(key: String): Long {
        if (pool == null) return 0L
        return try {
            pool.resource.use { jedis ->
                jedis.del(key)
            }
        } catch (e: Exception) {
            logger.debug("Error al eliminar clave Redis '$key': ${e.message}")
            0L
        }
    }

    fun close() {
        try {
            pool?.close()
        } catch (e: Exception) {
            logger.warn("Error al cerrar JedisPool: ${e.message}")
        }
    }
}
