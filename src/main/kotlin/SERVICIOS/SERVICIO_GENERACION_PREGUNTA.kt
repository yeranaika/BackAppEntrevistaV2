package SERVICIOS

import ERRORES.ErrorAplicacion
import ERRORES.ErrorRespuestaExterna
import ERRORES.ErrorServicioExterno
import ERRORES.ErrorValidacion
import ESQUEMAS.EsquemaJsonLoteLlm
import ESQUEMAS.PreguntaLlm
import ESQUEMAS.SolicitudGenerarPreguntas
import INTEGRACIONES.ProveedorPreguntasIa
import INTEGRACIONES.RespuestaProveedorIa
import INTEGRACIONES.SolicitudProveedorIa
import INTEGRACIONES.TipoProveedorIa
import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.IdsGenerados
import MODELOS.LectorCatalogo
import MODELOS.NivelExperiencia
import MODELOS.NuevaOpcion
import MODELOS.PreguntaGenerada
import MODELOS.RepositorioGeneracionPreguntaIa
import MODELOS.TipoPregunta
import MODELOS.TrazaGeneracion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.util.UUID

private const val CANTIDAD_MAXIMA_POR_LOTE = 10

/** Modelos permitidos → modelo real que se llama (alias antiguos incluidos). */
private val MODELOS_PERMITIDOS = mapOf(
    "gpt-4o-mini" to "gpt-4o-mini",
    "claude-haiku-4-5" to "claude-haiku-4-5",
    "claude-3-5-haiku" to "claude-haiku-4-5"
)

/** USD por token (entrada, salida). */
private val COSTO_POR_TOKEN = mapOf(
    "gpt-4o-mini" to (0.00000015 to 0.00000060),
    "claude-haiku-4-5" to (0.000001 to 0.000005)
)

data class PreguntaCreadaPorIa(
    val ids: IdsGenerados,
    val enunciado: String,
    val tokensEntrada: Int?,
    val tokensSalida: Int?,
    val costoUsd: Double?
)

/**
 * Genera preguntas con un LLM y las deja en el banco como pendientes de revisión.
 * Toda llamada queda auditada en pregunta_generacion_ia, incluso las que fallan.
 */
