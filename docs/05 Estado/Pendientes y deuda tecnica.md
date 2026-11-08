---
tipo: estado
tags: [pendientes, deuda-tecnica, backlog]
actualizado: 2026-11-07
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
- [ ] **Aplicar la migración 022** (`rubrica_snap`, D24, rama `fix/banco-preguntas`) en la BD local y en Railway, con
  respaldo, **antes** de desplegar esa rama (sin la columna fallan los endpoints de entrevista y el reporte), y volver
  a correrla una vez desplegada.
- [ ] **Ejecutar `src/DB/PROBAR_SEMILLA.ps1`** (D25): la semilla re-ejecutable está escrita pero el script aún no se
  ha corrido contra un PostgreSQL real.

## Media
- [ ] **Logs estructurados con id de solicitud** (correlacionar error del cliente con el log).
- [ ] **IA para premium en práctica y nivelación** (hoy solo freemium; la interfaz `EvaluadorRespuesta` lo permite).
- [ ] **Resumen de nivelación con IA** para premium (hoy determinista).
- [ ] **Transcripción de audio (STT)** para preguntas de video: hoy un clip sin transcripción no se evalúa.
- [x] Endpoint para que un admin vea costos de IA (2026-11-06, rama `feature/costos-ia-admin`): `GET /api/v1/admin/costos-ia`
  por día (UTC) y por modelo, con el costo estimado de las llamadas fallidas. Falta mostrarlo en el panel web.
- [x] `.env.example`: agregar `LIMITE_REGISTROS_POR_IP` y `LIMITE_RECUPERACIONES_POR_IP` (2026-11-06, con `CABECERA_IP_CLIENTE`).
- [ ] **Mostrar al usuario premium cuántas evaluaciones con IA le quedan** en el mes (campo opcional nuevo, coordinado con Android).
- [ ] **Descontar del tope las llamadas pagadas que responden mal**: desde 2026-11-06 sus tokens y costo quedan en el
  reporte (modo freemium) y aparecen en `GET /api/v1/admin/costos-ia` como `fallidas`; falta decidir si descuentan cupo.
- [ ] **Llamadas pagadas que no llegan a guardarse**: si el LLM ya respondió pero el intento se corta (apagado, rescate,
  error posterior), su costo no queda registrado. Requiere una tabla de uso de IA por llamada.
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
- [x] **Tope de gasto en la generación de preguntas con IA** (2026-11-07, rama `fix/banco-preguntas`, D23):
  `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` (5 USD/mes UTC, global) → `429 tope_generacion_ia_alcanzado`, y límite por IP
  `LIMITE_GENERACIONES_IA_POR_IP`. Quedan abiertos los cinco puntos siguientes.
- [ ] **Tope de generación por admin**: hoy es global porque `pregunta_generacion_ia` no guarda quién pidió la
  generación; requiere una columna `solicitado_por` (migración) y decidir el reparto.
- [ ] **Reserva previa del tope de generación**: sin reserva, dos generaciones simultáneas pueden pasar el tope en lo
  que cueste una de ellas (el tope de reportes, D17, sí reserva).
- [ ] **Cortocircuito abierto auditado como fallida**: cuando la política del LLM tiene el cortocircuito abierto
  (`*_no_disponible`), la generación se registra en `pregunta_generacion_ia` como fallida aunque no hubo llamada al
  proveedor (sin tokens, así que no suma gasto, pero ensucia la auditoría y el conteo de `fallidas`).
- [ ] **Panel admin: gasto del mes frente al tope** de generación (hoy solo se ve el 429 al llegar).
- [x] **Panel admin: texto de `GenerarPreguntas.jsx`** alineado con D23 (rama `fix/textos-costos-ia` del panel, sin
  push): fallidas con tokens, `provider_timeout` y el 429 del tope.
- [ ] **Estimar el costo de un `provider_timeout`** para el tope (hoy cuenta 0: no hay tokens, aunque el proveedor
  pudo cobrar la llamada).
- [ ] **Limpiar las BD ya contaminadas por la semilla vieja** (preguntas y opciones duplicadas, D25): decidir un SQL
  único, **con respaldo**, que conserve la copia a la que apuntan los snapshots e historial.

## Baja
- [ ] Revisar dependencias sin uso (`poi-ooxml`, `bcrypt`, `google-api-client:2.+` con versión dinámica).
- [ ] Tablas del módulo de visión (`modelo_vision_ia`, `frame_entrenamiento`, `prediccion_vision`) sin uso: decidir si se mantienen.
- [ ] Unificar el filtro `--tests` de Gradle (hoy no encuentra las clases de prueba).

Relacionado: [[Riesgos]] · [[Decisiones]]
