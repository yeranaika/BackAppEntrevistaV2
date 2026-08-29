package services.market

import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import data.tables.market.CargoSkillTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import data.tables.market.SkillTendenciaTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CargoSkillGeneratorServiceTest {

    private lateinit var db: Database
    private lateinit var cargoRepo: CargoRepository
    private lateinit var skillMarketRepo: SkillMarketRepository

    @BeforeTest
    fun setup() {
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction(db) {
            SchemaUtils.create(SkillTable, SkillTendenciaTable, CargoTable, CargoSkillTable)
        }

        cargoRepo = CargoRepository(db)
        skillMarketRepo = SkillMarketRepository(db)
    }

    @Test
    fun testGenerateRequirementsForCargoCreatesSkillsAndLinksThem() = runBlocking {
        // 1. Crear una carrera / cargo de prueba
        val cargo = cargoRepo.createCargo(
            nombre = "Backend Developer",
            area = "backend",
            descripcion = "Desarrollador backend",
            nivelBase = "junior"
        )
        val cargoUuid = UUID.fromString(cargo.cargoId)

        // 2. Ejecutar el generador con cliente fallback
        val client = JobMarketClient(rapidApiKey = null)
        val generator = CargoSkillGeneratorService(
            cargoRepository = cargoRepo,
            skillMarketRepository = skillMarketRepo,
            jobMarketClient = client
        )

        val result = generator.generateRequirementsForCargo(cargoUuid)

        assertTrue(result.skillsLinkedCount > 0, "Debe haber vinculado skills detectadas en el mercado")
        assertEquals(cargo.cargoId, result.cargoId)

        // 3. Verificar que las relaciones en BD existen con sus pesos
        val skillsDetails = cargoRepo.getCargoSkillsWithDetails(cargoUuid)
        assertTrue(skillsDetails.isNotEmpty(), "Debe haber generado y vinculado skills para el cargo")

        for (skill in skillsDetails) {
            assertTrue(skill.peso in 1..100, "El peso de ${skill.nombre} debe estar entre 1 y 100")
            assertTrue(skill.nivelRequerido in listOf("junior", "semisenior", "senior"))
            assertTrue(skill.categoria in listOf("tecnica", "blanda"))
        }

        client.close()
    }
}
