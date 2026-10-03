package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.ConsultaPreguntas
import ESQUEMAS.SolicitudGenerarPreguntas
import ESQUEMAS.SolicitudOpcion
import ESQUEMAS.SolicitudPregunta
import MODELOS.EstadoPregunta
import MODELOS.TablaGeneracionPreguntaIa
import MODELOS.TablaPregunta
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas del banco de preguntas con el repositorio real sobre H2. */
class PruebaServicioPregunta {
    private val sistema = SistemaPrueba(proveedorIa = ProveedorIaGrabador(LoteLlmDePrueba.json(3, "opcion_multiple")))
    private val servicio = sistema.pregunta
    private val catalogo = sistema.crearCatalogo()
    private val cargoId = catalogo.first.toString()
    private val skillId = catalogo.second.toString()
    private val adminId = UUID.randomUUID()

    private fun opcionMultiple(
        opciones: List<SolicitudOpcion> = listOf(
            SolicitudOpcion("Una corrutina", esCorrecta = true, explicacion = "Es la unidad de concurrencia liviana"),
            SolicitudOpcion("Un hilo del sistema"),
            SolicitudOpcion("Un proceso")
        ),
        enunciado: String = "¿Qué lanza launch en Kotlin?"
    ) = SolicitudPregunta(skillId = skillId, tipo = "opcion_multiple", categoria = "tecnica", nivel = "junior", enunciado = enunciado, opciones = opciones)

    private fun abierta(respuestaIdeal: String? = LoteLlmDePrueba.RESPUESTA_STAR) =
        SolicitudPregunta(cargoId = cargoId, tipo = "abierta_texto", categoria = "blanda", nivel = "senior", enunciado = "Cuéntame un conflicto en tu equipo", respuestaIdeal = respuestaIdeal)

    // ---------- Crear ----------

    @Test
    fun `una pregunta creada por el admin nace aprobada y conserva el orden de las opciones`() = runBlocking<Unit> {
        val pregunta = servicio.crear(opcionMultiple())
        assertEquals(EstadoPregunta.APROBADA, pregunta.estado)
        assertEquals(listOf("Una corrutina", "Un hilo del sistema", "Un proceso"), pregunta.opciones.map { it.texto })
        assertEquals(listOf(1, 2, 3), pregunta.opciones.map { it.orden })
        assertEquals(1, pregunta.opciones.count { it.esCorrecta })
    }

    @Test
    fun `opcion multiple exige entre 2 y 6 opciones, una sola correcta y sin repetir`() {
        fallaCon<ErrorValidacion>("opciones_invalidas") { servicio.crear(opcionMultiple(listOf(SolicitudOpcion("Única", true)))) }
        fallaCon<ErrorValidacion>("debe_haber_una_correcta") {
            servicio.crear(opcionMultiple(listOf(SolicitudOpcion("A", true), SolicitudOpcion("B", true))))
        }
        fallaCon<ErrorValidacion>("debe_haber_una_correcta") {
            servicio.crear(opcionMultiple(listOf(SolicitudOpcion("A"), SolicitudOpcion("B"))))
        }
        fallaCon<ErrorValidacion>("opcion_duplicada") {
            servicio.crear(opcionMultiple(listOf(SolicitudOpcion("A", true), SolicitudOpcion(" a "))))
        }
        fallaCon<ErrorValidacion>("opcion_vacia") { servicio.crear(opcionMultiple(listOf(SolicitudOpcion("A", true), SolicitudOpcion("  ")))) }
    }

    @Test
    fun `una pregunta abierta necesita respuesta ideal o rubrica y no lleva opciones`() {
        fallaCon<ErrorValidacion>("respuesta_ideal_o_rubrica_requerida") { servicio.crear(abierta(respuestaIdeal = " ")) }
        fallaCon<ErrorValidacion>("opciones_no_permitidas") {
            servicio.crear(abierta().copy(opciones = listOf(SolicitudOpcion("A", true), SolicitudOpcion("B"))))
        }
    }

    @Test
    fun `tipo, categoria, nivel y contexto se validan contra el catalogo`() {
        fallaCon<ErrorValidacion>("tipo_invalido") { servicio.crear(abierta().copy(tipo = "verdadero_falso")) }
        fallaCon<ErrorValidacion>("categoria_invalida") { servicio.crear(abierta().copy(categoria = "dura")) }
        fallaCon<ErrorValidacion>("nivel_invalido") { servicio.crear(abierta().copy(nivel = "experto")) }
        fallaCon<ErrorValidacion>("contexto_requerido") { servicio.crear(abierta().copy(cargoId = null)) }
        fallaCon<ErrorNoEncontrado>("cargo_no_encontrado") { servicio.crear(abierta().copy(cargoId = UUID.randomUUID().toString())) }
        fallaCon<ErrorValidacion>("enunciado_requerido") { servicio.crear(abierta().copy(enunciado = "   ")) }
    }

