package data.repository.ai

import data.tables.ai.OpcionPreguntaTable
import data.tables.ai.PreguntaGeneracionIaTable
import data.tables.ai.PreguntaTable
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import services.ai.AiFailedTrace
import services.ai.AiQuestionBatchPersistenceRequest
import services.ai.AiQuestionPersistence
import services.ai.InsertedQuestion
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class AiQuestionRepository(
    private val db: Database? = null
) : AiQuestionPersistence {
    private val json = Json { encodeDefaults = true }

    private suspend fun <T> tx(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }

    override suspend fun insertSuccessfulBatch(request: AiQuestionBatchPersistenceRequest): List<InsertedQuestion> = tx {
        val now = OffsetDateTime.now()
        request.questions.map { item ->
            val preguntaId = UUID.randomUUID()
            val generacionId = UUID.randomUUID()
            val rubricaJson: JsonObject = json.encodeToJsonElement(item.question.rubricaEvaluacion).jsonObject

            PreguntaTable.insert {
                it[PreguntaTable.preguntaId] = preguntaId
                it[PreguntaTable.skillId] = request.skillId
                it[PreguntaTable.cargoId] = request.cargoId
                it[PreguntaTable.tipoPregunta] = item.question.tipoPregunta
                it[PreguntaTable.categoriaHabilidad] = request.categoria
                it[PreguntaTable.nivelDificultad] = request.nivel
                it[PreguntaTable.enunciado] = item.question.enunciado
                it[PreguntaTable.respuestaIdeal] = item.question.respuestaIdeal
                it[PreguntaTable.rubricaEvaluacion] = rubricaJson
                it[PreguntaTable.contextoEvaluacionIa] = null
                it[PreguntaTable.generadaPorIa] = true
                it[PreguntaTable.estado] = "pendiente"
                it[PreguntaTable.fechaCreacion] = now
            }

            item.question.opciones.orEmpty().forEachIndexed { index, option ->
                OpcionPreguntaTable.insert {
                    it[OpcionPreguntaTable.opcionId] = UUID.randomUUID()
                    it[OpcionPreguntaTable.preguntaId] = preguntaId
                    it[OpcionPreguntaTable.textoOpcion] = option.texto.trim()
                    it[OpcionPreguntaTable.esCorrecta] = option.esCorrecta
                    it[OpcionPreguntaTable.explicacion] = option.explicacion.trim()
                    it[OpcionPreguntaTable.orden] = (index + 1).toShort()
                }
            }

            PreguntaGeneracionIaTable.insert {
                it[PreguntaGeneracionIaTable.generacionId] = generacionId
                it[PreguntaGeneracionIaTable.preguntaId] = preguntaId
                it[PreguntaGeneracionIaTable.cargoId] = request.cargoId
                it[PreguntaGeneracionIaTable.skillId] = request.skillId
                it[PreguntaGeneracionIaTable.nivelSolicitado] = request.nivel
                it[PreguntaGeneracionIaTable.modeloLlm] = request.modelo
                it[PreguntaGeneracionIaTable.promptEnviado] = request.promptEnviado
                it[PreguntaGeneracionIaTable.respuestaRaw] = request.respuestaRaw
                it[PreguntaGeneracionIaTable.parseExitoso] = true
                it[PreguntaGeneracionIaTable.tokensInput] = item.tokensInput
                it[PreguntaGeneracionIaTable.tokensOutput] = item.tokensOutput
                it[PreguntaGeneracionIaTable.costoUsd] = item.costoUsd?.let(BigDecimal::valueOf)
                it[PreguntaGeneracionIaTable.estadoRevision] = "pendiente_revision"
                it[PreguntaGeneracionIaTable.fechaGeneracion] = now
            }

            InsertedQuestion(preguntaId, generacionId)
        }
    }

    override suspend fun insertFailedTrace(trace: AiFailedTrace): Unit = tx {
        PreguntaGeneracionIaTable.insert {
            it[PreguntaGeneracionIaTable.generacionId] = UUID.randomUUID()
            it[PreguntaGeneracionIaTable.preguntaId] = null
            it[PreguntaGeneracionIaTable.cargoId] = trace.cargoId
            it[PreguntaGeneracionIaTable.skillId] = trace.skillId
            it[PreguntaGeneracionIaTable.nivelSolicitado] = trace.nivel
            it[PreguntaGeneracionIaTable.modeloLlm] = trace.modelo
            it[PreguntaGeneracionIaTable.promptEnviado] = trace.promptEnviado
            it[PreguntaGeneracionIaTable.respuestaRaw] = trace.respuestaRaw
            it[PreguntaGeneracionIaTable.parseExitoso] = false
            it[PreguntaGeneracionIaTable.tokensInput] = trace.tokensInput
            it[PreguntaGeneracionIaTable.tokensOutput] = trace.tokensOutput
            it[PreguntaGeneracionIaTable.costoUsd] = null
            it[PreguntaGeneracionIaTable.estadoRevision] = "error_parse"
            it[PreguntaGeneracionIaTable.fechaGeneracion] = OffsetDateTime.now()
        }
    }
}
