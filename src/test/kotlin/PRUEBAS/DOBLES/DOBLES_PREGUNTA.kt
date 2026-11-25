package PRUEBAS.DOBLES

import ESQUEMAS.LoteLlm
import ESQUEMAS.OpcionLlm
import ESQUEMAS.PreguntaLlm
import ESQUEMAS.RubricaLlm
import INTEGRACIONES.ProveedorPreguntasIa
import INTEGRACIONES.RespuestaProveedorIa
import INTEGRACIONES.SolicitudProveedorIa
import MODELOS.IdsGenerados
import MODELOS.LectorCatalogo
import MODELOS.PreguntaGenerada
import MODELOS.RepositorioGeneracionPreguntaIa
import MODELOS.TrazaGeneracion
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Catálogo con cargos y skills conocidos; cualquier otro id no existe. */
class CatalogoEnMemoria(
    val cargos: MutableMap<UUID, String> = mutableMapOf(),
    val skills: MutableMap<UUID, String> = mutableMapOf()
) : LectorCatalogo {
    override suspend fun nombreCargo(id: UUID) = cargos[id]
    override suspend fun nombreSkill(id: UUID) = skills[id]
}

/** Devuelve siempre el mismo contenido (o lanza [error]) y guarda lo que se le pidió. */
class ProveedorIaGrabador(
    private val contenido: String = "",
    private val tokensEntrada: Int? = 100,
    private val tokensSalida: Int? = 200,
    private val error: Exception? = null
) : ProveedorPreguntasIa {
    val solicitudes = mutableListOf<SolicitudProveedorIa>()

    override suspend fun generar(solicitud: SolicitudProveedorIa): RespuestaProveedorIa {
        solicitudes += solicitud
        error?.let { throw it }
        return RespuestaProveedorIa(contenido, tokensEntrada, tokensSalida)
    }
}

class GeneracionEnMemoria(private val fallaAlAuditar: Boolean = false) : RepositorioGeneracionPreguntaIa {
    val lotes = mutableListOf<Pair<TrazaGeneracion, List<PreguntaGenerada>>>()
    val fallos = mutableListOf<TrazaGeneracion>()
    /** Gasto ya registrado (para probar el tope mensual) y los rangos que consultó el servicio. */
    val usosPrevios = mutableListOf<MODELOS.UsoIaRegistrado>()
    val rangosConsultados = mutableListOf<Pair<java.time.Instant, java.time.Instant>>()

    override suspend fun usosEntre(desde: java.time.Instant, hasta: java.time.Instant): List<MODELOS.UsoIaRegistrado> {
        rangosConsultados += desde to hasta
        return usosPrevios.filter { it.fecha >= desde && it.fecha < hasta }
    }

    override suspend fun guardarLote(traza: TrazaGeneracion, preguntas: List<PreguntaGenerada>): List<IdsGenerados> {
        lotes += traza to preguntas
        return preguntas.map { IdsGenerados(UUID.randomUUID(), UUID.randomUUID()) }
    }

    /** Lo que "hay en el banco" para comparar; con [filtraSimilares] el doble hace lo mismo que el repositorio real. */
    val existentes = mutableListOf<MODELOS.PreguntaExistente>()
    var filtraSimilares = false

    override suspend fun existentesParaComparar(alcance: MODELOS.AlcanceComparacion) = existentes.toList()

    override suspend fun guardarLoteSinSimilares(
        traza: TrazaGeneracion,
        preguntas: List<PreguntaGenerada>,
        alcance: MODELOS.AlcanceComparacion,
        separar: (List<MODELOS.PreguntaExistente>) -> List<MODELOS.PreguntaSimilar?>
    ): MODELOS.LoteGuardado {
        if (!filtraSimilares) return MODELOS.LoteGuardado(guardarLote(traza, preguntas), emptyList())
        val coincidencias = separar(existentes)
        val nuevas = preguntas.filterIndexed { i, _ -> coincidencias[i] == null }
        return MODELOS.LoteGuardado(
            if (nuevas.isEmpty()) emptyList() else guardarLote(traza, nuevas),
            coincidencias.mapIndexedNotNull { i, c -> c?.let { MODELOS.DescartadaPorSimilar(i, it) } }
        )
    }

