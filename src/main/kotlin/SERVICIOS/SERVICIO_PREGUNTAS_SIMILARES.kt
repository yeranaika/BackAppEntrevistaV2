package SERVICIOS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.AlcanceComparacion
import MODELOS.GrupoPreguntasSimilares
import MODELOS.Pregunta
import MODELOS.PreguntaSimilar
import MODELOS.RepositorioPregunta
import UTILIDADES.sinVacios
import java.util.UUID

private const val UMBRAL_MINIMO_CONSULTA = 0.5

/**
 * Para el panel del admin: qué preguntas del banco se parecen a otra (D37). Solo avisa; nunca bloquea ni cambia estados.
 * [umbralPorDefecto] <= 0 apaga los avisos de crear, editar y aprobar (la consulta explícita siempre responde).
 */
class ServicioPreguntasSimilares(
    private val preguntas: RepositorioPregunta,
    private val umbralPorDefecto: Double = UMBRAL_SIMILITUD_PREGUNTAS_POR_DEFECTO
) {
    /** Aviso al crear, editar o aprobar: lo que se parece a [pregunta] con el umbral configurado. */
    suspend fun avisoPara(pregunta: Pregunta): List<PreguntaSimilar> =
        if (umbralPorDefecto <= 0.0) emptyList() else similares(pregunta, umbralPorDefecto)

    suspend fun similaresDe(id: UUID, umbralTexto: String?): List<PreguntaSimilar> {
        val pregunta = preguntas.buscarPorId(id) ?: throw ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta no existe")
        return similares(pregunta, leerUmbral(umbralTexto))
    }

    /** Grupos de preguntas parecidas dentro de un alcance; exige skill o cargo para no cruzar todo el banco. */
    suspend fun duplicadas(
        skill: String?, cargo: String?, nivel: String?, tipo: String?, categoria: String?, umbralTexto: String?
    ): List<GrupoPreguntasSimilares> {
        val skillId = skill.sinVacios()?.let(::leerUuid)
        val cargoId = cargo.sinVacios()?.let(::leerUuid)
        if (skillId == null && cargoId == null) {
            throw ErrorValidacion("contexto_requerido", "Indica skillId o cargoId: no se compara todo el banco contra todo")
        }
        val alcance = AlcanceComparacion(
            skillId, cargoId,
            leerTipo(tipo ?: throw ErrorValidacion("tipo_invalido", "Indica el tipo")),
            leerCategoria(categoria ?: throw ErrorValidacion("categoria_invalida", "Indica la categoría")),
            leerNivel(nivel ?: throw ErrorValidacion("nivel_invalido", "Indica el nivel"))
        )
        return DetectorPreguntasSimilares.grupos(preguntas.existentesParaComparar(alcance), leerUmbral(umbralTexto))
    }

    private suspend fun similares(pregunta: Pregunta, umbral: Double): List<PreguntaSimilar> {
        val alcance = AlcanceComparacion(pregunta.skillId, pregunta.cargoId, pregunta.tipo, pregunta.categoria, pregunta.nivel)
        return DetectorPreguntasSimilares.masParecidas(pregunta.enunciado, preguntas.existentesParaComparar(alcance, pregunta.id), umbral)
    }

    private fun leerUmbral(texto: String?): Double {
        val umbral = texto.sinVacios()?.let {
            it.toDoubleOrNull() ?: throw ErrorValidacion("umbral_invalido", "El umbral debe ser un número entre 0.5 y 1")
        } ?: umbralPorDefecto.takeIf { it >= UMBRAL_MINIMO_CONSULTA } ?: UMBRAL_SIMILITUD_PREGUNTAS_POR_DEFECTO
        if (umbral !in UMBRAL_MINIMO_CONSULTA..1.0) {
            throw ErrorValidacion("umbral_invalido", "El umbral debe ser un número entre 0.5 y 1")
        }
        return umbral
    }
}
