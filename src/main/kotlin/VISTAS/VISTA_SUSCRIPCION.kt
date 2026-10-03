package VISTAS

import ESQUEMAS.RespuestaCodigoCreado
import ESQUEMAS.RespuestaEstadoSuscripcion
import MODELOS.CodigoSuscripcion
import MODELOS.PROVEEDOR_GOOGLE_PLAY
import SERVICIOS.EstadoSuscripcion

private const val ORIGEN_GOOGLE = "google"
private const val ORIGEN_INTERNO = "interna"

fun EstadoSuscripcion.aRespuesta() = RespuestaEstadoSuscripcion(
    esPremium = esPremium,
    plan = vigente?.plan,
    origen = vigente?.proveedor?.lowercase()?.let { if (it == PROVEEDOR_GOOGLE_PLAY || it == ORIGEN_GOOGLE) ORIGEN_GOOGLE else ORIGEN_INTERNO },
    estado = vigente?.estado,
    inicio = vigente?.inicio?.toEpochMilli(),
    expiracion = vigente?.expiracion?.toEpochMilli()
)

fun CodigoSuscripcion.aRespuesta() = RespuestaCodigoCreado(
    codigo = codigo,
    expiracion = expiracion?.toString(),
    maxUsos = maxUsos,
    tipoLicencia = tipoLicencia
)
