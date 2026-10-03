---
tipo: tecnologia
tags: [tecnologia, ia, llm, openai, anthropic]
version: gpt-4o-mini · claude-haiku-4-5
actualizado: 2026-10-03
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
- **Seguridad**: las respuestas del candidato van en el mensaje de usuario como JSON de datos; las instrucciones dicen
  explícitamente que ignore instrucciones dentro de las respuestas.

## Motor freemium (sin IA)
`SERVICIOS/EVALUADOR_RESPUESTA.kt` → `EvaluadorRespuestaFreemium`: similitud por trigramas con la respuesta ideal (40 %),
cobertura de palabras clave de la rúbrica (40 %) y largo (20 %). Sin palabras clave reparte el peso. Se usa en práctica,
nivelación, reporte freemium y `/api/v1/practice/evaluate-freemium`.

> [!danger] Cuesta dinero
> No llamar al LLM real en pruebas sin intención explícita. Las E2E lo hacen solo con `-ConIa`. En pruebas unitarias se usan
> proveedores falsos (`ProveedorIaGrabador`, `ProveedorEvaluacionFalso`).

Relacionado: [[Flujos de negocio]] · [[Usuarios y modelo de negocio]] · [[Decisiones]]
