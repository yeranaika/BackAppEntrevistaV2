package PRUEBAS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.NivelExperiencia
import PRUEBAS.DOBLES.ObjetivosEnMemoria
import PRUEBAS.DOBLES.PerfilesEnMemoria
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioOnboarding
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PruebaServicioOnboarding {
    private val perfiles = PerfilesEnMemoria()
    private val objetivos = ObjetivosEnMemoria()
    private val servicio = ServicioOnboarding(perfiles, objetivos)
    private val usuarioId = UUID.randomUUID()

    @Test
    fun `guardar onboarding llena el perfil y el objetivo con el nivel traducido`() = runBlocking<Unit> {
        val resumen = servicio.guardarOnboarding(usuarioId, "TI", "jr", " Backend Developer ")

        assertEquals(NivelExperiencia.JUNIOR, resumen.nivelExperiencia)
        assertEquals("Backend Developer", servicio.obtenerObjetivo(usuarioId).nombreCargo)
        assertEquals("TI", servicio.obtenerObjetivo(usuarioId).sector)
        assertEquals(NivelExperiencia.JUNIOR, perfiles.buscarPorUsuario(usuarioId)!!.nivelExperiencia)
        assertEquals(resumen, servicio.obtenerOnboarding(usuarioId))
    }

    @Test
    fun `volver a guardar reemplaza el objetivo y conserva el historial`() = runBlocking<Unit> {
        servicio.guardarOnboarding(usuarioId, "TI", "jr", "Backend Developer")
        servicio.guardarOnboarding(usuarioId, "Analista", "sr", "Data Analyst")

        assertEquals("Data Analyst", servicio.obtenerObjetivo(usuarioId).nombreCargo)
        assertEquals(2, objetivos.historial.size)
    }

    @Test
    fun `onboarding incompleto se informa como null`() = runBlocking<Unit> {
        assertNull(servicio.obtenerOnboarding(usuarioId))
        servicio.guardarObjetivo(usuarioId, "Backend Developer", null)
        assertNull(servicio.obtenerOnboarding(usuarioId)) // falta área y nivel
    }

    @Test
    fun `guardar onboarding valida area, nivel y cargo`() {
        fallaCon<ErrorValidacion>("area_invalida") { servicio.guardarOnboarding(usuarioId, "Astronauta", "jr", "X") }
        fallaCon<ErrorValidacion>("nivel_experiencia_invalido") { servicio.guardarOnboarding(usuarioId, "TI", "experto", "X") }
        fallaCon<ErrorValidacion>("nombre_cargo_requerido") { servicio.guardarOnboarding(usuarioId, "TI", "jr", "  ") }
        fallaCon<ErrorValidacion>("nombre_cargo_invalido") { servicio.guardarOnboarding(usuarioId, "TI", "jr", "x".repeat(121)) }
    }

    @Test
    fun `objetivo inexistente responde objetivo_not_found`() {
        fallaCon<ErrorNoEncontrado>("objetivo_not_found") { servicio.obtenerObjetivo(usuarioId) }
        fallaCon<ErrorNoEncontrado>("objetivo_not_found") { servicio.eliminarObjetivo(usuarioId) }
    }

    @Test
    fun `eliminar objetivo lo desactiva`() = runBlocking<Unit> {
        servicio.guardarObjetivo(usuarioId, "Backend Developer", "TI")
        servicio.eliminarObjetivo(usuarioId)
        fallaCon<ErrorNoEncontrado>("objetivo_not_found") { servicio.obtenerObjetivo(usuarioId) }
    }
}
