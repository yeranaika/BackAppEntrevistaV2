# Comparación Cloud (Claude) vs MLP d35 — vídeo del usuario,23-nov-2026

**Fuente de referencia**:36 frames (cada3s) del vídeo `usuario-0032.mp4` (108.4s, grabado por la app
en la prueba manual de las00:32), anotados por Claude (cloud) → `cloud_anotacion.txt`.
**Salida de nuestro modelo**: reporte en pantalla de esa misma llamada (captura v16dbg).

## Conteo real (Cloud) vs reporte (MLP)

| Acción | Cloud (frames de3s → seg) | Modelo reportó | Veredicto |
|---|---|---|---|
| Brazos cruzados |12 frames ≈36s en3 grupos (t3-24, t36, t48-60) |4.0/8,1 vez, máx01:43 | Detecta ✓ pero duración pegajosa (segmento nunca cierra) |
| Manos a la cara |16 frames ≈48s en4 grupos (t15-27, t39-45, t69, t75-102) | **1.3/8 sin señal** | **FALLO GROSERO**: el modelo casi no registra toques de cara → sin umbral posible |
| Inclinación |14 frames ≈42s dispersos |4.0,1 vez, máx01:43 | Detecta ✓ pero misma duración pegajosa |
| Rigidez |11 frames ≈33s |4.3,1 vez, máx01:43 | Detecta ✓ |
| Mirada desviada |10 frames ≈30s en3 grupos (t27-36, t54-72, t96-105) | **3.4/8**,7 veces, máx00:09 | Detecta ✓ pero3.4 < umbral3.5 → **NO sale recuadro** |
| Manos en la mesa | **0 frames** | **3.1/8,5 veces** | **FALSOS POSITIVOS**:5 segmentos de una acción inexistente en el vídeo |
| Gestos |4 frames | Actividad gestual4.1 (señal) | Compatibles |

## Hallazgos de calibración (acción → ajuste)

1. **Segmentos pegajos (duración = llamada completa)**: todos los eventos con "1 vez · máx01:43"
   = el segmento abre al cruzar umbral y nunca cierra → la métrica de duración máxima es inservible
   y "1 vez" no refleja realidad. **Ajuste**: umbral de cierre (histeresis) p. ej. cerrar cuando
   score < umbral−0.5 durante3 ventanas seguidas; o recalcular duraciones desde los scores crudos.
2. **Manos a la cara**: el modelo puntúa1.3 con48s reales de la acción → problema de modelo/features
   (distancia muñeca-cara en vivo vs entrenamiento), NO de umbral. **Acción**: investigar el feature
   `manos_a_cara` en vivo (¿escala/normalización?, ¿el extractor en vivo difiere del de entrenamiento?);
   con1.3 no hay umbral que lo muestre. Alternativa corta: mostrar el recuadro igualmente como
   "detección débil" cuando esté en el top-3.
3. **Desvío de mirada3.4 vs umbral por defecto3.5** → justo bajo. **Ajuste**: `MIRADA_DESVIADA` a3.0
   en `UMBRALES_CAJA_VIVA` (como ya tiene brazos_cruzados/table_rest/manos_a_cara).
4. **Manos en la mesa:5 falsos positivos con0 reales** → el feature en vivo se activa con el apoyo
   natural de brazos. **Ajuste**: subir `table_rest` (caja) a3.5+ y subir su umbral de segmento del
   reporte, o entrenar con más negativos. OJO: el usuario pide ver recuadro al tocar la mesa — el
   problema es bidireccional (confunde apoyo natural con toque) → requiere revisión de features.
5. **Cloud como referencia**: sirve para validar direccionalidad (¿detectó lo general?) pero sus
   etiquetas son ruidosas (p.ej. inclinación puede sobre-llamarse) → usar para umbrales con
   tolerancia, no como ground truth exacto.

## Estado del pipeline validado
- Grabación en vivo ✓ (`● GRABANDO PRUEBA`, mp4 en `files/pruebas/`).
- Subida al backend: **500 por límite Ktor de50MB** (fix en curso T5.7) — vídeos de77.5MB y277MB.
- Predicciones:149 ventanas ok /0 fallidas en6.5min de llamada (motor d35 ✓).
- Nombre de modelo mostrado: `divlara-e14-mlp` (constante stale `CONSTANTES_VISION.kt:4`, fix en T5.7).
