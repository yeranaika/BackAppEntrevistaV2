package UTILIDADES

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/** Transacción en el pool de IO: JDBC es bloqueante y no debe ocupar los hilos de Ktor. */
suspend fun <T> transaccion(bloque: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = bloque)
