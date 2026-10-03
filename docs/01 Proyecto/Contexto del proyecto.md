---
tipo: proyecto
tags: [contexto, equipo]
fuente: DOCUMENTOS_INVESTIGACION/final.docx
actualizado: 2026-10-03
---

# Contexto del proyecto

## Origen
Proyecto de título / asignatura de Gestión de Proyectos TI (2026). Se entrega como **prototipo funcional académico**,
con plazo de **5 meses** y presupuesto de creación estimado en **$3.000.000 CLP**. Metodología **Scrum** adaptada a un
equipo de tres personas, con Carta Gantt y revisiones semanales.

## Equipo
| Persona | Rol | Foco |
|---|---|---|
| Sebastián Lara | Gerente de proyecto | Plazos, presupuesto, alcance (evitar *scope creep*) |
| **Víctor Molina** | Responsable técnico | **Arquitectura, backend, base de datos, IA, pagos** |
| Nicolás Vicencio | Responsable funcional | Requisitos, flujos de usuario, documentación |
| Armin Brun Rüth | Docente evaluador / patrocinador | Rigor metodológico, aprobación de etapas |

## Piezas del sistema
| Pieza | Repositorio / ubicación | Estado |
|---|---|---|
| **Backend (este repo)** | `BackAppEntrevistaV2` | Refactorizado en fases 0–7 (ver [[Estado del proyecto]]) |
| App Android | `EntrevistaAPPAndroid` (Kotlin + Jetpack Compose) | En uso; consume el backend por `/auth`, `/me`, `/api/prueba-practica`… |
| Backend antiguo | `EntrevistaAPPBack` | **Obsoleto, ignorar** (solo referencia histórica) |
| Investigación | `proyecto_titulo/DOCUMENTOS_INVESTIGACION/` | Informes de gestión, PMBOK, estudio de mercado |
| Bóveda de producto previa | `proyecto_titulo/Knowledge-Base/` | Notas iniciales de producto (anteriores a las decisiones técnicas) |

> [!warning] Alcance de este repositorio
> Este trabajo es **solo backend**. La app Android se lee **únicamente como referencia del contrato JSON** que el
> backend no debe romper (ver [[Guia para agentes]]).

## Socios y servicios externos previstos
Infraestructura cloud, proveedor de IA, instituciones educativas (pruebas piloto), especialistas en empleabilidad
(validan rúbricas), pasarela de pagos (fase posterior), Google Play (distribución).
El documento original proponía **Firebase + Gemini**; la implementación actual usa backend propio (Ktor + PostgreSQL)
y **OpenAI / Anthropic**. Ver por qué en [[Decisiones]].

## Historia técnica resumida
1. Versión inicial con código mezclado (rutas, repositorios y lógica juntos, contraseñas en claro, endpoints inseguros).
2. **Refactorización en 8 fases** (oct-2026): cimientos → login → usuario → preguntas → integraciones → entrevista →
   prueba → feedback. Cada fase con pruebas unitarias, E2E contra Postgres real y PR. Detalle en [[PLAN_REFACTORIZACION]].

Relacionado: [[Vision y objetivos]] · [[Alcance y limites]] · [[Riesgos]]
