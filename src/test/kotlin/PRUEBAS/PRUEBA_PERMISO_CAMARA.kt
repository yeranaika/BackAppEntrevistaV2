package PRUEBAS

import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ERRORES.ErrorNoEncontrado
import ESQUEMAS.ResumenLlamadaApp
import ESQUEMAS.SolicitudRegistrarLlamada
import ESQUEMAS.SolicitudRegistro
import MODELOS.ALCANCE_ANALISIS_CAMARA
import MODELOS.ALCANCE_ESTADISTICAS_USO
import MODELOS.AccionLlamada
import MODELOS.FRASE_FORTALEZA_CORPORAL
import MODELOS.FRASE_MEJORA_CORPORAL
import MODELOS.MetricaVideo
import MODELOS.TablaMetricaVideo
import MODELOS.TablaPrediccionSimulacion
import MODELOS.TablaReporteEntrevista
import MODELOS.TablaLlamada
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ---------- Arreglos de la revisión de D38 ----------

    private fun publicar(version: String) = runBlocking {
        sistema.consentimiento.publicar(version, "Consentimiento", "Texto $version", listOf(ALCANCE_ESTADISTICAS_USO, "marketing", ALCANCE_ANALISIS_CAMARA))
    }

    /** Lo que manda la pantalla general o la de Perfil: sin la clave de la cámara. */
    private fun aceptarSinClaveCamara(usuario: UUID, version: String) = runBlocking {
        sistema.consentimiento.otorgar(usuario, version, mapOf(ALCANCE_ESTADISTICAS_USO to false, "marketing" to true), null)
    }

    @Test
    fun `cambiar otras opciones sin la clave de camara conserva el permiso y lo medido`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        val s = sesion(ana)
        medir(ana, s)

        val nuevo = aceptarSinClaveCamara(ana, "v1.1")

        assertTrue(ALCANCE_ANALISIS_CAMARA in nuevo.alcancesAceptados)
        assertTrue(sistema.consentimiento.permite(ana))
        assertEquals(3L, metricasDe(s))
        assertEquals(1L, prediccionesDe(s))
    }

    @Test
    fun `aceptar una version nueva sin la clave de camara conserva el permiso dado aparte`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        val s = sesion(ana)
        medir(ana, s)
        publicar("v1.2")

        aceptarSinClaveCamara(ana, "v1.2")

        assertTrue(sistema.consentimiento.permite(ana))
        assertEquals(3L, metricasDe(s))
    }

    @Test
    fun `lo medido con la v1_0 se borra al aceptar la v1_1 sin el permiso de camara`() = runBlocking<Unit> {
        val v10 = sistema.consentimiento.textoVigente().version
        sistema.consentimiento.otorgar(ana, v10, mapOf("uso_datos" to true), null)
        val s = sesion(ana)
        assertEquals(3, medir(ana, s))
        publicarV11()

        aceptarSinClaveCamara(ana, "v1.1")

        assertFalse(sistema.consentimiento.permite(ana))
        assertEquals(0L, metricasDe(s))
        assertEquals(0L, prediccionesDe(s))
    }

    @Test
    fun `retirar el consentimiento dos veces vuelve a borrar aunque responda 404`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        val s = sesion(ana)
        medir(ana, s)
        sistema.consentimiento.revocar(ana)
        // Un lote que entró por otra vía después del retiro (por ejemplo, una carrera) también lo borra un reintento.
        transaction {
            TablaMetricaVideo.insert {
                it[metricaId] = UUID.randomUUID(); it[sesionId] = s; it[timestampMs] = 99_000
            }
        }

        fallaCon<ErrorNoEncontrado>("consentimiento_no_encontrado") { sistema.consentimiento.revocar(ana) }

        assertEquals(0L, metricasDe(s))
    }

    private fun llamada(idLocal: String) = SolicitudRegistrarLlamada(
        idLocal = idLocal, fechaInicioMs = 1_700_000_000_000, duracionSeg = 60, preguntasCnt = 2,
        resumen = ResumenLlamadaApp(acciones = listOf(AccionLlamada("head_nodding", 2, 1_000, 600)))
    )

    private fun resumenGuardado(idLocal: String) = transaction {
        TablaLlamada.selectAll().where { TablaLlamada.idLocal eq idLocal }.single()[TablaLlamada.resumen]
    }

    @Test
    fun `sin el permiso de camara la llamada en vivo se guarda sin lo medido`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = false)

        val registrada = sistema.llamadas.registrar(ana, llamada("sin-permiso")).llamada

        assertTrue(registrada.resumen.acciones.isEmpty())
        assertNull(registrada.resumen.feedback)
        assertTrue(resumenGuardado("sin-permiso").acciones.isEmpty())
        assertEquals(60, registrada.duracionSeg, "la duración y las preguntas sí se guardan")
    }

    @Test
    fun `retirar el permiso vacia lo medido de las llamadas y del reporte`() = runBlocking<Unit> {
        publicarV11()
        aceptar(ana, "v1.1", camara = true)
        sistema.llamadas.registrar(ana, llamada("con-permiso"))
        assertEquals(1, resumenGuardado("con-permiso").acciones.size)
        val s = sesion(ana)
        transaction {
            TablaReporteEntrevista.insert {
                it[reporteId] = UUID.randomUUID(); it[sesionId] = s; it[fechaGeneracion] = Instant.now()
                it[puntajeGlobal] = BigDecimal("75.00"); it[puntajeLenguajeCorporal] = BigDecimal("82.00")
                it[fortalezas] = listOf("Dominio de SQL", FRASE_FORTALEZA_CORPORAL)
                it[areasMejora] = listOf("Reforzar Kotlin", FRASE_MEJORA_CORPORAL)
            }
        }

        aceptar(ana, "v1.1", camara = false)

        assertTrue(resumenGuardado("con-permiso").acciones.isEmpty())
        val reporte = transaction { TablaReporteEntrevista.selectAll().where { TablaReporteEntrevista.sesionId eq s }.single() }
        assertNull(reporte[TablaReporteEntrevista.puntajeLenguajeCorporal])
        assertEquals(listOf("Dominio de SQL"), reporte[TablaReporteEntrevista.fortalezas])
        assertEquals(listOf("Reforzar Kotlin"), reporte[TablaReporteEntrevista.areasMejora])
        assertEquals(BigDecimal("75.00"), reporte[TablaReporteEntrevista.puntajeGlobal], "como en el borrado biométrico, el global se queda")
    }
}
