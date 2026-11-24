package MODELOS

import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

interface RepositorioLlamada {
    /** Guarda la llamada. Idempotente por (usuario, idLocal): si ya estaba no la toca y devuelve false. */
    suspend fun registrar(usuarioId: UUID, llamada: Llamada): Boolean

    suspend fun buscarPorIdLocal(usuarioId: UUID, idLocal: String): Llamada?

    /** Solo si la llamada es del usuario. */
    suspend fun buscar(usuarioId: UUID, id: UUID): Llamada?

    /** Las más recientes primero. */
    suspend fun listarDeUsuario(usuarioId: UUID, pagina: Int, tamano: Int): PaginaLlamadas
}

class RepositorioLlamadaExposed : RepositorioLlamada {

    override suspend fun registrar(usuarioId: UUID, llamada: Llamada): Boolean {
        if (buscarPorIdLocal(usuarioId, llamada.idLocal) != null) return false
        return try {
            transaccion {
                TablaLlamada.insert {
                    it[id] = llamada.id
                    it[TablaLlamada.usuarioId] = usuarioId
                    it[idLocal] = llamada.idLocal
                    it[sesionId] = llamada.sesionId
                    it[fechaInicio] = llamada.fechaInicio
                    it[duracionSeg] = llamada.duracionSeg
                    it[preguntasCnt] = llamada.preguntasCnt
                    it[resumen] = llamada.resumen
                    it[recibidaEn] = Instant.now()
                }
                true
            }
        } catch (e: ExposedSQLException) {
            // Otro envío de la misma llamada entró entre la lectura y la inserción: es un reenvío, no un error.
            if (e.sqlState != SQLSTATE_VALOR_DUPLICADO) throw e
            false
        }
    }

    override suspend fun buscarPorIdLocal(usuarioId: UUID, idLocal: String): Llamada? = transaccion {
        TablaLlamada.selectAll()
            .where { (TablaLlamada.usuarioId eq usuarioId) and (TablaLlamada.idLocal eq idLocal) }
            .limit(1).firstOrNull()?.aLlamada()
    }

    override suspend fun buscar(usuarioId: UUID, id: UUID): Llamada? = transaccion {
        TablaLlamada.selectAll()
            .where { (TablaLlamada.usuarioId eq usuarioId) and (TablaLlamada.id eq id) }
            .limit(1).firstOrNull()?.aLlamada()
    }

    override suspend fun listarDeUsuario(usuarioId: UUID, pagina: Int, tamano: Int): PaginaLlamadas = transaccion {
        val consulta = TablaLlamada.selectAll().where { TablaLlamada.usuarioId eq usuarioId }
        val total = consulta.count()
        val elementos = consulta
            .orderBy(TablaLlamada.fechaInicio to SortOrder.DESC, TablaLlamada.id to SortOrder.ASC)
            .limit(tamano, offset = (pagina - 1).toLong() * tamano)
            .map { it.aLlamada() }
        PaginaLlamadas(elementos, total, pagina, tamano)
    }

    private fun ResultRow.aLlamada() = Llamada(
        id = this[TablaLlamada.id],
        idLocal = this[TablaLlamada.idLocal],
        sesionId = this[TablaLlamada.sesionId],
        fechaInicio = this[TablaLlamada.fechaInicio],
        duracionSeg = this[TablaLlamada.duracionSeg],
        preguntasCnt = this[TablaLlamada.preguntasCnt],
        resumen = this[TablaLlamada.resumen]
    )
}
