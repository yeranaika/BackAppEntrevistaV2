package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearUsuarioAdmin
import MODELOS.ESTADO_ACTIVO
import MODELOS.ESTADO_INACTIVO
import MODELOS.ROL_ADMIN
import PRUEBAS.DOBLES.CodigosEnMemoria
import PRUEBAS.DOBLES.CorreoEnMemoria
import PRUEBAS.DOBLES.CuentasOAuthEnMemoria
import PRUEBAS.DOBLES.JWT_PRUEBA
import PRUEBAS.DOBLES.RefreshTokensEnMemoria
import PRUEBAS.DOBLES.UsuariosEnMemoria
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioAdminUsuario
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PruebaServicioAdminUsuario {
    private val usuarios = UsuariosEnMemoria()
    private val refreshTokens = RefreshTokensEnMemoria()
    private val tokens = ServicioToken(refreshTokens, usuarios, JWT_PRUEBA)
    private val contrasenas = ServicioContrasena(
        usuarios, usuarios, CuentasOAuthEnMemoria(usuarios), CodigosEnMemoria(), CorreoEnMemoria(), tokens,
        CoroutineScope(Dispatchers.Unconfined)
    )
    private val servicio = ServicioAdminUsuario(usuarios, contrasenas, tokens)
    private val admin = usuarios.agregar("admin@ejemplo.com", "Clave-admin-1", rol = ROL_ADMIN)
    private val ana = usuarios.agregar("ana@ejemplo.com", "Clave-segura-1")

    @Test
    fun `crear normaliza el correo y valida rol y contrasena`() = runBlocking<Unit> {
        val creado = servicio.crear(SolicitudCrearUsuarioAdmin(" Nuevo@Ejemplo.com ", "Clave-segura-1", rol = "ADMIN"))
        assertEquals("nuevo@ejemplo.com", creado.correo)
        assertEquals("admin", creado.rol)

        fallaCon<ErrorValidacion>("rol_invalido") { servicio.crear(SolicitudCrearUsuarioAdmin("b@ejemplo.com", "Clave-segura-1", rol = "root")) }
        fallaCon<ErrorValidacion>("weak_password") { servicio.crear(SolicitudCrearUsuarioAdmin("b@ejemplo.com", "123456")) }
        fallaCon<ErrorConflicto>("email_in_use") { servicio.crear(SolicitudCrearUsuarioAdmin("ana@ejemplo.com", "Clave-segura-1")) }
    }

    @Test
    fun `un admin no puede quitarse el rol ni desactivarse a si mismo`() {
        fallaCon<ErrorValidacion>("no_puede_quitarse_admin") { servicio.cambiarRol(admin.id, admin.id, "user") }
        fallaCon<ErrorValidacion>("no_puede_desactivarse") { servicio.desactivar(admin.id, admin.id) }
    }

    @Test
    fun `desactivar cierra las sesiones del usuario`() = runBlocking<Unit> {
        tokens.emitirPar(ana.id, ana.rol)
        servicio.desactivar(admin.id, ana.id)

        assertEquals(ESTADO_INACTIVO, usuarios.buscarPorId(ana.id)!!.estado)
        assertEquals(0, refreshTokens.sesionesActivas(ana.id))

        servicio.activar(ana.id)
        assertEquals(ESTADO_ACTIVO, usuarios.buscarPorId(ana.id)!!.estado)
    }

    @Test
    fun `acciones sobre un usuario inexistente responden user_not_found`() {
        val nadie = UUID.randomUUID()
        fallaCon<ErrorNoEncontrado>("user_not_found") { servicio.cambiarRol(admin.id, nadie, "admin") }
        fallaCon<ErrorNoEncontrado>("user_not_found") { servicio.desactivar(admin.id, nadie) }
        fallaCon<ErrorNoEncontrado>("user_not_found") { servicio.activar(nadie) }
        fallaCon<ErrorNoEncontrado>("user_not_found") { servicio.restablecerContrasena(nadie, "Clave-segura-1") }
    }
}
