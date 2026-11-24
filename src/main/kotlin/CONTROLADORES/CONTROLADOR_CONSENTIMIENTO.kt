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
import UTILIDADES.ipDelCliente
import UTILIDADES.responderConMensaje
import UTILIDADES.usuarioIdDesdeJwt
import VISTAS.aDocumentoLegal
import VISTAS.aRespuesta
import VISTAS.aRespuestaTexto
import VISTAS.aVersionEula
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Deben coincidir con la "Versión" de docs/legal/TERMINOS_DE_SERVICIO.md y POLITICA_DE_PRIVACIDAD.md. */
private const val VERSION_TERMINOS = "1.1.0"
/** 1.2.0: plazo de conservación del consentimiento de una cuenta eliminada y métricas de cámara (D38). */
private const val VERSION_POLITICA_PRIVACIDAD = "1.2.0"

/**
 * GET  /consent/current              texto legal vigente (Android)
 * POST /admin/consent/text           publicar versión (admin) { version, title, body, alcances? }; 409 si cambia una ya aceptada
 * POST /me/consent                   aceptar { version, alcances: { nombre: bool } }; descarta los alcances que la versión no ofrece
 * GET  /me/consent/latest            consentimiento vigente (204 si no hay)
 * POST /me/consent/revoke
 * GET  /api/v1/legal/eula | versions | terms | privacy
 * POST /api/v1/legal/admin/eula      publicar versión (admin) { version, title, body, alcances? }
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
                    servicio.publicar(solicitud.version, solicitud.title, solicitud.body, solicitud.alcances).aRespuestaTexto(),
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
                // La IP real del cliente (no la del proxy, y sin DNS inverso): es evidencia del consentimiento.
                val consentimiento = servicio.otorgar(call.usuarioIdDesdeJwt(), solicitud.version, solicitud.alcances, call.ipDelCliente())
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
            call.respond(documentoEstatico(documentos, DOCUMENTO_TERMINOS, "Términos y Condiciones de Servicio", "terms", VERSION_TERMINOS))
        }

        get("/privacy") {
            call.respond(documentoEstatico(documentos, DOCUMENTO_PRIVACIDAD, "Política de Privacidad y Protección de Datos", "privacy", VERSION_POLITICA_PRIVACIDAD))
        }

        route("/admin") {
            soloAdmin {
                post("/eula") {
                    val solicitud = call.receive<SolicitudPublicarTexto>()
                    call.responderConMensaje(
                        servicio.publicar(solicitud.version, solicitud.title, solicitud.body, solicitud.alcances).aDocumentoLegal(),
                        "EULA publicada (versión ${solicitud.version})",
                        HttpStatusCode.Created
                    )
                }
            }
        }
    }
}

private suspend fun documentoEstatico(documentos: FuenteDocumentosLegales, archivo: String, titulo: String, tipo: String, version: String) =
    RespuestaDocumentoLegal(
        title = titulo,
        version = version,
        type = tipo,
        contentMarkdown = documentos.leer(archivo)
            ?: throw ErrorServicioExterno("documento_no_disponible", "El documento legal no está disponible"),
        vigente = true
    )
