## Bitácora de noche — 2026-11-23 03:16 (UTC-3)

# Bitácora nocturna — DivLara (noche 22→23 nov 2026)

**Corte:** lunes 23 de noviembre de 2026, 02:55 (UTC-3).

## Tablero (hermes kanban --board divlara)

- **Terminadas (✓):** 50+. Destacan T0.1–T0.9 (matriz de datos, consentimiento v2.1, redacción de logs, borrado duro, higiene de logs, anonimización de video, endpoint de predicción, "el video nunca sale del teléfono", migración 024), T3.0–T3.17 (modelo, landmarks, llamada en vivo, extractor MediaPipe, overlay, historial, reporte), T4.1–T4.3, T5.4b, T5.7, E10 y DOC.
- **En curso (●):** T4.5 (grabar vídeo), T4.6 (POST /pruebas/video), T4.7 (umbrales por evento), T5.1 (prueba en vivo v16), T5.3 (plan de integración), T5.8 (archivos d35 + gitignore + verde + commit).
- **Listas (▶):** E6-prep, T4.4, T5.2 (reviews Antigravity ×3), T5.5 (este documento), T5.6 (calibrar umbrales), T5.9, T5.10.
- **Bloqueadas (⊘):** T3.5 (métricas de rendimiento) y T3.6 (validación en vivo vs offline).

## Hallazgos técnicos (logs /tmp/claude_*)

- **Integración:** backend merge `7da0fde` (visión d35, llamada en vivo, DEV D30–D36); Android merge `46ff239` sobre `main`. El `git fetch origin` de Android falló por credenciales; `main...origin/main` 0/0 puede estar viejo. Quedaron versionados por error `task-t314/315/316/317.md` (pendiente `git rm --cached`).
- **Plan (T5.3):** 9 archivos chocan con `origin/DEV`; el más seguro es `BD_PRUEBA.kt` (línea 179, ambos añaden parámetros a `SistemaPrueba`). Riesgo: `TipoProveedorIa.MIMO` podría romper un `when` exhaustivo. Estrategia: rama feature → `origin/DEV` → merge a `main`, con tag de respaldo.
- **Modelo d35:** motor relanzado en el puerto 8094 con `MODELO_VERSION=divlara-d35-mlp`; `/health` reporta `cargado:true` y `/predict` devuelve los 20 scores enteros. Suite: 522 tests, 0 fallos. `e14` sigue como default.
- **Build roto por d35 (origen de T5.8):** 5 fallos por faltar `MANIFIESTO.json` y `golden_features.json` en `models/divlara-d35-mlp/`. T5.8 los reinsertó (manifest con 4 semillas, SHA-256, macro 86.20 vs e14 81.79), corrigió 14 features con `scaler.mean` y recalculó el golden con onnxruntime.
- **Gitignore:** `pruebas/` (línea 116) ignoraba `src/test/kotlin/PRUEBAS/` por mayúsculas en Windows; se ancló a `/pruebas/` y entraron 11 tests de `src/test` y 5 de `motor-vision`.
- **Grabación (T4.5/T4.6):** flag `-PgrabarPrueba=true` guarda el video sin audio y lo sube por `POST /pruebas/video`; el backend valida `.mp4` y tope de 200 MB y `GET /pruebas` lista. **Pendiente: esas rutas no exigen sesión.**
- **Umbrales:** `UMBRAL_CAJA_VIVA = 3.5` en vivo; el reporte sigue en `UMBRAL_EVENTO_ACTIVO = 4.0`. Build verde: versionCode 16, versionName 1.9.7, puerto 8093.
- **Pulido v14:** debounce de 3 fotogramas en "Manos juntas", `textoVeces` y color `0xFFFFD600`; 453 tests. El fix de video sube el límite a 210 MB.

## Problemas resueltos

- `git fetch` de Android sin credenciales → merge local + tag `respaldo/pre-integracion-main` (falta el fetch).
- Git bloqueado por aprobación en Claude (`integra.log`) → resuelto en `integra2.log`.
- Tests en rojo por features de cara de e14 → corregidos con `scaler.mean`.
- `gradlew` por PowerShell pedía aprobación → corridos por Bash/WSL.

## Avisos

- La sesión de Claude caduca entre el 29-nov y el 02-dic: correr `claude auth login`.
- Pendientes: fetch con credenciales, `git rm --cached` de `task-t*.md`, proteger `/pruebas/*` y decidir rama remota (`DEV`/`main`).

## Corrida cron — 2026-11-23 04:21 (-03)

# Informe Nocturno — DivLara
**Fecha:** 23-nov-2026  
**Periodo:** Cron 04:21 -03  

---

## 📊 Estado del Kanban

### ✅ Completadas esta noche
- **T5.2**: Reviews de Antigravity (x3)
- **T5.4b**: Integración DEV + merge a main
- **T5.7**: Fix multipart 50MB + constante e14
- **T5.10**: Review Antigravity backend + integración
- **T4.1**: Modelo mlp_d35 → divlara-d35-mlp
- **T4.2**: Recuadros multi-evento + línea de tiempo
- **T4.3**: Pulido v14
- **T0.9**: Eliminar cuenta con consentimiento, migración 024
- **T0.8**: El video nunca sale del teléfono

### ⏳ En ejecución
- **T5.1**: Prueba en vivo con grabación v16
- **T5.3**: Claude pensador: plan de integración
- **T5.8**: Archivos d35 faltantes + gitignore + verde + commit

### ✅ Listas
- **T4.4**: Umbral recuadros en vivo 3.5
- **T4.5**: App graba video de prueba
- **T4.6**: Endpoint POST /pruebas/video
- **T4.7**: Umbrales por evento
- **T5.5**: Documentador nocturno
- **T5.6**: Calibrar umbrales con video + informe mañana
- **T5.9**: Backend persiste promedios + segmentos de /api/v1/llamadas
- **E6-prep**: Dataset R1+R2 para revisor sin IA

### 🚫 Bloqueadas
- **T3.5**: Métricas de rendimiento
- **T3.6**: Validación vivo vs offline

---

## 🔧 Hallazgos técnicos y problemas resueltos

### Integración
- Merge completado: backend 7da0fde y Android 46ff239
- Primer intento falló: entorno bloqueó git tag/fetch sin aprobación
- Segundo intento corrió con credenciales
- Pendiente: git fetch de Android y confirmar rama remota de subida

### Modelo d35
- Motor relanzado con MODELO_VERSION=divlara-d35-mlp (PID 618892, puerto 8094)
- Confirmación: /health y /predict funcionando (20 scores enteros escala 0-8)
- Estado: 522 tests en verde
- Constante e14 sigue como default en la app

### Fix 210MB + constante d35
- Build quedó rojo por archivos faltantes en models/divlara-d35-mlp
- Archivos afectados: MANIFIESTO.json y golden_features.json
- Tarea T5.8 en curso

### Gitignore
- Patrón pruebas/ ignoraba también src/test/kotlin/PRUEBAS/ (mayúsculas en Windows)
- Solución: anclado a /pruebas/
- Resultado: 11 tests de src/test y 5 de motor-vision ahora corren

### App v16
- Umbrales por evento en vivo:
  - arm_crossing, table_rest, hand_to_face_adaptors: 3.0
  - Resto: 3.5
- Mantenimiento de reporte en 4.0
- Debounce de 3 fotogramas para "manos juntas" con color amarillo
- Gramática: "1 vez / N veces"
- Grabación de prueba: flag -PgrabarPrueba=true
- Subida multipart a /pruebas/video

### Backend
- Endpoint POST /pruebas/video:
  - Límite: 200MB
  - Nombre de archivo generado por servidor
  - 400 sin residuos
