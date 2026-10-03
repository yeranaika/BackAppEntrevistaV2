package CONTROLADORES

import ERRORES.ErrorServicioExterno
import ESQUEMAS.RespuestaDocumentoLegal
import ESQUEMAS.RespuestaRevocacion
import ESQUEMAS.SolicitudConsentimiento
import ESQUEMAS.SolicitudPublicarTexto
import INTEGRACIONES.DOCUMENTO_PRIVACIDAD
import INTEGRACIONES.DOCUMENTO_TERMINOS
import INTEGRACIONES.FuenteDocumentosLegales
import MIDDLEWARES.soloAdmin
import SERVICIOS.ServicioConsentimiento
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aDocumentoLegal
import VISTAS.aRespuesta
import VISTAS.aRespuestaTexto
import VISTAS.aVersionEula
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val VERSION_DOCUMENTOS_ESTATICOS = "1.0.0"

/**
 * GET  /consent/current              texto legal vigente (Android)
 * POST /admin/consent/text           publicar versión (admin)
 * POST /me/consent                   aceptar { version, alcances: { nombre: bool } }
 * GET  /me/consent/latest            consentimiento vigente (204 si no hay)
 * POST /me/consent/revoke
 * GET  /api/v1/legal/eula | versions | terms | privacy
 * POST /api/v1/legal/admin/eula      publicar versión (admin)
 */
fun Route.controladorConsentimiento(servicio: ServicioConsentimiento, documentos: FuenteDocumentosLegales) {
    get("/consent/current") {
        call.respond(servicio.textoVigente().aRespuestaTexto())
    }

    route("/admin/consent") {
        soloAdmin {
            post("/text") {
                val solicitud = call.receive<SolicitudPublicarTexto>()
                call.responderConMensaje(
                    servicio.publicar(solicitud.version, solicitud.title, solicitud.body).aRespuestaTexto(),
                    "Texto legal publicado (versión ${solicitud.version})",
                    HttpStatusCode.Created
                )
            }
        }
    }

    authenticate("auth-jwt") {
        route("/me/consent") {
            post {
                val solicitud = call.receive<SolicitudConsentimiento>()
                val consentimiento = servicio.otorgar(call.usuarioIdDesdeJwt(), solicitud.version, solicitud.alcances, call.request.origin.remoteHost)
                call.responderConMensaje(
                    consentimiento.aRespuesta(servicio.alcancesComoMapa(consentimiento)),
                    "Consentimiento registrado",
                    HttpStatusCode.Created
                )
            }

            get("/latest") {
                val consentimiento = servicio.vigente(call.usuarioIdDesdeJwt())
                    ?: return@get call.respond(HttpStatusCode.NoContent)
                call.respond(consentimiento.aRespuesta(servicio.alcancesComoMapa(consentimiento)))
            }

            post("/revoke") {
                servicio.revocar(call.usuarioIdDesdeJwt())
                call.responderConMensaje(RespuestaRevocacion(revoked = true), "Consentimiento revocado")
            }
        }
    }

    route("/api/v1/legal") {
        get("/eula") {
            call.respond(servicio.textoVigente().aDocumentoLegal())
        }

        get("/versions") {
            call.respond(servicio.versiones().map { it.aVersionEula() })
        }

        get("/terms") {
            call.respond(documentoEstatico(documentos, DOCUMENTO_TERMINOS, "Términos y Condiciones de Servicio", "terms"))
        }

        get("/privacy") {
            call.respond(documentoEstatico(documentos, DOCUMENTO_PRIVACIDAD, "Política de Privacidad y Protección de Datos", "privacy"))
        }

        route("/admin") {
            soloAdmin {
                post("/eula") {
                    val solicitud = call.receive<SolicitudPublicarTexto>()
                    call.responderConMensaje(
                        servicio.publicar(solicitud.version, solicitud.title, solicitud.body).aDocumentoLegal(),
                        "EULA publicada (versión ${solicitud.version})",
                        HttpStatusCode.Created
                    )
                }
            }
        }
    }
}

private suspend fun documentoEstatico(documentos: FuenteDocumentosLegales, archivo: String, titulo: String, tipo: String) =
    RespuestaDocumentoLegal(
        title = titulo,
        version = VERSION_DOCUMENTOS_ESTATICOS,
        type = tipo,
        contentMarkdown = documentos.leer(archivo)
            ?: throw ErrorServicioExterno("documento_no_disponible", "El documento legal no está disponible"),
        vigente = true
    )
