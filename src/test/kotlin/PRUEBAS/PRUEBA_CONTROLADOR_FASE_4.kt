package PRUEBAS

import CONFIGURACION.ConfiguracionLimites
import MODELOS.TablaConsentimiento
import ESQUEMAS.SolicitudRegistro
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.SistemaPrueba
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.request.header
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las claves que la app Android lee hoy de /billing/status: a un usuario gratis le llegan estas y ninguna más (D29). */
private val CLAVES_ESTADO_SUSCRIPCION = setOf("is_premium", "plan", "source", "status", "start_at", "expires_at")

/** Contrato HTTP de mercado, consentimientos, recordatorios, billing, límites y salud (rutas que usa Android). */
class PruebaControladorFase4 {
    private val json = Json { ignoreUnknownKeys = true }

    private fun tokenDe(sistema: SistemaPrueba, correo: String, rol: String? = null): String = runBlocking {
        val par = sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        if (rol == null) return@runBlocking par.tokenAcceso
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.usuarios.actualizarRol(id, rol)
        sistema.tokens.emitirPar(id, rol).tokenAcceso
    }

    private suspend fun ApplicationTestBuilder.enviar(metodo: String, ruta: String, cuerpo: String? = null, token: String? = null): HttpResponse {
        val configurar: HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            cuerpo?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        return when (metodo) {
            "GET" -> client.get(ruta, configurar)
            "PUT" -> client.put(ruta, configurar)
            else -> client.post(ruta, configurar)
        }
    }

    private fun HttpResponse.objeto(): JsonObject = runBlocking { json.parseToJsonElement(bodyAsText()).jsonObject }
    private fun HttpResponse.lista() = runBlocking { json.parseToJsonElement(bodyAsText()).jsonArray }
    private fun JsonObject.texto(campo: String) = this[campo]?.jsonPrimitive?.content

    // ---------- Mercado ----------

