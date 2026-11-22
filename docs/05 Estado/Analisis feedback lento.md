---
tipo: estado
tags: [analisis, feedback, reporte, rendimiento, riesgos]
actualizado: 2026-11-18
---

# Análisis: creación del feedback (reporte) — lentitud y errores no contemplados

Rama: `analisis/feedback-lento`. Solo lectura de código; **no se llamó al LLM** (cuesta dinero) ni se midió en vivo.
Archivos: `SERVICIO_REPORTE_ENTREVISTA.kt`, `EVALUADOR_ENTREVISTA.kt`, `EVALUADOR_RESPUESTA.kt`, `PROVEEDOR_LLM.kt`, `CONTROLADOR_FEEDBACK.kt`.

## Flujo
`POST finalizar` → corrutina de fondo → `procesar` → `crearPendiente` + `tomarParaGenerar` → `calcular`
(premium? → reservar cupo IA → **1 llamada LLM con todas las respuestas** → puntajes) → `guardarResultado` → `acumularNiveles`.
La app consulta `GET /entrevistas/{id}/reporte` hasta que pase de `generando` a `listo|error`.

## Por qué tarda 2 min (hipótesis por código, falta medir)
1. **Peor caso de la IA ≈ 2,3 min**: `ProveedorConResiliencia` = 70 s de tope × 2 intentos (+ espera) cuando el proveedor responde error HTTP (5xx/429). `provider_timeout` (60 s) no se reintenta, pero ya son 60 s antes de caer al motor gratis.
2. **Una sola llamada gigante**: salida = observación + mejoras por respuesta + fortalezas + áreas + resumen. Cuantas más respuestas abiertas, más tokens de salida y más lenta. OpenAI no tiene `max_tokens`; Anthropic 8192.
3. **Sin medición**: no hay logs de tiempo por etapa (premium, reserva, LLM, guardado), así que hoy no se sabe cuál etapa pesa.
4. El motor freemium es CPU puro (milisegundos): la lentitud, si existe, viene del LLM o de la BD.

## Errores y riesgos que probablemente no se pensaron
| # | Hallazgo | Impacto |
|---|---|---|
| 1 | **Palabras clave con símbolos**: `limpiar` borra `# + .` → "C#" y "C++" quedan como `c`, y `contains("c")` es verdadero casi siempre. Igual `.NET`→`net`, `R`, `Go`, `SQL` dentro de otras palabras (`contains` sin límite de palabra) | Puntaje freemium inflado / falsos positivos |
| 2 | **Todo o nada con la IA**: si un solo `id` falta, un puntaje sale de 0..100 o una observación viene vacía, se descarta TODA la respuesta ya pagada y se cae al motor gratis | Dinero perdido + calidad menor sin aviso |
| 3 | **Truncado silencioso**: `max_tokens`/`length` → se descarta lo pagado y se usa freemium; el usuario premium no sabe que no recibió IA (solo `modoEvaluacion`) | Confusión y costo |
| 4 | **Reporte "generando" hasta 15 min**: si `procesar` falla antes de `crearPendiente` (BD caída) solo se loguea; la fila no existe y el rescate corre cada 5 min y exige 10 min de vigencia | Spinner largo, sin error visible |
| 5 | **`acumularNiveles` falla = nivel por skill perdido para siempre**: el comentario dice "se puede volver a acumular en la próxima entrevista", pero esa entrevista ya no se reprocesa | Progreso de skills incorrecto |
| 6 | **Sin tope de tiempo total de `generar`** más allá del de la IA; una BD lenta lo deja en `generando` | Reporte colgado |
| 7 | **N+1 en BD**: `buscarPorId` por pregunta (filas viejas), `buscarSkill` por detalle | Latencia que crece con las preguntas |
| 8 | **Enunciado y respuesta ideal sin tope de largo** en el prompt (solo la respuesta del usuario se corta a 2000) | Costo / contexto desbordado |
| 9 | **Un único mensaje de error** ("No pudimos generar tu reporte") para todo; el código real solo está en BD | Soporte a ciegas |
| 10 | **Sin pista de espera** en `GET reporte` (ni `Retry-After` ni progreso): la app no sabe cuándo volver a preguntar | Polling agresivo o pantalla muda |
| 11 | Respuesta ideal vacía en freemium → similitud 0, puntaje máximo ≈ 60 aunque la respuesta sea perfecta | Injusto en preguntas sin ideal |
| 12 | `largo` (20 %) premia rellenar palabras; repetir palabras clave sube la cobertura | Fácil de inflar |

## Plan de mejora (orden de prioridad)
**P0 — hoy, sin romper el contrato de Android**
1. Instrumentar tiempos por etapa (`premium`, `reserva`, `llm`, `guardar`) en `calcular`/`generar` y medir **una** entrevista real con IA (pedir permiso: tiene costo). Sin esto, todo lo demás es hipótesis.
2. Timeout propio para evaluación: 25–30 s y **un** reintento corto (o ninguno) y caer al freemium → peor caso ≈ 35 s. Mantener el de generación de preguntas aparte.
3. Corregir el bug 1: coincidencia por palabra completa y normalizando `c#`, `c++`, `.net`; prueba unitaria con mutación.
4. Crear la fila `generando` **antes** de lanzar la corrutina (síncrono en `finalizar`) y bajar rescate a 1 min / vigencia a 3 min (> 70 s de IA).

