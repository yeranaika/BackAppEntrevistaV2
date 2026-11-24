package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.deleteWhere
import java.util.UUID

/**
 * Borra lo que se midió con la cámara en todas las sesiones de una persona: las métricas de video (`metrica_video`) y
 * las predicciones de la simulación (`prediccion_simulacion`). Se usa cuando retira el permiso de cámara (D38).
 */
fun interface BorradorDatosCamara {
    /** Devuelve cuántas filas borró en total. */
    suspend fun borrarDeUsuario(usuarioId: UUID): Int
}

class BorradorDatosCamaraExposed : BorradorDatosCamara {
    override suspend fun borrarDeUsuario(usuarioId: UUID): Int = transaccion {
        val sesiones = TablaSesionEntrevista.select(TablaSesionEntrevista.sesionId)
            .where { TablaSesionEntrevista.usuarioId eq usuarioId }
        val metricas = TablaMetricaVideo.deleteWhere { TablaMetricaVideo.sesionId inSubQuery sesiones }
        val predicciones = TablaPrediccionSimulacion.deleteWhere { TablaPrediccionSimulacion.sesionId inSubQuery sesiones }
        metricas + predicciones
    }
}
