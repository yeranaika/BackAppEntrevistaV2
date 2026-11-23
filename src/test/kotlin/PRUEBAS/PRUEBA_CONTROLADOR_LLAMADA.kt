package PRUEBAS

import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.MotorInferenciaDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import ESQUEMAS.SolicitudRegistro
import MODELOS.Llamada
import MODELOS.ResumenLlamada
import MODELOS.AccionLlamada
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.sembrarBancoGeneral
import SERVICIOS.PedidoEntrevista
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
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Historial de llamadas en vivo (T3.15): repositorio sobre H2 y contrato HTTP de /api/v1/llamadas. */
class PruebaControladorLlamada {
    private val json = Json { ignoreUnknownKeys = true }

    private val motorGuionado = object : MotorInferenciaDivlara {
        override fun cantidadFeaturesEsperadas(): Int? = null
        override fun predecir(features: List<Double>) = ResultadoPrediccionDivlara(
            VERSION_MODELO_DIVLARA, mapOf("head_nodding" to if (features[0] == 1.0) 7.0 else 1.0)
        )
    }

    private fun tokenDe(sistema: SistemaPrueba, correo: String): String =
        runBlocking { sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1")).tokenAcceso }

    private fun ApplicationTestBuilder.get(ruta: String, token: String?): HttpResponse =
        runBlocking { client.get(ruta) { token?.let { bearerAuth(it) } } }

    private fun ApplicationTestBuilder.post(ruta: String, cuerpo: String, token: String?): HttpResponse = runBlocking {
        client.post(ruta) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }

    private fun cuerpo(idLocal: String, inicioMs: Long = 1_700_000_000_000, duracionSeg: Int = 120, extra: String = "") =
        """{"idLocal":"$idLocal","fechaInicioMs":$inicioMs,"duracionSeg":$duracionSeg,"preguntasCnt":3$extra}"""

    private val resumenJson = ""","resumen":{"acciones":[{"accion":"head_nodding","veces":2,"totalMs":9000,"maximoMs":6000}],""" +
        """"linea":[{"inicioMs":0,"finMs":3000,"accion":"sin_accion"},{"inicioMs":3000,"finMs":9000,"accion":"head_nodding"}]}"""

    // ---------- Repositorio ----------

    @Test
    fun `el repositorio guarda, lista por fecha descendente y es idempotente por idLocal`() = runBlocking {
        val sistema = SistemaPrueba()
        sistema.usuario.registrar(SolicitudRegistro("repo@ejemplo.com", "Clave-segura-1"))
        val usuarioId = sistema.usuarios.buscarPorCorreo("repo@ejemplo.com")!!.id
        fun llamada(idLocal: String, inicio: Long) = Llamada(
            UUID.randomUUID(), idLocal, null, Instant.ofEpochMilli(inicio), 60, 2,
            ResumenLlamada(listOf(AccionLlamada("head_nodding", 3, 9000, 4000)))
        )
        assertTrue(sistema.llamadasRepo.registrar(usuarioId, llamada("a", 1_000)))
        assertTrue(sistema.llamadasRepo.registrar(usuarioId, llamada("b", 2_000)))
        assertFalse(sistema.llamadasRepo.registrar(usuarioId, llamada("a", 9_999)), "el reenvío no duplica")

        val pagina = sistema.llamadasRepo.listarDeUsuario(usuarioId, 1, 10)
        assertEquals(2, pagina.total)
        assertEquals(listOf("b", "a"), pagina.elementos.map { it.idLocal })
        assertEquals(3, pagina.elementos.first().resumen.totalOcurrencias)
        assertEquals(1, sistema.llamadasRepo.listarDeUsuario(usuarioId, 2, 1).elementos.size)
        assertEquals(1_000, sistema.llamadasRepo.buscarPorIdLocal(usuarioId, "a")!!.fechaInicio.toEpochMilli())
    }

    // ---------- Endpoints ----------

    @Test
    fun `la lista de un usuario sin llamadas viene vacia y exige sesion`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "vacio@ejemplo.com")

        val lista = get("/api/v1/llamadas", token)
        assertEquals(HttpStatusCode.OK, lista.status)
        assertEquals(0, lista.objeto()["total"]!!.jsonPrimitive.content.toInt())
        assertTrue(lista.objeto()["elementos"]!!.jsonArray.isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, get("/api/v1/llamadas", null).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/api/v1/llamadas", cuerpo("x"), null).status)
    }

