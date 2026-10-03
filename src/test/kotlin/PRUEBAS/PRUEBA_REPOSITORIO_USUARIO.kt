package PRUEBAS

import ERRORES.ErrorConflicto
import MODELOS.CambiosPerfil
import MODELOS.NivelExperiencia
import MODELOS.NuevoUsuario
import MODELOS.TablaPerfil
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Comportamiento de los repositorios Exposed que no se puede probar con dobles en memoria. */
class PruebaRepositorioUsuario {
    private val sistema = SistemaPrueba()

    private fun nuevo(correo: String = "ana@ejemplo.com") =
        NuevoUsuario(correo = correo, hashContrasena = "hash", nombre = "Ana", idioma = "es")

    @Test
    fun `crear guarda usuario y perfil en la misma transaccion`() = runBlocking<Unit> {
        val id = sistema.usuarios.crear(nuevo(), CambiosPerfil(nivelExperiencia = NivelExperiencia.SENIOR, area = "TI"))
        assertEquals(NivelExperiencia.SENIOR, sistema.perfiles.buscarPorUsuario(id)!!.nivelExperiencia)
        // En la BD queda el valor que acepta el CHECK de perfil_usuario.
        assertEquals("senior", transaction { TablaPerfil.selectAll().single()[TablaPerfil.nivelExperiencia] })
    }

    @Test
    fun `si el perfil falla tampoco queda el usuario`() = runBlocking<Unit> {
        // area es VARCHAR(50): forzamos que falle el INSERT del perfil.
        assertFailsWith<Exception> {
            runBlocking { sistema.usuarios.crear(nuevo(), CambiosPerfil(area = "x".repeat(60))) }
        }
        assertFalse(sistema.usuarios.existeCorreo("ana@ejemplo.com"))
    }

    @Test
    fun `el indice unico de correo se traduce a email_in_use`() {
        runBlocking { sistema.usuarios.crear(nuevo()) }
        fallaCon<ErrorConflicto>("email_in_use") { sistema.usuarios.crear(nuevo()) }
    }

    @Test
    fun `los intentos fallidos se acumulan y un codigo nuevo invalida el anterior`() = runBlocking<Unit> {
        val id = sistema.usuarios.crear(nuevo())
        val ahora = Instant.now()
        sistema.codigos.crear(id, "111111", ahora, ahora.plusSeconds(900))
        val primero = sistema.codigos.buscarVigente(id, ahora)!!

        sistema.codigos.registrarIntentoFallido(primero.token)
        sistema.codigos.registrarIntentoFallido(primero.token)
        assertEquals(2, sistema.codigos.buscarVigente(id, ahora)!!.intentosFallidos)

        sistema.codigos.crear(id, "222222", ahora, ahora.plusSeconds(900))
        assertEquals("222222", sistema.codigos.buscarVigente(id, ahora)!!.codigo)
        assertFalse(sistema.codigos.consumir(primero.token))
    }

    @Test
    fun `un codigo solo se consume una vez`() = runBlocking<Unit> {
        val id = sistema.usuarios.crear(nuevo())
        val ahora = Instant.now()
        sistema.codigos.crear(id, "111111", ahora, ahora.plusSeconds(900))
        val codigo = sistema.codigos.buscarVigente(id, ahora)!!

        assertEquals(true, sistema.codigos.consumir(codigo.token))
        assertFalse(sistema.codigos.consumir(codigo.token))
        assertNull(sistema.codigos.buscarVigente(id, ahora))
    }
}