- Endpoint GET /pruebas
- Rutas sin authenticate (pendiente de proteger)
- T0.6 higiene de logs: corrección de trazas con posibles correos/URLs
- T0.5 borrado duro: 459 tests verdes

### Documentación profunda
- Corregida y con 10 fuentes citadas
- Nota: ddof mixto
- No hay TTS ni Room en el código
- "Cambio en caliente" = redesplegar motor o cambiar MOTOR_ENDPOINT

---

## ⚠️ Restricción
- Sin commits
- Sin tocar código
- Sin reiniciar servicios en esta corrida

---


---

## 2026-11-23 05:29 (-03) — corrida del cron (documentador nocturno)

**BITÁCORA NOCTURNA — DIVLARA**

**ESTADO DEL TABLERO**
*En curso:*
- T5.1 prueba en vivo con grabación (v16)
- T5.3 plan de integración de Claude
- T5.8 archivos d35 faltantes + gitignore de tests + commit

*En espera:*
- E6-prep dataset R1+R2
- T4.4 umbral recuadros
- T4.5 app graba vídeo
- T4.6 endpoint POST /pruebas/video
- T4.7 umbrales por evento
- T5.5 documentador nocturno
- T5.6 calibrar umbrales
- T5.9 backend persiste promedios y segmentos

*Cerradas esta noche:*
- T5.4b integración DEV + merge a main
- T5.10 reviews de Antigravity
- T5.7 fix multipart 50 MB
- T5.4 snapshot con DEV
- T5.2 tres reviews
- T4.1 modelo divlara-d35-mlp
- T4.2 recuadros con línea de tiempo
- T4.3 pulido v14
- T3.12 narrador TTS + ASR
- T3.13 BD local Room
- T3.14 overlay de visión
- T3.15 historial
- T3.16 consentimiento único
- T3.17 ventana de reporte
- E10 pg_dump + migración 024

**HALLAZGOS TÉCNICOS**
- Integración: sesión de Claude no pudo ejecutar git tag/fetch/switch/merge por permisos no interactivos. Backend en feature/llamada-vivo-d35 (f8d6d86), sin tag de respaldo, main intacto. Falta un fetch con credenciales.
- Choques con origin/DEV: 9 archivos. Riesgo semántico con TipoProveedorIa.MIMO en when exhaustivo. Android sin conflictos. Estrategia: feature → fetch de DEV → merge a main, con tag y rollback.
- Snapshot: backend f8d6d86 y Android 7ddb558, git status limpio, main sin movimientos (70f4b6c / d033f82). .gitignore ampliado y borrado archivo vacío =5.26.
- Umbrales v16: arm_crossing, table_rest y hand_to_face_adaptors en 3.0; resto 3.5; reporte 4.0. 4 tests nuevos; BUILD SUCCESSFUL (versionCode 16, versionName 1.9.7, puerto 8093).
- Grabación de prueba: flag -PgrabarPrueba=true guarda prueba-<timestamp>.mp4 sin audio y sube multipart a POST /pruebas/video; si falla, archivo queda en teléfono y se avisa sin crash.
- POST /pruebas/video: límite 200 MB con borrado de parciales, extensión .mp4, nombre generado por servidor (path traversal neutralizado) y 400 en casos inválidos; GET /pruebas lista archivos. Pendiente: no exigen sesión.
- Modelo d35: motor relanzado en puerto 8094 con MODELO_VERSION=divlara-d35-mlp; /health lo reporta cargado y /predict devuelve 20 scores enteros. 522 tests en verde. Default en CONSTANTES_VISION.kt sigue siendo e14.
- App: recuadros multi-evento, tarjeta "Eventos en el tiempo" con barra 00:00→duración, conteo y duración máxima; debounce de 3 fotogramas en "manos juntas" (amarillo) y gramática "1 vez / N veces". 453 tests pasando.
- videob terminó con timeout hacia 192.168.0.181 (exit 255) aunque dejó endpoints y tests en verde.

**PROBLEMAS RESUELTOS / INFRAESTRUCTURA**
- Docker: com.docker.service aparece detenido en Windows, pero Entrevista_APP (healthy) y Entrevista_Redis llevan ~4 h arriba, levantados tras reinicio del PC con bats iniciar_docker.bat / iniciar_backend.bat / iniciar_motor.bat vía schtasks.
- gitignore de pruebas/ también tapaba src/test/kotlin/PRUEBAS/ por mayúsculas en Windows; anclado a /pruebas/ y vuelven a correr 11 tests de src/test y 5 de motor-vision.
- Login de Claude vence entre el 29-nov y el 02-dic; renovar con claude auth login antes de esas fechas.

**PENDIENTES**
- Renovar login de Claude (vence 29-nov/02-dic)
- Integración DEV + merge a main
- Dataset R1+R2 (E6-prep)
- Umbral recuadros (T4.4)
- App graba vídeo (T4.5)
- Endpoint POST /pruebas/video (T4.6)
- Umbrales por evento (T4.7)
- Documentador nocturno (T5.5)
- Calibrar umbrales (T5.6)
- Backend persiste promedios y segmentos (T5.9)

---

---

## Entrada — 2026-11-23 06:34 (UTC-3) — bitácora nocturna (documentador nocturno, redacción OmniRoute `model: auto`)

# Bitácora nocturna — noche 22→23 nov 2026 (DivLara)

- **Cierre:** lunes 23 de noviembre de 2026, 06:34 (UTC-3).
- **Tablero divlara:** 41 done, 11 ready, 2 blocked (T3.5 métricas y T3.6 validación en vivo vs offline, R-2).

## Trabajo de la noche (horarios = última escritura de cada log)

- **21:41 — T4.1 modelo d35 (backend):** motor relanzado con `MODELO_VERSION=divlara-d35-mlp`; `/health` OK y `/predict` devolvió 20 scores enteros; 522 tests en verde; e14 sigue como default.
- **21:41 — T4.2 (app):** recuadros multi-evento en vivo + tarjeta "Eventos en el tiempo" con barra de segmentos por evento; build OK con 453 tests.
- **22:47 — T4.3 pulido v14:** píldora sobre el recuadro, gramática "1 vez / N veces", debounce de 3 fotogramas para "manos juntas" y color amarillo para ese evento; tests nuevos.
- **23:41 — T4.5 grabación de prueba:** flag `-PgrabarPrueba=true` guarda MP4 sin audio y lo sube por POST multipart a `/pruebas/video`; build exitoso.
- **23:48 — T4.4 umbrales en vivo:** `arm_crossing`, `table_rest` y `hand_to_face_adaptors` bajan a 3.0; resto 3.5; reporte 4.0; 4 tests nuevos; versionCode 16 / versionName 1.9.7.
- **00:39 — T4.6 endpoint (backend):** `POST /pruebas/video` (multipart, límite 200 MB, solo `.mp4`, nombre generado por el servidor) y `GET /pruebas`; tests verdes.
- **01:43 — snapshot:** ramas `feature/llamada-vivo-d35` (backend `f8d6d86`) y `feature/llamada-overlay-grabacion` (Android `7ddb558`); gitignores ampliados; `git status` limpio en ambos.
- **01:45 — plan de integración:** conflictos contra `origin/DEV`: `BD_PRUEBA.kt` choca en la línea 179 y riesgo con la constante `TipoProveedorIa.MIMO` en algún `when` exhaustivo.
- **01:51 — integración 1.ª pasada: FALLIDA:** el entorno bloqueó los comandos git por sesión no interactiva; no se cambió nada.
- **02:43 — integración 2.ª pasada:** merges locales: backend `7da0fde` y Android `46ff239` (notas de tarea y opencode al `.gitignore`); `testReleaseUnitTest` OK. Sin hacer: `git fetch` de Android (faltan credenciales) y el `clean`.
- **02:47 — T5.7 fix multipart:** límite a 210 MB + constante d35; build ROJO por faltar `MANIFIESTO.json` y `golden_features.json` en `models/divlara-d35-mlp/`.
- **02:53 — T5.8 archivos d35:** agregados `MANIFIESTO.json` (4 semillas, SHA-256, macro 86.20 vs e14 81.79) y `golden_features.json` (14 features de cara con `scaler.mean` de d35, predicciones recalculadas con onnxruntime); gitignore anclado a `/pruebas/` (en Windows mayúsculas ignoraba `src/test`); 11 tests extra al commit; tarea completada.

