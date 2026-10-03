package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private const val ESTADO_REVISION_ERROR = "error_parse"

/** Una pregunta válida del LLM con su parte del costo de la llamada. */
data class PreguntaGenerada(
    val contenido: ContenidoPregunta,
    val tokensEntrada: Int?,
    val tokensSalida: Int?,
    val costoUsd: Double?
)

/** Todo lo necesario para auditar una llamada al LLM. */
data class TrazaGeneracion(
    val cargoId: UUID?,
    val skillId: UUID?,
    val nivel: NivelExperiencia,
    val modelo: String,
    val prompt: String,
    val respuestaCruda: String?,
    val tokensEntrada: Int?,
    val tokensSalida: Int?
)

data class IdsGenerados(val preguntaId: UUID, val generacionId: UUID)

interface RepositorioGeneracionPreguntaIa {
    /** Guarda las preguntas (estado pendiente) con su trazabilidad, todo o nada. */
    suspend fun guardarLote(traza: TrazaGeneracion, preguntas: List<PreguntaGenerada>): List<IdsGenerados>

    /** Registra una llamada cuyo resultado no se pudo usar (para auditar costo y fallas). */
    suspend fun registrarFallo(traza: TrazaGeneracion)
}

class RepositorioGeneracionPreguntaIaExposed : RepositorioGeneracionPreguntaIa {

    override suspend fun guardarLote(traza: TrazaGeneracion, preguntas: List<PreguntaGenerada>): List<IdsGenerados> =
        transaccion {
            val ahora = OffsetDateTime.now()
            preguntas.map { generada ->
                val ids = IdsGenerados(UUID.randomUUID(), UUID.randomUUID())
                insertarPreguntaConOpciones(ids.preguntaId, generada.contenido, EstadoPregunta.PENDIENTE, esGeneradaPorIa = true, ahora)
                TablaGeneracionPreguntaIa.insert {
                    it[generacionId] = ids.generacionId
                    it[preguntaId] = ids.preguntaId
                    escribirTraza(it, traza, ahora)
                    it[respuestaRaw] = traza.respuestaCruda
                    it[parseExitoso] = true
                    it[tokensInput] = generada.tokensEntrada
                    it[tokensOutput] = generada.tokensSalida
                    it[costoUsd] = generada.costoUsd?.let(BigDecimal::valueOf)
                    it[estadoRevision] = EstadoPregunta.PENDIENTE.valorTrazabilidad
                }
                ids
            }
        }

    override suspend fun registrarFallo(traza: TrazaGeneracion) {
        transaccion {
            TablaGeneracionPreguntaIa.insert {
                it[generacionId] = UUID.randomUUID()
                it[preguntaId] = null
                escribirTraza(it, traza, OffsetDateTime.now())
                it[respuestaRaw] = traza.respuestaCruda
                it[parseExitoso] = false
                it[tokensInput] = traza.tokensEntrada
                it[tokensOutput] = traza.tokensSalida
                it[costoUsd] = null
                it[estadoRevision] = ESTADO_REVISION_ERROR
            }
        }
    }

    private fun escribirTraza(fila: UpdateBuilder<*>, traza: TrazaGeneracion, ahora: OffsetDateTime) {
        fila[TablaGeneracionPreguntaIa.cargoId] = traza.cargoId
        fila[TablaGeneracionPreguntaIa.skillId] = traza.skillId
        fila[TablaGeneracionPreguntaIa.nivelSolicitado] = traza.nivel.valorBd
        fila[TablaGeneracionPreguntaIa.modeloLlm] = traza.modelo
        fila[TablaGeneracionPreguntaIa.promptEnviado] = traza.prompt
        fila[TablaGeneracionPreguntaIa.fechaGeneracion] = ahora
    }
}
