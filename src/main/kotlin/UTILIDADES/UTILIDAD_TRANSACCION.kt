package UTILIDADES

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Transacción en el pool de IO: JDBC es bloqueante y no debe ocupar los hilos de Ktor.
 * [aislamiento]: una constante java.sql.Connection.TRANSACTION_*; null = el de la base de datos.
 */
suspend fun <T> transaccion(aislamiento: Int? = null, bloque: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, transactionIsolation = aislamiento, statement = bloque)

/** SQLSTATE de Postgres (y H2) cuando se viola un índice único. */
const val SQLSTATE_VALOR_DUPLICADO = "23505"
