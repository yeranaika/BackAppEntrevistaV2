package SERVICIOS

import CONFIGURACION.PROPORCION_PREGUNTAS_TECNICAS
import MODELOS.Cargo
import MODELOS.CategoriaHabilidad
import MODELOS.CriterioSeleccionPreguntas
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.Pregunta
import MODELOS.RepositorioPregunta
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Arma la lista de preguntas de una entrevista desde el banco (solo APROBADAS del nivel pedido).
 *
 * Por categoría prefiere, en este orden: preguntas del cargo → de las skills del cargo → generales.
 * Si el cargo no está en el catálogo, las técnicas salen de cualquier skill (sin cargo asignado).
 * Primero evita las que el usuario vio hace poco; si el banco no alcanza, las repite.
 * Si una categoría no alcanza, completa con la otra.
 */
class SelectorPreguntasEntrevista(
    private val preguntas: RepositorioPregunta,
    private val mercado: LectorMercado
) {

    suspend fun seleccionar(cargo: Cargo?, nivel: NivelExperiencia, cantidad: Int, recientes: Set<UUID>): List<Pregunta> {
        val skillsCargo = cargo?.let { c -> mercado.listarRequisitos(c.id).map { it.skill.id }.toSet() }.orEmpty()
        val contexto = Contexto(cargo, skillsCargo, nivel, recientes)

        val cantidadTecnicas = (cantidad * PROPORCION_PREGUNTAS_TECNICAS).roundToInt()
        val tecnicas = elegir(contexto, CategoriaHabilidad.TECNICA, cantidadTecnicas, emptySet()).toMutableList()
        val blandas = elegir(contexto, CategoriaHabilidad.BLANDA, cantidad - tecnicas.size, idsDe(tecnicas)).toMutableList()
        val faltantes = cantidad - tecnicas.size - blandas.size
        if (faltantes > 0) {
            tecnicas += elegir(contexto, CategoriaHabilidad.TECNICA, faltantes, idsDe(tecnicas + blandas))
        }
        // La entrevista abre con lo técnico y cierra con preguntas de comportamiento.
        return tecnicas + blandas
    }

    private suspend fun elegir(
        contexto: Contexto,
        categoria: CategoriaHabilidad,
        cantidad: Int,
        yaElegidas: Set<UUID>
    ): List<Pregunta> {
        val elegidas = mutableListOf<Pregunta>()
        for (evitarRecientes in listOf(true, false)) {
            for (criterio in criteriosPorPreferencia(contexto, categoria)) {
                val faltan = cantidad - elegidas.size
                if (faltan <= 0) return elegidas
                val excluir = yaElegidas + idsDe(elegidas) + (if (evitarRecientes) contexto.recientes else emptySet())
                elegidas += preguntas.seleccionarAprobadas(criterio.copy(excluir = excluir), faltan)
            }
        }
        return elegidas
    }

    private fun criteriosPorPreferencia(contexto: Contexto, categoria: CategoriaHabilidad): List<CriterioSeleccionPreguntas> {
        val base = CriterioSeleccionPreguntas(nivel = contexto.nivel, categoria = categoria)
        return listOfNotNull(
            contexto.cargo?.let { base.copy(cargoId = it.id) },
            base.copy(skillIds = contexto.skillsCargo, soloSinCargo = true).takeIf { contexto.skillsCargo.isNotEmpty() },
            // Con un cargo conocido, una pregunta técnica de otra skill no sirve como "general"; una blanda sí
            // (comunicación, liderazgo…). Con un cargo fuera del catálogo no hay cómo filtrar por skill.
            base.copy(soloSinCargo = true, soloSinSkill = categoria == CategoriaHabilidad.TECNICA && contexto.cargo != null)
        )
    }

    private fun idsDe(lista: List<Pregunta>) = lista.map { it.id }.toSet()

    private data class Contexto(
        val cargo: Cargo?,
        val skillsCargo: Set<UUID>,
        val nivel: NivelExperiencia,
        val recientes: Set<UUID>
    )
}