    @Test
    fun `guardar una llamada y leerla en la lista y en el detalle`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "ida@ejemplo.com")

        val creada = post("/api/v1/llamadas", cuerpo("local-1", extra = resumenJson), token)
        assertEquals(HttpStatusCode.Created, creada.status)
        val id = creada.objeto()["id"]!!.jsonPrimitive.content
        assertTrue(creada.objeto()["mensaje"]!!.jsonPrimitive.content.isNotBlank())

        val lista = get("/api/v1/llamadas", token).objeto()
        val fila = lista["elementos"]!!.jsonArray.single().jsonObject
        assertEquals(id, fila["id"]!!.jsonPrimitive.content)
        assertEquals("120", fila["duracionSeg"]!!.jsonPrimitive.content)
        assertEquals("3", fila["preguntasCnt"]!!.jsonPrimitive.content)
        assertEquals("2", fila["accionesCnt"]!!.jsonPrimitive.content)
        assertEquals("1700000000000", fila["fechaInicioMs"]!!.jsonPrimitive.content)

        val detalle = get("/api/v1/llamadas/$id", token).objeto()
        assertEquals("head_nodding", detalle["acciones"]!!.jsonArray.single().jsonObject["accion"]!!.jsonPrimitive.content)
        assertEquals(2, detalle["linea"]!!.jsonArray.size)
    }

    @Test
    fun `reenviar la misma llamada no duplica y responde 200`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "dup@ejemplo.com")

        val primera = post("/api/v1/llamadas", cuerpo("local-1"), token).objeto()["id"]!!.jsonPrimitive.content
        val reenvio = post("/api/v1/llamadas", cuerpo("local-1"), token)
        assertEquals(HttpStatusCode.OK, reenvio.status)
        assertEquals(primera, reenvio.objeto()["id"]!!.jsonPrimitive.content)
        assertEquals(1, get("/api/v1/llamadas", token).objeto()["total"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `un usuario no ve ni lista las llamadas de otro`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val dueno = tokenDe(sistema, "dueno@ejemplo.com")
        val otro = tokenDe(sistema, "otro@ejemplo.com")
        val id = post("/api/v1/llamadas", cuerpo("local-1"), dueno).objeto()["id"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NotFound, get("/api/v1/llamadas/$id", otro).status)
        assertEquals(0, get("/api/v1/llamadas", otro).objeto()["total"]!!.jsonPrimitive.content.toInt())
        // El mismo idLocal en otra cuenta es otra llamada, no un reenvío.
        assertEquals(HttpStatusCode.Created, post("/api/v1/llamadas", cuerpo("local-1"), otro).status)
    }

    @Test
    fun `datos invalidos responden 400`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "malo@ejemplo.com")

        assertEquals(HttpStatusCode.BadRequest, post("/api/v1/llamadas", cuerpo(""), token).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/v1/llamadas", cuerpo("x", duracionSeg = -1), token).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/v1/llamadas", cuerpo("x", inicioMs = 0), token).status)
        val negativo = ""","resumen":{"acciones":[{"accion":"a","veces":-1,"totalMs":1,"maximoMs":1}]}"""
        assertEquals(HttpStatusCode.BadRequest, post("/api/v1/llamadas", cuerpo("x", extra = negativo), token).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/v1/llamadas", cuerpo("x", extra = ""","sesionId":"no-es-uuid""""), token).status)
        assertEquals(HttpStatusCode.BadRequest, get("/api/v1/llamadas?pagina=0", token).status)
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/llamadas/${UUID.randomUUID()}", token).status)
    }

    @Test
    fun `sin resumen y con sesionId se deriva del reporte de la simulacion`() = testApplication {
        val sistema = SistemaPrueba(motorVision = motorGuionado).apply { sembrarBancoGeneral(tecnicas = 6, blandas = 4) }
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "deriva@ejemplo.com")
        val usuarioId = runBlocking { sistema.usuarios.buscarPorCorreo("deriva@ejemplo.com")!!.id }
        val sesion = runBlocking { sistema.entrevista.iniciar(usuarioId, PedidoEntrevista(nombreCargo = "Backend Developer")).id }
        val features = List(CANTIDAD_FEATURES_DIVLARA) { if (it == 0) "1" else "0" }.joinToString(",")
        listOf(3_000L, 4_000L, 5_000L).forEach { ts ->
            post("/api/v1/vision/predecir", """{"features":[$features],"timestampMs":$ts,"sesionId":"$sesion"}""", token)
        }

        val creada = post("/api/v1/llamadas", cuerpo("local-1", extra = ""","sesionId":"$sesion""""), token)
        assertEquals(HttpStatusCode.Created, creada.status)
        val detalle = creada.objeto()
        assertEquals(sesion.toString(), detalle["sesionId"]!!.jsonPrimitive.content)
        assertEquals("head_nodding", detalle["acciones"]!!.jsonArray.single().jsonObject["accion"]!!.jsonPrimitive.content)
        assertTrue(detalle["linea"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `una llamada sin resumen ni sesion queda con resumen vacio`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val token = tokenDe(sistema, "sinres@ejemplo.com")
        val detalle = post("/api/v1/llamadas", cuerpo("local-1"), token).objeto()
        assertTrue(detalle["acciones"]!!.jsonArray.isEmpty())
        assertNull(detalle["sesionId"]?.takeIf { it.toString() != "null" })
    }
}
