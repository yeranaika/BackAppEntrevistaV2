---
tipo: estado
tags: [estado, fases, ramas]
actualizado: 2026-11-23
---

# Estado del proyecto

> [!info] Actualizar esta nota al terminar cada tarea (fecha, rama, qué cambió).

## Fases de la refactorización (todas terminadas)
| Fase | Qué incluyó | Rama | Commit | Pruebas al cerrar |
|---|---|---|---|---|
| 0 | Cimientos: errores, config, contenedor de dependencias, Argon2id, fuera endpoints inseguros | `refactor/fase-0` | `94fa311` | 68 |
| 1 | Login: tokens con rotación, Google | `refactor/fase-1-login` | `a9fed8a` (+E2E `93ab2e2`) | 85 · E2E 34 |
| 2 | Usuario: cuenta, perfil, onboarding, contraseñas, admin | `refactor/fase-2-usuario` | `ab83d11` | E2E 48 |
| 3 | Banco de preguntas e IA | `refactor/fase-3-preguntas` | `0bccce4` | 145 · E2E 38 |
| 4 | Integraciones: resiliencia, Redis, mercado, consentimientos, recordatorios, billing, límites | `refactor/fase-4-integraciones` | `fb06766` | 181 · E2E 71 |
| 5 | Simulación de entrevista + contrato Android | `refactor/fase-5-entrevista` | `f035361` | 211 · E2E 50 |
| 6 | Práctica, nivelación, sincronización offline | `refactor/fase-6-prueba` | `9d15d1b` | 243 · E2E 39 |
| 7 | Reporte de feedback y progreso por skill | `refactor/fase-7-feedback` | `75fdb3e` | 260 · E2E 23 |
| Docs | `docs/documentacion/` (API.md + Postman) y esta bóveda | `refactor/documentacion-api` | `ae49742` + esta bóveda | colección 78/78 |
| Mensajes | `mensaje` en todo éxito de escritura (todas las fases) | `refactor/mensajes-exito` | — | 261 · colección 78/78 |
| Disponibilidad Freemium | `/ready` espera el calentamiento, `/health` conserva liveness y se filtran sus logs | `codex/freemium-main-port` | `6de97b2` + filtro de logs | 265 pruebas · colección generada con 106 solicitudes |
| Tope mensual de IA | Cupo de reportes con IA por usuario premium y mes (`LIMITE_EVALUACIONES_IA_MENSUAL`, D17); al agotarlo, motor freemium | `feature/tope-mensual-ia` | (ver git log) | 281 pruebas · 6 mutaciones detectadas · revisión adversarial |
| Integración | Las tres ramas anteriores (CI, tope de IA, límite por IP) juntas y verificadas | `integracion/backend-v2` | (ver git log) | 305 pruebas |
| Apagado ordenado | Espera con tope al apagar, reporte cortado reintentable y rescate periódico (D20) | `feature/apagado-ordenado` | (ver git log) | 323 pruebas · 13 mutaciones detectadas · revisión adversarial |
| Costos de IA (admin) | `GET /api/v1/admin/costos-ia`: gasto por día y por modelo, con el estimado de las llamadas fallidas | `feature/costos-ia-admin` | (ver git log) | 337 pruebas · 8 mutaciones detectadas · revisión adversarial final del conjunto |
| Métricas de video idempotentes | Un lote reenviado por la app no se duplica: una métrica por `timestampMs` de la sesión (D21, migración 020) | `feature/metricas-idempotentes` | (ver git log) | 338 pruebas |
| Métricas sin emociones | `expresion` se ignora, gestos solo observables, lenguaje corporal `null` si no se midió (D22, migración 021) | `feature/metricas-idempotentes` | (ver git log) | 340 pruebas · humo en Postgres 16 real 20/20 (2026-11-07) |
| Reemplazo de entrevista sin pérdida | La app (`ENT`) reemplaza la entrevista en curso en la misma transacción que crea la nueva: un `409 preguntas_insuficientes` deja la anterior en curso | `fix/banco-preguntas` | (ver git log) | 360 pruebas · 4 mutaciones detectadas · revisión adversarial |
| Tope de gasto en generación con IA | `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` (5 USD/mes UTC, global) → `429 tope_generacion_ia_alcanzado`; fallidas con tokens; límite por IP `LIMITE_GENERACIONES_IA_POR_IP` (D23) | `fix/banco-preguntas` | (ver git log) | 360 pruebas · 4 mutaciones detectadas · revisión adversarial |
| Rúbrica en el snapshot de entrevista | `rubrica_snap` por pregunta servida; editar una pregunta deja su traza de IA pendiente (D24, migración 022) | `fix/banco-preguntas` | (ver git log) | 360 pruebas · 4 mutaciones detectadas · revisión adversarial |
| Semilla re-ejecutable | Preguntas y opciones de la semilla solo se insertan si no existen; `src/DB/PROBAR_SEMILLA.ps1` 7/7 en PostgreSQL 16 (2026-11-07) (D25) | `fix/banco-preguntas` | (ver git log) | 360 pruebas · 4 mutaciones detectadas · revisión adversarial |
| Preguntas con lo que pide el mercado | `generar-ia` usa las ofertas de empleo guardadas por la sincronización (o busca hasta 6 s) y responde `contexto_mercado`; `GET /api/v1/admin/preguntas/cobertura` muestra qué skills de cada cargo tienen pocas preguntas (D26) | `feature/preguntas-desde-mercado` | (ver git log) | 381 pruebas · revisión adversarial (2026-11-17) |
| Compra de Google Play de otra cuenta | `POST /billing/google/verify` lee `obfuscatedExternalAccountId` y responde `409 compra_ya_registrada` si la compra la pagó otra cuenta de la app (hash SHA-256 del id, igual que la app); sin el campo decide el dueño del token (D27) | `feature/cierre-pendientes` | (ver git log) | 418 pruebas · 11 mutaciones detectadas (2026-11-17) |
| Gasto del mes frente al tope en Costos IA | `GET /api/v1/admin/costos-ia` trae `topeGeneracionPreguntas` (7 claves) del mes UTC en curso; `ServicioTopeGeneracionIa` es la única fuente del 429 y del panel (D28) | `feature/cierre-pendientes` | (ver git log) | 418 pruebas · 11 mutaciones detectadas (2026-11-17) |
| Cupo de reportes con IA visible | `GET /billing/status` y el canje traen `reportes_ia_limite`, `reportes_ia_restantes` y `reportes_ia_renovacion` solo para premium con IA activa, con la misma condición SQL que la reserva (D29) | `feature/cierre-pendientes` | (ver git log) | 418 pruebas · 11 mutaciones detectadas (2026-11-17) |
| Credits del Token Plan de MiMo | `GET /api/v1/admin/costos-ia` trae `planTokenMimo` (Credits usados del periodo frente al plan, calculados con los tokens de cada llamada a MiMo); con Token Plan, `generar-ia` con `mimo-*` se frena por Credits y no por USD (D31) | `feature/creditos-token-plan` | (ver git log) | 437 pruebas · 2 mutaciones detectadas (2026-11-19) |
| Feedback en menos de 5 min | Tope de 45 s para la IA (si se pasa, reporte freemium sin gastar cupo), rescate cada 1 min y reporte atascado a los 3 min, palabras clave con `C#`/`C++`/`.NET` corregidas y log de tiempos (D32). Medido con MiMo real: 24 s con IA, ~0,1 s freemium | `feature/feedback-menos-de-5-min` | (ver git log) | pruebas completas en verde · E2E fase 7 con `-ConIa` 25/25 (2026-11-19) |
| Feedback freemium con comparador | `ComparadorTextos` (TF-IDF + cobertura + Jaccard + orden, varias referencias y sinónimos) sin IA; `respuestas_alternativas` y `sinonimos` en la rúbrica (manual y generada por IA); resumen freemium con nivel bueno/medio/bajo y aviso al premium (D33) | `feature/feedback-freemium-comparador` | (ver git log) | 451 pruebas (2026-11-21) |
| Prueba del ciclo offline con preguntas del banco | `PruebaControladorPrueba`: descarga `GET /api/v1/preguntas` (lo que guarda SQLDelight, sin la correcta), responde sin red y `/sync/attempts` corrige alternativas (correcta, opción borrada) y abiertas; reenvío idempotente. Revisión: la transcripción por voz ya es en el teléfono (sin STT en el backend) | `test/offline-preguntas-banco-sync` | (ver git log) | 452 pruebas · 1 mutación detectada (2026-11-22) |
| Feedback genérico por pregunta | La IA escribe `feedback_generico` (correcto, medio, malo) en la rúbrica al generar; `GET/POST /api/v1/admin/preguntas/feedback-generico[/completar]` completa las preguntas existentes sin cambiar su estado; la corrección freemium usa el texto del nivel (D36) | `feature/feedback-generico-preguntas` | (ver git log) | 474 pruebas (2026-11-22) |
| IP del consentimiento | `ip_origen` con la IP real del cliente (la misma que el límite por IP), sin DNS inverso | `feature/ip-consentimiento-y-limites` | (ver git log) | 325 pruebas · 2 mutaciones detectadas |
| Límite por IP en Redis | Contador compartido entre instancias (D18), IP de la conexión o cabecera validada (D19), respaldo en memoria | `feature/limite-ip-redis` | (ver git log) | 289 pruebas · 12 mutaciones detectadas · revisión adversarial |
| Consentimiento v1.1 y documentos legales | Texto `v1.1` sin video ni emociones (RNF09/RNF10), alcances `estadisticas_uso` (reemplaza a `analytics`, que la app vieja enviaba premarcado), `marketing` y el permiso aparte `analisis_conducta_camara`; solo cuentan los alcances que ofrece la versión; 409 al cambiar una versión ya aceptada; Privacidad, Términos y EULA 1.1.0 (D35). `videoClipUrl` se ignora y el modelo de visión de ejemplo queda deprecado (D37, migración 028; era la 023 en el PR #7) | `feature/consentimiento-v1-1` | (ver git log) | 480 pruebas · semilla y migración 11/11 (2026-11-23) |
| Motor de visión desacoplado (T3.11) | El modelo E14 sale del backend: segunda app Ktor (`motor-vision`, 8094) con `POST /predict`; el backend es cliente HTTP (`MOTOR_ENDPOINT`, 3 s + 1 reintento, 503 `MOTOR_NO_DISPONIBLE`). Ver [[Motor de vision]] | sin rama (cambios sin commit sobre `main`) | — | 490 pruebas (backend 460 + motor 30) |
| Revisor de texto libre sin IA (T3.13) | `SERVICIOS/REVISOR_TEXTO_LIBRE.kt` (misma fórmula en la app, paridad por `revisor_casos_dorados.json`), `GET /api/v1/revision-local/preguntas` y `POST /api/v1/revision-local/respuestas` (idempotente por `idLocal`; migración 026, tabla `revision_texto_local`). Pesos A=0,45 / B=0,20 / C=0,35 y umbrales 70/40 aprobados; similitud por defecto Levenshtein (r=0,808 vs Jaccard 0,786, n=44). **Calibración PROVISIONAL**: `sesion_pregunta_respuesta` estaba vacía, se calibró con las semillas y respuestas redactadas a mano. Migración 026 **sin aplicar** a la BD local | (rama sin crear, sin commit) | (sin commit) | 484 pruebas (460 + 24) · app 402 · 2 mutaciones detectadas |
| Historial de llamadas en vivo (T3.15) | `POST/GET /api/v1/llamadas` y `GET /api/v1/llamadas/{id}` (`SERVICIOS/SERVICIO_LLAMADA.kt`, `MODELOS/REPOSITORIO_LLAMADA.kt`, migración 027, tabla `llamada` con `resumen` JSONB). Idempotente por `(usuario, idLocal)`: 201 la guarda, 200 si ya estaba. Sin `resumen` y con `sesionId`, se deriva del reporte de T3.10. En la app (SQLDelight, no Room): tabla `llamada_local` (migración local 4.sqm, esquema v5), se guarda al detener la cámara, se sube con WorkManager y la pantalla "Historial de llamadas" lee solo la base local. Migración 027 **sin aplicar** a la BD local; app **sin compilar ni probar** en esta tarea (otro agente usaba gradle ahí) | (rama sin crear, sin commit) | (sin commit) | 522 pruebas backend (8 nuevas de llamadas) · app: pruebas escritas, sin correr |
| Integración de `main` en `DEV` | Lo que entró directo a `main` (visión desacoplada, llamada en vivo, revisión local, feedback por ejes) se junta con D35, D36 y D37. La migración de D37 pasa a ser la **028** (`main` ya tenía otra 023). La prueba de borrar la cuenta publica una versión que ofrece los alcances que acepta, porque solo cuentan los ofrecidos (D35) | `feature/integra-main-en-dev` | (ver git log) | 569/570 del backend y 22/30 de `motor-vision`: fallan solo las 9 que necesitan el `.onnx` E14 · semilla 11/11 · migraciones 020–028 dos veces sobre una BD nueva (2026-11-23) |
| Permiso de cámara y plazo del consentimiento | Sin `analisis_conducta_camara` (si la versión lo ofrece) no se guarda nada medido con la cámara (métricas, predicciones, llamadas; `/vision/predecir` responde 403), y retirarlo lo borra, también del reporte. Al eliminar la cuenta, el consentimiento queda sin IP y se borra a los 5 años (Ley 19.628/21.719, tarea diaria, `retencion_legal`); texto `v1.1` y Política 1.2.0 actualizados (D38, migración 029) | `feature/permiso-camara-y-plazo-consentimiento` | (ver git log) | 584/585 (falta el `.onnx`) · revisión adversarial · semilla con la 029 14/14 (2026-11-23) |
| `/pruebas/video` apagado | Las rutas de calibración de video (sin sesión) solo se montan con `HABILITAR_PRUEBAS_VIDEO=true`; por defecto 404 (D39) | `fix/pruebas-video-apagado` | (ver git log) | 586/587 (falta el `.onnx`) (2026-11-24) |

Bitácora detallada de cada fase (hallazgos, bugs corregidos, decisiones): [[PLAN_REFACTORIZACION]].

## Ramas y PR
- Las ramas están **apiladas**: `main` → fase-0 → … → fase-7 → documentacion-api. La más reciente contiene todo.
- Fases 0–7 están en GitHub (`yeranaika/BackAppEntrevistaV2`). PR: fases 0–4 contra `main`, luego 5→4, 6→5, 7→6
  (descripciones en `pull-requests/`). Se fusionan **en orden**; al fusionar una, cambiar la base de la siguiente a `main`.
- `refactor/documentacion-api` está en GitHub; su PR va contra `refactor/fase-7-feedback`.
- `refactor/mensajes-exito` va encima de `refactor/documentacion-api`.
- `gh` no tiene sesión en la máquina de desarrollo: los PR se crean desde el enlace *compare* de GitHub.

## Base de datos local
Migraciones aplicadas hasta **018** (con respaldo previo de cada una).

**2026-11-07:** las migraciones 014–021 se probaron en PostgreSQL 16.15 real (sin Docker): sobre el esquema del primer
commit (`21b065f`) se aplicaron dos veces seguidas sin errores, y el esquema resultante es idéntico al de
`src/DB/BasedeDatos.EntrevistaApp.sql` (comparado con `pg_dump --schema-only`). Prueba de humo por HTTP contra esa BD:
lotes reenviados, solapados y 6 envíos simultáneos del mismo lote sin duplicar; reporte sin emociones; lenguaje corporal
`null` sin video o sin evidencia (20/20).

> [!warning] Migración 022 pendiente en Railway
> `migrations/022_rubrica_snap_y_traza_editada.sql` (rama `fix/banco-preguntas`, D24) está aplicada en la BD local
> (PostgreSQL 16 portátil, con respaldo previo, 2026-11-07; el backend en 8093 inicia entrevistas y responde el 409 sin
> cancelar la anterior). En Railway **aún no**: aplicarla (con respaldo) **antes** de desplegar —sin `rubrica_snap`
> fallan los endpoints de entrevista y el reporte— y volver a correrla después. `src/DB/PROBAR_SEMILLA.ps1` (D25):
> 7/7 comprobaciones en verde (14 preguntas y 24 opciones tras tres corridas).

## Qué funciona hoy (resumen)
Registro/login (correo y Google), perfil y onboarding, consentimientos, recordatorios, premium (códigos y Google Play,
que solo activa la cuenta que pagó; el premium ve cuántos reportes con IA le quedan),
mercado laboral, banco de preguntas (con generación IA guiada por las ofertas de empleo y cobertura por cargo), nivelación, práctica (+ offline), entrevista (+ métricas de
video), reporte de feedback (freemium / IA premium), progreso por skill, costos de IA con el gasto del mes frente al
tope de generación, y todo el contrato que usa la app Android.

Ver también [[Pendientes y deuda tecnica]] · [[Decisiones]] · [[Riesgos]]
