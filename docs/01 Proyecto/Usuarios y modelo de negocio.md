---
tipo: proyecto
tags: [usuarios, negocio, freemium]
actualizado: 2026-10-03
---

# Usuarios y modelo de negocio

## Usuarios
| Perfil | Qué necesita | Rol en el sistema |
|---|---|---|
| Estudiante / egresado | Primer empleo: practicar entrevistas técnicas y de comportamiento | `user` |
| Profesional en transición | Cambiar de cargo o subir de nivel (junior → semisenior → senior) | `user` |
| Administrador de contenido | Mantener el banco de preguntas, cargos, tests de nivelación, textos legales, códigos premium | `admin` |
| Institución (futuro) | Licencias para sus estudiantes (códigos `INST`) | — (sin panel propio, L08) |

Niveles de experiencia en todo el sistema: `junior | semisenior | senior` (la app también envía `jr | mid | sr`).

## Freemium / premium
| | Freemium | Premium |
|---|---|---|
| Registro, onboarding, nivelación, práctica, entrevista | ✅ | ✅ |
| Corrección de respuestas abiertas | Motor freemium (determinista, sin costo) | **IA** en el reporte de la entrevista |
| Reporte de feedback | ✅ (reglas) | ✅ (IA: observaciones, fortalezas y resumen redactados) |

Cómo se obtiene premium (ver [[Google Play Billing]]):
- **Google Play**: la app envía el `purchase_token`; el backend lo verifica (o lo simula en desarrollo).
- **Códigos**: un admin crea códigos `PROM` (promoción), `INST` (institución) o `GOOG`, con días y usos máximos.

> [!note] Costos
> Cada evaluación con IA cuesta tokens. Por eso la IA está restringida a premium, se usa **una llamada por entrevista**
> y se registran modelo, tokens y costo en `reporte_entrevista`. Ver [[LLM OpenAI y Anthropic]].

Relacionado: [[Vision y objetivos]] · [[Flujos de negocio]]
