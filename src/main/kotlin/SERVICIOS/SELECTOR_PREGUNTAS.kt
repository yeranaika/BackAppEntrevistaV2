package SERVICIOS

import CONFIGURACION.PROPORCION_PREGUNTAS_TECNICAS
import MODELOS.Cargo
import MODELOS.CategoriaHabilidad
import MODELOS.CriterioSeleccionPreguntas
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.Pregunta
import MODELOS.RepositorioPregunta
import MODELOS.TipoPregunta
import java.util.UUID
import kotlin.math.roundToInt

/** Para quién y con qué filtros se eligen las preguntas. */
data class ContextoSeleccion(
    val cargo: Cargo?,
    val nivel: NivelExperiencia,
    /** Preguntas vistas hace poco: se evitan mientras el banco alcance. */
    val recientes: Set<UUID> = emptySet(),
    /** Solo estos tipos (vacío = cualquiera). */
    val tipos: Set<TipoPregunta> = emptySet(),
    /** Si viene, solo preguntas de esta skill (práctica por skill). */
    val skillId: UUID? = null
)

/**
 * Elige preguntas APROBADAS del banco.
 *
 * Por categoría prefiere, en este orden: preguntas del cargo → de las skills del cargo → generales.
 * Si el cargo no está en el catálogo, las técnicas salen de cualquier skill (sin cargo asignado).
 * Primero evita las que el usuario vio hace poco; si el banco no alcanza, las repite.
 */
class SelectorPreguntas(
    private val preguntas: RepositorioPregunta,
    private val mercado: LectorMercado
) {

    /** Entrevista: 60 % técnicas y el resto blandas; si una categoría no alcanza, completa con la otra. */
    suspend fun seleccionarMixta(contexto: ContextoSeleccion, cantidad: Int): List<Pregunta> {
        val skillsCargo = skillsDelCargo(contexto.cargo)
        val cantidadTecnicas = (cantidad * PROPORCION_PREGUNTAS_TECNICAS).roundToInt()
        val tecnicas = elegir(contexto, skillsCargo, CategoriaHabilidad.TECNICA, cantidadTecnicas, emptySet()).toMutableList()
        val blandas = elegir(contexto, skillsCargo, CategoriaHabilidad.BLANDA, cantidad - tecnicas.size, idsDe(tecnicas))
        val faltantes = cantidad - tecnicas.size - blandas.size
        if (faltantes > 0) {
            tecnicas += elegir(contexto, skillsCargo, CategoriaHabilidad.TECNICA, faltantes, idsDe(tecnicas + blandas))
        }
        // La entrevista abre con lo técnico y cierra con preguntas de comportamiento.
        return tecnicas + blandas
    }

    /** Solo una categoría (práctica, nivelación). */
    suspend fun seleccionar(
        contexto: ContextoSeleccion,
        categoria: CategoriaHabilidad,
        cantidad: Int,
        yaElegidas: Set<UUID> = emptySet()
    ): List<Pregunta> = elegir(contexto, skillsDelCargo(contexto.cargo), categoria, cantidad, yaElegidas)

    private suspend fun skillsDelCargo(cargo: Cargo?): Set<UUID> =
        cargo?.let { c -> mercado.listarRequisitos(c.id).map { it.skill.id }.toSet() }.orEmpty()

    private suspend fun elegir(
        contexto: ContextoSeleccion,
        skillsCargo: Set<UUID>,
        categoria: CategoriaHabilidad,
        cantidad: Int,
        yaElegidas: Set<UUID>
    ): List<Pregunta> {
        val elegidas = mutableListOf<Pregunta>()
        for (evitarRecientes in listOf(true, false)) {
            for (criterio in criteriosPorPreferencia(contexto, skillsCargo, categoria)) {
                val faltan = cantidad - elegidas.size
                if (faltan <= 0) return elegidas
                val excluir = yaElegidas + idsDe(elegidas) + (if (evitarRecientes) contexto.recientes else emptySet())
                elegidas += preguntas.seleccionarAprobadas(criterio.copy(excluir = excluir), faltan)
            }
        }
        return elegidas
    }

    private fun criteriosPorPreferencia(
        contexto: ContextoSeleccion,
        skillsCargo: Set<UUID>,
        categoria: CategoriaHabilidad
    ): List<CriterioSeleccionPreguntas> {
        val base = CriterioSeleccionPreguntas(nivel = contexto.nivel, categoria = categoria, tipos = contexto.tipos)
        // Práctica de una skill: solo esa skill (del cargo o general).
        contexto.skillId?.let { return listOf(base.copy(skillIds = setOf(it))) }
        return listOfNotNull(
            contexto.cargo?.let { base.copy(cargoId = it.id) },
            base.copy(skillIds = skillsCargo, soloSinCargo = true).takeIf { skillsCargo.isNotEmpty() },
            // Con un cargo conocido, una pregunta técnica de otra skill no sirve como "general"; una blanda sí
            // (comunicación, liderazgo…). Con un cargo fuera del catálogo no hay cómo filtrar por skill.
            base.copy(soloSinCargo = true, soloSinSkill = categoria == CategoriaHabilidad.TECNICA && contexto.cargo != null)
        )
    }

    private fun idsDe(lista: List<Pregunta>) = lista.map { it.id }.toSet()
}