**P1 — esta semana**
5. **Reporte en dos fases**: guardar de inmediato el reporte freemium (`listo`, segundos) y mejorarlo con IA en segundo plano (reemplaza y pasa a `modo=ia`). El usuario ve algo en <2 s. Revisar con Android que tolere el cambio de contenido.
6. Partir la IA en lotes de 2–3 respuestas en paralelo (`async`) + resumen aparte: la latencia baja a la de un lote. Más barato en salida si se acortan textos (1 frase, máx. 2 mejoras).
7. Aceptación parcial: usar las evaluaciones válidas de la IA y el freemium solo para las faltantes; no descartar lo pagado. Registrar `uso` siempre.
8. Mover `acumularNiveles` a la misma transacción que `guardarResultado` (o marcarlo pendiente y reintentar).

**P2 — después**
9. Campos opcionales aditivos en `GET reporte`: `etapa`, `espera_estimada_seg` y cabecera `Retry-After`.
10. Códigos de error distinguibles para la app (sin filtrar internos) y alertas por porcentaje de caída a freemium.
11. Topar largos del prompt, quitar N+1 con una consulta por lote, ponderar mejor `largo`.

## Niveles de feedback (decisión pedida por la persona, 2026-11-18)
| | **Freemium: básico, resumido** | **Premium: avanzado, detallado** |
|---|---|---|
| Qué es | Por pregunta: la respuesta correcta/ideal, la explicación del banco y los conceptos clave; un resumen corto con puntajes | Por pregunta: **por qué falló esa persona**, qué dijo bien y qué le faltó citando su propia respuesta, cómo mejorarla; fortalezas, áreas y plan personalizados |
| Cuándo existe | **Ya viene creado con las preguntas**: se arma al servir la pregunta (`respuesta_ideal`, `explicacion` de la opción, `rubrica` ya se copian a `pregunta_sesion`). Al finalizar solo se calcula el puntaje: **< 2 s, sin IA** | Se genera con IA después, en segundo plano |
| Costo | Cero | Cuenta del cupo mensual de IA (D17) |

Cómo encaja con lo que ya existe (sin romper Android: mismos campos, solo más ricos):
1. `modo_evaluacion` ya distingue `freemium` / `ia`: es la bandera de nivel; no hace falta un campo nuevo.
2. Hoy el freemium da un texto genérico por puntaje ("Buena aproximación…"). Pasa a ser: respuesta ideal + explicación + conceptos que faltaron + resumen corto. Todo sale de datos que la pregunta ya trae.
3. El premium usa el reporte en **dos fases** (P1.5): el básico sale al instante y el detallado lo reemplaza cuando termine la IA; si la IA falla, la persona conserva el básico (nunca queda sin reporte).
4. Prompt premium: pedir explícitamente `por_que_fallo` (qué faltó o estuvo mal, citando su respuesta), `lo_que_hizo_bien` y `como_mejorar`; el `observacion` actual de 1–2 frases no alcanza.
5. En la app, el freemium puede mostrar el detalle como bloqueado ("Mejora a premium para saber por qué fallaste") usando `modo_evaluacion`; pedir a Android que lo muestre sin romper el contrato.
6. Esto **resuelve gran parte de la lentitud**: la gran mayoría ve su reporte al instante y solo premium espera a la IA, que ya no bloquea.

Pendiente de decidir por la persona: si la entrevista de un premium sin cupo recibe solo básico (hoy sí, D17) y si el básico lleva siempre la respuesta ideal completa (puede revelar el banco de preguntas).

## Requisito de tiempo (pedido por la persona, 2026-11-18)
El feedback debe estar disponible en **menos de 5 min** desde que se finaliza la entrevista, siempre (con o sin IA).
- Hoy el peor caso de IA (~2,3 min) cabe, pero el **rescate** puede dejar un reporte colgado hasta ~15 min: eso rompe el requisito (hallazgo 4).
- Garantía propuesta: techo duro de 90 s para la IA; si no llega, queda el básico (que ya está listo) y el detallado se reintenta solo una vez en segundo plano. Rescate cada 1 min con vigencia de 3 min.
- Medir `finalizar → listo` y alertar si p95 > 60 s o algún reporte supera 5 min.
- Estado (2026-11-18): **P0 aplicado** en la rama `analisis/feedback-lento` (ver abajo). Falta medir con la IA real cuando haya claves.

### P0 aplicado
- Palabras clave: `C#`, `C++`, `F#`, `.NET` se normalizan a letras; coincidencia desde inicio de palabra, y palabra completa para claves de ≤3 letras (`go`, `sql`). 2 pruebas nuevas.
- IA: tope de **45 s** para toda la evaluación (`TIEMPO_MAXIMO_EVALUACION_IA`); si se pasa, sale el reporte freemium sin consumir cupo. 1 prueba nueva.
- Rescate: cada **1 min** (antes 5) y reporte abandonado tras **3 min** (`VIGENCIA_RESERVA_IA`, antes 10). Techo del reporte: ~45 s de IA + guardado, muy por debajo de 5 min. 3 pruebas existentes ajustadas a los nuevos tiempos.
- Logs de tiempo por reporte (cálculo, guardado, modo) para medir p50/p95.
- **No hecho aún**: fila "generando" síncrona en `finalizar`, reporte en dos fases, lotes en paralelo, prompt premium `por_que_fallo`, feedback freemium con respuesta ideal. Siguen en P1.

## Cómo comprobar que mejoró
- Métrica: p50/p95 de `finalizar → estado=listo`, % de reportes que cayeron a freemium, tokens y costo por reporte.
- Meta: freemium < 2 s; con IA p95 < 40 s.
- Pruebas: `./gradlew test` completo, E2E fase 7 en 8093, prueba de mutación en el emparejamiento de palabras clave y en el timeout.

Relacionado: [[Estado del proyecto]] · [[Riesgos]] · [[Pendientes y deuda tecnica]]
