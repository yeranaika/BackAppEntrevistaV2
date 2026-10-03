package SERVICIOS

import CONFIGURACION.PREGUNTAS_NIVELACION_MINIMO
import CONFIGURACION.PREGUNTAS_NIVELACION_POR_NIVEL
import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.Cargo
import MODELOS.CategoriaHabilidad
import MODELOS.DetalleIntento
import MODELOS.EstadoPregunta
import MODELOS.EvaluacionNivelSkill
import MODELOS.EvaluacionSkill
import MODELOS.IntentoNivelacion
import MODELOS.LectorMercado
import MODELOS.ModoPractica
import MODELOS.NivelExperiencia
import MODELOS.NivelSkillUsuario
import MODELOS.Pregunta
import MODELOS.PreguntaServida
import MODELOS.RepositorioNivelSkill
import MODELOS.RepositorioNivelacion
import MODELOS.RepositorioPregunta
import MODELOS.RepositorioTestNivelacion
import MODELOS.RequisitoCargo
import MODELOS.ResultadoNivelacion
import MODELOS.TipoPregunta
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.UUID

/** Respuesta del usuario a una pregunta servida, sin validar. */
data class RespuestaPrueba(
    val preguntaServidaId: String,
    val opcionId: String? = null,
    val texto: String? = null,
    val tiempoRespuestaMs: Int? = null
)

data class NivelacionConResultado(val intento: IntentoNivelacion, val resultado: ResultadoNivelacion?)

private const val PRIORIDAD_ALTA = "alta"
private const val PRIORIDAD_MEDIA = "media"
private const val SKILLS_EN_RESUMEN = 3

/**
 * Test de nivelación (Flujo 1): mide en qué nivel está el usuario y qué le falta para su cargo meta.
 *
 * Preguntas: el test activo que un admin armó para el cargo; si no hay, 3 técnicas por nivel desde el banco.
 * Corrección: nivel global y por skill con [CalculadoraNivel]; las preguntas sin responder cuentan 0.
 * Resultado: brecha por skill contra cargo_skill y nivel acumulado en nivel_skill_usuario.
 */
