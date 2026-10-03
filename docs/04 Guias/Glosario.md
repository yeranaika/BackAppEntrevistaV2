---
tipo: guia
tags: [glosario]
actualizado: 2026-10-03
---

# Glosario

| Término | Significado |
|---|---|
| **Cargo / cargo meta** | Puesto al que apunta el usuario (ej. *Android Developer*). Tabla `cargo`; el del usuario en `objetivo_carrera` |
| **Skill** | Habilidad técnica o blanda (ej. Kotlin, Comunicación). Tabla `skill` |
| **Requisito del cargo** | Skill que pide un cargo, con nivel requerido, peso y si es obligatoria (`cargo_skill`) |
| **Nivel** | `junior`, `semisenior`, `senior` (en la app `jr`, `mid`, `sr`) |
| **Categoría** | `tecnica` o `blanda` (de una pregunta o skill) |
| **Tipo de pregunta** | `opcion_multiple`, `abierta_texto`, `simulacion_video` |
| **Banco de preguntas** | Preguntas aprobadas por un admin (`pregunta`); las generadas por IA nacen *pendientes* |
| **Nivelación** | Test para conocer el nivel actual y las **brechas** contra el cargo (Flujo 1) |
| **Práctica** | Ronda de preguntas con feedback inmediato (Flujo 2) |
| **Entrevista / sesión** | Simulación pregunta a pregunta (Flujo 3). `sesion_entrevista` |
| **Pregunta servida / snapshot** | Copia de la pregunta tal como se mostró; no cambia si se edita el banco |
| **Reporte** | Feedback de una entrevista finalizada: puntajes, radar, fortalezas, mejoras, recomendaciones |
| **Radar** | Puntaje por skill del reporte (`reporte_skill_detalle`) |
| **Brecha** | Niveles que faltan en una skill para el nivel que pide el cargo |
| **Motor freemium** | Corrector determinista de respuestas abiertas, sin IA ni costo |
| **Premium** | Usuario con suscripción activa (Google Play o código); su reporte se evalúa con IA |
| **STAR** | Método para respuestas de comportamiento: Situación, Tarea, Acción, Resultado |
| **Contrato de Android** | Rutas y JSON que la app ya usa (`/api/prueba-practica`, `/auth/*`, `/billing/*`) y no se pueden cambiar |
| **`tipoPrueba`** | En la app: `ENT` entrevista, `PR` práctica técnica, `BL` práctica blanda, `NV` nivelación |
| **E2E** | Prueba de punta a punta contra el servidor y la BD reales (`PRUEBAS_E2E/`) |
| **Fase** | Etapa de la refactorización (0 a 7) → [[Estado del proyecto]] |

Relacionado: [[Flujos de negocio]] · [[Modelo de datos]]
