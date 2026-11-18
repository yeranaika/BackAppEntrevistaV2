package SERVICIOS

import CONFIGURACION.GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO
import ERRORES.ErrorDemasiadosIntentos
import MODELOS.LectorUsosGeneracionIa
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Tope mensual de gasto de la generación con IA (D23) y reloj con el que se corta el mes en UTC. */
data class TopeGastoGeneracionIa(
    val usdMensual: Double = GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO,
    val reloj: Clock = Clock.systemUTC()
)

/**
 * Gasto del mes UTC en curso frente al tope (D23, D28). [gastadoUsd] y [topeUsd] van sin redondear: [estaAgotado] se
 * decide con ellos, y la vista redondea solo para mostrar. [estaDesactivado] y [estaAgotado] nunca son verdaderos a la
 * vez; la próxima generación responde 429 exactamente cuando uno de los dos lo es.
 */
data class EstadoTopeGeneracionIa(val mes: YearMonth, val gastadoUsd: BigDecimal, val topeUsd: BigDecimal) {
    /** Tope 0 (también un valor ilegible en la variable): la generación con IA está apagada. */
    val estaDesactivado: Boolean get() = topeUsd.signum() <= 0
    val estaAgotado: Boolean get() = !estaDesactivado && gastadoUsd >= topeUsd
    /** Nunca negativo: sin reserva previa, dos generaciones simultáneas pueden pasar el tope. */
    val restanteUsd: BigDecimal get() = if (estaDesactivado) BigDecimal.ZERO else (topeUsd - gastadoUsd).max(BigDecimal.ZERO)
    /** Día 1 del mes siguiente (UTC); null si está desactivada, porque no se repone sola. */
    val fechaReposicion: LocalDate? get() = if (estaDesactivado) null else mes.plusMonths(1).atDay(1)
}

/**
 * Única fuente del tope de gasto de la generación con IA (D23): la misma instancia decide el 429 de generar-ia
 * ([verificar]) y entrega lo que el panel ve en Costos IA ([estado], D28). Suma las llamadas de todos los admins del mes
 * UTC, también las fallidas con su costo estimado (costoTotalUsd, como el resumen de costos). Sin caché: con varias
 * instancias dejaría de ser una sola fuente.
 */
class ServicioTopeGeneracionIa(
    private val usos: LectorUsosGeneracionIa,
    private val tope: TopeGastoGeneracionIa = TopeGastoGeneracionIa()
) {
    private val log = LoggerFactory.getLogger(ServicioTopeGeneracionIa::class.java)

    /** Si el gasto no se puede leer, el error sube: ni un 429 falso ni un estado inventado. */
    suspend fun estado(): EstadoTopeGeneracionIa {
        val periodo = periodoMensualIa(tope.reloj.instant())
        val gastado = usos.usosEntre(periodo.desde, periodo.hasta).fold(BigDecimal.ZERO) { suma, uso -> suma + costoTotalUsd(uso) }
        return EstadoTopeGeneracionIa(periodo.mes, gastado, BigDecimal.valueOf(tope.usdMensual))
    }

    /**
     * Con el gasto del mes en el tope (o el tope en 0) lanza 429 y no se llama al LLM. Si el gasto no se puede leer, el
     * error sube y tampoco se llama (no se gasta a ciegas).
     */
    suspend fun verificar() {
        val estado = estado()
        if (estado.estaDesactivado) {
            throw ErrorDemasiadosIntentos(
                "tope_generacion_ia_alcanzado",
                "La generación de preguntas con IA está desactivada en el servidor (LIMITE_GASTO_GENERACION_IA_MENSUAL_USD = 0)"
            )
        }
        if (!estado.estaAgotado) return
        val usd = estado.topeUsd.stripTrailingZeros().toPlainString()
        log.info("Tope mensual de generación con IA alcanzado: USD {} de {} en {}", estado.gastadoUsd.toPlainString(), usd, estado.mes)
        throw ErrorDemasiadosIntentos(
            "tope_generacion_ia_alcanzado",
            "Se alcanzó el tope mensual de gasto en IA para generar preguntas (USD $usd); se repone el ${estado.fechaReposicion} (UTC)"
        )
    }
}
