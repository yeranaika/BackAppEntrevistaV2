---
tipo: estado
tags: [pendientes, deuda-tecnica, backlog]
actualizado: 2026-10-03
---

# Pendientes y deuda técnica (backend)

Ordenados por prioridad sugerida. Al tomar uno, anotarlo aquí con la rama; al terminarlo, moverlo a [[Estado del proyecto]].

## Alta
- [x] **Mensajes de éxito claros** (2026-10-03, rama `refactor/mensajes-exito`): todo éxito de escritura trae
  `mensaje`; las 5 respuestas que llegaban `{}` ahora responden `{"ok": true, "mensaje": "…"}`.
- [x] **Probar la colección de Postman contra el servidor** (2026-10-03): 78 de 78 requests OK; se corrigió el `area` de los ejemplos.
- [x] **Límite por IP en Redis** (2026-11-06, rama `feature/limite-ip-redis`, D18 y D19): contador compartido entre
  instancias con respaldo en memoria si Redis cae. Falta activar `CABECERA_IP_CLIENTE=X-Real-IP` en Railway tras la prueba de humo.
- [x] **Tope mensual de evaluaciones con IA por usuario** premium (2026-11-06, rama `feature/tope-mensual-ia`, D17):
  `LIMITE_EVALUACIONES_IA_MENSUAL` (30 por defecto); al llegar al tope el reporte sale con el motor freemium.
- [x] **Apagado ordenado** (2026-11-06, rama `feature/apagado-ordenado`, D20): espera con tope en ApplicationStopping,
  reporte cortado queda reintentable y rescate periódico de los que quedaron en `generando`. `railway.json` con 200 s de drenado.
- [x] **CI (R12)** (2026-11-06, rama `ci/github-actions`): GitHub Actions corre `./gradlew test` (JDK 21, H2, sin
  servicios externos) en cada push a `main` y en cada PR; si falla sube el reporte de pruebas. Local: 265/265.
- [ ] **Ktlint y Detekt en CI**: requieren configurar reglas compatibles con las convenciones (archivos en MAYÚSCULAS,
  nombres en español) antes de activarlos; con la configuración por defecto fallarían desde el primer día.
- [ ] **Revisión de código** de las ramas antes de fusionar a `main`.

## Media
- [ ] **Logs estructurados con id de solicitud** (correlacionar error del cliente con el log).
- [ ] **IA para premium en práctica y nivelación** (hoy solo freemium; la interfaz `EvaluadorRespuesta` lo permite).
- [ ] **Resumen de nivelación con IA** para premium (hoy determinista).
- [ ] **Transcripción de audio (STT)** para preguntas de video: hoy un clip sin transcripción no se evalúa.
- [ ] Endpoint para que un admin vea costos de IA (tokens y USD por día) desde `reporte_entrevista` y `pregunta_generacion_ia`.
- [x] `.env.example`: agregar `LIMITE_REGISTROS_POR_IP` y `LIMITE_RECUPERACIONES_POR_IP` (2026-11-06, con `CABECERA_IP_CLIENTE`).
- [ ] **Mostrar al usuario premium cuántas evaluaciones con IA le quedan** en el mes (campo opcional nuevo, coordinado con Android).
- [ ] **Descontar del tope las llamadas pagadas que responden mal** (hoy no descuentan cupo; requiere registrar sus tokens).
- [x] **IP del consentimiento** (2026-11-06, rama `feature/ip-consentimiento-y-limites`): `ip_origen` usa la misma IP
  que el límite por IP (`ipDelCliente()`, resuelta una vez por solicitud): sin DNS inverso y, con `CABECERA_IP_CLIENTE`,
  la del cliente y no la del proxy.
- [ ] **Límite por IP en `/auth/login` y `/auth/reset-password`**: **solo después de activar `CABECERA_IP_CLIENTE`**.
  Mientras todos lleguen con la IP del proxy, un límite por IP en el login sería un tope global: una sala de clases
  bloquearía el ingreso de todos. Hoy el login ya tiene bloqueo por correo (5 intentos) y el código de recuperación,
  tope de intentos por código.
- [ ] **Correo de recuperación sin reintento**: si el envío se corta por un apagado, no se reenvía solo (el usuario pide otro).
- [ ] **Entrevistas finalizadas hace más de 24 h sin reporte** (datos anteriores a la Fase 7 o caídas largas): no las
  rescata la tarea periódica; decidir un relleno único por SQL.

## Baja
- [ ] Revisar dependencias sin uso (`poi-ooxml`, `bcrypt`, `google-api-client:2.+` con versión dinámica).
- [ ] Tablas del módulo de visión (`modelo_vision_ia`, `frame_entrenamiento`, `prediccion_vision`) sin uso: decidir si se mantienen.
- [ ] Unificar el filtro `--tests` de Gradle (hoy no encuentra las clases de prueba).

Relacionado: [[Riesgos]] · [[Decisiones]]
