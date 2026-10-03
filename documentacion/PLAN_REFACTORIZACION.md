# Plan de refactorización — BackAppEntrevistaV2

Fecha: 2026-10-03 · Rama base: `feature/ai-question-generator` · Línea base: 56 pruebas en verde.

Referencias:
- Estándar del equipo: `.claude/skills/programacion-mvc`, `manejo-errores`, `arquitectura-software`.
- Modelo de datos objetivo: `DOCUMENTOS_INVESTIGACION/ESTRUCTURA_TEXTO_BASE_DATOS.md` y `src/DB/BasedeDatos.EntrevistaApp.sql`.

---

## Reglas que aplican a TODAS las fases

1. **Flujo de capas:** `CONTROLADOR → SERVICIO → REPOSITORIO (interfaz) → TABLA`. Ninguna ruta toca `transaction {}` ni tablas Exposed.
2. **Estructura de carpetas** (estándar del equipo):
   ```
   src/main/kotlin/
   ├── CONTROLADORES/   CONTROLADOR_LOGIN.kt, CONTROLADOR_USUARIO.kt, ...
   ├── SERVICIOS/       SERVICIO_LOGIN.kt, ...
   ├── MODELOS/         MODELO_USUARIO.kt (entidades), TABLA_USUARIO.kt (Exposed), REPOSITORIO_USUARIO.kt (interfaz + impl)
   ├── ESQUEMAS/        ESQUEMA_LOGIN.kt (DTO de request/response)
   ├── ERRORES/         ERRORES_APLICACION.kt
   ├── CONFIGURACION/   CONFIGURACION_GENERAL.kt, CONTENEDOR_DEPENDENCIAS.kt
   ├── INTEGRACIONES/   clientes de APIs externas (LLM, JSearch, Google, SMTP, Redis)
   └── UTILIDADES/
   ```
3. **Errores:** los servicios lanzan excepciones de dominio (`ErrorValidacion`, `ErrorNoEncontrado`, `ErrorNoAutorizado`, `ErrorConflicto`, `ErrorServicioExterno`). Un único `StatusPages` las traduce a `{"error": "codigo", "mensaje": "texto"}`. Se elimina `catch (Throwable)` y `mapOf("error"...)` de las rutas.
4. **Dependencias:** se construyen una sola vez en `CONTENEDOR_DEPENDENCIAS` y se inyectan por constructor. Se elimina `AuthDeps` y los `Repository()` sueltos.
5. **Contrato con Android:** la app usa hoy solo `auth/login`, `auth/register`, `auth/google`, `auth/refresh`, `GET/PUT me`, `GET/PUT me/perfil`. Esas rutas y sus JSON de éxito **no cambian**. El resto se puede reordenar.
6. **Cada fase termina** con: compila, pruebas en verde, pruebas nuevas del servicio con dobles de las interfaces, colección Postman actualizada, un commit por fase.

---

## Fase 0 — Cimientos y seguridad urgente ✅ (rama `refactor/fase-0`)
Prerrequisito de todo lo demás. **Estado:** completada salvo el punto de esquema de BD (ver abajo). 68 pruebas en verde y prueba de humo contra Postgres real.

Hecho:
- `/admin/market/*` protegido con `soloAdmin` (`MIDDLEWARES/MIDDLEWARE_SOLO_ADMIN.kt`): 401 sin token, 403 sin rol admin.
- Eliminados `/auth/request-reset`, `/auth/confirm-reset`, `AuthDeps`, `RecoveryCodeRepository/Table` y DTOs duplicados.
- `verifyPassword` ya no acepta texto plano ni recorta espacios; todo hash nuevo es Argon2id (BCrypt solo se verifica).
- `ERRORES/`, `ESQUEMAS/ESQUEMA_RESPUESTA_COMUN.kt`, `CONFIGURACION/` (configuración única, constantes, StatusPages, CORS, serialización, monitoreo, base de datos con pool Hikari) y `CONTENEDOR_DEPENDENCIAS`.
- `package com.example` eliminado; BOM y tildes rotas corregidas.

