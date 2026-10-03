package INTEGRACIONES

import java.util.concurrent.ConcurrentHashMap

/** Cuenta fallos por clave (ej: correo) dentro de una ventana de tiempo. */
interface ContadorIntentos {
    suspend fun fallos(clave: String): Int
    suspend fun registrarFallo(clave: String): Int
    suspend fun limpiar(clave: String)
}

/**
 * Contador en la caché compartida (Redis), para que el límite valga entre instancias del servidor.
 * Si la caché no responde no se bloquea a nadie: es preferible a impedir todos los logins.
 */
class ContadorIntentosCache(
    private val cache: Cache,
    private val prefijo: String,
    private val ventanaSegundos: Long
) : ContadorIntentos {
    override suspend fun fallos(clave: String): Int = cache.obtener(prefijo + clave)?.toIntOrNull() ?: 0
    override suspend fun registrarFallo(clave: String): Int = cache.incrementar(prefijo + clave, ventanaSegundos)?.toInt() ?: 0
    override suspend fun limpiar(clave: String) = cache.borrar(prefijo + clave)
}

/** Para pruebas: contador local sin vencimiento. */
class ContadorIntentosEnMemoria : ContadorIntentos {
    private val conteos = ConcurrentHashMap<String, Int>()
    override suspend fun fallos(clave: String) = conteos[clave] ?: 0
    override suspend fun registrarFallo(clave: String) = conteos.merge(clave, 1, Int::plus)!!
    override suspend fun limpiar(clave: String) { conteos.remove(clave) }
}
