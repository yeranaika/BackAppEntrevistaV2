package services.ai

import data.models.ai.GenerateQuestionsReq
import data.models.ai.LlmQuestion
import data.models.ai.OpcionPregunta
import data.models.ai.RubricaEvaluacion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import services.AiQuestionGeneratorService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.math.abs

class AiQuestionGeneratorServiceTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `legacy claude alias generates exactly requested valid questions and persists actual model`() = runBlocking {
        val persistence = RecordingPersistence()
        val provider = RecordingProvider(
            rawContent = batchJson(10, "opcion_multiple"),
            actualModel = "claude-haiku-4-5",
            promptTokens = 101,
            completionTokens = 203
        )
        val service = AiQuestionGeneratorService(
            openAiKey = "",
            anthropicKey = "",
            persistence = persistence,
            providers = mapOf(AiProviderKind.ANTHROPIC to provider)
        )

        val result = service.generate(validReq(cantidad = 10, tipo = "opcion_multiple", modelo = "claude-3-5-haiku"))

        assertEquals(10, result.inserted.size)
        assertEquals(0, result.errores)
        assertEquals("claude-haiku-4-5", provider.requests.single().model)
        assertEquals("claude-haiku-4-5", persistence.successful.single().modelo)
        assertEquals("blanda", persistence.successful.single().categoria)
        assertEquals(10, persistence.successful.single().questions.size)
        assertEquals(101, persistence.successful.single().questions.sumOf { it.tokensInput ?: 0 })
        assertEquals(203, persistence.successful.single().questions.sumOf { it.tokensOutput ?: 0 })
        val persistedCost = persistence.successful.single().questions.sumOf { it.costoUsd ?: 0.0 }
        assertTrue(abs(0.001116 - persistedCost) < 0.000000001, "expected official Haiku cost, got $persistedCost")
        assertTrue(persistence.failed.isEmpty())
    }

    @Test
    fun `validator rejects duplicate enunciados ignoring case and whitespace`() = runBlocking {
        val raw = batchJson(2, "abierta_texto", enunciado = { "  Pregunta duplicada $it  ".replace(Regex("\\d"), "") })
        val persistence = RecordingPersistence()
        val service = serviceFor(raw, persistence)

        assertFailsWith<AiProviderInvalidOutputException> { service.generate(validReq(cantidad = 2)) }
        assertEquals(1, persistence.failed.size)
        assertTrue(persistence.successful.isEmpty())
    }

    @Test
    fun `validator rejects respuesta ideal with fewer than three sentence-like segments`() = runBlocking {
        val raw = batchJson(
            count = 1,
            tipo = "abierta_texto",
            respuestaIdeal = "Situación Tarea Acción Resultado en una sola frase sin estructura suficiente"
        )
        val persistence = RecordingPersistence()
        val service = serviceFor(raw, persistence)

        assertFailsWith<AiProviderInvalidOutputException> { service.generate(validReq(cantidad = 1)) }
        assertEquals(1, persistence.failed.size)
        assertTrue(persistence.successful.isEmpty())
    }

    @Test
    fun `validator rejects duplicate option texts within a multiple choice question`() = runBlocking {
        val raw = batchJson(1, "opcion_multiple", duplicateOptions = true)
        val persistence = RecordingPersistence()
        val service = serviceFor(raw, persistence)

        assertFailsWith<AiProviderInvalidOutputException> {
            service.generate(validReq(cantidad = 1, tipo = "opcion_multiple"))
        }
        assertEquals(1, persistence.failed.size)
        assertTrue(persistence.successful.isEmpty())
    }

    @Test
    fun `strict parser rejects extra fields records one failed trace and persists no questions`() = runBlocking {
        val raw = """
            {"preguntas":[{
              "enunciado":"Describe una situación compleja de liderazgo.",
              "tipo_pregunta":"abierta_texto",
              "respuesta_ideal":"Situación: hubo presión. Tarea: debía coordinar. Acción: prioricé y comuniqué. Resultado: se entregó con métricas.",
              "rubrica_evaluacion":{"metodo":"STAR","criterios":["Situación clara","Tarea clara","Acción clara","Resultado medible"],"palabras_clave":["priorización","comunicación","métricas"],"tiempo_esperado_seg":120},
              "opciones":null,
              "extra":"must fail"
            }]}
        """.trimIndent()
        val persistence = RecordingPersistence()
        val service = AiQuestionGeneratorService(
            openAiKey = "",
            anthropicKey = "",
            persistence = persistence,
            providers = mapOf(AiProviderKind.OPENAI to RecordingProvider(rawContent = raw))
        )

        assertFailsWith<AiProviderInvalidOutputException> { service.generate(validReq(cantidad = 1)) }
        assertTrue(persistence.successful.isEmpty())
        assertEquals(1, persistence.failed.size)
        assertEquals("gpt-4o-mini", persistence.failed.single().modelo)
        assertEquals(raw, persistence.failed.single().respuestaRaw)
    }

    @Test
    fun `validator rejects malformed uuid and missing context without provider calls`() = runBlocking {
        val provider = RecordingProvider(rawContent = batchJson(1, "abierta_texto"))
        val service = AiQuestionGeneratorService(
            openAiKey = "",
            anthropicKey = "",
            persistence = RecordingPersistence(),
            providers = mapOf(AiProviderKind.OPENAI to provider)
        )

        assertFailsWith<AiRequestValidationException> {
            service.generate(validReq(cargoId = "not-a-uuid"))
        }
        assertFailsWith<AiRequestValidationException> {
            service.generate(validReq(cargoId = null, skillId = null))
        }
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `validator rejects wrong count markdown fences incomplete STAR and invalid options`() = runBlocking {
        val cases = listOf(
            batchJson(2, "abierta_texto"),
            "```json\n${batchJson(1, "abierta_texto")}\n```",
            batchJson(1, "abierta_texto", criteria = listOf("Situación", "Tarea", "Acción")),
            batchJson(1, "opcion_multiple", correctOptions = 2)
        )

        for (raw in cases) {
            val persistence = RecordingPersistence()
            val service = AiQuestionGeneratorService(
                openAiKey = "",
                anthropicKey = "",
                persistence = persistence,
                providers = mapOf(AiProviderKind.OPENAI to RecordingProvider(rawContent = raw))
            )
            assertFailsWith<AiProviderInvalidOutputException> {
                service.generate(validReq(cantidad = 1, tipo = if (raw.contains("opcion_multiple")) "opcion_multiple" else "abierta_texto"))
            }
            assertEquals(1, persistence.failed.size)
            assertTrue(persistence.successful.isEmpty())
        }
    }

    private fun validReq(
        cargoId: String? = UUID.randomUUID().toString(),
        skillId: String? = null,
        cantidad: Int = 1,
        tipo: String = "abierta_texto",
        modelo: String = "gpt-4o-mini"
    ) = GenerateQuestionsReq(
        cargoId = cargoId,
        skillId = skillId,
        nivel = "senior",
        cantidad = cantidad,
        tipo = tipo,
        categoria = "blanda",
        modelo = modelo
    )

    private fun serviceFor(raw: String, persistence: RecordingPersistence) = AiQuestionGeneratorService(
        openAiKey = "",
        anthropicKey = "",
        persistence = persistence,
        providers = mapOf(AiProviderKind.OPENAI to RecordingProvider(rawContent = raw))
    )

    private fun batchJson(
        count: Int,
        tipo: String,
        criteria: List<String> = listOf("Situación clara", "Tarea clara", "Acción clara", "Resultado medible"),
        correctOptions: Int = 1,
        respuestaIdeal: String = "Situación: existía un problema relevante. Tarea: debía resolverlo. Acción: apliqué una estrategia concreta. Resultado: logré una mejora medible.",
        enunciado: (Int) -> String = { index -> "Pregunta $index con contexto suficiente para evaluar desempeño profesional" },
        duplicateOptions: Boolean = false
    ): String {
        val questions = (1..count).map { index ->
            LlmQuestion(
                enunciado = enunciado(index),
                tipoPregunta = tipo,
                respuestaIdeal = respuestaIdeal,
                rubricaEvaluacion = RubricaEvaluacion(
                    metodo = "STAR",
                    criterios = criteria,
                    palabrasClave = listOf("estrategia", "métrica", "colaboración"),
                    tiempoEsperadoSeg = 120
                ),
                opciones = if (tipo == "opcion_multiple") {
                    (1..4).map { option ->
                        OpcionPregunta(
                            texto = if (duplicateOptions) "Opción repetida" else "Opción $option con contenido significativo",
                            esCorrecta = option <= correctOptions,
                            explicacion = "Explicación $option detallada"
                        )
                    }
                } else null
            )
        }
        return "{\"preguntas\":${json.encodeToString(questions)}}"
    }
}

private class RecordingProvider(
    private val rawContent: String,
    private val actualModel: String = "gpt-4o-mini",
    private val promptTokens: Int = 100,
    private val completionTokens: Int = 200
) : AiQuestionProvider {
    val requests = mutableListOf<AiQuestionProviderRequest>()
    override suspend fun generate(request: AiQuestionProviderRequest): AiQuestionProviderResponse {
        requests.add(request)
        return AiQuestionProviderResponse(rawContent, promptTokens = promptTokens, completionTokens = completionTokens, actualModel = actualModel)
    }
}

private class RecordingPersistence : AiQuestionPersistence {
    val successful = mutableListOf<AiQuestionBatchPersistenceRequest>()
    val failed = mutableListOf<AiFailedTrace>()

    override suspend fun insertSuccessfulBatch(request: AiQuestionBatchPersistenceRequest): List<InsertedQuestion> {
        successful.add(request)
        return request.questions.map { InsertedQuestion(UUID.randomUUID(), UUID.randomUUID()) }
    }

    override suspend fun insertFailedTrace(trace: AiFailedTrace) {
        failed.add(trace)
    }
}
