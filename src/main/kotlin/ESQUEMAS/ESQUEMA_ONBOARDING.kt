package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PUT /me/objetivo */
@Serializable
data class SolicitudObjetivo(
    val nombreCargo: String,
    val sector: String? = null
)

@Serializable
data class RespuestaObjetivo(
    val id: String,
    val nombreCargo: String,
    val sector: String?
)

/** PUT /perfil/objetivo: lo usa el onboarding de la app Android. */
@Serializable
data class SolicitudObjetivoPerfil(
    val area: String,
    val metaCargo: String,
    val nivel: String
)

@Serializable
data class RespuestaEstado(
    val status: String
)

/** POST /onboarding */
@Serializable
data class SolicitudOnboarding(
    val area: String,
    val nivelExperiencia: String,
    val nombreCargo: String,
    val descripcionObjetivo: String? = null
)

@Serializable
data class RespuestaOnboarding(
    val area: String,
    /** Código de la app: jr, mid o sr. */
    val nivelExperiencia: String,
    val nombreCargo: String,
    val descripcionObjetivo: String?
)

@Serializable
data class RespuestaEstadoOnboarding(
    @SerialName("completed") val estaCompleto: Boolean,
    @SerialName("data") val datos: RespuestaOnboarding? = null
)

@Serializable
data class RespuestaGuardarOnboarding(
    @SerialName("success") val esExitoso: Boolean,
    @SerialName("message") val mensaje: String,
    @SerialName("data") val datos: RespuestaOnboarding
)
