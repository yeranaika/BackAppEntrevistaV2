package ERRORES

/**
 * Errores de dominio. Los servicios los lanzan sin conocer HTTP;
 * CONFIGURACION_ERRORES los traduce al código de estado y al cuerpo de respuesta.
 *
 * @property codigo identificador estable en snake_case que consume la app (ej: "email_in_use").
 */
sealed class ErrorAplicacion(
    val codigo: String,
    mensaje: String,
    causa: Throwable? = null
) : RuntimeException(mensaje, causa)

/** Entrada inválida o regla de negocio no cumplida → 400. */
class ErrorValidacion(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** Sin sesión válida o credenciales incorrectas → 401. */
class ErrorNoAutorizado(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** Sesión válida pero sin permiso para la acción → 403. */
class ErrorProhibido(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** El recurso pedido no existe → 404. */
class ErrorNoEncontrado(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** El recurso ya existe o choca con el estado actual → 409. */
class ErrorConflicto(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** Se superó el límite de intentos (ej: código de recuperación) → 429. */
class ErrorDemasiadosIntentos(codigo: String, mensaje: String = codigo) : ErrorAplicacion(codigo, mensaje)

/** Un proveedor externo (LLM, correo, Google, JSearch…) falló o no está configurado → 503. */
class ErrorServicioExterno(
    codigo: String,
    mensaje: String = codigo,
    causa: Throwable? = null
) : ErrorAplicacion(codigo, mensaje, causa)
