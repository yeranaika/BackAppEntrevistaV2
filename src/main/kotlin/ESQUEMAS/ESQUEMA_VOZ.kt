package ESQUEMAS

import MODELOS.CorreccionTerminologia
import kotlinx.serialization.Serializable

// ---------- Transcripción por pregunta de la llamada (VOZ-3/VOZ-4) ----------

/** Solo texto: el audio nunca sale del teléfono. [parcial] = el reconocedor aún puede corregirla. */
@Serializable
data class SolicitudTranscripcion(
    val sesionId: String,
    val preguntaId: String,
    val inicioMs: Long,
    val finMs: Long,
    val texto: String,
    val idioma: String = "es",
    val confianza: Float? = null,
    val parcial: Boolean = false
)

/** Sin valores por defecto: el cliente lee todos los campos. */
@Serializable
data class RespuestaTranscripcion(
    val id: String,
    val sesionId: String,
    val preguntaId: String,
    val inicioMs: Long,
    val finMs: Long,
    val texto: String,
    val idioma: String,
    val confianza: Float?,
    val parcial: Boolean
)

@Serializable
data class RespuestaListaTranscripciones(val elementos: List<RespuestaTranscripcion>)

// ---------- Diccionarios de terminología (editables desde el panel y sincronizados con la app) ----------

/**
 * Lo que manda el panel para crear o reemplazar un paquete. Es el mismo JSON que devuelve GET, así que se puede exportar de
 * un ambiente e importar en otro. [versionEsperada] evita pisar la edición de otra persona (409 si ya cambió).
 */
@Serializable
data class SolicitudGuardarDiccionario(
    val nombre: String,
    val terminos: List<String>,
    val correcciones: List<CorreccionTerminologia> = emptyList(),
    val versionEsperada: Int? = null
)

/** Fila del manifiesto: la app compara [version] con la que tiene y solo baja los paquetes que cambiaron. */
@Serializable
data class RespuestaResumenDiccionario(
    val area: String,
    val nombre: String,
    val version: Int,
    val activo: Boolean,
    val totalTerminos: Int,
    val totalCorrecciones: Int
)

@Serializable
data class RespuestaListaDiccionarios(val elementos: List<RespuestaResumenDiccionario>)

@Serializable
data class RespuestaDiccionario(
    val area: String,
    val nombre: String,
    val version: Int,
    val activo: Boolean,
    val terminos: List<String>,
    val correcciones: List<CorreccionTerminologia>
)
