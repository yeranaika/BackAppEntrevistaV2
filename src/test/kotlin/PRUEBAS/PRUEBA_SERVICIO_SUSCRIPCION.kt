package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearCodigo
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.CompraGoogle
import INTEGRACIONES.VerificadorCompraSimulado
import MODELOS.TablaSuscripcion
import MODELOS.compraDeOtraCuenta
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ConsultorCupoIa
import SERVICIOS.CupoIaMensual
import SERVICIOS.ServicioSuscripcion
import SERVICIOS.cuentaOfuscadaDe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioSuscripcion {
    private val sistema = SistemaPrueba()
    private val servicio = sistema.suscripcion

    private fun nuevoUsuario(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    private fun codigo(dias: Int = 30, maxUsos: Int = 1, expiracion: String? = null) = runBlocking {
        servicio.crearCodigo(SolicitudCrearCodigo(dias = dias, maxUsos = maxUsos, tipoLicencia = "prom", expiracion = expiracion)).codigo
    }

    /** Una compra activa de Google Play pagada por [cuenta] (su obfuscatedExternalAccountId; null si no lo trae). */
    private fun compraDe(cuenta: String?) =
        CompraGoogle(esValida = true, estaActiva = true, expiracion = Instant.now().plusSeconds(30L * 24 * 3600), cuentaOfuscada = cuenta)

    // ---------- Códigos ----------

    @Test
    fun `canjear un codigo activa premium y suma sus dias a lo que queda`() = runBlocking<Unit> {
        val ana = nuevoUsuario("ana@ejemplo.com")
        assertFalse(servicio.estado(ana).esPremium)

        val primero = servicio.canjearCodigo(ana, codigo(30).lowercase())
        assertTrue(primero.esPremium)
        val segundo = servicio.canjearCodigo(ana, codigo(10))
        val dias = Duration.between(primero.vigente!!.expiracion, segundo.vigente!!.expiracion).toDays()
        assertEquals(10, dias)
    }

    @Test
    fun `un codigo de un uso no se puede canjear dos veces`() {
        val codigo = codigo(maxUsos = 1)
        runBlocking { servicio.canjearCodigo(nuevoUsuario("ana@ejemplo.com"), codigo) }
        fallaCon<ErrorValidacion>("codigo_invalido_o_expirado") { servicio.canjearCodigo(nuevoUsuario("beto@ejemplo.com"), codigo) }
    }

    @Test
    fun `canjes simultaneos no superan el maximo de usos`() = runBlocking<Unit> {
        val codigo = codigo(maxUsos = 2)
        val usuarios = (1..6).map { nuevoUsuario("u$it@ejemplo.com") }

        val exitos = usuarios.map { id ->
            async(Dispatchers.IO) { runCatching { servicio.canjearCodigo(id, codigo) }.isSuccess }
        }.awaitAll().count { it }

        assertEquals(2, exitos)
    }

    @Test
    fun `un codigo vencido no sirve`() {
        val codigo = codigo(dias = 0, expiracion = Instant.now().minusSeconds(60).toString())
        fallaCon<ErrorValidacion>("codigo_invalido_o_expirado") { servicio.canjearCodigo(nuevoUsuario("ana@ejemplo.com"), codigo) }
    }

    @Test
    fun `crear codigos valida licencia, duracion y usos`() {
        fallaCon<ErrorValidacion>("licencia_invalida") { servicio.crearCodigo(SolicitudCrearCodigo(30, tipoLicencia = "VIP")) }
        fallaCon<ErrorValidacion>("duracion_requerida") { servicio.crearCodigo(SolicitudCrearCodigo(0, tipoLicencia = "PROM")) }
        fallaCon<ErrorValidacion>("max_usos_invalido") { servicio.crearCodigo(SolicitudCrearCodigo(30, maxUsos = 0, tipoLicencia = "PROM")) }
        fallaCon<ErrorValidacion>("expiracion_invalida") { servicio.crearCodigo(SolicitudCrearCodigo(30, tipoLicencia = "PROM", expiracion = "mañana")) }
        assertTrue(codigo().matches(Regex("^PROM-[A-Z2-9]{8}$")))
    }

    // ---------- Google Play ----------

    @Test
    fun `una compra de Google no puede activar premium en dos cuentas`() = runBlocking<Unit> {
        val ana = nuevoUsuario("ana@ejemplo.com")
        servicio.verificarCompraGoogle(ana, "premium_mensual", "token-de-pago-123")
        assertTrue(servicio.estado(ana).esPremium)
        // El mismo dueño puede volver a verificar (ej: reinstaló la app).
        servicio.verificarCompraGoogle(ana, "premium_mensual", "token-de-pago-123")

        fallaCon<ErrorConflicto>("compra_ya_registrada") {
            servicio.verificarCompraGoogle(nuevoUsuario("beto@ejemplo.com"), "premium_mensual", "token-de-pago-123")
        }
    }

    @Test
    fun `una compra invalida responde 400 y Google caido responde 503`() {
        val ana = nuevoUsuario("ana@ejemplo.com")
        sistema.verificadorCompras.compra = CompraGoogle(esValida = true, estaActiva = false, expiracion = null)
        fallaCon<ErrorValidacion>("compra_invalida") { servicio.verificarCompraGoogle(ana, "premium_mensual", "token") }

        // Antes una caída de Google se informaba como "compra no válida".
        sistema.verificadorCompras.error = ErrorServicioExterno("google_play_no_disponible")
        fallaCon<ErrorServicioExterno>("google_play_no_disponible") { servicio.verificarCompraGoogle(ana, "premium_mensual", "token") }
    }

    // ---------- Google Play: la cuenta que pagó (D27) ----------

    @Test
    fun `una compra pagada desde otra cuenta de la app no activa premium`() = runBlocking<Unit> {
        val ana = nuevoUsuario("ana@ejemplo.com")
        val beto = nuevoUsuario("beto@ejemplo.com")
        sistema.verificadorCompras.compra = compraDe(cuentaOfuscadaDe(beto))

        val error = assertFailsWith<ErrorConflicto> { runBlocking { servicio.verificarCompraGoogle(ana, "premium_mensual", "token-de-beto") } }
        assertEquals("compra_ya_registrada", error.codigo)
        assertEquals(compraDeOtraCuenta().message, error.message, "el mismo texto que cuando el token ya es de otra cuenta")
        assertFalse(servicio.estado(ana).esPremium)

        // El rechazo no dejó el token tomado por ana: su dueño lo activa.
        servicio.verificarCompraGoogle(beto, "premium_mensual", "token-de-beto")
        assertTrue(servicio.estado(beto).esPremium)
    }

    @Test
    fun `la cuenta ofuscada es el SHA-256 en hex del id igual que en la app`() {
        // Vector comprobado con sha256sum sobre "3f2504e0-4f89-11d3-9a0c-0305e82c3301": se hashea UUID.toString()
        // (minúsculas, como /me entrega el id), no el texto tal como llegó (en mayúsculas daría 4ce2fd61…).
        val id = UUID.fromString("3F2504E0-4F89-11D3-9A0C-0305E82C3301")
        assertEquals("d1bfaf4aff653cb27984b7d978e51a7d406d1572df95d205c254beb18dc134d3", cuentaOfuscadaDe(id))
    }

    @Test
    fun `la compra con la cuenta propia activa premium aunque Google la devuelva en mayusculas`() = runBlocking<Unit> {
        val ana = nuevoUsuario("ana@ejemplo.com")
        sistema.verificadorCompras.compra = compraDe(cuentaOfuscadaDe(ana).uppercase())
        servicio.verificarCompraGoogle(ana, "premium_mensual", "token-de-ana")
        assertTrue(servicio.estado(ana).esPremium)
    }

    @Test
    fun `una compra sin cuenta ofuscada sigue funcionando y decide el dueno del token`() = runBlocking<Unit> {
        assertNull(VerificadorCompraSimulado().verificar("premium_mensual", "token").cuentaOfuscada, "el simulado no la trae")
        val ana = nuevoUsuario("ana@ejemplo.com")
        sistema.verificadorCompras.compra = compraDe(null) // desde la tienda de Play o una app antigua
        servicio.verificarCompraGoogle(ana, "premium_mensual", "token-sin-cuenta")
        assertTrue(servicio.estado(ana).esPremium)
        fallaCon<ErrorConflicto>("compra_ya_registrada") {
            servicio.verificarCompraGoogle(nuevoUsuario("beto@ejemplo.com"), "premium_mensual", "token-sin-cuenta")
        }
    }

    @Test
    fun `una compra inactiva de otra cuenta responde compra_invalida`() {
        val ana = nuevoUsuario("ana@ejemplo.com")
        val beto = nuevoUsuario("beto@ejemplo.com")
        sistema.verificadorCompras.compra = CompraGoogle(esValida = true, estaActiva = false, expiracion = null, cuentaOfuscada = cuentaOfuscadaDe(beto))
        fallaCon<ErrorValidacion>("compra_invalida") { servicio.verificarCompraGoogle(ana, "premium_mensual", "token") }
    }

    // ---------- Reportes con IA que le quedan al premium (D29) ----------

    private val renovacion = Instant.parse("2026-02-01T00:00:00Z")

    /** Suscripción real sobre H2 con un contador de cupo de prueba que registra cuántas veces se le consultó. */
    private inner class ConCupo(private val respuesta: suspend () -> CupoIaMensual?) {
        var consultas = 0
        val servicio = ServicioSuscripcion(sistema.suscripciones, sistema.verificadorCompras, cupoIa = ConsultorCupoIa {
            consultas++
            respuesta()
        })
    }

    @Test
    fun `a un usuario gratis no se le consulta el cupo`() = runBlocking<Unit> {
        val conCupo = ConCupo { CupoIaMensual(30, 0, renovacion) }
        val estado = conCupo.servicio.estadoConCupoIa(nuevoUsuario("ana@ejemplo.com"))
        assertFalse(estado.esPremium)
        assertNull(estado.cupoIa)
        assertEquals(0, conCupo.consultas)
    }

    @Test
    fun `canjear un codigo devuelve el estado con el cupo y el estado de los reportes no lo consulta`() = runBlocking<Unit> {
        val conCupo = ConCupo { CupoIaMensual(30, 12, renovacion) }
        val ana = nuevoUsuario("ana@ejemplo.com")

        val canje = conCupo.servicio.canjearCodigo(ana, codigo())

        assertTrue(canje.esPremium)
        assertEquals(CupoIaMensual(30, 12, renovacion), canje.cupoIa)
        assertEquals(18, canje.cupoIa!!.restantes)
        assertNull(conCupo.servicio.estado(ana).cupoIa, "estado() lo usa cada reporte: sin consulta extra")
        assertEquals(1, conCupo.consultas)
    }

    @Test
    fun `si el cupo no se puede leer el estado sale premium sin el`() = runBlocking<Unit> {
        val conCupo = ConCupo { error("BD caída") }
        val ana = nuevoUsuario("ana@ejemplo.com")
        servicio.canjearCodigo(ana, codigo())

        val estado = conCupo.servicio.estadoConCupoIa(ana)

        assertTrue(estado.esPremium)
        assertNull(estado.cupoIa)
    }

    @Test
    fun `una cancelacion al leer el cupo no se trata como falla`() {
        val conCupo = ConCupo { throw CancellationException("pedido cancelado") }
        val ana = nuevoUsuario("ana@ejemplo.com")
        runBlocking { servicio.canjearCodigo(ana, codigo()) }
        assertFailsWith<CancellationException> { runBlocking { conCupo.servicio.estadoConCupoIa(ana) } }
    }

    @Test
    fun `el estado considera la suscripcion activa aunque haya otra mas reciente vencida`() = runBlocking<Unit> {
        val ana = nuevoUsuario("ana@ejemplo.com")
        servicio.canjearCodigo(ana, codigo(30))
        transaction {
            TablaSuscripcion.insert {
                it[usuarioId] = ana
                it[plan] = "premium_mensual"
                it[proveedor] = "google_play"
                it[estado] = "vencida"
                it[fechaInicio] = Instant.now().plusSeconds(60)
                it[fechaExpiracion] = Instant.now().minusSeconds(60)
            }
        }
        val estado = servicio.estado(ana)
        assertTrue(estado.esPremium, "antes solo se miraba la más reciente")
        assertEquals("premium", estado.vigente!!.plan)
    }
}
