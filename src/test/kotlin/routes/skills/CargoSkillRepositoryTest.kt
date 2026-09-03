package routes.skills

import data.models.skills.CargoLevelItem
import data.repository.skills.CargoSkillRepository
import data.tables.market.CargoSkillTable
import data.tables.market.CargoTable
import data.tables.market.SkillTable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import services.cache.RedisCacheService
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CargoSkillRepositoryTest {

    private lateinit var db: Database
    private lateinit var repo: CargoSkillRepository
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_skills_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction(db) {
            SchemaUtils.create(CargoTable, SkillTable, CargoSkillTable)
        }

        repo = CargoSkillRepository(db)
    }

    @Test
    fun testGetCargosListRetornaNivelesCorrectamente() = runBlocking {
        val cargoId1 = UUID.randomUUID()
        val cargoId2 = UUID.randomUUID()

        transaction(db) {
            CargoTable.insert {
                it[cargoId] = cargoId1
                it[nombre] = "Backend Developer Junior"
                it[area] = "backend"
                it[descripcion] = "Rol junior de backend"
                it[nivelBase] = "junior"
                it[activo] = true
            }
            CargoTable.insert {
                it[cargoId] = cargoId2
                it[nombre] = "Mobile Developer Senior"
                it[area] = "mobile"
                it[descripcion] = "Rol senior de Android/iOS"
                it[nivelBase] = "senior"
                it[activo] = true
            }
        }

        val cargos = repo.getCargosList()

        assertEquals(2, cargos.size)
        val juniorCargo = cargos.find { it.nombre == "Backend Developer Junior" }
        assertNotNull(juniorCargo)
        assertEquals("junior", juniorCargo.nivelBase)
        assertEquals("backend", juniorCargo.area)

        val seniorCargo = cargos.find { it.nombre == "Mobile Developer Senior" }
        assertNotNull(seniorCargo)
        assertEquals("senior", seniorCargo.nivelBase)
    }

    @Test
    fun testGetCargoSkillsMatrixRetornaMatrizConPesos() = runBlocking {
        val cargoUuid = UUID.randomUUID()
        val skillUuid1 = UUID.randomUUID()
        val skillUuid2 = UUID.randomUUID()

        transaction(db) {
            CargoTable.insert {
                it[cargoId] = cargoUuid
                it[nombre] = "Data Engineer"
                it[area] = "data"
                it[descripcion] = "Ingeniería de datos"
                it[nivelBase] = "semisenior"
                it[activo] = true
            }

            SkillTable.insert {
                it[skillId] = skillUuid1
                it[nombre] = "Python"
                it[categoria] = "tecnica"
                it[tipoArea] = "data"
                it[demandaScore] = 95
                it[activo] = true
            }

            SkillTable.insert {
                it[skillId] = skillUuid2
                it[nombre] = "Trabajo en equipo"
                it[categoria] = "blanda"
                it[tipoArea] = "hr"
                it[demandaScore] = 80
                it[activo] = true
            }

            CargoSkillTable.insert {
                it[cargoSkillId] = UUID.randomUUID()
                it[cargoId] = cargoUuid
                it[skillId] = skillUuid1
                it[nivelRequerido] = "senior"
                it[peso] = 90
                it[obligatoria] = true
            }

            CargoSkillTable.insert {
                it[cargoSkillId] = UUID.randomUUID()
                it[cargoId] = cargoUuid
                it[skillId] = skillUuid2
                it[nivelRequerido] = "semisenior"
                it[peso] = 60
                it[obligatoria] = false
            }
        }

        val matrix = repo.getCargoSkillsMatrix(cargoUuid)
        assertNotNull(matrix)
        assertEquals(2, matrix.totalSkills)
        assertEquals(1, matrix.obligatoriasCount)
        assertEquals(1, matrix.opcionalesCount)

        val python = matrix.skills.find { it.nombre == "Python" }
        assertNotNull(python)
        assertTrue(python.obligatoria)
        assertEquals(90.toShort(), python.peso)
        assertEquals("senior", python.nivelRequerido)

        val softSkill = matrix.skills.find { it.nombre == "Trabajo en equipo" }
        assertNotNull(softSkill)
        assertFalse(softSkill.obligatoria)
    }

    @Test
    fun testGetTrendingSkillsFiltraPorCategoriaYSort() = runBlocking {
        transaction(db) {
            SkillTable.insert {
                it[skillId] = UUID.randomUUID()
                it[nombre] = "Kotlin"
                it[categoria] = "tecnica"
                it[tipoArea] = "backend"
                it[demandaScore] = 98
                it[activo] = true
            }
            SkillTable.insert {
                it[skillId] = UUID.randomUUID()
                it[nombre] = "Comunicación"
                it[categoria] = "blanda"
                it[tipoArea] = "hr"
                it[demandaScore] = 90
                it[activo] = true
            }
            SkillTable.insert {
                it[skillId] = UUID.randomUUID()
                it[nombre] = "SQL"
                it[categoria] = "tecnica"
                it[tipoArea] = "data"
                it[demandaScore] = 85
                it[activo] = true
            }
        }

        val tecnicas = repo.getTrendingSkills(categoria = "tecnica", limit = 10)
        assertEquals(2, tecnicas.total)
        assertEquals("Kotlin", tecnicas.skills[0].nombre)
        assertEquals(98.toShort(), tecnicas.skills[0].demandaScore)

        val blandas = repo.getTrendingSkills(categoria = "blanda", limit = 10)
        assertEquals(1, blandas.total)
        assertEquals("Comunicación", blandas.skills[0].nombre)
    }

    @Test
    fun testRedisCacheServiceGracefulFallback() {
        // Al apuntar a un puerto sin Redis activo, debe manejar graceful fallback sin lanzar excepciones
        val cache = RedisCacheService(host = "127.0.0.1", port = 65432, timeoutMs = 100)

        assertFalse(cache.isAvailable())
        val res = cache.get("cargos:lista")
        assertEquals(null, res)

        val setRes = cache.set("cargos:lista", "[]", 100)
        assertFalse(setRes)

        cache.close()
    }
}