    override suspend fun registrarFallo(traza: TrazaGeneracion) {
        if (fallaAlAuditar) error("BD caída")
        fallos += traza
    }
}

/** Arma el JSON de un lote como lo devolvería un LLM, con variantes para probar el validador. */
object LoteLlmDePrueba {
    private val json = Json { encodeDefaults = true }

    /** Enunciados sin palabras en común: el filtro de preguntas parecidas (D37) no debe descartarlos entre sí. */
    private val ENUNCIADOS_DISTINTOS = listOf(
        "Cuéntame cómo lideraste a tu equipo durante un cambio inesperado de prioridades",
        "Describe un error grave que cometiste y qué aprendiste después",
        "Háblame de una vez que debiste negociar plazos imposibles con un cliente",
        "Relata una situación donde recibiste una crítica dura sobre tu trabajo",
        "Explica cómo conviertes una idea innovadora en un producto entregable",
        "Narra un momento en que tuviste que enseñar una tecnología nueva a colegas",
        "Detalla cómo manejaste presión extrema antes de una entrega crítica",
        "Comparte una experiencia resolviendo desacuerdos técnicos con un compañero",
        "Cuenta cómo organizas tu semana cuando todo parece urgente",
        "Describe cuándo asumiste responsabilidad por un fracaso colectivo"
    )

    const val RESPUESTA_STAR =
        "Situación: existía un problema relevante. Tarea: debía resolverlo. Acción: apliqué una estrategia concreta. Resultado: logré una mejora medible."

    /** Feedback genérico completo (D36) que trae cada pregunta del lote si no se indica otro. */
    val FEEDBACK_DE_PRUEBA = MODELOS.FeedbackGenerico(
        correcto = "Muy bien: explicaste la situación y el resultado con claridad.",
        medio = "Vas bien, pero te faltó contar el resultado que lograste.",
        malo = "Repasa cómo contar una experiencia con situación, tarea, acción y resultado."
    )

    /** Respuesta del LLM al completar feedback: un objeto por número; [numeros] permite repetir o saltar números. */
    fun feedbackJson(numeros: List<Int>, texto: (Int) -> String = { "Texto $it de feedback para la pregunta." }): String {
        val items = numeros.joinToString(",") { n ->
            val t = Json.encodeToString(String.serializer(), texto(n))
            """{"numero":$n,"correcto":$t,"medio":$t,"malo":$t}"""
        }
        return """{"feedbacks":[$items]}"""
    }

    fun json(
        cantidad: Int,
        tipo: String,
        criterios: List<String> = listOf("Situación clara", "Tarea clara", "Acción clara", "Resultado medible"),
        opcionesCorrectas: Int = 1,
        respuestaIdeal: String = RESPUESTA_STAR,
        enunciado: (Int) -> String = { ENUNCIADOS_DISTINTOS[(it - 1) % ENUNCIADOS_DISTINTOS.size] },
        opcionesDuplicadas: Boolean = false,
        feedbackGenerico: (Int) -> MODELOS.FeedbackGenerico? = { FEEDBACK_DE_PRUEBA }
    ): String {
        val preguntas = (1..cantidad).map { i ->
            PreguntaLlm(
                enunciado = enunciado(i),
                tipoPregunta = tipo,
                respuestaIdeal = respuestaIdeal,
                rubrica = RubricaLlm(
                    "STAR", criterios, listOf("estrategia", "métrica", "colaboración"), 120, feedbackGenerico = feedbackGenerico(i)
                ),
                opciones = if (tipo != "opcion_multiple") null else (1..4).map { o ->
                    OpcionLlm(
                        texto = if (opcionesDuplicadas) "Opción repetida" else "Opción $o con contenido significativo",
                        esCorrecta = o <= opcionesCorrectas,
                        explicacion = "Explicación $o detallada"
                    )
                }
            )
        }
        return json.encodeToString(LoteLlm(preguntas))
    }
}
