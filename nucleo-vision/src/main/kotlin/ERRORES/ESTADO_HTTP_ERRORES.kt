package ERRORES

/**
 * Código HTTP de cada error de dominio. Vive aquí (sin Ktor) para que el backend y el motor de visión respondan
 * exactamente igual ante el mismo error.
 */
fun ErrorAplicacion.estadoHttp(): Int = when (this) {
    is ErrorValidacion -> 400
    is ErrorNoAutorizado -> 401
    is ErrorProhibido -> 403
    is ErrorNoEncontrado -> 404
    is ErrorConflicto -> 409
    is ErrorDemasiadosIntentos -> 429
    is ErrorRespuestaExterna -> 502
    is ErrorServicioExterno -> 503
}
