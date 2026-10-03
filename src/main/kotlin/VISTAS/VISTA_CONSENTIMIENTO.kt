package VISTAS

import ESQUEMAS.RespuestaConsentimiento
import ESQUEMAS.RespuestaDocumentoLegal
import ESQUEMAS.RespuestaTextoConsentimiento
import ESQUEMAS.RespuestaVersionEula
import MODELOS.Consentimiento
import MODELOS.TextoConsentimiento

fun TextoConsentimiento.aRespuestaTexto() = RespuestaTextoConsentimiento(version, titulo, cuerpo)

fun TextoConsentimiento.aDocumentoLegal() = RespuestaDocumentoLegal(
    title = titulo,
    version = version,
    type = "eula",
    contentMarkdown = cuerpo,
    vigente = estaVigente,
    fechaPublicacion = fechaPublicacion.toString()
)

fun TextoConsentimiento.aVersionEula() = RespuestaVersionEula(version, titulo, estaVigente, fechaPublicacion.toString())

/** [alcances]: mapa alcance → aceptado que calcula ServicioConsentimiento. */
fun Consentimiento.aRespuesta(alcances: Map<String, Boolean>) = RespuestaConsentimiento(
    id = id.toString(),
    version = version,
    alcances = alcances,
    fechaOtorgado = fechaOtorgado.toString(),
    fechaRevocado = fechaRevocado?.toString()
)
