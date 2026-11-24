# Análisis — Feedback personalizado a la persona (2026-11-23)

## 1. Qué existe hoy (verificado en código)
- **Reporte post-llamada**: `REPORTE_LLAMADA.kt` (segmentos `TramoLlamadaDto`, conteos,
  duración máxima, acciones) + `REPORTE_LLAMADA_VISTA.kt` (timeline "Eventos en el tiempo",
  leyenda color+valor con polaridad, conteos, duración máxima, señal promedio).
- **Backend**: guarda el reporte por usuario (JSONB) — historial disponible por `user_id`.
- **Ventanas en vivo**: los10 scores por ventana solo viven en la app durante la llamada.
- **Privacidad (inmutable)**: el video nunca sale del teléfono; viajan solo landmarks →
  eventos. El feedback se deriva SOLO de esos eventos (mismo régimen de consentimiento T0).

## 2. Qué es "personalizado" (definición operativa)
Hoy el reporte entrega **hechos crudos** ("24s de toques en la cara"). Personalizado es:
**interpretación por persona** — qué le fue bien, qué conviene mejorar, *priorizado*, y si
hay más de una llamada, **tendencia**. Ejemplo de salida deseada:

> "Presencia:8/10 — buen manejo de manos en la mesa.
>  Mirada:5/10 — desvío frecuente (~40% del tiempo); intenta sostener la cámara.
>  Manos:4/10 — te tocaste la cara24s en2min; apoya las manos en la mesa.
>  Tu llamada3 mejoró en mirada respecto a la2."

## 3. Propuesta por niveles (del más barato al opcional)

### N1 — Scoring por reglas (fase1, SIN LLM, recomiendo empezar aquí)
- **4 ejes** agrupando los eventos conductuales:
  1. *Presencia/cuerpo*: `torso_lean`, `arm_crossing`, `table_rest`, `manos_juntas`.
  2. *Mirada*: `gaze_direction` (desvío) + `gaze_alignment` (calidad de alineación).
  3. *Manos/cara*: `hand_to_face_adaptors`.
  4. *Manos en mesa/apoyo*: `table_rest` (positivo: aparece poco = bien).
- **Fórmula** (calibrable): `tasa = segundos_evento / duración_llamada`;
  `score_eje = clamp(0..10, 10 − k × tasa_eje)` con `k` por evento (los6 calibrados ya
  tienen tasas de referencia del vídeo de calibración: p.ej. toques24s/109s ≈22%).
- **Salida**: el **peor eje** manda1-2 frases accionables en español neutro, sin shaming
  ("apoya las manos en la mesa", no "fallaste"). Si ninguna conducta supera umbral →
  mensaje **positivo por defecto** ("sin señales de las6 conductas — buen trabajo").
- **Dónde vive**: el backend calcula `feedback` **al guardar el reporte** y lo agrega como
  campo nuevo (`feedback: {ejes:[{id, score, segundos, pctLlamada, veredicto}], resumen,
  peorEje}`) — el JSONB lo tolera sin migración; la app lo pinta en
  `REPORTE_LLAMADA_VISTA` como sección nueva **sin romper el contrato** (campo agregado,
  opcional).

### N2 — Tendencia entre llamadas (fase2)
- Con el histórico de reportes del usuario (ya persistidos): sparkline de score global por
  llamada + delta por eje ("mejoraste en mirada +2 vs tu llamada anterior").
- UI: sección "Tu progreso" en el historial de reportes.

### N3 — Benchmark vs dataset (fase3, OPCIONAL — solo con tu OK)
- Percentil vs los8916 clips de DivLara (mismas features) para dar contexto
  ("tu desvío de mirada está por debajo del promedio del dataset").
- Advertencia obligatoria: **no es una nota de selección** — es referencia interna.

### N4 — Texto generado con LLM (fase4, OPCIONAL — solo con tu OK explícito)
- Condensar las métricas en un párrafo personalizado. Costos por llamada; sale SOLO
  métricas agregadas (nunca landmarks crudos). Regla vigente: no se activa sin pedido.

## 4. Riesgos y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Score sin ground truth (nadie "calibró" la nota) | Presentar como **guía**, no calificación definitiva; mostrar siempre los segundos/pct junto al score. |
| Sensación de ranking/evaluación | Nunca mostrar "nota final única"; siempre por eje + tendencia propia (N2) antes que benchmark (N3). |
| Contrato roto con la app | Solo **campos agregados** en JSONB; la app ignora claves desconocidas (verificado en el DTO actual). |
| Privacidad | El feedback usa solo eventos (ya viajan con consentimiento T0); sin datos nuevos. |
| Textos robotizados/shaming | Templates en español neutro, revisados; foco en acción concreta. |

## 5. Plan de implementación (kanban)
1. **T-B1 backend**: calcular `feedback` al guardar reporte + tests (contrato, división por
   llamada corta, caso sin eventos).
2. **T-B2 app**: sección "Tu retroalimentación" en `REPORTE_LLAMADA_VISTA` + tests.
3. **T-B3 tendencia** (N2): histórico + delta por eje.
4. *N3/N4 (benchmark, LLM)*: solo con aprobación explícita.

## 6. Decisiones que quedan esperando OK del usuario
- Ajuste del umbral de "alineación de mirada" (hoy ≤4 casi siempre activo).
- `torso_inclination` y `arm_crossing` con las features actuales: indetectables (review
  d36) — requerirían features nuevas o pipelines aparte (MediaPipe manos/objeto).
- "Objeto en la mano": no viable con solo pose.
