package PRUEBAS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.DOCUMENTO_EULA
import INTEGRACIONES.DOCUMENTO_PRIVACIDAD
import INTEGRACIONES.DOCUMENTO_TERMINOS
import MODELOS.TablaConsentimiento
import MODELOS.TablaTextoConsentimiento
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioConsentimiento {
    private val sistema = SistemaPrueba()
    private val servicio = sistema.consentimiento
    private val ana: UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
    }

    @Test
    fun `los documentos legales existen y no estan vacios`() = runBlocking<Unit> {
        assertTrue(sistema.documentos.leer(DOCUMENTO_EULA)!!.contains("ACUERDO DE LICENCIA DE USUARIO FINAL"))
        assertTrue(sistema.documentos.leer(DOCUMENTO_TERMINOS)!!.contains("TÉRMINOS Y CONDICIONES"))
        assertTrue(sistema.documentos.leer(DOCUMENTO_PRIVACIDAD)!!.contains("POLÍTICA DE PRIVACIDAD"))
        assertNull(sistema.documentos.leer("NO_EXISTE.md"))
    }

    @Test
    fun `sin texto en la BD se publica el EULA del repositorio como 1_0_0`() = runBlocking<Unit> {
        val vigente = servicio.textoVigente()
        assertEquals("1.0.0", vigente.version)
        assertTrue(vigente.cuerpo.contains("ACUERDO DE LICENCIA"))
        assertEquals(1, servicio.versiones().size)
    }

    @Test
    fun `publicar deja una sola version vigente`() = runBlocking<Unit> {
        servicio.textoVigente()
        servicio.publicar("2.0.0", "EULA v2", "Texto nuevo")
        assertEquals("2.0.0", servicio.textoVigente().version)
        assertEquals(1, servicio.versiones().count { it.estaVigente })
        fallaCon<ErrorValidacion>("missing_fields") { servicio.publicar("3.0.0", "Título", " ") }
    }

    @Test
    fun `otorgar guarda los alcances aceptados y el permiso de entrenamiento en sus columnas reales`() = runBlocking<Unit> {
        val version = servicio.textoVigente().version
        servicio.otorgar(ana, version, mapOf("uso_datos" to true, "ia_entrenamiento" to true, "marketing" to false), "10.0.0.1")

        transaction {
            val fila = TablaConsentimiento.selectAll().single()
            assertEquals(listOf("ia_entrenamiento", "uso_datos"), fila[TablaConsentimiento.alcancesAceptados])
            assertEquals(true, fila[TablaConsentimiento.aceptaEntrenamientoIa])
            assertEquals("10.0.0.1", fila[TablaConsentimiento.ipOrigen])
        }
    }

    @Test
    fun `un consentimiento nuevo revoca el anterior y se puede revocar`() = runBlocking<Unit> {
        val version = servicio.textoVigente().version
        servicio.otorgar(ana, version, mapOf("uso_datos" to true), null)
        val segundo = servicio.otorgar(ana, version, mapOf("uso_datos" to true, "marketing" to true), null)

        assertEquals(segundo.id, servicio.vigente(ana)!!.id)
        transaction { assertEquals(1, TablaConsentimiento.selectAll().count { it[TablaConsentimiento.fechaRevocado] == null }) }

        servicio.revocar(ana)
        assertNull(servicio.vigente(ana))
        fallaCon<ErrorNoEncontrado>("consentimiento_no_encontrado") { servicio.revocar(ana) }
    }

    @Test
    fun `una version inexistente responde 404 en vez de violar la FK`() {
        fallaCon<ErrorNoEncontrado>("version_no_encontrada") { servicio.otorgar(ana, "9.9.9", mapOf("uso_datos" to true), null) }
        fallaCon<ErrorValidacion>("alcances_requeridos") { servicio.otorgar(ana, "1.0.0", emptyMap(), null) }
    }

    @Test
    fun `el mapa para la app marca en false los alcances posibles no aceptados`() = runBlocking<Unit> {
        val version = servicio.textoVigente().version
        transaction {
            TablaTextoConsentimiento.update({ TablaTextoConsentimiento.version eq version }) {
                it[alcancesPosibles] = listOf("uso_datos", "marketing", "ia_entrenamiento")
            }
        }
        val otorgado = servicio.otorgar(ana, version, mapOf("uso_datos" to true, "marketing" to false), null)
        assertEquals(
            mapOf("uso_datos" to true, "marketing" to false, "ia_entrenamiento" to false),
            servicio.alcancesComoMapa(assertNotNull(otorgado))
        )
    }
}
