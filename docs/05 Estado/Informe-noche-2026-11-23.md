# INFORME DE NOCHE — DivLara / Entrevistas (22→23 noviembre,2026)

> Trabajo nocturno autónomo. Servicios, prueba con grabación, integración de ramas,
> reviews, calibración con Cloud y documentación. Sin push (pendiente de tu OK).

##1. Servicios restaurados tras el reinicio del PC
- **Causa raíz**: el PC se reinició (~00:37) y bajó todo: Docker (PostgreSQL+Redis), backend8093, motor8094.
- **Docker no arrancaba** por dos motivos: (a) los lanzamientos por SSH mueren al cerrarse la sesión
  (job object), (b) schtasks fallaba por la ruta con espacios (`0x8007010B`). Solución: `.bat` sin
  espacios (`iniciar_docker.bat`) ejecutado con schtasks → `docker-compose` de `src/DB` subió
  `Entrevista_APP` (PostgreSQL16, **healthy**) + `Entrevista_Redis` con el volumen intacto
  (cuentas + consentimientos preservados).
- Backend y motor ahora corren como tareas programadas: `DockerUp`, `BackendUp`, `MotorUp`
  (re-ejecutables con `schtasks /Run /TN <nombre>`).
- Verificado: `GET /health`8093 → OK + `divlara-d35-mlp`; `GET /health`8094 → OK + d35.

##2. Prueba en vivo con grabación (lo que pediste)
- Flujo real de la app: Inicio → **Comenzar simulación** → hub → **Llamada en vivo (probar el modelo)**
  → pantalla previa → **▶ Iniciar** → llamada (cámara + overlay + predicciones) → **■ Detener** → reporte.
- Ejecutada con `uiautomator` (dumpsys ya no expone texto de Compose).
- **Grabación funciona**: indicador `● GRABANDO PRUEBA` en rojo; mp4 guardados en
  `/sdcard/Android/data/.../files/pruebas/`: **tu vídeo de las00:32 (77.5MB,108.4s)** y el mío de
  las02:18 (277MB,6.5min).
- **Subida al backend**: fallaba con500 → causa: límite multipart de Ktor de50MB
  (`formFieldLimit`). Arreglado (210MB) y verificado: `POST /pruebas/video` → **HTTP=200**,
  `GET /pruebas` lista `prueba-20261123-055528-453.mp4` (81.267.513 bytes).
- Pipeline de predicciones: **149 ventanas ok /0 fallidas** en6.5min (motor d35).
- Tests de la suite que subiste: **`testReleaseUnitTest` BUILD SUCCESSFUL**.

##3. Integración de ramas (sin perder avances de nadie)
- **Plan** del pensador Claude: `C:\Users\yeran\INTEGRACION_PLAN.md` (258 líneas) — inventario,
 9 archivos en intersección con `origin/DEV`, resolución por archivo, rollback con tags.
- **Snapshot** (ambos repos, antes de tocar nada):
  - Backend → `feature/llamada-vivo-d35` @ `f8d6d86` (107 archivos).
  - Android → `feature/llamada-overlay-grabacion` @ `7ddb558` (77 archivos).
- **Integración**:
  - Backend: tag `respaldo/pre-integracion-main` → pull main (ff) → merge `origin/DEV` (12 commits
    D30-D36 de NiconiCL/Víctor: MiMo, créditos Token Plan, feedback freemium/generico, ciclo offline)
    → merge final `7da0fde`.
  - Android: sin rama dev (main==origin/main) → merge `46ff239`.
- **Verificación propia (no confiada al worker)**:
  - Backend: **582 tests,0 fallos** (tras T5.8); Android: **464 tests,0 fallos**.
  - `git status` limpio en ambos; sin push (pendiente de tu OK; decidir subir a `origin/DEV`
    o `origin/main` en backend).
- Basura saneada: `=5.26` borrado; gitignore anclado `/pruebas/` (¡el patrón `pruebas/` sin anclar
  ignoraba `src/test/kotlin/PRUEBAS/` por la insensibilidad de mayúsculas de Windows —**16 tests
  nunca estaban versionados** y entraron en el commit!); `opencode.json`, `task-t*.md`, `/APKs/` ignorados.

##4. Fixes de código (commit `d000df4` en backend)
1. **`POST /pruebas/video`500** → `receiveMultipart(formFieldLimit=210MB)` + tests nuevos.
2. **Pill mostraba `divlara-e14-mlp`** → `CONSTANTES_VISION.VERSION_MODELO_DIVLARA = "divlara-d35-mlp"`.
3. **Modelo d35 incompleto** → `MANIFIESTO.json` (4 semillas, SHA-256, macro86.20) y
   `golden_features.json` (ventana343 features;14 de cara rellenadas con `scaler.mean` de d35;
   `expected_predicciones_onnx` recalculado ejecutando el ONNX real con onnxruntime).

