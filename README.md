# EntrevistaAPPBack

Backend en Kotlin con Ktor para la plataforma de preparación de entrevistas con IA.
Incluye autenticación local y Google OAuth, recuperación de contraseñas por correo (OTP), gestión de perfiles, onboarding, consentimientos legales (GDPR), recordatorios, facturación / suscripciones (Google Play / Códigos), sincronización de prácticas offline y panel de administración.

---

## 🛠 Requisitos

- **JDK 21**
- **Docker** con Docker Compose

---

## 🚀 Base de Datos con Docker

### Levantar la base de datos (con esquema y seeds automáticos)
```bash
# En Linux / WSL:
sudo docker compose -f src/DB/docker-compose.yml up -d

# En Windows (PowerShell):
docker compose -f src/DB/docker-compose.yml up -d
```

### Reiniciar / Limpiar la base de datos desde cero
```bash
# En Linux / WSL:
sudo docker compose -f src/DB/docker-compose.yml down -v
sudo docker compose -f src/DB/docker-compose.yml up -d

# En Windows (PowerShell):
docker compose -f src/DB/docker-compose.yml down -v
docker compose -f src/DB/docker-compose.yml up -d
```

---

## 💻 Ejecución del Backend

```powershell
# Ejecutar tests
.\gradlew.bat test

# Correr la aplicación
.\gradlew.bat run
```
*En Linux/macOS usar `./gradlew`.*

---

## 🧪 Pruebas de punta a punta (E2E)

Scripts en [`PRUEBAS_E2E/`](PRUEBAS_E2E) que recorren cada fase contra el **servidor levantado y Postgres real**.
Crean usuarios `e2e_*@prueba.local` y los borran al terminar. Requieren PowerShell 7 y el contenedor `Entrevista_APP`.

```powershell
# 1) levantar el backend
.\gradlew.bat run
# 2) en otra terminal
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_1_LOGIN.ps1                      # contra http://127.0.0.1:8080
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_1_LOGIN.ps1 -UrlBase http://127.0.0.1:8093
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_2_USUARIO.ps1   # requiere migrations/014 aplicada
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_3_PREGUNTAS.ps1  # -ConIa para incluir una generación real (cuesta)
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_4_INTEGRACIONES.ps1  # requiere migrations/015 y Redis; -ConLimites prueba el límite por IP
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_5_ENTREVISTA.ps1    # requiere migrations/016; arma su propio banco de preguntas
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_6_PRUEBA.ps1        # requiere migrations/017; práctica, nivelación y sincronización offline
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_7_FEEDBACK.ps1      # requiere migrations/018; -ConIa incluye una evaluación real con el LLM (cuesta)
```
La Fase 4 usa también el contenedor `Entrevista_Redis`, no llama a las APIs de empleo ni publica versiones del EULA,
y la sección de compras de Google Play necesita `GOOGLE_PLAY_BILLING_MOCK=true`. `-ConLimites` deja la IP sin poder
registrar cuentas durante unos minutos.
Código de salida: `0` todo pasa, `1` alguna verificación falla, `2` el backend no responde.

---

## 📮 Colección de Postman

Se incluye una colección completa lista para importar en Postman ubicada en:
[`postman/EntrevistaAPP_API.postman_collection.json`](postman/EntrevistaAPP_API.postman_collection.json)

**Características:**
- Incluye variables automáticas (`{{baseUrl}}`, `{{accessToken}}`, `{{refreshToken}}`, `{{adminToken}}`).
- Los endpoints de **Login** y **Register** guardan automáticamente el `accessToken` y `refreshToken` para las siguientes peticiones.

---

## 📖 Referencia Completa de Endpoints

### 0. Sistema
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/health` | Pública | Verifica la base de datos: `OK` o `503 db_no_disponible`. Redis no se incluye (la app funciona sin caché). |

**Límites:** `/auth/register` (30 cada 10 min) y `/auth/forgot-password` (5 cada 15 min) por IP → `429 demasiadas_solicitudes`. El login se bloquea 15 min por correo tras 5 contraseñas incorrectas → `429 demasiados_intentos` (contador compartido en Redis).

---

### 1. Autenticación Local & Sesiones (`/auth`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/auth/register` | Pública | Registro de usuario nuevo y perfil opcional. Retorna tokens JWT. | `{"email", "password", "nombre"?, "idioma"?, "telefono"?, "fechaNacimiento"?, "genero"?, "nivelExperiencia"?, "area"?, "pais"?, "notaObjetivos"?}` |
| `POST` | `/auth/login` | Pública | Inicio de sesión con correo y contraseña. Retorna `accessToken` y `refreshToken`. | `{"email", "password"}` |
| `POST` | `/auth/refresh` | Pública | Rotación de token: envía `refreshToken` y recibe un nuevo par de tokens. | `{"refreshToken"}` |
| `POST` | `/auth/logout` | Pública | Cierre de sesión y revocación del `refreshToken`. | `{"refreshToken"}` |

