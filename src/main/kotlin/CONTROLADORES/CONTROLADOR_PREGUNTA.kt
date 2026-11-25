package CONTROLADORES

import CONFIGURACION.LIMITE_GENERACION_IA
import ESQUEMAS.ConsultaPreguntas
import ESQUEMAS.RespuestaCompletarFeedbackGenerico
import ESQUEMAS.RespuestaEstadoFeedbackGenerico
import ESQUEMAS.RespuestaGenerarPreguntas
import ESQUEMAS.RespuestaOk
import ESQUEMAS.RespuestaSimilaresDePregunta
import ESQUEMAS.SolicitudCompletarFeedbackGenerico
import ESQUEMAS.SolicitudGenerarPreguntas
import ESQUEMAS.SolicitudPregunta
import ESQUEMAS.SolicitudRechazo
import MIDDLEWARES.soloAdmin
import SERVICIOS.ResultadoFeedbackGenerico
import SERVICIOS.ServicioCoberturaPreguntas
import SERVICIOS.ServicioFeedbackGenerico
import SERVICIOS.ServicioGeneracionPregunta
import SERVICIOS.ServicioPregunta
import SERVICIOS.ServicioPreguntasSimilares
import SERVICIOS.UMBRAL_SIMILITUD_PREGUNTAS_POR_DEFECTO
import UTILIDADES.booleanoDeConsulta
import UTILIDADES.enteroDeConsulta
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeConsulta
import UTILIDADES.uuidDeParametro
import VISTAS.aRespuesta
import VISTAS.aRespuestaAdmin
import VISTAS.aRespuestaPublica
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Solo admin:
 * POST   /api/v1/admin/preguntas                  crear (nace aprobada)
 * GET    /api/v1/admin/preguntas                  filtros: estado, tipo, categoria, nivel, skillId, cargoId, paraCargoId (del cargo o
 *                                                  sin cargo, D26), generadaPorIa, pagina, tamano
 * GET    /api/v1/admin/preguntas/{id}
 * PUT    /api/v1/admin/preguntas/{id}             editar (vuelve a pendiente)
 * PATCH  /api/v1/admin/preguntas/{id}/aprobar
 * PATCH  /api/v1/admin/preguntas/{id}/rechazar    { "motivo": "..." }
 * DELETE /api/v1/admin/preguntas/{id}             solo si nunca se usó
 * POST   /api/v1/admin/preguntas/generar-ia       genera con LLM (nacen pendientes); 429 tope_generacion_ia_alcanzado
 *                                                  si se llegó al gasto del mes (D23) o demasiadas_solicitudes por IP.
 *                                                  Con cargo o skill usa lo que piden las ofertas de empleo (D26)
 * POST   /api/v1/admin/questions/generate-ai      ruta antigua del panel, mismo comportamiento (comparte el límite)
 * GET    /api/v1/admin/preguntas/feedback-generico   cuántas preguntas (no rechazadas) no tienen los 3 feedbacks (D36)
 * POST   /api/v1/admin/preguntas/feedback-generico/completar
 *                                                  la IA escribe el feedback correcto/medio/malo que falta: las
 *                                                  `limite` (1..50) más antiguas, o las de `pregunta_ids`; con
 *                                                  `sobrescribir` reemplaza los textos existentes. No cambia el estado
 *                                                  de la pregunta. Usa el tope de gasto (D23) y el límite de generar-ia
 * GET    /api/v1/admin/preguntas/cobertura        skills que pide el mercado por cargo y cuántas preguntas tienen
 *                                                  (?cargoId=&minimo=1..50, por defecto 5 aprobadas por nivel) (D26)
 *
 * Usuario autenticado:
 * GET    /api/v1/preguntas                        solo aprobadas, sin solución; filtros + cantidad
 */
