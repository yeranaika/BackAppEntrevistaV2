package MODELOS

import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

interface RepositorioPrediccionSimulacion {
    /**
     * Guarda las variables de un instante. Idempotente: si la sesión ya tiene predicciones en [ts] (un reenvío)
     * no se guarda nada y devuelve false.
     */
    suspend fun registrar(sesionId: UUID, ts: Long, variables: List<PrediccionSimulacion>): Boolean

    /** Todas las predicciones de la sesión, ordenadas por instante. */
    suspend fun listar(sesionId: UUID): List<PrediccionSimulacion>
}

class RepositorioPrediccionSimulacionExposed : RepositorioPrediccionSimulacion {

    override suspend fun registrar(sesionId: UUID, ts: Long, variables: List<PrediccionSimulacion>): Boolean {
        if (variables.isEmpty()) return false
        return try {
            transaccion {
                val yaExiste = TablaPrediccionSimulacion.selectAll()
                    .where { (TablaPrediccionSimulacion.sesionId eq sesionId) and (TablaPrediccionSimulacion.ts eq ts) }
                    .limit(1).any()
                if (yaExiste) return@transaccion false
                TablaPrediccionSimulacion.batchInsert(variables, shouldReturnGeneratedValues = false) { p ->
                    this[TablaPrediccionSimulacion.id] = UUID.randomUUID()
                    this[TablaPrediccionSimulacion.sesionId] = sesionId
                    this[TablaPrediccionSimulacion.ts] = ts
                    this[TablaPrediccionSimulacion.variable] = p.variable
                    this[TablaPrediccionSimulacion.valor] = p.valor
                    this[TablaPrediccionSimulacion.nivel] = p.nivel.toShort()
                }
                true
            }
        } catch (e: ExposedSQLException) {
            // Otro envío del mismo instante entró entre la lectura y la inserción: es un reenvío, no un error.
            if (e.sqlState != SQLSTATE_VALOR_DUPLICADO) throw e
            false
        }
    }

    override suspend fun listar(sesionId: UUID): List<PrediccionSimulacion> = transaccion {
        TablaPrediccionSimulacion.selectAll()
            .where { TablaPrediccionSimulacion.sesionId eq sesionId }
            .orderBy(TablaPrediccionSimulacion.ts)
            .map {
                PrediccionSimulacion(
                    ts = it[TablaPrediccionSimulacion.ts],
                    variable = it[TablaPrediccionSimulacion.variable],
                    valor = it[TablaPrediccionSimulacion.valor],
                    nivel = it[TablaPrediccionSimulacion.nivel].toInt()
                )
            }
    }
}
