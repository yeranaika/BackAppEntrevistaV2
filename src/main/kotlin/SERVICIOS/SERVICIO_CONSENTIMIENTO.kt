package SERVICIOS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import INTEGRACIONES.DOCUMENTO_EULA
import INTEGRACIONES.FuenteDocumentosLegales
import MODELOS.Consentimiento
import MODELOS.RepositorioConsentimiento
import MODELOS.RepositorioTextoConsentimiento
import MODELOS.TextoConsentimiento
import java.util.UUID

private const val VERSION_INICIAL_EULA = "1.0.0"
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

    /** Si la BD aún no tiene texto, publica el EULA de docs/legal como versión inicial. */
    suspend fun textoVigente(): TextoConsentimiento =
        textos.vigente() ?: textos.publicar(
            VERSION_INICIAL_EULA,
            TITULO_INICIAL_EULA,
            documentos.leer(DOCUMENTO_EULA)
                ?: throw ErrorServicioExterno("eula_no_disponible", "No hay texto legal publicado ni archivo EULA disponible")
        )

    suspend fun versiones(): List<TextoConsentimiento> = textos.listar()

    suspend fun publicar(version: String, titulo: String, cuerpo: String): TextoConsentimiento {
        val versionLimpia = version.trim()
        if (versionLimpia.isEmpty() || versionLimpia.length > LARGO_MAXIMO_VERSION) {
            throw ErrorValidacion("version_invalida", "La versión es obligatoria y de hasta $LARGO_MAXIMO_VERSION caracteres")
        }
        if (titulo.isBlank() || cuerpo.isBlank()) throw ErrorValidacion("missing_fields", "El título y el cuerpo son obligatorios")
        return textos.publicar(versionLimpia, titulo.trim(), cuerpo)
    }

    suspend fun otorgar(usuarioId: UUID, version: String, alcances: Map<String, Boolean>, ipOrigen: String?): Consentimiento {
        if (version.isBlank()) throw ErrorValidacion("version_requerida", "La versión es obligatoria")
        if (alcances.isEmpty()) throw ErrorValidacion("alcances_requeridos", "Indica qué alcances aceptas o rechazas")
        if (alcances.keys.any { it.isBlank() || it.length > LARGO_MAXIMO_ALCANCE }) {
            throw ErrorValidacion("alcance_invalido", "Hay alcances con nombre vacío o demasiado largo")
        }
        // Antes una versión inexistente violaba la FK y respondía 500.
        textos.buscar(version.trim()) ?: throw ErrorNoEncontrado("version_no_encontrada", "No existe esa versión del texto legal")
        val aceptados = alcances.filterValues { it }.keys.sorted()
        return consentimientos.otorgar(usuarioId, version.trim(), aceptados, ipOrigen)
    }

    suspend fun vigente(usuarioId: UUID): Consentimiento? = consentimientos.vigente(usuarioId)

    suspend fun revocar(usuarioId: UUID) {
        if (!consentimientos.revocarVigente(usuarioId)) {
            throw ErrorNoEncontrado("consentimiento_no_encontrado", "No hay consentimiento vigente")
        }
    }

    /** Mapa alcance → aceptado para la app: aceptados en true y el resto de los posibles en false. */
    suspend fun alcancesComoMapa(consentimiento: Consentimiento): Map<String, Boolean> {
        val posibles = textos.buscar(consentimiento.version)?.alcancesPosibles.orEmpty()
        return (posibles + consentimiento.alcancesAceptados).associateWith { it in consentimiento.alcancesAceptados }
    }
}
