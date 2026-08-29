package services.market

import data.models.market.SkillDto
import data.repository.market.SkillMarketRepository
import data.tables.market.CargoSkillTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import data.tables.market.SkillTendenciaTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillTrendWorkerTest {

    private lateinit var db: Database
    private lateinit var repository: SkillMarketRepository
    private val skillIdKotlin = UUID.randomUUID()
    private val skillIdReact = UUID.randomUUID()
    private val cargoIdBackend = UUID.randomUUID()

    @BeforeTest
    fun setup() {
        // Base de datos H2 en memoria compatible con PostgreSQL para tests aislados
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction(db) {
            SchemaUtils.create(SkillTable, SkillTendenciaTable, CargoTable, CargoSkillTable)

            // Insertar skills de prueba
            SkillTable.insert {
                it[skillId] = skillIdKotlin
                it[nombre] = "Kotlin"
                it[categoria] = "tecnica"
                it[tipoArea] = "backend"
                it[descripcion] = "Lenguaje Kotlin"
                it[demandaScore] = 50
                it[activo] = true
            }

            SkillTable.insert {
                it[skillId] = skillIdReact
                it[nombre] = "React"
                it[categoria] = "tecnica"
                it[tipoArea] = "frontend"
                it[descripcion] = "Librería React"
                it[demandaScore] = 50
                it[activo] = true
            }

            // Insertar cargo y relación cargo_skill
            CargoTable.insert {
                it[cargoId] = cargoIdBackend
                it[nombre] = "Backend Developer"
                it[area] = "backend"
                it[nivelBase] = "junior"
                it[activo] = true
            }

            CargoSkillTable.insert {
                it[cargoSkillId] = UUID.randomUUID()
                it[cargoId] = cargoIdBackend
                it[skillId] = skillIdKotlin
                it[nivelRequerido] = "junior"
                it[peso] = 50
                it[obligatoria] = true
            }
        }

        repository = SkillMarketRepository(db)
    }

    @Test
    fun testSyncMarketTrendsCalculatesScoresAndInsertsHistory() = runBlocking {
        val client = JobMarketClient(rapidApiKey = null)
        val worker = SkillTrendWorker(repository = repository, jobMarketClient = client)

        val result = worker.syncMarketTrends()

        assertTrue(result.success, "El resultado de sincronización debe ser exitoso")
        assertEquals(2, result.skillsUpdatedCount, "Debe haber actualizado 2 skills")

        // Verificar que demandaScore se actualizó
        val updatedKotlin = repository.getSkillById(skillIdKotlin)
        assertTrue(updatedKotlin != null)
        assertTrue(updatedKotlin.demandaScore in 1..100)

        // Verificar que se insertó registro en historial de tendencias
        val trendsHistory = repository.getSkillTrendsHistory(skillIdKotlin)
        assertTrue(trendsHistory.isNotEmpty(), "Debe existir historial en skill_tendencia")
        assertEquals(skillIdKotlin.toString(), trendsHistory.first().skillId)

        client.close()
    }
}
