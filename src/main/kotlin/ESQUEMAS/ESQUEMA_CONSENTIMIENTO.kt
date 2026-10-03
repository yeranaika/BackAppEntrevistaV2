package ESQUEMAS

import kotlinx.serialization.Serializable

// JSON que ya consume la app Android (consent) y el panel admin (legal).

@Serializable
data class RespuestaTextoConsentimiento(
    val version: String,
    val title: String,
    val body: String
)

@Serializable
data class SolicitudPublicarTexto(
    val version: String,
    val title: String,
    val body: String
)

@Serializable
data class SolicitudConsentimiento(
    val version: String,
    val alcances: Map<String, Boolean>
)

@Serializable
data class RespuestaConsentimiento(
    val id: String,
    val version: String,
    val alcances: Map<String, Boolean>,
    val fechaOtorgado: String,
    val fechaRevocado: String? = null
)

@Serializable
data class RespuestaRevocacion(
    val revoked: Boolean
)

@Serializable
data class RespuestaDocumentoLegal(
    val title: String,
    val version: String,
    /** eula, terms o privacy */
    val type: String,
    val contentMarkdown: String,
    val vigente: Boolean,
    val fechaPublicacion: String? = null
)

@Serializable
data class RespuestaVersionEula(
    val version: String,
    val title: String,
    val vigente: Boolean,
    val fechaPublicacion: String? = null
)
