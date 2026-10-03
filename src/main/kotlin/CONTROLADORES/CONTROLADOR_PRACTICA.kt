package CONTROLADORES

import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import CONFIGURACION.PALABRAS_CLAVE_MAXIMAS
import ERRORES.ErrorValidacion
import ESQUEMAS.IdSincronizado
import ESQUEMAS.IntentoOfflineApp
import ESQUEMAS.RespuestaEvaluacionFreemium
import ESQUEMAS.RespuestaSincronizacionApp
import ESQUEMAS.SolicitudEvaluacionFreemium
import ESQUEMAS.SolicitudIniciarPractica
import ESQUEMAS.SolicitudResponderPrueba
import ESQUEMAS.SolicitudSincronizacion
import SERVICIOS.EvaluadorRespuesta
import SERVICIOS.IntentoOfflineEntrada
import SERVICIOS.PedidoPractica
import SERVICIOS.RespuestaOfflineEntrada
import SERVICIOS.RespuestaPrueba
import SERVICIOS.ServicioPractica
import SERVICIOS.ServicioPruebasApp
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import UTILIDADES.uuidDeParametro
import VISTAS.aCorreccion
import VISTAS.aRespuesta
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Práctica (todas requieren sesión; solo se ven las prácticas propias):
 * POST /api/v1/practicas                     iniciar { skillId? | cargoId? | cargo?, categoria?, modo?, nivel?, cantidadPreguntas? }
 * GET  /api/v1/practicas                     últimas prácticas
 * GET  /api/v1/practicas/{id}                detalle con la corrección de lo respondido
 * POST /api/v1/practicas/{id}/respuestas     { preguntaId, opcionId? | texto?, tiempoRespuestaMs? } → feedback inmediato
 * POST /api/v1/practicas/{id}/finalizar
 * GET  /api/v1/pruebas/historial             entrevistas, prácticas y nivelaciones juntas
 * POST /api/v1/sync/attempts                 intentos hechos sin conexión (idempotente por localAttemptId)
 * POST /api/v1/practice/evaluate-freemium    corrige un texto con el motor freemium
 */
fun Route.controladorPractica(servicio: ServicioPractica, pruebas: ServicioPruebasApp, evaluador: EvaluadorRespuesta) {
    authenticate("auth-jwt") {
        route("/api/v1/practicas") {
            post {
                val s = call.receive<SolicitudIniciarPractica>()
                val pedido = PedidoPractica(s.skillId, s.cargoId, s.cargo, s.categoria, s.modo, s.nivel, s.cantidadPreguntas)
                call.responderConMensaje(servicio.iniciar(call.usuarioIdDesdeJwt(), pedido).aRespuesta(), "Práctica iniciada", HttpStatusCode.Created)
            }

            get {
                call.respond(servicio.historial(call.usuarioIdDesdeJwt()).map { it.aRespuesta() })
            }

            route("/{id}") {
                get {
                    call.respond(servicio.obtener(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aRespuesta())
                }

                post("/respuestas") {
                    val s = call.receive<SolicitudResponderPrueba>()
                    val usuario = call.usuarioIdDesdeJwt()
                    val sesionId = call.uuidDeParametro("id")
                    val respuesta = servicio.responder(usuario, sesionId, RespuestaPrueba(s.preguntaId, s.opcionId, s.texto, s.tiempoRespuestaMs))
                    val pregunta = servicio.obtener(usuario, sesionId).preguntas.first { it.id == respuesta.preguntaServidaId }
                    call.responderConMensaje(respuesta.aCorreccion(pregunta), "Respuesta guardada")
                }

                post("/finalizar") {
                    call.responderConMensaje(servicio.finalizar(call.usuarioIdDesdeJwt(), call.uuidDeParametro("id")).aRespuesta(), "Práctica finalizada")
                }
            }
        }

        get("/api/v1/pruebas/historial") {
            call.respond(pruebas.historial(call.usuarioIdDesdeJwt()).map { it.aRespuesta() })
        }

        post("/api/v1/sync/attempts") {
            val lote = call.receive<SolicitudSincronizacion>().intentos.map { it.aEntrada() }
            val ids = servicio.sincronizarOffline(call.usuarioIdDesdeJwt(), lote)
            call.responderConMensaje(
                RespuestaSincronizacionApp(true, ids.size, ids.map { (local, servidor) -> IdSincronizado(local, servidor.toString()) }),
                "Intentos sincronizados: ${ids.size}"
            )
        }

        post("/api/v1/practice/evaluate-freemium") {
            val s = call.receive<SolicitudEvaluacionFreemium>()
            if (s.userText.length > LARGO_MAXIMO_RESPUESTA_ENTREVISTA || s.idealText.length > LARGO_MAXIMO_RESPUESTA_ENTREVISTA) {
                throw ErrorValidacion("texto_muy_largo", "Los textos admiten hasta $LARGO_MAXIMO_RESPUESTA_ENTREVISTA caracteres")
            }
            if (s.expectedKeywords.size > PALABRAS_CLAVE_MAXIMAS) {
                throw ErrorValidacion("palabras_clave_invalidas", "Se admiten hasta $PALABRAS_CLAVE_MAXIMAS palabras clave")
            }
            val e = evaluador.evaluar(s.userText, s.idealText, s.expectedKeywords.filter { it.isNotBlank() })
            call.responderConMensaje(
                RespuestaEvaluacionFreemium(e.puntaje, e.coberturaPalabrasClave, e.similitud, e.palabrasEncontradas, e.palabrasFaltantes, e.feedback),
                "Respuesta evaluada"
            )
        }
    }
}

private fun IntentoOfflineApp.aEntrada() = IntentoOfflineEntrada(
    idLocal = localAttemptId,
    skillId = skillId,
    cargoId = cargoId,
    modo = modo,
    categoria = categoria,
    nivel = nivelPreguntas,
    fechaCreacion = fechaCreacionIso,
    respuestas = respuestas.map {
        RespuestaOfflineEntrada(it.preguntaId, it.enunciado, it.opcionElegidaId, it.respuestaTexto, it.esCorrecta, it.puntaje, it.tiempoRespuestaMs, it.orden)
    }
)
