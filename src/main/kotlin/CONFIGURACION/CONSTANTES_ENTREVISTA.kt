package CONFIGURACION

/** Preguntas de una entrevista si el usuario no pide otra cantidad. */
const val PREGUNTAS_ENTREVISTA_POR_DEFECTO = 8
const val PREGUNTAS_ENTREVISTA_MINIMO = 3
const val PREGUNTAS_ENTREVISTA_MAXIMO = 15

/** Parte técnica de la entrevista; el resto son preguntas blandas (comportamiento). */
const val PROPORCION_PREGUNTAS_TECNICAS = 0.6

/** Una sesión abandonada más tiempo que esto se cancela sola al iniciar otra. */
const val HORAS_MAXIMAS_SESION_ENTREVISTA = 2L

/** No repetir preguntas de las últimas N sesiones mientras el banco alcance. */
const val SESIONES_SIN_REPETIR_PREGUNTAS = 3

const val LARGO_MAXIMO_RESPUESTA_ENTREVISTA = 4000
const val LARGO_MAXIMO_URL_VIDEO = 500

/** El cliente de visión envía paquetes de ~30 filas cada 500 ms; esto deja margen sin aceptar lotes abusivos. */
const val METRICAS_VIDEO_MAXIMAS_POR_LOTE = 300

const val PUNTAJE_MAXIMO_RESPUESTA = 100
const val TAMANO_PAGINA_ENTREVISTAS_POR_DEFECTO = 20
const val TAMANO_PAGINA_ENTREVISTAS_MAXIMO = 100