---

### 2. Autenticación con Google (`/auth/google`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/auth/google` | Pública | Login desde la App Android enviando el `idToken` de Google. | `{"idToken"}` |
| `GET` | `/auth/google/start` | Pública | Inicia el flujo OAuth web de Google. | - |
| `GET` | `/auth/google/callback` | Pública | Callback de retorno para OAuth web de Google. | Query params |

---

### 3. Recuperación de Contraseña vía Email OTP (`/auth`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/auth/forgot-password` | Pública | Envía un código OTP de 6 dígitos al correo del usuario. | `{"correo"}` |
| `POST` | `/auth/reset-password` | Pública | Valida el código OTP y actualiza la contraseña. | `{"correo", "codigo", "nuevaContrasena"}` |
| `POST` | `/auth/change-password` | `Bearer JWT` | Cambio de contraseña para un usuario con sesión activa. | `{"nuevaContrasena"}` |

---

### 4. Cuenta y Perfil del Usuario (`/me` y `/cuenta`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/me` | `Bearer JWT` | Retorna los datos del usuario autenticado, perfil y objetivo. | - |
| `PUT` | `/me` | `Bearer JWT` | Actualiza datos básicos y demográficos (nombre, idioma, teléfono, fechaNacimiento, género). | `{"nombre"?, "idioma"?, "telefono"?, "fechaNacimiento"?, "genero"?}` |
| `GET` | `/me/perfil` | `Bearer JWT` | Consulta el perfil del usuario (experiencia, área, país, accesibilidad). | - |
| `PUT` | `/me/perfil` | `Bearer JWT` | Crea o actualiza el perfil del usuario. | `{"nivelExperiencia"?, "area"?, "pais"?, "notaObjetivos"?, "flagsAccesibilidad"?}` |
| `GET` | `/me/objetivo` | `Bearer JWT` | Obtiene el objetivo de carrera actual. | - |
| `PUT` | `/me/objetivo` | `Bearer JWT` | Crea o actualiza el objetivo de carrera. | `{"nombreCargo", "sector"?}` |
| `DELETE` | `/me/objetivo` | `Bearer JWT` | Elimina el objetivo de carrera. | - |
| `DELETE` | `/cuenta` | `Bearer JWT` | **Derecho al Olvido (GDPR)**: Elimina la cuenta y todos sus datos en cascada. | `{"confirmar": "eliminar"}` |

---

### 5. Onboarding del Usuario (`/onboarding` y `/perfil/objetivo`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/onboarding` | `Bearer JWT` | Guarda toda la información inicial del onboarding (área, nivel, cargo objetivo). | `{"area", "nivelExperiencia", "nombreCargo", "descripcionObjetivo"?}` |
| `GET` | `/onboarding` | `Bearer JWT` | Retorna los datos guardados del onboarding. | - |
| `GET` | `/onboarding/status` | `Bearer JWT` | Consulta si el usuario completó el onboarding (`completed: true/false`). | - |
| `PUT` | `/perfil/objetivo` | `Bearer JWT` | Actualización rápida de objetivo (área, metaCargo, nivel). | `{"area", "metaCargo", "nivel"}` |

---

### 6. Recordatorios y Preferencias (`/recordatorios`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/recordatorios/preferencias` | `Bearer JWT` | Obtiene los días, hora y tipo de práctica configurados. | - |
| `PUT` | `/recordatorios/preferencias` | `Bearer JWT` | Guarda las preferencias de recordatorios y notificaciones. | `{"diasSemana": ["LUN","MIE",...], "hora": "19:00", "tipoPractica": "simulacion_ia", "habilitado": true}` — días `LUN..DOM` (también acepta nombres completos), hora `HH:mm`, `tipoPractica` hasta 32 caracteres. |

