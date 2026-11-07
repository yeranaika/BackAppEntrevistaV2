package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** De dónde salió un gasto en IA. */
enum class OrigenUsoIa { REPORTE_ENTREVISTA, GENERACION_PREGUNTAS }

/**
 * Un uso registrado de la IA. En la generación de preguntas hay una fila por pregunta con su parte de los tokens y del
 * costo de la llamada (el costo ya está repartido, no se repite). Una llamada fallida guarda sus tokens sin costo.
 */
data class UsoIaRegistrado(
    val origen: OrigenUsoIa,
    val fecha: Instant,
    val modelo: String,
    val tokensEntrada: Int?,
    val tokensSalida: Int?,
    val costoUsd: Double?,
    val exitoso: Boolean
)

/** Lectura (solo para el admin) de lo que se gastó en IA. */
interface LectorCostosIa {
    /** Usos con fecha en [desde, hasta). */
    suspend fun usosEntre(desde: Instant, hasta: Instant): List<UsoIaRegistrado>
}

class LectorCostosIaExposed : LectorCostosIa {

    override suspend fun usosEntre(desde: Instant, hasta: Instant): List<UsoIaRegistrado> = transaccion {
        // Reportes en los que el LLM respondió (y se pagó): modo 'ia' si la evaluación sirvió; modo freemium si respondió
        // algo inválido y se usó el respaldo (fallida). Si el LLM no llegó a responder, no hay modelo ni costo.
        val reportes = TablaReporteEntrevista.selectAll()
            .where {
                TablaReporteEntrevista.modeloLlm.isNotNull() and
                    (TablaReporteEntrevista.fechaGeneracion greaterEq desde) and
                    (TablaReporteEntrevista.fechaGeneracion less hasta)
            }
            .map {
                UsoIaRegistrado(
                    origen = OrigenUsoIa.REPORTE_ENTREVISTA,
                    fecha = it[TablaReporteEntrevista.fechaGeneracion],
                    modelo = it[TablaReporteEntrevista.modeloLlm]!!,
                    tokensEntrada = it[TablaReporteEntrevista.tokensEntrada],
                    tokensSalida = it[TablaReporteEntrevista.tokensSalida],
                    costoUsd = it[TablaReporteEntrevista.costoUsd]?.toDouble(),
                    exitoso = it[TablaReporteEntrevista.modoEvaluacion] == ModoEvaluacion.IA.valorBd
                )
            }
        val desdeConZona = OffsetDateTime.ofInstant(desde, ZoneOffset.UTC)
        val hastaConZona = OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC)
        val generaciones = TablaGeneracionPreguntaIa.selectAll()
            .where {
                (TablaGeneracionPreguntaIa.fechaGeneracion greaterEq desdeConZona) and
                    (TablaGeneracionPreguntaIa.fechaGeneracion less hastaConZona)
            }
            .map {
                UsoIaRegistrado(
                    origen = OrigenUsoIa.GENERACION_PREGUNTAS,
                    fecha = it[TablaGeneracionPreguntaIa.fechaGeneracion].toInstant(),
                    modelo = it[TablaGeneracionPreguntaIa.modeloLlm],
                    tokensEntrada = it[TablaGeneracionPreguntaIa.tokensInput],
                    tokensSalida = it[TablaGeneracionPreguntaIa.tokensOutput],
                    costoUsd = it[TablaGeneracionPreguntaIa.costoUsd]?.toDouble(),
                    exitoso = it[TablaGeneracionPreguntaIa.parseExitoso]
                )
            }
        reportes + generaciones
    }
}
