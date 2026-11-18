package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Administración del banco de preguntas ----------

@Serializable
data class SolicitudOpcion(
    val texto: String,
    val esCorrecta: Boolean = false,
    val explicacion: String? = null
)

/** Crear o editar una pregunta. Para opcion_multiple: entre 2 y 6 opciones y exactamente una correcta. */
@Serializable
data class SolicitudPregunta(
    val skillId: String? = null,
    val cargoId: String? = null,
    val tipo: String,
    val categoria: String,
    val nivel: String,
    val enunciado: String,
    val respuestaIdeal: String? = null,
    val rubrica: JsonObject? = null,
    val opciones: List<SolicitudOpcion> = emptyList()
)

@Serializable
data class SolicitudRechazo(
    val motivo: String
)

@Serializable
data class RespuestaOpcion(
    val id: String,
    val texto: String,
    val esCorrecta: Boolean,
    val explicacion: String?,
    val orden: Int
)

@Serializable
data class RespuestaPregunta(
    val id: String,
    val skillId: String?,
    val cargoId: String?,
    val tipo: String,
    val categoria: String,
    val nivel: String,
    val enunciado: String,
    val respuestaIdeal: String?,
    val rubrica: JsonObject?,
    val generadaPorIa: Boolean,
    val estado: String,
    val motivoRechazo: String?,
    val vecesUsada: Int,
    val fechaCreacion: String,
    val opciones: List<RespuestaOpcion>
)

@Serializable
data class RespuestaPaginaPreguntas(
    val elementos: List<RespuestaPregunta>,
    val total: Long,
    val pagina: Int,
    val tamano: Int
)

// ---------- Lectura para usuarios: sin respuesta ideal ni opción correcta ----------

@Serializable
data class RespuestaOpcionPublica(
    val id: String,
    val texto: String,
    val orden: Int
)

@Serializable
data class RespuestaPreguntaPublica(
    val id: String,
    val tipo: String,
    val categoria: String,
    val nivel: String,
    val enunciado: String,
    val opciones: List<RespuestaOpcionPublica>
)

// ---------- Generación con IA (snake_case: contrato existente del panel admin) ----------

@Serializable
data class SolicitudGenerarPreguntas(
    @SerialName("cargo_id") val cargoId: String? = null,
    @SerialName("skill_id") val skillId: String? = null,
    val nivel: String,
    val cantidad: Int = 5,
    val tipo: String = "abierta_texto",
    val categoria: String = "tecnica",
    val modelo: String = "gpt-4o-mini",
    /** Con cargo o skill, el prompt lleva lo que piden las ofertas de empleo reales (D26). false = como antes. */
    @SerialName("usar_mercado") val usarMercado: Boolean = true
)

@Serializable
data class RespuestaPreguntaGenerada(
    @SerialName("pregunta_id") val preguntaId: String,
    @SerialName("generacion_id") val generacionId: String,
    val enunciado: String,
    val estado: String,
    @SerialName("tokens_input") val tokensEntrada: Int?,
    @SerialName("tokens_output") val tokensSalida: Int?,
    @SerialName("costo_usd") val costoUsd: Double?
)

@Serializable
data class RespuestaSkillEnOfertas(
    val nombre: String,
    val ofertas: Int
)

@Serializable
data class RespuestaFragmentoOferta(
    val texto: String,
    @SerialName("titulo_oferta") val tituloOferta: String,
    val fuente: String
)

/**
 * Qué ofertas de empleo se le pasaron a la IA, o por qué no se usaron (motivo + mensaje para el admin). Sin valores
 * por defecto: con encodeDefaults = false un campo con su valor por defecto no viajaría, y el panel lee todos.
 */
@Serializable
data class RespuestaContextoMercado(
    val usado: Boolean,
    val motivo: String?,
    val mensaje: String?,
    val consulta: String?,
    @SerialName("ofertas_analizadas") val ofertasAnalizadas: Int,
    val fuentes: List<String>,
    @SerialName("es_respaldo") val esRespaldo: Boolean,
    @SerialName("skills_frecuentes") val skillsFrecuentes: List<RespuestaSkillEnOfertas>,
    val fragmentos: List<RespuestaFragmentoOferta>
)

@Serializable
data class RespuestaGenerarPreguntas(
    @SerialName("preguntas_generadas") val preguntasGeneradas: Int,
    @SerialName("preguntas_con_error") val preguntasConError: Int,
    val preguntas: List<RespuestaPreguntaGenerada>,
    @SerialName("contexto_mercado") val contextoMercado: RespuestaContextoMercado? = null
)

/** Filtros que llegan como query params; el servicio los valida. */
data class ConsultaPreguntas(
    val estado: String? = null,
    val tipo: String? = null,
    val categoria: String? = null,
    val nivel: String? = null,
    val skillId: String? = null,
    val cargoId: String? = null,
    /** De este cargo o sin cargo (D26). */
    val paraCargoId: String? = null,
    val generadaPorIa: Boolean? = null,
    val pagina: Int? = null,
    val tamano: Int? = null
)
