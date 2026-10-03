package PRUEBAS

import CONFIGURACION.configurarErrores
import ERRORES.ErrorConflicto
import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorProhibido
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PruebaConfiguracionErrores {

    @Serializable
    private data class CuerpoPrueba(val nombre: String)

    private fun ApplicationTestBuilder.montarApp() {
        application {
            install(ContentNegotiation) { json() }
            configurarErrores()
            routing {
                get("/validacion") { throw ErrorValidacion("dato_invalido", "El dato no es válido") }
                get("/no-autorizado") { throw ErrorNoAutorizado("credenciales_invalidas") }
                get("/prohibido") { throw ErrorProhibido("sin_permiso") }
                get("/no-encontrado") { throw ErrorNoEncontrado("usuario_no_encontrado") }
                get("/conflicto") { throw ErrorConflicto("correo_en_uso") }
                get("/externo") { throw ErrorServicioExterno("proveedor_caido", causa = IllegalStateException("timeout")) }
                get("/inesperado") { error("detalle interno sensible") }
                post("/json") { call.receive<CuerpoPrueba>() }
            }
        }
    }

    private fun campo(cuerpo: String, nombre: String): String? =
        Json.parseToJsonElement(cuerpo).jsonObject[nombre]?.jsonPrimitive?.content

    @Test
    fun `cada error de dominio se traduce a su codigo http`() = testApplication {
        montarApp()
        val casos = mapOf(
            "/validacion" to HttpStatusCode.BadRequest,
            "/no-autorizado" to HttpStatusCode.Unauthorized,
            "/prohibido" to HttpStatusCode.Forbidden,
            "/no-encontrado" to HttpStatusCode.NotFound,
            "/conflicto" to HttpStatusCode.Conflict,
            "/externo" to HttpStatusCode.ServiceUnavailable
        )
        casos.forEach { (ruta, estadoEsperado) ->
            assertEquals(estadoEsperado, client.get(ruta).status, ruta)
        }
    }

    @Test
    fun `el cuerpo trae codigo y mensaje`() = testApplication {
        montarApp()
        val cuerpo = client.get("/validacion").bodyAsText()
        assertEquals("dato_invalido", campo(cuerpo, "error"))
        assertEquals("El dato no es válido", campo(cuerpo, "mensaje"))
    }

    @Test
    fun `json malformado responde 400 invalid_json`() = testApplication {
        montarApp()
        val respuesta = client.post("/json") {
            contentType(ContentType.Application.Json)
            setBody("{no es json")
        }
        assertEquals(HttpStatusCode.BadRequest, respuesta.status)
        assertEquals("invalid_json", campo(respuesta.bodyAsText(), "error"))
    }

    @Test
    fun `error inesperado responde 500 sin filtrar detalles internos`() = testApplication {
        montarApp()
        val respuesta = client.get("/inesperado")
        assertEquals(HttpStatusCode.InternalServerError, respuesta.status)
        val cuerpo = respuesta.bodyAsText()
        assertEquals("server_error", campo(cuerpo, "error"))
        assertFalse(cuerpo.contains("sensible"))
    }
}
