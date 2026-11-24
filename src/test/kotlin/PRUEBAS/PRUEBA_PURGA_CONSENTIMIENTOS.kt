package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.TablaConsentimiento
import PRUEBAS.DOBLES.SistemaPrueba
import SERVICIOS.PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plazo de conservación del consentimiento de una cuenta eliminada (D38): 5 años desde que se eliminó. */
class PruebaPurgaConsentimientos {
    private val sistema = SistemaPrueba()
    private val ahora = Instant.parse("2031-06-01T12:00:00Z")

    private fun registrarYAceptar(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        val id = sistema.usuarios.buscarPorCorreo(correo)!!.id
        sistema.consentimiento.otorgar(id, sistema.consentimiento.textoVigente().version, mapOf("x" to true), "10.0.0.1")
        id
    }

    private fun eliminarHace(correo: String, eliminadaEn: Instant, retencionLegal: Boolean = false) = runBlocking {
        val id = registrarYAceptar(correo)
        sistema.usuario.eliminarCuenta(id, "eliminar")
        transaction {
            TablaConsentimiento.update({ TablaConsentimiento.identidadRef eq correo }) {
                it[cuentaEliminadaEn] = eliminadaEn
                it[TablaConsentimiento.retencionLegal] = retencionLegal
            }
        }
    }

    private fun correosGuardados() = transaction {
        TablaConsentimiento.selectAll().map { it[TablaConsentimiento.identidadRef] ?: "vigente:${it[TablaConsentimiento.usuarioId]}" }.toSet()
    }

    private fun haceAnios(anios: Long, masSegundos: Long = 0) =
        ahora.atOffset(ZoneOffset.UTC).minusYears(anios).toInstant().plusSeconds(masSegundos)

    @Test
    fun `borra solo los registros de cuentas eliminadas hace mas de 5 anios sin retencion legal`() = runBlocking<Unit> {
        assertEquals(5L, PLAZO_CONSERVACION_CONSENTIMIENTO_ANIOS)
        eliminarHace("vencida@ejemplo.com", haceAnios(5, masSegundos = -1))
        eliminarHace("justo@ejemplo.com", haceAnios(5, masSegundos = 1))
        eliminarHace("reciente@ejemplo.com", haceAnios(1))
        eliminarHace("en-juicio@ejemplo.com", haceAnios(9), retencionLegal = true)
        val activa = registrarYAceptar("activa@ejemplo.com")

        assertEquals(1, sistema.consentimiento.purgarRegistrosVencidos(ahora))

        assertEquals(
            setOf("justo@ejemplo.com", "reciente@ejemplo.com", "en-juicio@ejemplo.com", "vigente:$activa"),
            correosGuardados(),
            "la cuenta activa, las que no cumplen 5 años y la que tiene retención legal se conservan"
        )
        assertEquals(0, sistema.consentimiento.purgarRegistrosVencidos(ahora), "una segunda pasada no borra nada más")
    }

    @Test
    fun `una cuenta borrada sin fecha se completa en la purga y su plazo corre desde ahi`() = runBlocking<Unit> {
        val id = registrarYAceptar("por-sql@ejemplo.com")
        // Como un borrado hecho por SQL o por el backend anterior: queda huérfana, con IP y sin fecha de borrado.
        transaction {
            TablaConsentimiento.update({ TablaConsentimiento.usuarioId eq id }) {
                it[usuarioId] = null
                it[identidadRef] = "por-sql@ejemplo.com"
            }
        }

        assertEquals(0, sistema.consentimiento.purgarRegistrosVencidos(ahora))

        val fila = transaction { TablaConsentimiento.selectAll().single { it[TablaConsentimiento.identidadRef] == "por-sql@ejemplo.com" } }
        assertEquals(null, fila[TablaConsentimiento.ipOrigen])
        assertEquals(true, fila[TablaConsentimiento.cuentaEliminadaEn] != null)
        assertEquals(true, fila[TablaConsentimiento.fechaRevocado] != null)
    }
}
