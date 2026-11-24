package CONFIGURACION

// ---------- Feedback por reglas de la llamada en vivo (N1) ----------

/** Por debajo de esta duración no se puntúa: solo se informan los segundos detectados (el % de una llamada tan corta engaña). */
const val DURACION_MINIMA_FEEDBACK_SEG = 10

/**
 * Pendientes `k` de `score = clamp(0..10, 10 − k × tasa)` por eje (calibrables). Referencia del vídeo de calibración: toques en
 * la cara 24 s / 109 s ≈ 22 % → 4/10; desvío de mirada ≈ 40 % → 5/10.
 */
const val K_FEEDBACK_MANOS_CARA = 27.0
const val K_FEEDBACK_MIRADA = 12.5
const val K_FEEDBACK_PRESENCIA_CUERPO = 15.0
const val K_FEEDBACK_APOYO_MESA = 10.0

const val SCORE_FEEDBACK_MAXIMO = 10

/** Desde este score el eje se considera bien y no genera frases de mejora. */
const val SCORE_FEEDBACK_BIEN = 8

/** Desde este score (y bajo [SCORE_FEEDBACK_BIEN]) el eje "puede mejorar"; por debajo es la prioridad. */
const val SCORE_FEEDBACK_MEJORABLE = 5
