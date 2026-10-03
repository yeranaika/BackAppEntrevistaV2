package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearCodigo
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.CompraGoogle
import MODELOS.TablaSuscripcion
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
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
import kotlin.test.assertFalse
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
