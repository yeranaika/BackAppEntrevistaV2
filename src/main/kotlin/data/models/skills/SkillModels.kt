package data.models.skills

import kotlinx.serialization.Serializable

@Serializable
data class CargoLevelItem(
    val cargoId: String,
    val nombre: String,
    val area: String,
    val descripcion: String? = null,
    val nivelBase: String, // "junior", "semisenior", "senior"
    val activo: Boolean = true
)

@Serializable
data class CargoSkillMatrixItem(
    val skillId: String,
    val nombre: String,
    val categoria: String, // "tecnica" | "blanda"
    val tipoArea: String,  // "backend", "frontend", "mobile", "data", "devops", "hr"
    val nivelRequerido: String, // "junior", "semisenior", "senior"
    val peso: Short, // 1 - 100
    val obligatoria: Boolean
)

@Serializable
data class CargoSkillsMatrixResponse(
    val cargoId: String,
    val cargoNombre: String,
    val area: String,
    val nivelBase: String,
    val totalSkills: Int,
    val obligatoriasCount: Int,
    val opcionalesCount: Int,
    val skills: List<CargoSkillMatrixItem>
)

@Serializable
data class TrendingSkillItem(
    val skillId: String,
    val nombre: String,
    val categoria: String, // "tecnica" | "blanda"
    val tipoArea: String,
    val demandaScore: Short, // 1 - 100
    val descripcion: String? = null
)

@Serializable
data class TrendingSkillsResponse(
    val total: Int,
    val categoriaFiltro: String? = null,
    val skills: List<TrendingSkillItem>
)
