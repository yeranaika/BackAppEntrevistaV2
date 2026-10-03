package PRUEBAS.DOBLES

import INTEGRACIONES.Cache
import INTEGRACIONES.ClienteMercadoLaboral
import INTEGRACIONES.CompraGoogle
import INTEGRACIONES.OfertaLaboral
import INTEGRACIONES.VerificadorCompraGoogle
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Caché en memoria (sin vencimiento) que cuenta lecturas para verificar aciertos. */
class CacheEnMemoria : Cache {
    val valores = ConcurrentHashMap<String, String>()
    var aciertos = 0
        private set

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
