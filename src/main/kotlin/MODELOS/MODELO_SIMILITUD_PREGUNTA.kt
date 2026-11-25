package MODELOS

import java.util.UUID

/** Dónde se busca una pregunta parecida: mismo tipo, categoría y nivel, y mismo skill (o, sin skill, mismo cargo). */
data class AlcanceComparacion(
    val skillId: UUID?,
    val cargoId: UUID?,
    val tipo: TipoPregunta,
    val categoria: CategoriaHabilidad,
    val nivel: NivelExperiencia
)

/** Pregunta del banco contra la que se compara (aprobada o pendiente). */
data class PreguntaExistente(val id: UUID, val enunciado: String, val estado: EstadoPregunta, val vecesUsada: Int = 0)

/** [id] y [estado] son null cuando lo parecido es otra pregunta del mismo lote, que aún no está en el banco. */
data class PreguntaSimilar(val id: UUID?, val enunciado: String, val estado: EstadoPregunta?, val similitud: Double)

/** La pregunta [indice] del lote no se guardó por parecerse a [similar]. */
data class DescartadaPorSimilar(val indice: Int, val similar: PreguntaSimilar)

/** [ids] van en el orden del lote, sin las descartadas. */
data class LoteGuardado(val ids: List<IdsGenerados>, val descartadas: List<DescartadaPorSimilar>)

/** Un grupo de preguntas que se parecen entre sí (para limpiar el banco). */
data class GrupoPreguntasSimilares(val preguntas: List<PreguntaExistente>, val similitudMaxima: Double)
