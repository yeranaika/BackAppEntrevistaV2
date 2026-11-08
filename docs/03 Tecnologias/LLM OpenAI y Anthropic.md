---
tipo: tecnologia
tags: [tecnologia, ia, llm, openai, anthropic]
version: gpt-4o-mini · claude-haiku-4-5
actualizado: 2026-11-07
---

# LLM: OpenAI y Anthropic

**Para qué**: (1) **generar preguntas** para el banco (admin, quedan pendientes de revisión) y (2) **evaluar las
respuestas abiertas** del reporte de entrevista para usuarios **premium**. No se entrena un modelo propio (L04).

| | |
|---|---|
| Proveedores | OpenAI (`gpt-4o-mini`) y Anthropic (`claude-haiku-4-5`), detrás de `ProveedorPreguntasIa` |
| Config | `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` (opcionales: sin clave responde `503 provider_not_configured`) |
| Código | `INTEGRACIONES/PROVEEDOR_LLM.kt`, `SERVICIOS/SERVICIO_GENERACION_PREGUNTA.kt`, `SERVICIOS/EVALUADOR_ENTREVISTA.kt` |
| Costo | `costoLlmUsd(modelo, tokensEntrada, tokensSalida)`; se registra en `pregunta_generacion_ia` y `reporte_entrevista` |

## Cómo se usa
- **Salida estructurada**: se pide JSON con esquema estricto (`json_schema` de OpenAI / `output_config` de Anthropic) y
  luego se **valida en código** (ids, rangos, textos no vacíos). Una respuesta inválida → `502` y no se reintenta.
- **Resiliencia**: `ProveedorConResiliencia` reintenta solo errores HTTP del proveedor; timeout 60 s.
- **Evaluación de entrevistas**: una sola llamada por entrevista con todas las respuestas abiertas; si la IA falla o
  responde mal se usa el motor freemium (el reporte siempre sale). El modelo por defecto: OpenAI si hay clave, si no Anthropic.
- **Tope mensual** ([[Decisiones]] D17): cada usuario premium tiene hasta `LIMITE_EVALUACIONES_IA_MENSUAL` reportes con
  IA por mes calendario UTC (por defecto 30; `0` = sin IA). El cupo se reserva en la BD **antes** de llamar al LLM, así
  que dos reportes simultáneos no lo superan. Al llegar al tope el reporte sale con el motor freemium, sin error.
- **Cuánto se gasta**: `GET /api/v1/admin/costos-ia` (solo admin) suma por día y por modelo lo registrado en
  `reporte_entrevista` y `pregunta_generacion_ia`. Una evaluación de reporte que respondió mal guarda sus tokens y
  costo aunque el reporte salga freemium (cuenta como `fallida`); una generación de preguntas fallida no guarda costo y
  se estima con `costoLlmUsd` (si se agrega un modelo, cargar su precio ahí).
- **Qué fallidas de generación guardan tokens** (desde D23): las que el proveedor respondió y cobró —preguntas que no
  pasan la validación (`provider_invalid_output`), rechazo del modelo (`provider_refusal`), respuesta cortada
  (`provider_truncated`), sin contenido (`provider_missing_content`) o con formato inesperado
  (`provider_invalid_response`)— se auditan con los tokens que informó el proveedor, si vinieron en el sobre (viajan
  en `ErrorRespuestaExterna`, nunca al cliente). `provider_http_error` (no respondió o respondió con un estado distinto
  de 200) se audita **sin** tokens y se reintenta una vez; `provider_timeout` (se agotó el tiempo con el pedido ya
  enviado) también va sin tokens y **no** se reintenta, para no pagar dos veces.
- **Tope de gasto en la generación de preguntas** ([[Decisiones]] D23): `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` (5 USD
  por defecto; `0` = generación con IA desactivada) por mes calendario UTC, **global** (suma todos los admins: la tabla
  no guarda quién pidió). Se suma desde `pregunta_generacion_ia` con `costoTotalUsd`, fallidas incluidas con su costo
  estimado, igual que `GET /api/v1/admin/costos-ia`. Se revisa después de validar el pedido y antes de llamar al
  proveedor → `429 tope_generacion_ia_alcanzado` (el mensaje dice cuándo se repone). Sin reserva previa: dos
  generaciones simultáneas pueden pasar el tope en lo que cueste una. Además, `LIMITE_GENERACIONES_IA_POR_IP` (30 cada
  60 min) corta ráfagas → `429 demasiadas_solicitudes` → [[Redis]].
- **Seguridad**: las respuestas del candidato van en el mensaje de usuario como JSON de datos; las instrucciones dicen
  explícitamente que ignore instrucciones dentro de las respuestas.

## Motor freemium (sin IA)
`SERVICIOS/EVALUADOR_RESPUESTA.kt` → `EvaluadorRespuestaFreemium`: similitud por trigramas con la respuesta ideal (40 %),
cobertura de palabras clave de la rúbrica (40 %) y largo (20 %). Sin palabras clave reparte el peso. Se usa en práctica,
nivelación, reporte freemium y `/api/v1/practice/evaluate-freemium`. En el reporte de entrevista las palabras clave salen
de la **rúbrica copiada** al servir la pregunta (`rubrica_snap`, D24), no del banco: editarla después no cambia la nota.
Solo las filas anteriores a la migración 022 sin copia usan la del banco.

> [!danger] Cuesta dinero
> No llamar al LLM real en pruebas sin intención explícita. Las E2E lo hacen solo con `-ConIa`. En pruebas unitarias se usan
> proveedores falsos (`ProveedorIaGrabador`, `ProveedorEvaluacionFalso`).

Relacionado: [[Flujos de negocio]] · [[Usuarios y modelo de negocio]] · [[Decisiones]]
