package PRUEBAS

import CONFIGURACION.configurarErrores
import MIDDLEWARES.soloAdmin
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class PruebaSoloAdmin {
    private val algoritmo = Algorithm.HMAC512("secreto-prueba")

    private fun token(rol: String): String = JWT.create()
        .withIssuer("emisor")
        .withAudience("audiencia")
        .withSubject("00000000-0000-0000-0000-000000000001")
        .withClaim("role", rol)
        .sign(algoritmo)

    private fun ApplicationTestBuilder.montarApp() {
        application {
            install(ContentNegotiation) { json() }
            configurarErrores()
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(algoritmo).withIssuer("emisor").withAudience("audiencia").build())
                    validate { cred -> JWTPrincipal(cred.payload) }
                }
            }
            routing {
                route("/zona") {
                    soloAdmin { get("/admin") { call.respondText("admin ok") } }
                    // Ruta hermana con el mismo authenticate: no debe heredar la restricción de admin.
                    authenticate("auth-jwt") { get("/usuario") { call.respondText("usuario ok") } }
                    soloAdmin { get("/admin-2") { call.respondText("admin 2 ok") } }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.pedir(ruta: String, rol: String?): HttpResponse =
        client.get(ruta) { if (rol != null) bearerAuth(token(rol)) }

    @Test
    fun `sin token responde 401`() = testApplication {
        montarApp()
        assertEquals(HttpStatusCode.Unauthorized, pedir("/zona/admin", null).status)
    }

    @Test
    fun `usuario normal recibe 403 en rutas admin`() = testApplication {
        montarApp()
        assertEquals(HttpStatusCode.Forbidden, pedir("/zona/admin", "user").status)
        assertEquals(HttpStatusCode.Forbidden, pedir("/zona/admin-2", "user").status)
    }

    @Test
    fun `admin accede a todas las rutas admin`() = testApplication {
        montarApp()
        assertEquals(HttpStatusCode.OK, pedir("/zona/admin", "admin").status)
        assertEquals(HttpStatusCode.OK, pedir("/zona/admin-2", "admin").status)
    }

    @Test
    fun `ruta hermana de usuario no queda restringida a admin`() = testApplication {
        montarApp()
        assertEquals(HttpStatusCode.OK, pedir("/zona/usuario", "user").status)
    }
}