Pendiente:
- Retirar `createMissingTablesAndColumns`: al arrancar, Exposed reporta índices de `usuario`, `consentimiento` y `perfil_usuario` que no están mapeados. Hay que alinear las tablas Exposed con `src/DB/*.sql` antes de quitarlo.
- En Windows, tras cambiar mayúsculas de carpetas, correr `gradlew clean` (la carpeta `build/` vieja guarda el nombre en minúscula).

Tareas originales:

| Tarea | Detalle |
|---|---|
| Cerrar `/admin/market/*` | Hoy **no exige token**: cualquiera puede crear cargos y lanzar `generate-all` (consume cuota de API externa). Proteger con JWT + rol admin. |
| Eliminar `/auth/request-reset` y `/auth/confirm-reset` | Devuelven el código de recuperación en la respuesta. |
| Quitar fallback texto plano | `verifyPassword` acepta `pwd == hash`. Quitar también el `trim()` de la contraseña. |
| Un solo algoritmo de hash | Argon2id para todo (BCrypt solo para verificar hashes antiguos). |
| No exponer `e.message` | Rutas de market devuelven el mensaje interno de la excepción. |
| `ERRORES/` + `StatusPages` | Excepciones de dominio y formato único de error. |
| `CONFIGURACION/` | Un solo `Settings` cargado una vez; mover a él Redis, SMTP, OpenAI, Anthropic, JSearch (hoy se leen aparte en `Application.kt`). Constantes: TTL access 15 min, TTL refresh 15 días, largo mínimo contraseña 8. |
| `CONTENEDOR_DEPENDENCIAS` | Reemplaza `AuthDeps` y la creación de repos en `Routing.kt`. |
| Base de datos | Quitar `Thread.sleep` del arranque; decidir fuente única del esquema (SQL de `src/DB` + `migrations/`) y retirar `createMissingTablesAndColumns`. |
| Limpieza | Quitar BOM, borrar `RecoveryCodeRepository`/`RecoveryCodeTable` (código muerto), unificar `ErrorRes`/`OkRes`, quitar `package com.example`. |

---

## Fase 1 — Login ✅ (rama `refactor/fase-1-login`)
**Estado:** completada. 85 pruebas en verde y prueba de humo contra Postgres real.

Hecho:
- `CONTROLADORES/CONTROLADOR_LOGIN` → `SERVICIOS/SERVICIO_LOGIN` → `SERVICIOS/SERVICIO_TOKEN` → `MODELOS/REPOSITORIO_REFRESH_TOKEN`, `MODELOS/REPOSITORIO_CUENTA_OAUTH`, `MODELOS/LECTOR_USUARIO_SESION` (interfaces + implementación Exposed).
- `INTEGRACIONES/CLIENTE_GOOGLE_IDENTIDAD` detrás de `VerificadorIdentidadGoogle`; distingue token inválido (401) de Google caído (503).
- `UTILIDADES/`: contraseñas, JWT del request y transacciones en el pool de IO.
- Eliminados `routes/auth/{Refresh,Logout,RefreshSupport,authRoutes}`, `security/{Jwt,RefreshTokens}`, `AuthCtx` y la dependencia `ktor-client-logging-jvm:0.55.0`.

Corregido:
- El refresh perdía el rol admin; ahora relee rol y estado del usuario en cada renovación.
- Dos refresh simultáneos con el mismo token podían ganar ambos; la revocación ahora es atómica.
- Reutilizar un refresh ya rotado revoca todas las sesiones del usuario (detección de robo).
- Login con Google emitía siempre rol `user` y dejaba entrar a cuentas inactivas.
- El login revelaba "cuenta inactiva" sin contraseña correcta y respondía más rápido si el correo no existía.

Tareas originales:
**Hoy:** repartido entre `AuthController`, `RefreshRoutes`, `LogoutRoutes`, `RefreshSupport`, `RefreshTokens.kt` (en la raíz), `security/Jwt.kt`, `AuthMiddleware`.

| Endpoint | Se mantiene |
|---|---|
| `POST /auth/login` · `POST /auth/google` · `GET /auth/google/callback` | ✅ (Android) |
| `POST /auth/refresh` · `POST /auth/logout` | ✅ |

