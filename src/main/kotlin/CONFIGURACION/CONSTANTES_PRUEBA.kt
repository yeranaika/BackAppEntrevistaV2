package CONFIGURACION

import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset

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

/** Reporte de entrevista: peso de cada parte en el puntaje global (se reparte si una parte no se midió). */
const val PESO_PUNTAJE_TECNICO = 0.5
const val PESO_PUNTAJE_BLANDO = 0.3
const val PESO_PUNTAJE_CORPORAL = 0.2

/** Desde este puntaje (0-100) una skill o una parte de la entrevista cuenta como fortaleza. */
const val UMBRAL_FORTALEZA = 75.0

/** Generación del reporte: primer intento + reintentos manuales. */
const val INTENTOS_MAXIMOS_REPORTE = 3

/** Reporte de entrevista: evaluaciones con IA por usuario premium en cada mes calendario. 0 = sin IA. Se cambia con LIMITE_EVALUACIONES_IA_MENSUAL (D17). */
const val EVALUACIONES_IA_MENSUALES_POR_DEFECTO = 30

/** Zona en la que se corta el mes del tope de IA: UTC, como el resto de las fechas (TIMESTAMPTZ). */
val ZONA_CORTE_CUOTA_IA: ZoneId = ZoneOffset.UTC

/**
 * Una reserva de IA en un reporte que sigue "generando" deja de contar pasado este tiempo (el servidor se reinició
 * a mitad de la generación). La llamada al LLM tiene un tope de ~70 s, así que una generación real nunca dura tanto.
 */
val VIGENCIA_RESERVA_IA: Duration = Duration.ofMinutes(10)

/** Progreso por skill: últimos N puntos (skill × entrevista). */
const val PUNTOS_PROGRESO_LIMITE = 200
