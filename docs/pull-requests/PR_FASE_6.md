# Fase 6: práctica, nivelación y sincronización offline + contrato de la app Android

## Resumen
Implementa el Flujo 1 (nivelación) y el Flujo 2 (práctica) en capas CONTROLADORES → SERVICIOS → MODELOS. Rama apilada sobre `refactor/fase-5-entrevista`: este PR muestra solo la Fase 6.

## Qué incluye
- **Práctica** `/api/v1/practicas`: por skill o por cargo; modos opción múltiple / abierta / mixto; feedback inmediato por respuesta (opción correcta y su explicación, o motor freemium en las abiertas); una práctica en curso a la vez; puntaje acumulado por skill.
- **Nivelación** `/api/v1/nivelacion`: test del admin para el cargo o 3 técnicas por nivel desde el banco; nivel global y por skill (`CalculadoraNivel`); brechas contra `cargo_skill` con prioridad; `nivel_skill_usuario`; se rinde una sola vez, también con envíos simultáneos.
- **Tests de nivelación del admin** `/api/v1/admin/tests-nivelacion` (solo preguntas aprobadas y escritas).
- **App Android**: `/api/prueba-practica` acepta `PR`, `BL` y `NV` además de `ENT` (acepta `respuestaAbierta` y `respuestaTexto`); la nivelación devuelve `nivelDetectado`; `GET /intentos` devuelve el historial de todas las pruebas.
- `EvaluadorRespuesta` (interfaz, intercambiable por LLM en premium) + `EvaluadorRespuestaFreemium` + `CorrectorRespuestas`; `SelectorPreguntas` y `ResolutorContextoPrueba` compartidos con la entrevista.
- Se elimina el código antiguo (`routes/`, `data/`, `services/`): `src/main/kotlin` queda solo con las capas MVC.

## Bugs corregidos
- `/api/v1/sync/attempts` respondía "sincronizado" sin guardar nada (los intentos offline se perdían). Ahora guarda, es idempotente por `localAttemptId` y vuelve a corregir en el servidor las preguntas del banco.
- `/api/v1/practice/evaluate-freemium` era público; ahora exige sesión.
- El motor freemium regalaba 40 puntos cuando la pregunta no tenía palabras clave.
- `skill_tendencia.nivel_requerido` tenía como valor por defecto `'intermedio'`, que su propio CHECK rechaza.

## Decisiones
- `resultado_nivelacion.onboarding_id` pasa a opcional y se guarda `cargo_id`: V2 usa `objetivo_carrera`, no `onboarding_usuario`. Por eso no se marca `nivel_verificado`.
- La nivelación no cambia el nivel del perfil; se informa como "nivel sugerido". La práctica suma puntaje a la skill pero no cambia su nivel.
- Umbral de 60 puntos: una abierta cuenta como correcta y un nivel se da por logrado.

## Despliegue
Aplicar `migrations/017_practica_nivelacion.sql` (idempotente). Verificada sobre la BD local y sobre una BD nueva junto a 014–016.

## Pruebas
- `./gradlew clean test`: 243/243 (práctica 12, nivelación 10, contrato HTTP 9 con los DTO de la app, evaluador 5), estable en corridas repetidas.
- E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_6_PRUEBA.ps1`: 39/39 contra Postgres real (incluye 4 envíos simultáneos de una nivelación → 1 solo resultado).
- Regresión E2E fases 1–5: 34 + 48 + 38 + 71 + 50, sin datos de prueba remanentes.
- Mutación: rendir dos veces una nivelación, duplicar un intento offline o revelar la corrección antes de terminar hacen fallar las pruebas.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