Resultado:
- `CONTROLADOR_LOGIN` (delgado) → `SERVICIO_LOGIN` (credenciales, Google, emisión de tokens) → `SERVICIO_TOKEN` (access + refresh con rotación) → `REPOSITORIO_REFRESH_TOKEN`, `REPOSITORIO_OAUTH`.
- `INTEGRACIONES/CLIENTE_GOOGLE_IDENTIDAD` detrás de una interfaz (testeable sin red).
- El rol (`admin`) se firma también al refrescar (hoy se pierde en `/refresh`).
- Pruebas: login ok, credenciales malas, usuario inactivo, refresh rotado, refresh revocado, Google con correo no verificado.
- Mover `security/` y `controllers/AuthController` a la estructura nueva; `configurarSeguridad` ya vive en `MIDDLEWARES/`.

## Fase 2 — Creación de usuario ✅ (rama `refactor/fase-2-usuario`)
**Estado:** completada para cuenta, perfil, objetivo/onboarding, contraseñas y administración de usuarios.
128 pruebas en verde + E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_2_USUARIO.ps1` (48/48) contra Postgres real.
Requiere aplicar `migrations/014_password_reset_intentos.sql`.

Hecho:
- `CONTROLADOR_USUARIO`, `CONTROLADOR_ONBOARDING`, `CONTROLADOR_CONTRASENA`, `CONTROLADOR_ADMIN_USUARIO` → `SERVICIO_USUARIO`, `SERVICIO_ONBOARDING`, `SERVICIO_CONTRASENA`, `SERVICIO_ADMIN_USUARIO` → repositorios con interfaz en `MODELOS/`.
- `VISTAS/VISTA_USUARIO`: traducción modelo → JSON (incluye nivel `junior/semisenior/senior` ↔ `jr/mid/sr`).
- `INTEGRACIONES/CLIENTE_CORREO` detrás de `EnviadorCorreo` (envío en segundo plano, HTML escapado).
- Validaciones únicas en `UTILIDADES/UTILIDAD_VALIDACION_USUARIO` (registro y actualización usan las mismas reglas).
- Eliminados `controllers/`, `models/`, `services/AuthService`, `routes/{me,onboarding,auth,admin usuarios}` y los repositorios antiguos de usuario.

Corregido (verificado contra la BD real):
- **El registro desde Android fallaba siempre**: enviaba `nivelExperiencia: ""`, el perfil violaba el CHECK de `perfil_usuario`, respondía 500 y dejaba el usuario creado a medias (el reintento decía "correo ya registrado"). Ahora vacío = ausente y usuario + perfil se guardan en una sola transacción.
- **Guardar el nivel nunca funcionó**: el código escribía `jr`/`mid`/frases de la app y la BD solo acepta `junior/semisenior/senior`.
- `change-password` aceptaba cualquier contraseña actual.
- El código de recuperación no tenía límite de intentos (ahora 5, luego 429) y los códigos anteriores seguían vigentes.
- `forgot-password` revelaba si un correo existía o era de Google; ahora responde igual y el correo sale en segundo plano.
- Restablecer la contraseña (usuario o admin) y desactivar una cuenta cierran todas sus sesiones.
- Las cuentas creadas con Google quedaban con `origen_registro = 'local'`.
- Un admin podía quitarse el rol o desactivarse a sí mismo.

Compatibilidad con Android (se mantiene):
- Rutas y JSON de éxito iguales; `/perfil/objetivo` se conserva (la app la usa en el onboarding).
- `/auth/register` responde errores como `{"error"}` + 409/422 porque la app los decodifica con un `Json` estricto.
- Los errores incluyen `message` además de `mensaje`: la app lo muestra en los flujos de contraseña.

Pendiente (se movió a la Fase 4, junto a Google Play): consentimientos/EULA, recordatorios y billing siguen en `routes/`.

Tareas originales:
**Hoy:** registro en `AuthService`, `/me` partido entre `AuthController` y `MeRoutes`, objetivo por 3 caminos (`/me/objetivo`, `/perfil/objetivo`, onboarding), dos flujos de recuperación, alta por admin en `repository-add-user-admin`, borrado de cuenta en `DeleteAccountRoute`.

Resultado:
- `CONTROLADOR_USUARIO` (`POST /auth/register`, `GET/PUT /me`, `GET/PUT /me/perfil`, `DELETE` cuenta) → `SERVICIO_USUARIO`.
- `CONTROLADOR_CONTRASENA` (`forgot-password`, `reset-password`, `change-password`) → `SERVICIO_CONTRASENA` → `SERVICIO_CORREO` (interfaz).
- `CONTROLADOR_ONBOARDING` → `SERVICIO_ONBOARDING`: **un solo** camino para cargo meta y nivel, alineado con la tabla `onboarding_usuario`. Se retira `/perfil/objetivo` (Android no lo usa).
- `CONTROLADOR_ADMIN_USUARIO` → reutiliza `SERVICIO_USUARIO` (sin lógica duplicada de alta).
- Validaciones (`Validaciones` de `MeRoutes`) pasan a `ESQUEMAS/` + servicio.
- **Seguridad detectada en Fase 0:**
  - `POST /auth/change-password` recibe `contrasenaActual` pero **no la verifica**: con un token robado se puede cambiar la contraseña.
  - El código de recuperación (6 dígitos, 15 min) no tiene límite de intentos: se puede adivinar por fuerza bruta. Agregar contador de intentos y bloqueo.
  - `forgot-password` revela si un correo está registrado (respuestas distintas).
- Pruebas: correo duplicado, contraseña débil, perfil inválido, cuenta Google sin contraseña, código expirado.

## Fase 3 — Creación de preguntas ✅ (rama `refactor/fase-3-preguntas`)
**Estado:** completada. 145 pruebas en verde + E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_3_PREGUNTAS.ps1` (38/38) contra Postgres real,
verificada con una mutación (detecta si los usuarios ven preguntas no aprobadas). Regresión E2E Fases 1 y 2: 34/34 y 48/48.

