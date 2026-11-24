package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** Frases del reporte que salen solo de las métricas de video (SERVICIO_REPORTE_ENTREVISTA). */
const val FRASE_FORTALEZA_CORPORAL = "Buen lenguaje corporal y contacto visual"
const val FRASE_MEJORA_CORPORAL = "Trabaja el contacto visual y la postura frente a la cámara"

/**
 * Borra lo que se midió con la cámara en todo lo de una persona (D38): las métricas de video (`metrica_video`), las
 * predicciones de la simulación (`prediccion_simulacion`), el resumen de sus llamadas en vivo (acciones, línea de tiempo
 * y feedback) y, en sus reportes, el puntaje de lenguaje corporal y las frases que salen de él. Como en el borrado de
 * datos biométricos de una sesión, el puntaje global y el resumen del reporte se quedan. Se usa al retirar el permiso
 * de cámara o el consentimiento.
 */
fun interface BorradorDatosCamara {
    /** Devuelve cuántas filas cambió o borró en total. */
    suspend fun borrarDeUsuario(usuarioId: UUID): Int
}

class BorradorDatosCamaraExposed : BorradorDatosCamara {
    override suspend fun borrarDeUsuario(usuarioId: UUID): Int = transaccion {
        val sesiones = TablaSesionEntrevista.select(TablaSesionEntrevista.sesionId)
            .where { TablaSesionEntrevista.usuarioId eq usuarioId }
        val metricas = TablaMetricaVideo.deleteWhere { TablaMetricaVideo.sesionId inSubQuery sesiones }
        val predicciones = TablaPrediccionSimulacion.deleteWhere { TablaPrediccionSimulacion.sesionId inSubQuery sesiones }
        val llamadas = TablaLlamada.update({ TablaLlamada.usuarioId eq usuarioId }) { it[resumen] = ResumenLlamada() }
        var reportes = 0
        TablaReporteEntrevista.selectAll()
            .where { (TablaReporteEntrevista.sesionId inSubQuery sesiones) and TablaReporteEntrevista.puntajeLenguajeCorporal.isNotNull() }
            .forEach { fila ->
                reportes += TablaReporteEntrevista.update({ TablaReporteEntrevista.reporteId eq fila[TablaReporteEntrevista.reporteId] }) {
                    it[puntajeLenguajeCorporal] = null
                    it[fortalezas] = fila[TablaReporteEntrevista.fortalezas] - FRASE_FORTALEZA_CORPORAL
                    it[areasMejora] = fila[TablaReporteEntrevista.areasMejora] - FRASE_MEJORA_CORPORAL
                }
            }
        metricas + predicciones + llamadas + reportes
    }
}
