package PRUEBAS

import ERRORES.ErrorConflicto
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearCargo
import MODELOS.NuevaSkill
import MODELOS.TablaSkill
import PRUEBAS.DOBLES.ClienteMercadoLaboralFalso
import PRUEBAS.DOBLES.SistemaPrueba
import PRUEBAS.DOBLES.fallaCon
import PRUEBAS.DOBLES.oferta
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class PruebaServicioMercado {
    private val ofertasGenerales = listOf(
        oferta("Senior Backend Engineer", "Kotlin, Ktor y PostgreSQL. Kotlin en microservicios."),
        oferta("Backend Developer", "Kotlin y Docker")
    )
    private val ofertasAndroid = listOf(
        oferta("Senior Android Developer", "Kotlin, Jetpack Compose y coroutines"),
        oferta("Android Engineer", "Kotlin y Jetpack Compose. Trabajo en equipo.")
    )
    private val cliente = ClienteMercadoLaboralFalso(mapOf(null to ofertasGenerales, "Android Developer" to ofertasAndroid))
    private val sistema = SistemaPrueba(clienteMercado = cliente)

    private fun skill(nombre: String, categoria: String = "tecnica") = runBlocking {
        sistema.mercadoRepo.obtenerOCrearSkill(NuevaSkill(nombre, categoria, "backend", null, 50))
    }

    private fun demandaDe(nombre: String): Short =
        transaction { TablaSkill.selectAll().where { TablaSkill.nombre eq nombre }.single()[TablaSkill.demandaScore] }

    // ---------- Bug corregido: se analizaba un texto vacío ----------

    @Test
    fun `la sincronizacion usa el texto real de las ofertas`() = runBlocking<Unit> {
        val kotlin = skill("Kotlin")
        skill("React")

        val resultado = sistema.tendencias.sincronizar()

        // Kotlin aparece 3 veces (la que más) y React ninguna: antes ambas quedaban en 35.
        assertEquals(95, demandaDe("Kotlin").toInt())
        assertEquals(35, demandaDe("React").toInt())
        assertEquals(3, sistema.mercadoRepo.historialTendencias(kotlin, 10).single().frecuenciaOfertas)
        assertEquals("Kotlin", resultado.actualizadas.first().skill.nombre)
    }

    @Test
    fun `los requisitos de un cargo salen de las ofertas de ese cargo`() = runBlocking<Unit> {
        val cargo = sistema.mercado.crearCargo(SolicitudCrearCargo("Android Developer", "mobile", generarSkills = false)).first

        val generados = sistema.requisitos.generarPara(cargo.id)

        assertEquals(listOf<String?>("Android Developer"), cliente.consultas, "no debe caer a las ofertas genéricas")
        val nombres = generados.requisitos.map { it.skill.nombre }
        assertTrue("Jetpack Compose" in nombres, nombres.toString())
        assertTrue("PostgreSQL" !in nombres, "PostgreSQL solo está en las ofertas genéricas")
        val trabajoEnEquipo = generados.requisitos.single { it.skill.nombre == "Trabajo en equipo" }
        assertEquals("blanda", trabajoEnEquipo.skill.categoria)
    }

    @Test
    fun `si no hay ofertas del cargo se usan las generales`() = runBlocking<Unit> {
        val cargo = sistema.mercado.crearCargo(SolicitudCrearCargo("Cargo Raro", "backend", generarSkills = false)).first
        val generados = sistema.requisitos.generarPara(cargo.id)
        assertEquals(listOf("Cargo Raro", null), cliente.consultas)
        assertTrue(generados.requisitos.any { it.skill.nombre == "PostgreSQL" })
    }

    // ---------- Caché ----------

    @Test
    fun `listar cargos usa la cache y crear un cargo la invalida`() = runBlocking<Unit> {
        sistema.mercado.crearCargo(SolicitudCrearCargo("Backend Developer", "backend", generarSkills = false))
        assertEquals(1, sistema.mercado.listarCargos().size)
        sistema.mercado.listarCargos()
        assertEquals(1, sistema.cache.aciertos)

        sistema.mercado.crearCargo(SolicitudCrearCargo("Data Engineer", "data", generarSkills = false))
        assertEquals(2, sistema.mercado.listarCargos().size, "antes quedaba desactualizada hasta 6 horas")
    }

    @Test
    fun `sincronizar tendencias invalida la cache del mercado`() = runBlocking<Unit> {
        skill("Kotlin")
        sistema.mercado.tendencias(null, 10)
        assertTrue(sistema.cache.valores.keys.any { it.startsWith("mercado:tendencias") })
        sistema.tendencias.sincronizar()
        assertTrue(sistema.cache.valores.isEmpty())
    }

    // ---------- Validaciones ----------

    @Test
    fun `consultas y alta de cargos validan sus datos`() {
        fallaCon<ErrorNoEncontrado>("cargo_not_found") { sistema.mercado.matrizDeCargo(UUID.randomUUID()) }
        fallaCon<ErrorNoEncontrado>("skill_no_encontrada") { sistema.mercado.historialDeSkill(UUID.randomUUID()) }
        fallaCon<ErrorValidacion>("categoria_invalida") { sistema.mercado.tendencias("dura", null) }
        fallaCon<ErrorValidacion>("limite_invalido") { sistema.mercado.tendencias(null, 500) }
        fallaCon<ErrorValidacion>("nivel_invalido") {
            sistema.mercado.crearCargo(SolicitudCrearCargo("X", "backend", nivelBase = "experto", generarSkills = false))
        }
        runBlocking { sistema.mercado.crearCargo(SolicitudCrearCargo("Backend", "backend", generarSkills = false)) }
        fallaCon<ErrorConflicto>("cargo_existente") { sistema.mercado.crearCargo(SolicitudCrearCargo("Backend", "backend", generarSkills = false)) }
    }

    // ---------- Tarea programada ----------

    @Test
    fun `reiniciar el servidor no vuelve a sincronizar si la ultima fue reciente`() = runBlocking<Unit> {
        skill("Kotlin")
        assertEquals(kotlin.time.Duration.ZERO, sistema.tendencias.esperaHastaProximaSincronizacion(7.days))
        sistema.tendencias.sincronizar()
        assertTrue(sistema.tendencias.esperaHastaProximaSincronizacion(7.days) > 6.days)
    }
}
