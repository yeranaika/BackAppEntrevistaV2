package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPractica
import MODELOS.EstadoPregunta
import MODELOS.ModoPractica
import MODELOS.NivelExperiencia
import MODELOS.NuevaOpcion
import MODELOS.TablaRespuestaPractica
import MODELOS.TablaSesionPractica
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.sembrarPregunta
import SERVICIOS.IntentoOfflineEntrada
import SERVICIOS.PedidoPractica
import SERVICIOS.RespuestaOfflineEntrada
import SERVICIOS.RespuestaPrueba
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioPractica {
    private val sistema = SistemaPrueba()
    private val servicio = sistema.practica
    private val ana: UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
    }
    private val skillKotlin: UUID = sistema.crearCatalogo("Android Developer", "Kotlin").second

    private val idealSuspend = "Una función suspend puede pausarse sin bloquear el hilo y reanudarse después dentro de una corrutina."

    private fun sembrarAbierta(skillId: UUID? = skillKotlin, nivel: NivelExperiencia = NivelExperiencia.JUNIOR) = runBlocking {
        sistema.preguntas.crear(
            ContenidoPregunta(
                skillId, null, TipoPregunta.ABIERTA_TEXTO, CategoriaHabilidad.TECNICA, nivel, "¿Qué es una función suspend?", idealSuspend,
                Json.parseToJsonElement("""{"criterios":["x"],"palabras_clave":["suspend","hilo","corrutina"]}""").jsonObject, emptyList()
            ),
            EstadoPregunta.APROBADA
        )
    }

    private fun sembrarAlternativa(skillId: UUID? = skillKotlin) = runBlocking {
        sistema.preguntas.crear(
            ContenidoPregunta(
                skillId, null, TipoPregunta.OPCION_MULTIPLE, CategoriaHabilidad.TECNICA, NivelExperiencia.JUNIOR, "¿Qué es val?", null, null,
                listOf(NuevaOpcion("Inmutable", true, "val no se puede reasignar"), NuevaOpcion("Mutable", false, null))
            ),
            EstadoPregunta.APROBADA
        )
    }

    private fun iniciar(pedido: PedidoPractica = PedidoPractica(skillId = skillKotlin.toString(), nivel = "jr")) = runBlocking { servicio.iniciar(ana, pedido) }

    // ---------- Iniciar ----------

    @Test
    fun `practica una skill con preguntas escritas de esa skill y nunca de video`() = runBlocking<Unit> {
        repeat(3) { sembrarAlternativa() }
        sembrarAbierta()
        sistema.sembrarPregunta(skillId = skillKotlin, tipo = TipoPregunta.SIMULACION_VIDEO)
        val otraSkill = sistema.crearCatalogo("Data Engineer", "Spark").second
        sembrarAlternativa(otraSkill)

        val sesion = iniciar()

        assertEquals(4, sesion.preguntas.size)
        assertTrue(sesion.preguntas.all { it.skillId == skillKotlin.toString() && it.tipoPregunta != TipoPregunta.SIMULACION_VIDEO })
        assertEquals(ModoPractica.MIXTO, sesion.modo)
        assertEquals("Kotlin", sesion.cargoObjetivo)
    }

    @Test
    fun `el modo filtra el tipo de pregunta`() = runBlocking<Unit> {
        repeat(3) { sembrarAlternativa() }
        sembrarAbierta()
        val sesion = iniciar(PedidoPractica(skillId = skillKotlin.toString(), modo = "abierta_texto", nivel = "jr"))
        assertEquals(listOf(TipoPregunta.ABIERTA_TEXTO), sesion.preguntas.map { it.tipoPregunta })
    }

    @Test
    fun `sin skill practica el cargo del objetivo`() = runBlocking<Unit> {
        repeat(3) { sembrarAlternativa() }
        val cargo = sistema.mercadoRepo.buscarCargoPorNombre("Android Developer")!!
        sistema.mercadoRepo.vincularSkill(cargo.id, skillKotlin, NivelExperiencia.JUNIOR, 50, true)
        sistema.objetivos.reemplazarActivo(ana, "Android Developer", "mobile")
        val sesion = iniciar(PedidoPractica(nivel = "jr"))
        assertEquals("Android Developer", sesion.cargoObjetivo)
        assertEquals(3, sesion.preguntas.size)
    }

    @Test
    fun `valida lo que se pide`() {
        fallaCon<ErrorValidacion>("modo_invalido") { servicio.iniciar(ana, PedidoPractica(skillId = skillKotlin.toString(), modo = "video")) }
        fallaCon<ErrorValidacion>("cantidad_invalida") { servicio.iniciar(ana, PedidoPractica(skillId = skillKotlin.toString(), cantidadPreguntas = 21)) }
        fallaCon<ErrorValidacion>("skill_id_invalido") { servicio.iniciar(ana, PedidoPractica(skillId = "abc")) }
        fallaCon<ErrorNoEncontrado>("skill_no_encontrada") { servicio.iniciar(ana, PedidoPractica(skillId = UUID.randomUUID().toString())) }
        fallaCon<ErrorValidacion>("categoria_invalida") { servicio.iniciar(ana, PedidoPractica(nombreCargo = "QA", categoria = "dura")) }
        fallaCon<ErrorConflicto>("preguntas_insuficientes") { servicio.iniciar(ana, PedidoPractica(skillId = skillKotlin.toString(), nivel = "jr")) }
    }

    @Test
    fun `empezar otra practica abandona la anterior`() = runBlocking<Unit> {
        repeat(4) { sembrarAlternativa() }
        val primera = iniciar()
        iniciar()
        assertEquals(EstadoPractica.ABANDONADA, servicio.obtener(ana, primera.id).estado)
    }

    @Test
    fun `evita repetir preguntas de practicas recientes mientras el banco alcance`() = runBlocking<Unit> {
        repeat(4) { sembrarAlternativa() }
        val primera = iniciar(PedidoPractica(skillId = skillKotlin.toString(), nivel = "jr", cantidadPreguntas = 2))
        val segunda = iniciar(PedidoPractica(skillId = skillKotlin.toString(), nivel = "jr", cantidadPreguntas = 2))
        assertTrue(primera.preguntas.map { it.preguntaId }.intersect(segunda.preguntas.map { it.preguntaId }.toSet()).isEmpty())
    }

    // ---------- Responder ----------

    @Test
    fun `feedback inmediato con la explicacion de la opcion y el motor freemium en las abiertas`() = runBlocking<Unit> {
        sembrarAlternativa()
        sembrarAbierta()
        val sesion = iniciar()
        val alternativa = sesion.preguntas.first { it.tipoPregunta == TipoPregunta.OPCION_MULTIPLE }
        val abierta = sesion.preguntas.first { it.tipoPregunta == TipoPregunta.ABIERTA_TEXTO }

        val bien = servicio.responder(ana, sesion.id, RespuestaPrueba(alternativa.id, opcionId = alternativa.opcionCorrecta!!.id, tiempoRespuestaMs = 4000))
        assertTrue(bien.esCorrecta)
        assertEquals(0, BigDecimal(100).compareTo(bien.puntaje))
        assertEquals("val no se puede reasignar", bien.feedback)

        val texto = servicio.responder(ana, sesion.id, RespuestaPrueba(abierta.id, texto = "Una función suspend se pausa sin bloquear el hilo dentro de una corrutina y luego se reanuda."))
        assertTrue(texto.esCorrecta, "puntaje ${texto.puntaje}")
        assertTrue(texto.feedback!!.isNotBlank())

        transaction {
            val filas = TablaRespuestaPractica.selectAll().toList()
            assertEquals(2, filas.size)
            assertEquals(0, BigDecimal(10).compareTo(filas.first { it[TablaRespuestaPractica.orden].toInt() == alternativa.orden }[TablaRespuestaPractica.puntaje]), "en la BD es 0 a 10")
            assertEquals(4000, filas.first { it[TablaRespuestaPractica.orden].toInt() == alternativa.orden }[TablaRespuestaPractica.tiempoRespuestaMs])
        }
    }

    @Test
    fun `una pregunta se responde una vez y un lote con un error no guarda nada`() = runBlocking<Unit> {
        repeat(3) { sembrarAlternativa() }
        val sesion = iniciar()
        val (p1, p2, p3) = sesion.preguntas
        servicio.responder(ana, sesion.id, RespuestaPrueba(p1.id, opcionId = p1.opciones[0].id))
        fallaCon<ErrorConflicto>("pregunta_ya_respondida") { servicio.responder(ana, sesion.id, RespuestaPrueba(p1.id, opcionId = p1.opciones[0].id)) }
        fallaCon<ErrorValidacion>("opcion_invalida") {
            servicio.responderVarias(ana, sesion.id, listOf(RespuestaPrueba(p2.id, opcionId = p2.opciones[0].id), RespuestaPrueba(p3.id, opcionId = "x")))
        }
        assertEquals(1, servicio.obtener(ana, sesion.id).respuestas.size)
        fallaCon<ErrorValidacion>("tiempo_invalido") { servicio.responder(ana, sesion.id, RespuestaPrueba(p2.id, opcionId = p2.opciones[0].id, tiempoRespuestaMs = -1)) }
        fallaCon<ErrorNoEncontrado>("pregunta_no_encontrada") { servicio.responder(ana, sesion.id, RespuestaPrueba("no-existe", opcionId = "x")) }
    }

    @Test
    fun `la practica de otro usuario no existe para mi`() = runBlocking<Unit> {
        sembrarAlternativa()
        val sesion = iniciar()
        sistema.usuario.registrar(SolicitudRegistro("beto@ejemplo.com", "Clave-segura-1"))
        val beto = sistema.usuarios.buscarPorCorreo("beto@ejemplo.com")!!.id
        fallaCon<ErrorNoEncontrado>("practica_no_encontrada") { servicio.obtener(beto, sesion.id) }
        fallaCon<ErrorNoEncontrado>("practica_no_encontrada") { servicio.finalizar(beto, sesion.id) }
    }

    // ---------- Finalizar ----------

    @Test
    fun `finalizar promedia lo respondido y acumula el puntaje de la skill sin cambiar su nivel`() = runBlocking<Unit> {
        repeat(2) { sembrarAlternativa() }
        val sesion = iniciar()
        fallaCon<ErrorValidacion>("sin_respuestas") { servicio.finalizar(ana, sesion.id) }
        val (p1, p2) = sesion.preguntas
        servicio.responder(ana, sesion.id, RespuestaPrueba(p1.id, opcionId = p1.opcionCorrecta!!.id))
        servicio.responder(ana, sesion.id, RespuestaPrueba(p2.id, opcionId = p2.opciones.first { !it.esCorrecta }.id))

        val finalizada = servicio.finalizar(ana, sesion.id)

        assertEquals(EstadoPractica.FINALIZADA, finalizada.estado)
        assertEquals(0, BigDecimal(50).compareTo(finalizada.puntaje))
        transaction {
            val fila = TablaSesionPractica.selectAll().single()
            assertEquals(2, fila[TablaSesionPractica.totalPreguntas].toInt())
            assertEquals(1, fila[TablaSesionPractica.correctas].toInt())
        }
        val nivelKotlin = sistema.nivelesSkill.listar(ana).single()
        assertEquals(0, BigDecimal(50).compareTo(nivelKotlin.puntaje))
        assertNull(nivelKotlin.nivel, "la práctica no decide el nivel")
        fallaCon<ErrorConflicto>("practica_no_activa") { servicio.finalizar(ana, sesion.id) }
        fallaCon<ErrorConflicto>("practica_no_activa") { servicio.responder(ana, sesion.id, RespuestaPrueba(p1.id, opcionId = p1.opciones[0].id)) }
    }

    // ---------- Sincronización offline ----------

    private fun intentoOffline(idLocal: String, respuestas: List<RespuestaOfflineEntrada>) =
        IntentoOfflineEntrada(idLocal, skillKotlin.toString(), null, "opcion_multiple", "tecnica", "junior", "2025-12-30T10:00:00Z", respuestas)

    @Test
    fun `sincroniza intentos offline una sola vez y vuelve a corregir con el banco`() = runBlocking<Unit> {
        val pregunta = sembrarAlternativa()
        val incorrecta = pregunta.opciones.first { !it.esCorrecta }.id.toString()
        // La app dice que acertó, pero eligió la opción incorrecta: manda la corrección del servidor.
        val delBanco = RespuestaOfflineEntrada(pregunta.id.toString(), "¿Qué es val?", incorrecta, null, true, 10.0, 3000, 1)
        val local = RespuestaOfflineEntrada("pregunta-local-7", "Pregunta descargada antes", null, null, true, 8.0, null, 2)

        val primera = servicio.sincronizarOffline(ana, listOf(intentoOffline("local-1", listOf(delBanco, local))))
        val reintento = servicio.sincronizarOffline(ana, listOf(intentoOffline("local-1", listOf(delBanco, local))))

        assertEquals(primera, reintento, "reenviar el mismo lote no duplica")
        val sesion = servicio.obtener(ana, primera.single().second)
        assertEquals(EstadoPractica.FINALIZADA, sesion.estado)
        assertEquals(listOf(false, true), sesion.respuestas.map { it.esCorrecta })
        assertEquals(0, BigDecimal(80).compareTo(sesion.respuestas[1].puntaje), "sin la pregunta en el banco se guarda lo que calculó la app")
        assertEquals("2025-12-30T10:00:00Z", sesion.fechaInicio.toString())
        assertEquals(1, sistema.nivelesSkill.listar(ana).single().evaluaciones, "el reintento no acumula dos veces")
    }

    @Test
    fun `valida el lote offline antes de guardar nada`() {
        val ok = RespuestaOfflineEntrada("p", "Enunciado", null, null, false, 0.0, null, 1)
        fallaCon<ErrorValidacion>("lote_invalido") { servicio.sincronizarOffline(ana, emptyList()) }
        fallaCon<ErrorValidacion>("id_local_repetido") { servicio.sincronizarOffline(ana, listOf(intentoOffline("a", listOf(ok)), intentoOffline("a", listOf(ok)))) }
        fallaCon<ErrorValidacion>("skill_no_encontrada") {
            servicio.sincronizarOffline(ana, listOf(intentoOffline("a", listOf(ok)).copy(skillId = UUID.randomUUID().toString())))
        }
        fallaCon<ErrorValidacion>("orden_invalido") { servicio.sincronizarOffline(ana, listOf(intentoOffline("a", listOf(ok, ok)))) }
        fallaCon<ErrorValidacion>("respuestas_invalidas") { servicio.sincronizarOffline(ana, listOf(intentoOffline("a", emptyList()))) }
        fallaCon<ErrorValidacion>("modo_invalido") { servicio.sincronizarOffline(ana, listOf(intentoOffline("a", listOf(ok)).copy(modo = "video"))) }
        assertEquals(0, transaction { TablaSesionPractica.selectAll().count() })
    }
}
