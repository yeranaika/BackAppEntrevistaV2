package SERVICIOS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import INTEGRACIONES.DOCUMENTO_EULA
import INTEGRACIONES.FuenteDocumentosLegales
import MODELOS.ALCANCE_ANALISIS_CAMARA
import MODELOS.BorradorDatosCamara
import MODELOS.Consentimiento
import MODELOS.RepositorioConsentimiento
import MODELOS.RepositorioTextoConsentimiento
import MODELOS.TextoConsentimiento
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private const val VERSION_INICIAL_EULA = "1.2.0"
private const val TITULO_INICIAL_EULA = "Acuerdo de Licencia de Usuario Final (EULA)"
private const val LARGO_MAXIMO_VERSION = 20
private const val LARGO_MAXIMO_ALCANCE = 50

/**
 * Años que se guarda el registro de consentimiento de una cuenta eliminada, contados desde que se eliminó (D38). Cubre
 * los 4 años que tiene la Agencia para perseguir una infracción (art. 40 de la Ley 19.628 modificada por la Ley 21.719)
 * y los 5 de la acción ordinaria del Código Civil (art. 2515). Debe coincidir con la Política de Privacidad.
 */
const val PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS = 5L

/** Si la persona dio el permiso para medir conductas con la cámara (D38). */
interface PermisoCamara {
    suspend fun permite(usuarioId: UUID): Boolean

    /**
     * Se llama después de guardar algo medido con la cámara. Si el permiso se retiró mientras se guardaba, el borrado
     * del retiro pudo no verlo: se borra ahora y devuelve false (lo guardado ya no cuenta).
     */
    suspend fun confirmarDespuesDeGuardar(usuarioId: UUID): Boolean = true

    companion object {
        /** Sin consentimiento de por medio (pruebas que no lo usan): siempre permite. */
        val SIEMPRE: PermisoCamara = object : PermisoCamara {
            override suspend fun permite(usuarioId: UUID) = true
        }
    }
}

/**
 * Texto legal vigente (EULA) y consentimientos de cada usuario.
 * Se guardan los alcances aceptados; los rechazados se infieren de los posibles del texto.
 * También decide el permiso de cámara (D38): sin él no se guarda nada medido con la cámara, y al retirarlo se borra
 * lo que se había medido ([datosCamara]).
 */
class ServicioConsentimiento(
    private val textos: RepositorioTextoConsentimiento,
    private val consentimientos: RepositorioConsentimiento,
    private val documentos: FuenteDocumentosLegales,
    private val datosCamara: BorradorDatosCamara = BorradorDatosCamara { 0 }
) : PermisoCamara {
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
        val aceptadosPedidos = alcances.filterValues { it }.keys.map { it.trim() }.filter { it in ofrecidos }
        val camaraPedida = alcances.entries.firstOrNull { it.key.trim() == ALCANCE_ANALISIS_CAMARA }?.value
        val anterior = consentimientos.vigente(usuarioId)
        // La pantalla general del consentimiento y la de Perfil no editan el permiso de cámara (se da aparte, en
        // "Acepto, activar cámara"): si el pedido no trae la clave, se conserva el que tenía. Solo un false lo retira.
        val conservaCamara = camaraPedida == null && ALCANCE_ANALISIS_CAMARA in ofrecidos &&
            anterior != null && ALCANCE_ANALISIS_CAMARA in anterior.alcancesAceptados
        val aceptados = (aceptadosPedidos + listOfNotNull(ALCANCE_ANALISIS_CAMARA.takeIf { conservaCamara })).distinct().sorted()
        // Se borra antes de guardar el consentimiento: si algo falla después, un reintento vuelve a borrar (D38).
        val permitiraDespues = ALCANCE_ANALISIS_CAMARA !in ofrecidos || ALCANCE_ANALISIS_CAMARA in aceptados
        if (camaraPedida == false || (!permitiraDespues && permite(usuarioId))) {
            borrarDatosCamara(usuarioId, "no tiene el permiso de cámara")
        }
        return consentimientos.otorgar(usuarioId, texto.version, aceptados, ipOrigen)
    }

    suspend fun vigente(usuarioId: UUID): Consentimiento? = consentimientos.vigente(usuarioId)

    /**
     * Retirar el consentimiento retira también el permiso de cámara: se borra lo medido con ella (D38). Se borra antes de
     * revocar y siempre, así un reintento (aunque ya no haya consentimiento vigente y responda 404) vuelve a borrar.
     */
    suspend fun revocar(usuarioId: UUID) {
        borrarDatosCamara(usuarioId, "retiró el consentimiento")
        if (!consentimientos.revocarVigente(usuarioId)) {
            throw ErrorNoEncontrado("consentimiento_no_encontrado", "No hay consentimiento vigente")
        }
    }

    /**
     * Si se puede guardar lo que se mide con la cámara (D38). Cuando la versión del consentimiento de la persona (o la
     * vigente, si todavía no aceptó ninguna) ofrece `analisis_conducta_camara`, hace falta haberlo aceptado. Una
     * versión que no lo ofrece (la v1.0) es anterior al permiso aparte y sigue como antes.
     */
    override suspend fun permite(usuarioId: UUID): Boolean {
        val consentimiento = consentimientos.vigente(usuarioId)
        val version = consentimiento?.version ?: textos.vigente()?.version ?: return true
        val ofrece = textos.buscar(version)?.alcancesPosibles.orEmpty().contains(ALCANCE_ANALISIS_CAMARA)
        if (!ofrece) return true
        return consentimiento != null && ALCANCE_ANALISIS_CAMARA in consentimiento.alcancesAceptados
    }

    /**
     * Borra los registros de consentimiento de cuentas eliminadas hace más de [PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS]
     * años, salvo los que tengan retención legal (D38). Lo llama una vez al día TareaPurgaConsentimientos.
     */
    suspend fun purgarRegistrosVencidos(ahora: Instant): Int {
        val limite = ahora.atOffset(ZoneOffset.UTC).minusYears(PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS).toInstant()
        val borrados = consentimientos.purgarCuentasEliminadas(limite)
        if (borrados > 0) log.info("Se borraron {} registros de consentimiento de cuentas eliminadas antes de {}", borrados, limite)
        return borrados
    }

    override suspend fun confirmarDespuesDeGuardar(usuarioId: UUID): Boolean {
        if (permite(usuarioId)) return true
        borrarDatosCamara(usuarioId, "perdió el permiso de cámara mientras se guardaba")
        return false
    }

    private suspend fun borrarDatosCamara(usuarioId: UUID, motivo: String) {
        val borradas = datosCamara.borrarDeUsuario(usuarioId)
        log.info("Permiso de cámara: el usuario {} {}; se borraron {} filas medidas con la cámara", usuarioId, motivo, borradas)
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