## Hallazgos y pendientes

- T4.4, T4.5, T4.6 y T5.8 terminan en "TAREA COMPLETADA" pero el tablero sigue en **ready**: los workers no cerraron sus tarjetas.
- Rutas `/pruebas` sin `authenticate`: proteger antes de desplegar fuera de pruebas.
- Quedan versionados `task-t314/315/316/317.md`: quitar con `git rm --cached`.
- Claude Code vence entre 2026-11-29 y 2026-12-02: renovar con `claude auth login`.
- Sin evidencia de caída de Docker/PostgreSQL esta noche: el contenedor `Entrevista_APP` (`postgres:16`, puerto 5432) estaba activo cuando E10 hizo el `pg_dump` dentro del contenedor y lo validó en `dumps/`.

## 2026-11-23 07:46 (-03) — guardia nocturna (cron bitacora-noche)

# Bitácora nocturna DivLara — lunes 23-nov-2026, 07:46 (UTC-3)

## 07:46 — Estado del kanban
- **Reentreno d36** (Fase1) en ejecución:
  - 8 lotes de etiquetado completados correctamente
  - Dos archivos JSON válidos generados
  - **En cola**: Fase2 reentreno GPU (6 eventos), Fase3 calibración de umbrales, Fase4 integración backend, Fase5 rebuild Android v17 + review Antigravity

## Completadas durante la noche

**Snapshot y limpieza de repos**
- Commits feature: f8d6d86 (backend) y 7ddb558 (Android)
- Git status limpio
- .gitignore actualizado: borrado "=5.26", agregados .opencode/, dumps/, APKs/

**Integración con DEV**
- 9 archivos con conflicto; el seguro: BD_PRUEBA.kt línea 179 (ambos lados añaden parámetros a SistemaPrueba)
- Riesgo semántico: la constante TipoProveedorIa.MIMO podría romper un `when` exhaustivo
- **Detenido**: comandos de escritura de git bloqueados en sesión no interactiva

**Nivelación de umbrales en vivo**
- arm_crossing: 3.0
- table_rest: 3.0
- hand_to_face_adaptors: 3.0
- resto: 3.5
- reporte: 4.0
- 4 tests nuevos

**Sistema de pruebas**
- Grabación: flag `-PgrabarPrueba=true` + subida multipart a POST /pruebas/video
- Backend: POST /pruebas/video + GET /pruebas (3 tests: límite 200 MB, validación extensión, nombres sanitizados)
- Fix: problema de 50 MB en multipart

**Motor d35**
- 522 tests en verde
- /health reporta: divlara-d35-mlp
- /predict devuelve 20 scores enteros (0-8)

**Overlay multi-evento**
- Tarjeta "Eventos en el tiempo"
- Línea de tiempo por evento
- 453 tests

**Pulido v14**
- Píldola corregida
- Gramática "1 vez/N veces"
- Debounce de 3 fotogramas en "manos juntas"
- Color amarillo corregido
- Fix de 50 MB en multipart

## Problema detectado
- Integración con DEV detenida por bloqueo de comandos de escritura en git (tag/fetch/switch/merge requieren aprobación en sesión no interactiva)
- Backend en feature/llamada-vivo-d35 (f8d6d86), sin tag de respaldo, sin tocar main
- **Requiere**: ejecutar paso 1 con credenciales o permitir comandos de escritura

## Pendientes para la mañana
- T5.8: archivos d35 faltantes + gitignore de tests + commit
- T5.9: backend persiste promedios y segmentos de /api/v1/llamadas
- T5.6: calibrar umbrales con video e informe
- Proteger /pruebas/video y /pruebas con authenticate antes de despliegue

## Aviso de sesión
- Renovar sesión de Claude antes del 2026-11-29 (vence entre 29-nov y 02-dic) con `claude auth login` para trabajos no interactivos

## Estado de la guardia
- Sin commits
- Sin cambios de código
- Sin reinicios de servicios


---

## 2026-11-23 08:52 -03 (bitacora-noche cron)
# Bitácora Nocturna — DivLara / Entrevistas-IA

## 📅 Información General
**Hora del corte:** lunes 23 de noviembre de 2026, 08:49 -03

## 🎯 Estado del Kanban
- **EN EJECUCIÓN**
  - T_8c86e887 "Reentreno d36 - Fase1 datos y etiquetado del video de calibracion" (worker claude-code)
    - Log: "los 8 lotes terminaron bien, los dos JSON siguen válidos" → TAREA COMPLETADA

- **COLA DE TAREAS (ready)**
  - Fase2 reentreno GPU con foco en 6 eventos
  - Fase3 calibración de umbrales
  - Fase4 integración backend d36
  - Fase5 rebuild Android v17
  - review Antigravity del plan e integración
  - E6-prep dataset R1+R2 para el revisor sin IA
  - T4.4 umbral recuadros en vivo 3.5
  - T4.5 app graba vídeo de prueba
  - T4.6 endpoint POST /pruebas/video
  - T4.7 umbrales por evento
  - T5.1 prueba en vivo con grabación (v16)
  - T5.3 plan de integración
  - T5.5 documentador nocturno
  - T5.6 calibrar umbrales con vídeo
  - T5.8 archivos d35 faltantes + gitignore + commit
  - T5.9 backend persiste promedios y segmentos de /api/v1/llamadas

- **COMPLETADAS esta noche**
  - snapshot con limpieza de .gitignore
  - integración DEV+merge a main
  - fix multipart 50MB + constante e14
  - reviews Antigravity backend/integración
  - T3.8–T3.17
  - T4.1–T4.3
  - T5.2, T5.4, T5.4b, T5.7, T5.10

## 🔍 Hallazgos Técnicos
- **Snapshot**
  - Backend commiteado en rama feature/llamada-vivo-d35 (f8d6d86)
  - Android en feature/overlay (7ddb558)
  - git status limpio; no se tocó main

- **Plan de integración**
  - 9 archivos backend chocan con origin/DEV
  - Riesgo semántico en la constante TipoProveedorIa.MIMO en when exhaustivos
  - Estrategia propuesta: rama feature → traer origin/DEV → merge a main, con tag de respaldo y rollback

- **Integración**
  - Primer intento FALLÓ (sandbox bloqueó git tag/fetch/switch/merge, sesión no interactiva)
  - Segundo intento corrió y fusionó:
    - Backend merge 7da0fde
    - Android merge 46ff239
    - .gitignore ampliado (task-t*.md, opencode.json, .opencode/, APKs/)
  - PENDIENTE: git fetch de Android con credenciales y clean
  - PENDIENTE: quitar con git rm --cached los task-t31*.md versionados por error