Hecho:
- `CONTROLADOR_PREGUNTA` → `SERVICIO_PREGUNTA` (banco: crear, listar paginado, editar, aprobar, rechazar, eliminar, lectura para usuarios) y `SERVICIO_GENERACION_PREGUNTA` (LLM) → `REPOSITORIO_PREGUNTA`, `REPOSITORIO_GENERACION_PREGUNTA_IA`, `LECTOR_CATALOGO`.
- `INTEGRACIONES/PROVEEDOR_LLM`: OpenAI y Anthropic detrás de `ProveedorPreguntasIa`; nuevo error de dominio `ErrorRespuestaExterna` (502).
- `ESQUEMAS/ESQUEMA_RESPUESTA_LLM` (contrato y JSON Schema del LLM) y `SERVICIOS/VALIDADOR_LOTE_PREGUNTA_IA` (reglas STAR, opciones, duplicados).
- `VISTAS/VISTA_PREGUNTA`: vista admin (con solución) y vista pública (sin respuesta ideal, opción correcta ni explicación).
- Eliminados `services/ai`, `services/AiQuestionGeneratorService`, `routes/admin/AdminAiRoutes`, `data/{models,repository,tables}/ai`.

Corregido:
- **El LLM nunca recibía el contexto**: la ruta no pasaba cargo ni skill y el prompt decía "Preguntas generales". Ahora incluye sus nombres.
- Un `cargo_id`/`skill_id` inexistente pagaba la llamada al LLM y luego fallaba al guardar; ahora responde 404 antes de llamar.
- Si fallaba la auditoría de una generación fallida, el cliente recibía 500 en vez del error real del proveedor.
- No existía forma de revisar lo generado por IA ni de crear preguntas manuales; aprobar/rechazar ahora deja constancia en `pregunta_generacion_ia` (estado y quién revisó).

Cambio de contrato (solo panel admin): los errores de validación de `generate-ai` pasan de 422 a 400 y usan el formato único `{error, mensaje}`.

