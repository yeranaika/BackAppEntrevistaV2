package routes.auth

import data.repository.usuarios.UserRepository
import data.tables.usuarios.UsuarioTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import security.hashPassword
import security.verifyPassword
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserRegistrationUnitTest {

    private lateinit var db: Database
    private lateinit var users: UserRepository

    @BeforeTest
    fun setup() {
        db = Database.connect(
            url = "jdbc:h2:mem:testdb_reg_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction(db) {
            SchemaUtils.create(UsuarioTable)
        }

        users = UserRepository()
    }

    @Test
    fun testCreacionUsuarioYVerificacionDeContrasena() = runBlocking {
        val email = "candidato.nuevo@test.com"
        val rawPassword = "MiPasswordSegura123!"
        val hashed = hashPassword(rawPassword)

        // 1. Verificar que el correo no existe previamente
        assertFalse(users.existsByEmail(email))

        // 2. Crear usuario
        val userId = users.create(
            email = email,
            hash = hashed,
            nombre = "Candidato de Prueba",
            idioma = "es"
        )
        assertNotNull(userId)

        // 3. Verificar que ahora existe
        assertTrue(users.existsByEmail(email))

        // 4. Buscar por email y verificar datos
        val userRow = users.findByEmail(email)
        assertNotNull(userRow)
        assertEquals("candidato.nuevo@test.com", userRow.email)
        assertEquals("Candidato de Prueba", userRow.nombre)
        assertEquals("activo", userRow.estado)

        // 5. Verificar verificación de contraseña
        assertTrue(verifyPassword(rawPassword, userRow.hash), "La contraseña correcta debe validar exitosamente")
        assertFalse(verifyPassword("PasswordIncorrecta", userRow.hash), "Una contraseña errónea no debe validar")
    }
}
