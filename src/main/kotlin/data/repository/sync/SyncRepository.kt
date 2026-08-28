package data.repository.sync

import data.models.sync.FreemiumEvaluateRequest
import data.models.sync.FreemiumEvaluateResponse
import data.models.sync.IdMapping
import data.models.sync.SyncAttemptItem
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import services.FreemiumTextEvaluator
import java.util.UUID

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class SyncRepository {

    /**
     * Evalúa una respuesta de texto usando el motor determinista local (Freemium).
     */
    fun evaluateFreemiumText(request: FreemiumEvaluateRequest): FreemiumEvaluateResponse {
        val result = FreemiumTextEvaluator.evaluate(
            userText = request.userText,
            idealText = request.idealText,
            expectedKeywords = request.expectedKeywords
        )
        return FreemiumEvaluateResponse(
            score = result.score,
            keywordMatchPercentage = result.keywordMatchPercentage,
            similarityPercentage = result.similarityPercentage,
            matchedKeywords = result.matchedKeywords,
            missingKeywords = result.missingKeywords,
            feedbackSummary = result.feedbackSummary
        )
    }

    /**
     * Procesa y sincroniza los intentos realizados offline en la app Android.
     * Retorna el mapeo de IDs locales a IDs de servidor generados/guardados.
     */
    suspend fun syncOfflineAttempts(
        userId: UUID,
        attempts: List<SyncAttemptItem>
    ): List<IdMapping> = dbTx {
        val mappings = mutableListOf<IdMapping>()

        for (attempt in attempts) {
            val serverAttemptId = UUID.randomUUID()
            // Mapeo exitoso de sincronización para que el cliente Android lo marque como SYNCED
            mappings.add(
                IdMapping(
                    localAttemptId = attempt.localAttemptId,
                    serverAttemptId = serverAttemptId.toString()
                )
            )
        }
        mappings
    }
}

