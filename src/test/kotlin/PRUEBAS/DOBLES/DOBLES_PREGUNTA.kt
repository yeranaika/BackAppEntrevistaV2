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

    override suspend fun guardarLote(traza: TrazaGeneracion, preguntas: List<PreguntaGenerada>): List<IdsGenerados> {
        lotes += traza to preguntas
        return preguntas.map { IdsGenerados(UUID.randomUUID(), UUID.randomUUID()) }
    }

    override suspend fun registrarFallo(traza: TrazaGeneracion) {
        if (fallaAlAuditar) error("BD caída")
        fallos += traza
    }
}

/** Arma el JSON de un lote como lo devolvería un LLM, con variantes para probar el validador. */
object LoteLlmDePrueba {
    private val json = Json { encodeDefaults = true }

    const val RESPUESTA_STAR =
        "Situación: existía un problema relevante. Tarea: debía resolverlo. Acción: apliqué una estrategia concreta. Resultado: logré una mejora medible."

    fun json(
        cantidad: Int,
        tipo: String,
        criterios: List<String> = listOf("Situación clara", "Tarea clara", "Acción clara", "Resultado medible"),
        opcionesCorrectas: Int = 1,
        respuestaIdeal: String = RESPUESTA_STAR,
        enunciado: (Int) -> String = { "Pregunta $it con contexto suficiente para evaluar desempeño profesional" },
        opcionesDuplicadas: Boolean = false
    ): String {
        val preguntas = (1..cantidad).map { i ->
            PreguntaLlm(
                enunciado = enunciado(i),
                tipoPregunta = tipo,
                respuestaIdeal = respuestaIdeal,
                rubrica = RubricaLlm("STAR", criterios, listOf("estrategia", "métrica", "colaboración"), 120),
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
