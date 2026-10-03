package ESQUEMAS

import SERVICIOS.ParTokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los @SerialName mantienen el JSON que ya consume la app Android.

@Serializable
data class SolicitudLogin(
    @SerialName("email") val correo: String,
    @SerialName("password") val contrasena: String
)

@Serializable
data class SolicitudLoginGoogle(
    @SerialName("idToken") val idTokenGoogle: String
)

@Serializable
data class SolicitudRefresh(
    @SerialName("refreshToken") val tokenRefresco: String
)

@Serializable
data class RespuestaSesion(
    @SerialName("accessToken") val tokenAcceso: String,
    @SerialName("refreshToken") val tokenRefresco: String
)

/** El flujo web (callback OAuth) ya respondía en snake_case. */
@Serializable
data class RespuestaSesionWeb(
    @SerialName("access_token") val tokenAcceso: String,
    @SerialName("refresh_token") val tokenRefresco: String
)

fun ParTokens.aRespuesta() = RespuestaSesion(tokenAcceso, tokenRefresco)

fun ParTokens.aRespuestaWeb() = RespuestaSesionWeb(tokenAcceso, tokenRefresco)