fun Route.controladorPregunta(
    servicio: ServicioPregunta,
    generacion: ServicioGeneracionPregunta,
    cobertura: ServicioCoberturaPreguntas,
    feedbackGenerico: ServicioFeedbackGenerico,
    similares: ServicioPreguntasSimilares
) {
    route("/api/v1/admin/preguntas") {
        soloAdmin {
            post {
                val pregunta = servicio.crear(call.receive<SolicitudPregunta>())
                call.responderConMensaje(pregunta.conAviso(similares), "Pregunta creada y aprobada", HttpStatusCode.Created)
            }

            get {
                call.respond(servicio.listar(call.consultaPreguntas()).aRespuesta())
            }

            rateLimit(LIMITE_GENERACION_IA) { post("/generar-ia") { generarConIa(generacion) } }

            // Rutas fijas: Ktor las prefiere a /{id}, así "feedback-generico" o "cobertura" nunca se leen como un id.
            get("/feedback-generico") {
                call.respond(RespuestaEstadoFeedbackGenerico(feedbackGenerico.pendientes()))
            }

            rateLimit(LIMITE_GENERACION_IA) {
                post("/feedback-generico/completar") {
                    val resultado = feedbackGenerico.completar(call.receive<SolicitudCompletarFeedbackGenerico>())
                    call.responderConMensaje(resultado.aRespuesta(), mensajeFeedbackGenerico(resultado))
                }
            }

            get("/duplicadas") {
                val q = call.request.queryParameters
                val grupos = similares.duplicadas(q["skillId"], q["cargoId"], q["nivel"], q["tipo"], q["categoria"], q["umbral"])
                call.respond(grupos.map { it.aRespuesta() })
            }

            get("/cobertura") {
                call.respond(cobertura.cobertura(call.uuidDeConsulta("cargoId"), call.enteroDeConsulta("minimo")).aRespuesta())
            }

            route("/{id}") {
                get {
                    call.respond(servicio.obtener(call.uuidDeParametro("id")).aRespuestaAdmin())
                }

                get("/similares") {
                    val id = call.uuidDeParametro("id")
                    val umbral = call.request.queryParameters["umbral"]
                    val encontradas = similares.similaresDe(id, umbral)
                    call.respond(RespuestaSimilaresDePregunta(id.toString(), umbral?.toDoubleOrNull() ?: UMBRAL_SIMILITUD_PREGUNTAS_POR_DEFECTO, encontradas.map { it.aRespuesta() }))
                }

                put {
                    val pregunta = servicio.editar(call.uuidDeParametro("id"), call.receive<SolicitudPregunta>())
                    call.responderConMensaje(pregunta.conAviso(similares), "Pregunta actualizada; queda pendiente de aprobación")
                }

                patch("/aprobar") {
                    val pregunta = servicio.aprobar(call.uuidDeParametro("id"), call.usuarioIdDesdeJwt())
                    call.responderConMensaje(pregunta.conAviso(similares), "Pregunta aprobada")
                }

                patch("/rechazar") {
                    val solicitud = call.receive<SolicitudRechazo>()
                    val pregunta = servicio.rechazar(call.uuidDeParametro("id"), solicitud.motivo, call.usuarioIdDesdeJwt())
                    call.responderConMensaje(pregunta.aRespuestaAdmin(), "Pregunta rechazada")
                }

                delete {
                    servicio.eliminar(call.uuidDeParametro("id"))
                    call.respond(RespuestaOk("Pregunta eliminada"))
                }
            }
        }
    }

    route("/api/v1/admin/questions") {
        soloAdmin {
            rateLimit(LIMITE_GENERACION_IA) { post("/generate-ai") { generarConIa(generacion) } }
        }
    }

    authenticate("auth-jwt") {
        get("/api/v1/preguntas") {
            val preguntas = servicio.listarParaUsuario(call.consultaPreguntas(), call.enteroDeConsulta("cantidad"))
            call.respond(preguntas.map { it.aRespuestaPublica() })
        }
    }
}

private suspend fun RoutingContext.generarConIa(generacion: ServicioGeneracionPregunta) {
    val resultado = generacion.generarConMercado(call.receive<SolicitudGenerarPreguntas>())
    val creadas = resultado.preguntas
    val conMercado = resultado.mercado.contexto?.let {
        if (it.ofertasAnalizadas == 1) " Se usó 1 oferta de empleo." else " Se usaron ${it.ofertasAnalizadas} ofertas de empleo."
    }.orEmpty()
    val descartadas = resultado.descartadas.size
    val avisoDescartadas = if (descartadas == 0) "" else " Se descartaron $descartadas por parecerse a preguntas que ya existen."
    call.responderConMensaje(
        RespuestaGenerarPreguntas(
            preguntasGeneradas = creadas.size,
            preguntasConError = 0,
            preguntasDescartadas = descartadas,
            preguntas = creadas.map { it.aRespuesta() },
            descartadas = resultado.descartadas.map { it.aRespuesta() },
            contextoMercado = resultado.mercado.aRespuesta()
        ),
        "Preguntas generadas: ${creadas.size}. Quedan pendientes de aprobación.$conMercado$avisoDescartadas",
        // Si todas se parecían a algo existente no se creó nada: 200, no 201.
        if (creadas.isEmpty()) HttpStatusCode.OK else HttpStatusCode.Created
    )
}

private suspend fun MODELOS.Pregunta.conAviso(similares: ServicioPreguntasSimilares) =
    aRespuestaAdmin().copy(similares = similares.avisoPara(this).map { it.aRespuesta() })

private fun ApplicationCall.consultaPreguntas() = ConsultaPreguntas(
    estado = request.queryParameters["estado"],
    tipo = request.queryParameters["tipo"],
    categoria = request.queryParameters["categoria"],
    nivel = request.queryParameters["nivel"],
    skillId = request.queryParameters["skillId"],
    cargoId = request.queryParameters["cargoId"],
    paraCargoId = request.queryParameters["paraCargoId"],
    generadaPorIa = booleanoDeConsulta("generadaPorIa"),
    pagina = enteroDeConsulta("pagina"),
    tamano = enteroDeConsulta("tamano")
)

private fun ResultadoFeedbackGenerico.aRespuesta() = RespuestaCompletarFeedbackGenerico(
    actualizadas = actualizadas.size,
    omitidas = omitidas,
    pendientes = pendientes,
    llamadasIa = llamadasIa,
    costoUsd = costoUsd,
    preguntaIds = actualizadas.map { it.toString() },
    detenidoPor = detenidoPor
)

private fun mensajeFeedbackGenerico(resultado: ResultadoFeedbackGenerico): String = buildString {
    append(if (resultado.actualizadas.size == 1) "Se completó 1 pregunta" else "Se completaron ${resultado.actualizadas.size} preguntas")
    if (resultado.omitidas > 0) append("; ${resultado.omitidas} quedaron sin feedback válido")
    if (resultado.detenidoPor != null) append("; se detuvo por ${resultado.detenidoPor}")
    append(". Pendientes: ${resultado.pendientes}.")
}
