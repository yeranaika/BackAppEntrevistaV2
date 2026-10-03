package PRUEBAS

import CONFIGURACION.DIAS_VIGENCIA_REFRESH_TOKEN
import CONFIGURACION.TTL_TOKEN_ACCESO_SEGUNDOS
import ERRORES.ErrorNoAutorizado
import ERRORES.ErrorProhibido
import ERRORES.ErrorValidacion
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.JWT_PRUEBA
import PRUEBAS.DOBLES.RefreshTokensEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import PRUEBAS.DOBLES.UsuariosEnMemoria
import SERVICIOS.ServicioToken
import com.auth0.jwt.JWT
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PruebaServicioToken {
    private val usuarios = UsuariosEnMemoria()
    private val repositorio = RefreshTokensEnMemoria()
    private val reloj = RelojAjustable()
    private val servicio = ServicioToken(repositorio, usuarios, JWT_PRUEBA, reloj)
    private val usuario = usuarios.agregar("ana@ejemplo.com", "Clave-segura-1")

    @Test
    fun `emitirPar firma un JWT con sujeto, rol, emisor, audiencia y vida corta`() = runBlocking<Unit> {
        val par = servicio.emitirPar(usuario.id, usuario.rol)
        // El reloj de la prueba está fijo en el pasado: se valida firma y claims sin el chequeo de vencimiento.
        val jwt = JWT.decode(par.tokenAcceso)
        JWT_PRUEBA.algoritmo.verify(jwt)

        assertEquals(JWT_PRUEBA.emisor, jwt.issuer)
        assertEquals(listOf(JWT_PRUEBA.audiencia), jwt.audience)
        assertEquals(usuario.id.toString(), jwt.subject)
        assertEquals("user", jwt.getClaim("role").asString())
        assertEquals(TTL_TOKEN_ACCESO_SEGUNDOS.toLong(), (jwt.expiresAt.time - jwt.issuedAt.time) / 1000)
    }

    @Test
    fun `el refresh token se guarda solo como hash`() = runBlocking<Unit> {
        val par = servicio.emitirPar(usuario.id, usuario.rol)
        assertEquals(1, repositorio.tokens.size)
        assertFalse(par.tokenRefresco in repositorio.tokens.keys)
    }

    @Test
    fun `rotar entrega un par nuevo y el anterior deja de servir`() = runBlocking<Unit> {
        val original = servicio.emitirPar(usuario.id, usuario.rol)
        val rotado = servicio.rotar(original.tokenRefresco)

        assertNotEquals(original.tokenRefresco, rotado.tokenRefresco)
        servicio.rotar(rotado.tokenRefresco)
    }

    @Test
    fun `reutilizar un refresh ya rotado revoca todas las sesiones del usuario`() {
        val original = runBlocking { servicio.emitirPar(usuario.id, usuario.rol) }
        val rotado = runBlocking { servicio.rotar(original.tokenRefresco) }

        fallaCon<ErrorNoAutorizado>("invalid_refresh") { servicio.rotar(original.tokenRefresco) }
        // El token legítimo más nuevo también queda revocado: quien lo robó no puede seguir.
        fallaCon<ErrorNoAutorizado>("invalid_refresh") { servicio.rotar(rotado.tokenRefresco) }
    }

    @Test
    fun `rotar vuelve a leer el rol del usuario`() = runBlocking<Unit> {
        val par = servicio.emitirPar(usuario.id, usuario.rol)
        usuarios.actualizar(usuario.id) { it.copy(rol = ROL_ADMIN) }

        val rotado = servicio.rotar(par.tokenRefresco)
        assertEquals("admin", JWT.decode(rotado.tokenAcceso).getClaim("role").asString())
    }

    @Test
    fun `rotar falla si la cuenta fue desactivada`() {
        val par = runBlocking { servicio.emitirPar(usuario.id, usuario.rol) }
        usuarios.actualizar(usuario.id) { it.copy(estaActivo = false) }
        fallaCon<ErrorProhibido>("inactive_user") { servicio.rotar(par.tokenRefresco) }
    }

    @Test
    fun `rotar falla con un refresh vencido`() {
        val par = runBlocking { servicio.emitirPar(usuario.id, usuario.rol) }
        reloj.adelantar(Duration.ofDays(DIAS_VIGENCIA_REFRESH_TOKEN).plusSeconds(1))
        fallaCon<ErrorNoAutorizado>("invalid_refresh") { servicio.rotar(par.tokenRefresco) }
    }

    @Test
    fun `rotar valida el token recibido`() {
        fallaCon<ErrorValidacion>("missing_refresh") { servicio.rotar("   ") }
        fallaCon<ErrorNoAutorizado>("invalid_refresh") { servicio.rotar("token-que-no-existe") }
    }

    @Test
    fun `revocar cierra la sesion y es idempotente`() {
        val par = runBlocking { servicio.emitirPar(usuario.id, usuario.rol) }
        runBlocking {
            servicio.revocar(par.tokenRefresco)
            servicio.revocar(par.tokenRefresco)
            servicio.revocar("token-que-no-existe")
        }
        assertTrue(repositorio.tokens.values.all { it.estaRevocado })
        fallaCon<ErrorNoAutorizado>("invalid_refresh") { servicio.rotar(par.tokenRefresco) }
    }
}
