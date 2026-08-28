package routes.me

import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// ---------- DTOs ----------
@Serializable
data class MeRes(
    val id: String,
    val email: String,
    val nombre: String? = null,
    val idioma: String? = null,

    // Campos expuestos del usuario (tabla usuario)
    val telefono: String? = null,
    val genero: String? = null,
    val fechaNacimiento: String? = null,    // "YYYY-MM-DD"
    val estado: String? = null,
    val origenRegistro: String? = null,
    val fechaUltimoLogin: String? = null,

    val perfil: PerfilRes? = null,
    val meta: String? = null
)

@Serializable
data class PerfilRes(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class PutMeReq(
    val nombre: String? = null,
    val idioma: String? = null,

    // Campos nuevos editables del usuario
    val telefono: String? = null,
    val fechaNacimiento: String? = null,  // "YYYY-MM-DD" o null
    val genero: String? = null
)

@Serializable
data class PutPerfilReq(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class PutObjetivoReq(
    val nombreCargo: String,
    val sector: String? = null
)

@Serializable
data class ObjetivoRes(
    val id: String,
    val nombreCargo: String,
    val sector: String?
)

@Serializable
data class OkRes(val ok: Boolean = true)

@Serializable
data class ErrorRes(val error: String)

// ---------- Validaciones ----------
object Validaciones {

    private val NIVELES_APP = setOf(
        "Estoy empezando en este tema",
        "Tengo experiencia intermedia",
        "Tengo mucha experiencia"
    )

    private val NIVELES_CODIGO = setOf("jr", "mid", "sr", "otro")

    private val AREAS_APP = setOf(
        "TI",
        "Desarollador",   // igual que en la app
        "Analista",
        "Administracion",
        "Otra área",
        "Ventas / Comercial",
        "Finanzas",
        "RRHH / Personas",
        "Diseño / UX",
        "Operaciones / Logística"
    )

    private val AREAS_CODIGO = setOf("tec", "soft", "mix", "_")

    val IDIOMAS = setOf("es", "en", "pt", "fr", "de")

    fun validarNivelExperiencia(nivel: String?): String? {
        if (nivel == null) return null
        val ok = nivel in NIVELES_APP || nivel in NIVELES_CODIGO
        return if (ok) null else "nivel_experiencia_invalido"
    }

    fun validarArea(area: String?): String? {
        if (area == null) return null
        val ok = area in AREAS_APP || area in AREAS_CODIGO
        return if (ok) null else "area_invalida"
    }

    fun validarPais(pais: String?): String? {
        if (pais == null) return null
        val upper = pais.uppercase()
        return if (upper.matches(Regex("^[A-Z]{2}$"))) null else "pais_invalido"
    }

    fun validarIdioma(idioma: String?): String? {
        if (idioma == null) return null
        return if (idioma in IDIOMAS) null else "idioma_invalido"
    }
}

// ---------- Helper ----------
private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

// ---------- Routes ----------
fun Route.meRoutes(
    users: UserRepository,
    profiles: ProfileRepository,
    objetivos: ObjetivoCarreraRepository
) {
    authenticate("auth-jwt") {
        route("/me") {

            // GET /me
            get {
                val uid = call.userIdFromJwt()
                val u = users.findById(uid)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorRes("user_not_found"))

                val p = profiles.findByUser(uid)
                val obj = objetivos.findByUser(uid)

                val perfilRes = p?.let {
                    PerfilRes(
                        nivelExperiencia   = it.nivelExperiencia,
                        area               = it.area,
                        pais               = it.pais,
                        notaObjetivos      = it.notaObjetivos,
                        flagsAccesibilidad = it.flagsAccesibilidad
                    )
                }

                call.respond(
                    MeRes(
                        id = u.id.toString(),
                        email = u.email,
                        nombre = u.nombre,
                        idioma = u.idioma,

                        telefono = u.telefono,
                        genero = u.genero,
                        fechaNacimiento = u.fechaNacimiento?.toString(),   // "YYYY-MM-DD"
                        estado = u.estado,
                        origenRegistro = u.origenRegistro,
                        fechaUltimoLogin = u.fechaUltimoLogin?.toString(), // ISO

                        perfil = perfilRes,
                        meta = obj?.nombreCargo
                    )
                )
            }

            // PUT /me
            put {
                val uid = call.userIdFromJwt()
                val u = users.findById(uid)
                    ?: return@put call.respond(HttpStatusCode.NotFound, ErrorRes("user_not_found"))

                val req = runCatching { call.receive<PutMeReq>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
                }

                // Validar idioma
                val errorIdioma = Validaciones.validarIdioma(req.idioma)
                if (errorIdioma != null) {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes(errorIdioma))
                }

                // Validar teléfono: CHECK telefono ~ '^\+?[0-9]{7,20}$'
                val telefonoLimpio = req.telefono?.trim()?.takeIf { it.isNotEmpty() }
                if (telefonoLimpio != null && !telefonoLimpio.matches(Regex("^\\+?[0-9]{7,20}$"))) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("telefono_invalido")
                    )
                }

                // Validar género: CHECK genero IN (...)
                val generoLimpio = req.genero?.trim()
                val GENEROS_VALIDOS = setOf(
                    "masculino",
                    "femenino",
                    "no_binario",
                    "otro",
                    "prefiere_no_decirlo"
                )
                if (generoLimpio != null && generoLimpio !in GENEROS_VALIDOS) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("genero_invalido")
                    )
                }

                // Validar + parsear fecha de nacimiento según el CHECK
                val fechaNacimientoParsed: LocalDate? = if (req.fechaNacimiento != null) {
                    if (req.fechaNacimiento.isBlank()) {
                        null  // "" → borrar
                    } else {
                        val parsed = try {
                            LocalDate.parse(req.fechaNacimiento) // "YYYY-MM-DD"
                        } catch (e: DateTimeParseException) {
                            return@put call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorRes("fecha_nacimiento_invalida")
                            )
                        }

                        val minDate = LocalDate.of(1900, 1, 1)
                        val maxDate = LocalDate.now().minusYears(14)
                        if (parsed.isBefore(minDate) || parsed.isAfter(maxDate)) {
                            return@put call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorRes("fecha_nacimiento_fuera_de_rango")
                            )
                        }
                        parsed
                    }
                } else {
                    null // campo ausente → no tocar fecha
                }

                // Mezclamos con lo que ya tiene el usuario en BD
                val fechaFinal = when {
                    req.fechaNacimiento == null -> u.fechaNacimiento // no cambiar
                    else -> fechaNacimientoParsed                    // puede ser null (borrar) o fecha válida
                }

                val generoFinal = when {
                    req.genero == null -> u.genero   // no cambiar
                    else -> generoLimpio             // nuevo valor (puede ser null si decides soportar borrar)
                }

                var touched = 0

                if (req.nombre != null)  touched += users.updateNombre(uid, req.nombre)
                if (req.idioma  != null) touched += users.updateIdioma(uid, req.idioma)

                if (req.telefono != null) {
                    // telefonoLimpio = null → se borra en BD
                    touched += users.updateTelefono(uid, telefonoLimpio)
                }

                if (req.fechaNacimiento != null || req.genero != null) {
                    touched += users.updateDatosDemograficos(
                        userId = uid,
                        fechaNacimiento = fechaFinal,
                        genero = generoFinal
                    )
                }

                if (touched == 0) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("nothing_to_update")
                    )
                }

                call.respond(OkRes())
            }

            // GET /me/perfil
            get("/perfil") {
                val uid = call.userIdFromJwt()
                val p = profiles.findByUser(uid)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorRes("profile_not_found"))

                call.respond(
                    PerfilRes(
                        nivelExperiencia   = p.nivelExperiencia,
                        area               = p.area,
                        pais               = p.pais,
                        notaObjetivos      = p.notaObjetivos,
                        flagsAccesibilidad = p.flagsAccesibilidad
                    )
                )
            }

            // PUT /me/perfil
            put("/perfil") {
                val uid = call.userIdFromJwt()
                val req = runCatching { call.receive<PutPerfilReq>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
                }

                val paisNormalizado = req.pais?.uppercase()

                val errorNivel = Validaciones.validarNivelExperiencia(req.nivelExperiencia)
                if (errorNivel != null) {
                    application.log.error("PUT /me/perfil → $errorNivel (nivel='${req.nivelExperiencia}')")
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes(errorNivel))
                }

                val errorArea = Validaciones.validarArea(req.area)
                if (errorArea != null) {
                    application.log.error("PUT /me/perfil → $errorArea (area='${req.area}')")
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes(errorArea))
                }

                val errorPais = Validaciones.validarPais(paisNormalizado)
                if (errorPais != null) {
                    application.log.error("PUT /me/perfil → $errorPais (pais='${req.pais}')")
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes(errorPais))
                }

                profiles.upsert(
                    userId             = uid,
                    nivelExperiencia   = req.nivelExperiencia,
                    area               = req.area,
                    pais               = paisNormalizado,
                    notaObjetivos      = req.notaObjetivos,
                    flagsAccesibilidad = req.flagsAccesibilidad
                )

                call.respond(OkRes())
            }

            // GET /me/objetivo
            get("/objetivo") {
                val uid = call.userIdFromJwt()
                val obj = objetivos.findByUser(uid)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorRes("objetivo_not_found"))

                call.respond(
                    ObjetivoRes(
                        id = obj.id.toString(),
                        nombreCargo = obj.nombreCargo,
                        sector = obj.sector
                    )
                )
            }

            // PUT /me/objetivo
            put("/objetivo") {
                val uid = call.userIdFromJwt()
                val req = runCatching { call.receive<PutObjetivoReq>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
                }

                if (req.nombreCargo.isBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("nombre_cargo_requerido")
                    )
                }

                val objetivoId = objetivos.upsert(uid, req.nombreCargo, req.sector)

                call.respond(
                    ObjetivoRes(
                        id = objetivoId.toString(),
                        nombreCargo = req.nombreCargo,
                        sector = req.sector
                    )
                )
            }

            delete("/objetivo") {
                val uid = call.userIdFromJwt()
                val deleted = objetivos.delete(uid)

                if (deleted == 0) {
                    return@delete call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("objetivo_not_found")
                    )
                }

                call.respond(OkRes())
            }
        }
    }
}