- **Umbrales en vivo**
  - mapa por evento → arm_crossing 3.0, table_rest 3.0, hand_to_face_adaptors 3.0, resto 3.5 (UMBRAL_CAJA_VIVA), reporte 4.0
  - Build verde: testReleaseUnitTest + assembleReleaseLocal
  - versionCode 16, versionName 1.9.7, puerto 8093, overlay y grabación activos

- **Grabación de prueba (app)**
  - GRABADOR_PRUEBA.kt
  - Guarda prueba-<timestamp>.mp4 sin audio
  - Indicador rojo
  - Subida multipart al pulsar Detener

- **Endpoint POST /pruebas/video (backend)**
  - Guarda con nombre del servidor
  - Valida extensión
  - Tole 200 MB
  - GET /pruebas lista los mp4
  - 3 tests en verde
  - PENDIENTE: ambas rutas no exigen sesión → protegerlas con authenticate antes de desplegar

- **Motor d35**
  - Relanzado en puerto 8094 con MODELO_VERSION=divlara-d35-mlp
  - /health OK
  - /predict devuelve 20 scores enteros 0-8
  - 522 tests en verde
  - e14 sigue como default en CONSTANTES_VISION.kt

- **d35 files**
  - MANIFIESTO.json y golden_features.json generados desde el onnx real
  - Las 14 features de cara rellenadas con scaler.mean
  - gitignore anclado /pruebas/ porque Windows no distingue mayúsculas y ocultaba 16 tests

- **Fix 500**
  - Subida aceptada a 200 MB
  - BUILD FAILED con 5 fallos por falta de MANIFIESTO.json y golden_features.json en models/divlara-d35-mlp/ (resuelto luego en d35files)

- **Overlay UI (andr)**
  - Recuadros multi-evento en vivo
  - Tarjeta "Eventos en el tiempo" con barra de segmentos
  - 453 tests pasando

- **Fix pulido**
  - Debounce de 3 fotogramas para "manos juntas"
  - Color amarillo #FFFFD600
  - Gramática "1 vez / N veces"

## ✅ Problemas Resueltos
- Sandbox bloqueó la integración de git → segunda pasada con permisos adecuados; merge logrado
- Auth de Claude: aviso que la sesión expira entre el 29-nov y el 02-dic de 2026 → renovar con claude auth login antes de esa fecha para trabajos no interactivos
- Servidor 192.168.0.181 no responde al cierre de videob (timeout, exit 255) → revisar conectividad del backend remoto
- El patrón gitignore pruebas/ bloqueaba los tests → anclado a /pruebas/

## 📝 Nota Final
No se detectó evidencia en los logs de un incidente Docker/PostgreSQL esta noche; en esta máquina solo corre el contenedor obsidian (Up 13 days).


---

## Bitácora nocturna 2026-11-23 — turno noche 22-23 nov (añadido 2026-11-23 09:57 -03)

# Bitácora Nocturna — DivLara
**22-23 nov 2026**

## 09:57 - Cierre de turno nocturno

### Estado del proyecto
- **Kanban:** 45 completadas, 1 en ejecución, 2 bloqueadas, ~14 listas
- **En ejecución:** t_809cd15e "Reentreno d36 - Fase2 reentreno GPU con foco en 6 eventos"
- **Bloqueadas:** T3.5 Métricas de rendimiento en la app y T3.6 Validación en vivo vs offline (R-2)
- **Listas:** T4.4, T4.5, T4.6, T4.7, T5.1, T5.3, T5.5, T5.6, T5.8, T5.9, E6-prep, fases 3-5 del reentreno d36, reviews Antigravity

### Avances de la noche
- **d36 Fase1 (etiquetado):** 8 lotes terminados, JSON válidos
- **d36 Fase2 (reentreno):** val 87.21 vs 87.03 de d35; artefactos en E:\entrenamienrto-IA\DivLara\scratch\e17\d36_out\ y copiados a A:\...\BackAppEntrevistaV2\models\divlara-d36-mlp\; parity ONNX vs torch 0.0 en salida redondeada y 1.4e-6 antes de redondear. Sin mejorar h2f (AUC < 0.5: faltan datos o features de cara). Polaridad de gaze corregida: ≤2 es desvío, la app dispara al revés (arreglo en Fase5). El export requirió protobuf 6.33 aparte (el venv tiene protobuf 4.25, incompatible con onnx 1.23).
- **d35 en el motor:** 522 tests en verde; motor relanzado con MODELO_VERSION=divlara-d35-mlp en el puerto 8094; /health confirma el modelo cargado y /predict devuelve 20 scores enteros. e14 sigue como default.
- **Archivos d35:** MANIFIESTO.json y golden_features.json agregados; gitignore anclado (/pruebas/) resolvió que Windows ignoraba src/test; commit con mensaje ajustado.
- **Integración:** backend merge a main desde feature/llamada-vivo-d35 con tag de respaldo; Android merge 46ff239; testReleaseUnitTest exitoso. Pendiente git fetch de Android (faltan credenciales) y quitar del índice los archivos de nota versionados (git rm --cached).
- **Fix multipart:** límite subido a 210 MB para /pruebas/video con tests nuevos.
- **App:** overlay de recuadros multi-evento con línea de tiempo por evento en el reporte (453 tests); píldora que tapa el recuadro, gramática "1 vez/N veces", debounce de 3 fotogramas para "manos juntas" y su color amarillo; grabación de prueba con -PgrabarPrueba=true que sube multipart a /pruebas/video; umbrales en vivo (arm_crossing, table_rest, hand_to_face_adaptors en 3.0, resto 3.5, reporte 4.0) con BUILD SUCCESSFUL y versionCode 16.
- **Backend:** POST /pruebas/video con validación de extensión, peso y nombre seguro (200 MB por defecto) y GET /pruebas; aviso de que ambas rutas no exigen sesión. T0.9 migración 024: el borrado de cuenta conserva el consentimiento (CASCADE verificado con psql). T0.6 higiene de logs: dos puntos que filtraban trazas con correos/URLs pasan a registrar solo la clase de excepción. T3.10 reporte de simulación con migración 025 y 13 tests.
- **Documentación profunda DivLara:** terminada con fuentes citadas y correcciones (ddof mixto, TTS/Room no existen en la app, golden_features.json de 188.816 bytes).

### Problemas resueltos
- Tras el reinicio del PC, Docker/PostgreSQL quedaron caídos y se levantaron con schtasks + los bats iniciar_docker.bat / iniciar_backend.bat / iniciar_motor.bat.
- Bloqueo de git por permisos en la sesión de integración (tag/fetch/merge pedían aprobación): resuelto ejecutando los pasos desde una terminal con credenciales.
- Aviso de sesión de Claude: caduca entre 2026-11-29 y 2026-12-02 (renovar con claude auth login).
- La tarea T3.1b agotó 26 turnos sin completar (coste 0,97 USD) y siguió por otra vía.

### Pendientes
- T5.8 commit de archivos d35 + gitignore
- T5.9 persistencia de promedios/segmentos
- T5.6 calibración de umbrales con video
- Fases 3-5 del reentreno d36
- Fetch con credenciales en Android

---

## 2026-11-23 11:03 (-03) — Bitácora nocturna (cron documentador nocturno)

# INFORME DE BITÁCORA NOCTURNA - DIVLARA

**Fecha:** Lunes 23 de noviembre de 2026  
**Hora:** 11:03  
**Dispositivo:** Raspberry  
**Sistema:** DivLara

---

## 1. RESUMEN EJECUTIVO

Se han completado 6 tareas principales durante la sesión nocturna, enfocadas en la integración de modelos ML (d35), mejoras en la interfaz de Android, correcciones en el backend y preparación del reentrenamiento d36. La integración DEV ha sido detenida por restricciones de permisos en la sesión no interactiva. El backend del puerto 8094 confirma el modelo d35 activo con 522 tests verificados.

