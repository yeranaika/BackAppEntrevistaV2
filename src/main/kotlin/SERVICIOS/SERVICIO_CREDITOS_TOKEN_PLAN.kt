package SERVICIOS

import ERRORES.ErrorDemasiadosIntentos
import INTEGRACIONES.creditosTokenPlanMimo
import INTEGRACIONES.esModeloMimo
import MODELOS.LectorCostosIa
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Token Plan de Xiaomi MiMo (D31): suscripción de precio fijo con una cuota de Credits por periodo. [creditos] 0 = no
 * hay Token Plan (la clave es de pago por uso, o no hay MiMo) y manda el tope en USD de siempre (D23).
 * [diaRenovacion] (1–28) es el día del mes en que el plan se renueva (el de la suscripción); los días se cortan en UTC.
 */
data class PlanTokenMimo(
    val creditos: Long = 0,
    val diaRenovacion: Int = 1,
    val reloj: Clock = Clock.systemUTC()
) {
    val activo: Boolean get() = creditos > 0
}

/**
 * Credits del Token Plan usados en el periodo en curso [desde, hasta) (hasta = la renovación), sumando todas las
 * llamadas a MiMo que registró el servidor: reportes de entrevista y generación de preguntas, también las fallidas
 * (se pagan igual). No ve lo que se gaste con la misma clave fuera de la app: MiMo no tiene una API para leer el saldo.
 */
data class EstadoCreditosTokenPlan(
    val desde: LocalDate,
    val hasta: LocalDate,
    val creditosPlan: Long,
    val creditosUsados: Long,
    /** Llamadas a MiMo del periodo (exitosas y fallidas). */
    val llamadas: Int
) {
    val creditosRestantes: Long get() = (creditosPlan - creditosUsados).coerceAtLeast(0)
    val estaAgotado: Boolean get() = creditosUsados >= creditosPlan
}

/** Inicio del periodo del plan que contiene [hoy]: el último [dia] del mes que no sea posterior a hoy. */
internal fun inicioPeriodoPlan(hoy: LocalDate, dia: Int): LocalDate {
    val diaValido = dia.coerceIn(1, 28)
    val esteMes = hoy.withDayOfMonth(diaValido)
    return if (esteMes.isAfter(hoy)) esteMes.minusMonths(1) else esteMes
}

class ServicioCreditosTokenPlan(
    private val lector: LectorCostosIa,
    private val plan: PlanTokenMimo
) {
    private val log = LoggerFactory.getLogger(ServicioCreditosTokenPlan::class.java)

    val activo: Boolean get() = plan.activo

    /** null si no hay Token Plan. Si el uso no se puede leer, el error sube (no se inventa un estado). */
    suspend fun estado(): EstadoCreditosTokenPlan? {
        if (!plan.activo) return null
        val hoy = LocalDate.now(plan.reloj.withZone(ZoneOffset.UTC))
        val desde = inicioPeriodoPlan(hoy, plan.diaRenovacion)
        val hasta = desde.plusMonths(1)
        val usos = lector.usosEntre(desde.atStartOfDay(ZoneOffset.UTC).toInstant(), hasta.atStartOfDay(ZoneOffset.UTC).toInstant())
            .filter { esModeloMimo(it.modelo) }
        val usados = usos.sumOf { creditosTokenPlanMimo(it.modelo, it.tokensEntrada, it.tokensSalida) }
        return EstadoCreditosTokenPlan(desde, hasta, plan.creditos, usados, usos.size)
    }

    /** Con los Credits del periodo agotados lanza 429 y no se llama a MiMo (que igual cortaría el servicio). */
    suspend fun verificar() {
        val estado = estado() ?: return
        if (!estado.estaAgotado) return
        log.info("Token Plan de MiMo agotado: {} de {} Credits (periodo {} a {})", estado.creditosUsados, estado.creditosPlan, estado.desde, estado.hasta)
        throw ErrorDemasiadosIntentos(
            "tope_generacion_ia_alcanzado",
            "Se acabaron los Credits del Token Plan de MiMo de este periodo; se renuevan el ${estado.hasta} (UTC)"
        )
    }
}
