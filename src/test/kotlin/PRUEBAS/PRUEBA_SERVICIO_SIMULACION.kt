package PRUEBAS

import CONFIGURACION.ACCION_ESPERANDO_VENTANA
import CONFIGURACION.ACCION_SIN_ACCION
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import MODELOS.PrediccionSimulacion
import MODELOS.TablaPrediccionSimulacion
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.ConstructorReporteSimulacion
import SERVICIOS.PedidoEntrevista
import VISTAS.aTexto
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reglas del reporte de simulación (T3.10): qué se guarda, quién lo ve y cómo se arman los tramos. */
class PruebaServicioSimulacion {
    private val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
    private val ana = nuevoUsuario("ana@ejemplo.com")
    private val beto = nuevoUsuario("beto@ejemplo.com")
    private val sesion = runBlocking { sistema.entrevista.iniciar(ana, PedidoEntrevista(nombreCargo = "Backend Developer")) }.id

    private fun nuevoUsuario(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    private fun resultado(vararg valores: Pair<String, Double>) =
        ResultadoPrediccionDivlara(VERSION_MODELO_DIVLARA, mapOf(*valores))

    private fun instante(ts: Long, accion: String? = null) = listOf(
        PrediccionSimulacion(ts, "head_nodding", if (accion == "head_nodding") 7.0 else 1.0, if (accion == "head_nodding") 7 else 1),
        PrediccionSimulacion(ts, "gesture_activity", if (accion == "gesture_activity") 6.0 else 0.4, if (accion == "gesture_activity") 6 else 0)
    )

    // ---------- Guardar ----------

    @Test
    fun `guarda una fila por variable con el nivel redondeado a la escala 0-8`() = runBlocking {
        sistema.simulacion.registrar(ana, sesion, 3_000, resultado("head_nodding" to 6.6, "torso_lean" to 11.0, "arm_crossing" to -2.0))

        val filas = sistema.prediccionesSimulacion.listar(sesion).associateBy { it.variable }
        assertEquals(setOf("head_nodding", "torso_lean", "arm_crossing"), filas.keys)
        assertEquals(7, filas.getValue("head_nodding").nivel)
        assertEquals(8, filas.getValue("torso_lean").nivel, "el nivel no pasa del tope de la escala")
        assertEquals(0, filas.getValue("arm_crossing").nivel)
        assertEquals(6.6, filas.getValue("head_nodding").valor, "el valor se guarda tal cual")
        assertTrue(filas.values.all { it.ts == 3_000L })
    }

    @Test
    fun `sin sesion no guarda nada y reenviar el mismo instante no duplica`() = runBlocking {
        sistema.simulacion.registrar(ana, null, 1_000, resultado("head_nodding" to 5.0))
        assertTrue(sistema.prediccionesSimulacion.listar(sesion).isEmpty())

        repeat(2) { sistema.simulacion.registrar(ana, sesion, 2_000, resultado("head_nodding" to 5.0)) }
        assertEquals(1, sistema.prediccionesSimulacion.listar(sesion).size)
    }

    @Test
    fun `sin timestamp usa los milisegundos transcurridos desde el inicio de la sesion`() = runBlocking {
        sistema.reloj.adelantar(Duration.ofMillis(4_500))

        sistema.simulacion.registrar(ana, sesion, null, resultado("head_nodding" to 5.0))

        assertEquals(4_500L, sistema.prediccionesSimulacion.listar(sesion).single().ts)
    }

    @Test
    fun `la sesion ajena o inexistente responde 404 y el timestamp negativo 400`() {
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { sistema.simulacion.registrar(beto, sesion, 0, resultado("a" to 1.0)) }
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { sistema.simulacion.registrar(ana, UUID.randomUUID(), 0, resultado("a" to 1.0)) }
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { sistema.simulacion.reporte(beto, sesion) }
        fallaCon<ErrorValidacion>("timestamp_invalido") { sistema.simulacion.registrar(ana, sesion, -1, resultado("a" to 1.0)) }
        assertTrue(runBlocking { sistema.prediccionesSimulacion.listar(sesion) }.isEmpty())
    }

    @Test
    fun `la purga biometrica tambien borra las predicciones de la sesion`() = runBlocking {
        sistema.simulacion.registrar(ana, sesion, 1_000, resultado("head_nodding" to 5.0))
        sistema.sesionesEntrevista.cerrar(sesion, MODELOS.EstadoSesionEntrevista.CANCELADA, sistema.reloj.instant())

        sistema.sesionesEntrevista.purgarDatosBiometricos(sesion)

        assertEquals(0, transaction(sistema.db) { TablaPrediccionSimulacion.selectAll().count() }.toInt())
    }

    // ---------- Armar los tramos ----------

    @Test
    fun `une los instantes consecutivos de la misma accion en un tramo y suma el resumen`() {
        val datos = instante(3_000, "head_nodding") + instante(4_000, "head_nodding") + instante(5_000, "gesture_activity") +
            instante(6_000) + instante(7_000, "head_nodding") + instante(8_000, "head_nodding")

        val reporte = ConstructorReporteSimulacion.construir(sesion, datos.shuffled())

        assertEquals(
            listOf(
                Triple(0L, 3_000L, ACCION_ESPERANDO_VENTANA),
                Triple(3_000L, 5_000L, "head_nodding"),
                Triple(5_000L, 6_000L, "gesture_activity"),
                Triple(6_000L, 7_000L, ACCION_SIN_ACCION),
                Triple(7_000L, 9_000L, "head_nodding")
            ),
            reporte.tramos.map { Triple(it.inicioMs, it.finMs, it.accion) }
        )
        assertEquals(9_000L, reporte.duracionMs, "el último tramo dura lo que duró el paso anterior")
        assertEquals(6, reporte.predicciones, "cuenta instantes, no variables")
        val primero = reporte.resumen.first()
        assertEquals(listOf("head_nodding", "gesture_activity"), reporte.resumen.map { it.accion }, "ordenado por tiempo total descendente")
        assertEquals(Triple(2, 4_000L, 2_000L), Triple(primero.veces, primero.totalMs, primero.maximoMs))
        assertEquals(2_000.0, primero.promedioMs)
    }

    @Test
    fun `una sola prediccion dura un segundo y empezar en cero no deja tramo de espera`() {
        val reporte = ConstructorReporteSimulacion.construir(sesion, instante(0, "gesture_activity"))

        assertEquals(1, reporte.tramos.size)
        assertEquals(1_000L, reporte.duracionMs)
    }

    @Test
    fun `el texto tiene cabecera, linea de tiempo y resumen con anchos fijos`() = runBlocking {
        listOf(3_000L to "head_nodding", 4_000L to "head_nodding", 5_000L to "gesture_activity").forEach { (ts, accion) ->
            val alta = if (accion == "head_nodding") "head_nodding" to 7.0 else "gesture_activity" to 6.0
            sistema.simulacion.registrar(ana, sesion, ts, resultado(alta, "torso_lean" to 1.0))
        }

        val lineas = sistema.simulacion.reporte(ana, sesion).aTexto().lines()

        assertEquals("== REPORTE DE SIMULACION ==", lineas[0])
        assertEquals("Sesion: $sesion   Duracion: 00:00:06   Predicciones: 3", lineas[1])
        assertEquals("== LINEA DE TIEMPO ==", lineas[3])
        assertEquals("hh:mm:ss       hh:mm:ss       ACCION                            DUR", lineas[4])
        assertEquals("00:00:00       00:00:03       (esperando ventana)              3.0s", lineas[5])
        assertEquals("00:00:03       00:00:05       head_nodding                     2.0s", lineas[6])
        assertEquals("00:00:05       00:00:06       gesture_activity                 1.0s", lineas[7])
        assertEquals("== RESUMEN POR ACCION ==", lineas[9])
        assertEquals("ACCION                          VECES   TOTAL(s)   MAX(s)  PROM(s)", lineas[10])
        assertEquals("head_nodding                        1        2.0      2.0      2.0", lineas[11])
        assertEquals("gesture_activity                    1        1.0      1.0      1.0", lineas[12])
        assertTrue(lineas.all { !it.contains('<') && it.all { c -> c.code < 128 } }, "texto plano ASCII, sin HTML ni emojis")
    }

    @Test
    fun `sin predicciones el reporte lo dice con claridad`() = runBlocking {
        val texto = sistema.simulacion.reporte(ana, sesion).aTexto()

        assertEquals("== REPORTE DE SIMULACION ==\nSesion: $sesion\n\nsin predicciones registradas para esta sesion\n", texto)
    }
}