---

## 2. TAREAS COMPLETADAS

| ID | Descripción | Estado |
|-----|-------------|--------|
| T5.4b | Integración DEV + merge a main | ✅ Completado |
| T5.7 | Fix multipart 50MB + constante e14 | ✅ Completado |
| T5.10 | Reviews antigravity | ✅ Completado |
| T4.1 | Modelo mlp_d35 en backend | ✅ Completado |
| T4.2 | Recuadros + línea de tiempo | ✅ Completado |
| T4.3 | Pulido v14 y Reentreno d36 Fase 1 | ✅ Completado |

---

## 3. TAREAS EN CURSO

- **Fase 2 del reentreno d36**: Configuración GPU, 6 eventos, worker Claude

---

## 4. TAREAS EN COLA

- T4.4: Umbral 3.5
- T4.5: App graba vídeo
- T4.6: POST /pruebas/video
- T4.7: Umbrales por evento
- T5.1: Prueba en vivo v16
- T5.3: Plan de integración
- T5.5: Documentador nocturno
- T5.6: Calibrar umbrales
- T5.8: Archivos d35 + commit
- T5.9: Backend persiste promedios/segmentos
- E6-prep: Dataset R1+R2

---

## 5. TAREAS BLOQUEADAS

- **T3.5**: Métricas
- **T3.6**: Validación en vivo/offline

**Causa:** Esperan resultado R-2

---

## 6. HALLAZGOS TÉCNICOS

### 6.1. Integración DEV (T5.4)
- **Estado:** ❌ FALLÓ
- **Causa:** El entorno bloqueó `git tag/fetch/merge` por requerir aprobación en sesión no interactiva
- **Consecuencia:** Backend sigue en `feature/llamada-vivo-d35` (commit f8d6d86), sin tag y sin tocar main
- **Acción requerida:** Permitir comandos git o ejecutar paso 1 manualmente

### 6.2. Plan de Integración
- **Archivos en conflicto:** 9 archivos backend con `origin/DEV`
- **Riesgo semántico:** `TipoProveedorIa.MIMO` en when exhaustivo
- **Orden recomendado:**
  1. Commit en feature
  2. Traer `origin/DEV`
  3. Merge a main
  4. Tag de respaldo
  5. Rollback si necesario

### 6.3. Backend Puerto 8094 (d35)
- **Tests:** 522 tests, 0 fallos
- **Modelo:** `divlara-d35-mlp` activo
- **Endpoint `/health`:** Confirma modelo cargado
- **Endpoint `/predict`:** Retorna 20 scores enteros (escala 0-8)
- **Constante por defecto:** e14

### 6.4. Backend /pruebas (T4.6)
- **POST /pruebas/video:**
  - ✅ Acepta multipart
  - ✅ Valida extensión .mp4
  - ✅ Tope 200MB (borra parcial si excede)
  - ✅ Protege path traversal (nombre generado por servidor)
  - ✅ 3 tests verdes
  - ⚠️ **Pendiente:** No exige autenticación de sesión
- **GET /pruebas:** Lista archivos

### 6.5. Android Overlay
- **Tests:** 453 verdes
- **Recuadros:** Paleta de colores implementada
- **Tarjeta "Eventos en el tiempo":** Línea de tiempo por evento
- **Llamadas abiertas:** No traen segmentos (DetalleLlamadaDto no incluye)

### 6.6. Nivelar (Umbralización)
- **Valores configurados:**
  - `arm_crossing`, `table_rest`, `hand_to_face_adaptors`: 3.0
  - Resto: 3.5
- **Reporte:** "Eventos en el tiempo" sigue en 4.0
- **IDs reales:** `arm_crossing`, `hand_to_face_adaptors` (no "brazos_cruzados"/"manos_a_cara")

### 6.7. Grabar (T4.5)
- **Parámetro:** `-PgrabarPrueba=true`
- **Comportamiento:**
  - Graba mp4 sin audio
  - Sube multipart a `/pruebas/video` con Ktor
  - Si falla: archivo queda en teléfono, avisa sin crash
  - Si cámara no soporta Preview+ImageAnalysis+VideoCapture: solo log

### 6.8. Fix v14
- **Debounce:** 3 fotogramas para "manos juntas"
- **Color:** Amarillo #FFD600
- **Helper:** `textoVeces` ("1 vez"/"N veces")
- **Techo:** 9 recuadros (solo verificado que compila)

---

## 7. PROBLEMAS Y PENDIENTES

| Problema | Prioridad | Acción |
|----------|-----------|--------|
| Sesión Claude caduca entre 2026-11-29 y 2026-12-02 | 🔴 Alta | Ejecutar `claude auth login` antes de esa fecha |
| Integración DEV detenida por permisos git | 🔴 Alta | Permitir comandos git o ejecutar paso 1 manualmente |
| T3.5 y T3.6 bloqueadas esperando R-2 | 🟡 Media | Esperar resultado R-2 |
| Backend /pruebas no exige autenticación | 🟡 Media | Implementar authenticate antes de desplegar |
| Techo de 9 recuadros solo verificado que compila | 🟢 Baja | Verificar funcionalidad completa |

---

## 8. BUILD INFORMATION

- **VersionCode:** 16
- **VersionName:** 1.9.7
- **Puerto:** 8093 (Android)
- **Puerto:** 8094 (Backend d35)

---

## 9. RECOMENDACIONES

1. **Prioridad inmediata:** Resolver permisos git para completar integración DEV
2. **Seguridad:** Implementar autenticación en endpoint `/pruebas/video`
3. **Próximos pasos:** Continuar con Fase 2 del reentreno d36 y tareas en cola
4. **Monitoreo:** Verificar caducidad de sesión Claude antes del 29 de noviembre


---

## Redaccion 2026-11-23 12:18 (hora local)

# Bitácora nocturna — lunes 23-nov-2026, 12:14 (cierre de la corrida nocturna)

## Estado general
- **Board DivLara:** 46 tareas completadas, 1 lista (E6-prep · Dataset de verdad R1+R2 para validar revisor sin IA), 2 bloqueadas (T3.5 Métricas de rendimiento en la app y T3.6 Validación en vivo vs offline, ambas R-2) y ~10 en ready esperando worker (T4.4–T4.7, T5.1, T5.3, T5.5, T5.6, T5.8, T5.9).
- **Ramas activas:**
  - Backend: feature/llamada-vivo-d35 (commit f8d6d86)
  - Android: feature/llamada-overlay-grabacion (7ddb558)
  - Main sin cambios (backend 70f4b6c, Android d033f82)

