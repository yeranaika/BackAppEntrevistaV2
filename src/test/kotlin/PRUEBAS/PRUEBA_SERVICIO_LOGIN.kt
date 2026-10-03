package PRUEBAS

import ERRORES.ErrorAplicacion
import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorProhibido
import INTEGRACIONES.IdentidadGoogle
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.CuentasOAuthEnMemoria
import PRUEBAS.DOBLES.GoogleEnMemoria
import PRUEBAS.DOBLES.JWT_PRUEBA
import PRUEBAS.DOBLES.RefreshTokensEnMemoria
import PRUEBAS.DOBLES.UsuariosEnMemoria
import SERVICIOS.ServicioLogin
import SERVICIOS.ServicioToken
import com.auth0.jwt.JWT
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PruebaServicioLogin {
    private val usuarios = UsuariosEnMemoria()
    private val cuentasOAuth = CuentasOAuthEnMemoria(usuarios)
    private val identidades = mutableMapOf<String, IdentidadGoogle>()
    private val servicio = ServicioLogin(
        usuarios = usuarios,
        cuentasOAuth = cuentasOAuth,
        verificadorGoogle = GoogleEnMemoria(identidades),
        tokens = ServicioToken(RefreshTokensEnMemoria(), usuarios, JWT_PRUEBA)
    )

    private inline fun <reified T : ErrorAplicacion> fallaCon(codigo: String, noinline bloque: suspend () -> Unit) {
        val error = assertFailsWith<T> { runBlocking { bloque() } }
        assertEquals(codigo, error.codigo)
    }

    private fun rolDe(tokenAcceso: String) = JWT.decode(tokenAcceso).getClaim("role").asString()

    // ---------- Correo y contraseña ----------

    @Test
    fun `login correcto normaliza el correo, registra el acceso y firma el rol`() = runBlocking<Unit> {
        val admin = usuarios.agregar("admin@ejemplo.com", "Clave-admin-1", rol = ROL_ADMIN)

        val par = servicio.iniciarSesion("  Admin@Ejemplo.COM ", "Clave-admin-1")

        assertEquals("admin", rolDe(par.tokenAcceso))
        assertEquals(listOf(admin.id), usuarios.ultimosLogin)
    }

    @Test
    fun `contrasena incorrecta o correo inexistente responden igual`() {
        usuarios.agregar("ana@ejemplo.com", "Clave-segura-1")
        fallaCon<ErrorNoAutorizado>("bad_credentials") { servicio.iniciarSesion("ana@ejemplo.com", "otra-clave") }
        fallaCon<ErrorNoAutorizado>("bad_credentials") { servicio.iniciarSesion("nadie@ejemplo.com", "Clave-segura-1") }
    }

    @Test
    fun `cuenta inactiva solo se revela con la contrasena correcta`() {
        usuarios.agregar("ana@ejemplo.com", "Clave-segura-1", estaActivo = false)
        fallaCon<ErrorNoAutorizado>("bad_credentials") { servicio.iniciarSesion("ana@ejemplo.com", "otra-clave") }
        fallaCon<ErrorProhibido>("inactive_user") { servicio.iniciarSesion("ana@ejemplo.com", "Clave-segura-1") }
    }

    // ---------- Google ----------

    @Test
    fun `google rechaza un idToken invalido`() {
        fallaCon<ErrorNoAutorizado>("invalid_google_token") { servicio.iniciarSesionConGoogle("no-es-un-token") }
    }

    @Test
    fun `google rechaza un correo sin verificar`() {
        identidades["token-sin-verificar"] = IdentidadGoogle("sub-1", "ana@gmail.com", correoVerificado = false)
        fallaCon<ErrorNoAutorizado>("google_email_not_verified") { servicio.iniciarSesionConGoogle("token-sin-verificar") }
    }

    @Test
    fun `google crea la cuenta la primera vez y reutiliza el vinculo despues`() = runBlocking<Unit> {
        identidades["token-ana"] = IdentidadGoogle("sub-ana", "Ana@Gmail.com", correoVerificado = true)

        servicio.iniciarSesionConGoogle("token-ana")
        servicio.iniciarSesionConGoogle("token-ana")

        val creado = assertNotNull(usuarios.buscarPorCorreo("ana@gmail.com"))
        assertEquals(1, usuarios.usuarios.size)
        assertEquals(listOf(creado.id, creado.id), usuarios.ultimosLogin)
    }

    @Test
    fun `google respeta el rol de una cuenta existente`() = runBlocking<Unit> {
        usuarios.agregar("admin@gmail.com", "Clave-admin-1", rol = ROL_ADMIN)
        identidades["token-admin"] = IdentidadGoogle("sub-admin", "admin@gmail.com", correoVerificado = true)

        assertEquals("admin", rolDe(servicio.iniciarSesionConGoogle("token-admin").tokenAcceso))
    }

    @Test
    fun `google no permite entrar a una cuenta inactiva`() {
        usuarios.agregar("bloqueada@gmail.com", "Clave-segura-1", estaActivo = false)
        identidades["token-bloqueada"] = IdentidadGoogle("sub-b", "bloqueada@gmail.com", correoVerificado = true)
        fallaCon<ErrorProhibido>("inactive_user") { servicio.iniciarSesionConGoogle("token-bloqueada") }
    }
}
