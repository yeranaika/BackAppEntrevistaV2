package data.models.market

import kotlinx.serialization.Serializable

@Serializable
data class SkillDto(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val descripcion: String? = null,
    val demandaScore: Short,
    val activo: Boolean
)

@Serializable
data class SkillTendenciaDto(
    val tendenciaId: String,
    val skillId: String,
    val frecuenciaOfertas: Int,
    val nivelRequerido: String,
    val fechaActualizacion: String
)

@Serializable
data class SkillDemandSummary(
    val skillId: String,
    val nombre: String,
    val demandaScore: Short,
    val frecuenciaOfertas: Int,
    val nivelPredominante: String
)

@Serializable
data class MarketSyncResult(
    val success: Boolean,
    val message: String,
    val skillsUpdatedCount: Int,
    val durationMs: Long,
    val timestamp: String,
    val topSkills: List<SkillDemandSummary> = emptyList()
)

@Serializable
data class CargoDto(
    val cargoId: String,
    val nombre: String,
    val area: String,
    val descripcion: String? = null,
    val nivelBase: String,
    val activo: Boolean
)

@Serializable
data class CargoSkillDetailDto(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val nivelRequerido: String,
    val peso: Short,
    val obligatoria: Boolean
)

@Serializable
data class CreateCargoReq(
    val nombre: String,
    val area: String,
    val descripcion: String? = null,
    val nivelBase: String = "semisenior",
    val autoGenerateSkills: Boolean = true
)

@Serializable
data class CargoGenerationResult(
    val cargoId: String,
    val cargoNombre: String,
    val skillsLinkedCount: Int,
    val skills: List<CargoSkillDetailDto>
)

@Serializable
data class CreateCargoResponse(
    val cargo: CargoDto,
    val generatedSkills: CargoGenerationResult? = null
)

@Serializable
data class BulkCargoGenerationResponse(
    val totalCargosProcessed: Int,
    val results: List<CargoGenerationResult>
)
