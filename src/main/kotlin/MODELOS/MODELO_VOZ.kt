package MODELOS

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Transcripción (solo texto) de lo que la persona respondió a una pregunta durante la llamada. */
data class TranscripcionRespuesta(
    val id: UUID,
    val sesionId: UUID,
    val preguntaId: UUID,
    val inicioMs: Long,
    val finMs: Long,
    val texto: String,
    val idioma: String,
    val confianza: Float?,
    /** true mientras el reconocedor sigue corrigiendo: un parcial nunca reemplaza a un final ya guardado. */
    val parcial: Boolean
)

/** Resultado de guardar: [creada] es false si reemplazó una anterior o la ignoró por ser parcial. */
data class TranscripcionGuardada(val transcripcion: TranscripcionRespuesta, val creada: Boolean)

/** Corrección posterior al reconocimiento: "cotlin" → "Kotlin". */
@Serializable
data class CorreccionTerminologia(val de: String, val a: String)

/**
 * Paquete de terminología de un área (programador-front, ...). [terminos] sesgan al reconocedor de la app; [correcciones]
 * arreglan lo que aun así sale mal. [version] sube en cada edición y la app solo descarga los paquetes que cambiaron.
 */
data class DiccionarioTerminologia(
    val area: String,
    val nombre: String,
    val version: Int,
    val activo: Boolean,
    val terminos: List<String>,
    val correcciones: List<CorreccionTerminologia>,
    val actualizadoEn: Instant
)