    // ---------- Revisión ----------

    @Test
    fun `las preguntas de IA nacen pendientes y aprobarlas actualiza la trazabilidad`() = runBlocking<Unit> {
        val creada = sistema.generacion.generar(
            SolicitudGenerarPreguntas(cargoId = cargoId, nivel = "junior", cantidad = 3, tipo = "opcion_multiple")
        ).first()
        val id = creada.ids.preguntaId
        assertEquals(EstadoPregunta.PENDIENTE, servicio.obtener(id).estado)

        val aprobada = servicio.aprobar(id, adminId)
        assertEquals(EstadoPregunta.APROBADA, aprobada.estado)
        transaction {
            val traza = TablaGeneracionPreguntaIa.selectAll().where { TablaGeneracionPreguntaIa.preguntaId eq id }.single()
            assertEquals("aprobada", traza[TablaGeneracionPreguntaIa.estadoRevision])
            assertEquals(adminId, traza[TablaGeneracionPreguntaIa.revisadoPor])
        }
    }

    @Test
    fun `rechazar exige motivo y lo guarda, aprobar despues lo limpia`() = runBlocking<Unit> {
        val id = servicio.crear(abierta()).id
        fallaCon<ErrorValidacion>("motivo_requerido") { servicio.rechazar(id, "  ", adminId) }

        assertEquals("Ambigua", servicio.rechazar(id, " Ambigua ", adminId).motivoRechazo)
        assertNull(servicio.aprobar(id, adminId).motivoRechazo)
    }

    @Test
    fun `editar reemplaza el contenido y devuelve la pregunta a revision`() = runBlocking<Unit> {
        val id = servicio.crear(opcionMultiple()).id
        val editada = servicio.editar(
            id,
            opcionMultiple(listOf(SolicitudOpcion("Nueva A", true), SolicitudOpcion("Nueva B")), enunciado = "Enunciado nuevo")
        )
        assertEquals("Enunciado nuevo", editada.enunciado)
        assertEquals(listOf("Nueva A", "Nueva B"), editada.opciones.map { it.texto })
        assertEquals(EstadoPregunta.PENDIENTE, editada.estado)
    }

    @Test
    fun `solo se elimina una pregunta que nunca se uso`() = runBlocking<Unit> {
        val usada = servicio.crear(abierta()).id
        transaction { TablaPregunta.update({ TablaPregunta.preguntaId eq usada }) { it[vecesUsada] = 3 } }
        fallaCon<ErrorConflicto>("pregunta_en_uso") { servicio.eliminar(usada) }

        val nueva = servicio.crear(opcionMultiple()).id
        servicio.eliminar(nueva)
        fallaCon<ErrorNoEncontrado>("pregunta_no_encontrada") { servicio.obtener(nueva) }
    }

    // ---------- Consultas ----------

    @Test
    fun `listar filtra por estado y tipo y pagina`() = runBlocking<Unit> {
        repeat(3) { servicio.crear(opcionMultiple(enunciado = "Pregunta $it")) }
        val rechazada = servicio.crear(abierta()).id
        servicio.rechazar(rechazada, "Duplicada", adminId)

        val aprobadas = servicio.listar(ConsultaPreguntas(estado = "aprobada", tamano = 2))
        assertEquals(3, aprobadas.total)
        assertEquals(2, aprobadas.elementos.size)
        assertEquals(1, servicio.listar(ConsultaPreguntas(estado = "aprobada", tamano = 2, pagina = 2)).elementos.size)
        assertEquals(1, servicio.listar(ConsultaPreguntas(tipo = "abierta_texto")).total)
        fallaCon<ErrorValidacion>("estado_invalido") { servicio.listar(ConsultaPreguntas(estado = "borrador")) }
        fallaCon<ErrorValidacion>("tamano_invalido") { servicio.listar(ConsultaPreguntas(tamano = 500)) }
    }

    @Test
    fun `los usuarios solo reciben preguntas aprobadas`() = runBlocking<Unit> {
        val aprobada = servicio.crear(opcionMultiple()).id
        servicio.rechazar(servicio.crear(abierta()).id, "Mala", adminId)
        sistema.generacion.generar(SolicitudGenerarPreguntas(cargoId = cargoId, nivel = "junior", cantidad = 3, tipo = "opcion_multiple"))

        val visibles = servicio.listarParaUsuario(ConsultaPreguntas(), cantidad = 20)
        assertEquals(listOf(aprobada), visibles.map { it.id })
        assertTrue(visibles.all { it.estado == EstadoPregunta.APROBADA })
    }
}
