package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudActualizarCuenta
import ESQUEMAS.SolicitudActualizarPerfil
import ESQUEMAS.SolicitudRegistro
import MODELOS.NivelExperiencia
import PRUEBAS.DOBLES.JWT_PRUEBA
import PRUEBAS.DOBLES.ObjetivosEnMemoria
import PRUEBAS.DOBLES.PerfilesEnMemoria
import PRUEBAS.DOBLES.RefreshTokensEnMemoria
import PRUEBAS.DOBLES.UsuariosEnMemoria
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioToken
import SERVICIOS.ServicioUsuario
import UTILIDADES.verificarContrasena
import com.auth0.jwt.JWT
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaServicioUsuario {
    private val perfiles = PerfilesEnMemoria()
    private val usuarios = UsuariosEnMemoria(perfiles)
    private val objetivos = ObjetivosEnMemoria()
    private val reloj = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC)
    private val servicio = ServicioUsuario(
        usuarios, perfiles, objetivos, ServicioToken(RefreshTokensEnMemoria(), usuarios, JWT_PRUEBA), reloj
    )

    private fun registrar(solicitud: SolicitudRegistro) = runBlocking { servicio.registrar(solicitud) }

    private fun registrarAna(): UUID {
        registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1", nombre = "Ana"))
        return runBlocking { usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id }
    }

    // ---------- Registro ----------

    @Test
    fun `registro normaliza el correo, guarda la contrasena como hash y emite rol user`() = runBlocking<Unit> {
        val par = servicio.registrar(SolicitudRegistro("  Ana@Ejemplo.COM ", "Clave-segura-1"))

        val creado = assertNotNull(usuarios.buscarPorCorreo("ana@ejemplo.com"))
        assertTrue(verificarContrasena("Clave-segura-1", creado.hashContrasena))
        assertEquals("user", JWT.decode(par.tokenAcceso).getClaim("role").asString())
    }

    @Test
    fun `registro acepta el formulario de Android con campos vacios`() = runBlocking<Unit> {
        // Así lo envía RegisterViewModel: el perfil se completa después en el onboarding.
        val flags = buildJsonObject { put("tts", JsonPrimitive(false)); put("altoContraste", JsonPrimitive(true)) }
        servicio.registrar(
            SolicitudRegistro(
                correo = "ana@ejemplo.com", contrasena = "Clave-segura-1", nombre = "Ana", idioma = "es",
                telefono = "", fechaNacimiento = "", genero = "",
                nivelExperiencia = "", area = "", pais = "", notaObjetivos = "", flagsAccesibilidad = flags
            )
        )

        val id = usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id
        val perfil = assertNotNull(perfiles.buscarPorUsuario(id))
        assertNull(perfil.nivelExperiencia)
        assertNull(perfil.area)
        assertEquals(flags, perfil.flagsAccesibilidad)
    }

    @Test
    fun `registro traduce el nivel de la app al valor de la base de datos`() = runBlocking<Unit> {
        servicio.registrar(SolicitudRegistro("ana@ejemplo.com", "Clave-segura-1", nivelExperiencia = "mid", pais = "cl"))
        val perfil = perfiles.buscarPorUsuario(usuarios.buscarPorCorreo("ana@ejemplo.com")!!.id)!!
        assertEquals(NivelExperiencia.SEMISENIOR, perfil.nivelExperiencia)
        assertEquals("CL", perfil.pais)
    }

    @Test
    fun `registro rechaza datos invalidos con los codigos que conoce Android`() {
        fallaCon<ErrorValidacion>("invalid_email") { servicio.registrar(SolicitudRegistro("no-es-correo", "Clave-segura-1")) }
        fallaCon<ErrorValidacion>("weak_password") { servicio.registrar(SolicitudRegistro("a@ejemplo.com", "corta")) }
        fallaCon<ErrorValidacion>("invalid_country") {
            servicio.registrar(SolicitudRegistro("a@ejemplo.com", "Clave-segura-1", pais = "CHL"))
        }
        fallaCon<ErrorValidacion>("invalid_birthdate") {
            servicio.registrar(SolicitudRegistro("a@ejemplo.com", "Clave-segura-1", fechaNacimiento = "31-12-2000"))
        }
        fallaCon<ErrorValidacion>("invalid_birthdate") {
            servicio.registrar(SolicitudRegistro("a@ejemplo.com", "Clave-segura-1", fechaNacimiento = "2020-01-01"))
        }
        fallaCon<ErrorValidacion>("nivel_experiencia_invalido") {
            servicio.registrar(SolicitudRegistro("a@ejemplo.com", "Clave-segura-1", nivelExperiencia = "experto"))
        }
        assertEquals(0, usuarios.cantidad)
    }

    @Test
    fun `registro rechaza un correo ya usado`() {
        registrarAna()
        fallaCon<ErrorConflicto>("email_in_use") { servicio.registrar(SolicitudRegistro("ANA@ejemplo.com", "Clave-segura-1")) }
    }

    // ---------- Cuenta ----------

    @Test
    fun `obtenerCuenta junta usuario, perfil y objetivo`() = runBlocking<Unit> {
        val id = registrarAna()
        objetivos.reemplazarActivo(id, "Backend Developer", "TI")

        val cuenta = servicio.obtenerCuenta(id)
        assertEquals("Ana", cuenta.usuario.nombre)
        assertEquals("Backend Developer", cuenta.objetivo?.nombreCargo)
    }

    @Test
    fun `actualizarCuenta cambia lo enviado y conserva lo ausente`() = runBlocking<Unit> {
        val id = registrarAna()
        servicio.actualizarCuenta(id, SolicitudActualizarCuenta(idioma = "en", telefono = "+56912345678", fechaNacimiento = "1995-05-20"))
        servicio.actualizarCuenta(id, SolicitudActualizarCuenta(genero = "otro"))

        val usuario = usuarios.buscarPorId(id)!!
        assertEquals("Ana", usuario.nombre)
        assertEquals("en", usuario.idioma)
        assertEquals("+56912345678", usuario.telefono)
        assertEquals(LocalDate.of(1995, 5, 20), usuario.fechaNacimiento)
        assertEquals("otro", usuario.genero)
    }

    @Test
    fun `actualizarCuenta borra el telefono cuando llega vacio`() = runBlocking<Unit> {
        val id = registrarAna()
        servicio.actualizarCuenta(id, SolicitudActualizarCuenta(telefono = "+56912345678"))
        servicio.actualizarCuenta(id, SolicitudActualizarCuenta(telefono = ""))
        assertNull(usuarios.buscarPorId(id)!!.telefono)
    }

    @Test
    fun `actualizarCuenta valida cada campo con su codigo`() {
        val id = registrarAna()
        fallaCon<ErrorValidacion>("idioma_invalido") { servicio.actualizarCuenta(id, SolicitudActualizarCuenta(idioma = "klingon")) }
        fallaCon<ErrorValidacion>("telefono_invalido") { servicio.actualizarCuenta(id, SolicitudActualizarCuenta(telefono = "abc")) }
        fallaCon<ErrorValidacion>("genero_invalido") { servicio.actualizarCuenta(id, SolicitudActualizarCuenta(genero = "x")) }
        fallaCon<ErrorValidacion>("fecha_nacimiento_invalida") {
            servicio.actualizarCuenta(id, SolicitudActualizarCuenta(fechaNacimiento = "20/05/1995"))
        }
        fallaCon<ErrorValidacion>("fecha_nacimiento_fuera_de_rango") {
            servicio.actualizarCuenta(id, SolicitudActualizarCuenta(fechaNacimiento = "2020-01-01"))
        }
        fallaCon<ErrorValidacion>("nothing_to_update") { servicio.actualizarCuenta(id, SolicitudActualizarCuenta()) }
        fallaCon<ErrorNoEncontrado>("user_not_found") {
            servicio.actualizarCuenta(UUID.randomUUID(), SolicitudActualizarCuenta(nombre = "X"))
        }
    }

    // ---------- Perfil ----------

    @Test
    fun `actualizarPerfil acepta codigo, nombre y texto de la app para el nivel`() = runBlocking<Unit> {
        val id = registrarAna()
        listOf("jr" to NivelExperiencia.JUNIOR, "semisenior" to NivelExperiencia.SEMISENIOR, "Tengo mucha experiencia" to NivelExperiencia.SENIOR)
            .forEach { (enviado, esperado) ->
                servicio.actualizarPerfil(id, SolicitudActualizarPerfil(nivelExperiencia = enviado))
                assertEquals(esperado, servicio.obtenerPerfil(id).nivelExperiencia)
            }
    }

    @Test
    fun `actualizarPerfil valida nivel, area y pais`() {
        val id = registrarAna()
        fallaCon<ErrorValidacion>("nivel_experiencia_invalido") { servicio.actualizarPerfil(id, SolicitudActualizarPerfil(nivelExperiencia = "x")) }
        fallaCon<ErrorValidacion>("area_invalida") { servicio.actualizarPerfil(id, SolicitudActualizarPerfil(area = "Astronauta")) }
        fallaCon<ErrorValidacion>("pais_invalido") { servicio.actualizarPerfil(id, SolicitudActualizarPerfil(pais = "Chile")) }
    }

    @Test
    fun `obtenerPerfil sin perfil responde profile_not_found`() {
        fallaCon<ErrorNoEncontrado>("profile_not_found") { servicio.obtenerPerfil(registrarAna()) }
    }

    // ---------- Borrado ----------

    @Test
    fun `eliminarCuenta exige escribir eliminar`() = runBlocking<Unit> {
        val id = registrarAna()
        fallaCon<ErrorValidacion>("must_type_eliminar") { servicio.eliminarCuenta(id, "si") }

        servicio.eliminarCuenta(id, " ELIMINAR ")
        assertNull(usuarios.buscarPorId(id))
        fallaCon<ErrorNoEncontrado>("user_not_found") { servicio.eliminarCuenta(id, "eliminar") }
    }
}
