package CONFIGURACION

/** Versión del modelo de visión que produce las métricas de video: modelo guía E14 (Multi-Task MLP, RNF08, migración 023). */
const val VERSION_MODELO_DIVLARA = "divlara-e14-mlp"

/** Cantidad de características numéricas que espera el modelo guía E14 (`feature_columns` en `meta.json`). */
const val CANTIDAD_FEATURES_DIVLARA = 343

/** Escala numérica de salida de las variables observables del modelo DivLara. */
const val ESCALA_MODELO_DIVLARA = "0-8"
