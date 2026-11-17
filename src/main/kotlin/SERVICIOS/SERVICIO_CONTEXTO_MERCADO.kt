package SERVICIOS

import INTEGRACIONES.OfertaLaboral
import INTEGRACIONES.OfertasEnCache
import INTEGRACIONES.claveOfertas
import INTEGRACIONES.sonDeRespaldo
import MODELOS.CATEGORIA_BLANDA
import MODELOS.CategoriaHabilidad
import MODELOS.LectorMercado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MAXIMO_FRAGMENTOS = 6
private const val FRAGMENTOS_POR_OFERTA = 2
private const val LARGO_MINIMO_FRAGMENTO = 25
private const val LARGO_MAXIMO_FRAGMENTO = 220
private const val LARGO_MAXIMO_TITULO = 80
private const val MAXIMO_SKILLS_FRECUENTES = 8
private const val SKILLS_DEL_CARGO_PARA_SER_RELEVANTE = 2

/** Un trozo de una oferta real que dice qué se pide (texto de terceros: el prompt lo marca como datos). */
data class FragmentoOferta(val texto: String, val tituloOferta: String, val fuente: String)

data class SkillEnOfertas(val nombre: String, val ofertas: Int)

/** Lo que piden las ofertas reales de un cargo (o de una skill), listo para el prompt y para mostrarlo en el panel. */
data class ContextoMercado(
    /** Lo que se buscó en las APIs de empleo (el nombre del cargo o, sin cargo, el de la skill). */
    val consulta: String,
    /** Ofertas que hablan del cargo o de la skill (las demás se descartan). */
    val ofertasAnalizadas: Int,
    val fuentes: List<String>,
    val skillsFrecuentes: List<SkillEnOfertas>,
    val fragmentos: List<FragmentoOferta>,
    /** Salieron del dataset de respaldo porque ninguna API de empleo respondió. */
    val esRespaldo: Boolean
)

/** Por qué una generación no usó las ofertas de empleo. */
enum class MotivoSinMercado(val codigo: String, val mensaje: String) {
    DESACTIVADO("desactivado", "Se pidió generar sin mirar las ofertas de empleo."),
    SIN_CONSULTA("sin_cargo_ni_skill", "Sin cargo ni skill no hay ofertas que buscar."),
    SIN_RESPUESTA(
        "apis_sin_respuesta",
        "Las APIs de empleo no respondieron a tiempo: se generó sin ofertas. La búsqueda sigue y la próxima generación ya las tendrá."
    ),
    SIN_OFERTAS("sin_ofertas", "Las APIs de empleo no devolvieron ofertas para esta búsqueda: se generó sin ofertas."),
    SIN_OFERTAS_RELEVANTES("sin_ofertas_relevantes", "Las ofertas encontradas no hablan de este cargo ni de esta skill: se generó sin ofertas."),
    SIN_MENCIONES(
        "sin_menciones",
        "Las ofertas del cargo no dicen nada útil para preguntas de esta categoría (por ejemplo, ninguna habilidad blanda): se generó sin ofertas."
    ),
    ERROR("error_mercado", "No se pudieron leer las ofertas de empleo: se generó sin ofertas.")
}

data class ResultadoContextoMercado(val contexto: ContextoMercado?, val motivo: MotivoSinMercado?) {
    companion object {
        fun sin(motivo: MotivoSinMercado) = ResultadoContextoMercado(null, motivo)
    }
}

fun interface FuenteContextoMercado {
    /**
     * Nunca falla: si no hay ofertas útiles devuelve el motivo y la generación sigue sin ellas. [categoria] filtra lo
     * que se entrega: unas preguntas blandas no reciben Kotlin ni Docker como "lo que pide el mercado".
     */
    suspend fun contexto(cargoId: UUID?, nombreCargo: String?, nombreSkill: String?, categoria: CategoriaHabilidad): ResultadoContextoMercado
}

/**
 * Arma el contexto de mercado para generar preguntas (D26). Usa primero las ofertas que dejó guardadas la
 * sincronización semanal (no gasta cuota de las APIs de empleo). Si no hay, las busca con un tiempo máximo corto: la
 * generación pasa por el proxy del panel (55 s) y además tiene que esperar al LLM. Si la búsqueda no alcanza, la
 * generación sigue sin ofertas y la búsqueda continúa en segundo plano para dejar la caché lista.
 */
