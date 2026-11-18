package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.NivelExperiencia
import MODELOS.NuevaSkill
import MODELOS.NuevoCargo
import MODELOS.ROL_ADMIN
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.ClienteMercadoLaboralFalso
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.oferta
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D26 por HTTP: la generación usa las ofertas del cargo y la cobertura cruza el mercado con el banco. */
class PruebaCoberturaPreguntas {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "/api/v1/admin/preguntas"

    private fun tokenDe(sistema: SistemaPrueba, correo: String, rol: String? = null): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, rol)
        sistema.tokens.emitirPar(id, rol).tokenAcceso
    }

    private suspend fun ApplicationTestBuilder.obtener(ruta: String, token: String?) =
        client.get(ruta) { token?.let { bearerAuth(it) } }

    private suspend fun ApplicationTestBuilder.generar(cuerpo: String, token: String) = client.post("$base/generar-ia") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content
    private fun JsonObject.niveles(campo: String) = this[campo]!!.jsonObject.let {
        listOf(it.texto("junior"), it.texto("semisenior"), it.texto("senior")).map { n -> n!!.toInt() }
    }

    private fun pregunta(skillId: UUID, cargoId: UUID?, nivel: NivelExperiencia) = ContenidoPregunta(
        skillId = skillId, cargoId = cargoId, tipo = TipoPregunta.ABIERTA_TEXTO, categoria = CategoriaHabilidad.TECNICA,
        nivel = nivel, enunciado = "Pregunta ${UUID.randomUUID()}", respuestaIdeal = "Respuesta", rubrica = null, opciones = emptyList()
    )

    @Test
    fun `generar usa las ofertas del cargo y lo informa en contexto_mercado`() = testApplication {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        val ofertas = listOf(oferta("Senior Backend Engineer", "Build services in Kotlin with Ktor.  Strong SQL and PostgreSQL skills."))
        val sistema = SistemaPrueba(proveedorIa = proveedor, clienteMercado = ClienteMercadoLaboralFalso(mapOf("Backend Developer" to ofertas)))
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val (cargoId, skillId) = sistema.crearCatalogo()

        val respuesta = generar("""{"cargo_id":"$cargoId","skill_id":"$skillId","nivel":"senior","cantidad":1}""", admin)
        assertEquals(HttpStatusCode.Created, respuesta.status)
        val cuerpo = respuesta.objeto()
        val contexto = cuerpo["contexto_mercado"]!!.jsonObject
        assertEquals("true", contexto.texto("usado"))
        assertEquals("1", contexto.texto("ofertas_analizadas"))
        assertEquals("Backend Developer", contexto.texto("consulta"))
        assertTrue(contexto["fragmentos"]!!.jsonArray.first().jsonObject.texto("texto")!!.contains("Kotlin"))
        assertTrue(cuerpo.texto("mensaje")!!.contains("Se usó 1 oferta de empleo"), cuerpo.toString())
        assertTrue(proveedor.solicitudes.single().prompt.contains("LO QUE PIDE EL MERCADO"))

        // Sin pedirlo, como antes: no se buscan ofertas y se dice por qué.
        val sinMercado = generar("""{"cargo_id":"$cargoId","nivel":"senior","cantidad":1,"usar_mercado":false}""", admin).objeto()
        val motivo = sinMercado["contexto_mercado"]!!.jsonObject
        assertEquals("false", motivo.texto("usado"))
        assertEquals("desactivado", motivo.texto("motivo"))
        assertFalse(proveedor.solicitudes.last().prompt.contains("LO QUE PIDE EL MERCADO"))
        assertEquals(listOf<String?>("Backend Developer"), sistema.clienteMercado.consultas)
    }

    @Test
    fun `cobertura cuenta por cargo y nivel las preguntas que sirven para cada skill del mercado`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val usuario = tokenDe(sistema, "ana@ejemplo.com")

        val (backend, frontend, kotlinId) = runBlocking {
            val repo = sistema.mercadoRepo
            val backend = repo.crearCargo(NuevoCargo("Backend Developer", "backend", null, NivelExperiencia.SEMISENIOR))
            val frontend = repo.crearCargo(NuevoCargo("Frontend Developer", "frontend", null, NivelExperiencia.SEMISENIOR))
            val kotlin = repo.obtenerOCrearSkill(NuevaSkill("Kotlin", "tecnica", "backend", null, 80))
            val sql = repo.obtenerOCrearSkill(NuevaSkill("SQL", "tecnica", "backend", null, 60))
            repo.vincularSkill(backend.id, sql, NivelExperiencia.SEMISENIOR, 95, false)
            repo.vincularSkill(backend.id, kotlin, NivelExperiencia.SENIOR, 70, true)

            val p = sistema.preguntas
            p.crear(pregunta(kotlin, backend.id, NivelExperiencia.JUNIOR), EstadoPregunta.APROBADA) // del cargo: cuenta
            p.crear(pregunta(kotlin, null, NivelExperiencia.JUNIOR), EstadoPregunta.APROBADA) // sin cargo: cuenta
            p.crear(pregunta(kotlin, frontend.id, NivelExperiencia.JUNIOR), EstadoPregunta.APROBADA) // de otro cargo: no
            p.crear(pregunta(kotlin, backend.id, NivelExperiencia.SENIOR), EstadoPregunta.PENDIENTE)
            p.crear(pregunta(kotlin, backend.id, NivelExperiencia.SENIOR), EstadoPregunta.RECHAZADA) // no cuenta
            p.crear(pregunta(kotlin, frontend.id, NivelExperiencia.SENIOR), EstadoPregunta.PENDIENTE) // de otro cargo: no
            Triple(backend, frontend, kotlin)
        }

        assertEquals(HttpStatusCode.Unauthorized, obtener("$base/cobertura", null).status)
        assertEquals(HttpStatusCode.Forbidden, obtener("$base/cobertura", usuario).status)

        val respuesta = obtener("$base/cobertura?cargoId=${backend.id}&minimo=2", admin)
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val cuerpo = respuesta.objeto()
        assertEquals("2", cuerpo.texto("minimo"))
        val cargo = cuerpo["cargos"]!!.jsonArray.single().jsonObject
        val skills = cargo["skills"]!!.jsonArray.map { it.jsonObject }
        // La obligatoria primero aunque pese menos.
        assertEquals(listOf("Kotlin", "SQL"), skills.map { it.texto("nombre") })
        val kotlin = skills[0]
        assertEquals(listOf(2, 0, 0), kotlin.niveles("aprobadas"))
        assertEquals(listOf(0, 0, 1), kotlin.niveles("pendientes"))
        assertEquals(listOf(0, 2, 2), kotlin.niveles("faltan"))
        assertEquals(listOf(2, 2, 2), skills[1].niveles("faltan"))
        assertEquals("10", cargo.texto("preguntasFaltantes"))
        assertEquals("2", cargo.texto("skillsIncompletas"))

        // El panel abre las pendientes de la celda con paraCargoId: la lista coincide con lo que cuenta la cobertura.
        val pendientes = obtener("$base?estado=pendiente&skillId=$kotlinId&nivel=senior&paraCargoId=${backend.id}", admin).objeto()
        assertEquals("1", pendientes.texto("total"))
        val sinFiltroDeCargo = obtener("$base?estado=pendiente&skillId=$kotlinId&nivel=senior", admin).objeto()
        assertEquals("2", sinFiltroDeCargo.texto("total"))

        // Sin cargo: todos los activos, primero el que más preguntas necesita.
        val todos = obtener("$base/cobertura", admin).objeto()
        assertEquals("5", todos.texto("minimo"))
        assertEquals(listOf(backend.nombre, frontend.nombre), todos["cargos"]!!.jsonArray.map { it.jsonObject.texto("nombre") })

        val minimoMalo = obtener("$base/cobertura?minimo=0", admin)
        assertEquals(HttpStatusCode.BadRequest, minimoMalo.status)
        assertEquals("minimo_invalido", minimoMalo.objeto().texto("error"))
        assertEquals("id_invalido", obtener("$base/cobertura?cargoId=abc", admin).objeto().texto("error"))
        val inexistente = obtener("$base/cobertura?cargoId=${UUID.randomUUID()}", admin)
        assertEquals(HttpStatusCode.NotFound, inexistente.status)
    }
}
