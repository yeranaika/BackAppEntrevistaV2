package MODELOS

import java.time.LocalDateTime
import java.util.UUID

const val CATEGORIA_TECNICA = "tecnica"
const val CATEGORIA_BLANDA = "blanda"
const val DEMANDA_MINIMA: Short = 1
const val DEMANDA_MAXIMA: Short = 100

data class Cargo(
    val id: UUID,
    val nombre: String,
    val area: String,
    val descripcion: String?,
    val nivelBase: NivelExperiencia,
    val estaActivo: Boolean
)

data class Skill(
    val id: UUID,
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val descripcion: String?,
    val demandaScore: Short,
    val estaActiva: Boolean
)

/** Una skill tal como la exige un cargo. */
data class RequisitoCargo(
    val skill: Skill,
    val nivelRequerido: NivelExperiencia,
    val peso: Short,
    val esObligatoria: Boolean
)

data class TendenciaSkill(
    val id: UUID,
    val skillId: UUID,
    val frecuenciaOfertas: Int,
    val nivelRequerido: NivelExperiencia,
    val fecha: LocalDateTime
)

data class NuevoCargo(
    val nombre: String,
    val area: String,
    val descripcion: String?,
    val nivelBase: NivelExperiencia
)

data class NuevaSkill(
    val nombre: String,
    val categoria: String,
    val tipoArea: String,
    val descripcion: String?,
    val demandaScore: Short
)