class ServicioGeneracionPregunta(
    private val proveedores: Map<TipoProveedorIa, ProveedorPreguntasIa>,
    private val repositorio: RepositorioGeneracionPreguntaIa,
    private val catalogo: LectorCatalogo
) {
    private val log = LoggerFactory.getLogger(ServicioGeneracionPregunta::class.java)
    private val jsonRubrica = Json { encodeDefaults = true }

    private data class PedidoValidado(
        val cargoId: UUID?,
        val skillId: UUID?,
        val nivel: NivelExperiencia,
        val tipo: TipoPregunta,
        val categoria: CategoriaHabilidad,
        val cantidad: Int,
        val modelo: String
    )

    suspend fun generar(solicitud: SolicitudGenerarPreguntas): List<PreguntaCreadaPorIa> {
        val pedido = validar(solicitud)
        // Se valida el catálogo antes de llamar al LLM: un id inexistente no debe costar una llamada.
        val nombreCargo = pedido.cargoId?.let { catalogo.nombreCargo(it) }
        val nombreSkill = pedido.skillId?.let { catalogo.nombreSkill(it) }
        val proveedor = proveedores[tipoProveedor(pedido.modelo)]
            ?: throw ErrorServicioExterno("provider_not_configured", "No hay proveedor para el modelo ${pedido.modelo}")

        val prompt = construirPrompt(pedido, nombreCargo, nombreSkill)
        val traza = TrazaGeneracion(pedido.cargoId, pedido.skillId, pedido.nivel, pedido.modelo, prompt, null, null, null)

        val respuesta = llamarProveedor(proveedor, pedido, prompt, traza)
        val trazaConRespuesta = traza.copy(
            respuestaCruda = respuesta.contenido,
            tokensEntrada = respuesta.tokensEntrada,
            tokensSalida = respuesta.tokensSalida
        )
        val preguntasLlm = try {
            ValidadorLotePreguntaIa.parsearYValidar(respuesta.contenido, pedido.cantidad, pedido.tipo)
        } catch (e: LoteInvalidoException) {
            log.warn("Lote de IA descartado: {}", e.message)
            registrarFalloSinOcultar(trazaConRespuesta)
            throw ErrorRespuestaExterna("provider_invalid_output", "La IA devolvió preguntas que no cumplen las reglas de calidad")
        }

        val generadas = conCostos(preguntasLlm.map { aContenido(it, pedido) }, pedido.modelo, respuesta)
        val ids = repositorio.guardarLote(trazaConRespuesta, generadas)
        return ids.zip(generadas) { id, generada ->
            PreguntaCreadaPorIa(id, generada.contenido.enunciado, generada.tokensEntrada, generada.tokensSalida, generada.costoUsd)
        }
    }

    private suspend fun validar(solicitud: SolicitudGenerarPreguntas): PedidoValidado {
        val nivel = leerNivel(solicitud.nivel)
        val tipo = leerTipo(solicitud.tipo)
        val categoria = leerCategoria(solicitud.categoria)
        val modelo = MODELOS_PERMITIDOS[solicitud.modelo]
            ?: throw ErrorValidacion("modelo_invalido", "Modelo no soportado: usa ${MODELOS_PERMITIDOS.keys.joinToString()}")
        if (solicitud.cantidad !in 1..CANTIDAD_MAXIMA_POR_LOTE) {
            throw ErrorValidacion("cantidad_invalida", "Se pueden generar entre 1 y $CANTIDAD_MAXIMA_POR_LOTE preguntas por vez")
        }
        val (cargoId, skillId) = validarContexto(solicitud.cargoId, solicitud.skillId, catalogo)
        return PedidoValidado(cargoId, skillId, nivel, tipo, categoria, solicitud.cantidad, modelo)
    }

    private suspend fun llamarProveedor(
        proveedor: ProveedorPreguntasIa,
        pedido: PedidoValidado,
        prompt: String,
        traza: TrazaGeneracion
    ): RespuestaProveedorIa = try {
        proveedor.generar(
            SolicitudProveedorIa(
                prompt = prompt,
                modelo = pedido.modelo,
                esquemaJson = EsquemaJsonLoteLlm.construir(pedido.cantidad, pedido.tipo),
                instruccionUsuario = "Genera exactamente ${pedido.cantidad} preguntas ahora. Responde solo el objeto JSON solicitado."
            )
        )
    } catch (e: ErrorAplicacion) {
        // Sin API key no hubo llamada que auditar.
        if (e.codigo != "provider_not_configured") registrarFalloSinOcultar(traza)
        throw e
    }

    /** Si la auditoría falla se registra en el log, pero se informa el error original al cliente. */
    private suspend fun registrarFalloSinOcultar(traza: TrazaGeneracion) {
        runCatching { repositorio.registrarFallo(traza) }
            .onFailure { log.error("No se pudo auditar la generación fallida", it) }
    }

    private fun aContenido(pregunta: PreguntaLlm, pedido: PedidoValidado) = ContenidoPregunta(
        skillId = pedido.skillId,
        cargoId = pedido.cargoId,
        tipo = pedido.tipo,
        categoria = pedido.categoria,
        nivel = pedido.nivel,
        enunciado = pregunta.enunciado.trim(),
        respuestaIdeal = pregunta.respuestaIdeal.trim(),
        rubrica = jsonRubrica.encodeToJsonElement(pregunta.rubrica).jsonObject,
        opciones = pregunta.opciones.orEmpty().map { NuevaOpcion(it.texto.trim(), it.esCorrecta, it.explicacion.trim()) }
    )

    /** Reparte los tokens de la llamada entre las preguntas para conocer el costo de cada una. */
    private fun conCostos(contenidos: List<ContenidoPregunta>, modelo: String, respuesta: RespuestaProveedorIa): List<PreguntaGenerada> {
        val entrada = repartir(respuesta.tokensEntrada, contenidos.size)
        val salida = repartir(respuesta.tokensSalida, contenidos.size)
        val (precioEntrada, precioSalida) = COSTO_POR_TOKEN[modelo] ?: (0.0 to 0.0)
        return contenidos.mapIndexed { i, contenido ->
            PreguntaGenerada(
                contenido = contenido,
                tokensEntrada = entrada[i],
                tokensSalida = salida[i],
                costoUsd = (entrada[i] ?: 0) * precioEntrada + (salida[i] ?: 0) * precioSalida
            )
        }
    }

    private fun repartir(total: Int?, partes: Int): List<Int?> {
        if (total == null) return List(partes) { null }
        val base = total / partes
        val resto = total % partes
        return List(partes) { i -> base + if (i < resto) 1 else 0 }
    }

    private fun tipoProveedor(modelo: String) = if (modelo.startsWith("gpt")) TipoProveedorIa.OPENAI else TipoProveedorIa.ANTHROPIC

    private fun construirPrompt(pedido: PedidoValidado, nombreCargo: String?, nombreSkill: String?): String {
        val contexto = buildString {
            if (nombreCargo != null) append("Cargo objetivo: $nombreCargo. ")
            if (nombreSkill != null) append("Skill evaluada: $nombreSkill. ")
        }
        return """
            Eres un experto en diseño de entrevistas y evaluación de competencias.
            Genera exactamente ${pedido.cantidad} preguntas válidas.

            CONTEXTO:
            - $contexto
            - Nivel: ${pedido.nivel.valorBd}
            - Tipo: ${pedido.tipo.valorBd}
            - Categoría: ${pedido.categoria.valorBd}

            Reglas obligatorias:
            - Devuelve únicamente un objeto JSON que cumpla el schema estricto entregado.
            - No uses markdown ni texto fuera del JSON.
            - Cada respuesta_ideal debe ser completa y cubrir Situación, Tarea, Acción y Resultado.
            - La rúbrica debe usar metodo STAR y criterios explícitos para Situación, Tarea, Acción y Resultado.
            - palabras_clave debe contener términos significativos.
            - Para opcion_multiple entrega exactamente cuatro opciones y una sola correcta.
            - Para tipos no opcion_multiple, opciones debe ser null.
        """.trimIndent()
    }
}
