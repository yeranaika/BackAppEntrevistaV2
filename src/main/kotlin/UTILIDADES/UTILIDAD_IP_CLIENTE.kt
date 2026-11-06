package UTILIDADES

import io.ktor.server.application.ApplicationCall
import java.net.InetAddress

private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
private val CARACTERES_IPV6 = Regex("""^[0-9A-Fa-f:.]{2,45}$""")

/** Igual que ip_origen VARCHAR(45): el largo máximo de una IPv6 escrita como texto. */
private const val LARGO_MAXIMO_IP = 45

/**
 * La IP literal en forma canónica, o null si [valor] no es una IP. Nunca hace DNS: a InetAddress solo llega un texto
 * con ':', únicamente caracteres hexadecimales y que **empieza** por un dígito hexadecimal o ':'. Solo así el JDK lo
 * trata como literal IPv6; si empezara por '.', lo mandaría al resolvedor de nombres.
 */
fun normalizarIp(valor: String?): String? = normalizarIp(valor) { InetAddress.getByName(it).hostAddress }

/** [analizarIpv6] solo recibe textos que el JDK trata como literal IPv6 (las pruebas verifican que nunca otra cosa). */
internal fun normalizarIp(valor: String?, analizarIpv6: (String) -> String): String? {
    val texto = valor?.trim().orEmpty()
    if (texto.isEmpty() || texto.length > LARGO_MAXIMO_IP) return null
    IPV4.matchEntire(texto)?.let { coincidencia ->
        val octetos = coincidencia.groupValues.drop(1).map { it.toInt() }
        return if (octetos.all { it <= 255 }) octetos.joinToString(".") else null
    }
    if (':' !in texto || !CARACTERES_IPV6.matches(texto)) return null
    if (Character.digit(texto[0], 16) == -1 && texto[0] != ':') return null
    // ::ffff:a.b.c.d (IPv4 mapeada) vuelve como a.b.c.d.
    return runCatching { analizarIpv6(texto) }.getOrNull()
}

/** Clave del límite: una IPv4 tal cual; una IPv6 por su bloque /64, porque un cliente suele controlar el bloque entero. */
fun claveLimiteIp(ip: String): String {
    if (':' !in ip) return ip
    val bytes = runCatching { InetAddress.getByName(ip).address }.getOrNull() ?: return ip
    if (bytes.size != 16) return ip
    val prefijo = (0 until 8 step 2).joinToString(":") { i ->
        "%x".format(((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff))
    }
    return "$prefijo::/64"
}

/**
 * IP del cliente para el límite por IP. Si hay [cabeceraConfiable] (por ejemplo X-Real-IP, que el proxy de Railway
 * sobrescribe), se usa solo si trae una IP literal válida: sin comas ni listas. Si no, se usa la conexión TCP directa
 * (request.local, sin DNS inverso). Nunca devuelve una cadena vacía.
 */
fun ApplicationCall.ipCliente(cabeceraConfiable: String?): String {
    cabeceraConfiable?.let { cabecera -> normalizarIp(request.headers[cabecera])?.let { return it } }
    val conexion = request.local.remoteAddress
    return normalizarIp(conexion) ?: conexion.trim().lowercase().take(LARGO_MAXIMO_IP).ifEmpty { "desconocida" }
}