---

### 7. Consentimientos Legales (`/consent` y `/me/consent`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/consent/current` | Pública | Obtiene el texto y versión del consentimiento legal vigente. | - |
| `POST` | `/me/consent` | `Bearer JWT` | Registra la aceptación del consentimiento con sus alcances. | `{"version": "v1.0", "alcances": {"uso_datos_sesion": true, ...}}` |
| `GET` | `/me/consent/latest` | `Bearer JWT` | Retorna el último consentimiento activo del usuario. | - |
| `POST` | `/me/consent/revoke` | `Bearer JWT` | Revoca el consentimiento activo del usuario (`404` si no hay). | - |
| `POST` | `/admin/consent/text` | `Bearer JWT (Admin)` | Publica una nueva versión del texto (queda como única vigente). | `{"version", "title", "body"}` |

Una versión inexistente responde `404 version_no_encontrada`. Si la BD no tiene texto, se publica `docs/legal/EULA.md` como `1.0.0`.

**Documentos legales (`/api/v1/legal`, públicos):** `GET /eula`, `GET /versions`, `GET /terms`, `GET /privacy` (desde `docs/legal/`); `POST /admin/eula` (admin) publica una versión.

---

### 8. Billing y Suscripciones (`/billing`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/billing/status` | `Bearer JWT` | Estado actual de suscripción (es Premium, plan, vencimiento). | - |
| `POST` | `/billing/google/verify` | `Bearer JWT` | Valida y activa compras realizadas vía Google Play Billing. | `{"product_id", "purchase_token", "purchase_time"}` |
| `POST` | `/billing/code/redeem` | `Bearer JWT` | Canjea un código promocional o institucional. | `{"code"}` |
| `POST` | `/billing/admin/codes` | `Bearer JWT (Admin)` | Crea códigos de suscripción (PROM, INST, GOOG). | `{"days", "label"?, "max_uses", "license_type", "expires_at"?}` |

Un `purchase_token` pertenece a una sola cuenta (`409 compra_ya_registrada`; se guarda solo su hash). Google caído → `503`, compra inválida → `400 compra_invalida`. El canje es atómico (nunca supera `max_uses`) y suma sus días al premium vigente.

---

### 9. Práctica, nivelación, sincronización offline y freemium (`/api/v1`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/api/v1/practicas` | `Bearer JWT` | Inicia una práctica (1–20 preguntas escritas). Con `skillId` practica esa skill; si no, el cargo (u objetivo del onboarding). Empezar otra abandona la anterior. | `{"skillId"? \| "cargoId"? \| "cargo"?, "categoria"?, "modo"? (opcion_multiple\|abierta_texto\|mixto), "nivel"?, "cantidadPreguntas"?}` |
| `GET` | `/api/v1/practicas` | `Bearer JWT` | Últimas prácticas. | - |
| `GET` | `/api/v1/practicas/{id}` | `Bearer JWT` | Detalle con la corrección de lo respondido. | - |
| `POST` | `/api/v1/practicas/{id}/respuestas` | `Bearer JWT` | Responde y recibe feedback inmediato (opción correcta y su explicación, o el motor freemium en las abiertas). | `{"preguntaId", "opcionId"? \| "texto"?, "tiempoRespuestaMs"?}` |
| `POST` | `/api/v1/practicas/{id}/finalizar` | `Bearer JWT` | Cierra con el promedio (0–100) y lo acumula en el puntaje de cada skill. | - |
| `POST` | `/api/v1/nivelacion` | `Bearer JWT` | Test de nivelación: el que armó un admin para el cargo o 3 técnicas por nivel desde el banco. | `{"cargoId"? \| "cargo"?}` |
| `GET` | `/api/v1/nivelacion/{id}` | `Bearer JWT` | Detalle; la corrección se ve al terminar. | - |
| `POST` | `/api/v1/nivelacion/{id}/respuestas` | `Bearer JWT` | Rinde el test (una sola vez): nivel global, nivel por skill y brechas contra el cargo. | `{"respuestas": [{"preguntaId", "opcionId"? \| "texto"?}]}` |
| `GET` | `/api/v1/nivelacion/resultado` | `Bearer JWT` | Último resultado (`204` si nunca se niveló). | - |
| `GET` | `/api/v1/me/niveles-skill` | `Bearer JWT` | Nivel y puntaje acumulado por skill. | - |
| `GET` | `/api/v1/pruebas/historial` | `Bearer JWT` | Entrevistas, prácticas y nivelaciones juntas. | - |
| `POST` | `/api/v1/sync/attempts` | `Bearer JWT` | Intentos hechos sin conexión. Idempotente por `localAttemptId`; si la pregunta está en el banco se vuelve a corregir en el servidor. | `{"attempts": [{"localAttemptId", "skillId", "modo", "nivelPreguntas", "fechaCreacionIso"?, "respuestas": [...]}]}` |
| `POST` | `/api/v1/practice/evaluate-freemium` | `Bearer JWT` | Corrige un texto con el motor freemium (sin IA). | `{"userText", "idealText", "expectedKeywords": [...]}` |

