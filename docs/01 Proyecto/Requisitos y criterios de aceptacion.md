---
tipo: proyecto
tags: [requisitos, aceptacion, trazabilidad]
fuente: DOCUMENTOS_INVESTIGACION/final.docx (2. Requisitos, 3. Criterios de aceptación)
actualizado: 2026-10-03
---

# Requisitos y criterios de aceptación

Trazabilidad entre lo que pide el informe del proyecto y lo que hoy hace el backend.
Estado: ✅ cubierto · 🟡 parcial / distinto a lo planificado · ⬜ no corresponde al backend o pendiente.

## Requisitos (R01–R14)
| Código | Requisito (resumen) | Estado en el backend |
|---|---|---|
| R01 | Registro e inicio de sesión seguro (Firebase Auth + Google) bajo Ley 19.628 | 🟡 Registro/login propios con **JWT + Argon2id** y login con **Google** (idToken y OAuth web). No se usa Firebase → [[Decisiones]] |
| R02 | Onboarding: área, cargo meta, nivel, tipo de entrevista | ✅ `/perfil/objetivo`, `/onboarding`, `/me/objetivo` |
| R03 | Tests de nivelación con resultados y recomendaciones | ✅ `/api/v1/nivelacion`: nivel global, por skill y brechas contra el cargo |
| R04 | Simulaciones de entrevista con preguntas adaptadas (Gemini) | 🟡 `/api/v1/entrevistas`: preguntas del **banco** según cargo/skills/nivel; la IA **genera** preguntas para el banco (admin) |
| R05 | Feedback automatizado ≤ 5 s | ✅ Inmediato en práctica/nivelación (motor freemium, ms). Entrevista: reporte en segundo plano (freemium o IA premium) |
| R06 | Historial offline (SQLDelight) | ⬜ Es de la app. El backend expone historial y **sincroniza intentos offline** (`/api/v1/sync/attempts`) |
| R07 | Backend Ktor + Kotlin/JVM + PostgreSQL | ✅ |
| R08 | HTTPS/TLS, validación de tokens, control de acceso | ✅ JWT, roles, límites, 404 en recursos ajenos. TLS lo pone el despliegue → [[Seguridad]] |
| R09 | UI Android Jetpack Compose, WCAG 2.1 AA | ⬜ App |
| R10 | Firebase Analytics / Crashlytics | ⬜ App |
| R11 | Arquitectura lista para freemium / premium / institucional | 🟡 Freemium/premium ✅ (códigos, Google Play, IA solo premium). Institucional: roles user/admin |
| R12 | CI/CD con GitHub Actions, Ktlint, Detekt | ⬜ Pendiente → [[Pendientes y deuda tecnica]] |
| R13 | Validación con usuarios piloto | ⬜ Proceso del proyecto |
| R14 | Revisión de criterios por especialistas | 🟡 Rúbricas por pregunta en el banco; revisión humana de preguntas generadas por IA (aprobar/rechazar) |

## Criterios de aceptación (C01–C15) relevantes al backend
| Código | Criterio | Cómo se verifica |
|---|---|---|
| C01 | ≥ 90 % de módulos operativos | E2E de las fases 1–7 en verde ([[Pruebas]]) |
| C02 | Flujo principal sin errores críticos | E2E de cada fase + colección Postman |
| C04 | Preguntas coherentes con área, cargo y nivel | Selector de preguntas (cargo → skills del cargo → generales) |
| C05 | Feedback con fortalezas, mejoras y recomendación | Reporte de entrevista (`/api/v1/entrevistas/{id}/reporte`) |
| C06 | 95 % del feedback ≤ 5 s | Práctica/nivelación: corrección síncrona. Entrevista: asíncrona con estado `generando` |
| C07 | Historial con fecha, tipo, respuestas, feedback y progreso | `/api/prueba-practica/intentos`, `/api/v1/pruebas/historial`, `/api/v1/me/progreso` |
| C09 | Autenticación segura y control de acceso | [[Seguridad]] |
| C12 | Backend conectado a PostgreSQL, almacena y sincroniza | ✅ |
| C13 | Calidad de código (Ktlint/Detekt o revisión) | Revisión + pruebas; linters pendientes |
| C15 | Feedback sin sesgos ni lenguaje discriminatorio | Instrucciones del evaluador IA; motor freemium con textos fijos revisados |

Relacionado: [[Vision y objetivos]] · [[Alcance y limites]] · [[Flujos de negocio]]