class ServicioContextoMercado(
    private val ofertas: OfertasEnCache,
    private val mercado: LectorMercado,
    private val tiempoMaximo: Duration = 6.seconds,
    private val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : FuenteContextoMercado, AutoCloseable {

    private val log = LoggerFactory.getLogger(ServicioContextoMercado::class.java)

    /** Una sola búsqueda en curso por consulta: dos admins generando para el mismo cargo no gastan cuota dos veces. */
    private val enCurso = ConcurrentHashMap<String, Deferred<List<OfertaLaboral>>>()

    override suspend fun contexto(
        cargoId: UUID?,
        nombreCargo: String?,
        nombreSkill: String?,
        categoria: CategoriaHabilidad
    ): ResultadoContextoMercado = try {
        armar(cargoId, nombreCargo?.trim()?.takeIf { it.isNotEmpty() }, nombreSkill?.trim()?.takeIf { it.isNotEmpty() }, categoria)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("No se pudo armar el contexto de mercado ({})", e.javaClass.simpleName)
        ResultadoContextoMercado.sin(MotivoSinMercado.ERROR)
    }

    private suspend fun armar(cargoId: UUID?, cargo: String?, skill: String?, categoria: CategoriaHabilidad): ResultadoContextoMercado {
        val consulta = cargo ?: skill ?: return ResultadoContextoMercado.sin(MotivoSinMercado.SIN_CONSULTA)
        val encontradas = ofertas.guardadas(consulta) ?: buscarConTope(consulta)
            ?: return ResultadoContextoMercado.sin(MotivoSinMercado.SIN_RESPUESTA)
        if (encontradas.isEmpty()) return ResultadoContextoMercado.sin(MotivoSinMercado.SIN_OFERTAS)

        val requisitos = if (cargoId != null && skill == null) mercado.listarRequisitos(cargoId) else emptyList()
        val relevantes = encontradas.filter { esRelevante(it, cargo, skill, requisitos.map { r -> r.skill.nombre }) }
        if (relevantes.isEmpty()) return ResultadoContextoMercado.sin(MotivoSinMercado.SIN_OFERTAS_RELEVANTES)

        // Solo lo de la categoría pedida: unas preguntas blandas no deben recibir Kotlin ni Docker como guía.
        val esBlanda = categoria == CategoriaHabilidad.BLANDA
        val deLaCategoria: (String) -> Boolean = { nombre -> (nombre in NormalizadorSkill.SKILLS_BLANDAS) == esBlanda }
        val guias = when {
            skill != null -> listOf(skill)
            else -> requisitos.filter { (it.skill.categoria == CATEGORIA_BLANDA) == esBlanda }.map { it.skill.nombre }
                .ifEmpty { if (esBlanda) NormalizadorSkill.SKILLS_BLANDAS.toList() else emptyList() }
        }
        // Con skill o con skills del cargo, solo frases que las nombran; si no, frases con alguna skill de la categoría.
        val fraseUtil: (String) -> Boolean = if (guias.isNotEmpty()) {
            { frase -> guias.any { NormalizadorSkill.mencionaSkill(frase, it) } }
        } else {
            { frase -> NormalizadorSkill.extraerSkills(frase).keys.any(deLaCategoria) }
        }
        val frecuentes = NormalizadorSkill.analizarOfertas(relevantes).entries
            .filter { deLaCategoria(it.key) }
            .sortedWith(compareByDescending<Map.Entry<String, EstadisticaSkill>> { it.value.ofertasConSkill }.thenBy { it.key })
            .take(MAXIMO_SKILLS_FRECUENTES)
            .map { SkillEnOfertas(it.key, it.value.ofertasConSkill) }
        val fragmentos = ExtractorFragmentos.extraer(relevantes, fraseUtil)
        if (frecuentes.isEmpty() && fragmentos.isEmpty()) return ResultadoContextoMercado.sin(MotivoSinMercado.SIN_MENCIONES)

        return ResultadoContextoMercado(
            ContextoMercado(
                consulta = consulta,
                ofertasAnalizadas = relevantes.size,
                fuentes = relevantes.map { it.fuente }.distinct(),
                skillsFrecuentes = frecuentes,
                fragmentos = fragmentos,
                esRespaldo = sonDeRespaldo(relevantes)
            ),
            null
        )
    }

    /**
     * Una oferta sirve si habla de lo que se va a preguntar. Con skill: si la menciona. Solo con cargo: si su título
     * comparte una palabra con el cargo o si pide varias skills del cargo. Así no se usan ofertas que una API devolvió sin
     * filtrar (ni el dataset de respaldo, que es de backend) para un cargo de otra área.
     */
    private fun esRelevante(oferta: OfertaLaboral, cargo: String?, skill: String?, skillsDelCargo: List<String>): Boolean {
        if (skill != null) return NormalizadorSkill.mencionaSkill(oferta.textoCompleto, skill)
        if (cargo == null) return false
        if (PalabrasCargo.compartenPalabra(cargo, oferta.titulo)) return true
        if (skillsDelCargo.isEmpty()) return false
        val necesarias = minOf(SKILLS_DEL_CARGO_PARA_SER_RELEVANTE, skillsDelCargo.size)
        return skillsDelCargo.count { NormalizadorSkill.mencionaSkill(oferta.textoCompleto, it) } >= necesarias
    }

    /** null si la búsqueda no terminó a tiempo (sigue en segundo plano y guarda el resultado en la caché). */
    private suspend fun buscarConTope(consulta: String): List<OfertaLaboral>? {
        val clave = claveOfertas(consulta)
        // LAZY: se registra antes de arrancar, así nunca queda en el mapa una búsqueda que ya terminó.
        val nueva = alcance.async(start = CoroutineStart.LAZY) { ofertas.buscarOfertas(consulta) }
        val existente = enCurso.putIfAbsent(clave, nueva)
        val busqueda = if (existente != null) {
            nueva.cancel()
            existente
        } else {
            nueva.invokeOnCompletion { enCurso.remove(clave, nueva) }
            nueva.start()
            nueva
        }
        return try {
            withTimeoutOrNull(tiempoMaximo) { busqueda.await() }.also {
                if (it == null) log.info("Las APIs de empleo no respondieron en {}; se genera sin ofertas", tiempoMaximo)
            }
        } catch (e: CancellationException) {
            // Si se canceló quien espera (el admin cerró la página) se propaga; la búsqueda sigue en su alcance.
            currentCoroutineContext().ensureActive()
            null // se canceló la búsqueda (apagado del servidor): sin ofertas
        } catch (e: Exception) {
            log.warn("La búsqueda de ofertas falló ({})", e.javaClass.simpleName)
            null
        }
    }

    override fun close() = alcance.cancel()
}

/** Palabras de un cargo comparadas entre español e inglés ("Desarrollador Backend" ~ "Backend Developer"). */
internal object PalabrasCargo {
    private val VACIAS = setOf(
        "para", "with", "and", "the", "del", "las", "los", "con", "junior", "senior", "semisenior", "semi", "remote",
        "remoto", "full", "time", "part", "lead", "jefe", "head", "trainee", "intern", "practicante"
    )

    // Traducciones de las palabras más comunes de los cargos del catálogo.
    private val EQUIVALENTES = mapOf(
        "desarrollador" to "developer", "desarrolladora" to "developer", "programador" to "developer",
        "ingeniero" to "engineer", "ingeniera" to "engineer", "analista" to "analyst", "datos" to "data",
        "administrador" to "administrator", "administradora" to "administrator", "soporte" to "support",
        "sistemas" to "systems", "contable" to "accountant", "contador" to "accountant", "contadora" to "accountant",
        "ventas" to "sales", "negocios" to "business", "calidad" to "quality", "pruebas" to "testing",
        "diseñador" to "designer", "disenador" to "designer", "asistente" to "assistant", "administrativo" to "administrative",
        "proyectos" to "project", "producto" to "product", "seguridad" to "security", "redes" to "network",
        "movil" to "mobile", "móvil" to "mobile", "funcional" to "functional", "infraestructura" to "infrastructure"
    )

    // Palabras de rol: las comparten cargos muy distintos ("Frontend Developer" y "Backend Developer"), así que no
    // alcanzan para decir que una oferta es del cargo.
    private val ROLES = setOf(
        "developer", "engineer", "manager", "analyst", "specialist", "consultant", "assistant", "administrator",
        "designer", "architect", "technician", "coordinator", "executive", "officer", "scientist", "expert"
    )

    // Siglas cortas que sí distinguen un cargo (con menos de 4 letras se descartarían).
    private val SIGLAS = setOf("ml", "qa", "ux", "ui", "ios", "sre", "bi", "ai", "ia", "net", "seo", "erp", "crm", "sap")

    fun palabras(texto: String): Set<String> =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { (it.length >= 4 || it in SIGLAS) && it !in VACIAS }
            .map { EQUIVALENTES[it] ?: it }
            .toSet()

    /** Comparten al menos una palabra que no es de rol: "Desarrollador Backend" ~ "Backend Engineer", no ~ "Frontend Developer". */
    fun compartenPalabra(cargo: String, titulo: String): Boolean =
        (palabras(cargo) intersect palabras(titulo)).any { it !in ROLES }
}

/** Saca de las ofertas frases cortas que dicen qué se pide, repartidas entre ofertas distintas. */
internal object ExtractorFragmentos {
    // Las descripciones vienen sin HTML: las etiquetas de bloque (p, li, br…) quedaron como saltos de línea y las de
    // texto (strong, a…) como un espacio, así que una frase con <strong>Kotlin</strong> no se parte.
    private val CORTES = Regex("""(?<=[.!?;:])\s+|[\r\n]+|\s*[•·▪●■►✓✔]\s*""")
    private val CONTROL = Regex("""[\p{Cc}\p{Cf}]""")
    // Sin caracteres que puedan cerrar o abrir bloques en el prompt (cada fragmento va entre comillas).
    private val CONFLICTIVOS = Regex("""[`{}<>\[\]"\\]""")
    private val ESPACIOS = Regex("""\s+""")

    fun limpiar(texto: String): String =
        texto.replace(CONTROL, " ").replace(CONFLICTIVOS, " ").replace(ESPACIOS, " ").trim().trim('-', '*', ',', ' ')

    fun recortar(texto: String, largoMaximo: Int = LARGO_MAXIMO_FRAGMENTO): String {
        if (texto.length <= largoMaximo) return texto
        val corte = texto.take(largoMaximo).substringBeforeLast(' ').trimEnd(',', ';', ':', ' ')
        return "$corte…"
    }

    fun extraer(
        ofertas: List<OfertaLaboral>,
        esUtil: (String) -> Boolean,
        maximo: Int = MAXIMO_FRAGMENTOS,
        porOferta: Int = FRAGMENTOS_POR_OFERTA
    ): List<FragmentoOferta> {
        val vistos = mutableSetOf<String>()
        val candidatos = ofertas.map { oferta ->
            oferta to oferta.descripcion.split(CORTES).asSequence()
                .map(::limpiar)
                .filter { it.length >= LARGO_MINIMO_FRAGMENTO && esUtil(it) }
                .map { recortar(it) }
                .toList()
        }
        val resultado = mutableListOf<FragmentoOferta>()
        val usados = IntArray(candidatos.size)
        val tomados = IntArray(candidatos.size)
        // Por vueltas: una frase de cada oferta por vuelta, para que no salgan todas de la misma.
        repeat(porOferta) {
            candidatos.forEachIndexed { i, (oferta, frases) ->
                while (usados[i] < frases.size && tomados[i] <= it) {
                    val frase = frases[usados[i]++]
                    if (vistos.add(frase.lowercase())) {
                        resultado += FragmentoOferta(frase, recortar(limpiar(oferta.titulo), LARGO_MAXIMO_TITULO), oferta.fuente)
                        tomados[i]++
                        if (resultado.size >= maximo) return resultado
                    }
                }
            }
        }
        return resultado
    }
}
