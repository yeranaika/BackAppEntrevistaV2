package SERVICIOS

import CONFIGURACION.VIGENCIA_RESERVA_IA
import CONFIGURACION.ZONA_CORTE_CUOTA_IA
import MODELOS.RepositorioReporte
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Mes calendario de un tope de IA, [desde, hasta), cortado a medianoche en [ZONA_CORTE_CUOTA_IA] (UTC). Un solo cálculo
 * para el cupo de reportes con IA (D17) y el tope de gasto de la generación de preguntas (D23).
 */
data class PeriodoMensualIa(val mes: YearMonth, val desde: Instant, val hasta: Instant)

fun periodoMensualIa(ahora: Instant): PeriodoMensualIa {
    val mes = YearMonth.from(ahora.atZone(ZONA_CORTE_CUOTA_IA))
    return PeriodoMensualIa(
        mes = mes,
        desde = mes.atDay(1).atStartOfDay(ZONA_CORTE_CUOTA_IA).toInstant(),
        hasta = mes.plusMonths(1).atDay(1).atStartOfDay(ZONA_CORTE_CUOTA_IA).toInstant()
    )
}

/**
 * Cupo del mes de reportes con IA de un usuario premium (D17, D29). [usadas] cuenta lo mismo que la reserva (listas
 * con IA y reservas vigentes) y puede superar [limite] si el límite se bajó a mitad de mes. [renovacion] es el inicio
 * del mes siguiente (UTC).
 */
data class CupoIaMensual(val limite: Int, val usadas: Long, val renovacion: Instant) {
    /** Entre 0 y [limite]: la próxima reserva funciona si y solo si es mayor que 0. */
    val restantes: Int get() = (limite - usadas).coerceAtMost(limite.toLong()).coerceAtLeast(0L).toInt()
}

/** Cuántos reportes con IA le quedan al usuario en el mes. null si no hay cupo que mostrar (sin IA o límite 0). */
fun interface ConsultorCupoIa {
    suspend fun consultar(usuarioId: UUID): CupoIaMensual?
}

/**
 * Lee el cupo con la misma condición que la reserva (RepositorioReporte.contarCupoIaUsado), sin bloquear: es una foto
 * para mostrar, y quien decide sigue siendo la reserva. Es una clase aparte de ServicioReporteEntrevista porque la usa
 * la suscripción, y el reporte ya depende de la suscripción (VerificadorPremium).
 */
class ServicioCupoIa(
    private val reportes: RepositorioReporte,
    /** LIMITE_EVALUACIONES_IA_MENSUAL: el mismo tope que usa el reporte. */
    private val limite: Int,
    /** false sin LLM configurado: los reportes nunca usan IA y no hay cupo que mostrar. */
    private val iaDisponible: Boolean,
    private val reloj: Clock = Clock.systemUTC()
) : ConsultorCupoIa {

    override suspend fun consultar(usuarioId: UUID): CupoIaMensual? {
        if (!iaDisponible || limite <= 0) return null
        val ahora = reloj.instant()
        val periodo = periodoMensualIa(ahora)
        val usadas = reportes.contarCupoIaUsado(usuarioId, periodo.desde, periodo.hasta, ahora.minus(VIGENCIA_RESERVA_IA))
        return CupoIaMensual(limite, usadas, renovacion = periodo.hasta)
    }
}
