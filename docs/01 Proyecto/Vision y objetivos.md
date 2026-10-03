---
tipo: proyecto
tags: [producto, vision, objetivos]
fuente: DOCUMENTOS_INVESTIGACION/final.docx (informe de gestión, 2026)
actualizado: 2026-10-03
---

# Visión y objetivos

## El problema
Los procesos de selección exigen cada vez más: evalúan conocimientos técnicos **y** habilidades blandas
(comunicación, criterio, seguridad). La brecha de habilidades es una de las principales barreras para la
transformación de las empresas (Foro Económico Mundial, 2025) y el 73 % de los profesionales de talento cree que la IA
cambiará la forma de contratar (LinkedIn Talent Solutions, 2025). Los candidatos — sobre todo estudiantes y egresados —
llegan a las entrevistas sin práctica realista ni retroalimentación.

## La propuesta
**Entrevistas IA**: una app móvil para **practicar entrevistas laborales** en un entorno seguro, basada en
*práctica deliberada*: el usuario identifica brechas, se equivoca sin consecuencias y mejora antes de la entrevista real.

| Capacidad | Para qué |
|---|---|
| Onboarding con área, cargo meta y nivel | Personalizar todo lo demás (no preguntas genéricas) |
| Test de nivelación | Conocer el punto de partida y las brechas contra el cargo |
| Práctica por skill o por cargo | Aprender con feedback inmediato pregunta a pregunta |
| Simulación de entrevista técnica y conductual | El núcleo: practicar como en una entrevista real |
| Feedback estructurado (fortalezas, mejoras, recomendaciones) | El valor principal: saber qué mejorar |
| Historial y progreso por skill | Ver la evolución y repasar (también offline en la app) |
| Mercado laboral (cargos y skills en tendencia) | Que lo que se practica responda a lo que piden las empresas |

## Objetivo general
Desarrollar y validar un **prototipo funcional** de aplicación móvil basada en IA que simule entrevistas laborales
técnicas y conductuales, entregue retroalimentación personalizada, registre el progreso del usuario y lo prepare
para procesos de selección.

## Objetivos específicos (SMART, resumidos)
1. **Flujo principal**: registro, login, onboarding (área, cargo meta, nivel). → [[Flujos de negocio]]
2. **Tests de nivelación y aprendizaje**: medir el nivel inicial con preguntas de alternativas y abiertas, guardar el avance.
3. **Entrevistas simuladas** técnicas y de habilidades blandas según el perfil.
4. **Feedback con IA**: fortalezas, aspectos por mejorar y recomendaciones, sin entrenar un modelo propio.
5. **Historial y repaso offline**: revisar sesiones, resultados y feedback anteriores.
6. **Validación** con pruebas funcionales y de usabilidad (usuarios piloto).

## Metas medibles que afectan al backend
- **Latencia del feedback ≤ 5 s** en el 95 % de los casos (con conexión estable).
- **≥ 95 %** de las ejecuciones principales sin errores críticos.
- Feedback **sin lenguaje discriminatorio ni sesgado**, claro y orientado al aprendizaje.

## Qué significa "terminado" para el backend
Que la app pueda completar, contra esta API, el flujo **registro → onboarding → nivelación → práctica → entrevista
→ feedback → historial**, con datos protegidos y errores controlados. Detalle en [[Requisitos y criterios de aceptacion]].

Relacionado: [[Contexto del proyecto]] · [[Alcance y limites]] · [[Usuarios y modelo de negocio]]
