---
tipo: estado
tags: [estado, fases, ramas]
actualizado: 2026-11-07
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
| Semilla re-ejecutable | Preguntas y opciones de la semilla solo se insertan si no existen; `src/DB/PROBAR_SEMILLA.ps1` (aún sin ejecutar) (D25) | `fix/banco-preguntas` | (ver git log) | 360 pruebas · 4 mutaciones detectadas · revisión adversarial |
| IP del consentimiento | `ip_origen` con la IP real del cliente (la misma que el límite por IP), sin DNS inverso | `feature/ip-consentimiento-y-limites` | (ver git log) | 325 pruebas · 2 mutaciones detectadas |
| Límite por IP en Redis | Contador compartido entre instancias (D18), IP de la conexión o cabecera validada (D19), respaldo en memoria | `feature/limite-ip-redis` | (ver git log) | 289 pruebas · 12 mutaciones detectadas · revisión adversarial |

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

> [!warning] Migración 022 pendiente
> `migrations/022_rubrica_snap_y_traza_editada.sql` (rama `fix/banco-preguntas`, D24) **aún no se aplica** ni en la BD
> local ni en Railway. Aplicarla (con respaldo) **antes** de desplegar: sin `rubrica_snap` fallan los endpoints de
> entrevista y el reporte. `src/DB/PROBAR_SEMILLA.ps1` (D25) tampoco se ha ejecutado todavía.

## Qué funciona hoy (resumen)
Registro/login (correo y Google), perfil y onboarding, consentimientos, recordatorios, premium (códigos y Google Play),
mercado laboral, banco de preguntas (con generación IA), nivelación, práctica (+ offline), entrevista (+ métricas de
video), reporte de feedback (freemium / IA premium), progreso por skill, y todo el contrato que usa la app Android.

Ver también [[Pendientes y deuda tecnica]] · [[Decisiones]] · [[Riesgos]]
