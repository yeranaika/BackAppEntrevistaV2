package services.market

import data.models.market.CargoGenerationResult
import data.models.market.CargoSkillDetailDto
import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.roundToInt

class CargoSkillGeneratorService(
    private val cargoRepository: CargoRepository,
    private val skillMarketRepository: SkillMarketRepository,
    private val jobMarketClient: JobMarketClient
) {
    private val logger = LoggerFactory.getLogger(CargoSkillGeneratorService::class.java)

    /**
     * Consulta el mercado laboral para un cargo específico, descubre qué skills están pidiendo
     * los reclutadores y sincroniza la tabla cargo_skill (creando las skills si no existen).
     */
    suspend fun generateRequirementsForCargo(cargoId: UUID): CargoGenerationResult = withContext(Dispatchers.IO) {
        val cargo = cargoRepository.getCargoById(cargoId)
            ?: throw IllegalArgumentException("Cargo con ID  no encontrado")

        logger.info("Generando requisitos de mercado para el cargo: '{}' ({})", cargo.nombre, cargo.area)

        // 1. Obtener ofertas específicas para este cargo
        val postings = jobMarketClient.fetchTechJobPostings(specificQuery = cargo.nombre)
        val totalPostings = postings.size.coerceAtLeast(1)

        // 2. Extraer frecuencias y seniorities específicas para este cargo
        val skillFrequencyMap = mutableMapOf<String, Int>()
        val skillPostingCounts = mutableMapOf<String, Int>()
        val skillSeniorityCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (posting in postings) {
            val text = " "
            val extracted = SkillNormalizer.extractSkillsFromText(text)
            val seniority = SkillNormalizer.detectSeniority(text)

            for ((skillName, count) in extracted) {
                skillFrequencyMap[skillName] = (skillFrequencyMap[skillName] ?: 0) + count
                skillPostingCounts[skillName] = (skillPostingCounts[skillName] ?: 0) + 1
                val sMap = skillSeniorityCounts.getOrPut(skillName) { mutableMapOf() }
                sMap[seniority] = (sMap[seniority] ?: 0) + 1
            }
        }

        // Si la búsqueda online no arrojó skills, complementar con el dataset estructurado
        if (skillFrequencyMap.isEmpty()) {
            val fallbackPostings = jobMarketClient.fetchTechJobPostings(specificQuery = null)
            for (posting in fallbackPostings) {
                val text = "${posting.title} ${posting.description}"
                val extracted = SkillNormalizer.extractSkillsFromText(text)
                val seniority = SkillNormalizer.detectSeniority(text)

                for ((skillName, count) in extracted) {
                    skillFrequencyMap[skillName] = (skillFrequencyMap[skillName] ?: 0) + count
                    skillPostingCounts[skillName] = (skillPostingCounts[skillName] ?: 0) + 1
                    val sMap = skillSeniorityCounts.getOrPut(skillName) { mutableMapOf() }
                    sMap[seniority] = (sMap[seniority] ?: 0) + 1
                }
            }
        }

        val maxFreq = (skillFrequencyMap.values.maxOrNull() ?: 1).coerceAtLeast(1)
        val generatedList = mutableListOf<CargoSkillDetailDto>()

        // 3. Vincular y calcular pesos para cada skill detectada en las ofertas
        for ((skillName, freq) in skillFrequencyMap) {
            val postingsWithSkill = skillPostingCounts[skillName] ?: 1
            val percentage = postingsWithSkill.toDouble() / totalPostings.toDouble()
            val seniorityMap = skillSeniorityCounts[skillName] ?: emptyMap()
            val detectedLevel = seniorityMap.maxByOrNull { it.value }?.key ?: cargo.nivelBase

            // Inferencia de categoría y área
            val isBlanda = listOf(
                "Comunicación", "Trabajo en equipo", "Liderazgo",
                "Resolución de problemas", "Adaptabilidad", "Gestión del tiempo",
                "Inteligencia emocional", "Pensamiento crítico"
            ).contains(skillName)

            val categoria = if (isBlanda) "blanda" else "tecnica"
            val tipoArea = if (isBlanda) "hr" else cargo.area

            // Peso de 30 a 98 según el % de ofertas que lo exigen y la frecuencia
            val relativeWeight = (35.0 + (percentage * 50.0) + ((freq.toDouble() / maxFreq) * 15.0)).roundToInt().coerceIn(1, 100)
            val isObligatoria = percentage >= 0.35 || relativeWeight >= 75

            // A. Crear la skill en catálogo si no existía
            val skillId = cargoRepository.getOrCreateSkill(
                nombre = skillName,
                categoria = categoria,
                tipoArea = tipoArea,
                descripcion = "Habilidad del mercado requerida para el cargo ",
                demandaScore = relativeWeight.toShort()
            )

            // B. Vincular a la carrera / cargo en cargo_skill
            cargoRepository.linkOrUpdateCargoSkill(
                cargoId = cargoId,
                skillId = skillId,
                nivelRequerido = detectedLevel,
                peso = relativeWeight.toShort(),
                obligatoria = isObligatoria
            )

            generatedList.add(
                CargoSkillDetailDto(
                    skillId = skillId.toString(),
                    nombre = skillName,
                    categoria = categoria,
                    tipoArea = tipoArea,
                    nivelRequerido = detectedLevel,
                    peso = relativeWeight.toShort(),
                    obligatoria = isObligatoria
                )
            )
        }

        logger.info("Se vincularon {} skills al cargo '{}'", generatedList.size, cargo.nombre)

        CargoGenerationResult(
            cargoId = cargo.cargoId,
            cargoNombre = cargo.nombre,
            skillsLinkedCount = generatedList.size,
            skills = generatedList.sortedByDescending { it.peso }
        )
    }

    /**
     * Itera sobre todas las carreras/cargos de la BD y actualiza automáticamente los requisitos de mercado.
     */
    suspend fun generateRequirementsForAllCargos(): List<CargoGenerationResult> = withContext(Dispatchers.IO) {
        val allCargos = cargoRepository.getAllCargos()
        logger.info("Iniciando generación masiva de requisitos para {} carreras...", allCargos.size)

        val results = mutableListOf<CargoGenerationResult>()
        for (cargo in allCargos) {
            try {
                val cargoUuid = UUID.fromString(cargo.cargoId)
                val res = generateRequirementsForCargo(cargoUuid)
                results.add(res)
            } catch (e: Exception) {
                logger.error("Error generando requisitos para cargo '{}': {}", cargo.nombre, e.message)
            }
        }
        results
    }
}
