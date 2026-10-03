package services

import com.auth0.jwt.JWT
import data.repository.usuarios.UserRepository
import MODELOS.TablaRefreshToken
import data.tables.usuarios.UsuarioTable
import kotlinx.coroutines.runBlocking
import models.RegisterReq
import models.UpdateProfileReq
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {
    private lateinit var db: Database
    private lateinit var users: UserRepository
    private lateinit var service: AuthService

    @BeforeTest
    fun setup() {
        db = AuthTestDb.connect()
        users = UserRepository()
        service = AuthTestDb.service(users)
    }

    private fun register(email: String = "user@example.com", password: String = "Password123!") =
        runBlocking { service.register(RegisterReq(email = email, password = password, nombre = "Test")) }

    private fun userId(email: String = "user@example.com"): UUID =
        runBlocking { users.findByEmail(email)!!.id }

    // ---------- Registro ----------

    @Test
    fun `register crea usuario, normaliza email y emite tokens con rol user`() {
        val tokens = runBlocking {
            service.register(RegisterReq(email = "  New.User@Example.COM ", password = "Password123!"))
        }

        assertTrue(tokens.tokenAcceso.isNotBlank())
        assertTrue(tokens.tokenRefresco.isNotBlank())
        assertEquals("user", JWT.decode(tokens.tokenAcceso).getClaim("role").asString())
        assertNotNull(runBlocking { users.findByEmail("new.user@example.com") })
        transaction(db) { assertEquals(1, TablaRefreshToken.selectAll().count()) }
    }

    @Test
    fun `register rechaza email invalido`() {
        assertFailsWith<AuthService.InvalidEmailException> { register(email = "no-es-email") }
    }

    @Test
    fun `register rechaza password debil`() {
        assertFailsWith<AuthService.WeakPasswordException> { register(password = "corta") }
    }

    @Test
    fun `register rechaza pais invalido`() {
        assertFailsWith<AuthService.InvalidCountryException> {
            runBlocking { service.register(RegisterReq("a@example.com", "Password123!", pais = "CHL")) }
        }
    }

    @Test
    fun `register rechaza fecha de nacimiento malformada`() {
        assertFailsWith<AuthService.InvalidBirthdateException> {
            runBlocking { service.register(RegisterReq("a@example.com", "Password123!", fechaNacimiento = "31-12-2000")) }
        }
    }

    @Test
    fun `register rechaza email duplicado`() {
        register()
        assertFailsWith<AuthService.EmailInUseException> { register() }
    }

    // ---------- Update de perfil ----------

    @Test
    fun `updateProfile actualiza los campos enviados y conserva los ausentes`() {
        register()
        val id = userId()

        runBlocking {
            service.updateProfile(id, UpdateProfileReq(nombre = "Nuevo", idioma = "en", telefono = "+56912345678", fechaNacimiento = "1995-05-20", genero = "otro"))
            service.updateProfile(id, UpdateProfileReq(nombre = "Otro Nombre"))
        }

        val u = runBlocking { users.findById(id)!! }
        assertEquals("Otro Nombre", u.nombre)
        assertEquals("en", u.idioma)
        assertEquals("+56912345678", u.telefono)
        assertEquals(LocalDate.of(1995, 5, 20), u.fechaNacimiento)
        assertEquals("otro", u.genero)
    }

    @Test
    fun `updateProfile lanza user not found para un id inexistente`() {
        assertFailsWith<AuthService.UserNotFoundException> {
            runBlocking { service.updateProfile(UUID.randomUUID(), UpdateProfileReq(nombre = "X")) }
        }
    }

    @Test
    fun `updateProfile valida cada campo con su error especifico`() {
        register()
        val id = userId()

        fun update(req: UpdateProfileReq) = runBlocking { service.updateProfile(id, req) }

        assertFailsWith<AuthService.InvalidLanguageException> { update(UpdateProfileReq(idioma = "klingon")) }
        assertFailsWith<AuthService.InvalidPhoneException> { update(UpdateProfileReq(telefono = "abc")) }
        assertFailsWith<AuthService.InvalidGenderException> { update(UpdateProfileReq(genero = "desconocido")) }
        assertFailsWith<AuthService.InvalidBirthdateFormatException> { update(UpdateProfileReq(fechaNacimiento = "20/05/1995")) }
        assertFailsWith<AuthService.InvalidBirthdateRangeException> { update(UpdateProfileReq(fechaNacimiento = LocalDate.now().toString())) }
        assertFailsWith<AuthService.NothingToUpdateException> { update(UpdateProfileReq()) }
    }
}
