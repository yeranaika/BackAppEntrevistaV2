package MODELOS

import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val ESTADO_INACTIVO = "inactivo"
const val ORIGEN_LOCAL = "local"
const val ORIGEN_GOOGLE = "google"
const val IDIOMA_POR_DEFECTO = "es"

data class Usuario(
    val id: UUID,
    val correo: String,
    val nombre: String?,
    val idioma: String,
    val rol: String,
    val estado: String,
    val telefono: String?,
    val origenRegistro: String,
    val fechaCreacion: LocalDateTime,
    val fechaUltimoLogin: LocalDateTime?,
    val fechaNacimiento: LocalDate?,
    val genero: String?
)

data class NuevoUsuario(
    val correo: String,
    val hashContrasena: String,
    val nombre: String?,
    val idioma: String,
    val rol: String = ROL_USUARIO,
    val telefono: String? = null,
    val fechaNacimiento: LocalDate? = null,
    val genero: String? = null
)

/** Datos de cuenta que el usuario puede cambiar. `null` en un campo = no tocarlo. */
data class CambiosCuenta(
    val nombre: String? = null,
    val idioma: String? = null,
    val telefono: Cambio<String>? = null,
    val fechaNacimiento: Cambio<LocalDate>? = null,
    val genero: Cambio<String>? = null
) {
    val estaVacio: Boolean
        get() = nombre == null && idioma == null && telefono == null && fechaNacimiento == null && genero == null
}

/** Distingue "no tocar" (null) de "borrar" (Cambio(null)) en campos opcionales. */
data class Cambio<T>(val valor: T?)

/**
 * Nivel de experiencia. La base de datos solo acepta [valorBd]; la app Android trabaja con [codigoApp].
 */
enum class NivelExperiencia(val valorBd: String, val codigoApp: String, private val sinonimos: Set<String>) {
    JUNIOR("junior", "jr", setOf("jr", "junior", "estoy empezando en este tema")),
    SEMISENIOR("semisenior", "mid", setOf("mid", "semisenior", "semi senior", "ssr", "tengo experiencia intermedia")),
    SENIOR("senior", "sr", setOf("sr", "senior", "tengo mucha experiencia"));

    companion object {
        fun desdeTexto(texto: String): NivelExperiencia? {
            val normalizado = texto.trim().lowercase()
            return entries.firstOrNull { normalizado in it.sinonimos }
        }

        fun desdeBd(valor: String?): NivelExperiencia? = entries.firstOrNull { it.valorBd == valor }
    }
}

data class Perfil(
    val usuarioId: UUID,
    val nivelExperiencia: NivelExperiencia?,
    val area: String?,
    val pais: String?,
    val notaObjetivos: String?,
    val flagsAccesibilidad: JsonElement?
)

/** Campos del perfil a guardar. `null` = no tocar. */
data class CambiosPerfil(
    val nivelExperiencia: NivelExperiencia? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
) {
    val estaVacio: Boolean
        get() = nivelExperiencia == null && area == null && pais == null && notaObjetivos == null && flagsAccesibilidad == null
}

data class ObjetivoCarrera(
    val id: UUID,
    val nombreCargo: String,
    val sector: String?
)

data class CodigoRecuperacion(
    val token: UUID,
    val usuarioId: UUID,
    val codigo: String,
    val expiraEn: Instant,
    val intentosFallidos: Int
)