Tareas originales:
**Hoy:** `AiQuestionGeneratorService` + `services/ai/*` (es la parte más ordenada: ya tiene interfaces). Falta CRUD manual y revisión.

Resultado:
- `CONTROLADOR_PREGUNTA` (admin): crear manual, listar con filtros (skill, cargo, nivel, tipo, estado), aprobar/rechazar con motivo, generar con IA.
- `SERVICIO_PREGUNTA` (reglas: opción múltiple exige ≥2 opciones y 1 correcta; abierta exige `respuesta_ideal` o rúbrica) y `SERVICIO_GENERACION_PREGUNTA` (orquesta IA, valida lote, guarda trazabilidad en `pregunta_generacion_ia`).
- `REPOSITORIO_PREGUNTA` sobre `pregunta` + `opcion_pregunta`.
- Las preguntas generadas por IA quedan en `estado = 'pendiente'`; solo `aprobada` se sirve a usuarios.
- Renombrar a español lo existente (`AiQuestion*` → `*_PREGUNTA_IA`).

## Fase 4 — Conexión con servicios y APIs externas ✅ (rama `refactor/fase-4-integraciones`)

**Estado:** completada. 181 pruebas en verde + E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_4_INTEGRACIONES.ps1` (71/71, 73/73 con `-ConLimites`)
contra Postgres y Redis reales; regresión E2E de las fases 1–3 en verde (34, 48 y 38). Migración `015` aplicada a la BD local.

Hecho:
- `UTILIDAD_RESILIENCIA`: `PoliticaResiliencia` común (timeout por intento, reintento exponencial con aleatoriedad solo en fallas
  transitorias, cortocircuito, sin reintentar cancelaciones). La usan LLM (`ProveedorConResiliencia`), correo, Google Play,
  APIs de empleo (una política por fuente) y Redis.
- `INTEGRACIONES/`: `Cache` + `CacheRedis` (no bloquea hilos de Ktor, cortocircuito, falla abierta, `obtenerOCalcular`),
  `ContadorIntentos`, `ClienteMercadoLaboral` (JSearch → Remotive → Arbeitnow → dataset de contingencia),
  `VerificadorCompraGoogle` (real o simulado), `FuenteDocumentosLegales`.
- Mercado en capas: `REPOSITORIO_MERCADO` (lector/escritor), `NormalizadorSkill`, `ServicioRequisitosCargo`,
  `ServicioTendenciasSkill` + `TareaSincronizacionMercado` (no resincroniza en cada reinicio), `ServicioMercado` con caché e invalidación.
  `/market/cargos` y `/api/v1/cargos` comparten servicio y caché.
- Consentimientos/EULA/legal, recordatorios y billing migrados a CONTROLADORES → SERVICIOS → MODELOS con el mismo JSON de Android.
- Bloqueo de login por correo (5 fallos → 15 min, en Redis) y límite por IP de `register`/`forgot-password` (plugin RateLimit).
- `GET /health` verifica la BD (503 si no responde). Se retiró `createMissingTablesAndColumns`; Hikari con timeout de conexión de 5 s.
- Código antiguo eliminado: `routes/{market,skills,consent,legal,billing,usuario}`, `services/{cache,market}`, `security/billing`
  y sus modelos/tablas. Queda `routes/sync` y `FreemiumTextEvaluator` para la Fase 6.
- Dependencias de Ktor fijadas en 3.3.1 (las versiones `3.+` mezclaban 3.6 con 3.3 y rompían las pruebas).

Pendiente conocido: el límite por IP vive en memoria de cada instancia (con varias instancias, moverlo a Redis).

Hallazgos verificados que corrigió esta fase:
- **Mercado/skills (bug desde el commit 4c83f60):** `SkillTrendWorker` y `CargoSkillGeneratorService` analizan `" "` en vez de `"${posting.title} ${posting.description}"`. Las tendencias nunca cuentan ofertas reales (toda skill técnica queda en 35) y los requisitos por cargo salen siempre del conjunto genérico. Varios mensajes perdieron sus interpolaciones (`"Cargo con ID  no encontrado"`).
- **Esquema alterado por `createMissingTablesAndColumns`** (BD local): `consentimiento` tiene una columna extra `alcances` donde se guarda todo, mientras `alcances_aceptados` queda en `[]` y `acepta_entrenamiento_ia` siempre en false; `fecha_otorgado` y `usuario.fecha_creacion` perdieron la zona horaria; `perfil_usuario.nivel_experiencia` pasó a VARCHAR(40); índices duplicados; `objetivo_carrera` solo existe porque la crea Exposed. Plan: migración `015` (0 consentimientos guardados, sin pérdida de datos) y retirar la creación automática.
- **Billing:** un mismo `purchase_token` de Google Play se puede canjear en varias cuentas (no se guarda el token); el canje de códigos no es atómico y se puede pasar de `max_usos`; `/billing/status` mira solo la última suscripción; si Google no responde se informa "compra no válida".
- **Redis:** llamadas bloqueantes en hilos de Ktor; con Redis caído cada petición espera segundos (falta cortocircuito); cache-aside copiado 3 veces; crear un cargo no invalida `cargos:lista` (hasta 6 h desactualizado).
- **Recordatorios:** sin validación de `hora`, `diasSemana` ni `tipoPractica` (valores largos → 500).
- **Consentimientos:** publicar con una versión inexistente viola la FK → 500.
- Límite de intentos: bloquear login por correo tras intentos fallidos (contador en Redis) y limitar `forgot-password`/`register` por IP sin romper las E2E.
**Hoy:** clientes dispersos (`JobMarketClient`, proveedores LLM, `GooglePlayBillingService` dentro de `security/`, `EmailService`, `RedisCacheService`), cada uno con su propio manejo de errores y timeouts.

Resultado (`INTEGRACIONES/`):
- Una interfaz por proveedor: `ProveedorLLM` (OpenAI, Anthropic), `ClienteMercadoLaboral` (JSearch), `ClienteFacturacion` (Google Play), `ClienteCorreo` (SMTP), `Cache` (Redis).
- Política común: timeout, reintentos con espera exponencial solo en errores transitorios, circuito abierto si el proveedor cae, `ErrorServicioExterno` hacia el servicio, log sin datos sensibles.
- Registro de consumo LLM (tokens y costo) centralizado.
- `SkillTrendWorker` y `CargoSkillGeneratorService` pasan a usar estas interfaces.
- Unificar `/market/cargos` y `/api/v1/cargos` (hoy duplicados con y sin cache).
- Pruebas con dobles: proveedor caído, timeout, respuesta mal formada.

## Fase 5 — Creación de entrevista (Flujo 3: simulación) ✅ (rama `refactor/fase-5-entrevista`)
**Estado:** completada. 211 pruebas en verde + E2E `PRUEBAS_E2E/PRUEBA_E2E_FASE_5_ENTREVISTA.ps1` (50/50) contra Postgres real;
regresión E2E de las fases 1–4 en verde. Migración `016` aplicada a la BD local.

Hecho:
- `/api/v1/entrevistas` (iniciar, historial, actual, detalle, siguiente, responder, métricas en lote, finalizar, cancelar) y el adaptador
  `/api/prueba-practica/front` + `/{id}/respuestas` para la pantalla de entrevista de Android (ENT/MIX/SIM), sobre el mismo `ServicioEntrevista`.
- `SelectorPreguntasEntrevista`: 60 % técnicas / 40 % blandas, cargo → skills del cargo → generales, sin repetir las últimas 3 sesiones.
- Snapshot completo por pregunta (migración `016`: tipo, categoría, skill, opciones, opción elegida, fecha de respuesta).
- Una sola sesión en curso: bloqueo del usuario en la transacción + índice único parcial; abandonadas > 2 h se cancelan solas.
- Respuestas todo-o-nada con bloqueo de la sesión; opción múltiple corregida al instante; corrección oculta hasta finalizar.
- `ProcesadorEntrevistaFinalizada` (en segundo plano al finalizar): por ahora solo registra; la Fase 7 genera el reporte.
- La búsqueda de cargo por nombre ya no distingue mayúsculas (Android envía `metaCargo` escrito por el usuario).

Decisiones:
- La app Android no graba audio ni video: las preguntas `simulacion_video` se le muestran como abiertas de texto.
- Un cargo fuera del catálogo se puede practicar: usa preguntas técnicas de cualquier skill sin cargo asignado (el banco no admite
  preguntas sin cargo ni skill, así que no existen técnicas "generales" puras).
- El puntaje que muestra la app es "correctas / preguntas de alternativas"; las abiertas se evalúan en la Fase 7.

Plan original:

Resultado:
- `CONTROLADOR_ENTREVISTA`: iniciar sesión (cargo + nivel), obtener siguiente pregunta, enviar respuesta (transcripción / URL del clip), enviar métricas de video en lote, finalizar o cancelar.
- `SERVICIO_ENTREVISTA`: selecciona preguntas aprobadas por cargo/skills/nivel, guarda snapshot del enunciado, impide dos sesiones `en_progreso` por usuario, controla estados `en_progreso → finalizada | cancelada`.
- `REPOSITORIO_SESION_ENTREVISTA`, `REPOSITORIO_METRICA_VIDEO` (inserción en lote).
- Al finalizar dispara la generación del reporte (Fase 7) en segundo plano.

## Fase 6 — Creación de prueba (Flujo 1: nivelación y Flujo 2: práctica)
**Hoy:** tablas `test_nivelacion`, `intento_test`, `resultado_nivelacion`, `sesion_practica`, `respuesta_practica` sin código; existe `SyncRoutes` (práctica offline) y `FreemiumTextEvaluator` acoplados en un repositorio.

Resultado:
- `CONTROLADOR_PRUEBA` (admin): crear test de nivelación con lista de preguntas precalculada (`preguntas_ids`).
- `CONTROLADOR_NIVELACION`: obtener test para mi cargo meta, rendir intento, ver resultado.
- `CONTROLADOR_PRACTICA`: iniciar sesión por skill, responder, finalizar; sincronizar intentos offline.
- `SERVICIO_NIVELACION`: corrige, asigna nivel, calcula brecha contra `cargo_skill` (`skills_gap` / `skills_ok`), marca `nivel_verificado`, actualiza `nivel_skill_usuario`.
- `SERVICIO_PRACTICA`: corrige opción múltiple; evalúa abiertas con el motor freemium (interfaz `EvaluadorRespuesta`, intercambiable por LLM en premium).

## Fase 7 — Creación de feedback
**Hoy:** tablas `reporte_entrevista`, `reporte_skill_detalle` sin código. El feedback de práctica está solo en el evaluador freemium.

Resultado:
- `SERVICIO_FEEDBACK` con estrategias intercambiables (`EvaluadorRespuesta`: freemium, LLM) para:
  - feedback inmediato por respuesta de práctica (`feedback_texto`, `feedback_ia`);
  - reporte de entrevista asíncrono: puntaje técnico, blando y lenguaje corporal, fortalezas, áreas de mejora, recomendaciones de skills, detalle por skill (radar);
  - resumen del diagnóstico de nivelación (`resumen_ia`).
- Estado `generando → listo | error` con `error_detalle`; reintento manual si falla.
- Actualiza `nivel_skill_usuario` (promedio acumulado por skill).
- `CONTROLADOR_FEEDBACK`: `GET` reporte de una sesión, historial de reportes, progreso por skill.

---

## Fuera de estas fases (se ordenan junto con la fase que los toque)
Billing/suscripciones, consentimientos/EULA y recordatorios: migrados a capas en la Fase 4. Sincronización offline y freemium: Fase 6.

## Orden y dependencias
```
0 Cimientos ─► 1 Login ─► 2 Usuario ─► 3 Preguntas ─► 4 Integraciones ─┬─► 5 Entrevista ─┐
                                                                        └─► 6 Prueba ─────┴─► 7 Feedback
```
Fases 0–4 son refactorización de lo existente. Fases 5–7 son construcción nueva sobre la estructura ya limpia.