**Tests de nivelación (admin):** `POST|GET /api/v1/admin/tests-nivelacion`, `GET|PUT|DELETE /api/v1/admin/tests-nivelacion/{id}`
(`{"titulo", "cargoId"?, "area", "nivelObjetivo"?, "descripcion"?, "preguntasIds": [3..30 aprobadas, sin video]}`; DELETE es baja lógica).

- **Nivel:** se sube de junior a senior mientras el promedio del nivel llegue a 60; un nivel sin preguntas corta la subida. Las no respondidas cuentan 0.
- **Brecha:** por cada skill evaluada contra `cargo_skill.nivel_requerido`; prioridad alta si faltan 2 niveles o la skill es obligatoria.
- **Práctica:** suma puntaje a la skill pero no cambia su nivel (eso lo decide la nivelación). Una abierta cuenta como correcta desde 60 puntos.

---

### 10. Administración de Usuarios (`/admin`)
*(Requiere JWT con rol `admin`)*

| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/admin/usuarios` | `Bearer JWT (Admin)` | Lista todos los usuarios registrados en el sistema. | - |
| `POST` | `/admin/users` | `Bearer JWT (Admin)` | Crea un nuevo usuario con rol especificado. | `{"correo", "contrasena", "nombre"?, "idioma"?, "rol": "admin"|"user"}` |
| `POST` | `/admin/usuarios` | `Bearer JWT (Admin)` | Endpoint alternativo de creación de usuario. | `{"correo", "contrasena", "nombre"?, "idioma"?, "rol"}` |
| `PATCH` | `/admin/usuarios/{usuarioId}/rol` | `Bearer JWT (Admin)` | Cambia el rol de un usuario (`user` o `admin`). | `{"nuevoRol": "admin"}` |
| `PATCH` | `/admin/usuarios/{usuarioId}/activar` | `Bearer JWT (Admin)` | Reactiva una cuenta de usuario desactivada. | - |
| `PATCH` | `/admin/usuarios/{usuarioId}/password` | `Bearer JWT (Admin)` | Resetea la contraseña de cualquier usuario. | `{"nuevaContrasena": "..."}` |
| `DELETE` | `/admin/usuarios/{usuarioId}` | `Bearer JWT (Admin)` | Desactiva (soft delete) un usuario. | - |
| `POST` | `/admin/consent/text` | `Bearer JWT (Admin)` | Publica una nueva versión del texto legal de consentimiento. | `{"version", "title", "body"}` |

---

### 11. Banco de Preguntas e IA (`/api/v1/admin/preguntas`)
*(Requiere JWT con rol `admin`. Las preguntas creadas por un admin nacen `aprobada`; las generadas por IA nacen `pendiente`.)*

| Método | Ruta | Descripción | Body (JSON) |
|---|---|---|---|
| `POST` | `/api/v1/admin/preguntas` | Crea una pregunta. Opción múltiple: 2–6 opciones y una sola correcta; abierta: `respuestaIdeal` o `rubrica`. | `{"skillId"?, "cargoId"?, "tipo", "categoria", "nivel", "enunciado", "respuestaIdeal"?, "rubrica"?, "opciones"?: [{"texto","esCorrecta","explicacion"?}]}` |
| `GET` | `/api/v1/admin/preguntas` | Lista paginada. Filtros: `estado`, `tipo`, `categoria`, `nivel`, `skillId`, `cargoId`, `generadaPorIa`, `pagina`, `tamano`. | - |
| `GET` | `/api/v1/admin/preguntas/{id}` | Detalle completo (incluye solución). | - |
| `PUT` | `/api/v1/admin/preguntas/{id}` | Reemplaza el contenido; la pregunta vuelve a `pendiente`. | Igual que crear |
| `PATCH` | `/api/v1/admin/preguntas/{id}/aprobar` | Aprueba (y marca la traza de IA como aprobada). | - |
| `PATCH` | `/api/v1/admin/preguntas/{id}/rechazar` | Rechaza con motivo obligatorio. | `{"motivo"}` |
| `DELETE` | `/api/v1/admin/preguntas/{id}` | Borra si nunca se usó (si no, 409 `pregunta_en_uso`). | - |
| `POST` | `/api/v1/admin/preguntas/generar-ia` | Genera 1–10 preguntas con LLM (también en la ruta antigua `/api/v1/admin/questions/generate-ai`). 404 si el cargo/skill no existe, 503 sin API key, 502 si el LLM responde algo inutilizable. | `{"cargo_id"?, "skill_id"?, "nivel", "cantidad", "tipo", "categoria", "modelo": "gpt-4o-mini" \| "claude-haiku-4-5"}` |

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/preguntas` | `Bearer JWT` | Preguntas **aprobadas** al azar, sin respuesta ideal ni opción correcta. Filtros: `skillId`, `cargoId`, `nivel`, `tipo`, `categoria`, `cantidad` (1–20). |

