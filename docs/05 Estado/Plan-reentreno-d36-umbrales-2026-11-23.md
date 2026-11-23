# PLAN — Reentreno d36 + Calibración de umbrales + Rebuild v17 (23-nov-2026)

> Contexto: te vas de casa un buen rato → GPU del PC de IA libre para usarla completa.
> Ancla de calibración: **`pruebas/prueba-20261123-055528-453.mp4`** (81MB,108s, tu grabación
> con TODAS las vistas: cara, cruce de brazos, manos en la mesa, objeto en la mano,
> desvíos de mirada, inclinación de torso).
> Objetivo: subir el resultado en esos6 focos + umbrales derivados de datos + rebuild Android.

## Fases

### Fase1 — Datos y cobertura (Claude, sin GPU)
1. Extraer frames del vídeo subido (1/s → ~108 frames) desde
   `A:\...\BackAppEntrevistaV2\pruebas\prueba-20261123-055528-453.mp4`.
2. Etiquetar por frame los6 focos con `claude -p` (visión, método Cloud ya validado):
   cara/toques, cruce de brazos, manos en la mesa, objeto en la mano, desvío de mirada,
   inclinación de torso → `calibracion_d36.json` (frame, inicio/fin, score observado).
3. Verificar cobertura de targets: leer `models/divlara-d35-mlp/meta.json` + pipeline
   `E:\entrenamienrto-IA\DivLara\scratch\e17\` → ¿cuáles de los6 existen como target?
   **`objeto en la mano`**: si NO existe → evaluar viabilidad (¿se infiere de pose pura?
   probablemente NO sin features de mano/objeto) y reportar; no inventar la clase.
4. Entregar: etiquetas + tabla de gaps (target vs vídeo vs score actual).

### Fase2 — Reentreno con foco (Claude, GPU libre)
1. Reutilizar el pipeline e17 (`d35final.py` → d36): mismos343 features/20 targets.
2. Foco en los focos gap (manos_a_cara recall, table_rest FP, desvío):
   más datos de esos eventos en los splits existentes (6.430 clips D30-D33) + loss con
   class-weight por esos targets;4-6 seeds, seleccionar por val.
3. Métrica de éxito: **macro test ≥86.20** (hoy) Y mejora de recall en los6 focos
   (contra la anotación Cloud del vídeo); no aceptar macro menor.
4. Exportar ONNX (shift+round+clip) + parity check (tol1e-4) → `models/divlara-d36-mlp/`.

### Fase3 — Calibración de umbrales con el vídeo
1. Correr el extractor + motor sobre el vídeo etiquetado → score por frame/evento.
2. Por cada foco: umbral que maximiza F1 vs la anotación Cloud (reemplaza los fijos
  3.0/3.5/4.0 por los derivados) → `UMBRALES_D36.json` + tabla final documentada.
3. Incluir histeresis de cierre de segmentos (duración real vs "pegado" a la llamada).

### Fase4 — Integración backend
1. `MANIFIESTO.json` + `golden_features.json` de d36 (método ya probado: esperado = ONNX real).
2. Cambiar `VERSION_MODELO_DIVLARA` → `divlara-d36-mlp`; reiniciar `MotorUp`;
   verificar `/health`8093+8094 = d36 cargado.
3. Suite backend (582) + Android (464) en verde.

### Fase5 — Rebuild Android v17
1. `MIRADA_DESVIADA` → umbral de Fase3 (se esperaba3.0).
2. Histeresis de cierre de segmentos (Fase3).
3. `UMBRALES_CAJA_VIVA` actualizados con los de Fase3 (por evento).
4. Build: `assembleReleaseLocal -PversionCode=17 -PversionName=1.9.8 -PbackendPort=8093
   -PmostrarOverlayVision=true -PgrabarPrueba=true` + tests + install + prueba en vivo.
5. APK servido como siempre.

### Fase6 — Reviews (Antigravity)
- Review del plan y de los cambios de training code (fases2-3) ANTES de integrar.
- Review final de integración backend+app (como la de anoche).

## Reglas del plan
- Solo rutas en `ai-pc` (A:\ / E:\). Sin NUC.
- Workers máx2 concurrentes; kanban `divlara` tarjetas T6.x; antibloqueo ya mapeado.
- Commits solo tras verificación verde (como anoche); **sin push**.
- OmniRoute sigue documentando cada hora (cron ya activo).
- Si `objeto en la mano` no es viable con pose → se reporta, no se fuerza.

## Entregables al volver
Informe `Informe-reentreno-d36.md` (resultados, tabla de umbrales antes/después,
métricas vs e14/d35, APK v17) + todo en `docs/05 Estado/`.
