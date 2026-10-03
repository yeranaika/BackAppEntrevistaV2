package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.EstadoSesionEntrevista
import MODELOS.MetricaVideo
import MODELOS.NivelExperiencia
import MODELOS.NuevaSkill
import MODELOS.SesionEntrevista
import MODELOS.TablaMetricaVideo
import MODELOS.TablaPregunta
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.sembrarBancoGeneral
import PRUEBAS.DOBLES.sembrarPregunta
import SERVICIOS.PedidoEntrevista
import SERVICIOS.RespuestaUsuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioEntrevista {
    private val sistema = SistemaPrueba()
    private val servicio = sistema.entrevista
    private val ana = nuevoUsuario("ana@ejemplo.com")

    private fun nuevoUsuario(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    private fun iniciar(usuario: UUID = ana, pedido: PedidoEntrevista = PedidoEntrevista(nombreCargo = "Backend Developer")) =
        runBlocking { servicio.iniciar(usuario, pedido) }

    private fun SesionEntrevista.opcionMultiple() = preguntas.first { it.tipo == TipoPregunta.OPCION_MULTIPLE }
    private fun SesionEntrevista.abierta() = preguntas.first { it.tipo == TipoPregunta.ABIERTA_TEXTO }

    // ---------- Iniciar ----------

    @Test
    fun `inicia con 8 preguntas, primero tecnicas y luego blandas, usando el objetivo del onboarding`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 6, blandas = 4)
        sistema.objetivos.reemplazarActivo(ana, "Data Engineer", "data")

        val sesion = servicio.iniciar(ana, PedidoEntrevista())

        assertEquals("Data Engineer", sesion.cargoObjetivo)
        assertEquals(NivelExperiencia.JUNIOR, sesion.nivel, "sin nivel en el perfil se usa junior")
        assertEquals(EstadoSesionEntrevista.EN_PROGRESO, sesion.estado)
        assertEquals(8, sesion.preguntas.size)
        assertEquals(List(5) { CategoriaHabilidad.TECNICA } + List(3) { CategoriaHabilidad.BLANDA }, sesion.preguntas.map { it.categoria })
        assertEquals((1..8).toList(), sesion.preguntas.map { it.orden })
    }

    @Test
    fun `sin cargo ni objetivo pide el cargo y valida nivel y cantidad`() {
        fallaCon<ErrorValidacion>("cargo_requerido") { servicio.iniciar(ana, PedidoEntrevista()) }
        fallaCon<ErrorValidacion>("nivel_invalido") { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA", nivel = "experto")) }
        fallaCon<ErrorValidacion>("cantidad_invalida") { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA", cantidadPreguntas = 2)) }
        fallaCon<ErrorValidacion>("cantidad_invalida") { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA", cantidadPreguntas = 16)) }
        fallaCon<ErrorValidacion>("cargo_id_invalido") { servicio.iniciar(ana, PedidoEntrevista(cargoId = "abc")) }
        fallaCon<ErrorNoEncontrado>("cargo_no_encontrado") { servicio.iniciar(ana, PedidoEntrevista(cargoId = UUID.randomUUID().toString())) }
    }

    @Test
    fun `prefiere preguntas del cargo y de sus skills y nunca usa las de otro cargo`() = runBlocking<Unit> {
        val (cargoId, _) = sistema.crearCatalogo("Android Developer", "Kotlin")
        val (otroCargo, _) = sistema.crearCatalogo("Data Engineer", "Spark")
        val compose = sistema.mercadoRepo.obtenerOCrearSkill(NuevaSkill("Jetpack Compose", "tecnica", "mobile", null, 50))
        val sql = sistema.mercadoRepo.obtenerOCrearSkill(NuevaSkill("SQL", "tecnica", "data", null, 50))
        sistema.mercadoRepo.vincularSkill(cargoId, compose, NivelExperiencia.JUNIOR, 30, true)

        val delCargo = List(2) { sistema.sembrarPregunta(cargoId = cargoId) }
        val deSkillDelCargo = sistema.sembrarPregunta(skillId = compose)
        val generales = List(3) { sistema.sembrarPregunta() }
        val ajenas = listOf(sistema.sembrarPregunta(cargoId = otroCargo), sistema.sembrarPregunta(skillId = sql))
        repeat(3) { sistema.sembrarPregunta(CategoriaHabilidad.BLANDA) }

        // Sin nivel en el perfil se usaría el nivel base del cargo (semisenior): se pide junior explícito.
        val ids = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "android developer", nivel = "jr")).preguntas.map { it.preguntaId }

        assertTrue(ids.containsAll(delCargo.map { it.id } + deSkillDelCargo.id), "las del cargo y su skill van primero")
        assertTrue(ids.none { it in ajenas.map { a -> a.id } }, "nunca preguntas de otro cargo ni de skills ajenas")
        assertEquals(2, ids.count { it in generales.map { g -> g.id } }, "completa las 5 técnicas con generales")
    }

    @Test
    fun `un cargo fuera del catalogo usa tecnicas de cualquier skill pero nunca de otro cargo`() = runBlocking<Unit> {
        val (otroCargo, kotlin) = sistema.crearCatalogo("Android Developer", "Kotlin")
        val deSkill = List(5) { sistema.sembrarPregunta(skillId = kotlin) }
        val deOtroCargo = sistema.sembrarPregunta(cargoId = otroCargo)
        repeat(3) { sistema.sembrarPregunta(CategoriaHabilidad.BLANDA, skillId = kotlin) }

        val sesion = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "Cargo que no existe", nivel = "jr"))

        assertNull(sesion.cargoId)
        assertEquals("Cargo que no existe", sesion.cargoObjetivo)
        assertTrue(sesion.preguntas.map { it.preguntaId }.containsAll(deSkill.map { it.id }))
        assertTrue(sesion.preguntas.none { it.preguntaId == deOtroCargo.id })
    }

    @Test
    fun `sin nivel en el perfil usa el nivel base del cargo`() = runBlocking<Unit> {
        sistema.crearCatalogo("Android Developer", "Kotlin")
        sistema.sembrarBancoGeneral(nivel = NivelExperiencia.SEMISENIOR)
        assertEquals(NivelExperiencia.SEMISENIOR, servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "Android Developer")).nivel)
    }

    @Test
    fun `respeta el nivel pedido y avisa si el banco no alcanza`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(nivel = NivelExperiencia.SENIOR)
        repeat(2) { sistema.sembrarPregunta(nivel = NivelExperiencia.JUNIOR) }
        sistema.sembrarPregunta(nivel = NivelExperiencia.SENIOR, estado = EstadoPregunta.PENDIENTE)

        val sesion = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA", nivel = "sr"))
        assertEquals(NivelExperiencia.SENIOR, sesion.nivel)
        assertEquals(8, sesion.preguntas.size, "las pendientes no se usan")
        servicio.cancelar(ana, sesion.id)

        fallaCon<ErrorConflicto>("preguntas_insuficientes") { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA", nivel = "junior")) }
    }

    @Test
    fun `si faltan blandas completa con tecnicas`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 8, blandas = 1)
        val sesion = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA"))
        assertEquals(8, sesion.preguntas.size)
        assertEquals(1, sesion.preguntas.count { it.categoria == CategoriaHabilidad.BLANDA })
    }

    @Test
    fun `evita repetir preguntas de sesiones recientes mientras el banco alcance`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val primera = iniciar()
        servicio.cancelar(ana, primera.id)
        val segunda = iniciar()
        assertTrue(primera.preguntas.map { it.preguntaId }.intersect(segunda.preguntas.map { it.preguntaId }.toSet()).isEmpty())
        servicio.cancelar(ana, segunda.id)

        // Con el banco agotado vuelve a usarlas en vez de fallar.
        assertEquals(8, iniciar().preguntas.size)
    }

    @Test
    fun `cada pregunta usada suma un uso en el banco`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val usos = transaction { TablaPregunta.selectAll().associate { it[TablaPregunta.preguntaId] to it[TablaPregunta.vecesUsada] } }
        assertTrue(sesion.preguntas.all { usos[it.preguntaId] == 1 })
    }

    // ---------- Una sola entrevista en curso ----------

    @Test
    fun `no se pueden tener dos entrevistas en curso`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val primera = iniciar()
        fallaCon<ErrorConflicto>("entrevista_en_progreso") { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA")) }

        // La app Android la reemplaza (rinde todo de una vez y no puede retomarla).
        val nueva = servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA"), reemplazarEnProgreso = true)
        assertEquals(EstadoSesionEntrevista.CANCELADA, servicio.obtener(ana, primera.id).estado)
        assertEquals(nueva.id, servicio.enProgreso(ana)!!.id)
    }

    @Test
    fun `una entrevista abandonada mas de 2 horas se cancela sola`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val abandonada = iniciar()
        sistema.reloj.adelantar(Duration.ofHours(3))

        assertNull(servicio.enProgreso(ana))
        assertEquals(EstadoSesionEntrevista.CANCELADA, servicio.obtener(ana, abandonada.id).estado)
        assertNotNull(iniciar())
    }

    @Test
    fun `inicios simultaneos crean una sola entrevista`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val exitos = (1..5).map {
            async(Dispatchers.IO) { runCatching { servicio.iniciar(ana, PedidoEntrevista(nombreCargo = "QA")) }.isSuccess }
        }.awaitAll().count { it }
        assertEquals(1, exitos)
        assertEquals(1, servicio.historial(ana, null, null).total)
    }

    // ---------- Responder ----------

    @Test
    fun `opcion multiple se corrige al instante y la abierta queda para el reporte`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val multiple = sesion.opcionMultiple()
        val correcta = multiple.opcionCorrecta!!.id

        assertEquals(0, BigDecimal(100).compareTo(servicio.responder(ana, sesion.id, RespuestaUsuario(multiple.id, opcionId = correcta)).puntaje))
        val otra = sesion.preguntas.filter { it.tipo == TipoPregunta.OPCION_MULTIPLE }[1]
        val incorrecta = otra.opciones.first { !it.esCorrecta }.id
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.responder(ana, sesion.id, RespuestaUsuario(otra.id, opcionId = incorrecta)).puntaje))

        val abierta = servicio.responder(ana, sesion.id, RespuestaUsuario(sesion.abierta().id, texto = "  Usé el método STAR...  "))
        assertEquals("Usé el método STAR...", abierta.transcripcion)
        assertNull(abierta.puntaje)
    }

    @Test
    fun `valida cada respuesta segun el tipo de pregunta`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val multiple = sesion.opcionMultiple().id
        val abierta = sesion.abierta().id

        fallaCon<ErrorValidacion>("opcion_invalida") { servicio.responder(ana, sesion.id, RespuestaUsuario(multiple, opcionId = "otra")) }
        fallaCon<ErrorValidacion>("opcion_invalida") { servicio.responder(ana, sesion.id, RespuestaUsuario(multiple, texto = "la A")) }
        fallaCon<ErrorValidacion>("respuesta_requerida") { servicio.responder(ana, sesion.id, RespuestaUsuario(abierta, texto = "   ")) }
        fallaCon<ErrorValidacion>("respuesta_muy_larga") { servicio.responder(ana, sesion.id, RespuestaUsuario(abierta, texto = "x".repeat(4001))) }
        fallaCon<ErrorValidacion>("video_url_invalida") {
            servicio.responder(ana, sesion.id, RespuestaUsuario(abierta, texto = "ok", videoClipUrl = "http://inseguro.com/clip.mp4"))
        }
        fallaCon<ErrorNoEncontrado>("pregunta_no_encontrada") { servicio.responder(ana, sesion.id, RespuestaUsuario(UUID.randomUUID(), texto = "x")) }
        fallaCon<ErrorValidacion>("pregunta_repetida") {
            servicio.responderVarias(ana, sesion.id, listOf(RespuestaUsuario(abierta, texto = "a"), RespuestaUsuario(abierta, texto = "b")))
        }
    }

    @Test
    fun `una pregunta de video se puede responder solo con el clip`() = runBlocking<Unit> {
        repeat(5) { sistema.sembrarPregunta() }
        repeat(3) { sistema.sembrarPregunta(CategoriaHabilidad.BLANDA, tipo = TipoPregunta.SIMULACION_VIDEO) }
        val sesion = iniciar()
        val video = sesion.preguntas.first { it.tipo == TipoPregunta.SIMULACION_VIDEO }

        val respondida = servicio.responder(ana, sesion.id, RespuestaUsuario(video.id, videoClipUrl = "https://storage.ejemplo.com/clip.mp4"))
        assertEquals("https://storage.ejemplo.com/clip.mp4", respondida.videoClipUrl)
    }

    @Test
    fun `una pregunta se responde una sola vez y un lote con un error no guarda nada`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val abierta = sesion.abierta().id
        servicio.responder(ana, sesion.id, RespuestaUsuario(abierta, texto = "primera"))
        fallaCon<ErrorConflicto>("pregunta_ya_respondida") { servicio.responder(ana, sesion.id, RespuestaUsuario(abierta, texto = "segunda")) }

        val multiple = sesion.opcionMultiple()
        fallaCon<ErrorValidacion>("opcion_invalida") {
            servicio.responderVarias(
                ana, sesion.id,
                listOf(RespuestaUsuario(multiple.id, opcionId = multiple.opcionCorrecta!!.id), RespuestaUsuario(sesion.preguntas[1].id, opcionId = "x"))
            )
        }
        assertEquals(1, servicio.obtener(ana, sesion.id).respondidas)
    }

    @Test
    fun `siguiente pregunta devuelve la primera sin responder`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar(pedido = PedidoEntrevista(nombreCargo = "QA", cantidadPreguntas = 3))
        assertEquals(1, servicio.siguientePregunta(ana, sesion.id)!!.orden)
        sesion.preguntas.take(2).forEach { servicio.responder(ana, sesion.id, RespuestaUsuario(it.id, opcionId = it.opcionCorrecta?.id, texto = "x")) }
        assertEquals(3, servicio.siguientePregunta(ana, sesion.id)!!.orden)
    }

    @Test
    fun `la entrevista de otro usuario no existe para mi`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val beto = nuevoUsuario("beto@ejemplo.com")
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { servicio.obtener(beto, sesion.id) }
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { servicio.responder(beto, sesion.id, RespuestaUsuario(sesion.abierta().id, texto = "x")) }
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { servicio.cancelar(beto, sesion.id) }
    }

    @Test
    fun `el snapshot no cambia si se edita o borra la pregunta del banco`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        val original = sesion.preguntas.first()
        sistema.preguntas.reemplazarContenido(
            original.preguntaId!!,
            ContenidoPregunta(null, null, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.TECNICA, NivelExperiencia.JUNIOR, "Otro enunciado", null, null, emptyList())
        )
        sistema.preguntas.eliminar(sesion.preguntas[1].preguntaId!!)

        val despues = servicio.obtener(ana, sesion.id)
        assertEquals(original.enunciado, despues.preguntas.first().enunciado)
        assertEquals(original.opciones, despues.preguntas.first().opciones)
        assertEquals(8, despues.preguntas.size)
    }

    // ---------- Finalizar y cancelar ----------

    @Test
    fun `finalizar exige al menos una respuesta, cierra la sesion y dispara el reporte`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()
        fallaCon<ErrorValidacion>("sin_respuestas") { servicio.finalizar(ana, sesion.id) }

        servicio.responder(ana, sesion.id, RespuestaUsuario(sesion.abierta().id, texto = "respuesta"))
        val finalizada = servicio.finalizar(ana, sesion.id)

        assertEquals(EstadoSesionEntrevista.FINALIZADA, finalizada.estado)
        assertNotNull(finalizada.fechaFin)
        assertEquals(listOf(sesion.id), sistema.procesadorEntrevista.procesadas.map { it.id })
        fallaCon<ErrorConflicto>("entrevista_no_activa") { servicio.finalizar(ana, sesion.id) }
        fallaCon<ErrorConflicto>("entrevista_no_activa") { servicio.responder(ana, sesion.id, RespuestaUsuario(sesion.preguntas[0].id, texto = "x")) }
        fallaCon<ErrorConflicto>("entrevista_no_activa") { servicio.cancelar(ana, sesion.id) }
    }

    @Test
    fun `si el reporte falla la entrevista igual queda finalizada`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        sistema.procesadorEntrevista.error = IllegalStateException("LLM caído")
        val sesion = iniciar()
        servicio.responder(ana, sesion.id, RespuestaUsuario(sesion.abierta().id, texto = "respuesta"))
        assertEquals(EstadoSesionEntrevista.FINALIZADA, servicio.finalizar(ana, sesion.id).estado)
    }

    @Test
    fun `cancelar deja la sesion cancelada y permite iniciar otra`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val sesion = iniciar()
        assertEquals(EstadoSesionEntrevista.CANCELADA, servicio.cancelar(ana, sesion.id).estado)
        assertNull(servicio.enProgreso(ana))
        assertNotNull(iniciar())
    }

    // ---------- Métricas de video ----------

    private fun metrica(ms: Long, contacto: String? = "80.5", expresion: String? = "seguro") =
        MetricaVideo(ms, contacto?.let(::BigDecimal), BigDecimal("70"), BigDecimal("65.25"), null, expresion)

    @Test
    fun `las metricas se guardan en lote y se validan`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral()
        val sesion = iniciar()

        assertEquals(30, servicio.registrarMetricas(ana, sesion.id, (0 until 30).map { metrica(it * 500L) }))
        assertEquals(30, transaction { TablaMetricaVideo.selectAll().count() })

        fallaCon<ErrorValidacion>("lote_invalido") { servicio.registrarMetricas(ana, sesion.id, emptyList()) }
        fallaCon<ErrorValidacion>("lote_invalido") { servicio.registrarMetricas(ana, sesion.id, (0..300).map { metrica(it.toLong()) }) }
        fallaCon<ErrorValidacion>("metrica_fuera_de_rango") { servicio.registrarMetricas(ana, sesion.id, listOf(metrica(1, contacto = "100.01"))) }
        fallaCon<ErrorValidacion>("expresion_invalida") { servicio.registrarMetricas(ana, sesion.id, listOf(metrica(1, expresion = "feliz"))) }
        fallaCon<ErrorValidacion>("metrica_invalida") { servicio.registrarMetricas(ana, sesion.id, listOf(metrica(-1))) }

        servicio.cancelar(ana, sesion.id)
        fallaCon<ErrorConflicto>("entrevista_no_activa") { servicio.registrarMetricas(ana, sesion.id, listOf(metrica(1))) }
        assertEquals(30, transaction { TablaMetricaVideo.selectAll().count() }, "los lotes rechazados no guardan nada")
    }

    // ---------- Historial ----------

    @Test
    fun `historial paginado con conteo de respuestas`() = runBlocking<Unit> {
        sistema.sembrarBancoGeneral(tecnicas = 10, blandas = 6)
        val primera = iniciar()
        servicio.responder(ana, primera.id, RespuestaUsuario(primera.abierta().id, texto = "x"))
        servicio.finalizar(ana, primera.id)
        sistema.reloj.adelantar(Duration.ofMinutes(5))
        val segunda = iniciar()

        val pagina = servicio.historial(ana, 1, 1)
        assertEquals(2, pagina.total)
        assertEquals(segunda.id, pagina.elementos.single().id, "la más reciente primero")
        assertEquals(1, servicio.historial(ana, 2, 1).elementos.single().respondidas)
        fallaCon<ErrorValidacion>("tamano_invalido") { servicio.historial(ana, 1, 101) }
        fallaCon<ErrorValidacion>("pagina_invalida") { servicio.historial(ana, 0, 10) }
    }
}
