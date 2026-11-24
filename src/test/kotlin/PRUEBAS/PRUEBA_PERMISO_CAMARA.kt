package PRUEBAS

import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ESQUEMAS.SolicitudRegistro
import MODELOS.ALCANCE_ANALISIS_CAMARA
import MODELOS.ALCANCE_ESTADISTICAS_USO
import MODELOS.MetricaVideo
import MODELOS.TablaMetricaVideo
import MODELOS.TablaPrediccionSimulacion
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Permiso aparte de cámara (D38): sin él no se guarda lo medido con la cámara (métricas de video y predicciones de la
 * simulación), y al retirarlo se borra lo que se había medido.
 */
class PruebaPermisoCamara {
    private val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
    private val ana = nuevoUsuario("ana@ejemplo.com")
    private val beto = nuevoUsuario("beto@ejemplo.com")

    private fun nuevoUsuario(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    private fun publicarV11() = runBlocking {
        sistema.consentimiento.publicar(
            "v1.1", "Consentimiento", "Texto", listOf(ALCANCE_ESTADISTICAS_USO, "marketing", ALCANCE_ANALISIS_CAMARA)
        )
    }

    private fun aceptar(usuario: UUID, version: String, camara: Boolean) = runBlocking {
        sistema.consentimiento.otorgar(usuario, version, mapOf(ALCANCE_ESTADISTICAS_USO to true, ALCANCE_ANALISIS_CAMARA to camara), null)
    }

    private fun sesion(usuario: UUID): UUID = runBlocking {
        sistema.entrevista.iniciar(usuario, PedidoEntrevista(nombreCargo = "Backend Developer")).id
    }

    private fun metricas(n: Int) = (0 until n).map { MetricaVideo(it * 500L, BigDecimal("80"), BigDecimal("70"), BigDecimal("65"), null) }

    /** Manda un lote de métricas y una predicción de la simulación; devuelve cuántas métricas se guardaron. */
    private fun medir(usuario: UUID, sesionId: UUID): Int = runBlocking {
        val guardadas = sistema.entrevista.registrarMetricas(usuario, sesionId, metricas(3))
        sistema.simulacion.registrar(usuario, sesionId, 1_000, ResultadoPrediccionDivlara(VERSION_MODELO_DIVLARA, mapOf("head_nodding" to 5.0)))
        guardadas
    }

    private fun metricasDe(sesionId: UUID) = transaction { TablaMetricaVideo.selectAll().where { TablaMetricaVideo.sesionId eq sesionId }.count() }
    private fun prediccionesDe(sesionId: UUID) =
        transaction { TablaPrediccionSimulacion.selectAll().where { TablaPrediccionSimulacion.sesionId eq sesionId }.count() }

    @Test
    fun `con una version que no ofrece el permiso de camara todo se guarda como antes`() = runBlocking<Unit> {
        val version = sistema.consentimiento.textoVigente().version
        sistema.consentimiento.otorgar(ana, version, mapOf("uso_datos" to true), null)
        val s = sesion(ana)

        assertTrue(sistema.consentimiento.permite(ana))
        assertEquals(3, medir(ana, s))
        assertEquals(1L, prediccionesDe(s))
    }

    @Test
    fun `sin el permiso de camara la practica sigue pero no se guarda nada medido con la camara`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = false)
        val s = sesion(ana)

        assertFalse(sistema.consentimiento.permite(ana))
        assertEquals(0, medir(ana, s), "responde 0 métricas en vez de un error")
        assertEquals(0L, metricasDe(s))
        assertEquals(0L, prediccionesDe(s))
    }

    @Test
    fun `sin ningun consentimiento y con una version vigente que ofrece la camara no se guarda nada`() = runBlocking<Unit> {
        publicarV11()
        val s = sesion(ana)

        assertFalse(sistema.consentimiento.permite(ana))
        assertEquals(0, medir(ana, s))
        assertEquals(0L, prediccionesDe(s))
    }

    @Test
    fun `con el permiso de camara se guarda y al quitarlo se borra lo medido, sin tocar a otra persona`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        aceptar(beto, "v1.1", camara = true)
        val sa = sesion(ana)
        val sb = sesion(beto)
        assertEquals(3, medir(ana, sa))
        assertEquals(3, medir(beto, sb))

        aceptar(ana, "v1.1", camara = false)

        assertEquals(0L, metricasDe(sa))
        assertEquals(0L, prediccionesDe(sa))
        assertEquals(3L, metricasDe(sb), "lo de otra persona no se toca")
        assertEquals(1L, prediccionesDe(sb))
        assertEquals(0, medir(ana, sa), "después de quitarlo ya no se guarda")
    }

    @Test
    fun `volver a aceptar con el permiso de camara no borra lo medido`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        val s = sesion(ana)
        medir(ana, s)

        aceptar(ana, "v1.1", camara = true)

        assertEquals(3L, metricasDe(s))
        assertEquals(1L, prediccionesDe(s))
    }

    @Test
    fun `retirar el consentimiento retira el permiso de camara y borra lo medido`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        val s = sesion(ana)
        medir(ana, s)

        sistema.consentimiento.revocar(ana)

        assertEquals(0L, metricasDe(s))
        assertEquals(0L, prediccionesDe(s))
        assertFalse(sistema.consentimiento.permite(ana))
    }
}
