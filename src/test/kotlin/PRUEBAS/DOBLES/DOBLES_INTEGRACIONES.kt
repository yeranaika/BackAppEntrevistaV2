package PRUEBAS.DOBLES

import INTEGRACIONES.Cache
import INTEGRACIONES.ClienteMercadoLaboral
import INTEGRACIONES.CompraGoogle
import INTEGRACIONES.ConteoVentana
import INTEGRACIONES.OfertaLaboral
import INTEGRACIONES.VerificadorCompraGoogle
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Caché en memoria (sin vencimiento) que cuenta lecturas para verificar aciertos. */
/**
 * Caché en memoria: valores sin vencimiento (cuenta aciertos) y contadores de ventana fija con un reloj inyectable,
 * con la misma semántica que el script de Redis. Una misma instancia compartida simula el Redis de varios servidores.
 */
class CacheEnMemoria(private val ahoraMs: () -> Long = System::currentTimeMillis) : Cache {
    data class Contador(val conteo: Long, val venceEnMs: Long)

    val valores = ConcurrentHashMap<String, String>()
    val contadores = ConcurrentHashMap<String, Contador>()

    /** false simula Redis caído para los contadores de ventana. */
    @Volatile var disponible = true
    var aciertos = 0
        private set

    override suspend fun contarEnVentana(clave: String, ventanaMs: Long, cantidad: Int): ConteoVentana? {
        if (!disponible) return null
        val ahora = ahoraMs()
        val contador = contadores.compute(clave) { _, previo ->
            if (previo == null || previo.venceEnMs <= ahora) Contador(cantidad.toLong(), ahora + ventanaMs)
            else previo.copy(conteo = previo.conteo + cantidad) // no alarga la ventana, igual que el script de Redis
        }!!
        return ConteoVentana(contador.conteo, contador.venceEnMs - ahora)
    }

    override suspend fun obtener(clave: String): String? = valores[clave]?.also { aciertos++ }
    override suspend fun guardar(clave: String, valor: String, ttlSegundos: Long) { valores[clave] = valor }
    override suspend fun borrarPorPrefijo(prefijo: String) { valores.keys.removeIf { it.startsWith(prefijo) } }
    override suspend fun borrar(clave: String) { valores.remove(clave) }
    override suspend fun incrementar(clave: String, ttlSegundos: Long): Long =
        valores.merge(clave, "1") { actual, _ -> (actual.toLong() + 1).toString() }!!.toLong()
}

/** Devuelve ofertas según la consulta (clave null = búsqueda general) y registra qué se pidió. */
class ClienteMercadoLaboralFalso(private val ofertasPorConsulta: Map<String?, List<OfertaLaboral>>) : ClienteMercadoLaboral {
    val consultas = mutableListOf<String?>()

    override suspend fun buscarOfertas(consulta: String?): List<OfertaLaboral> {
        consultas += consulta
        return ofertasPorConsulta[consulta].orEmpty()
    }
}

fun oferta(titulo: String, descripcion: String) = OfertaLaboral(titulo, descripcion, "Prueba")

/** Google Play falso: responde [compra] o lanza [error]. */
class VerificadorCompraFalso(
    var compra: CompraGoogle = CompraGoogle(esValida = true, estaActiva = true, expiracion = Instant.now().plusSeconds(30L * 24 * 3600)),
    var error: Exception? = null
) : VerificadorCompraGoogle {
    override suspend fun verificar(productoId: String, tokenCompra: String): CompraGoogle {
        error?.let { throw it }
        return compra
    }
}
