package PRUEBAS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorRespuestaExterna
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudGenerarPreguntas
import INTEGRACIONES.TipoProveedorIa
import MODELOS.CategoriaHabilidad
import MODELOS.NivelExperiencia
import MODELOS.TipoPregunta
import PRUEBAS.DOBLES.CatalogoEnMemoria
import PRUEBAS.DOBLES.GeneracionEnMemoria
import PRUEBAS.DOBLES.LoteLlmDePrueba
import PRUEBAS.DOBLES.ProveedorIaGrabador
import PRUEBAS.DOBLES.fallaCon
import SERVICIOS.ServicioGeneracionPregunta
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruebaServicioGeneracionPregunta {
    private val cargoId = UUID.randomUUID()
    private val skillId = UUID.randomUUID()
    private val catalogo = CatalogoEnMemoria(
        cargos = mutableMapOf(cargoId to "Backend Developer"),
        skills = mutableMapOf(skillId to "Kotlin")
    )

    private fun solicitud(
        cantidad: Int = 1,
        tipo: String = "abierta_texto",
        modelo: String = "gpt-4o-mini",
        cargo: String? = cargoId.toString(),
        skill: String? = null
    ) = SolicitudGenerarPreguntas(cargo, skill, nivel = "senior", cantidad = cantidad, tipo = tipo, categoria = "blanda", modelo = modelo)

    private fun servicio(
        proveedor: ProveedorIaGrabador,
        repositorio: GeneracionEnMemoria = GeneracionEnMemoria(),
        tope: SERVICIOS.TopeGastoGeneracionIa = SERVICIOS.TopeGastoGeneracionIa(reloj = PRUEBAS.DOBLES.RelojAjustable(java.time.Instant.parse("2026-11-15T12:00:00Z")))
    ) =
        ServicioGeneracionPregunta(
            mapOf(TipoProveedorIa.OPENAI to proveedor, TipoProveedorIa.ANTHROPIC to proveedor),
            repositorio,
            catalogo,
            tope
        )

    private fun uso(fecha: String, costo: Double?, exitoso: Boolean = true, tokensEntrada: Int? = null, tokensSalida: Int? = null) =
        MODELOS.UsoIaRegistrado(
            MODELOS.OrigenUsoIa.GENERACION_PREGUNTAS, java.time.Instant.parse(fecha), "gpt-4o-mini",
            tokensEntrada, tokensSalida, costo, exitoso
        )

    private fun topeEn(usd: Double, instante: String = "2026-11-15T12:00:00Z") =
        SERVICIOS.TopeGastoGeneracionIa(usd, PRUEBAS.DOBLES.RelojAjustable(java.time.Instant.parse(instante)))

    // ---------- Tope mensual de gasto (D23) ----------

    @Test
    fun `con el tope mensual agotado responde 429 sin llamar al LLM ni auditar`() {
        val repositorio = GeneracionEnMemoria().apply { usosPrevios += uso("2026-11-03T10:00:00Z", 5.0) }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        fallaCon<ERRORES.ErrorDemasiadosIntentos>("tope_generacion_ia_alcanzado") {
            servicio(proveedor, repositorio, topeEn(5.0)).generar(solicitud())
        }
        assertTrue(proveedor.solicitudes.isEmpty())
        assertTrue(repositorio.fallos.isEmpty())
    }

    @Test
    fun `por debajo del tope genera`() = runBlocking<Unit> {
        val repositorio = GeneracionEnMemoria().apply { usosPrevios += uso("2026-11-03T10:00:00Z", 4.99) }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        assertEquals(1, servicio(proveedor, repositorio, topeEn(5.0)).generar(solicitud()).size)
    }

    @Test
    fun `las llamadas fallidas cuentan con su costo estimado`() {
        // gpt-4o-mini: 1 M tokens de entrada = USD 0.15, sin costo guardado porque falló.
        val repositorio = GeneracionEnMemoria().apply {
            usosPrevios += uso("2026-11-03T10:00:00Z", costo = null, exitoso = false, tokensEntrada = 1_000_000, tokensSalida = 0)
        }
        fallaCon<ERRORES.ErrorDemasiadosIntentos>("tope_generacion_ia_alcanzado") {
            servicio(ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto")), repositorio, topeEn(0.15)).generar(solicitud())
        }
    }

    @Test
    fun `el gasto del mes anterior no cuenta y el mes se corta en UTC`() = runBlocking<Unit> {
        val repositorio = GeneracionEnMemoria().apply { usosPrevios += uso("2026-10-31T23:59:59Z", 10.0) }
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        servicio(proveedor, repositorio, topeEn(5.0, "2026-11-01T00:00:00Z")).generar(solicitud())
        assertEquals(1, proveedor.solicitudes.size)
        assertEquals(
            java.time.Instant.parse("2026-11-01T00:00:00Z") to java.time.Instant.parse("2026-12-01T00:00:00Z"),
            repositorio.rangosConsultados.single()
        )
    }

    @Test
    fun `tope 0 desactiva la generacion con IA y lo dice sin prometer que se repone`() {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        val error = kotlin.test.assertFailsWith<ERRORES.ErrorDemasiadosIntentos> {
            runBlocking { servicio(proveedor, GeneracionEnMemoria(), topeEn(0.0)).generar(solicitud()) }
        }
        assertEquals("tope_generacion_ia_alcanzado", error.codigo)
        assertTrue(error.message!!.contains("desactivada"))
        assertTrue(!error.message!!.contains("repone"))
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    @Test
    fun `el mensaje del tope muestra el monto sin notacion cientifica y cuando se repone`() {
        val repositorio = GeneracionEnMemoria().apply { usosPrevios += uso("2026-11-03T10:00:00Z", 0.001) }
        val error = kotlin.test.assertFailsWith<ERRORES.ErrorDemasiadosIntentos> {
            runBlocking { servicio(ProveedorIaGrabador(), repositorio, topeEn(0.0001)).generar(solicitud()) }
        }
        assertTrue(error.message!!.contains("USD 0.0001"), error.message)
        assertTrue(error.message!!.contains("2026-12-01"), error.message)
    }

    @Test
    fun `el tope se lee con coma decimal y un valor ilegible desactiva la generacion`() {
        assertEquals(CONFIGURACION.GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO, CONFIGURACION.leerTopeGastoUsd(null))
        assertEquals(CONFIGURACION.GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO, CONFIGURACION.leerTopeGastoUsd("  "))
        assertEquals(0.5, CONFIGURACION.leerTopeGastoUsd("0,5"))
        assertEquals(12.0, CONFIGURACION.leerTopeGastoUsd(" 12 "))
        listOf("1.5 USD", "-3", "NaN", "Infinity").forEach { assertEquals(0.0, CONFIGURACION.leerTopeGastoUsd(it), it) }
    }

    @Test
    fun `la validacion del pedido va antes del tope`() {
        val repositorio = GeneracionEnMemoria().apply { usosPrevios += uso("2026-11-03T10:00:00Z", 50.0) }
        fallaCon<ErrorValidacion>("cantidad_invalida") {
            servicio(ProveedorIaGrabador(), repositorio, topeEn(5.0)).generar(solicitud(cantidad = 11))
        }
    }

    @Test
    fun `un rechazo con tokens queda auditado con esos tokens y un error HTTP sin ellos`() {
        val conTokens = GeneracionEnMemoria()
        fallaCon<ErrorRespuestaExterna>("provider_refusal") {
            servicio(ProveedorIaGrabador(error = ErrorRespuestaExterna("provider_refusal", tokensEntrada = 50, tokensSalida = 7)), conTokens)
                .generar(solicitud())
        }
        assertEquals(50, conTokens.fallos.single().tokensEntrada)
        assertEquals(7, conTokens.fallos.single().tokensSalida)

        val sinTokens = GeneracionEnMemoria()
        fallaCon<ErrorRespuestaExterna>("provider_http_error") {
            servicio(ProveedorIaGrabador(error = ErrorRespuestaExterna("provider_http_error")), sinTokens).generar(solicitud())
        }
        assertEquals(null, sinTokens.fallos.single().tokensEntrada)
    }

    // ---------- Flujo correcto ----------

    @Test
    fun `alias antiguo de Claude genera el lote, reparte tokens y calcula el costo`() = runBlocking<Unit> {
        val repositorio = GeneracionEnMemoria()
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(10, "opcion_multiple"), tokensEntrada = 101, tokensSalida = 203)

        val creadas = servicio(proveedor, repositorio).generar(solicitud(10, "opcion_multiple", "claude-3-5-haiku"))

        assertEquals(10, creadas.size)
        assertEquals("claude-haiku-4-5", proveedor.solicitudes.single().modelo)
        val (traza, preguntas) = repositorio.lotes.single()
        assertEquals("claude-haiku-4-5", traza.modelo)
        assertEquals(101, preguntas.sumOf { it.tokensEntrada ?: 0 })
        assertEquals(203, preguntas.sumOf { it.tokensSalida ?: 0 })
        assertTrue(abs(0.001116 - preguntas.sumOf { it.costoUsd ?: 0.0 }) < 1e-9)
        assertTrue(preguntas.all { it.contenido.categoria == CategoriaHabilidad.BLANDA && it.contenido.nivel == NivelExperiencia.SENIOR })
        assertTrue(preguntas.all { it.contenido.opciones.size == 4 })
    }

    @Test
    fun `el prompt incluye el nombre del cargo y de la skill`() = runBlocking<Unit> {
        // Antes la ruta no enviaba el contexto y el LLM recibía "Preguntas generales".
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        servicio(proveedor).generar(solicitud(skill = skillId.toString()))

        val prompt = proveedor.solicitudes.single().prompt
        assertTrue(prompt.contains("Cargo objetivo: Backend Developer"), prompt)
        assertTrue(prompt.contains("Skill evaluada: Kotlin"), prompt)
    }

    // ---------- Validación antes de gastar una llamada ----------

    @Test
    fun `cargo o skill inexistente responde 404 sin llamar al LLM`() {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        fallaCon<ErrorNoEncontrado>("cargo_no_encontrado") { servicio(proveedor).generar(solicitud(cargo = UUID.randomUUID().toString())) }
        fallaCon<ErrorNoEncontrado>("skill_no_encontrada") {
            servicio(proveedor).generar(solicitud(cargo = null, skill = UUID.randomUUID().toString()))
        }
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    @Test
    fun `solicitudes invalidas no llaman al LLM`() {
        val proveedor = ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto"))
        val s = servicio(proveedor)
        fallaCon<ErrorValidacion>("invalid_uuid") { s.generar(solicitud(cargo = "no-es-uuid")) }
        fallaCon<ErrorValidacion>("contexto_requerido") { s.generar(solicitud(cargo = null)) }
        fallaCon<ErrorValidacion>("cantidad_invalida") { s.generar(solicitud(cantidad = 11)) }
        fallaCon<ErrorValidacion>("modelo_invalido") { s.generar(solicitud(modelo = "gpt-5-turbo")) }
        fallaCon<ErrorValidacion>("tipo_invalido") { s.generar(solicitud(tipo = "verdadero_falso")) }
        assertTrue(proveedor.solicitudes.isEmpty())
    }

    // ---------- Respuestas inutilizables del LLM ----------

    @Test
    fun `lotes que no cumplen las reglas se descartan y quedan auditados`() {
        val casosInvalidos = listOf(
            "abierta_texto" to LoteLlmDePrueba.json(2, "abierta_texto"), // cantidad distinta a la pedida
            "abierta_texto" to "```json\n${LoteLlmDePrueba.json(1, "abierta_texto")}\n```",
            "abierta_texto" to LoteLlmDePrueba.json(1, "abierta_texto", criterios = listOf("Situación", "Tarea", "Acción")),
            "abierta_texto" to LoteLlmDePrueba.json(1, "abierta_texto", respuestaIdeal = "Situación Tarea Acción Resultado en una sola frase sin estructura"),
            "opcion_multiple" to LoteLlmDePrueba.json(1, "opcion_multiple", opcionesCorrectas = 2),
            "opcion_multiple" to LoteLlmDePrueba.json(1, "opcion_multiple", opcionesDuplicadas = true),
            "abierta_texto" to LoteLlmDePrueba.json(1, "abierta_texto").replace("\"opciones\":null", "\"opciones\":null,\"extra\":1")
        )
        casosInvalidos.forEach { (tipo, contenido) ->
            val repositorio = GeneracionEnMemoria()
            fallaCon<ErrorRespuestaExterna>("provider_invalid_output") {
                servicio(ProveedorIaGrabador(contenido), repositorio).generar(solicitud(tipo = tipo))
            }
            assertTrue(repositorio.lotes.isEmpty(), contenido)
            assertEquals(contenido, repositorio.fallos.single().respuestaCruda)
        }
    }

    @Test
    fun `enunciados duplicados ignorando mayusculas y espacios se rechazan`() {
        val contenido = LoteLlmDePrueba.json(2, "abierta_texto", enunciado = { i -> if (i == 1) "  Pregunta Duplicada " else "pregunta   duplicada" })
        fallaCon<ErrorRespuestaExterna>("provider_invalid_output") { servicio(ProveedorIaGrabador(contenido)).generar(solicitud(2)) }
    }

    @Test
    fun `una falla del proveedor queda auditada y llega tal cual al cliente`() {
        val repositorio = GeneracionEnMemoria()
        val proveedor = ProveedorIaGrabador(error = ErrorRespuestaExterna("provider_refusal"))
        fallaCon<ErrorRespuestaExterna>("provider_refusal") { servicio(proveedor, repositorio).generar(solicitud()) }
        assertEquals(1, repositorio.fallos.size)
    }

    @Test
    fun `sin API key no hay llamada que auditar`() {
        val repositorio = GeneracionEnMemoria()
        val proveedor = ProveedorIaGrabador(error = ErrorServicioExterno("provider_not_configured"))
        fallaCon<ErrorServicioExterno>("provider_not_configured") { servicio(proveedor, repositorio).generar(solicitud()) }
        assertTrue(repositorio.fallos.isEmpty())
    }

    @Test
    fun `si la auditoria falla se informa el error original y no un 500`() {
        val repositorio = GeneracionEnMemoria(fallaAlAuditar = true)
        fallaCon<ErrorRespuestaExterna>("provider_invalid_output") {
            servicio(ProveedorIaGrabador("no es json"), repositorio).generar(solicitud())
        }
    }

    @Test
    fun `las preguntas de IA guardan rubrica STAR y respuesta ideal`() = runBlocking<Unit> {
        val repositorio = GeneracionEnMemoria()
        servicio(ProveedorIaGrabador(LoteLlmDePrueba.json(1, "abierta_texto")), repositorio).generar(solicitud())

        val contenido = repositorio.lotes.single().second.single().contenido
        assertEquals(TipoPregunta.ABIERTA_TEXTO, contenido.tipo)
        assertEquals(LoteLlmDePrueba.RESPUESTA_STAR, contenido.respuestaIdeal)
        assertEquals("\"STAR\"", contenido.rubrica!!["metodo"].toString())
    }
}
