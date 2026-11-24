package CONFIGURACION

// ---------- Transcripción de la llamada ----------

/** Una respuesta hablada de varios minutos cabe de sobra; más que esto es un envío mal hecho. */
const val LARGO_MAXIMO_TRANSCRIPCION = 8_000
const val LARGO_MAXIMO_IDIOMA = 10
/** Una respuesta no dura más de una hora. */
const val DURACION_MAXIMA_RESPUESTA_MS = 60L * 60 * 1000

// ---------- Diccionarios de terminología ----------

const val LARGO_MAXIMO_AREA = 40
const val LARGO_MAXIMO_NOMBRE_DICCIONARIO = 80
const val LARGO_MAXIMO_TERMINO = 60
const val TERMINOS_MAXIMOS_POR_DICCIONARIO = 2_000
const val CORRECCIONES_MAXIMAS_POR_DICCIONARIO = 2_000

/** Letras minúsculas, números y guiones: "programador-front". Es el id estable que usa la app para pedir el paquete. */
val PATRON_AREA_DICCIONARIO = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
