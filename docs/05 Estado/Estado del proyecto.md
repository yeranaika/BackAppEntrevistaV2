---
tipo: estado
tags: [estado, fases, ramas]
actualizado: 2026-10-03
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
| Docs | `docs/documentacion/` (API.md + Postman) y esta bóveda | `refactor/documentacion-api` | `ae49742` + esta bóveda | — |

Bitácora detallada de cada fase (hallazgos, bugs corregidos, decisiones): [[PLAN_REFACTORIZACION]].

## Ramas y PR
- Las ramas están **apiladas**: `main` → fase-0 → … → fase-7 → documentacion-api. La más reciente contiene todo.
- Fases 0–7 están en GitHub (`yeranaika/BackAppEntrevistaV2`). PR: fases 0–4 contra `main`, luego 5→4, 6→5, 7→6
  (descripciones en `pull-requests/`). Se fusionan **en orden**; al fusionar una, cambiar la base de la siguiente a `main`.
- `refactor/documentacion-api` está en GitHub; su PR va contra `refactor/fase-7-feedback`.
- `gh` no tiene sesión en la máquina de desarrollo: los PR se crean desde el enlace *compare* de GitHub.

## Base de datos local
Migraciones aplicadas hasta **018** (con respaldo previo de cada una).

## Qué funciona hoy (resumen)
Registro/login (correo y Google), perfil y onboarding, consentimientos, recordatorios, premium (códigos y Google Play),
mercado laboral, banco de preguntas (con generación IA), nivelación, práctica (+ offline), entrevista (+ métricas de
video), reporte de feedback (freemium / IA premium), progreso por skill, y todo el contrato que usa la app Android.

Ver también [[Pendientes y deuda tecnica]] · [[Decisiones]] · [[Riesgos]]