class ServicioNivelacion(
    private val nivelaciones: RepositorioNivelacion,
    private val tests: RepositorioTestNivelacion,
    private val preguntas: RepositorioPregunta,
    private val selector: SelectorPreguntas,
    private val contexto: ResolutorContextoPrueba,
    private val mercado: LectorMercado,
    private val niveles: RepositorioNivelSkill,
    private val corrector: CorrectorRespuestas,
    private val reloj: Clock = Clock.systemUTC()
) {

    suspend fun iniciar(usuarioId: UUID, cargoId: String?, nombreCargo: String?): IntentoNivelacion {
        val (cargo, nombre) = contexto.cargo(usuarioId, cargoId, nombreCargo)
        val (testId, elegidas) = preguntasDeTestAdmin(cargo) ?: (null to preguntasDelBanco(cargo))
        if (elegidas.size < PREGUNTAS_NIVELACION_MINIMO) {
            throw ErrorConflicto(
                "preguntas_insuficientes",
                "Aún no hay suficientes preguntas aprobadas para nivelar $nombre; inténtalo más adelante"
            )
        }
        val servidas = elegidas.mapIndexed { indice, pregunta -> pregunta.aServida(indice + 1) }
        return nivelaciones.crearIntento(usuarioId, testId, cargo?.id, nombre, servidas, reloj.instant())
    }

    suspend fun obtener(usuarioId: UUID, intentoId: UUID): NivelacionConResultado {
        val intento = buscarPropio(usuarioId, intentoId) ?: throw ErrorNoEncontrado("nivelacion_no_encontrada", "El test de nivelación no existe")
        return NivelacionConResultado(intento, if (intento.estaTerminado) nivelaciones.buscarResultado(intentoId) else null)
    }

    suspend fun buscarPropio(usuarioId: UUID, intentoId: UUID): IntentoNivelacion? =
        nivelaciones.buscarIntento(intentoId)?.takeIf { it.usuarioId == usuarioId }

    suspend fun ultimoResultado(usuarioId: UUID): ResultadoNivelacion? = nivelaciones.ultimoResultado(usuarioId)

    suspend fun nivelesPorSkill(usuarioId: UUID): List<Pair<NivelSkillUsuario, String>> =
        niveles.listar(usuarioId).map { it to (mercado.buscarSkill(it.skillId)?.nombre ?: "Skill") }

    /** Corrige todas las respuestas, asigna el nivel y guarda el resultado. Se rinde una sola vez. */
    suspend fun responder(usuarioId: UUID, intentoId: UUID, respuestas: List<RespuestaPrueba>): NivelacionConResultado {
        val intento = obtener(usuarioId, intentoId).intento
        if (intento.estaTerminado) throw nivelacionTerminada()
        if (respuestas.isEmpty()) throw ErrorValidacion("sin_respuestas", "Responde al menos una pregunta")
        val detalle = corregir(intento, respuestas)
        val resultado = calcularResultado(intento, detalle)

        if (!nivelaciones.registrarResultado(intentoId, detalle, resultado, usuarioId)) throw nivelacionTerminada()
        niveles.acumular(usuarioId, evaluacionesPorSkill(detalle), resultado.fecha)
        return NivelacionConResultado(nivelaciones.buscarIntento(intentoId)!!, resultado)
    }

    // ---------- Preguntas ----------

    /** Preguntas vigentes del test que armó un admin para el cargo, si alcanzan. */
    private suspend fun preguntasDeTestAdmin(cargo: Cargo?): Pair<UUID, List<Pregunta>>? {
        val test = cargo?.let { tests.buscarActivoParaCargo(it.id) } ?: return null
        val vigentes = test.preguntasIds.mapNotNull { id ->
            preguntas.buscarPorId(id)?.takeIf { it.estado == EstadoPregunta.APROBADA && it.tipo != TipoPregunta.SIMULACION_VIDEO }
        }
        return if (vigentes.size >= PREGUNTAS_NIVELACION_MINIMO) test.id to vigentes.sortedBy { it.nivel.ordinal } else null
    }

    /** De junior a senior, para ver hasta dónde llega. */
    private suspend fun preguntasDelBanco(cargo: Cargo?): List<Pregunta> {
        val elegidas = mutableListOf<Pregunta>()
        for (nivel in NivelExperiencia.entries) {
            val contextoNivel = ContextoSeleccion(cargo, nivel, tipos = ModoPractica.MIXTO.tipos)
            elegidas += selector.seleccionar(contextoNivel, CategoriaHabilidad.TECNICA, PREGUNTAS_NIVELACION_POR_NIVEL, elegidas.map { it.id }.toSet())
        }
        return elegidas
    }

    // ---------- Corrección ----------

    private fun corregir(intento: IntentoNivelacion, respuestas: List<RespuestaPrueba>): List<DetalleIntento> {
        val porId = respuestas.groupBy { it.preguntaServidaId }
        if (porId.values.any { it.size > 1 }) throw ErrorValidacion("pregunta_repetida", "Cada pregunta se responde una sola vez")
        val idsServidos = intento.detalle.map { it.pregunta.id }.toSet()
        if (porId.keys.any { it !in idsServidos }) {
            throw ErrorNoEncontrado("pregunta_no_encontrada", "La pregunta no pertenece a este test")
        }
        return intento.detalle.map { item ->
            val respuesta = porId[item.pregunta.id]?.single()
            DetalleIntento(item.pregunta, respuesta?.let { corrector.corregir(item.pregunta, it.opcionId, it.texto) })
        }
    }

    private suspend fun calcularResultado(intento: IntentoNivelacion, detalle: List<DetalleIntento>): ResultadoNivelacion {
        val nivelGlobal = CalculadoraNivel.nivelLogrado(puntajesPorNivel(detalle))
        val requisitos = intento.cargoId?.let { mercado.listarRequisitos(it) }.orEmpty().associateBy { it.skill.id }
        val evaluaciones = detalle.filter { it.pregunta.skillId != null }
            .groupBy { UUID.fromString(it.pregunta.skillId) }
            .map { (skillId, items) -> evaluarSkill(skillId, items, requisitos[skillId]) }
        val (brechas, ok) = evaluaciones.partition { it.brecha > 0 }
        val ordenadas = brechas.sortedWith(compareBy({ it.prioridad != PRIORIDAD_ALTA }, { -it.brecha }, { it.puntaje }))
        return ResultadoNivelacion(
            intentoId = intento.id,
            cargoId = intento.cargoId,
            nivelGlobal = nivelGlobal,
            puntajeGlobal = promedio(detalle),
            skillsBrecha = ordenadas,
            skillsOk = ok,
            resumen = resumen(nivelGlobal, ordenadas),
            fecha = reloj.instant()
        )
    }

    private suspend fun evaluarSkill(skillId: UUID, items: List<DetalleIntento>, requisito: RequisitoCargo?): EvaluacionSkill {
        val nivel = CalculadoraNivel.nivelLogrado(puntajesPorNivel(items))
        val brecha = requisito?.let { CalculadoraNivel.brecha(nivel, it.nivelRequerido) } ?: 0
        return EvaluacionSkill(
            skillId = skillId.toString(),
            nombre = requisito?.skill?.nombre ?: mercado.buscarSkill(skillId)?.nombre ?: "Skill",
            nivelActual = nivel.valorBd,
            nivelRequerido = requisito?.nivelRequerido?.valorBd,
            puntaje = promedio(items).toDouble(),
            brecha = brecha,
            prioridad = when {
                brecha == 0 -> null
                brecha >= 2 || requisito?.esObligatoria == true -> PRIORIDAD_ALTA
                else -> PRIORIDAD_MEDIA
            }
        )
    }

    private fun evaluacionesPorSkill(detalle: List<DetalleIntento>): List<EvaluacionNivelSkill> =
        detalle.filter { it.pregunta.skillId != null }
            .groupBy { UUID.fromString(it.pregunta.skillId) }
            .map { (skillId, items) -> EvaluacionNivelSkill(skillId, promedio(items), CalculadoraNivel.nivelLogrado(puntajesPorNivel(items))) }

    /** Las preguntas sin responder cuentan 0. */
    private fun puntajesPorNivel(items: List<DetalleIntento>): Map<NivelExperiencia, List<Double>> =
        items.groupBy({ it.pregunta.nivelExperiencia }) { it.respuesta?.puntaje ?: 0.0 }

    private fun promedio(items: List<DetalleIntento>): BigDecimal =
        BigDecimal.valueOf(items.map { it.respuesta?.puntaje ?: 0.0 }.average().takeIf { !it.isNaN() } ?: 0.0)
            .setScale(2, RoundingMode.HALF_UP)

    private fun resumen(nivel: NivelExperiencia, brechas: List<EvaluacionSkill>): String {
        val base = "Tu nivel actual es ${nombreNivel(nivel)}."
        if (brechas.isEmpty()) return "$base Cumples el nivel que pide el cargo en las skills evaluadas."
        return "$base Para llegar al cargo refuerza: ${brechas.take(SKILLS_EN_RESUMEN).joinToString(", ") { it.nombre }}."
    }

    private fun nivelacionTerminada() = ErrorConflicto("nivelacion_finalizada", "Este test de nivelación ya fue respondido")
}

/** Cómo se muestra el nivel a las personas. */
fun nombreNivel(nivel: NivelExperiencia) = when (nivel) {
    NivelExperiencia.JUNIOR -> "Junior"
    NivelExperiencia.SEMISENIOR -> "Semi Senior"
    NivelExperiencia.SENIOR -> "Senior"
}

/** Pregunta servida por id (para los servicios que reciben respuestas). */
internal fun List<PreguntaServida>.porId(id: String) = firstOrNull { it.id == id }
