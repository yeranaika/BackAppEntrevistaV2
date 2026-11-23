package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import INTEGRACIONES.DOCUMENTO_EULA
import INTEGRACIONES.FuenteDocumentosLegales
import MODELOS.Consentimiento
import MODELOS.RepositorioConsentimiento
import MODELOS.RepositorioTextoConsentimiento
import MODELOS.TextoConsentimiento
import org.slf4j.LoggerFactory
import java.util.UUID

private const val VERSION_INICIAL_EULA = "1.1.0"
private const val TITULO_INICIAL_EULA = "Acuerdo de Licencia de Usuario Final (EULA)"
private const val LARGO_MAXIMO_VERSION = 20
private const val LARGO_MAXIMO_ALCANCE = 50

/**
 * Texto legal vigente (EULA) y consentimientos de cada usuario.
 * Se guardan los alcances aceptados; los rechazados se infieren de los posibles del texto.
 */
class ServicioConsentimiento(
    private val textos: RepositorioTextoConsentimiento,
    private val consentimientos: RepositorioConsentimiento,
    private val documentos: FuenteDocumentosLegales
) {
    private val log = LoggerFactory.getLogger(ServicioConsentimiento::class.java)

    /** Si la BD aún no tiene texto, publica el EULA de docs/legal como versión inicial. */
    suspend fun textoVigente(): TextoConsentimiento =
        textos.vigente() ?: textos.publicar(
            VERSION_INICIAL_EULA,
            TITULO_INICIAL_EULA,
            documentos.leer(DOCUMENTO_EULA)
                ?: throw ErrorServicioExterno("eula_no_disponible", "No hay texto legal publicado ni archivo EULA disponible")
        )

    suspend fun versiones(): List<TextoConsentimiento> = textos.listar()

    /**
     * Deja [version] como la vigente. Volver a publicar una versión existente con el mismo contenido solo la reactiva;
     * cambiar el título, el cuerpo o los alcances de una versión que alguien ya aceptó responde 409, porque cambiaría
     * lo que esa persona aceptó: los cambios van en una versión nueva (D35).
     */
    suspend fun publicar(version: String, titulo: String, cuerpo: String, alcances: List<String>? = null): TextoConsentimiento {
        val versionLimpia = version.trim()
        if (versionLimpia.isEmpty() || versionLimpia.length > LARGO_MAXIMO_VERSION) {
            throw ErrorValidacion("version_invalida", "La versión es obligatoria y de hasta $LARGO_MAXIMO_VERSION caracteres")
        }
        if (titulo.isBlank() || cuerpo.isBlank()) throw ErrorValidacion("missing_fields", "El título y el cuerpo son obligatorios")
        val alcancesLimpios = alcances?.map { it.trim() }
        if (alcancesLimpios != null && alcancesLimpios.any { it.isEmpty() || it.length > LARGO_MAXIMO_ALCANCE }) {
            throw ErrorValidacion("alcance_invalido", "Hay alcances con nombre vacío o demasiado largo")
        }
        val tituloLimpio = titulo.trim()
        val existente = textos.buscar(versionLimpia)
        val sinCambios = existente != null && mismoContenido(existente, tituloLimpio, cuerpo, alcancesLimpios)
        if (existente != null && !sinCambios && consentimientos.contarAceptaciones(versionLimpia) > 0) {
            throw ErrorConflicto(
                "version_con_aceptaciones",
                "La versión $versionLimpia ya fue aceptada por usuarios: publica los cambios como una versión nueva"
            )
        }
        // Sin cambios solo se reactiva: la lista de alcances queda tal cual (ni siquiera cambia su orden).
        return textos.publicar(versionLimpia, tituloLimpio, cuerpo, if (sinCambios) null else alcancesLimpios?.distinct())
    }

    /**
     * Registra lo que el usuario aceptó de [version]. Solo cuentan los alcances que esa versión ofrece: una clave que el
     * texto no ofrece se descarta (y queda en el log) en vez de rechazar el pedido, para que la app pueda seguir
     * aceptando aunque la versión vigente sea anterior a sus opciones. Así nunca se guarda un permiso que el texto no
     * pidió (D35).
     */
    suspend fun otorgar(usuarioId: UUID, version: String, alcances: Map<String, Boolean>, ipOrigen: String?): Consentimiento {
        if (version.isBlank()) throw ErrorValidacion("version_requerida", "La versión es obligatoria")
        if (alcances.isEmpty()) throw ErrorValidacion("alcances_requeridos", "Indica qué alcances aceptas o rechazas")
        if (alcances.keys.any { it.isBlank() || it.length > LARGO_MAXIMO_ALCANCE }) {
            throw ErrorValidacion("alcance_invalido", "Hay alcances con nombre vacío o demasiado largo")
        }
        // Antes una versión inexistente violaba la FK y respondía 500.
        val texto = textos.buscar(version.trim())
            ?: throw ErrorNoEncontrado("version_no_encontrada", "No existe esa versión del texto legal")
        val ofrecidos = texto.alcancesPosibles.toSet()
        val noOfrecidos = alcances.keys.map { it.trim() }.filterNot { it in ofrecidos }
        if (noOfrecidos.isNotEmpty()) {
            log.warn("Consentimiento {}: se descartan alcances que la versión no ofrece: {}", texto.version, noOfrecidos.sorted())
        }
        val aceptados = alcances.filterValues { it }.keys.map { it.trim() }.filter { it in ofrecidos }.distinct().sorted()
        return consentimientos.otorgar(usuarioId, texto.version, aceptados, ipOrigen)
    }

    suspend fun vigente(usuarioId: UUID): Consentimiento? = consentimientos.vigente(usuarioId)

    suspend fun revocar(usuarioId: UUID) {
        if (!consentimientos.revocarVigente(usuarioId)) {
            throw ErrorNoEncontrado("consentimiento_no_encontrado", "No hay consentimiento vigente")
        }
    }

    /**
     * Mapa alcance → aceptado para la app: solo los alcances que ofrece la versión, aceptados en true y el resto en
     * false. Una clave guardada antes de filtrar en [otorgar] (por ejemplo "analytics" sobre la v1.0, que no lo ofrece)
     * no sale: si no, la app la leería como un permiso que el texto nunca pidió (D35).
     */
    suspend fun alcancesComoMapa(consentimiento: Consentimiento): Map<String, Boolean> {
        val posibles = textos.buscar(consentimiento.version)?.alcancesPosibles.orEmpty()
        return posibles.associateWith { it in consentimiento.alcancesAceptados }
    }

    /** Sin [alcances] se conservan los que tenía la versión, así que no cuentan como cambio. */
    private fun mismoContenido(existente: TextoConsentimiento, titulo: String, cuerpo: String, alcances: List<String>?): Boolean =
        existente.titulo == titulo && existente.cuerpo == cuerpo &&
            (alcances == null || alcances.toSet() == existente.alcancesPosibles.toSet())
}
