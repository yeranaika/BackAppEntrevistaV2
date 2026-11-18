package PRUEBAS

import UTILIDADES.TareasSegundoPlano
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Apagado ordenado (D20): esperar las tareas en curso, con tope, antes de cerrar. */
class PruebaTareasSegundoPlano {
    private val tareas = TareasSegundoPlano(Dispatchers.IO)

    @Test
    fun `espera a la tarea en curso antes de cerrar`() = runBlocking<Unit> {
        val barrera = CompletableDeferred<Unit>()
        val terminada = AtomicBoolean(false)
        tareas.launch { barrera.await(); terminada.set(true) }

        val cierre = async { tareas.esperarYCerrar(10.seconds) }
        delay(100)
        assertFalse(cierre.isCompleted, "no cierra mientras la tarea sigue")
        barrera.complete(Unit)

        assertEquals(0, cierre.await(), "no se canceló nada")
        assertTrue(terminada.get())
    }

    @Test
    fun `espera tambien a las tareas lanzadas durante la espera`() = runBlocking<Unit> {
        val primera = CompletableDeferred<Unit>()
        val segunda = CompletableDeferred<Unit>()
        val terminoLaSegunda = AtomicBoolean(false)
        tareas.launch {
            primera.await()
            tareas.launch { segunda.await(); terminoLaSegunda.set(true) } // por ejemplo, un reintento
        }

        val cierre = async { tareas.esperarYCerrar(10.seconds) }
        delay(100) // el cierre ya está esperando a la primera tarea (la segunda todavía no existe)
        primera.complete(Unit)
        delay(100)
        assertFalse(cierre.isCompleted, "la tarea lanzada durante la espera también se espera")
        segunda.complete(Unit)

        assertEquals(0, cierre.await())
        assertTrue(terminoLaSegunda.get())
    }

    @Test
    fun `al vencer el plazo cancela y deja limpiar`() = runBlocking<Unit> {
        val limpio = AtomicBoolean(false)
        val empezo = CompletableDeferred<Unit>()
        tareas.launch {
            try {
                empezo.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(50); limpio.set(true) } // p. ej. marcar el reporte como interrumpido
            }
        }
        empezo.await()

        assertEquals(1, tareas.esperarYCerrar(200.milliseconds), "una tarea no terminó a tiempo")
        assertTrue(limpio.get(), "tuvo tiempo de dejar su estado antes de cerrar la BD")
    }

    @Test
    fun `cerrar dos veces no falla y despues no ejecuta tareas nuevas`() = runBlocking<Unit> {
        assertEquals(0, tareas.esperarYCerrar(1.seconds))
        assertEquals(0, tareas.esperarYCerrar(1.seconds))
        val ejecutada = AtomicBoolean(false)
        tareas.launch { ejecutada.set(true) }.join()
        assertFalse(ejecutada.get(), "cerrado: una tarea nueva no corre")
    }

    @Test
    fun `una tarea que falla no corta a las demas`() = runBlocking<Unit> {
        val barrera = CompletableDeferred<Unit>()
        val terminada = AtomicBoolean(false)
        tareas.launch { error("falla esta tarea") }
        tareas.launch { barrera.await(); terminada.set(true) }
        delay(50)
        barrera.complete(Unit)
        assertEquals(0, tareas.esperarYCerrar(5.seconds))
        assertTrue(terminada.get())
    }
}
