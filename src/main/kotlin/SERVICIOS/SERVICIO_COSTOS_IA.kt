package SERVICIOS

import ERRORES.ErrorValidacion
import INTEGRACIONES.costoLlmUsd
import MODELOS.LectorCostosIa
import MODELOS.OrigenUsoIa
import MODELOS.UsoIaRegistrado
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Días que se muestran si no se indica el rango. */
const val DIAS_COSTOS_IA_POR_DEFECTO = 30L

/** Rango máximo que se puede consultar de una vez (un trimestre). */
const val DIAS_COSTOS_IA_MAXIMO = 92L

/** Gasto en IA de un grupo de usos (USD sin redondear; la vista redondea). */
data class GastoIa(
    val usos: Int = 0,
    val fallidas: Int = 0,
    val tokensEntrada: Long = 0,
    val tokensSalida: Long = 0,
    val costoUsd: BigDecimal = BigDecimal.ZERO,
    val costoEstimadoUsd: BigDecimal = BigDecimal.ZERO
) {
    operator fun plus(otro: GastoIa) = GastoIa(
        usos + otro.usos, fallidas + otro.fallidas, tokensEntrada + otro.tokensEntrada, tokensSalida + otro.tokensSalida,
        costoUsd + otro.costoUsd, costoEstimadoUsd + otro.costoEstimadoUsd
    )
}

data class CostoIaDia(val fecha: LocalDate, val reportes: GastoIa, val generacionPreguntas: GastoIa)

data class ResumenCostosIa(
    val desde: LocalDate,
    val hasta: LocalDate,
    val reportes: GastoIa,
    val generacionPreguntas: GastoIa,
    val porDia: List<CostoIaDia>,
    val porModelo: List<Pair<String, GastoIa>>
) {
    val total: GastoIa get() = reportes + generacionPreguntas
}

/**
 * Cuánto se gastó en IA, para el admin: reportes de entrevista evaluados con IA (premium, D17) y generación de preguntas.
 * Los días se cortan en UTC, igual que el tope mensual. Una llamada que falló (respuesta inválida) se pagó igual pero no
 * guardó su costo: se estima con la misma tabla de precios que usa el resto del sistema (costoLlmUsd).
 */
class ServicioCostosIa(
    private val lector: LectorCostosIa,
    private val reloj: Clock = Clock.systemUTC()
) {
    /** [desde] y [hasta] son días incluidos (UTC). Sin ellos: los últimos [DIAS_COSTOS_IA_POR_DEFECTO] días. */
    suspend fun resumen(desde: LocalDate?, hasta: LocalDate?): ResumenCostosIa {
        val fin = hasta ?: LocalDate.now(reloj.withZone(ZoneOffset.UTC))
        val inicio = desde ?: fin.minusDays(DIAS_COSTOS_IA_POR_DEFECTO - 1)
        if (fin.isBefore(inicio)) throw ErrorValidacion("rango_invalido", "La fecha 'hasta' no puede ser anterior a 'desde'")
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_COSTOS_IA_MAXIMO) {
            throw ErrorValidacion("rango_invalido", "Se pueden consultar como máximo $DIAS_COSTOS_IA_MAXIMO días de una vez")
        }

        val usos = lector.usosEntre(
            inicio.atStartOfDay(ZoneOffset.UTC).toInstant(),
            fin.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        )
        val porOrigen = usos.groupBy { it.origen }
        val porDia = usos.groupBy { it.fecha.atZone(ZoneOffset.UTC).toLocalDate() }.toSortedMap().map { (dia, delDia) ->
            CostoIaDia(dia, sumar(delDia.filter { it.origen == OrigenUsoIa.REPORTE_ENTREVISTA }), sumar(delDia.filter { it.origen == OrigenUsoIa.GENERACION_PREGUNTAS }))
        }
        val porModelo = usos.groupBy { it.modelo }.map { (modelo, delModelo) -> modelo to sumar(delModelo) }
            .sortedWith(compareByDescending<Pair<String, GastoIa>> { it.second.costoUsd }.thenBy { it.first })

        return ResumenCostosIa(
            desde = inicio,
            hasta = fin,
            reportes = sumar(porOrigen[OrigenUsoIa.REPORTE_ENTREVISTA].orEmpty()),
            generacionPreguntas = sumar(porOrigen[OrigenUsoIa.GENERACION_PREGUNTAS].orEmpty()),
            porDia = porDia,
            porModelo = porModelo
        )
    }

    private fun sumar(usos: List<UsoIaRegistrado>): GastoIa = usos.fold(GastoIa()) { suma, uso -> suma + gastoDe(uso) }

    private fun gastoDe(uso: UsoIaRegistrado): GastoIa {
        val tokens = GastoIa(tokensEntrada = (uso.tokensEntrada ?: 0).toLong(), tokensSalida = (uso.tokensSalida ?: 0).toLong())
        if (uso.exitoso) return tokens.copy(usos = 1, costoUsd = costoTotalUsd(uso))
        val estimado = if (uso.costoUsd == null) costoTotalUsd(uso) else BigDecimal.ZERO
        return tokens.copy(fallidas = 1, costoUsd = costoTotalUsd(uso), costoEstimadoUsd = estimado)
    }
}

/**
 * Costo total de un uso: el guardado; si una llamada fallida (respondió algo inútil) no lo guardó, el estimado con los
 * tokens que sí se pagaron. Lo usan el resumen para el admin y el tope de generación (D23), para que sumen igual.
 */
fun costoTotalUsd(uso: UsoIaRegistrado): BigDecimal = when {
    uso.costoUsd != null -> BigDecimal.valueOf(uso.costoUsd)
    uso.exitoso -> BigDecimal.ZERO
    else -> BigDecimal.valueOf(costoLlmUsd(uso.modelo, uso.tokensEntrada, uso.tokensSalida))
}
