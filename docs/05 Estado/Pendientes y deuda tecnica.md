---
tipo: estado
tags: [pendientes, deuda-tecnica, backlog]
actualizado: 2026-10-03
---

# Pendientes y deuda técnica (backend)

Ordenados por prioridad sugerida. Al tomar uno, anotarlo aquí con la rama; al terminarlo, moverlo a [[Estado del proyecto]].

## Alta
- [x] **Probar la colección de Postman contra el servidor** (2026-10-03): 78 de 78 requests OK; se corrigió el `area` de los ejemplos.
- [ ] **Límite por IP en Redis**: hoy vive en memoria de cada instancia; con varias instancias se puede superar.
- [ ] **Tope mensual de evaluaciones con IA por usuario** premium (hoy sin límite; cada reporte cuesta tokens).
- [ ] **Apagado ordenado**: un reporte o correo en curso se corta si el servidor se detiene (cola de trabajos o espera al cerrar).
- [ ] **CI (R12)**: GitHub Actions con `./gradlew test`, más Ktlint y Detekt.
- [ ] **Revisión de código** de las ramas antes de fusionar a `main`.

## Media
- [ ] **Logs estructurados con id de solicitud** (correlacionar error del cliente con el log).
- [ ] **IA para premium en práctica y nivelación** (hoy solo freemium; la interfaz `EvaluadorRespuesta` lo permite).
- [ ] **Resumen de nivelación con IA** para premium (hoy determinista).
- [ ] **Transcripción de audio (STT)** para preguntas de video: hoy un clip sin transcripción no se evalúa.
- [ ] Endpoint para que un admin vea costos de IA (tokens y USD por día) desde `reporte_entrevista` y `pregunta_generacion_ia`.
- [ ] `.env.example`: agregar `LIMITE_REGISTROS_POR_IP` y `LIMITE_RECUPERACIONES_POR_IP`.

## Baja
- [ ] Revisar dependencias sin uso (`poi-ooxml`, `bcrypt`, `google-api-client:2.+` con versión dinámica).
- [ ] Tablas del módulo de visión (`modelo_vision_ia`, `frame_entrenamiento`, `prediccion_vision`) sin uso: decidir si se mantienen.
- [ ] Unificar el filtro `--tests` de Gradle (hoy no encuentra las clases de prueba).

Relacionado: [[Riesgos]] · [[Decisiones]]
