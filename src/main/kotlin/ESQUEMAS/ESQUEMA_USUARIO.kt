package ESQUEMAS

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Los @SerialName mantienen el JSON que ya consume la app Android.

@Serializable
data class SolicitudRegistro(
    @SerialName("email") val correo: String,
    @SerialName("password") val contrasena: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null,
    val genero: String? = null,
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

/** Campo ausente = no tocar. En teléfono, fecha y género, "" significa borrar. */
@Serializable
data class SolicitudActualizarCuenta(
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val fechaNacimiento: String? = null,
    val genero: String? = null
)

@Serializable
data class SolicitudActualizarPerfil(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class SolicitudEliminarCuenta(
    val confirmar: String
)

@Serializable
data class RespuestaPerfil(
    /** Código de la app: jr, mid o sr. */
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class RespuestaCuenta(
    val id: String,
    @SerialName("email") val correo: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: String? = null,
    val genero: String? = null,
    /** "YYYY-MM-DD" */
    val fechaNacimiento: String? = null,
    val estado: String? = null,
    val origenRegistro: String? = null,
    val fechaUltimoLogin: String? = null,
    val perfil: RespuestaPerfil? = null,
    /** Cargo del objetivo activo. */
    val meta: String? = null
)
