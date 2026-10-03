package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.EstadoReporte
import MODELOS.MetricaVideo
import MODELOS.ModoEvaluacion
import MODELOS.NivelExperiencia
import MODELOS.ReporteEntrevista
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.ProveedorEvaluacionFalso
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.sembrarPregunta
import SERVICIOS.EvaluadorEntrevista
import SERVICIOS.EvaluadorEntrevistaIa
import SERVICIOS.PedidoEntrevista
import SERVICIOS.RespuestaUsuario
import SERVICIOS.ServicioReporteEntrevista
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioReporte {
    private val sistema = SistemaPrueba()
    private val ana: UUID = nuevoUsuario("ana@ejemplo.com")
    private val catalogo = sistema.crearCatalogo("Android Developer", "Kotlin")
    private val kotlin = catalogo.second
    private val ideal = "Una corrutina se suspende sin bloquear el hilo y se reanuda después."

    init {
        runBlocking { sistema.mercadoRepo.vincularSkill(catalogo.first, kotlin, NivelExperiencia.SEMISENIOR, 60, true) }
        // 5 técnicas de Kotlin (3 alternativas + 2 abiertas) y 3 blandas generales.
        repeat(3) { sistema.sembrarPregunta(skillId = kotlin) }
        repeat(2) {
            runBlocking {
                sistema.preguntas.crear(
                    ContenidoPregunta(
                        kotlin, null, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.TECNICA, NivelExperiencia.JUNIOR, "¿Qué es una corrutina? $it", ideal,
                        Json.parseToJsonElement("""{"criterios":["x"],"palabras_clave":["corrutina","hilo"]}""").jsonObject, emptyList()
                    ),
                    EstadoPregunta.APROBADA
                )
            }
        }
        repeat(3) { sistema.sembrarPregunta(CategoriaHabilidad.BLANDA) }
    }

    private fun nuevoUsuario(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    /** Entrevista finalizada: acierta las alternativas, responde bien las técnicas abiertas y [textoBlandas] en las blandas. */
    private fun entrevistaFinalizada(
        usuario: UUID = ana,
        aciertaAlternativas: Boolean = true,
        textoBlandas: String? = "no sé",
        textoTecnicas: String = ideal
    ): SesionEntrevista = runBlocking {
        val sesion = sistema.entrevista.iniciar(usuario, PedidoEntrevista(nombreCargo = "Android Developer", nivel = "jr"))
        val respuestas = sesion.preguntas.mapNotNull { p ->
            when {
                p.tipo == TipoPregunta.OPCION_MULTIPLE ->
                    RespuestaUsuario(p.id, opcionId = if (aciertaAlternativas) p.opcionCorrecta!!.id else p.opciones.first { !it.esCorrecta }.id)
                p.categoria == CategoriaHabilidad.TECNICA -> RespuestaUsuario(p.id, texto = textoTecnicas)
                else -> textoBlandas?.let { RespuestaUsuario(p.id, texto = it) }
            }
        }
        sistema.entrevista.responderYFinalizar(usuario, sesion.id, respuestas)
    }

    private fun generar(sesion: SesionEntrevista, servicio: ServicioReporteEntrevista = sistema.reporte): ReporteEntrevista = runBlocking {
        servicio.procesar(sesion)
        sistema.reportesRepo.buscarPorSesion(sesion.id)!!
    }

    private fun servicioCon(evaluadorIa: EvaluadorEntrevista?) = ServicioReporteEntrevista(
        sistema.reportesRepo, sistema.sesionesEntrevista, sistema.metricasVideo, sistema.preguntas, sistema.mercadoRepo, sistema.nivelesSkill,
        sistema.evaluadorEntrevistaGratis, evaluadorIa, sistema.premium, CoroutineScope(Dispatchers.IO), sistema.reloj
    )

    private fun esperarFin(sesionId: UUID): ReporteEntrevista = runBlocking {
        withTimeout(10_000) {
            while (true) {
                val reporte = sistema.reportesRepo.buscarPorSesion(sesionId)!!
                if (reporte.estado != EstadoReporte.GENERANDO) return@withTimeout reporte
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE") error("inalcanzable")
        }
    }

    // ---------- Freemium ----------

    @Test
    fun `reporte freemium con puntajes por parte, radar, fortalezas y mejoras`() {
        val reporte = generar(entrevistaFinalizada())

        assertEquals(EstadoReporte.LISTO, reporte.estado)
        assertEquals(ModoEvaluacion.FREEMIUM, reporte.modo)
        assertNull(reporte.uso)
        assertTrue(reporte.puntajeTecnico.toDouble() > 90, "alternativas correctas y abiertas iguales a la ideal: ${reporte.puntajeTecnico}")
        assertTrue(reporte.puntajeBlando.toDouble() < 30, "\"no sé\" en las blandas: ${reporte.puntajeBlando}")
        assertEquals(0, BigDecimal.ZERO.compareTo(reporte.puntajeLenguajeCorporal), "sin métricas de video")
        val esperado = (reporte.puntajeTecnico.toDouble() * 0.5 + reporte.puntajeBlando.toDouble() * 0.3) / 0.8
        assertEquals(esperado, reporte.puntajeGlobal.toDouble(), 0.02, "sin video el peso se reparte entre técnico y blando")

        val radar = reporte.detalles.associateBy { it.nombre }
        assertEquals(setOf("Kotlin", "Habilidades blandas"), radar.keys)
        assertEquals(5, radar.getValue("Kotlin").preguntasRespondidas)
        assertEquals(NivelExperiencia.JUNIOR, radar.getValue("Kotlin").nivelEvaluado)
        assertNull(radar.getValue("Habilidades blandas").nivelEvaluado)
        assertTrue("Dominio de Kotlin" in reporte.fortalezas)
        assertTrue("Reforzar Habilidades blandas" in reporte.areasMejora)
        assertTrue(reporte.recomendaciones.isEmpty(), "Kotlin está bien")
        assertTrue(reporte.resumen!!.contains("Android Developer"))
    }

    @Test
    fun `guarda la correccion de cada respuesta abierta y acumula el puntaje de la skill`() = runBlocking<Unit> {
        val sesion = entrevistaFinalizada()
        generar(sesion)
        val preguntas = sistema.sesionesEntrevista.buscar(sesion.id)!!.preguntas
        val abiertas = preguntas.filter { it.tipo == TipoPregunta.ABIERTA_TEXTO }
        assertTrue(abiertas.all { it.feedback != null && it.puntaje != null })
        assertTrue(abiertas.filter { it.categoria == CategoriaHabilidad.BLANDA }.all { it.puntaje!!.toDouble() < 30 })

        val nivelKotlin = sistema.nivelesSkill.listar(ana).single { it.skillId == kotlin }
        assertEquals(1, nivelKotlin.evaluaciones)
        assertNull(nivelKotlin.nivel, "la entrevista suma puntaje pero no fija el nivel")
    }

    @Test
    fun `una skill obligatoria floja es recomendacion de prioridad alta`() {
        val reporte = generar(entrevistaFinalizada(aciertaAlternativas = false, textoTecnicas = "no sé"))
        val recomendacion = reporte.recomendaciones.single()
        assertEquals("Kotlin", recomendacion.nombre)
        assertEquals("alta", recomendacion.prioridad)
        assertEquals("semisenior", recomendacion.nivelRequerido)
        assertTrue("Reforzar Kotlin" in reporte.areasMejora)
    }

    @Test
    fun `las preguntas sin responder cuentan cero y se avisa`() {
        val reporte = generar(entrevistaFinalizada(textoBlandas = null))
        assertEquals(0, BigDecimal.ZERO.compareTo(reporte.puntajeBlando))
        assertTrue(reporte.areasMejora.any { it.startsWith("Respondiste 5 de 8") }, reporte.areasMejora.toString())
    }

    @Test
    fun `las metricas de video dan el puntaje corporal y entran al global`() = runBlocking<Unit> {
        val sesion = sistema.entrevista.iniciar(ana, PedidoEntrevista(nombreCargo = "Android Developer", nivel = "jr"))
        val metrica = { ms: Long, expresion: String -> MetricaVideo(ms, BigDecimal("40"), BigDecimal("50"), BigDecimal("60"), null, expresion) }
        sistema.entrevista.registrarMetricas(ana, sesion.id, listOf(metrica(0, "nervioso"), metrica(500, "nervioso"), metrica(1000, "seguro")))
        val p = sesion.preguntas.first { it.tipo == TipoPregunta.OPCION_MULTIPLE }
        val finalizada = sistema.entrevista.responderYFinalizar(ana, sesion.id, listOf(RespuestaUsuario(p.id, opcionId = p.opcionCorrecta!!.id)))

        val reporte = generar(finalizada)

        assertEquals(0, BigDecimal(50).compareTo(reporte.puntajeLenguajeCorporal))
        val esperado = reporte.puntajeTecnico.toDouble() * 0.5 + reporte.puntajeBlando.toDouble() * 0.3 + 50 * 0.2
        assertEquals(esperado, reporte.puntajeGlobal.toDouble(), 0.02)
        assertTrue(reporte.areasMejora.any { it.contains("contacto visual") })
        assertTrue(reporte.areasMejora.any { it.contains("nervioso") })
    }

    // ---------- IA (premium) ----------

    @Test
    fun `un usuario premium recibe la evaluacion de la IA con su costo registrado`() {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val reporte = generar(entrevistaFinalizada(), servicioCon(EvaluadorEntrevistaIa(proveedor, "gpt-4o-mini", sistema.evaluadorEntrevistaGratis)))

        assertEquals(ModoEvaluacion.IA, reporte.modo)
        assertEquals("Resumen de la IA.", reporte.resumen)
        assertTrue("Comunicación clara" in reporte.fortalezas)
        assertEquals(0, BigDecimal(90).compareTo(reporte.puntajeBlando), "las blandas las puntuó la IA")
        val uso = assertNotNull(reporte.uso)
        assertEquals(1000, uso.tokensEntrada)
        assertTrue(uso.costoUsd > 0)
        // Las respuestas del candidato van como datos en el mensaje de usuario, nunca en las instrucciones.
        val solicitud = proveedor.solicitudes.single()
        assertTrue(solicitud.instruccionUsuario.contains("no sé") && !solicitud.prompt.contains("no sé"))
    }

    @Test
    fun `si la IA falla o responde mal se usa el motor freemium y el reporte igual queda listo`() {
        sistema.premium.esPremium = true
        listOf("caido", "json_roto", "ids_incorrectos").forEach { modo ->
            val proveedor = ProveedorEvaluacionFalso(modo)
            val reporte = generar(entrevistaFinalizada(usuario = nuevoUsuario("$modo@ejemplo.com")), servicioCon(EvaluadorEntrevistaIa(proveedor, "gpt-4o-mini", sistema.evaluadorEntrevistaGratis)))
            assertEquals(EstadoReporte.LISTO, reporte.estado, modo)
            assertEquals(ModoEvaluacion.FREEMIUM, reporte.modo, modo)
            assertEquals(1, proveedor.solicitudes.size, modo)
        }
    }

    @Test
    fun `sin premium nunca se llama al LLM`() {
        val proveedor = ProveedorEvaluacionFalso()
        val reporte = generar(entrevistaFinalizada(), servicioCon(EvaluadorEntrevistaIa(proveedor, "gpt-4o-mini", sistema.evaluadorEntrevistaGratis)))
        assertEquals(ModoEvaluacion.FREEMIUM, reporte.modo)
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    @Test
    fun `procesar dos veces la misma entrevista genera una sola vez`() = runBlocking<Unit> {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val servicio = servicioCon(EvaluadorEntrevistaIa(proveedor, "gpt-4o-mini", sistema.evaluadorEntrevistaGratis))
        val sesion = entrevistaFinalizada()
        servicio.procesar(sesion)
        servicio.procesar(sesion)
        assertEquals(1, proveedor.solicitudes.size, "no paga dos veces la misma evaluación")
        assertEquals(1, sistema.reportesRepo.buscarPorSesion(sesion.id)!!.intentos)
    }

    // ---------- Errores y reintentos ----------

    @Test
    fun `un error deja el reporte en error con un codigo y se puede reintentar hasta 3 veces`() = runBlocking<Unit> {
        var falla = true
        val evaluador = EvaluadorEntrevista { contexto ->
            if (falla) error("se cayó la BD a mitad del reporte") else sistema.evaluadorEntrevistaGratis.evaluar(contexto)
        }
        sistema.premium.esPremium = true
        val servicio = servicioCon(evaluador)
        val sesion = entrevistaFinalizada()

        val conError = generar(sesion, servicio)
        assertEquals(EstadoReporte.ERROR, conError.estado)
        assertEquals("error_interno", conError.errorDetalle, "nunca el mensaje interno de la excepción")

        servicio.reintentar(ana, sesion.id)
        assertEquals(EstadoReporte.ERROR, esperarFin(sesion.id).estado)
        falla = false
        servicio.reintentar(ana, sesion.id)
        val listo = esperarFin(sesion.id)
        assertEquals(EstadoReporte.LISTO, listo.estado)
        assertEquals(3, listo.intentos)
        fallaCon<ErrorConflicto>("reporte_no_reintentable") { servicio.reintentar(ana, sesion.id) }
    }

    @Test
    fun `con los intentos agotados no se puede reintentar`() = runBlocking<Unit> {
        val servicio = servicioCon(EvaluadorEntrevista { error("siempre falla") })
        sistema.premium.esPremium = true
        val sesion = entrevistaFinalizada()
        generar(sesion, servicio)
        repeat(2) { servicio.reintentar(ana, sesion.id); esperarFin(sesion.id) }
        fallaCon<ErrorConflicto>("reintentos_agotados") { servicio.reintentar(ana, sesion.id) }
    }

    @Test
    fun `solo hay reporte de entrevistas propias y finalizadas`() = runBlocking<Unit> {
        val enCurso = sistema.entrevista.iniciar(ana, PedidoEntrevista(nombreCargo = "Android Developer", nivel = "jr"))
        fallaCon<ErrorConflicto>("entrevista_no_finalizada") { sistema.reporte.obtener(ana, enCurso.id) }
        sistema.entrevista.cancelar(ana, enCurso.id)
        fallaCon<ErrorNoEncontrado>("reporte_no_disponible") { sistema.reporte.obtener(ana, enCurso.id) }

        val finalizada = entrevistaFinalizada()
        assertNull(sistema.reporte.obtener(ana, finalizada.id).second, "aún no se procesa: se informa como generando")
        fallaCon<ErrorNoEncontrado>("entrevista_no_encontrada") { sistema.reporte.obtener(nuevoUsuario("beto@ejemplo.com"), finalizada.id) }
    }

    // ---------- Historial y progreso ----------

    @Test
    fun `historial de reportes y progreso de cada skill entre entrevistas`() = runBlocking<Unit> {
        generar(entrevistaFinalizada(aciertaAlternativas = false, textoTecnicas = "no sé"))
        sistema.reloj.adelantar(java.time.Duration.ofDays(1))
        generar(entrevistaFinalizada())

        assertEquals(2, sistema.reporte.historial(ana).size)
        val kotlinProgreso = sistema.reporte.progreso(ana).single { it.nombre == "Kotlin" }
        assertEquals(2, kotlinProgreso.historial.size)
        assertTrue(kotlinProgreso.historial[0].puntaje < kotlinProgreso.historial[1].puntaje, "mejoró de la primera a la segunda")
        assertEquals(2, kotlinProgreso.nivel.evaluaciones)
    }
}
