package SERVICIOS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.Cargo
import MODELOS.ConteoPreguntasSkill
import MODELOS.EstadoPregunta
import MODELOS.LectorCoberturaPreguntas
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.RequisitoCargo
import java.time.LocalDateTime
import java.util.UUID

const val MINIMO_COBERTURA_POR_DEFECTO = 5
private const val MINIMO_COBERTURA_MAXIMO = 50

/** Preguntas de una skill que sirven para un cargo, por nivel. */
data class CoberturaSkill(
    val requisito: RequisitoCargo,
    val aprobadas: Map<NivelExperiencia, Int>,
    val pendientes: Map<NivelExperiencia, Int>
) {
    fun faltan(minimo: Int): Map<NivelExperiencia, Int> =
        NivelExperiencia.entries.associateWith { (minimo - (aprobadas[it] ?: 0)).coerceAtLeast(0) }
}

data class CoberturaCargo(val cargo: Cargo, val skills: List<CoberturaSkill>)

data class CoberturaBanco(
    val minimo: Int,
    val ultimaSincronizacionMercado: LocalDateTime?,
    val cargos: List<CoberturaCargo>
)

/**
 * Cruza lo que pide el mercado (las skills de cada cargo, que actualiza la sincronización semanal) con el banco de
 * preguntas (D26). Una pregunta sirve para el cargo C y la skill S si es de S y de C, o de S sin cargo: es lo que usa
 * SelectorPreguntas. Se cuenta por nivel porque la entrevista elige preguntas del nivel de cada persona.
 */
class ServicioCoberturaPreguntas(
    private val mercado: LectorMercado,
    private val lector: LectorCoberturaPreguntas
) {

    suspend fun cobertura(cargoId: UUID?, minimoPedido: Int?): CoberturaBanco {
        val minimo = minimoPedido ?: MINIMO_COBERTURA_POR_DEFECTO
        if (minimo !in 1..MINIMO_COBERTURA_MAXIMO) {
            throw ErrorValidacion("minimo_invalido", "El mínimo de preguntas por nivel debe estar entre 1 y $MINIMO_COBERTURA_MAXIMO")
        }
        val cargos = if (cargoId != null) {
            listOf(mercado.buscarCargo(cargoId)?.takeIf { it.estaActivo } ?: throw ErrorNoEncontrado("cargo_not_found", "No se encontró el cargo especificado"))
        } else {
            mercado.listarCargosActivos()
        }
        val requisitosPorCargo = cargos.associateWith { mercado.listarRequisitos(it.id) }
        val conteos = lector.contarPorSkill(requisitosPorCargo.values.flatten().map { it.skill.id }.toSet())
        val conteosPorSkill = conteos.groupBy { it.skillId }

        val resultado = cargos.map { cargo ->
            val skills = requisitosPorCargo.getValue(cargo)
                .map { requisito -> coberturaDe(requisito, cargo.id, conteosPorSkill[requisito.skill.id].orEmpty()) }
                .sortedWith(
                    compareByDescending<CoberturaSkill> { it.requisito.esObligatoria }
                        .thenByDescending { it.requisito.peso }
                        .thenBy { it.requisito.skill.nombre.lowercase() }
                )
            CoberturaCargo(cargo, skills)
        }.sortedWith(compareByDescending<CoberturaCargo> { c -> c.skills.sumOf { it.faltan(minimo).values.sum() } }.thenBy { it.cargo.nombre })

        return CoberturaBanco(minimo, mercado.fechaUltimaTendencia(), resultado)
    }

    private fun coberturaDe(requisito: RequisitoCargo, cargoId: UUID, conteos: List<ConteoPreguntasSkill>): CoberturaSkill {
        // Las de otro cargo no le sirven a este (el selector no las elige).
        val utiles = conteos.filter { it.cargoId == null || it.cargoId == cargoId }
        fun porNivel(estado: EstadoPregunta) = utiles.filter { it.estado == estado }
            .groupBy { it.nivel }
            .mapValues { (_, filas) -> filas.sumOf { it.cantidad } }
        return CoberturaSkill(requisito, porNivel(EstadoPregunta.APROBADA), porNivel(EstadoPregunta.PENDIENTE))
    }
}
