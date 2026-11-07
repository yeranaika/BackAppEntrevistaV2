package PRUEBAS

import CONFIGURACION.CODIGO_GENERACION_INTERRUMPIDA
import CONFIGURACION.EVALUACIONES_IA_MENSUALES_POR_DEFECTO
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
import UTILIDADES.TareasSegundoPlano
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val ENERO: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val FEBRERO: Instant = Instant.parse("2026-02-01T00:00:00Z")

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

    private fun servicioCon(evaluadorIa: EvaluadorEntrevista?, tope: Int = EVALUACIONES_IA_MENSUALES_POR_DEFECTO) = ServicioReporteEntrevista(
        sistema.reportesRepo, sistema.sesionesEntrevista, sistema.metricasVideo, sistema.preguntas, sistema.mercadoRepo, sistema.nivelesSkill,
        sistema.evaluadorEntrevistaGratis, evaluadorIa, sistema.premium, CoroutineScope(Dispatchers.IO), sistema.reloj,
        evaluacionesIaMensuales = tope
    )

    private fun ia(proveedor: ProveedorEvaluacionFalso) = EvaluadorEntrevistaIa(proveedor, "gpt-4o-mini", sistema.evaluadorEntrevistaGratis)

    /** Evaluaciones con IA entregadas a [usuario] en enero de 2026 (el mes en que arranca el reloj de prueba). */
    private fun usadasEnEnero(usuario: UUID = ana): Long = runBlocking { sistema.reportesRepo.contarEvaluacionesIa(usuario, ENERO, FEBRERO) }

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

    // ---------- Tope mensual de IA (D17) ----------

    @Test
    fun `con el tope mensual alcanzado el premium recibe el reporte freemium sin llamar al LLM`() {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val servicio = servicioCon(ia(proveedor), tope = 2)

        repeat(2) { assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo) }
        val tercero = generar(entrevistaFinalizada(), servicio)

        assertEquals(EstadoReporte.LISTO, tercero.estado, "el reporte sale igual, sin error")
        assertEquals(ModoEvaluacion.FREEMIUM, tercero.modo)
        assertNull(tercero.uso, "sin costo registrado")
        assertTrue(!tercero.resumen.isNullOrBlank())
        assertEquals(2, proveedor.solicitudes.size, "el LLM solo se pagó dos veces")
        assertEquals(2, usadasEnEnero())
    }

    @Test
    fun `al cambiar de mes el premium vuelve a tener evaluaciones con IA`() {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val servicio = servicioCon(ia(proveedor), tope = 1)

        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo)
        assertEquals(ModoEvaluacion.FREEMIUM, generar(entrevistaFinalizada(), servicio).modo)
        sistema.reloj.adelantar(Duration.ofDays(31))
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo, "en febrero el cupo empieza de nuevo")
        assertEquals(2, proveedor.solicitudes.size)
    }

    @Test
    fun `el tope es por usuario`() {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val servicio = servicioCon(ia(proveedor), tope = 1)
        val beto = nuevoUsuario("beto@ejemplo.com")

        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo)
        assertEquals(ModoEvaluacion.FREEMIUM, generar(entrevistaFinalizada(), servicio).modo, "ana agotó su cupo")
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(usuario = beto), servicio).modo, "beto conserva el suyo")
    }

    @Test
    fun `con tope cero nunca se llama al LLM aunque sea premium`() {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val reporte = generar(entrevistaFinalizada(), servicioCon(ia(proveedor), tope = 0))
        assertEquals(EstadoReporte.LISTO, reporte.estado)
        assertEquals(ModoEvaluacion.FREEMIUM, reporte.modo)
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    @Test
    fun `una evaluacion con IA que cae al freemium no consume cupo`() {
        val proveedor = ProveedorEvaluacionFalso("caido")
        sistema.premium.esPremium = true
        val servicio = servicioCon(ia(proveedor), tope = 1)

        assertEquals(ModoEvaluacion.FREEMIUM, generar(entrevistaFinalizada(), servicio).modo)
        assertEquals(0, usadasEnEnero(), "el usuario no recibió IA")
        proveedor.modo = "valido"
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo, "el cupo seguía disponible")
        assertEquals(1, usadasEnEnero())
    }

    @Test
    fun `un reporte en error libera su cupo y el reintento respeta el tope`() = runBlocking<Unit> {
        var falla = true
        var llamadasIa = 0
        val evaluador = EvaluadorEntrevista { contexto ->
            llamadasIa++
            if (falla) error("se cayó la BD a mitad del reporte") else sistema.evaluadorEntrevistaGratis.evaluar(contexto)
        }
        sistema.premium.esPremium = true
        val servicio = servicioCon(evaluador, tope = 1)
        val sesion = entrevistaFinalizada()

        val conError = generar(sesion, servicio)
        assertEquals(EstadoReporte.ERROR, conError.estado)
        assertEquals(1, llamadasIa, "tenía cupo: se usó la IA")
        assertEquals(ModoEvaluacion.FREEMIUM, conError.modo, "el cupo reservado se devolvió")

        // Con el cupo libre, otra entrevista sí recibe IA y lo agota.
        val proveedor = ProveedorEvaluacionFalso()
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicioCon(ia(proveedor), tope = 1)).modo)

        falla = false
        servicio.reintentar(ana, sesion.id)
        val listo = esperarFin(sesion.id)
        assertEquals(EstadoReporte.LISTO, listo.estado, "el reintento funciona aunque el tope ya esté usado")
        assertEquals(1, llamadasIa, "el reintento no volvió a llamar a la IA: el cupo del mes ya estaba agotado")
        assertEquals(ModoEvaluacion.FREEMIUM, listo.modo)
        assertEquals(1, usadasEnEnero())
    }

    @Test
    fun `una evaluacion reservada al final del mes cuenta en ese mes aunque termine en el siguiente`() {
        sistema.premium.esPremium = true
        val proveedor = ProveedorEvaluacionFalso()
        // La llamada al LLM tarda 1 minuto: la reserva es el 31 de enero y el resultado se guarda el 1 de febrero.
        val lenta = EvaluadorEntrevista { contexto -> sistema.reloj.adelantar(Duration.ofMinutes(1)); ia(proveedor).evaluar(contexto) }
        val servicio = servicioCon(lenta, tope = 1)
        sistema.reloj.adelantar(Duration.between(ENERO, FEBRERO).minusSeconds(10))

        val cruzada = generar(entrevistaFinalizada(), servicio)

        assertEquals(ModoEvaluacion.IA, cruzada.modo)
        assertTrue(cruzada.fecha < FEBRERO, "queda con la fecha de la reserva: ${cruzada.fecha}")
        assertEquals(1, usadasEnEnero())
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicioCon(ia(proveedor), tope = 1)).modo, "febrero empieza con su cupo completo")
    }

    @Test
    fun `dos reportes simultaneos del mismo usuario no superan el tope`() = runBlocking<Unit> {
        val proveedor = ProveedorEvaluacionFalso()
        sistema.premium.esPremium = true
        val servicio = servicioCon(ia(proveedor), tope = 1)
        val sesiones = listOf(entrevistaFinalizada(), entrevistaFinalizada())

        sesiones.map { async(Dispatchers.IO) { servicio.procesar(it) } }.awaitAll()

        val modos = sesiones.map { sistema.reportesRepo.buscarPorSesion(it.id)!! }.onEach { assertEquals(EstadoReporte.LISTO, it.estado) }.map { it.modo }
        assertEquals(1, modos.count { it == ModoEvaluacion.IA }, modos.toString())
        assertEquals(1, modos.count { it == ModoEvaluacion.FREEMIUM }, modos.toString())
        assertEquals(1, proveedor.solicitudes.size)
    }

    @Test
    fun `una reserva huerfana de hace mas de diez minutos no cuenta`() = runBlocking<Unit> {
        sistema.premium.esPremium = true
        val proveedor = ProveedorEvaluacionFalso()
        val servicio = servicioCon(ia(proveedor), tope = 1)
        // El servidor se reinició a mitad de la generación: el reporte quedó 'generando' con el cupo reservado.
        val huerfana = entrevistaFinalizada()
        val ahora = sistema.reloj.instant()
        sistema.reportesRepo.crearPendiente(huerfana.id, ahora)
        assertTrue(sistema.reportesRepo.tomarParaGenerar(huerfana.id, 3, ahora))
        assertTrue(sistema.reportesRepo.reservarEvaluacionIa(huerfana.id, ana, 1, ENERO, FEBRERO, ahora.minusSeconds(600), ahora))

        assertEquals(ModoEvaluacion.FREEMIUM, generar(entrevistaFinalizada(), servicio).modo, "la reserva todavía está vigente")
        sistema.reloj.adelantar(Duration.ofMinutes(11))
        assertEquals(ModoEvaluacion.IA, generar(entrevistaFinalizada(), servicio).modo, "pasados 10 minutos la reserva ya no cuenta")
    }

    // ---------- Apagado ordenado y rescate (D20) ----------

    /** Deja el reporte de [sesion] tomado para generar ahora ('generando', un intento más). */
    private fun tomado(sesion: SesionEntrevista): ReporteEntrevista = runBlocking {
        sistema.reportesRepo.crearPendiente(sesion.id, sistema.reloj.instant())
        assertTrue(sistema.reportesRepo.tomarParaGenerar(sesion.id, 3, sistema.reloj.instant()))
        sistema.reportesRepo.buscarPorSesion(sesion.id)!!
    }

    private fun reporteDe(sesion: SesionEntrevista) = runBlocking { sistema.reportesRepo.buscarPorSesion(sesion.id)!! }

    @Test
    fun `un apagado a mitad deja el reporte reintentable al instante`() = runBlocking<Unit> {
        sistema.premium.esPremium = true
        var bloquear = true
        val empezo = CompletableDeferred<Unit>()
        val lento = EvaluadorEntrevista { contexto ->
            if (bloquear) { empezo.complete(Unit); awaitCancellation() }
            sistema.evaluadorEntrevistaGratis.evaluar(contexto)
        }
        val servicio = servicioCon(lento)
        val sesion = entrevistaFinalizada()
        val tareas = TareasSegundoPlano(Dispatchers.IO)
        tareas.launch { servicio.procesar(sesion) }
        empezo.await()
        assertEquals(ModoEvaluacion.IA, reporteDe(sesion).modo, "tenía un cupo de IA reservado")

        assertEquals(1, tareas.esperarYCerrar(0.seconds), "apagado sin tiempo de espera")

        val interrumpido = reporteDe(sesion)
        assertEquals(EstadoReporte.ERROR, interrumpido.estado)
        assertEquals(CODIGO_GENERACION_INTERRUMPIDA, interrumpido.errorDetalle)
        assertEquals(ModoEvaluacion.FREEMIUM, interrumpido.modo, "el cupo de IA se devolvió")
        assertEquals(0, interrumpido.intentos, "el intento cortado no cuenta")

        bloquear = false
        servicio.reintentar(ana, sesion.id)
        assertEquals(EstadoReporte.LISTO, esperarFin(sesion.id).estado, "el usuario lo genera de nuevo")
    }

    @Test
    fun `un timeout interno sigue siendo un error normal`() = runBlocking<Unit> {
        sistema.premium.esPremium = true
        val conTimeout = EvaluadorEntrevista { withTimeout(1) { delay(1_000) }; error("inalcanzable") }
        val reporte = generar(entrevistaFinalizada(), servicioCon(conTimeout))
        assertEquals(EstadoReporte.ERROR, reporte.estado)
        assertEquals("error_interno", reporte.errorDetalle, "no se confunde con un apagado")
        assertEquals(1, reporte.intentos, "este intento sí cuenta")
    }

    @Test
    fun `el rescate deja reintentable un reporte abandonado pasados diez minutos`() = runBlocking<Unit> {
        val sesion = entrevistaFinalizada()
        tomado(sesion)
        sistema.reloj.adelantar(Duration.ofMinutes(9))
        assertEquals(0, sistema.reporte.rescatarInterrumpidos(), "a los 9 minutos todavía puede estar generándose")
        sistema.reloj.adelantar(Duration.ofMinutes(2))
        assertEquals(1, sistema.reporte.rescatarInterrumpidos())

        val rescatado = reporteDe(sesion)
        assertEquals(EstadoReporte.ERROR, rescatado.estado)
        assertEquals(CODIGO_GENERACION_INTERRUMPIDA, rescatado.errorDetalle)
        assertEquals(ModoEvaluacion.FREEMIUM, rescatado.modo)
        assertEquals(0, rescatado.intentos)
        assertEquals(0, sistema.reporte.rescatarInterrumpidos(), "una segunda pasada no hace nada")
    }

    @Test
    fun `el rescate no toca un reintento recien tomado`() = runBlocking<Unit> {
        val sesion = entrevistaFinalizada()
        tomado(sesion)
        sistema.reportesRepo.marcarError(sesion.id, "error_interno", sistema.reloj.instant())
        sistema.reloj.adelantar(Duration.ofHours(3))
        // El usuario reintenta ahora: el intento queda fechado ahora, no hace 3 horas.
        assertTrue(sistema.reportesRepo.tomarParaGenerar(sesion.id, 3, sistema.reloj.instant()))

        assertEquals(0, sistema.reporte.rescatarInterrumpidos())
        assertEquals(EstadoReporte.GENERANDO, reporteDe(sesion).estado)
    }

    @Test
    fun `un intento interrumpido no cuenta para el maximo de reintentos`() = runBlocking<Unit> {
        val sesion = entrevistaFinalizada()
        tomado(sesion)
        repeat(2) {
            sistema.reportesRepo.marcarError(sesion.id, "error_interno", sistema.reloj.instant())
            sistema.reportesRepo.tomarParaGenerar(sesion.id, 3, sistema.reloj.instant())
        }
        assertEquals(3, reporteDe(sesion).intentos, "el tercer intento se cortó por una caída")
        sistema.reloj.adelantar(Duration.ofMinutes(11))
        sistema.reporte.rescatarInterrumpidos()

        assertEquals(2, reporteDe(sesion).intentos)
        sistema.reporte.reintentar(ana, sesion.id) // no responde reintentos_agotados
        assertEquals(EstadoReporte.LISTO, esperarFin(sesion.id).estado)
    }

    @Test
    fun `el rescate crea el reporte de una entrevista finalizada que se quedo sin el`() = runBlocking<Unit> {
        val antigua = entrevistaFinalizada()
        sistema.reloj.adelantar(Duration.ofHours(25))
        val huerfana = entrevistaFinalizada()
        sistema.reloj.adelantar(Duration.ofMinutes(6))
        val reciente = entrevistaFinalizada()
        sistema.reloj.adelantar(Duration.ofMinutes(5))
        val enCurso = sistema.entrevista.iniciar(ana, PedidoEntrevista(nombreCargo = "Android Developer", nivel = "jr"))

        assertEquals(1, sistema.reporte.rescatarInterrumpidos())

        val rescatado = reporteDe(huerfana)
        assertEquals(EstadoReporte.ERROR, rescatado.estado)
        assertEquals(CODIGO_GENERACION_INTERRUMPIDA, rescatado.errorDetalle)
        assertEquals(0, rescatado.intentos)
        listOf(antigua, reciente).forEach { assertNull(sistema.reportesRepo.buscarPorSesion(it.id), "fuera de la ventana") }
        assertNull(sistema.reportesRepo.buscarPorSesion(enCurso.id), "una entrevista en curso no se toca")
    }

    @Test
    fun `el rescate no toca reportes terminados`() = runBlocking<Unit> {
        val lista = entrevistaFinalizada().also { generar(it) }
        val conError = entrevistaFinalizada().also { tomado(it) }
        sistema.reportesRepo.marcarError(conError.id, "error_interno", sistema.reloj.instant())
        sistema.reloj.adelantar(Duration.ofHours(1))

        assertEquals(0, sistema.reporte.rescatarInterrumpidos())
        assertEquals(EstadoReporte.LISTO, reporteDe(lista).estado)
        assertEquals("error_interno", reporteDe(conError).errorDetalle)
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
