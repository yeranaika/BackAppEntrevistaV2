package routes.legal

import data.models.usuarios.CreateConsentTextReq
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentTextTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EulaDatabaseVersioningTest {

    private lateinit var db: Database
    private lateinit var repo: ConsentTextRepository

    @BeforeTest
    fun setup() {
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_eula_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction(db) {
            SchemaUtils.create(ConsentTextTable)
        }

        repo = ConsentTextRepository(db)
    }

    @Test
    fun testObtenerEulaVigentePorDefecto() = runBlocking {
        val currentEula = repo.getCurrent()
        assertNotNull(currentEula)
        assertEquals("1.0.0", currentEula.version)
        assertTrue(currentEula.vigente)
        assertTrue(currentEula.body.isNotEmpty())
    }

    @Test
    fun testPublicarNuevaVersionDesdePanelAdmin() = runBlocking {
        // 1. Inicializar versión 1.0.0
        repo.getCurrent()

        // 2. Admin publica versión 2.0.0 con nuevos términos
        val newVersionReq = CreateConsentTextReq(
            version = "2.0.0",
            title = "EULA y Términos Actualizados 2026",
            body = "Nueva versión del acuerdo de licencia de usuario final con cláusulas de IA."
        )
        val created = repo.createOrUpdate(newVersionReq)

        assertEquals("2.0.0", created.version)
        assertTrue(created.vigente)

        // 3. Verificar que getCurrent() ahora retorna la versión 2.0.0
        val currentAfterUpdate = repo.getCurrent()
        assertEquals("2.0.0", currentAfterUpdate.version)
        assertEquals("EULA y Términos Actualizados 2026", currentAfterUpdate.title)

        // 4. Verificar historial de versiones para el panel de administración
        val allVersions = repo.getAllVersions()
        assertEquals(2, allVersions.size)

        val v2 = allVersions.find { it.version == "2.0.0" }
        assertNotNull(v2)
        assertTrue(v2.vigente)

        val v1 = allVersions.find { it.version == "1.0.0" }
        assertNotNull(v1)
        assertFalse(v1.vigente, "La versión 1.0.0 debe haber quedado no vigente")
    }
}
