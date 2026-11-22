package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private const val ESTADO_TRAZA_EXITOSA = "aprobada"
private const val ESTADO_TRAZA_FALLIDA = "error_parse"

/** Lo que necesita la IA para escribir el feedback genérico de una pregunta del banco (D36). */
data class PreguntaParaFeedback(
    val id: UUID,
    val tipo: TipoPregunta,
    val categoria: CategoriaHabilidad,
    val nivel: NivelExperiencia,
    val enunciado: String,
    val respuestaIdeal: String?,
    /** Texto de la opción correcta, solo en opción múltiple. */
    val opcionCorrecta: String?,
    val feedback: FeedbackGenerico
)

/** Una llamada al LLM para completar feedback: se audita en pregunta_generacion_ia y cuenta para el tope mensual (D23). */
data class UsoFeedbackGenerico(
    val modelo: String,
    val prompt: String,
    val respuestaCruda: String?,
    val tokensEntrada: Int?,
    val tokensSalida: Int?,
    val costoUsd: Double?,
    val exitoso: Boolean
)

/** Preguntas del banco y su feedback genérico (D36). */
interface RepositorioFeedbackGenerico {
    /** Preguntas no rechazadas a las que les falta al menos un nivel, las más antiguas primero. */
    suspend fun sinFeedbackCompleto(limite: Int): List<PreguntaParaFeedback>

    suspend fun contarSinFeedbackCompleto(): Int

    /** Las preguntas pedidas que existen, en el orden pedido. */
    suspend fun buscar(ids: List<UUID>): List<PreguntaParaFeedback>

    /**
     * Combina [feedback] con el que ya tiene la pregunta ([sobrescribir] = gana el nuevo) y lo guarda en la rúbrica sin
     * cambiar el estado de la pregunta ni el resto de la rúbrica. false si la pregunta ya no existe.
     */
    suspend fun guardar(id: UUID, feedback: FeedbackGenerico, sobrescribir: Boolean): Boolean

    suspend fun registrarUso(uso: UsoFeedbackGenerico)
}

class RepositorioFeedbackGenericoExposed : RepositorioFeedbackGenerico {

    override suspend fun sinFeedbackCompleto(limite: Int): List<PreguntaParaFeedback> = transaccion {
        cargar(idsSinFeedbackCompleto().take(limite))
    }

    override suspend fun contarSinFeedbackCompleto(): Int = transaccion { idsSinFeedbackCompleto().size }

    override suspend fun buscar(ids: List<UUID>): List<PreguntaParaFeedback> = transaccion { cargar(ids.distinct()) }

    override suspend fun guardar(id: UUID, feedback: FeedbackGenerico, sobrescribir: Boolean): Boolean = transaccion {
        // Se bloquea la fila: si el admin edita la pregunta al mismo tiempo, una escritura espera a la otra.
        val fila = TablaPregunta.select(TablaPregunta.rubricaEvaluacion)
            .where { TablaPregunta.preguntaId eq id }
            .forUpdate()
            .singleOrNull() ?: return@transaccion false
        val rubrica = fila[TablaPregunta.rubricaEvaluacion]
        val combinado = feedbackGenericoDe(rubrica).combinar(feedback, sobrescribir)
        TablaPregunta.update({ TablaPregunta.preguntaId eq id }) {
            it[rubricaEvaluacion] = rubricaConFeedbackGenerico(rubrica, combinado)
        }
        true
    }

    override suspend fun registrarUso(uso: UsoFeedbackGenerico) {
        transaccion {
            TablaGeneracionPreguntaIa.insert {
                it[generacionId] = UUID.randomUUID()
                it[preguntaId] = null
                it[modeloLlm] = uso.modelo
                it[promptEnviado] = uso.prompt
                it[respuestaRaw] = uso.respuestaCruda
                it[parseExitoso] = uso.exitoso
                it[tokensInput] = uso.tokensEntrada
                it[tokensOutput] = uso.tokensSalida
                it[costoUsd] = uso.costoUsd?.let(BigDecimal::valueOf)
                // El CHECK solo admite los estados de revisión: el feedback se aplica directo, sin revisión aparte.
                it[estadoRevision] = if (uso.exitoso) ESTADO_TRAZA_EXITOSA else ESTADO_TRAZA_FALLIDA
                it[fechaGeneracion] = OffsetDateTime.now()
            }
        }
    }

    /** Se filtra en Kotlin con la misma lectura que usa la corrección: lo que ella no ve, aquí cuenta como faltante. */
    private fun idsSinFeedbackCompleto(): List<UUID> =
        TablaPregunta.select(TablaPregunta.preguntaId, TablaPregunta.rubricaEvaluacion)
            .where { TablaPregunta.estado neq EstadoPregunta.RECHAZADA.valorBd }
            .orderBy(TablaPregunta.fechaCreacion to SortOrder.ASC, TablaPregunta.preguntaId to SortOrder.ASC)
            .filter { !feedbackGenericoDe(it[TablaPregunta.rubricaEvaluacion]).completo }
            .map { it[TablaPregunta.preguntaId] }

    private fun cargar(ids: List<UUID>): List<PreguntaParaFeedback> {
        if (ids.isEmpty()) return emptyList()
        val correctas = TablaOpcionPregunta.selectAll()
            .where { (TablaOpcionPregunta.preguntaId inList ids) and (TablaOpcionPregunta.esCorrecta eq true) }
            .associate { it[TablaOpcionPregunta.preguntaId] to it[TablaOpcionPregunta.textoOpcion] }
        val filas = TablaPregunta.selectAll().where { TablaPregunta.preguntaId inList ids }.associateBy { it[TablaPregunta.preguntaId] }
        return ids.mapNotNull { id ->
            val fila = filas[id] ?: return@mapNotNull null
            val tipo = TipoPregunta.desdeBd(fila[TablaPregunta.tipoPregunta]) ?: TipoPregunta.ABIERTA_TEXTO
            PreguntaParaFeedback(
                id = id,
                tipo = tipo,
                categoria = CategoriaHabilidad.desdeBd(fila[TablaPregunta.categoriaHabilidad]) ?: CategoriaHabilidad.TECNICA,
                nivel = NivelExperiencia.desdeBd(fila[TablaPregunta.nivelDificultad]) ?: NivelExperiencia.JUNIOR,
                enunciado = fila[TablaPregunta.enunciado],
                respuestaIdeal = fila[TablaPregunta.respuestaIdeal],
                opcionCorrecta = if (tipo == TipoPregunta.OPCION_MULTIPLE) correctas[id] else null,
                feedback = feedbackGenericoDe(fila[TablaPregunta.rubricaEvaluacion])
            )
        }
    }
}
