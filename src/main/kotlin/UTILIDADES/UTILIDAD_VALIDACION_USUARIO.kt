package UTILIDADES

import CONFIGURACION.LARGO_MAXIMO_CONTRASENA
import CONFIGURACION.LARGO_MINIMO_CONTRASENA
import ERRORES.ErrorValidacion
import MODELOS.NivelExperiencia
import java.time.LocalDate
import java.time.format.DateTimeParseException

// Los códigos de error son los que ya conoce la app Android; no cambiarlos sin actualizarla.

private val REGEX_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val REGEX_PAIS = Regex("^[A-Z]{2}$")
private val REGEX_TELEFONO = Regex("^\\+?[0-9]{7,20}$")
private val REGEX_CODIGO_RECUPERACION = Regex("^[0-9]{6}$")

private val IDIOMAS_PERMITIDOS = setOf("es", "en", "pt", "fr", "de")
private val GENEROS_PERMITIDOS = setOf("masculino", "femenino", "no_binario", "otro", "prefiere_no_decirlo")

// Textos que muestra la app (incluido "Desarollador", tal cual lo envía) y códigos cortos.
private val AREAS_PERMITIDAS = setOf(
    "TI", "Desarollador", "Analista", "Administracion", "Otra área", "Ventas / Comercial",
    "Finanzas", "RRHH / Personas", "Diseño / UX", "Operaciones / Logística",
    "tec", "soft", "mix", "_"
)

private const val LARGO_MAXIMO_CORREO = 320
private const val LARGO_MAXIMO_NOMBRE = 120
private const val LARGO_MAXIMO_CARGO = 120
private const val LARGO_MAXIMO_SECTOR = 50
private const val EDAD_MINIMA_ANIOS = 14L
private val FECHA_NACIMIENTO_MINIMA: LocalDate = LocalDate.of(1900, 1, 1)

/** Normaliza (sin espacios, minúsculas) y valida el formato. */
fun validarCorreo(correo: String, codigo: String = "invalid_email"): String {
    val normalizado = correo.trim().lowercase()
    if (normalizado.length > LARGO_MAXIMO_CORREO || !REGEX_CORREO.matches(normalizado)) {
        throw ErrorValidacion(codigo, "El correo no tiene un formato válido")
    }
    return normalizado
}

fun validarNuevaContrasena(contrasena: String, codigo: String = "weak_password") {
    if (contrasena.length < LARGO_MINIMO_CONTRASENA) {
        throw ErrorValidacion(codigo, "La contraseña debe tener al menos $LARGO_MINIMO_CONTRASENA caracteres")
    }
    if (contrasena.length > LARGO_MAXIMO_CONTRASENA) {
        throw ErrorValidacion("password_too_long", "La contraseña no puede superar $LARGO_MAXIMO_CONTRASENA caracteres")
    }
}

fun validarNombre(nombre: String): String {
    val limpio = nombre.trim()
    if (limpio.length > LARGO_MAXIMO_NOMBRE) {
        throw ErrorValidacion("nombre_invalido", "El nombre no puede superar $LARGO_MAXIMO_NOMBRE caracteres")
    }
    return limpio
}

fun validarIdioma(idioma: String): String {
    if (idioma !in IDIOMAS_PERMITIDOS) throw ErrorValidacion("idioma_invalido", "Idioma no soportado")
    return idioma
}

fun validarTelefono(telefono: String): String {
    val limpio = telefono.trim()
    if (!REGEX_TELEFONO.matches(limpio)) throw ErrorValidacion("telefono_invalido", "El teléfono no tiene un formato válido")
    return limpio
}

fun validarGenero(genero: String): String {
    val limpio = genero.trim()
    if (limpio !in GENEROS_PERMITIDOS) throw ErrorValidacion("genero_invalido", "Género no válido")
    return limpio
}

/** Fecha "YYYY-MM-DD" entre 1900 y hace [EDAD_MINIMA_ANIOS] años. */
fun validarFechaNacimiento(
    texto: String,
    hoy: LocalDate,
    codigoFormato: String = "fecha_nacimiento_invalida",
    codigoRango: String = "fecha_nacimiento_fuera_de_rango"
): LocalDate {
    val fecha = try {
        LocalDate.parse(texto.trim())
    } catch (_: DateTimeParseException) {
        throw ErrorValidacion(codigoFormato, "La fecha de nacimiento debe tener formato AAAA-MM-DD")
    }
    if (fecha.isBefore(FECHA_NACIMIENTO_MINIMA) || fecha.isAfter(hoy.minusYears(EDAD_MINIMA_ANIOS))) {
        throw ErrorValidacion(codigoRango, "La fecha de nacimiento no es válida")
    }
    return fecha
}

/** Código de país ISO de 2 letras, devuelto en mayúsculas. */
fun validarPais(pais: String, codigo: String = "pais_invalido"): String {
    val normalizado = pais.trim().uppercase()
    if (!REGEX_PAIS.matches(normalizado)) throw ErrorValidacion(codigo, "El país debe ser un código de 2 letras")
    return normalizado
}

fun validarArea(area: String): String {
    val limpia = area.trim()
    if (limpia !in AREAS_PERMITIDAS) throw ErrorValidacion("area_invalida", "Área no válida")
    return limpia
}

fun validarNivel(nivel: String): NivelExperiencia =
    NivelExperiencia.desdeTexto(nivel)
        ?: throw ErrorValidacion("nivel_experiencia_invalido", "Nivel de experiencia no válido")

fun validarCargo(nombreCargo: String): String {
    val limpio = nombreCargo.trim()
    if (limpio.isEmpty()) throw ErrorValidacion("nombre_cargo_requerido", "El cargo es obligatorio")
    if (limpio.length > LARGO_MAXIMO_CARGO) {
        throw ErrorValidacion("nombre_cargo_invalido", "El cargo no puede superar $LARGO_MAXIMO_CARGO caracteres")
    }
    return limpio
}

fun validarSector(sector: String): String {
    val limpio = sector.trim()
    if (limpio.length > LARGO_MAXIMO_SECTOR) {
        throw ErrorValidacion("sector_invalido", "El sector no puede superar $LARGO_MAXIMO_SECTOR caracteres")
    }
    return limpio
}

fun validarCodigoRecuperacion(codigo: String): String {
    val limpio = codigo.trim()
    if (!REGEX_CODIGO_RECUPERACION.matches(limpio)) throw ErrorValidacion("codigo_invalido", "Código inválido")
    return limpio
}

/** La app envía "" en campos que el usuario dejó en blanco: se tratan como ausentes. */
fun String?.sinVacios(): String? = this?.takeIf { it.isNotBlank() }
