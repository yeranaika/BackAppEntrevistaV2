# T3.15 — Historial de llamadas en vivo: persistir cada llamada con su reporte

## Objetivo
El usuario reportó: "el historial de llamadas no queda guardado". Al terminar una
**llamada en vivo** debe quedar registrada (fecha, duración, #preguntas, resumen de
ocurrencias/acciones reconocidas del reporte tabulado de T3.10) y ser **consultable
en la app** en una pantalla "Historial de llamadas".

## Backend (BackAppEntrevistaV2)
1. Verificar qué existe HOY: `SERVICIO_ENTREVISTA.kt` / `TABLA_ENTREVISTA.kt` /
   `REPOSITORIO_SESION_ENTREVISTA.kt` (están modificados por tareas previas) — ver si
   la sesión de llamada ya se persiste al cerrar. Si falta: crear tabla `llamada`
   (id, usuario, fecha_inicio, duracion_seg, preguntas_cnt, resumen JSONB) + migración
   SQL en `src/DB` siguiendo el patrón existente (línea 159-style: tabla +
   `ALTER ... OWNER TO` + índices).
2. Endpoints: `POST /api/v1/llamadas` (al cerrar llamada, o derivarla del cierre del
   reporte T3.10 `/vision/reporte/{id}`), `GET /api/v1/llamadas` (lista del usuario,
   paginada), `GET /api/v1/llamadas/{id}` (detalle con reporte).
3. NO romper contratos existentes; auth con el patrón de sesión actual.

## App (EntrevistaAPPAndroid)
1. Al cerrar la llamada en vivo (misma ruta donde ya se sube el último lote de
   landmarks o se guarda el consentimiento): guardar el resumen **en Room** (reusar la
   base local de T3.13 / `ALMACEN_LOCAL` con su patrón `HISTORIAL_GUARDADO_MAXIMO`)
   y encolar sincronización con server cuando haya red (mismo patrón que
   `*HistoryViewModel.cargarHistorial` de práctica/nivelación/entrevista).
2. Pantalla "Historial de llamadas": lista (fecha, duración, #acciones) + detalle con
   el reporte tabulado (línea de tiempo de acciones, nº ocurrencias, tiempo máximo por
   pose) ya disponible en backend. Navegación desde el Hub/Entrevista (junto a los
   historiales existentes).
3. Consulta offline: si no hay red, leer solo Room (requisito de "guardar en la base
   local del dispositivo para poder consultarlas cuando no haya internet").

## Tests
- Backend: H2 tests de repositorio + endpoints (GET lista vacía, POST+GET roundtrip).
- App: unit del ViewModel de historial con fuente fake; no romper 375 tests baseline.

## Entregable
APK versionada con `.\guardar-apk.ps1` + suites en 0 fallos antes de cerrar.