    @Test
    fun `el catalogo es publico, con alias antiguos, y la administracion es solo para admin`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val cargo = """{"nombre":"Backend Developer","area":"backend","autoGenerateSkills":false}"""

        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/admin/market/cargos", cargo).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/admin/market/cargos", cargo, usuario).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/admin/market/sync-trends", token = usuario).status)

        val creado = enviar("POST", "/admin/market/cargos", cargo, admin)
        assertEquals(HttpStatusCode.Created, creado.status)
        val id = creado.objeto()["cargo"]!!.jsonObject.texto("cargoId")!!
        assertEquals(HttpStatusCode.Conflict, enviar("POST", "/admin/market/cargos", cargo, admin).status)

        assertEquals(1, enviar("GET", "/api/v1/cargos").lista().size)
        assertEquals(1, enviar("GET", "/market/cargos").lista().size)
        assertEquals(HttpStatusCode.OK, enviar("GET", "/api/v1/cargos/$id/skills").status)
        assertEquals(0, enviar("GET", "/market/cargos/$id/skills").lista().size)
        assertEquals("cargo_not_found", enviar("GET", "/api/v1/cargos/00000000-0000-0000-0000-000000000000/skills").objeto().texto("error"))
        assertEquals(HttpStatusCode.BadRequest, enviar("GET", "/api/v1/cargos/no-es-uuid/skills").status)
        assertEquals("limite_invalido", enviar("GET", "/api/v1/skills/trending?limit=0").objeto().texto("error"))
    }

    // ---------- Consentimiento ----------

    @Test
    fun `flujo de consentimiento que usa Android`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        runBlocking { sistema.consentimiento.publicar("v1.1", "Consentimiento de uso de datos", "Texto", listOf("estadisticas_uso", "marketing", "analisis_conducta_camara")) }

        val vigente = enviar("GET", "/consent/current").objeto()
        val version = vigente.texto("version")!!
        assertEquals("v1.1", version)
        assertEquals(HttpStatusCode.NoContent, enviar("GET", "/me/consent/latest", token = ana).status)
        assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/me/consent", "{}").status)

        // Lo que manda la app, más claves que la versión no ofrece ("analytics" premarcado de la app vieja): se descartan (D35).
        val otorgado = enviar("POST", "/me/consent", """{"version":"$version","alcances":{"estadisticas_uso":true,"marketing":false,"analytics":true,"ia_entrenamiento":true}}""", ana)
        assertEquals(HttpStatusCode.Created, otorgado.status)
        val alcances = otorgado.objeto()["alcances"]!!.jsonObject.mapValues { it.value.jsonPrimitive.boolean }
        assertEquals(mapOf("estadisticas_uso" to true, "marketing" to false, "analisis_conducta_camara" to false), alcances)
        // La app vieja (solo "analytics" premarcado) acepta igual, pero no queda ninguna estadística encendida.
        val vieja = enviar("POST", "/me/consent", """{"version":"$version","alcances":{"analytics":true,"marketing":false}}""", ana)
        assertEquals(HttpStatusCode.Created, vieja.status)
        assertEquals(false, vieja.objeto()["alcances"]!!.jsonObject["estadisticas_uso"]!!.jsonPrimitive.boolean)
        assertEquals(null, vieja.objeto()["alcances"]!!.jsonObject["analytics"])
        assertEquals(version, enviar("GET", "/me/consent/latest", token = ana).objeto().texto("version"))

        assertEquals("version_no_encontrada", enviar("POST", "/me/consent", """{"version":"9.9.9","alcances":{"uso_datos":true}}""", ana).objeto().texto("error"))
        assertEquals("true", enviar("POST", "/me/consent/revoke", token = ana).objeto().texto("revoked"))
        assertEquals(HttpStatusCode.NotFound, enviar("POST", "/me/consent/revoke", token = ana).status)
    }

    /** Otorga el consentimiento de [token] enviando [ipEnCabecera] en X-Real-IP y devuelve la ip_origen guardada. */
    private suspend fun ApplicationTestBuilder.ipGuardadaAlConsentir(token: String, ipEnCabecera: String): String? {
        val version = enviar("GET", "/consent/current").objeto().texto("version")!!
        val respuesta = client.post("/me/consent") {
            bearerAuth(token)
            header("X-Real-IP", ipEnCabecera)
            contentType(ContentType.Application.Json)
            setBody("""{"version":"$version","alcances":{"uso_datos":true}}""")
        }
        assertEquals(HttpStatusCode.Created, respuesta.status)
        return transaction { TablaConsentimiento.selectAll().single()[TablaConsentimiento.ipOrigen] }
    }

    @Test
    fun `el consentimiento guarda la IP real del cliente con la cabecera de confianza`() = testApplication {
        val sistema = SistemaPrueba(limites = ConfiguracionLimites(1_000, 1_000, cabeceraIpCliente = "X-Real-IP"))
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        assertEquals("203.0.113.9", ipGuardadaAlConsentir(ana, "203.0.113.9"), "la del cliente, no la del proxy")
    }

    @Test
    fun `sin cabecera de confianza el consentimiento ignora la IP que manda el cliente`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val guardada = ipGuardadaAlConsentir(ana, "198.51.100.77")
        assertTrue(guardada != "198.51.100.77", "una cabecera inventada no es evidencia: se guarda la conexión ($guardada)")
        assertTrue(!guardada.isNullOrBlank())
    }

    @Test
    fun `documentos legales publicos y publicacion solo para admin`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val usuario = tokenDe(sistema, "ana@ejemplo.com")
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)

        assertEquals("terms", enviar("GET", "/api/v1/legal/terms").objeto().texto("type"))
        val privacidad = enviar("GET", "/api/v1/legal/privacy").objeto()
        assertEquals("privacy", privacidad.texto("type"))
        assertEquals("1.2.0", privacidad.texto("version"))
        assertTrue(privacidad.texto("contentMarkdown")!!.contains("**Versión:** 1.2.0"), "la versión de la API y la del archivo coinciden")
        assertEquals("1.1.0", enviar("GET", "/api/v1/legal/terms").objeto().texto("version"))
        assertTrue(enviar("GET", "/api/v1/legal/eula").objeto().texto("contentMarkdown")!!.isNotBlank())

        val nueva = """{"version":"2.0.0","title":"EULA v2","body":"Texto"}"""
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/api/v1/legal/admin/eula", nueva, usuario).status)
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/admin/consent/text", nueva, usuario).status)
        assertEquals(HttpStatusCode.Created, enviar("POST", "/admin/consent/text", nueva, admin).status)
        assertEquals("2.0.0", enviar("GET", "/consent/current").objeto().texto("version"))
        assertEquals(2, enviar("GET", "/api/v1/legal/versions").lista().size)

        // Una vez aceptada, la 2.0.0 no se puede cambiar: 409 (D35). Republicarla igual sí.
        assertEquals(HttpStatusCode.Created, enviar("POST", "/me/consent", """{"version":"2.0.0","alcances":{"analytics":true}}""", usuario).status)
        val cambiada = enviar("POST", "/api/v1/legal/admin/eula", """{"version":"2.0.0","title":"EULA v2","body":"Texto cambiado"}""", admin)
        assertEquals(HttpStatusCode.Conflict, cambiada.status)
        assertEquals("version_con_aceptaciones", cambiada.objeto().texto("error"))
        assertEquals(HttpStatusCode.Created, enviar("POST", "/admin/consent/text", nueva, admin).status)
    }

    // ---------- Recordatorios ----------

    @Test
    fun `preferencias de recordatorio`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val ruta = "/recordatorios/preferencias"

        assertEquals("recordatorio_no_configurado", enviar("GET", ruta, token = ana).objeto().texto("error"))
        val guardado = enviar("PUT", ruta, """{"diasSemana":["LUN","MIE","VIE"],"hora":"20:30","tipoPractica":"entrevista","habilitado":true}""", ana)
        assertEquals(HttpStatusCode.OK, guardado.status)
        assertEquals(3, enviar("GET", ruta, token = ana).objeto()["diasSemana"]!!.jsonArray.size)
        assertEquals("hora_invalida", enviar("PUT", ruta, """{"diasSemana":["LUN"],"hora":"99:99","tipoPractica":"x"}""", ana).objeto().texto("error"))
    }

    // ---------- Billing ----------

    @Test
    fun `billing con el JSON snake_case de Android`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val beto = tokenDe(sistema, "beto@ejemplo.com")
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)

        val gratis = enviar("GET", "/billing/status", token = ana).objeto()
        assertEquals("false", gratis.texto("is_premium"))
        assertEquals(CLAVES_ESTADO_SUSCRIPCION, gratis.keys, "a un usuario gratis no le llega ninguna clave reportes_ia_*")

        val pedido = """{"days":30,"max_uses":1,"license_type":"PROM"}"""
        assertEquals(HttpStatusCode.Forbidden, enviar("POST", "/billing/admin/codes", pedido, ana).status)
        val codigo = enviar("POST", "/billing/admin/codes", pedido, admin).also { assertEquals(HttpStatusCode.Created, it.status) }.objeto().texto("code")!!

        val canje = enviar("POST", "/billing/code/redeem", """{"code":"$codigo"}""", ana).objeto()
        assertEquals("true", canje.texto("is_premium"))
        assertEquals("interna", canje.texto("source"))
        assertEquals("codigo_invalido_o_expirado", enviar("POST", "/billing/code/redeem", """{"code":"$codigo"}""", beto).objeto().texto("error"))

        val compra = """{"product_id":"premium_mensual","purchase_token":"tok-1","purchase_time":1}"""
        assertEquals("premium_active", enviar("POST", "/billing/google/verify", compra, beto).objeto().texto("status"))
        assertEquals(HttpStatusCode.Conflict, enviar("POST", "/billing/google/verify", compra, ana).status)

        // D27: Google dice que la compra la pagó beto (obfuscatedExternalAccountId = hash de su cuenta): ana no la activa.
        val idBeto = runBlocking { sistema.usuarios.buscarPorCorreo("beto@ejemplo.com")!!.id }
        sistema.verificadorCompras.compra = INTEGRACIONES.CompraGoogle(
            esValida = true, estaActiva = true, expiracion = java.time.Instant.now().plusSeconds(86_400), cuentaOfuscada = SERVICIOS.cuentaOfuscadaDe(idBeto)
        )
        val deOtraCuenta = enviar("POST", "/billing/google/verify", compra.replace("tok-1", "tok-2"), ana)
        assertEquals(HttpStatusCode.Conflict, deOtraCuenta.status)
        assertEquals("compra_ya_registrada", deOtraCuenta.objeto().texto("error"))
    }

    @Test
    fun `billing informa al premium los reportes con IA que le quedan`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val ana = tokenDe(sistema, "ana@ejemplo.com")
        val admin = tokenDe(sistema, "admin@ejemplo.com", ROL_ADMIN)
        val codigo = enviar("POST", "/billing/admin/codes", """{"days":30,"max_uses":1,"license_type":"PROM"}""", admin).objeto().texto("code")!!
        // El reloj de prueba está en enero de 2026: el cupo se renueva el 2026-02-01T00:00:00Z.
        val febrero = 1_769_904_000_000L

        val canje = enviar("POST", "/billing/code/redeem", """{"code":"$codigo"}""", ana).objeto()
        val estado = enviar("GET", "/billing/status", token = ana).objeto()

        listOf(canje, estado).forEach { cuerpo ->
            assertEquals("true", cuerpo.texto("is_premium"))
            assertEquals(30, cuerpo["reportes_ia_limite"]!!.jsonPrimitive.int)
            assertEquals(30, cuerpo["reportes_ia_restantes"]!!.jsonPrimitive.int)
            assertEquals(febrero, cuerpo["reportes_ia_renovacion"]!!.jsonPrimitive.long)
        }
        assertEquals("Código canjeado; tu cuenta ya es premium", canje.texto("mensaje"))
        assertEquals(CLAVES_ESTADO_SUSCRIPCION + setOf("reportes_ia_limite", "reportes_ia_restantes", "reportes_ia_renovacion"), estado.keys)
        assertEquals(HttpStatusCode.Unauthorized, enviar("GET", "/billing/status").status)
    }

    // ---------- Límites y bloqueo ----------

    @Test
    fun `superar el limite por IP responde 429 con el formato estandar`() = testApplication {
        val sistema = SistemaPrueba(limites = ConfiguracionLimites(registrosPorIp = 2, recuperacionesPorIp = 1))
        application { sistema.montar(this) }

        repeat(2) { assertEquals(HttpStatusCode.Created, enviar("POST", "/auth/register", """{"email":"u$it@ejemplo.com","password":"Clave-segura-1"}""").status) }
        val bloqueado = enviar("POST", "/auth/register", """{"email":"u9@ejemplo.com","password":"Clave-segura-1"}""")
        assertEquals(HttpStatusCode.TooManyRequests, bloqueado.status)
        assertEquals("demasiadas_solicitudes", bloqueado.objeto().texto("error"))

        assertEquals(HttpStatusCode.OK, enviar("POST", "/auth/forgot-password", """{"correo":"a@ejemplo.com"}""").status)
        assertEquals(HttpStatusCode.TooManyRequests, enviar("POST", "/auth/forgot-password", """{"correo":"a@ejemplo.com"}""").status)
    }

    @Test
    fun `el bloqueo de login conserva su propio codigo de error`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        tokenDe(sistema, "ana@ejemplo.com")
        val mala = """{"email":"ana@ejemplo.com","password":"mala"}"""

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, enviar("POST", "/auth/login", mala).status) }
        val bloqueado = enviar("POST", "/auth/login", """{"email":"ana@ejemplo.com","password":"Clave-segura-1"}""")
        assertEquals(HttpStatusCode.TooManyRequests, bloqueado.status)
        // El handler de status(429) del rate limit no debe pisar este cuerpo.
        assertEquals("demasiados_intentos", bloqueado.objeto().texto("error"))
    }

    @Test
    fun `salud responde OK si la base de datos responde`() = testApplication {
        val sistema = SistemaPrueba()
        application { sistema.montar(this) }
        val salud = enviar("GET", "/health")
        assertEquals(HttpStatusCode.OK, salud.status)
        val cuerpo = salud.objeto()
        assertEquals("OK", cuerpo.texto("status"))
        assertEquals(CONFIGURACION.VERSION_MODELO_DIVLARA, cuerpo["modeloDivlara"]?.jsonObject?.texto("version"))
    }
}
