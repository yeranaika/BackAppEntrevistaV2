package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mismo JSON que ya exponían /api/v1/cargos, /api/v1/skills y /market/* (panel admin).

@Serializable
data class RespuestaCargo(
    val cargoId: String,
    val nombre: String,
    val area: String,
    val descripcion: String? = null,
    val nivelBase: String,
    val activo: Boolean
)

@Serializable
data class RespuestaRequisito(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val nivelRequerido: String,
    val peso: Short,
    val obligatoria: Boolean
)

@Serializable
data class RespuestaMatrizCargo(
    val cargoId: String,
    val cargoNombre: String,
    val area: String,
    val nivelBase: String,
    val totalSkills: Int,
    val obligatoriasCount: Int,
    val opcionalesCount: Int,
    val skills: List<RespuestaRequisito>
)

@Serializable
data class RespuestaSkill(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val descripcion: String? = null,
    val demandaScore: Short,
    val activo: Boolean
)

@Serializable
data class RespuestaSkillTendencia(
    val skillId: String,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val demandaScore: Short,
    val descripcion: String? = null
)

@Serializable
data class RespuestaTendencias(
    val total: Int,
    val categoriaFiltro: String? = null,
    val skills: List<RespuestaSkillTendencia>
)

@Serializable
data class RespuestaHistorialTendencia(
    val tendenciaId: String,
    val skillId: String,
    val frecuenciaOfertas: Int,
    val nivelRequerido: String,
    val fechaActualizacion: String
)

@Serializable
data class RespuestaDemandaSkill(
    val skillId: String,
    val nombre: String,
    val demandaScore: Short,
    val frecuenciaOfertas: Int,
    val nivelPredominante: String
)

@Serializable
data class RespuestaSincronizacion(
    val success: Boolean,
    val message: String,
    val skillsUpdatedCount: Int,
    val durationMs: Long,
    val timestamp: String,
    val topSkills: List<RespuestaDemandaSkill>
)

@Serializable
data class SolicitudCrearCargo(
    val nombre: String,
    val area: String,
    val descripcion: String? = null,
    val nivelBase: String = "semisenior",
    @SerialName("autoGenerateSkills") val generarSkills: Boolean = true
)

@Serializable
data class RespuestaRequisitosGenerados(
    val cargoId: String,
    val cargoNombre: String,
    val skillsLinkedCount: Int,
    val skills: List<RespuestaRequisito>
)

@Serializable
data class RespuestaCargoCreado(
    val cargo: RespuestaCargo,
    val generatedSkills: RespuestaRequisitosGenerados? = null
)

@Serializable
data class RespuestaGeneracionMasiva(
    val totalCargosProcessed: Int,
    val results: List<RespuestaRequisitosGenerados>
)
