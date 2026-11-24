package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudRegistro
import INTEGRACIONES.DOCUMENTO_EULA
import INTEGRACIONES.DOCUMENTO_PRIVACIDAD
import INTEGRACIONES.DOCUMENTO_TERMINOS
import MODELOS.ALCANCE_ANALISIS_CAMARA
import MODELOS.ALCANCE_ESTADISTICAS_USO
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
    fun `sin texto en la BD se publica el EULA del repositorio como 1_1_0`() = runBlocking<Unit> {
        val vigente = servicio.textoVigente()
        assertEquals("1.1.0", vigente.version)
        assertTrue(vigente.cuerpo.contains("ACUERDO DE LICENCIA"))
        // D35: el EULA de respaldo ya no ofrece usar los datos para "mejora del modelo".
        assertTrue("mejora del modelo" !in vigente.cuerpo && "Ley 21.719" in vigente.cuerpo)
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
    fun `publicar guarda los alcances posibles y volver a publicar sin ellos los conserva`() = runBlocking<Unit> {
        servicio.publicar("1.1", "Consentimiento", "Texto", listOf(" analytics ", "marketing", "analytics"))
        assertEquals(listOf("analytics", "marketing"), servicio.textoVigente().alcancesPosibles)

        servicio.publicar("1.1", "Consentimiento", "Texto corregido")
        assertEquals(listOf("analytics", "marketing"), servicio.textoVigente().alcancesPosibles)

        // El mapa para la app marca en false lo que el usuario no aceptó, aunque no lo haya enviado.
        val consentimiento = servicio.otorgar(ana, "1.1", mapOf("analytics" to true), "10.0.0.1")
        assertEquals(mapOf("analytics" to true, "marketing" to false), servicio.alcancesComoMapa(consentimiento))

        fallaCon<ErrorValidacion>("alcance_invalido") { servicio.publicar("1.2", "Consentimiento", "Texto", listOf(" ")) }
    }

    @Test
    fun `otorgar guarda los alcances aceptados y el permiso de entrenamiento en sus columnas reales`() = runBlocking<Unit> {
        val version = servicio.publicar("2.0.0", "Consentimiento", "Texto", listOf("uso_datos", "ia_entrenamiento", "marketing")).version
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

    @Test
    fun `otorgar descarta los alcances que la version no ofrece`() = runBlocking<Unit> {
        servicio.publicar("v1.1", "Consentimiento", "Texto", listOf(ALCANCE_ESTADISTICAS_USO, "marketing", ALCANCE_ANALISIS_CAMARA))
        val otorgado = servicio.otorgar(
            ana, "v1.1",
            mapOf(
                ALCANCE_ESTADISTICAS_USO to true, "marketing" to false, "ia_entrenamiento" to true, " inventado " to true,
                // Lo que manda premarcado la app anterior a la gestión del consentimiento: no es un opt-in real.
                "analytics" to true
            ),
            null
        )

        assertEquals(listOf(ALCANCE_ESTADISTICAS_USO), otorgado.alcancesAceptados, "solo lo que la versión ofrece")
        assertEquals(false, otorgado.aceptaEntrenamientoIa, "un permiso de entrenamiento que el texto no pidió no cuenta")
        transaction {
            val fila = TablaConsentimiento.selectAll().single()
            assertEquals(listOf(ALCANCE_ESTADISTICAS_USO), fila[TablaConsentimiento.alcancesAceptados])
            assertEquals(false, fila[TablaConsentimiento.aceptaEntrenamientoIa])
        }
        assertEquals(
            mapOf(ALCANCE_ESTADISTICAS_USO to true, "marketing" to false, ALCANCE_ANALISIS_CAMARA to false),
            servicio.alcancesComoMapa(otorgado)
        )
    }

    @Test
    fun `un alcance guardado antes del filtro que la version no ofrece no sale en el mapa`() = runBlocking<Unit> {
        // Como en producción: la v1.0 no ofrece "analytics", pero el backend anterior guardaba cualquier clave.
        servicio.publicar("v1.0", "Consentimiento", "Texto", listOf("uso_datos_sesion", "comunicaciones_plataforma"))
        val otorgado = servicio.otorgar(ana, "v1.0", mapOf("uso_datos_sesion" to true), null)
        transaction {
            TablaConsentimiento.update({ TablaConsentimiento.id eq otorgado.id }) {
                it[alcancesAceptados] = listOf("analytics", "uso_datos_sesion")
            }
        }

        val heredado = servicio.vigente(ana)!!
        assertEquals(listOf("analytics", "uso_datos_sesion"), heredado.alcancesAceptados)
        assertEquals(
            mapOf("uso_datos_sesion" to true, "comunicaciones_plataforma" to false),
            servicio.alcancesComoMapa(heredado),
            "la app no debe leer analytics=true: esa versión nunca lo pidió"
        )
    }

    @Test
    fun `con una version vigente sin alcances la app puede aceptar y no queda ningun permiso`() = runBlocking<Unit> {
        // Lo que pasaría con la app actual si en producción rige un texto sin las opciones de la app (v1.0 o el EULA).
        val version = servicio.textoVigente().version
        val otorgado = servicio.otorgar(ana, version, mapOf(ALCANCE_ESTADISTICAS_USO to true, "marketing" to true), null)

        assertEquals(emptyList<String>(), otorgado.alcancesAceptados)
        assertEquals(version, servicio.vigente(ana)!!.version, "aceptar el texto sigue funcionando")
        assertEquals(emptyMap<String, Boolean>(), servicio.alcancesComoMapa(otorgado))
    }

    @Test
    fun `el permiso de camara se registra aparte y conserva las demas opciones`() = runBlocking<Unit> {
        servicio.publicar("v1.1", "Consentimiento", "Texto", listOf(ALCANCE_ESTADISTICAS_USO, "marketing", ALCANCE_ANALISIS_CAMARA))
        val general = servicio.otorgar(ana, "v1.1", mapOf(ALCANCE_ESTADISTICAS_USO to true, "marketing" to false), null)
        assertEquals(false, servicio.alcancesComoMapa(general)[ALCANCE_ANALISIS_CAMARA], "el consentimiento general no lo marca")

        // "Acepto, activar cámara": misma versión, con lo que ya tenía la persona y la cámara en true.
        val conCamara = servicio.otorgar(ana, "v1.1", mapOf(ALCANCE_ESTADISTICAS_USO to true, "marketing" to false, ALCANCE_ANALISIS_CAMARA to true), null)
        assertEquals(listOf(ALCANCE_ANALISIS_CAMARA, ALCANCE_ESTADISTICAS_USO), conCamara.alcancesAceptados)
        assertEquals(conCamara.id, servicio.vigente(ana)!!.id)
        transaction { assertEquals(1, TablaConsentimiento.selectAll().count { it[TablaConsentimiento.fechaRevocado] == null }) }
    }

    @Test
    fun `una version ya aceptada no se puede cambiar pero si reactivar`() = runBlocking<Unit> {
        servicio.publicar("v1.1", "Consentimiento", "Texto", listOf("analytics", "marketing"))
        servicio.otorgar(ana, "v1.1", mapOf("analytics" to true), null)
        servicio.publicar("v2", "Consentimiento", "Otro texto", listOf("analytics"))
        assertEquals("v2", servicio.textoVigente().version)

        // Mismo contenido (con o sin alcances): solo vuelve a quedar vigente.
        servicio.publicar(" v1.1 ", " Consentimiento ", "Texto")
        assertEquals("v1.1", servicio.textoVigente().version)
        servicio.publicar("v1.1", "Consentimiento", "Texto", listOf("marketing", "analytics", "analytics"))
        assertEquals(listOf("analytics", "marketing"), servicio.textoVigente().alcancesPosibles)

        // Cualquier cambio de lo que ya se aceptó responde 409 y no toca el texto.
        fallaCon<ErrorConflicto>("version_con_aceptaciones") { servicio.publicar("v1.1", "Consentimiento", "Texto cambiado") }
        fallaCon<ErrorConflicto>("version_con_aceptaciones") { servicio.publicar("v1.1", "Otro título", "Texto") }
        fallaCon<ErrorConflicto>("version_con_aceptaciones") { servicio.publicar("v1.1", "Consentimiento", "Texto", listOf("analytics")) }
        assertEquals("Texto", servicio.textoVigente().cuerpo)

        // Una versión que nadie aceptó todavía se puede corregir.
        servicio.publicar("v3", "Consentimiento", "Borrador")
        servicio.publicar("v3", "Consentimiento", "Borrador corregido", listOf("analytics"))
        assertEquals("Borrador corregido", servicio.textoVigente().cuerpo)
        assertEquals(listOf("analytics"), servicio.textoVigente().alcancesPosibles)
    }
}
