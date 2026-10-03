package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.IntentoNivelacion
import MODELOS.NivelExperiencia
import MODELOS.NuevaSkill
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.sembrarPregunta
import SERVICIOS.CalculadoraNivel
import SERVICIOS.DatosTestNivelacion
import SERVICIOS.RespuestaPrueba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioNivelacion {
    private val sistema = SistemaPrueba()
    private val servicio = sistema.nivelacion
    private val ana: UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
    }
    private val catalogo = sistema.crearCatalogo("Android Developer", "Kotlin")
    private val cargoId = catalogo.first
    private val kotlin = catalogo.second
    private val compose: UUID = runBlocking { sistema.mercadoRepo.obtenerOCrearSkill(NuevaSkill("Jetpack Compose", "tecnica", "mobile", null, 50)) }

    init {
        runBlocking {
            sistema.mercadoRepo.vincularSkill(cargoId, kotlin, NivelExperiencia.SEMISENIOR, 60, true)
            sistema.mercadoRepo.vincularSkill(cargoId, compose, NivelExperiencia.JUNIOR, 40, false)
        }
    }

    /** 3 preguntas por nivel: Kotlin en todos, Compose solo junior. */
    private fun sembrarBanco() {
        NivelExperiencia.entries.forEach { nivel ->
            repeat(2) { sistema.sembrarPregunta(nivel = nivel, skillId = kotlin) }
            sistema.sembrarPregunta(nivel = nivel, skillId = if (nivel == NivelExperiencia.JUNIOR) compose else kotlin)
        }
    }

    private fun iniciar() = runBlocking { servicio.iniciar(ana, null, "Android Developer") }

    /** Responde bien los niveles indicados y mal el resto. */
    private fun responderHasta(intento: IntentoNivelacion, logrados: Set<NivelExperiencia>) = runBlocking {
        servicio.responder(ana, intento.id, intento.detalle.map { item ->
            val pregunta = item.pregunta
            val opcion = if (pregunta.nivelExperiencia in logrados) pregunta.opcionCorrecta!! else pregunta.opciones.first { !it.esCorrecta }
            RespuestaPrueba(pregunta.id, opcionId = opcion.id)
        })
    }

    @Test
    fun `la calculadora sube de nivel en orden y se detiene en el primer nivel no logrado`() {
        val j = NivelExperiencia.JUNIOR
        val m = NivelExperiencia.SEMISENIOR
        val s = NivelExperiencia.SENIOR
        assertEquals(m, CalculadoraNivel.nivelLogrado(mapOf(j to listOf(100.0, 60.0), m to listOf(70.0), s to listOf(10.0))))
        assertEquals(j, CalculadoraNivel.nivelLogrado(mapOf(j to listOf(0.0), m to listOf(100.0), s to listOf(100.0))), "no se salta niveles")
        assertEquals(j, CalculadoraNivel.nivelLogrado(mapOf(j to listOf(100.0), s to listOf(100.0))), "sin preguntas semisenior no llega a senior")
        assertEquals(s, CalculadoraNivel.nivelLogrado(mapOf(j to listOf(80.0), m to listOf(60.0), s to listOf(90.0))))
        assertEquals(1, CalculadoraNivel.brecha(j, m))
        assertEquals(0, CalculadoraNivel.brecha(s, m))
    }

    @Test
    fun `arma el test con 3 tecnicas por nivel, de junior a senior`() {
        sembrarBanco()
        sistema.sembrarPregunta(CategoriaHabilidad.BLANDA, skillId = kotlin)
        sistema.sembrarPregunta(nivel = NivelExperiencia.JUNIOR, skillId = kotlin, tipo = TipoPregunta.SIMULACION_VIDEO)

        val intento = iniciar()

        assertEquals(cargoId, intento.cargoId)
        assertEquals(9, intento.detalle.size)
        assertEquals(List(3) { "junior" } + List(3) { "semisenior" } + List(3) { "senior" }, intento.detalle.map { it.pregunta.nivel })
        assertTrue(intento.detalle.all { it.pregunta.categoria == "tecnica" && it.pregunta.tipo != "simulacion_video" })
        assertTrue(intento.detalle.all { it.respuesta == null })
    }

    @Test
    fun `asigna el nivel global, la brecha por skill y acumula el nivel de cada skill`() = runBlocking<Unit> {
        sembrarBanco()
        val (terminado, resultado) = responderHasta(iniciar(), setOf(NivelExperiencia.JUNIOR))

        assertNotNull(resultado)
        assertEquals(NivelExperiencia.JUNIOR, resultado.nivelGlobal)
        assertEquals(0, resultado.puntajeGlobal.compareTo(java.math.BigDecimal("33.33")))
        val brechaKotlin = resultado.skillsBrecha.single()
        assertEquals("Kotlin", brechaKotlin.nombre)
        assertEquals("junior", brechaKotlin.nivelActual)
        assertEquals("semisenior", brechaKotlin.nivelRequerido)
        assertEquals(1, brechaKotlin.brecha)
        assertEquals("alta", brechaKotlin.prioridad, "es obligatoria para el cargo")
        assertEquals(listOf("Jetpack Compose"), resultado.skillsOk.map { it.nombre })
        assertTrue(resultado.resumen!!.contains("Kotlin"))
        assertTrue(terminado.estaTerminado)

        val niveles = sistema.nivelesSkill.listar(ana).associateBy { it.skillId }
        assertEquals(NivelExperiencia.JUNIOR, niveles.getValue(kotlin).nivel)
        assertEquals(NivelExperiencia.JUNIOR, niveles.getValue(compose).nivel)
        assertEquals(resultado.intentoId, servicio.ultimoResultado(ana)!!.intentoId)
    }

    @Test
    fun `quien acierta hasta semisenior queda semisenior y sin brechas`() {
        sembrarBanco()
        val (_, resultado) = responderHasta(iniciar(), setOf(NivelExperiencia.JUNIOR, NivelExperiencia.SEMISENIOR))
        assertEquals(NivelExperiencia.SEMISENIOR, resultado!!.nivelGlobal)
        assertTrue(resultado.skillsBrecha.isEmpty())
    }

    @Test
    fun `las preguntas sin responder cuentan cero`() = runBlocking<Unit> {
        sembrarBanco()
        val intento = iniciar()
        val juniors = intento.detalle.filter { it.pregunta.nivel == "junior" }.take(1)
        val (_, resultado) = servicio.responder(ana, intento.id, juniors.map { RespuestaPrueba(it.pregunta.id, opcionId = it.pregunta.opcionCorrecta!!.id) })
        assertEquals(NivelExperiencia.JUNIOR, resultado!!.nivelGlobal)
        assertEquals(0, resultado.puntajeGlobal.compareTo(java.math.BigDecimal("11.11")))
    }

    @Test
    fun `se rinde una sola vez, tambien con dos envios simultaneos`() = runBlocking<Unit> {
        sembrarBanco()
        val intento = iniciar()
        val respuestas = intento.detalle.map { RespuestaPrueba(it.pregunta.id, opcionId = it.pregunta.opcionCorrecta!!.id) }

        val exitos = (1..4).map { async(Dispatchers.IO) { runCatching { servicio.responder(ana, intento.id, respuestas) }.isSuccess } }.awaitAll().count { it }

        assertEquals(1, exitos)
        fallaCon<ErrorConflicto>("nivelacion_finalizada") { servicio.responder(ana, intento.id, respuestas) }
        assertEquals(1, sistema.nivelesSkill.listar(ana).first { it.skillId == kotlin }.evaluaciones)
    }

    @Test
    fun `valida las respuestas y el dueño`() = runBlocking<Unit> {
        sembrarBanco()
        val intento = iniciar()
        val primera = intento.detalle.first().pregunta
        fallaCon<ErrorValidacion>("sin_respuestas") { servicio.responder(ana, intento.id, emptyList()) }
        fallaCon<ErrorNoEncontrado>("pregunta_no_encontrada") { servicio.responder(ana, intento.id, listOf(RespuestaPrueba("otra", opcionId = "x"))) }
        fallaCon<ErrorValidacion>("pregunta_repetida") {
            servicio.responder(ana, intento.id, listOf(RespuestaPrueba(primera.id, opcionId = primera.opciones[0].id), RespuestaPrueba(primera.id, opcionId = primera.opciones[1].id)))
        }
        fallaCon<ErrorValidacion>("opcion_invalida") { servicio.responder(ana, intento.id, listOf(RespuestaPrueba(primera.id, opcionId = "x"))) }
        sistema.usuario.registrar(SolicitudRegistro("beto@ejemplo.com", "Clave-segura-1"))
        val beto = sistema.usuarios.buscarPorCorreo("beto@ejemplo.com")!!.id
        fallaCon<ErrorNoEncontrado>("nivelacion_no_encontrada") { servicio.obtener(beto, intento.id) }
        assertNull(servicio.obtener(ana, intento.id).resultado)
    }

    @Test
    fun `sin banco suficiente avisa`() {
        repeat(2) { sistema.sembrarPregunta(skillId = kotlin) }
        fallaCon<ErrorConflicto>("preguntas_insuficientes") { servicio.iniciar(ana, null, "Android Developer") }
    }

    // ---------- Tests armados por un admin ----------

    @Test
    fun `si hay un test activo para el cargo se usan sus preguntas`() = runBlocking<Unit> {
        sembrarBanco()
        val elegidas = List(4) { sistema.sembrarPregunta(nivel = NivelExperiencia.SENIOR, skillId = kotlin) } +
            sistema.sembrarPregunta(nivel = NivelExperiencia.JUNIOR, skillId = kotlin)
        val test = sistema.testsNivelacion.crear(
            DatosTestNivelacion("Nivelación Android", cargoId.toString(), "mobile", null, null, elegidas.map { it.id.toString() })
        )

        val intento = iniciar()

        assertEquals(test.id, intento.testId)
        assertEquals(elegidas.map { it.id.toString() }.toSet(), intento.detalle.map { it.pregunta.preguntaId }.toSet())
        assertEquals("junior", intento.detalle.first().pregunta.nivel, "ordenadas de junior a senior")

        sistema.testsNivelacion.desactivar(test.id)
        assertNull(servicio.iniciar(ana, null, "Android Developer").testId, "sin test activo vuelve al banco")
    }

    @Test
    fun `los tests de admin solo aceptan preguntas aprobadas y escritas`() {
        sembrarBanco()
        val tres = List(3) { sistema.sembrarPregunta(skillId = kotlin).id.toString() }
        val pendiente = sistema.sembrarPregunta(skillId = kotlin, estado = MODELOS.EstadoPregunta.PENDIENTE).id.toString()
        val video = sistema.sembrarPregunta(skillId = kotlin, tipo = TipoPregunta.SIMULACION_VIDEO).id.toString()
        val tests = sistema.testsNivelacion
        fun datos(ids: List<String>, nivel: String? = null, cargo: String? = cargoId.toString()) = DatosTestNivelacion("Test", cargo, "mobile", nivel, null, ids)

        fallaCon<ErrorValidacion>("cantidad_invalida") { tests.crear(datos(tres.take(2))) }
        fallaCon<ErrorValidacion>("pregunta_repetida") { tests.crear(datos(tres + tres[0])) }
        fallaCon<ErrorValidacion>("pregunta_no_aprobada") { tests.crear(datos(tres + pendiente)) }
        fallaCon<ErrorValidacion>("pregunta_no_permitida") { tests.crear(datos(tres + video)) }
        fallaCon<ErrorNoEncontrado>("pregunta_no_encontrada") { tests.crear(datos(tres + UUID.randomUUID().toString())) }
        fallaCon<ErrorValidacion>("nivel_invalido") { tests.crear(datos(tres, nivel = "experto")) }
        fallaCon<ErrorNoEncontrado>("cargo_no_encontrado") { tests.crear(datos(tres, cargo = UUID.randomUUID().toString())) }
        fallaCon<ErrorValidacion>("titulo_invalido") { tests.crear(datos(tres).copy(titulo = " ")) }
        fallaCon<ErrorNoEncontrado>("test_no_encontrado") { tests.desactivar(UUID.randomUUID()) }
        assertEquals("mixto", runBlocking { tests.crear(datos(tres)) }.nivelObjetivo)
    }
}
