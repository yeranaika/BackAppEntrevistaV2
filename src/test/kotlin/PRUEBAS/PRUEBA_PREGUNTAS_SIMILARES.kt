package PRUEBAS

import ESQUEMAS.SolicitudGenerarPreguntas
import INTEGRACIONES.TipoProveedorIa
import MODELOS.EstadoPregunta
import MODELOS.PreguntaExistente
import PRUEBAS.DOBLES.CatalogoEnMemoria
import PRUEBAS.DOBLES.GeneracionEnMemoria
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import SERVICIOS.DetectorPreguntasSimilares
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.ServicioTopeGeneracionIa
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaPreguntasSimilares {
    private fun existente(texto: String, estado: EstadoPregunta = EstadoPregunta.APROBADA) = PreguntaExistente(UUID.randomUUID(), texto, estado)

    private val deadlock = "¿Qué es un deadlock y cómo se puede evitar en una aplicación concurrente?"
    private val joins = "Explica la diferencia entre un INNER JOIN y un LEFT JOIN en SQL con un ejemplo"

    // ---------- Detector ----------

    @Test
    fun `una copia exacta o con otras mayusculas es casi 1`() {
        assertTrue(DetectorPreguntasSimilares.similitud(deadlock, deadlock) > 0.99)
        assertTrue(DetectorPreguntasSimilares.similitud(deadlock, deadlock.uppercase()) > 0.95)
    }

    @Test
    fun `las mismas palabras en otro orden pasan el umbral`() {
        val reordenada = "¿Cómo se puede evitar un deadlock y qué es en una aplicación concurrente?"
        assertTrue(DetectorPreguntasSimilares.similitud(deadlock, reordenada) >= 0.8)
    }

    @Test
    fun `un tema distinto queda bajo el umbral`() {
        assertTrue(DetectorPreguntasSimilares.similitud(deadlock, joins) < 0.5)
    }

    @Test
    fun `texto vacio no se parece a nada`() {
        assertEquals(0.0, DetectorPreguntasSimilares.similitud("", deadlock))
    }

    @Test
    fun `separar descarta las parecidas al banco y a otra del mismo lote`() {
        val banco = listOf(existente(deadlock))
        val resultado = DetectorPreguntasSimilares.separar(listOf(deadlock, joins, joins), banco, 0.8)
        assertNotNull(resultado[0])
        assertEquals(banco[0].id, resultado[0]!!.id)
        assertNull(resultado[1])
        assertNotNull(resultado[2])
        assertNull(resultado[2]!!.id) // parecida a otra del lote, no del banco
    }

    @Test
    fun `umbral 0 apaga la deteccion y el borde exacto cuenta`() {
        val banco = listOf(existente(deadlock))
        assertNull(DetectorPreguntasSimilares.separar(listOf(deadlock), banco, 0.0)[0])
        val exacto = DetectorPreguntasSimilares.similitud(deadlock, deadlock)
        assertNotNull(DetectorPreguntasSimilares.separar(listOf(deadlock), banco, exacto)[0])
    }

    @Test
    fun `grupos junta las parecidas y deja solas las distintas`() {
        val a = existente(deadlock)
        val b = existente("$deadlock Explícalo")
        val c = existente("Qué es una transacción ACID en bases de datos")
        val grupos = DetectorPreguntasSimilares.grupos(listOf(a, b, c), 0.8)
        assertEquals(1, grupos.size)
        assertEquals(setOf(a.id, b.id), grupos[0].preguntas.map { it.id }.toSet())
    }

    // ---------- Generación ----------

    private val cargoId = UUID.randomUUID()
    private val catalogo = CatalogoEnMemoria(cargos = mutableMapOf(cargoId to "Backend"))
    private val conflicto = "Cuéntame cómo resolviste un conflicto grave dentro de tu equipo de trabajo"

    private fun servicio(proveedor: ProveedorIaGrabador, repo: GeneracionEnMemoria, umbral: Double = 0.8) = ServicioGeneracionPregunta(
        mapOf(TipoProveedorIa.OPENAI to proveedor), repo, catalogo,
        ServicioTopeGeneracionIa(repo, SERVICIOS.TopeGastoGeneracionIa(reloj = PRUEBAS.DOBLES.RelojAjustable(java.time.Instant.parse("2026-11-15T12:00:00Z")))),
        umbralSimilitud = umbral
    )

    private fun pedido(cantidad: Int) =
        SolicitudGenerarPreguntas(cargoId.toString(), null, nivel = "senior", cantidad = cantidad, tipo = "abierta_texto", categoria = "blanda", modelo = "gpt-4o-mini")

    @Test
    fun `una pregunta parecida al banco se descarta y las demas se guardan`() = runBlocking<Unit> {
        val repo = GeneracionEnMemoria().apply { filtraSimilares = true; existentes += existente(deadlock) }
        val textos = listOf(deadlock, conflicto)
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(2, "abierta_texto", enunciado = { textos[it - 1] }))
        val resultado = servicio(proveedor, repo).generarConMercado(pedido(2))
        assertEquals(1, resultado.preguntas.size)
        assertEquals(conflicto, resultado.preguntas[0].enunciado)
        assertEquals(deadlock, resultado.descartadas.single().enunciado)
        assertEquals(1, repo.lotes.single().second.size)
        assertTrue(repo.fallos.isEmpty())
    }

    @Test
    fun `si todas se parecen no se guarda nada pero se audita para que el gasto cuente`() = runBlocking<Unit> {
        val repo = GeneracionEnMemoria().apply { filtraSimilares = true; existentes += existente(deadlock) }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto", enunciado = { deadlock }))
        val resultado = servicio(proveedor, repo).generarConMercado(pedido(1))
        assertTrue(resultado.preguntas.isEmpty())
        assertEquals(1, resultado.descartadas.size)
        assertTrue(repo.lotes.isEmpty())
        assertEquals(1, repo.fallos.size)
        assertEquals(100, repo.fallos[0].tokensEntrada)
    }

    @Test
    fun `con umbral 0 no se descarta ni se agregan existentes al prompt`() = runBlocking<Unit> {
        val repo = GeneracionEnMemoria().apply { filtraSimilares = true; existentes += existente(deadlock) }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto", enunciado = { deadlock }))
        val resultado = servicio(proveedor, repo, umbral = 0.0).generarConMercado(pedido(1))
        assertEquals(1, resultado.preguntas.size)
        assertTrue(resultado.descartadas.isEmpty())
        assertTrue("PREGUNTAS QUE YA EXISTEN" !in proveedor.solicitudes.single().prompt)
    }

    @Test
    fun `el prompt lleva los enunciados existentes como datos para no repetirlos`() = runBlocking<Unit> {
        val repo = GeneracionEnMemoria().apply { existentes += existente("Pregunta \"con comillas\"\ny salto de línea") }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        servicio(proveedor, repo).generarConMercado(pedido(1))
        val prompt = proveedor.solicitudes.single().prompt
        assertTrue("PREGUNTAS QUE YA EXISTEN" in prompt)
        assertTrue("Pregunta 'con comillas' y salto de línea" in prompt)
    }
}
