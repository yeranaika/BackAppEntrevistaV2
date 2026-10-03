package CONTROLADORES

import ESQUEMAS.RespuestaMensaje
import ESQUEMAS.SolicitudIniciarNivelacion
import ESQUEMAS.SolicitudResponderVarias
import ESQUEMAS.SolicitudTestNivelacion
import MIDDLEWARES.soloAdmin
import SERVICIOS.DatosTestNivelacion
import SERVICIOS.RespuestaPrueba
import SERVICIOS.ServicioNivelacion
import SERVICIOS.ServicioTestNivelacion
import UTILIDADES.booleanoDeConsulta
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeConsulta
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Nivelación (requiere sesión):
 * POST /api/v1/nivelacion                  iniciar { cargoId? | cargo? } (por defecto el objetivo del onboarding)
 * GET  /api/v1/nivelacion/resultado        último resultado (204 si nunca se niveló)
 * GET  /api/v1/nivelacion/{id}             detalle; la corrección se ve al terminar
 * POST /api/v1/nivelacion/{id}/respuestas  { respuestas: [{ preguntaId, opcionId? | texto? }] } → nivel y brechas
 * GET  /api/v1/me/niveles-skill            nivel y puntaje acumulado por skill
 *
 * Tests de nivelación (admin):
 * POST /api/v1/admin/tests-nivelacion      { titulo, cargoId?, area, nivelObjetivo?, descripcion?, preguntasIds }
 * GET  /api/v1/admin/tests-nivelacion      ?cargoId, ?activo
 * GET  /api/v1/admin/tests-nivelacion/{id}
 * PUT  /api/v1/admin/tests-nivelacion/{id}
 * DELETE /api/v1/admin/tests-nivelacion/{id}   (baja lógica)
 */
fun Route.controladorNivelacion(servicio: ServicioNivelacion, tests: ServicioTestNivelacion) {
    authenticate("auth-jwt") {
        route("/api/v1/nivelacion") {
            post {
                val s = call.receive<SolicitudIniciarNivelacion>()
                call.respond(HttpStatusCode.Created, servicio.iniciar(call.usuarioIdDesdeJwt(), s.cargoId, s.cargo).aRespuesta(null))
            }

            get("/resultado") {
                val resultado = servicio.ultimoResultado(call.usuarioIdDesdeJwt()) ?: return@get call.respond(HttpStatusCode.NoContent)
                call.respond(resultado.aRespuesta())
            }

            get("/{id}") {
                val (intento, resultado) = servicio.obtener(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"))
                call.respond(intento.aRespuesta(resultado))
            }

            post("/{id}/respuestas") {
                val respuestas = call.receive<SolicitudResponderVarias>().respuestas.map { RespuestaPrueba(it.preguntaId, it.opcionId, it.texto) }
                val (intento, resultado) = servicio.responder(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id"), respuestas)
                call.respond(intento.aRespuesta(resultado))
            }
        }

        get("/api/v1/me/niveles-skill") {
            call.respond(servicio.nivelesPorSkill(call.usuarioIdDesdeJwt()).map { (nivel, nombre) -> nivel.aRespuesta(nombre) })
        }
    }

    route("/api/v1/admin/tests-nivelacion") {
        soloAdmin {
            post {
                call.respond(HttpStatusCode.Created, tests.crear(call.receive<SolicitudTestNivelacion>().aDatos()).aRespuesta())
            }

            get {
                val soloActivos = call.booleanoDeConsulta("activo") ?: false
                call.respond(tests.listar(call.uuidDeConsulta("cargoId"), soloActivos).map { it.aRespuesta() })
            }

            route("/{id}") {
                get { call.respond(tests.obtener(call.uuidDeParametro("id")).aRespuesta()) }

                put {
                    call.respond(tests.actualizar(call.uuidDeParametro("id"), call.receive<SolicitudTestNivelacion>().aDatos()).aRespuesta())
                }

                delete {
                    tests.desactivar(call.uuidDeParametro("id"))
                    call.respond(RespuestaMensaje("Test de nivelación desactivado"))
                }
            }
        }
    }
}

private fun SolicitudTestNivelacion.aDatos() = DatosTestNivelacion(titulo, cargoId, area, nivelObjetivo, descripcion, preguntasIds)
