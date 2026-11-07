package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.EstadoReporte
import MODELOS.ModoEvaluacion
import MODELOS.ResultadoReporte
import MODELOS.TipoPregunta
import MODELOS.UsoLlm
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
import SERVICIOS.RespuestaUsuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val INICIO_MES: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val FIN_MES: Instant = Instant.parse("2026-02-01T00:00:00Z")
private val A_MEDIO_MES: Instant = Instant.parse("2026-01-15T12:00:00Z")

/** Reserva y conteo del tope mensual de IA (D17) contra la BD real de prueba (H2), incluida la concurrencia. */
class PruebaRepositorioReporte {
    private val sistema = SistemaPrueba().apply { sembrarBancoGeneral(tecnicas = 10, blandas = 6) }
    private val repo = sistema.reportesRepo
    private val ana: UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
    }

    /** Una entrevista finalizada de [usuario] con su reporte recién tomado para generar ('generando'). */
    private fun reporteGenerando(usuario: UUID = ana): UUID = runBlocking {
        val sesion = sistema.entrevista.iniciar(usuario, PedidoEntrevista(nombreCargo = "QA"))
        val p = sesion.preguntas.first { it.tipo == TipoPregunta.OPCION_MULTIPLE }
        sistema.entrevista.responderYFinalizar(usuario, sesion.id, listOf(RespuestaUsuario(p.id, opcionId = p.opcionCorrecta!!.id)))
        repo.crearPendiente(sesion.id, A_MEDIO_MES)
        assertTrue(repo.tomarParaGenerar(sesion.id, 3, A_MEDIO_MES))
        sesion.id
    }

    private suspend fun reservar(sesionId: UUID, tope: Int, fecha: Instant = A_MEDIO_MES, usuario: UUID = ana) =
        repo.reservarEvaluacionIa(sesionId, usuario, tope, INICIO_MES, FIN_MES, fecha.minusSeconds(600), fecha)

    private fun resultado(modo: ModoEvaluacion, fecha: Instant) = ResultadoReporte(
        puntajeGlobal = BigDecimal(80), puntajeTecnico = BigDecimal(80), puntajeBlando = BigDecimal(80), puntajeLenguajeCorporal = BigDecimal.ZERO,
        fortalezas = emptyList(), areasMejora = emptyList(), recomendaciones = emptyList(), resumen = "ok",
        modo = modo, uso = if (modo == ModoEvaluacion.IA) UsoLlm("gpt-4o-mini", 10, 5, 0.0001) else null,
        detalles = emptyList(), evaluaciones = emptyList(), fecha = fecha
    )

    @Test
    fun `reservar devuelve true hasta el tope y false despues`() = runBlocking<Unit> {
        val reportes = List(3) { reporteGenerando() }
        assertEquals(listOf(true, true, false), reportes.map { reservar(it, tope = 2) })
        assertEquals(ModoEvaluacion.FREEMIUM, repo.buscarPorSesion(reportes[2])!!.modo, "el que no alcanzó cupo no queda marcado")
    }

    @Test
    fun `la propia sesion no cuenta y reservar dos veces no consume dos cupos`() = runBlocking<Unit> {
        val sesion = reporteGenerando()
        assertTrue(reservar(sesion, tope = 1))
        assertTrue(reservar(sesion, tope = 1), "es la misma reserva")
        assertFalse(reservar(reporteGenerando(), tope = 1), "otra sesión sí choca con el tope")
    }

    @Test
    fun `no reserva un reporte que no esta generando`() = runBlocking<Unit> {
        val listo = reporteGenerando()
        repo.guardarResultado(listo, resultado(ModoEvaluacion.FREEMIUM, A_MEDIO_MES))
        assertFalse(reservar(listo, tope = 5))

        val conError = reporteGenerando()
        repo.marcarError(conError, "error_interno", A_MEDIO_MES)
        assertFalse(reservar(conError, tope = 5))
    }

    @Test
    fun `solo cuentan los reportes listos con IA dentro del mes, cerrado al inicio y abierto al final`() = runBlocking<Unit> {
        repo.guardarResultado(reporteGenerando(), resultado(ModoEvaluacion.IA, INICIO_MES))
        repo.guardarResultado(reporteGenerando(), resultado(ModoEvaluacion.IA, FIN_MES))
        repo.guardarResultado(reporteGenerando(), resultado(ModoEvaluacion.FREEMIUM, A_MEDIO_MES))
        assertTrue(reservar(reporteGenerando(), tope = 5), "una reserva en curso no es una evaluación entregada")

        assertEquals(1, repo.contarEvaluacionesIa(ana, INICIO_MES, FIN_MES), "solo el del 1 de enero: el de febrero, el freemium y la reserva no")

        val otro = reporteGenerando()
        repo.guardarResultado(otro, resultado(ModoEvaluacion.IA, A_MEDIO_MES))
        assertEquals(EstadoReporte.LISTO, repo.buscarPorSesion(otro)!!.estado)
        assertEquals(2, repo.contarEvaluacionesIa(ana, INICIO_MES, FIN_MES))
    }

    @Test
    fun `marcarError devuelve el cupo reservado`() = runBlocking<Unit> {
        val sesion = reporteGenerando()
        assertTrue(reservar(sesion, tope = 1))
        repo.marcarError(sesion, "error_interno", A_MEDIO_MES)

        assertEquals(ModoEvaluacion.FREEMIUM, repo.buscarPorSesion(sesion)!!.modo)
        assertTrue(reservar(reporteGenerando(), tope = 1), "el cupo quedó libre para otra entrevista")
    }

    @Test
    fun `el tope es por usuario`() = runBlocking<Unit> {
        val beto = runBlocking {
            sistema.usuario.registrar(SolicitudRegistro("beto@ejemplo.com", "Clave-segura-1"))
            sistema.usuarios.buscarPorCorreo("beto@ejemplo.com")!!.id
        }
        assertTrue(reservar(reporteGenerando(), tope = 1))
        assertTrue(reservar(reporteGenerando(beto), tope = 1, usuario = beto))
    }

    @Test
    fun `reservas simultaneas respetan el tope`() = runBlocking<Unit> {
        val reportes = List(10) { reporteGenerando() }
        val exitos = reportes.map { sesion ->
            async(Dispatchers.IO) { runCatching { reservar(sesion, tope = 3) }.getOrDefault(false) }
        }.awaitAll().count { it }

        assertEquals(3, exitos, "ni más reservas que el tope ni menos por errores de bloqueo")
        assertEquals(3, reportes.count { repo.buscarPorSesion(it)!!.modo == ModoEvaluacion.IA })
    }

    // ---------- Apagado ordenado (D20) ----------

    @Test
    fun `tomarParaGenerar fecha el inicio del intento`() = runBlocking<Unit> {
        val sesion = reporteGenerando()
        repo.marcarError(sesion, "error_interno", INICIO_MES)
        val ahora = Instant.parse("2026-01-20T08:00:00Z")
        assertTrue(repo.tomarParaGenerar(sesion, 3, ahora))
        assertEquals(ahora, repo.buscarPorSesion(sesion)!!.fecha, "el rescate sabe que este intento recién empezó")
    }

    @Test
    fun `marcarInterrumpido solo actua sobre reportes en generacion y nunca deja intentos negativos`() = runBlocking<Unit> {
        val listo = reporteGenerando()
        repo.guardarResultado(listo, resultado(ModoEvaluacion.IA, A_MEDIO_MES))
        repo.marcarInterrumpido(listo, A_MEDIO_MES)
        assertEquals(EstadoReporte.LISTO, repo.buscarPorSesion(listo)!!.estado, "un reporte listo no se toca")

        val sesion = runBlocking {
            val s = sistema.entrevista.iniciar(ana, PedidoEntrevista(nombreCargo = "QA"))
            val p = s.preguntas.first { it.tipo == TipoPregunta.OPCION_MULTIPLE }
            sistema.entrevista.responderYFinalizar(ana, s.id, listOf(RespuestaUsuario(p.id, opcionId = p.opcionCorrecta!!.id))).id
        }
        repo.crearPendiente(sesion, A_MEDIO_MES) // generando, sin intentos todavía
        repo.marcarInterrumpido(sesion, A_MEDIO_MES)
        val interrumpido = repo.buscarPorSesion(sesion)!!
        assertEquals(EstadoReporte.ERROR, interrumpido.estado)
        assertEquals(0, interrumpido.intentos)
    }

    @Test
    fun `una generacion abandonada no pisa al reintento que vino despues`() = runBlocking<Unit> {
        val sesion = reporteGenerando()
        repo.marcarError(sesion, "error_interno", A_MEDIO_MES)
        val viejo = UUID.randomUUID()
        assertTrue(repo.tomarParaGenerar(sesion, 3, A_MEDIO_MES, viejo))
        repo.marcarInterrumpido(sesion, A_MEDIO_MES) // el rescate la dio por abandonada
        val nuevo = UUID.randomUUID()
        assertTrue(repo.tomarParaGenerar(sesion, 3, A_MEDIO_MES, nuevo)) // el usuario reintentó

        assertFalse(repo.guardarResultado(sesion, resultado(ModoEvaluacion.IA, A_MEDIO_MES), viejo), "la vieja despierta: no guarda")
        assertEquals(EstadoReporte.GENERANDO, repo.buscarPorSesion(sesion)!!.estado)
        assertTrue(repo.guardarResultado(sesion, resultado(ModoEvaluacion.FREEMIUM, A_MEDIO_MES), nuevo))

        repo.marcarError(sesion, "error_interno", A_MEDIO_MES, viejo) // la vieja falla después
        repo.marcarInterrumpido(sesion, A_MEDIO_MES, viejo)
        val reporte = repo.buscarPorSesion(sesion)!!
        assertEquals(EstadoReporte.LISTO, reporte.estado, "el resultado del reintento queda intacto")
        assertEquals(ModoEvaluacion.FREEMIUM, reporte.modo)
    }

    @Test
    fun `guardarResultado no pisa un reporte que ya no esta en generacion`() = runBlocking<Unit> {
        val sesion = reporteGenerando()
        repo.marcarInterrumpido(sesion, A_MEDIO_MES) // el rescate lo dio por abandonado
        assertFalse(repo.guardarResultado(sesion, resultado(ModoEvaluacion.IA, A_MEDIO_MES)), "una generación zombi no guarda")
        assertEquals(EstadoReporte.ERROR, repo.buscarPorSesion(sesion)!!.estado)
    }
}