---

### 12. Mercado laboral y skills
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/cargos` (alias `/market/cargos`) | Pública | Cargos activos (caché Redis 6 h, se invalida al crear cargos). |
| `GET` | `/api/v1/cargos/{id}/skills` | Pública | Matriz de skills del cargo (caché 12 h). |
| `GET` | `/market/cargos/{id}/skills` | Pública | Solo la lista de requisitos (formato antiguo del panel). |
| `GET` | `/api/v1/skills/trending?categoria=tecnica\|blanda&limit=1..100` | Pública | Skills más demandadas. |
| `GET` | `/market/skills` · `/market/skills/{id}/tendencias` | Pública | Skills por demanda e historial semanal. |
| `POST` | `/admin/market/sync-trends` | Admin | Sincroniza tendencias con las APIs de empleo (**consume cuota**). |
| `POST` | `/admin/market/cargos` | Admin | Crea un cargo. `{"nombre", "area", "descripcion"?, "nivelBase"?, "autoGenerateSkills"?}` |
| `POST` | `/admin/market/cargos/{id}/generate-requirements` · `/admin/market/cargos/generate-all` | Admin | Regenera requisitos desde ofertas reales. |

La sincronización corre sola cada 7 días; al reiniciar el servidor espera lo que falte desde la última (no gasta cuota en cada arranque).
Fuentes en orden: JSearch → Remotive → Arbeitnow → dataset de contingencia, cada una con timeout, reintentos y cortocircuito.

---

### 13. Simulación de entrevista (`/api/v1/entrevistas`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/api/v1/entrevistas` | `Bearer JWT` | Inicia una entrevista (8 preguntas por defecto, 3–15). Sin cargo usa el objetivo del onboarding; sin nivel, el del perfil. | `{"cargoId"? \| "cargo"?, "nivel"?, "cantidadPreguntas"?}` |
| `GET` | `/api/v1/entrevistas` | `Bearer JWT` | Historial paginado (`?pagina`, `?tamano`). | - |
| `GET` | `/api/v1/entrevistas/actual` | `Bearer JWT` | Entrevista en curso (`204` si no hay). | - |
| `GET` | `/api/v1/entrevistas/{id}` | `Bearer JWT` | Detalle con preguntas y respuestas; la corrección solo se muestra al terminar. | - |
| `GET` | `/api/v1/entrevistas/{id}/siguiente` | `Bearer JWT` | Primera pregunta sin responder (`204` si no quedan). | - |
| `POST` | `/api/v1/entrevistas/{id}/respuestas` | `Bearer JWT` | Responde una pregunta (una sola vez). | `{"preguntaSesionId", "texto"? \| "opcionId"? \| "videoClipUrl"?}` |
| `POST` | `/api/v1/entrevistas/{id}/metricas` | `Bearer JWT` | Lote de métricas de video (1–300, puntajes 0–100). | `{"metricas": [{"timestampMs", "contactoVisual"?, "postura"?, "confianza"?, "gestos"?, "expresion"?}]}` |
| `POST` | `/api/v1/entrevistas/{id}/finalizar` | `Bearer JWT` | Cierra la entrevista (al menos 1 respuesta) y lanza el reporte en segundo plano. | - |
| `POST` | `/api/v1/entrevistas/{id}/cancelar` | `Bearer JWT` | Cancela la entrevista en curso. | - |

