package PRUEBAS

import ESQUEMAS.SolicitudRegistro
import MODELOS.TablaConsentimiento
import MODELOS.TablaMetricaVideo
import MODELOS.TablaSesionEntrevista
import MODELOS.TablaTextoConsentimiento
import MODELOS.TablaUsuario
import PRUEBAS.DOBLES.SistemaPrueba
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regla de privacidad (T0.9): al eliminar la cuenta se borra todo, salvo el registro de consentimiento. */
class PruebaEliminarCuentaConsentimiento {
    private val sistema = SistemaPrueba()

    private fun registrar(correo: String): UUID = runBlocking {
        sistema.usuario.registrar(SolicitudRegistro(correo, "Clave-segura-1"))
        sistema.usuarios.buscarPorCorreo(correo)!!.id
    }

    /** Solo se guardan los alcances que ofrece la versión (D35): esta los ofrece para probar que se conservan. */
    private fun publicarVersionConAlcances(): String = runBlocking {
        sistema.consentimiento.publicar("9.0.0", "Consentimiento de prueba", "Texto", listOf("uso_datos", "ia_entrenamiento")).version
    }

    private fun sembrarSesionConMetricas(usuarioId: UUID): UUID = transaction {
        val sesion = UUID.randomUUID()
        TablaSesionEntrevista.insert {
            it[sesionId] = sesion
            it[TablaSesionEntrevista.usuarioId] = usuarioId
            it[cargoObjetivo] = "Desarrollador"
            it[nivelDificultad] = "junior"
            it[fechaInicio] = Instant.now()
        }
        listOf(0L, 500L).forEach { ms ->
            TablaMetricaVideo.insert {
                it[metricaId] = UUID.randomUUID()
                it[sesionId] = sesion
                it[timestampMs] = ms
            }
        }
        sesion
    }

    @Test
    fun `eliminar la cuenta borra todo y conserva el consentimiento con la identidad`() = runBlocking<Unit> {
        val ana = registrar("ana@ejemplo.com")
        val otra = registrar("otra@ejemplo.com")
        val version = publicarVersionConAlcances()
        sistema.consentimiento.otorgar(ana, version, mapOf("uso_datos" to true, "ia_entrenamiento" to true), "10.0.0.1")
        sistema.consentimiento.otorgar(otra, version, mapOf("uso_datos" to true), null)
        val sesionAna = sembrarSesionConMetricas(ana)
        val sesionOtra = sembrarSesionConMetricas(otra)

        sistema.usuario.eliminarCuenta(ana, "eliminar")

        // Datos de la cuenta: desaparecen
        assertNull(sistema.usuarios.buscarPorId(ana))
        assertNull(sistema.perfiles.buscarPorUsuario(ana))
        transaction {
            assertTrue(TablaSesionEntrevista.selectAll().none { it[TablaSesionEntrevista.sesionId] == sesionAna })
            assertTrue(TablaMetricaVideo.selectAll().none { it[TablaMetricaVideo.sesionId] == sesionAna })
            // Consentimiento: se conserva, ya sin usuario pero con su identidad
            val fila = TablaConsentimiento.selectAll().single { it[TablaConsentimiento.identidadRef] != null }
            assertEquals("ana@ejemplo.com", fila[TablaConsentimiento.identidadRef])
            assertNull(fila[TablaConsentimiento.usuarioId])
            assertEquals(version, fila[TablaConsentimiento.version])
            assertEquals(listOf("ia_entrenamiento", "uso_datos"), fila[TablaConsentimiento.alcancesAceptados])
            assertTrue(fila[TablaConsentimiento.aceptaEntrenamientoIa])
            assertNotNull(fila[TablaConsentimiento.fechaOtorgado])
            // Los textos legales (globales) siguen ahí
            assertTrue(TablaTextoConsentimiento.selectAll().count() > 0)
            // Lo de otra persona no se toca
            assertEquals(otra, TablaConsentimiento.selectAll().single { it[TablaConsentimiento.usuarioId] != null }[TablaConsentimiento.usuarioId])
            assertEquals(1L, TablaSesionEntrevista.selectAll().count())
            assertEquals(2L, TablaMetricaVideo.selectAll().count())
            assertEquals(sesionOtra, TablaSesionEntrevista.selectAll().single()[TablaSesionEntrevista.sesionId])
            assertEquals(1L, TablaUsuario.selectAll().count())
        }
    }

    @Test
    fun `un usuario sin consentimiento tambien se elimina limpio`() = runBlocking<Unit> {
        val bea = registrar("bea@ejemplo.com")
        sembrarSesionConMetricas(bea)

        sistema.usuario.eliminarCuenta(bea, "eliminar")

        assertNull(sistema.usuarios.buscarPorId(bea))
        transaction {
            assertEquals(0L, TablaConsentimiento.selectAll().count())
            assertEquals(0L, TablaSesionEntrevista.selectAll().count())
            assertEquals(0L, TablaMetricaVideo.selectAll().count())
        }
    }
}
