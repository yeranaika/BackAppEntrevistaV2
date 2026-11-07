package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import SERVICIOS.ProcesadorEntrevistaFinalizada
import SERVICIOS.RespuestaUsuario
import SERVICIOS.ServicioEntrevista
import UTILIDADES.TareasSegundoPlano
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Orden del apagado en Ktor (D20): las tareas se esperan antes de cerrar la BD. */
class PruebaApagadoOrdenado {

    @Test
    fun `la espera de tareas ocurre antes de cerrar la base de datos`() {
        val eventos = Collections.synchronizedList(mutableListOf<String>())
        testApplication {
            application {
                val tareas = TareasSegundoPlano(Dispatchers.IO)
                // Mismo orden que Application.kt: configurarBaseDatos se suscribe primero a ApplicationStopped.
                monitor.subscribe(ApplicationStopped) { eventos += "base-de-datos-cerrada" }
                monitor.subscribe(ApplicationStopping) { runBlocking { tareas.esperarYCerrar(10.seconds) } }
                tareas.launch { delay(300); eventos += "reporte-terminado" }
            }
            startApplication()
        }
        assertEquals(listOf("reporte-terminado", "base-de-datos-cerrada"), eventos.toList())
    }

    @Test
    fun `finalizar no registra como error una cancelacion por apagado`() = runBlocking<Unit> {
        val sistema = SistemaPrueba().apply { sembrarBancoGeneral() }
        sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1"))
        val ana = sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
        val tareas = TareasSegundoPlano(Dispatchers.IO)
        val empezo = CompletableDeferred<Unit>()
        val procesadorLento = ProcesadorEntrevistaFinalizada { _: SesionEntrevista -> empezo.complete(Unit); awaitCancellation() }
        val servicio = ServicioEntrevista(
            sistema.sesionesEntrevista, sistema.metricasVideo, sistema.selectorPreguntas, sistema.resolutorContexto,
            procesadorLento, tareas, sistema.reloj
        )
        val logger = LoggerFactory.getLogger(ServicioEntrevista::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val sesion = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA"))
            val p = sesion.preguntas.first { it.tipo == TipoPregunta.OPCION_MULTIPLE }
            servicio.responderYFinalizar(ana, sesion.id, listOf(RespuestaUsuario(p.id, opcionId = p.opcionCorrecta!!.id)))
            empezo.await()

            assertEquals(1, tareas.esperarYCerrar(0.seconds))
            assertTrue(appender.list.none { it.level == Level.ERROR }, appender.list.joinToString { it.formattedMessage })
        } finally {
            logger.detachAppender(appender)
        }
    }
}
