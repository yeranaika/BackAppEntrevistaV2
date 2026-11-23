package PRUEBAS.DOBLES

import ERRORES.ErrorAplicacion
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifica que el bloque falle con el error de dominio [T] y el [codigo] indicado. */
inline fun <reified T : ErrorAplicacion> fallaCon(codigo: String, noinline bloque: suspend () -> Unit) {
    val error = assertFailsWith<T> { runBlocking { bloque() } }
    assertEquals(codigo, error.codigo)
}
