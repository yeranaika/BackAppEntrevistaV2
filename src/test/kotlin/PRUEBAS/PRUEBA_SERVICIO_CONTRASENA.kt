package PRUEBAS

import CONFIGURACION.INTENTOS_MAXIMOS_CODIGO_RECUPERACION
import CONFIGURACION.MINUTOS_VIGENCIA_CODIGO_RECUPERACION
import ERRORES.ErrorDemasiadosIntentos
import ERRORES.ErrorValidacion
import PRUEBAS.DOBLES.CodigosEnMemoria
import PRUEBAS.DOBLES.CorreoEnMemoria
import PRUEBAS.DOBLES.CuentasOAuthEnMemoria
import PRUEBAS.DOBLES.JWT_PRUEBA
import PRUEBAS.DOBLES.RefreshTokensEnMemoria
import PRUEBAS.DOBLES.RelojAjustable
import PRUEBAS.DOBLES.UsuariosEnMemoria
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioContrasena
import SERVICIOS.ServicioToken
import UTILIDADES.verificarContrasena
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruebaServicioContrasena {
    private val usuarios = UsuariosEnMemoria()
    private val cuentasOAuth = CuentasOAuthEnMemoria(usuarios)
    private val codigos = CodigosEnMemoria()
    private val correo = CorreoEnMemoria()
    private val refreshTokens = RefreshTokensEnMemoria()
    private val reloj = RelojAjustable()
    private val tokens = ServicioToken(refreshTokens, usuarios, JWT_PRUEBA, reloj)
    private val servicio = ServicioContrasena(
        usuarios, usuarios, cuentasOAuth, codigos, correo, tokens,
        // Unconfined: el "envío en segundo plano" termina antes de volver, así la prueba lo ve.
        CoroutineScope(Dispatchers.Unconfined), reloj
    )
    private val ana = usuarios.agregar("ana@ejemplo.com", "Clave-vieja-1")

    private fun solicitarCodigo(): String {
        runBlocking { servicio.solicitarRecuperacion("ana@ejemplo.com") }
        return codigos.ultimoCodigoDe(ana.id)
    }

    private fun codigoIncorrecto(codigo: String) = ((codigo.toInt() + 1) % 1_000_000).toString().padStart(6, '0')

    // ---------- Solicitar código ----------

    @Test
    fun `solicitar envia un codigo de 6 digitos al correo registrado`() {
        val codigo = solicitarCodigo()
        assertTrue(Regex("^[0-9]{6}$").matches(codigo))
        assertEquals(listOf(CorreoEnMemoria.Enviado("ana@ejemplo.com", "codigo", codigo)), correo.enviados)
    }

    @Test
    fun `solicitar con un correo no registrado no falla ni envia nada`() = runBlocking<Unit> {
        servicio.solicitarRecuperacion("nadie@ejemplo.com")
        assertTrue(correo.enviados.isEmpty())
        assertTrue(codigos.registros.isEmpty())
    }

    @Test
    fun `una cuenta de Google recibe un aviso en vez de codigo`() = runBlocking<Unit> {
        cuentasOAuth.vinculos["sub-ana"] = ana.id
        servicio.solicitarRecuperacion("ana@ejemplo.com")
        assertEquals("aviso_google", correo.enviados.single().tipo)
        assertTrue(codigos.registros.isEmpty())
    }

    @Test
    fun `solicitar valida el formato del correo`() {
        fallaCon<ErrorValidacion>("correo_invalido") { servicio.solicitarRecuperacion("no-es-correo") }
    }

    // ---------- Restablecer ----------

    @Test
    fun `restablecer con el codigo correcto cambia la contrasena y cierra las sesiones`() = runBlocking<Unit> {
        tokens.emitirPar(ana.id, ana.rol)
        val codigo = solicitarCodigo()

        servicio.restablecer(" ANA@ejemplo.com ", codigo, "Clave-nueva-1")

        assertTrue(verificarContrasena("Clave-nueva-1", usuarios.hashDe(ana.id)))
        assertEquals(0, refreshTokens.sesionesActivas(ana.id))
        // El código no se puede reutilizar.
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", codigo, "Clave-otra-1") }
    }

    @Test
    fun `restablecer con codigo incorrecto falla sin revelar si el correo existe`() {
        val codigo = solicitarCodigo()
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", codigoIncorrecto(codigo), "Clave-nueva-1") }
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("nadie@ejemplo.com", codigo, "Clave-nueva-1") }
    }

    @Test
    fun `tras el maximo de intentos fallidos el codigo deja de servir aunque sea correcto`() {
        val codigo = solicitarCodigo()
        repeat(INTENTOS_MAXIMOS_CODIGO_RECUPERACION - 1) {
            fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", codigoIncorrecto(codigo), "Clave-nueva-1") }
        }
        fallaCon<ErrorDemasiadosIntentos>("demasiados_intentos") {
            servicio.restablecer("ana@ejemplo.com", codigoIncorrecto(codigo), "Clave-nueva-1")
        }
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", codigo, "Clave-nueva-1") }
        assertTrue(verificarContrasena("Clave-vieja-1", usuarios.hashDe(ana.id)))
    }

    @Test
    fun `el codigo vence a los minutos configurados`() {
        val codigo = solicitarCodigo()
        reloj.adelantar(Duration.ofMinutes(MINUTOS_VIGENCIA_CODIGO_RECUPERACION).plusSeconds(1))
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", codigo, "Clave-nueva-1") }
    }

    @Test
    fun `pedir un codigo nuevo invalida el anterior`() {
        val primero = solicitarCodigo()
        val segundo = solicitarCodigo()
        if (primero != segundo) {
            fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", primero, "Clave-nueva-1") }
        }
        runBlocking { servicio.restablecer("ana@ejemplo.com", segundo, "Clave-nueva-1") }
    }

    @Test
    fun `restablecer valida el codigo y la contrasena nueva`() {
        fallaCon<ErrorValidacion>("codigo_invalido") { servicio.restablecer("ana@ejemplo.com", "12ab", "Clave-nueva-1") }
        fallaCon<ErrorValidacion>("contrasena_debil") { servicio.restablecer("ana@ejemplo.com", "123456", "corta") }
    }

    // ---------- Cambiar desde el perfil ----------

    @Test
    fun `cambiar exige la contrasena actual correcta`() = runBlocking<Unit> {
        fallaCon<ErrorValidacion>("contrasena_actual_incorrecta") { servicio.cambiar(ana.id, "otra-clave", "Clave-nueva-1") }

        servicio.cambiar(ana.id, "Clave-vieja-1", "Clave-nueva-1")
        assertTrue(verificarContrasena("Clave-nueva-1", usuarios.hashDe(ana.id)))
    }

    @Test
    fun `cambiar mantiene la sesion del dispositivo actual`() = runBlocking<Unit> {
        tokens.emitirPar(ana.id, ana.rol)
        servicio.cambiar(ana.id, "Clave-vieja-1", "Clave-nueva-1")
        assertEquals(1, refreshTokens.sesionesActivas(ana.id))
    }

    @Test
    fun `cambiar rechaza cuentas de Google y repetir la misma contrasena`() {
        fallaCon<ErrorValidacion>("contrasena_repetida") { servicio.cambiar(ana.id, "Clave-vieja-1", "Clave-vieja-1") }
        cuentasOAuth.vinculos["sub-ana"] = ana.id
        fallaCon<ErrorValidacion>("cuenta_google") { servicio.cambiar(ana.id, "Clave-vieja-1", "Clave-nueva-1") }
    }

    // ---------- Administrador ----------

    @Test
    fun `restablecer por admin cambia la contrasena y cierra las sesiones`() = runBlocking<Unit> {
        tokens.emitirPar(ana.id, ana.rol)
        servicio.restablecerPorAdmin(ana.id, "Clave-admin-1")
        assertTrue(verificarContrasena("Clave-admin-1", usuarios.hashDe(ana.id)))
        assertEquals(0, refreshTokens.sesionesActivas(ana.id))
    }
}
