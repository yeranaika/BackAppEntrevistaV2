package data.repository.ai

import data.models.ai.LlmQuestion
import data.models.ai.OpcionPregunta
import data.models.ai.RubricaEvaluacion
import data.tables.ai.OpcionPreguntaTable
import data.tables.ai.PreguntaGeneracionIaTable
import data.tables.ai.PreguntaTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import services.ai.AiQuestionBatchPersistenceRequest
import services.ai.PersistableAiQuestion
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AiQuestionRepositoryTest {
    private lateinit var db: Database
    private lateinit var repo: AiQuestionRepository

    @BeforeTest
    fun setup() {
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_ai_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(SkillTable, CargoTable, PreguntaTable, OpcionPreguntaTable, PreguntaGeneracionIaTable)
        }
        repo = AiQuestionRepository(db)
    }

    @Test
    fun `batch persistence stores category normalized options and one success trace per question`() = runBlocking {
        val inserted = repo.insertSuccessfulBatch(batchRequest(listOf(validMultipleQuestion())))

        assertEquals(1, inserted.size)
        transaction(db) {
            assertEquals(1, PreguntaTable.selectAll().count())
            val question = PreguntaTable.selectAll().single()
            assertEquals("blanda", question[PreguntaTable.categoriaHabilidad])
            assertEquals("opcion_multiple", question[PreguntaTable.tipoPregunta])
            assertEquals(true, question[PreguntaTable.generadaPorIa])
            assertEquals("pendiente", question[PreguntaTable.estado])
            assertEquals(null, question[PreguntaTable.contextoEvaluacionIa])

            val options = OpcionPreguntaTable.selectAll().orderBy(OpcionPreguntaTable.orden).toList()
            assertEquals(4, options.size)
            assertEquals(listOf(1, 2, 3, 4), options.map { it[OpcionPreguntaTable.orden].toInt() })
            assertEquals(1, options.count { it[OpcionPreguntaTable.esCorrecta] })

            val trace = PreguntaGeneracionIaTable.selectAll().single()
            assertNotNull(trace[PreguntaGeneracionIaTable.preguntaId])
            assertEquals("claude-haiku-4-5", trace[PreguntaGeneracionIaTable.modeloLlm])
            assertEquals(true, trace[PreguntaGeneracionIaTable.parseExitoso])
            assertEquals(10, trace[PreguntaGeneracionIaTable.tokensInput])
            assertEquals(20, trace[PreguntaGeneracionIaTable.tokensOutput])
        }
    }

    @Test
    fun `batch persistence rolls back all rows when any insert fails`() = runBlocking {
        val invalidQuestion = validMultipleQuestion().copy(tipoPregunta = "tipo_demasiado_largo_para_columna")

        assertFailsWith<Exception> {
            repo.insertSuccessfulBatch(batchRequest(listOf(validMultipleQuestion(), invalidQuestion)))
        }

        transaction(db) {
            assertEquals(0, PreguntaTable.selectAll().count())
            assertEquals(0, OpcionPreguntaTable.selectAll().count())
            assertEquals(0, PreguntaGeneracionIaTable.selectAll().count())
        }
    }

    private fun batchRequest(questions: List<LlmQuestion>) = AiQuestionBatchPersistenceRequest(
        cargoId = UUID.randomUUID(),
        skillId = null,
        nivel = "senior",
        categoria = "blanda",
        modelo = "claude-haiku-4-5",
        promptEnviado = "prompt audit",
        respuestaRaw = "raw audit",
        questions = questions.map { PersistableAiQuestion(it, tokensInput = 10, tokensOutput = 20, costoUsd = 0.0001) }
    )

    private fun validMultipleQuestion() = LlmQuestion(
        enunciado = "Pregunta de selección múltiple significativa",
        tipoPregunta = "opcion_multiple",
        respuestaIdeal = "Situación: contexto. Tarea: responsabilidad. Acción: decisión. Resultado: impacto.",
        rubricaEvaluacion = RubricaEvaluacion(
            metodo = "STAR",
            criterios = listOf("Situación", "Tarea", "Acción", "Resultado"),
            palabrasClave = listOf("decisión", "impacto", "métrica"),
            tiempoEsperadoSeg = 90
        ),
        opciones = listOf(
            OpcionPregunta("Correcta completa", true, "Porque cumple STAR"),
            OpcionPregunta("Distractor uno", false, "Falla en resultado"),
            OpcionPregunta("Distractor dos", false, "Falla en acción"),
            OpcionPregunta("Distractor tres", false, "Falla en situación")
        )
    )
}
