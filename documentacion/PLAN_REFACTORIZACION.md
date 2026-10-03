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

## Fase 1 — Login
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

## Fase 2 — Creación de usuario (cuenta, perfil, recuperación)
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

## Fase 3 — Creación de preguntas
**Hoy:** `AiQuestionGeneratorService` + `services/ai/*` (es la parte más ordenada: ya tiene interfaces). Falta CRUD manual y revisión.

Resultado:
- `CONTROLADOR_PREGUNTA` (admin): crear manual, listar con filtros (skill, cargo, nivel, tipo, estado), aprobar/rechazar con motivo, generar con IA.
- `SERVICIO_PREGUNTA` (reglas: opción múltiple exige ≥2 opciones y 1 correcta; abierta exige `respuesta_ideal` o rúbrica) y `SERVICIO_GENERACION_PREGUNTA` (orquesta IA, valida lote, guarda trazabilidad en `pregunta_generacion_ia`).
- `REPOSITORIO_PREGUNTA` sobre `pregunta` + `opcion_pregunta`.
- Las preguntas generadas por IA quedan en `estado = 'pendiente'`; solo `aprobada` se sirve a usuarios.
- Renombrar a español lo existente (`AiQuestion*` → `*_PREGUNTA_IA`).

## Fase 4 — Conexión con servicios y APIs externas
**Hoy:** clientes dispersos (`JobMarketClient`, proveedores LLM, `GooglePlayBillingService` dentro de `security/`, `EmailService`, `RedisCacheService`), cada uno con su propio manejo de errores y timeouts.

Resultado (`INTEGRACIONES/`):
- Una interfaz por proveedor: `ProveedorLLM` (OpenAI, Anthropic), `ClienteMercadoLaboral` (JSearch), `ClienteFacturacion` (Google Play), `ClienteCorreo` (SMTP), `Cache` (Redis).
- Política común: timeout, reintentos con espera exponencial solo en errores transitorios, circuito abierto si el proveedor cae, `ErrorServicioExterno` hacia el servicio, log sin datos sensibles.
- Registro de consumo LLM (tokens y costo) centralizado.
- `SkillTrendWorker` y `CargoSkillGeneratorService` pasan a usar estas interfaces.
- Unificar `/market/cargos` y `/api/v1/cargos` (hoy duplicados con y sin cache).
- Pruebas con dobles: proveedor caído, timeout, respuesta mal formada.

## Fase 5 — Creación de entrevista (Flujo 3: simulación)
**Hoy:** las tablas existen (`sesion_entrevista`, `sesion_pregunta_respuesta`, `metrica_video`) pero **no hay código**.

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
Billing/suscripciones, consentimientos/EULA, recordatorios: se migran a capas durante la Fase 2 (los que dependen del usuario) y Fase 4 (Google Play).

## Orden y dependencias
```
0 Cimientos ─► 1 Login ─► 2 Usuario ─► 3 Preguntas ─► 4 Integraciones ─┬─► 5 Entrevista ─┐
                                                                        └─► 6 Prueba ─────┴─► 7 Feedback
```
Fases 0–4 son refactorización de lo existente. Fases 5–7 son construcción nueva sobre la estructura ya limpia.
