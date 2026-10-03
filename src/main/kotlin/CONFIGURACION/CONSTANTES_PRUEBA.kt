package CONFIGURACION

/** Una respuesta abierta (0-100) cuenta como correcta desde este puntaje. */
const val UMBRAL_RESPUESTA_CORRECTA = 60.0

/** Para dar por logrado un nivel en la nivelación (promedio 0-100 de las preguntas de ese nivel). */
const val UMBRAL_NIVEL_LOGRADO = 60.0

/** Nivelación armada desde el banco: preguntas técnicas por cada nivel (junior, semisenior, senior). */
const val PREGUNTAS_NIVELACION_POR_NIVEL = 3
const val PREGUNTAS_NIVELACION_MINIMO = 3
/** Un test armado por un admin. */
const val PREGUNTAS_TEST_NIVELACION_MAXIMO = 30

const val PREGUNTAS_PRACTICA_POR_DEFECTO = 8
const val PREGUNTAS_PRACTICA_MINIMO = 1
const val PREGUNTAS_PRACTICA_MAXIMO = 20
const val SESIONES_PRACTICA_SIN_REPETIR = 3

/** Sincronización offline: tamaño máximo de lote. */
const val INTENTOS_OFFLINE_MAXIMOS_POR_LOTE = 50
const val RESPUESTAS_OFFLINE_MAXIMAS_POR_INTENTO = 50
const val LARGO_MAXIMO_ID_LOCAL = 64
const val LARGO_MAXIMO_ENUNCIADO_OFFLINE = 2000

/** Historial de la app: últimas N pruebas de cada tipo. */
const val HISTORIAL_PRUEBAS_LIMITE = 50

/** Evaluación freemium suelta (/api/v1/practice/evaluate-freemium). */
const val PALABRAS_CLAVE_MAXIMAS = 30
