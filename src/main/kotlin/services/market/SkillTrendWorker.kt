package services.market

import data.models.market.MarketSyncResult
import data.models.market.SkillDemandSummary
import data.repository.market.SkillMarketRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class SkillTrendWorker(
    private val repository: SkillMarketRepository,
    private val jobMarketClient: JobMarketClient,
    private val cargoSkillGenerator: CargoSkillGeneratorService? = null,
    private val interval: Duration = 7.days,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val logger = LoggerFactory.getLogger(SkillTrendWorker::class.java)
    private var job: Job? = null
    private val isRunning = AtomicBoolean(false)

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Iniciando SkillTrendWorker con intervalo de {}", interval)
            job = scope.launch {
                while (isActive) {
                    try {
                        logger.info("Ejecutando ciclo programado de actualización de tendencias de skills...")
                        syncMarketTrends()
                    } catch (e: CancellationException) {
                        logger.info("SkillTrendWorker cancelado.")
                        break
                    } catch (e: Exception) {
                        logger.error("Error en ciclo programado de SkillTrendWorker: {}", e.message, e)
                    }
                    delay(interval)
                }
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Deteniendo SkillTrendWorker...")
            job?.cancel()
            job = null
        }
    }

    suspend fun syncMarketTrends(): MarketSyncResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logger.info("Iniciando sincronización de tendencias de mercado y skills...")

        val activeSkills = repository.getAllActiveSkills()
        if (activeSkills.isEmpty()) {
            logger.warn("No se encontraron skills activas en la base de datos.")
            return@withContext MarketSyncResult(
                success = false,
                message = "No hay skills activas registradas en la base de datos",
                skillsUpdatedCount = 0,
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }

        // 1. Obtener ofertas de trabajo
        val jobPostings = jobMarketClient.fetchTechJobPostings()
        logger.info("Analizando {} ofertas de trabajo para extraer menciones de skills...", jobPostings.size)

        // 2. Extraer menciones y niveles por skill
        val skillFrequencyMap = mutableMapOf<String, Int>()
        val skillSeniorityCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (posting in jobPostings) {
            val fullText = " "
            val matches = SkillNormalizer.extractSkillsFromText(fullText)
            val detectedSeniority = SkillNormalizer.detectSeniority(fullText)

            for ((canonicalSkill, count) in matches) {
                skillFrequencyMap[canonicalSkill] = (skillFrequencyMap[canonicalSkill] ?: 0) + count
                val seniorityMap = skillSeniorityCounts.getOrPut(canonicalSkill) { mutableMapOf() }
                seniorityMap[detectedSeniority] = (seniorityMap[detectedSeniority] ?: 0) + 1
            }
        }

        val maxFrequency = (skillFrequencyMap.values.maxOrNull() ?: 1).coerceAtLeast(1)
        var updatedCount = 0
        val topSummaryList = mutableListOf<SkillDemandSummary>()

        // 3. Calcular demanda_score y persistir
        for (skill in activeSkills) {
            val skillUuid = try {
                UUID.fromString(skill.skillId)
            } catch (e: Exception) {
                continue
            }

            val freq = skillFrequencyMap[skill.nombre] ?: 0
            val seniorityMap = skillSeniorityCounts[skill.nombre] ?: emptyMap()
            val predominantSeniority = seniorityMap.maxByOrNull { it.value }?.key ?: "semisenior"

            // Cálculo de score (1 - 100)
            val calculatedScore: Short = if (skill.categoria == "blanda") {
                // Las habilidades blandas mantienen un puntaje base alto (75 - 98) complementado por menciones
                val softScore = (75 + ((freq.toDouble() / maxFrequency) * 23.0)).roundToInt().coerceIn(1, 100)
                softScore.toShort()
            } else {
                // Habilidades técnicas escaladas entre 35 y 98 según frecuencia relativa
                val techScore = (35 + ((freq.toDouble() / maxFrequency) * 60.0)).roundToInt().coerceIn(1, 100)
                techScore.toShort()
            }

            // A. Actualizar demanda_score en tabla skill
            repository.updateSkillDemandScore(skillUuid, calculatedScore)

            // B. Registrar en tabla skill_tendencia
            repository.insertTendencia(
                skillId = skillUuid,
                frecuenciaOfertas = freq,
                nivelRequerido = predominantSeniority
            )

            // C. Actualizar matriz de ponderación en cargo_skill
            repository.updateCargoSkillWeights(skillUuid, calculatedScore)

            updatedCount++
            topSummaryList.add(
                SkillDemandSummary(
                    skillId = skill.skillId,
                    nombre = skill.nombre,
                    demandaScore = calculatedScore,
                    frecuenciaOfertas = freq,
                    nivelPredominante = predominantSeniority
                )
            )
        }

        // 4. Sincronización detallada de requisitos por carrera/cargo
        if (cargoSkillGenerator != null) {
            try {
                logger.info("Ejecutando generador de requisitos específicos por carrera...")
                cargoSkillGenerator.generateRequirementsForAllCargos()
            } catch (e: Exception) {
                logger.warn("Error en generador de requisitos por carrera: {}", e.message)
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val top10 = topSummaryList.sortedByDescending { it.demandaScore }.take(10)
        logger.info("Sincronización completada con éxito. {} skills actualizadas en {} ms.", updatedCount, duration)

        MarketSyncResult(
            success = true,
            message = "Sincronización de tendencias completada con éxito para  skills.",
            skillsUpdatedCount = updatedCount,
            durationMs = duration,
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            topSkills = top10
        )
    }
}