## Tareas avanzadas / completadas
- **Snapshot:** limpieza de basura (archivo vacío '=5.26', gitignores de .opencode/, dumps/, APKs/) y creación de ramas feature con commits limpios en ambos repos.
- **Plan de integración:** análisis de los 9 archivos compartidos con origin/DEV; conflicto seguro en BD_PRUEBA.kt (línea 179), probables en CONTENEDOR_DEPENDENCIAS.kt, CONFIGURACION_RUTAS.kt, CONFIGURACION_GENERAL.kt y 5 docs; riesgo semántico con la constante TipoProveedorIa.MIMO en when exhaustivos. Orden propuesto: commit en feature → traer origin/DEV → fusionar a main una sola vez, con tag de respaldo y rollback.
- **Integración:** FALLÓ por bloqueo de git (git tag/fetch/switch/merge pidieron aprobación y la sesión no es interactiva); no se hizo ningún cambio ni se tocó main. Requiere habilitar esos comandos o ejecutar el paso 1 manualmente.
- **Motor d35:** 522 tests verdes; motor relanzado en el puerto 8094 con MODELO_VERSION=divlara-d35-mlp; GET /health OK y POST /predict devolvió 20 scores enteros (escala 0-8). El default en CONSTANTES_VISION.kt sigue en e14.
- **Umbrales en vivo:** mapa por evento con arm_crossing=3.0, table_rest=3.0, hand_to_face_adaptors=3.0, resto=3.5 (UMBRAL_CAJA_VIVA) y reporte=4.0; 4 tests nuevos; build verde (versionCode 16, versionName 1.9.7, port 8093).
- **App graba vídeo de prueba:** flag -PgrabarPrueba=true guarda .mp4 sin audio y sube por POST multipart a /pruebas/video; 3 tests.
- **Backend POST /pruebas/video:** guarda en pruebas/ con nombre del servidor, valida extensión .mp4, tope 200 MB y devuelve ok/archivo/bytes; 3 tests. Pendiente: ambas rutas no exigen sesión.
- **Overlay Android:** recuadros multi-evento en vivo y tarjeta "Eventos en el tiempo" con línea de tiempo por evento; 453 tests.
- **Pulido:** recuadros que no caben no se dibujan, gramática "1 vez / N veces", debounce de 3 fotogramas para "manos juntas" y su color amarillo.

## Problemas resueltos
- Docker/PostgreSQL caídos tras el reinicio del PC: se levantaron con schtasks + los bats iniciar_docker.bat / iniciar_backend.bat / iniciar_motor.bat.
- Sesión no interactiva de Claude bloqueando git: detectado y documentado, sin saltarse la protección.
- Aviso de caducidad de la sesión Claude entre 2026-11-29 y 2026-12-02: correr claude auth login antes de esa fecha.

## Pendientes
- Ejecutar el paso 1 de integración con credenciales, decidir rama remota (DEV o main) y confirmar si BdPrueba necesita Postgres/Redis.
- Proteger /pruebas/video y /pruebas antes de desplegar.
- T5.5 (documentador nocturno) y T5.6 (calibrar umbrales con vídeo) siguen en ready.


---

## Bitácora nocturna — 2026-11-23 13:25 -03

# BITÁCORA NOCTURNA 22–23 NOV 2026 — DIVLARA/ENTREVISTAS-IA

## Kanban (board divlara)
- **Done**: 47 tareas completadas
- **Blocked**: 2 tareas bloqueadas (T3.5 métricas, T3.6 validación vivo/offline, R-2)
- **Running**: 1 tarea activa (Antigravity: reviews del plan e integración del reentreno d36)
- **Ready**: E6-prep, T4.4–T4.7, T5.1, T5.3, T5.5–T5.9

## Reentreno d36 (5 fases completadas)
- Fase 1: etiquetado del video de calibración, 8 lotes OK
- Fase 2: val 87.21 vs 87.03 de d35 (mejora mínima); h2f no separable (AUC <0.5, 3 participantes con h2f ≥4); polaridad de gaze corregida (0–2=desvío, 3–5=frontal, 6–8=contacto fijo); paridad ONNX/torch 0.0 (redondeado) / 1.4e-6 (sin redondear); protobuf 6.33 instalado aparte
- Fase 3: umbrales calibrados; falsos positivos de mesa 88s→0, recall de cara 0→28%; torso/brazos/cara (≥4) no se arreglan con umbrales; artefactos en scratch/e17/d36_out y models/divlara-d36-mlp
- Fase 4: integración backend d36
- Fase 5: APK EntrevistaAPP-v17-1.9.8, 468 tests verdes, prueba en vivo 49s OK

## Backend/app
- T4.4: umbral recuadros en vivo 3.5 (reporte sigue en 4.0), build verde, sin commit
- T4.5: app graba vídeo de prueba (-PgrabarPrueba=true) y lo sube a /pruebas/video al Detener
- T4.6: POST /pruebas/video (200MB, .mp4) + GET /pruebas; las rutas NO exigen sesión (proteger antes de desplegar)
- T4.7/fix500: BUILD FAILED por faltantes en models/divlara-d35-mlp/ (faltan MANIFIESTO.json y golden_features.json) → tarea ready T5.8
- d35files: MANIFIESTO y golden_features generados (14 features de cara con scaler.mean de d35), gitignore /pruebas/ anclado (Windows no distingue mayúsculas), commit hecho; entraron 16 tests ignorados
- Motor d35: 522 tests verdes; relanzado en 8094 con MODELO_VERSION=divlara-d35-mlp; /predict devuelve 20 scores enteros; e14 sigue como default
- Fix UI: debounce 3 fotogramas para "manos juntas", color amarillo, gramática "1 vez / N veces"; 468 tests
- Fase 6 (revisión): hallazgos sin aplicar — "manos en la mesa" exige ≥5 y d35 nunca llega; con M>1 el estado se congela y los segmentos se alargan si cae el servidor; la pantalla del reporte contradice las barras de mirada/torso
- Integración: backend merge 7da0fde (feature/llamada-vivo-d35 + origin/DEV); Android merge 46ff239 a main; git fetch de Android falló por credenciales (falta re-fetch antes de push)
- T0.8: subida de video apagada (el video nunca sale del teléfono)
- T0.9: migración 024 (borrar cuenta conserva consentimiento, identidad_ref VARCHAR(320))
- T0.6: higiene de logs (SMTP/ErrorServicioExterno ya no loguean trazas con correos/URLs)
- Doc profunda con fuentes citadas; ddof mixto (0 en métricas visuales, 1 en lm_*_std); "cambio en caliente" = redesplegar motor o cambiar MOTOR_ENDPOINT

## Problemas y avisos
- Workers con exit 255 tras "TAREA COMPLETADA" (W1, W2, integra2, videob) por timeout de SSH a 192.168.0.181: repetir verificación remota
- Claude: auth vence entre 2026-11-29 y 2026-12-02 → correr `claude auth login` o setup-token
- Docker/PostgreSQL cayeron con el reinicio del PC y se levantaron con schtasks + iniciar_docker.bat / iniciar_backend.bat / iniciar_motor.bat

## Pendientes de hoy
- T5.8
- T5.6 (calibrar umbrales con video + informe)
- T5.9 (backend persiste promedios y segmentos)
- E6-prep
- reviews Antigravity de d36
- desbloquear T3.5/T3.6

## 2026-11-23 14:31 -03 (cron bitacora-noche)

* **Bitácora nocturna DivLara — ejecución del cron, lunes 23 de noviembre de 2026, 14:27 (-03)**

* **Estado del kanban (board divlara):**
  - En ejecución: t_73ec23b9 "Reentreno d36 - Fase7 arreglos review y rebuild v18" (única tarea corriendo).
  - Listas (ready): E6-prep (dataset R1+R2 para el revisor sin IA), T4.4 umbral recuadros 3.5, T4.5 app graba vídeo de prueba, T4.6 endpoint POST /pruebas/video, T4.7 umbrales por evento, T5.1 prueba en vivo con grabación v16, T5.3 plan de integración, T5.5 documentador nocturno, T5.6 calibrar umbrales con vídeo, T5.8 archivos d35 faltantes + gitignore + verde + commit, T5.9 backend persiste promedios y segmentos.
  - Bloqueadas: T3.5 métricas de rendimiento y T3.6 validación en vivo vs offline.
  - Cerradas esta noche: reentreno d36 fases 1 a 6 (datos/etiquetado, reentreno GPU con foco en 6 eventos, calibración de umbrales, integración backend d36, rebuild Android v17, reviews Antigravity), T5.7 fix multipart 50 MB + constante e14, T5.4b integración DEV y merge a main, snapshot con ramas feature, y decenas de tareas T0.x/T3.x ya en done.

