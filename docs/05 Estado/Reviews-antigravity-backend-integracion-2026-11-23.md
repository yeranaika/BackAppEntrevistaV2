# Reviews Antigravity — Backend + Integración (T5.2/T5.10,23-nov-2026 ~02:57-03:10)

## Review3 — Integración de ramas: VEREDICTO FINAL
**"La integración en main (d000df4 sobre7da0fde→b3ca82d) NO perdió código ni funcionalidad de ninguno de los dos lados."**
- Lado `origin/DEV` (12 commits D30-D36, NiconiCL/Víctor): **APROBADO sin pérdidas** —41 archivos
  exclusivos con0 diferencias vs HEAD; los9 compartidos conservan D30 MiMo, D31 créditos Token Plan,
  D32 feedback<5min, D33 ComparadorTextos, D34 STAR, ciclo offline, D36 feedback genérico.
- Lado feature local (visión d35, llamada en vivo, revisión local, consentimiento): **APROBADO** —
 98 archivos exclusivos,0 pérdidas;3 diferencias = arreglos intencionales de d000df4.
-0 marcadores de conflicto en todo el repo;65/65 archivos de prueba ↔65 XMLs; **582/582 verdes**
 (552 backend +30 motor-vision) ejecutados4s antes del commit.

## Review2 — Backend: hallazgos priorizados
### P0 (bloquean despliegue)
1. `POST /pruebas/video` + `GET /pruebas` montados SIEMPRE, sin auth, sin flag de entorno, sin
   cuota/rate-limit → DoS de disco (200MB anónimos) + fuga de listado (CONFIGURACION_RUTAS.kt:58,
   CONTROLADOR_PRUEBA_VIDEO.kt:44-80). Extras: sin magic bytes ftyp, nombre con condición de
   carrera (ms), huérfanos en IOException, JSON por interpolación.
2. `promedios`/`segmentos` descartados en silencio (ignoreUnknownKeys) → el historial de llamadas
   pierde desglose0-8 y línea de tiempo (ESQUEMA_LLAMADA.kt:11-14, MODELO_LLAMADA.kt:17-20,
   SERVICIO_LLAMADA.kt:62-70, VISTA_LLAMADA.kt:14-18; Android REMOTO_LLAMADAS.kt:31-43 los envía).

### P1
3. IDOR/FK500 en `POST /api/v1/llamadas`: con `sesionId`+`resumen` no valida propiedad/existencia
   → sesión ajena insertada o500 por FK (SERVICIO_LLAMADA.kt:36-43,62-65).
4. CI GitHub Actions en rojo garantizado:7 tests exigen `models/divlara-d35-mlp/modelo.onnx`
   excluido por .gitignore:108; además el test del límite51MB puede OOM en runners512MB.
5. `POST /api/v1/vision/predecir` acepta sesiones finalizadas/purgadas (sin estado EN_PROGRESO)
   y valida sesionId DESPUÉS de inferir (desperdicia ONNX); sin rateLimit.
6. Race use-after-close en `CargadorModeloDivlara` (close() vs run() JNI → SIGSEGV posible) +
   inferencia síncrona en el event-loop (CONTROLADOR_MOTOR.kt:25-29).

### P2
7. d35 con salidas enteras (shift+round+clip) → empates constantes; `ConstructorReporteSimulacion`
   gana por orden alfabético (SERVICIO_SIMULACION.kt:76-81) — variables basales opacan eventos.
8. `urlSalud` roto con MOTOR_ENDPOINT sin ruta o con `/`; reenvío de sesionId al motor;
   `refrescarEstado` sin isSuccess.
9. Entradas sin acotar →500 PostgreSQL (fechaInicioMs Long.MAX_VALUE en SERVICIO_LLAMADA.kt:79;
   bandaLocal/revisadoEnMs en SERVICIO_REVISION_LOCAL.kt:99-112).
10. Dualidad de evaluadores léxicos: `ComparadorTextos` (D33, preserva C#/C++) vs
    `RevisorTextoLibre` (T3.13, destruye C#/C++ y ignora alternativas/sinónimos).

### P3
- Docs con arrastre: Puesta en marcha enumera solo hasta023 (faltan024-027), cita
  `divlara-resnet-v1`; GENERAR_DOCUMENTACION/API.md citan e14; meta.json "12 cabezas" (son20);
  `opencode.json`+`task-t315.md` en el índice pese a gitignore (`git rm --cached`).

### Riesgos citados
- Railway sin migraciones022-027 →500 en llamadas/visión/revisión/borrado.
- Push a main → CI rojo por onnx.
- Apagado de motor-vision bajo carga → SIGSEGV ONNX.

### Verificación de fusión (review2)
- ProveedorMimo, CreditosTokenPlan, TopeGeneracionIa, FeedbackGenerico, cableado en
  CONTENEDOR_DEPENDENCIAS y BD_PRUEBA: **correctamente fusionados, sin regresiones**.
- SHA-256 de los5 artefactos d35 coinciden con MANIFIESTO.json ✓.

### Cambios recomendados (resumen)
Flag+auth+cuota para /pruebas; persistir promedios/segmentos (no requiere migración, JSONB);
validar sesionId/estado ANTES de inferir + rateLimit; ReentrantReadWriteLock en Cargador;
Assume.assumeTrue para onnx en CI; git rm --cached de opencode/task-t315; unificar normalización;
actualizar docs e14→d35 y aplicar migraciones022-027 antes de desplegar.
