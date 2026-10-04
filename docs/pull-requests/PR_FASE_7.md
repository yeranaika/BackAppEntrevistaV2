# Fase 7: reporte de feedback de la entrevista y progreso por skill

## Resumen
Implementa el feedback de la simulación de entrevista: al finalizarla se genera en segundo plano un reporte con puntajes, radar por skill y feedback por respuesta. Rama apilada sobre `refactor/fase-6-prueba`: este PR muestra solo la Fase 7. Con esta fase quedan completas las fases 0 a 7.

## Qué incluye
- **`ServicioReporteEntrevista`** (implementa `ProcesadorEntrevistaFinalizada`): puntajes técnico, blando y de lenguaje corporal (promedio de las métricas de video); global con pesos 50/30/20 que se reparten si algo no se midió; radar por skill (`reporte_skill_detalle`); fortalezas, áreas de mejora, skills del cargo a reforzar (alta si es obligatoria), resumen y la corrección de cada respuesta abierta.
- **Evaluación de respuestas abiertas** (`EvaluadorEntrevista`): motor freemium para todos; IA para premium cuando hay `OPENAI_API_KEY` o `ANTHROPIC_API_KEY`. Una sola llamada por entrevista, JSON validado y las respuestas del candidato como datos en el mensaje de usuario. Si la IA falla o responde algo inválido se usa el freemium: el reporte siempre sale.
- **Estado** `generando → listo | error`: el error guarda solo un código y el usuario ve un mensaje claro; reintento manual con tope de 3 intentos. La toma del reporte es una actualización condicional: dos generaciones de la misma entrevista nunca corren a la vez (ni pagan dos veces la IA).
- **Endpoints**: `GET /api/v1/entrevistas/{id}/reporte`, `POST /api/v1/entrevistas/{id}/reporte/reintentar` (202), `GET /api/v1/reportes`, `GET /api/v1/me/progreso`.
- Métricas de video resumidas con `AVG` en la BD (una entrevista puede tener miles de filas).
- Costo del LLM centralizado en `costoLlmUsd` (lo usan la generación de preguntas y la evaluación).

## Decisiones
- La entrevista suma puntaje a cada skill en `nivel_skill_usuario` pero no fija su nivel (eso lo decide la nivelación).
- Un clip de video sin transcripción no se puede evaluar: no cuenta en el puntaje y se avisa en las áreas de mejora.
- Modelo, tokens y costo del LLM quedan en la BD y no se exponen al usuario.
- Revisar que el EULA cubra el envío de las respuestas de usuarios premium a OpenAI / Anthropic.
- Pendiente en la app Android: no tiene pantalla de reporte; puede usar `GET /api/v1/entrevistas/{pruebaId}/reporte`.

## Despliegue
Aplicar `migrations/018_reporte_entrevista.sql` (idempotente): modo de evaluación, modelo, tokens, costo e intentos del reporte. Verificada sobre la BD local y sobre una BD nueva junto a 014–017.

## Pruebas
- `./gradlew clean test`: 260/260 (reporte 13, contrato HTTP 4), estables en 5 corridas seguidas.
- E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_7_FEEDBACK.ps1`: 23/23 contra Postgres real con el motor freemium (sin costo); `-ConIa` incluye una evaluación real con el LLM.
- Regresión E2E fases 1–6: 34 + 48 + 38 + 71 + 50 + 39, sin datos de prueba remanentes.
- Mutación: generar dos veces un reporte, usar la IA sin premium o mostrar el código interno del error hacen fallar las pruebas.
- Corrige una prueba de la Fase 6 que fallaba al azar (~25 %) cuando la primera pregunta servida era abierta.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