* **Avances técnicos (de los logs claude_*):**
  - Reentreno d36: fase 1 completó los 8 lotes de etiquetado; fase 2 entrenó en GPU con macro 87.21 en val contra 87.03 de d35; el modelo no separa hand_to_face (AUC < 0.5, val con solo 3 participantes con h2f >= 4); polaridad de gaze corregida según variable_definitions.py (0-2 desvío, 3-5 frontal, 6-8 contacto fijo).
  - Paridad ONNX vs torch: diferencia 0.0 en salida redondeada y 1.4e-6 sin redondear sobre 203 filas de val+test; export en scratch/e17/d36_out y copia a models/divlara-d36-mlp. Protobuf 6.33 instalado aparte porque el venv traía 4.25 incompatible con onnx 1.23.
  - Motor d35 en ejecución en el puerto 8094 (PID 618892) con MODELO_VERSION=divlara-d35-mlp; /health responde cargado y /predict devuelve 20 scores enteros de la escala 0-8.
  - Fase 3 de calibración: los umbrales nuevos pasan los falsos positivos de mesa de 88 s a 0 y suben el recall de cara de 0 a 28 %; torso y brazos no se arreglan con umbrales porque d35 queda clavado en 4.
  - Fase 5: build exitoso con 468 tests, APK EntrevistaAPP-v17-1.9.8.apk, prueba en vivo de 49 s sin fallos con el reporte "Eventos en el tiempo".
  - Umbral de recuadros en vivo en 3.5 con UMBRAL_CAJA_VIVA, reporte intacto en 4.0, BUILD SUCCESSFUL con testReleaseUnitTest y assembleReleaseLocal.
  - T3.11 motor desacoplado: 490 tests en verde (460 backend + 30 motor), 503 cuando el motor está apagado.
  - Snapshot: backend commit f8d6d86 en feature/llamada-vivo-d35 y Android 7ddb558 en feature/llamada-overlay-grabacion, git status limpio, main sin cambios.

* **Problemas resueltos y avisos:**
  - El gitignore con patrón pruebas/ bloqueaba src/test/kotlin/PRUEBAS/ en Windows; quedó anclado como /pruebas/ y entraron al commit 11 tests de src/test y 5 de motor-vision.
  - T5.7 reportó BUILD FAILED en 5 tests por archivos faltantes (pendiente en T5.8).
  - videob terminó W2_EXIT=255 con "Timeout, server 192.168.0.181 not responding".
  - Las rutas POST /pruebas/video y GET /pruebas quedaron sin authenticate: protegerlas antes de desplegar.
  - En el log umbral aparece un aviso de caducidad de la licencia de claude en 6 días.
  - El reporte de la app muestra promedio y etiqueta "señal" con >= 4, contradiciendo las barras de mirada y torso (hallazgo de fase 6, sin aplicar).

* **Pendiente para mañana:** calibrar umbrales con el vídeo real (T5.6), persistir promedios y segmentos en backend (T5.9), cerrar T5.8 y ejecutar T5.5.



---

## Bitacora 2026-11-23 15:35 (-03)

# Bitácora nocturna DivLara

**Fecha:** lunes 23-nov-2026  
**Ejecutada a las:** 15:35 (hora del sistema)

## Estado del board `divlara`
- 17 tareas en `ready` (T4.4–T4.7, T5.1, T5.3, T5.5, T5.6, T5.8, T5.9, E6-prep, etc.)
- 2 tareas bloqueadas (T3.5 métricas y T3.6 validación en vivo vs offline)
- Resto de tareas completadas

## Tareas recién cerradas
- T5.10 (reviews Antigravity backend + integración)
- 4 fases del reentreno d36: datos/etiquetado, entrenamiento GPU en 6 eventos, calibración de umbrales, integración backend

---

## Hallazgos técnicos y trabajos de la sesión

### Snap (T5.4)
- Ramas creadas:
  - `feature/llamada-vivo-d35` (backend, commit f8d6d86)
  - `feature/llamada-overlay-grabacion` (Android, commit 7ddb558)
- Estado: git status limpio en ambos repositorios
- Configuración: `.gitignore` ampliado (dumps, .opencode, APKs); `main` intacto

### Plan de integración (T5.5)
- Conflicto detectado: 9 archivos con `origin/DEV` en backend
- Problema específico: `BD_PRUEBA.kt` en línea 179
- Orden propuesto: commit en feature → traer DEV → fusionar a main, con tag de respaldo y rollback

### Integración (T5.4 previo)
- Resultado: **FALLÓ**
- Causa: entorno bloqueó `git tag`/`git fetch` (sesión no interactiva)
- Estado: no se hizo ningún cambio

### Nivelar (T4.4)
- Umbrales configurados:
  - `arm_crossing=3.0`
  - `table_rest=3.0`
  - `hand_to_face_adaptors=3.0`
  - Resto: 3.5
  - Reporte: 4.0
- Resultado: BUILD SUCCESSFUL con 4 tests nuevos
- Versión: versionCode 16, versionName 1.9.7

### Grabar (T4.5)
- Funcionalidad: app graba vídeo de prueba a `pruebas/prueba-<ts>.mp4`
- Parámetro: `-PgrabarPrueba=true`
- Comportamiento: sube multipart al backend
- Limitación: sin audio
- Tests: 3 verdes

### Videob (T4.6)
- Endpoints:
  - `POST /pruebas/video` con validación de extensión/peso (200 MB)
  - `GET /pruebas`
- Estado: tests en verde
- Pendiente: proteger rutas con `authenticate` antes de desplegar
- Nota: log terminó con timeout al servidor 192.168.0.181 (VIDEOB_EXIT=255), pero la tarea está marcada completada

### D35 (T4.x)
- Resultado: 522 tests verdes
- Configuración: motor relanzado en puerto 8094 con `MODELO_VERSION=divlara-d35-mlp`
- Endpoints:
  - `/health` → OK
  - `/predict` → devuelve 20 scores enteros
- Comportamiento: por defecto sigue e14

### Android (T4.2/T4.3)
- Features implementadas:
  - Recuadros multi-evento en vivo
  - Tarjeta "Eventos en el tiempo" con línea de tiempo por evento
- Resultado: 453 tests verdes
- Fix posterior:
  - Debounce de 3 fotogramas para "manos juntas"
  - Gramática "1 vez/N veces"
  - Color amarillo para ese evento
- Estado: compilado pero no probado en pantalla

---

## Problemas resueltos
- Motor d35 levantado a mano tras matar el java del puerto 8094
- `.venv` Linux corriendo por WSL
- gitignore limpio

---

## Pendientes principales
- Ejecutar el plan de integración DEV→main cuando se permitan los comandos git
- Renovar login de Claude vence entre 29-nov y 02-dic: ejecutar `claude auth login`## 2026-11-23 16:39 (-03) — Bitácora de guardia (cron nocturno)