##5. Calibración con Cloud como referencia (tu vídeo)
-36 frames (cada3s) anotados por Claude → `cloud_anotacion.txt` vs nuestro reporte:
  - **Brazos cruzados**: Cloud36s reales → modelo detecta (4.0) ✓ pero duración "pegajosa" (segmento = llamada completa).
  - **Manos a la cara**: Cloud **48s reales** → modelo **1.3/8** = no lo ve. **No es umbral: es modelo/features** (el mayor gap real).
  - **Desvío de mirada**: modelo3.4 < umbral3.5 → **no sale recuadro** (recomendado: bajar a3.0).
  - **Manos en la mesa**: Cloud **0** → modelo **5 falsos positivos** (confunde apoyo natural con toque).
  - **Segmentos nunca cierran** (duración máxima = duración de la llamada) → añadir histeresis de cierre.
- Detalle completo: `comparacion-cloud-mlp.md` (bitácora del proyecto).

##6. Reviews Antigravity (3 subagentes)
- **Review1 (Android)** ✅ —16 hallazgos priorizados; guardar: `review-android-antigravity.md`.
  P1: caja corporal con iris, consentimiento vs permiso cámara, sync sube sin gate de consentimiento,
  **contrato `/api/v1/llamadas` pierde `promedios`+`segmentos`**, `GRABAR_PRUEBA` sin blindar en release.
- **Reviews2 (backend)** ✅ — veredicto: integración D30-D36 "conservó intacta la lógica Kotlin",
  pero **4 bloqueantes**: P0 `/pruebas/video`+`GET /pruebas` sin auth/entorno/cuota (DoS disco),
  P0 `promedios`/`segmentos` descartados por `ignoreUnknownKeys` (pérdida silenciosa), P1 IDOR/FK500
  en `POST /api/v1/llamadas` (no valida propiedad de `sesionId`), P1 CI roto en GitHub Actions
  (7 tests exigen `modelo.onnx` que `.gitignore` excluye). P2: d35 con salidas enteras → empates
  alfabéticos en `ConstructorReporteSimulacion`; race `use-after-close` en `CargadorModeloDivlara`
  (posible SIGSEGV ONNX); `urlSalud` roto con endpoints sin ruta. P3: docs con arrastre e14/resnet-v1.
- **Reviews3 (integración)** ✅ — **"NO perdió código ni funcionalidad de ninguno de los dos lados"**:
  lado DEV (41 archivos exclusivos) =0 diferencias vs HEAD; lado feature (98 archivos) =0 pérdidas
  (solo3 diferencias = arreglos intencionales de d000df4);0 marcadores de conflicto;582/582 tests
  verificados1:1 con65 XMLs ejecutados4s antes del commit. Hallazgos menores: `opencode.json`/
  `task-t315.md` en el índice pese a gitignore (`git rm --cached`), docs sin actualizar (e14, conteos),
  mis3 notas de la noche sin trackear en `docs/05 Estado/`.
- Guardados: reviews completos en el transcript y resúmenes en `docs/05 Estado/`.

##7. Infra nocturna activa
- Cron **`Bitacora noche DivLara`** (cada60m, `33ee80bace8a`): redacta con OmniRoute (`model=auto`)
  y añade a `docs/05 Estado/Bitacora-noche-2026-11-23.md`.
- Kanban `divlara`: T4.1-T4.4 ✅, T5.1 ✅ (prueba), T5.3/T5.4/T5.4b ✅, T5.7 parcial→T5.8 ✅;
  **T5.9** (backend persista promedios/segmentos) y **T5.10** (reviews) en cola.
- Cron obsoleto `Aviso plan listo DivLara` pausado (fallaba por directorio inexistente).

##8. Pendientes para tu decisión / mañana
1. **Push**: ¿subimos main a `origin/DEV` (integración del equipo) o a `origin/main`? (sin push sin tu OK).
2. **T5.9**: backend debe persistir `promedios`+`segmentos` (review P1 — el historial pierde la línea de tiempo).
3. **Umbral `MIRADA_DESVIADA`→3.0** y histeresis de segmentos (build Android nuevo — requiere rebuild+install).
4. **Manos a la cara**: investigar features en vivo (con1.3 no hay umbral que lo salve).
5. `/pruebas/video` **sin autenticación** (ok para pruebas; añadir JWT antes de producción).
6. Re-sync del teléfono: el pill ahora mostrará d35 en la próxima llamada (constante corregida).
7. Claude auth vence entre29-nov y02-dic (`claude auth login`).

---
*Generado:23-nov-2026 ~03:00 — se actualiza con los reviews2/3 al terminar.*