- Una sola entrevista en curso por usuario (`409 entrevista_en_progreso`); una abandonada más de 2 h se cancela sola.
- Preguntas aprobadas del nivel pedido: primero las del cargo, luego las de sus skills, luego generales; evita repetir las de las últimas 3 entrevistas.
- Cada pregunta guarda un snapshot (enunciado, tipo, opciones): editar o borrar el banco no cambia una entrevista rendida.
- Las entrevistas de otro usuario responden `404`.

### 14. Reporte de feedback y progreso (`/api/v1`)
| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/entrevistas/{id}/reporte` | `Bearer JWT` | Reporte de una entrevista finalizada: `generando`, `listo` o `error`. |
| `POST` | `/api/v1/entrevistas/{id}/reporte/reintentar` | `Bearer JWT` | Vuelve a generar un reporte que terminó con error (`202`; hasta 3 intentos en total). |
| `GET` | `/api/v1/reportes` | `Bearer JWT` | Historial de reportes. |
| `GET` | `/api/v1/me/progreso` | `Bearer JWT` | Nivel y puntaje acumulado por skill, con su puntaje en cada entrevista. |

- El reporte se genera en segundo plano al finalizar la entrevista: corrige las respuestas abiertas, calcula los puntajes
  técnico, blando y de lenguaje corporal (promedio de las métricas de video), y arma el radar por skill, las fortalezas,
  las áreas de mejora y las skills del cargo que conviene reforzar.
- **Puntaje global:** técnico 50 %, blando 30 %, lenguaje corporal 20 % (si algo no se midió, su peso se reparte). Las preguntas sin responder cuentan 0.
- **Evaluación de las respuestas abiertas:** motor freemium (sin costo) para todos; con IA para usuarios premium cuando hay
  `OPENAI_API_KEY` o `ANTHROPIC_API_KEY`. Si la IA falla o responde algo inválido se usa el freemium: el reporte siempre sale.
  El modelo, los tokens y el costo quedan en la BD y no se muestran al usuario.
- Si la generación falla, el usuario ve un mensaje claro (`puedeReintentar`); el código del error queda solo en la BD.

**App Android (`/api/prueba-practica`, mismos servicios por debajo):** `POST /front` crea la prueba según `tipoPrueba`:
`ENT` entrevista, `PR` práctica técnica, `BL` práctica blanda, `NV` nivelación (con el JSON de siempre).
`POST /{pruebaId}/respuestas` guarda las respuestas (acepta `respuestaAbierta` y `respuestaTexto`) y la cierra;
la nivelación devuelve `nivelDetectado` ("Junior" | "Semi Senior" | "Senior"). `GET /intentos` es el historial de todas.
En la entrevista las abiertas quedan para el reporte de feedback (Fase 7); en práctica y nivelación se corrigen con el motor freemium.

---

## ⚙️ Variables de entorno de integraciones
| Variable | Uso |
|---|---|
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Caché y bloqueo de login. Si Redis cae la app sigue funcionando (sin caché). |
| `JSEARCH_API_HOST`, `JSEARCH_API_KEY` | API de empleo principal; sin ella se usan las fuentes gratuitas. |
| `GOOGLE_PLAY_PACKAGE`, `GOOGLE_PLAY_SERVICE_JSON_B64` | Verificación real de compras (se validan al arrancar). |
| `GOOGLE_PLAY_BILLING_MOCK` | `true` para simular compras válidas por 30 días (desarrollo). |
| `LIMITE_REGISTROS_POR_IP`, `LIMITE_RECUPERACIONES_POR_IP` | Cambian los límites por IP (por defecto 30 y 5). |

**Migraciones:** el servidor ya no crea ni altera tablas al arrancar. Aplicar `migrations/014` a `018` (idempotentes) sobre una BD existente:
```powershell
Get-Content migrations/015_alinear_esquema.sql | docker exec -i Entrevista_APP psql -U root -d DBentrevista
```