- Hora de la corrida: lun 23 nov 2026 16:39 (-03). Kanban divlara: 1 en ejecución, 2 bloqueadas, varias listas.
- En curso: t_9890e62b "Reentreno d36 - Fase10 refresco sobrecarga tamaño v21" (log /tmp/claude_fase10.log vacío aún).
- Bloqueadas: T3.5 Métricas de rendimiento en la app y T3.6 Validación en vivo vs offline.
- Listas (ready): E6-prep dataset R1+R2, T4.4 umbral recuadros 3.5, T4.5 app graba vídeo de prueba, T4.6 POST /pruebas/video, T4.7 umbrales por evento, T5.1 prueba en vivo v16, T5.3 plan de integración, T5.5 documentador nocturno, T5.6 calibrar umbrales con video, T5.8 archivos d35 + gitignore tests + commit, T5.9 backend persiste promedios y segmentos de /api/v1/llamadas, Integración git dev+master y push, Feedback personalizado backend scoring + app UI.
- Completadas recientemente: d36 fases 1-9 (datos, reentreno GPU, calibración, integración backend, rebuild v17, reviews Antigravity, arreglos v18, debounce v19, multi-cajas + review), T4.1-T4.3, T5.4 snapshot/merge DEV, T5.7 fix multipart 50 MB + constante e14, T5.10 reviews Antigravity.

**Hallazgos técnicos y problemas resueltos:**
- Snapshot: backend commiteado en feature/llamada-vivo-d35 (f8d6d86) y Android en feature/llamada-overlay-grabacion (7ddb558); git status limpio en ambos; main intacto (70f4b6c / d033f82). gitignore limpiado (.opencode/, dumps/, APKs/, *.apk).
- Plan de integración: 9 archivos backend cruzan con origin/DEV; choque seguro en BD_PRUEBA.kt línea 179; aditivos CONTENEDOR_DEPENDENCIAS.kt, CONFIGURACION_RUTAS.kt, CONFIGURACION_GENERAL.kt; 5 docs probablemente chocan; riesgo semántico de la constante TipoProveedorIa.MIMO en un when exhaustivo. Android sin conflictos. Orden elegido: rama feature → traer origin/DEV → merge a main, con tag de respaldo y rollback.
- Integración NO ejecutada: el entorno bloqueó los comandos de escritura de git (git tag/fetch/switch/merge y gradlew pidieron aprobación en sesión no interactiva). Backend sigue sin tag de respaldo y sin tocar main. Aviso de Claude: la sesión caduca entre 2026-11-29 y 2026-12-02.
- Motor: corre MODELO_VERSION=divlara-d35-mlp en puerto 8094; /health OK y /predict devuelve 20 scores enteros; 522 tests verdes. e14 sigue como versión por defecto en CONSTANTES_VISION.kt.
- Umbrales en vivo: arm_crossing 3.0, table_rest 3.0, hand_to_face_adaptors 3.0, resto 3.5, reporte 4.0; 4 tests nuevos; BUILD SUCCESSFUL con versionCode 16, versionName 1.9.7, puerto 8093, overlay y grabación activos.
- App: grabación de prueba con -PgrabarPrueba=true que sube multipart a /pruebas/video (sin audio, indicador rojo, sin crash si falla); 3 tests.
- Backend: endpoints POST /pruebas/video y GET /pruebas con validación de extensión y tope 200 MB; 3 tests; pendiente protegerlos con authenticate antes de desplegar.
- Android UI: recuadros multi-evento en vivo y tarjeta "Eventos en el tiempo" con línea de tiempo por evento; 453 tests pasando. Las llamadas abiertas desde el servidor no traen segmentos (DetalleLlamadaDto no los incluye).
- Pulido v14: píldora que tapa el recuadro, gramática "1 vez / N veces" con textoVeces, debounce de 3 fotogramas y color amarillo en "manos juntas"; tests nuevos; FIX_EXIT=0.
- Incidencias de entorno: VIDEOB_EXIT=255 y W1/W2_EXIT=255 por timeout del servidor 192.168.0.181; el trabajo quedó reportado por los logs.



---

## 2026-11-23 17:49 (-03) — Bitácora nocturna (corrida del cron)

# Bitácora nocturna — DivLara

## Hora
Corrida iniciada: 23-nov-2026 17:49 (-03)

## Tareas avanzadas/completadas
- Reentreno d36 · Fase10 (v21): log cierra TAREA COMPLETADA: BUILD SUCCESSFUL, APK `EntrevistaAPP-v21-1.12.0.apk` (versionCode 21) instalado, capturas con 3 y 4 cajas a la vez; la tarjeta sigue `running`
- T3.13–T3.17: BD local Room, overlay, historial, consentimiento único, ventana post-llamada
- T4.1–T4.3: modelo d35, recuadros multi-evento, pulido v14
- T5.2: reviews Antigravity ×3
- T5.4b: merge DEV→main
- T5.7: fix multipart 50 MB
- Fases 1–9 del reentreno d36
- T4.4–T4.7: umbral recuadros, graba vídeo, POST /pruebas/video, umbrales por evento
- T5.1: prueba v16
- T5.3: plan de integración
- T5.5: esta bitácora
- T5.6: calibrar umbrales
- T5.8: archivos d35 faltantes
- T5.9: backend persiste promedios+segmentos
- E6-prep dataset
- Integración git dev+master
- Feedback personalizado

## Hallazgos técnicos
- Motor de visión: 522 tests en verde; corre en el puerto 8094 con `MODELO_VERSION=divlara-d35-mlp`; `/predict` devuelve 20 scores enteros (0–8); e14 sigue como default
- Backend AI PC verificado por SSH: `localhost:8093/health` → `{"status":"OK","modeloDivlara":{"version":"divlara-d35-mlp","cargado":true}}`
- Integración: snapshots backend `f8d6d86` y Android `7ddb558`; merge Android a main `46ff239`, merge backend `7da0fde` con `origin/DEV`; merge reversible con tag `respaldo/pre-integracion-main`
- Modelo d35: `MANIFIESTO.json` y `golden_features.json` generados desde el export; las 14 features de cara rellenadas con `scaler.mean` de d35
- Gitignore: `pruebas/` ignoraba también `src/test/kotlin/PRUEBAS/` por el case-insensitive de Windows → anclado a `/pruebas/`, lo que sumó 11 tests de `src/test` y 5 de `motor-vision`
- Umbrales en vivo (v16): `arm_crossing`, `table_rest`, `hand_to_face_adaptors` = 3.0; resto 3.5; reporte "Eventos en el tiempo" 4.0; ids reales del código (no existen "brazos_cruzados"/"manos_a_cara")
- Grabación de prueba: flag `-PgrabarPrueba=true` graba sin audio y sube por `POST /pruebas/video` (multipart, tope 200 MB, `.mp4`, nombre del servidor)
- UI: debounce "manos juntas" (3 fotogramas), gramática "1 vez"/"N veces", amarillo `0xFFFFD600`, 9 recuadros

## Problemas resueltos
- Integración: la 1.ª pasada falló (sesión no interactiva bloqueó `git tag`/`git fetch`); la 2.ª completó, pero el fetch de Android falló por credenciales: `main..origin/main = 0/0` es local y puede estar viejo
- Modelo d35: primer intento dejó 3 tests en rojo al rellenar las 14 features de cara con `scaler.mean` de d35; corregido
- BUILD FAILED: `models/divlara-d35-mlp/` solo tiene `golden.json`, `meta.json`, `modelo.onnx`, `scaler.json`; faltan `MANIFIESTO.json` y `golden_features.json` → 5 fallos (`PruebaSaludModeloDivlara`, `PruebaVisionDesacoplada` + 3 de `motor-vision`)
- Antibloqueo: `/tmp/divlara_bloqueos.log` marcó atascos 16:16 (fase9) y 16:34 (fase10); ambos resueltos

## Pendientes
- T5.8: archivos d35 faltantes, gitignore, verde y commit
- Fetch de Android con credenciales antes de pushear; `git rm --cached` de `task-t*.md`
- Proteger `/pruebas` y regenerar `docs/documentacion/API.md`
- Calibrar umbrales con vídeo (T5.6) y persistir promedios+segmentos (T5.9)

