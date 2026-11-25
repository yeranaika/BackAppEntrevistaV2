# API de EntrevistaAPP

> Archivo generado por `docs/documentacion/GENERAR_DOCUMENTACION.py`. No editar a mano: cambiar la definición y volver a generar.

Backend Kotlin + Ktor. Todas las respuestas son JSON en UTF-8. La colección de Postman con los mismos endpoints está en
[`postman/`](postman/) (ver [README](README.md)).

## Conceptos generales

### URL base
| Entorno | URL |
|---|---|
| Local | `http://localhost:8080` |

### Autenticación
- **JWT Bearer**: `Authorization: Bearer <accessToken>`. El `accessToken` dura **15 minutos**.
- `POST /auth/login` y `POST /auth/register` entregan `accessToken` y `refreshToken` (dura **15 días**).
- `POST /auth/refresh` rota el refresh token: el anterior deja de servir. Reusar uno ya rotado revoca todas las sesiones.
- **Roles**: `user` y `admin`. Las rutas de admin responden `401` sin token y `403` con un token de usuario normal.
- En las tablas: **Pública** (sin token), **Usuario** (cualquier sesión), **Admin** (rol admin).

### Formato de error
Todos los errores usan el mismo cuerpo, con un código estable para el cliente y un mensaje en español para mostrar:
```json
{ "error": "entrevista_en_progreso", "mensaje": "Ya tienes una entrevista en curso…", "message": "Ya tienes una entrevista en curso…" }
```
`message` repite `mensaje` por compatibilidad con la app Android. Excepción: `POST /auth/register` responde
`{"error": "email_in_use"}` (409) y `{"error": "invalid_country" | "invalid_birthdate"}` (422), como lo espera la app.

| HTTP | Cuándo |
|---|---|
| 400 | Datos inválidos o regla de negocio no cumplida (`id_invalido`, `nivel_invalido`, `sin_respuestas`…) |
| 401 | Sin sesión, token vencido o credenciales incorrectas |
| 403 | Sesión válida sin permiso (rutas de admin) |
| 404 | No existe, o es de otro usuario (no se revela que existe) |
| 409 | Choca con el estado actual (`entrevista_en_progreso`, `pregunta_ya_respondida`, `compra_ya_registrada`…) |
| 429 | Límite superado: `demasiadas_solicitudes` (por IP), `demasiados_intentos` (login) o `tope_generacion_ia_alcanzado` (gasto mensual en generar preguntas con IA) |
| 500 | Error inesperado (`error_interno`); el detalle queda en el log, nunca en la respuesta |
| 502 | Un proveedor externo respondió algo inutilizable (ej: el LLM) |
| 503 | Un proveedor externo o la base de datos no está disponible |

### Respuestas de éxito
Toda respuesta exitosa de un `POST`, `PUT`, `PATCH` o `DELETE` trae `mensaje`: un texto en español para mostrarle al
usuario. Si la operación devuelve un recurso, `mensaje` va como un campo más junto a los datos (no los envuelve):
```json
{ "id": "…", "estado": "aprobada", "enunciado": "…", "mensaje": "Pregunta creada y aprobada" }
```
Si no hay recurso que devolver, la respuesta es `{ "ok": true, "mensaje": "Perfil actualizado" }`. Las que Android ya
leía como `message` (contraseñas, borrar cuenta, admin de usuarios) traen los dos campos con el mismo texto.
Los `GET` devuelven solo los datos; `204` significa "no hay nada" (ej: no hay entrevista en curso) y no trae cuerpo.

### Convenciones
- **Fechas**: ISO-8601 en UTC (`2026-10-03T14:05:00Z`); fechas sin hora `YYYY-MM-DD`.
- **Ids**: UUID. Un id mal formado en la ruta responde `400 id_invalido`.
- **Niveles**: `junior | semisenior | senior` (también se aceptan `jr | mid | sr`).
- **Paginación**: `?pagina=1&tamano=20` donde aplica (tamaño máximo 100).
- **Límites por IP**: registro 30 cada 10 min; recuperación de contraseña 5 cada 15 min; generación de preguntas con
  IA (admin) 30 cada 60 min. **Login**: 5 contraseñas incorrectas bloquean el correo 15 min.
- ⚠️ en una descripción = cuesta dinero (LLM), consume cuota de APIs externas, manda correos o borra datos.

## Índice

- [00 · Sistema](#00--sistema)
- [01 · Autenticación](#01--autenticación)
- [02 · Contraseña](#02--contraseña)
- [03 · Cuenta y perfil](#03--cuenta-y-perfil)
- [04 · Onboarding y objetivo](#04--onboarding-y-objetivo)
- [05 · Consentimientos y documentos legales](#05--consentimientos-y-documentos-legales)
- [06 · Recordatorios](#06--recordatorios)
- [07 · Suscripción (billing)](#07--suscripción-billing)
- [08 · Mercado laboral](#08--mercado-laboral)
- [09 · Banco de preguntas](#09--banco-de-preguntas)
- [10 · Entrevista](#10--entrevista)
- [11 · Feedback](#11--feedback)
- [12 · Práctica](#12--práctica)
- [13 · Nivelación](#13--nivelación)
- [14 · Tests de nivelación (admin)](#14--tests-de-nivelación-admin)
- [15 · App Android (/api/prueba-practica)](#15--app-android-apiprueba-practica)
- [16 · Administración de usuarios](#16--administración-de-usuarios)
- [17 · Cierre de sesión](#17--cierre-de-sesión)
- [18 · Rutas antiguas (alias)](#18--rutas-antiguas-alias)
- [Catálogo de códigos de error](#catálogo-de-códigos-de-error)

## 00 · Sistema

Estado del servicio.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/health` | Pública | Salud |
| `GET` | `/ready` | Pública | Disponibilidad |

### Salud

`GET /health` · Pública · responde `200`

Liveness del proceso: responde `200` en JSON mientras el servidor siga vivo, aunque una dependencia o el modelo de visión no estén disponibles. Incluye `modeloDivlara: { version, cargado, error? }` (campo `error` solo cuando existen los artefactos y falla su carga). Ejemplo sin artefactos: `{"status":"OK","modeloDivlara":{"version":"divlara-e14-mlp","cargado":false}}`. No usar como healthcheck de disponibilidad. Contrato de inferencia del modelo guía E14: `models/divlara-e14-mlp/MANIFIESTO.json`.

### Disponibilidad

`GET /ready` · Pública · responde `200`

Responde `503 {"status":"warming"}` durante el calentamiento HTTP Freemium y `200 {"status":"ready"}` al terminar. Configura el healthcheck de Railway para consultar esta ruta.

## 01 · Autenticación

Registro, login con correo o Google, rotación y cierre de sesión. Los requests de login guardan `accessToken` y `refreshToken` automáticamente.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/auth/register` | Pública | Registrar usuario |
| `POST` | `/auth/login` | Pública | Login |
| `POST` | `/auth/login` | Pública | Login admin |
| `POST` | `/auth/refresh` | Pública | Renovar tokens |
| `POST` | `/auth/google` | Pública | Login con Google (Android) |
| `GET` | `/auth/google/start` | Pública | Login con Google (web) |

### Registrar usuario

`POST /auth/register` · Pública · responde `201`, `409`

Crea la cuenta (contraseña Argon2id, mínimo 8 caracteres) y devuelve tokens.
Errores con el formato antiguo que lee Android: `409 {"error":"email_in_use"}` y `422 {"error":"<código>"}` (ej. `area_invalida`, `nivel_experiencia_invalido`, `invalid_country`, `invalid_birthdate`).
Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`.
Límite: 30 registros cada 10 minutos por IP → `429 demasiadas_solicitudes`.

```json
{
  "email": "{{correo}}",
  "password": "{{contrasena}}",
  "nombre": "Usuario Prueba",
  "nivelExperiencia": "junior",
  "area": "TI"
}
```

### Login

`POST /auth/login` · Pública · responde `200`

Devuelve `accessToken` (JWT, 15 min) y `refreshToken` (15 días).
`401 bad_credentials`; tras 5 contraseñas incorrectas el correo queda bloqueado 15 min → `429 demasiados_intentos`.

```json
{
  "email": "{{correo}}",
  "password": "{{contrasena}}"
}
```

### Login admin

`POST /auth/login` · Pública · responde `200`

Igual que Login, pero guarda el token en `adminToken`. La cuenta debe tener rol `admin` (se asigna desde Administración de usuarios o en la BD).

```json
{
  "email": "{{correoAdmin}}",
  "password": "{{contrasenaAdmin}}"
}
```

### Renovar tokens

`POST /auth/refresh` · Pública · responde `200`

Rota el refresh token (el anterior deja de servir). Reusar uno ya rotado revoca todas las sesiones del usuario → `401`.

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

### Login con Google (Android)

`POST /auth/google` · Pública · responde `200`

Recibe el `idToken` que obtiene la app con Google Sign-In y devuelve los tokens de la API.

```json
{
  "idToken": "<id token de Google>"
}
```

### Login con Google (web)

`GET /auth/google/start` · Pública · responde `200`

Redirige al consentimiento de Google; al volver (`/auth/google/callback`) entrega los tokens. Abrir en el navegador, no en Postman.

## 02 · Contraseña

Recuperación por código enviado al correo y cambio desde el perfil.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/auth/forgot-password` | Pública | Olvidé mi contraseña |
| `POST` | `/auth/reset-password` | Pública | Restablecer con código |
| `POST` | `/auth/change-password` | Usuario | Cambiar contraseña |

### Olvidé mi contraseña

`POST /auth/forgot-password` · Pública · responde `200`

⚠️ Envía un correo real. Responde lo mismo exista o no la cuenta. Límite: 5 cada 15 min por IP.

```json
{
  "correo": "{{correo}}"
}
```

### Restablecer con código

`POST /auth/reset-password` · Pública · responde `200`

Valida el código de 6 dígitos (15 min, intentos limitados) y cambia la contraseña; cierra las demás sesiones.

```json
{
  "correo": "{{correo}}",
  "codigo": "123456",
  "nuevaContrasena": "Otra-clave-segura-2"
}
```

### Cambiar contraseña

`POST /auth/change-password` · Usuario · responde `200`, `400`

Requiere la contraseña actual. `400` si la nueva es igual a la actual o no cumple el mínimo.
El ejemplo usa **la misma** contraseña a propósito (responde `400`) para no cambiar la de la variable `contrasena`. Para cambiarla de verdad: poner otra en `nuevaContrasena` y luego actualizar la variable `contrasena`.

```json
{
  "contrasenaActual": "{{contrasena}}",
  "nuevaContrasena": "{{contrasena}}"
}
```

## 03 · Cuenta y perfil

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/me` | Usuario | Mi cuenta |
| `PUT` | `/me` | Usuario | Actualizar cuenta |
| `GET` | `/me/perfil` | Usuario | Mi perfil |
| `PUT` | `/me/perfil` | Usuario | Actualizar perfil |
| `DELETE` | `/cuenta` | Usuario | Eliminar mi cuenta |

### Mi cuenta

`GET /me` · Usuario · responde `200`

Cuenta + perfil + cargo meta. Guarda `usuarioId`.

### Actualizar cuenta

`PUT /me` · Usuario · responde `200`

```json
{
  "nombre": "Usuario Prueba",
  "idioma": "es",
  "telefono": "+56912345678",
  "fechaNacimiento": "1998-05-20",
  "genero": "otro"
}
```

### Mi perfil

`GET /me/perfil` · Usuario · responde `200`, `404`

### Actualizar perfil

`PUT /me/perfil` · Usuario · responde `200`

Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`.

```json
{
  "nivelExperiencia": "junior",
  "area": "TI",
  "pais": "CL",
  "notaObjetivos": "Conseguir mi primer trabajo"
}
```

### Eliminar mi cuenta

`DELETE /cuenta` · Usuario · responde `200`

⚠️ Borrado definitivo de la cuenta y todos sus datos. Requiere `{"confirmar": "eliminar"}`.

```json
{
  "confirmar": "eliminar"
}
```

## 04 · Onboarding y objetivo

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `PUT` | `/perfil/objetivo` | Usuario | Onboarding (Android) |
| `POST` | `/onboarding` | Usuario | Onboarding |
| `GET` | `/onboarding` | Usuario | Ver onboarding |
| `GET` | `/onboarding/status` | Usuario | Estado del onboarding |
| `GET` | `/me/objetivo` | Usuario | Mi objetivo |
| `PUT` | `/me/objetivo` | Usuario | Cambiar objetivo |
| `DELETE` | `/me/objetivo` | Usuario | Borrar objetivo |

### Onboarding (Android)

`PUT /perfil/objetivo` · Usuario · responde `200`

Área, cargo meta y nivel (acepta `jr|mid|sr` o `junior|semisenior|senior`). Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`.

```json
{
  "area": "TI",
  "metaCargo": "Backend Developer",
  "nivel": "jr"
}
```

### Onboarding

`POST /onboarding` · Usuario · responde `200`

```json
{
  "area": "TI",
  "nivelExperiencia": "junior",
  "nombreCargo": "Backend Developer",
  "descripcionObjetivo": "Primer empleo"
}
```

### Ver onboarding

`GET /onboarding` · Usuario · responde `200`

### Estado del onboarding

`GET /onboarding/status` · Usuario · responde `200`

### Mi objetivo

`GET /me/objetivo` · Usuario · responde `200`, `404`

### Cambiar objetivo

`PUT /me/objetivo` · Usuario · responde `200`

```json
{
  "nombreCargo": "Backend Developer",
  "sector": "tecnologia"
}
```

### Borrar objetivo

`DELETE /me/objetivo` · Usuario · responde `200`

Desactiva el cargo meta (la entrevista y la práctica lo usan por defecto).

## 05 · Consentimientos y documentos legales

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/consent/current` | Pública | Texto vigente |
| `POST` | `/me/consent` | Usuario | Aceptar consentimiento |
| `GET` | `/me/consent/latest` | Usuario | Mi consentimiento |
| `POST` | `/me/consent/revoke` | Usuario | Revocar consentimiento |
| `GET` | `/api/v1/legal/eula` | Pública | EULA vigente |
| `GET` | `/api/v1/legal/versions` | Pública | Versiones del EULA |
| `GET` | `/api/v1/legal/terms` | Pública | Términos de servicio |
| `GET` | `/api/v1/legal/privacy` | Pública | Política de privacidad |
| `POST` | `/api/v1/legal/admin/eula` | Admin | Publicar versión del EULA (admin) |

### Texto vigente

`GET /consent/current` · Pública · responde `200`

### Aceptar consentimiento

`POST /me/consent` · Usuario · responde `201`

`404 version_no_encontrada` si la versión no existe. Un consentimiento nuevo revoca el anterior.

```json
{
  "version": "{{versionConsentimiento}}",
  "alcances": {
    "uso_datos": true,
    "ia_entrenamiento": false
  }
}
```

### Mi consentimiento

`GET /me/consent/latest` · Usuario · responde `200`, `204`

### Revocar consentimiento

`POST /me/consent/revoke` · Usuario · responde `200`, `404`

### EULA vigente

`GET /api/v1/legal/eula` · Pública · responde `200`

### Versiones del EULA

`GET /api/v1/legal/versions` · Pública · responde `200`

### Términos de servicio

`GET /api/v1/legal/terms` · Pública · responde `200`

### Política de privacidad

`GET /api/v1/legal/privacy` · Pública · responde `200`

### Publicar versión del EULA (admin)

`POST /api/v1/legal/admin/eula` · Admin · responde `200`

⚠️ Cambia el texto vigente para todos los usuarios. Alias: `POST /admin/consent/text`.

```json
{
  "version": "2.0.0",
  "title": "EULA",
  "body": "Texto"
}
```

## 06 · Recordatorios

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `PUT` | `/recordatorios/preferencias` | Usuario | Guardar preferencias |
| `GET` | `/recordatorios/preferencias` | Usuario | Ver preferencias |

### Guardar preferencias

`PUT /recordatorios/preferencias` · Usuario · responde `200`

Días `LUN..DOM` (también acepta nombres completos), hora `HH:mm`, `tipoPractica` hasta 32 caracteres.

```json
{
  "diasSemana": [
    "LUN",
    "MIE",
    "VIE"
  ],
  "hora": "20:30",
  "tipoPractica": "entrevista",
  "habilitado": true
}
```

### Ver preferencias

`GET /recordatorios/preferencias` · Usuario · responde `200`

`404 recordatorio_no_configurado` si nunca se guardaron.

## 07 · Suscripción (billing)

JSON en snake_case (contrato de Android).

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/billing/status` | Usuario | Estado de la suscripción |
| `POST` | `/billing/admin/codes` | Admin | Crear código premium (admin) |
| `POST` | `/billing/code/redeem` | Usuario | Canjear código |
| `POST` | `/billing/google/verify` | Usuario | Verificar compra de Google Play |

### Estado de la suscripción

`GET /billing/status` · Usuario · responde `200`

`is_premium`, `plan`, `source` (`google` | `interna`), `status`, `start_at` y `expires_at` (ms epoch). A un premium con IA activa le suma `reportes_ia_limite`, `reportes_ia_restantes` (0 a límite) y `reportes_ia_renovacion` (ms epoch del inicio del mes siguiente, UTC): los reportes con IA que le quedan en el mes, contados como la reserva (D29). A un usuario gratis no le llegan.

### Crear código premium (admin)

`POST /billing/admin/codes` · Admin · responde `201`

`license_type`: PROM, INST o GOOG.

```json
{
  "days": 30,
  "label": "postman",
  "max_uses": 1,
  "license_type": "PROM"
}
```

### Canjear código

`POST /billing/code/redeem` · Usuario · responde `200`, `400`

Atómico: nunca supera `max_uses`. Responde el mismo estado que `GET /billing/status` (con el cupo de reportes con IA, D29) más `mensaje`. `400 codigo_invalido_o_expirado`.

```json
{
  "code": "{{codigoPremium}}"
}
```

### Verificar compra de Google Play

`POST /billing/google/verify` · Usuario · responde `200`

Un token pertenece a una sola cuenta (`409 compra_ya_registrada`). Si Google dice que la pagó otra cuenta de la app (`obfuscatedExternalAccountId` distinto del SHA-256 del id de la cuenta), también `409 compra_ya_registrada` (D27); sin ese dato decide el dueño del token. Google caído → `503`. Con `GOOGLE_PLAY_BILLING_MOCK=true` se simula.

```json
{
  "product_id": "premium_mensual",
  "purchase_token": "<token de Google Play>",
  "purchase_time": 1735689600000
}
```

## 08 · Mercado laboral

Catálogo público de cargos y skills (caché Redis: cargos 6 h, matriz 12 h; crear un cargo invalida la caché). Las tendencias se sincronizan solas cada 7 días desde JSearch → Remotive → Arbeitnow → dataset de contingencia. La administración es solo para admin.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/admin/market/cargos` | Admin | Crear cargo (admin) |
| `GET` | `/api/v1/cargos` | Pública | Cargos |
| `GET` | `/api/v1/cargos/{{cargoId}}/skills` | Pública | Matriz de skills del cargo |
| `GET` | `/api/v1/skills/trending` | Pública | Skills en tendencia |
| `GET` | `/market/skills` | Pública | Skills por demanda |
| `GET` | `/market/skills/{{skillId}}/tendencias` | Pública | Historial de una skill |
| `POST` | `/admin/market/sync-trends` | Admin | Sincronizar tendencias (admin) |
| `POST` | `/admin/market/cargos/{{cargoId}}/generate-requirements` | Admin | Regenerar requisitos de un cargo (admin) |
| `POST` | `/admin/market/cargos/generate-all` | Admin | Regenerar requisitos de todos los cargos (admin) |

### Crear cargo (admin)

`POST /admin/market/cargos` · Admin · responde `201`

Con `autoGenerateSkills: true` arma los requisitos desde las APIs de empleo (⚠️ consume cuota).

```json
{
  "nombre": "Cargo Postman {{$timestamp}}",
  "area": "backend",
  "descripcion": "Creado desde Postman",
  "nivelBase": "junior",
  "autoGenerateSkills": false
}
```

### Cargos

`GET /api/v1/cargos` · Pública · responde `200`

Alias antiguo: `GET /market/cargos`.

### Matriz de skills del cargo

`GET /api/v1/cargos/{{cargoId}}/skills` · Pública · responde `200`

### Skills en tendencia

`GET /api/v1/skills/trending?categoria=tecnica&limit=10` · Pública · responde `200`

### Skills por demanda

`GET /market/skills` · Pública · responde `200`

### Historial de una skill

`GET /market/skills/{{skillId}}/tendencias` · Pública · responde `200`, `400`, `404`

### Sincronizar tendencias (admin)

`POST /admin/market/sync-trends` · Admin · responde `200`

⚠️ Llama a las APIs de empleo (consume cuota). Corre sola cada 7 días.

### Regenerar requisitos de un cargo (admin)

`POST /admin/market/cargos/{{cargoId}}/generate-requirements` · Admin · responde `200`

⚠️ Consume cuota de las APIs de empleo. `POST /admin/market/cargos/generate-all` hace lo mismo con todos.

### Regenerar requisitos de todos los cargos (admin)

`POST /admin/market/cargos/generate-all` · Admin · responde `200`

⚠️ Consume cuota de las APIs de empleo por cada cargo.

## 09 · Banco de preguntas

Administración (solo admin) y lectura para usuarios.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/admin/preguntas` | Admin | Crear pregunta de alternativas (admin) |
| `POST` | `/api/v1/admin/preguntas` | Admin | Crear otra pregunta de alternativas (admin) |
| `POST` | `/api/v1/admin/preguntas` | Admin | Crear pregunta abierta (admin) |
| `POST` | `/api/v1/admin/preguntas` | Admin | Crear pregunta de comportamiento (admin) |
| `GET` | `/api/v1/admin/preguntas` | Admin | Listar preguntas (admin) |
| `GET` | `/api/v1/admin/preguntas/{{preguntaId}}` | Admin | Ver pregunta (admin) |
| `GET` | `/api/v1/admin/preguntas/{{preguntaId}}/similares` | Admin | Preguntas parecidas a una (admin) |
| `GET` | `/api/v1/admin/preguntas/duplicadas` | Admin | Posibles preguntas duplicadas (admin) |
| `PATCH` | `/api/v1/admin/preguntas/{{preguntaId}}/aprobar` | Admin | Aprobar pregunta (admin) |
| `PUT` | `/api/v1/admin/preguntas/{{preguntaId}}` | Admin | Editar pregunta (admin) |
| `DELETE` | `/api/v1/admin/preguntas/{{preguntaId}}` | Admin | Borrar pregunta (admin) |
| `PATCH` | `/api/v1/admin/preguntas/{{preguntaId}}/rechazar` | Admin | Rechazar pregunta (admin) |
| `POST` | `/api/v1/admin/preguntas/generar-ia` | Admin | Generar preguntas con IA (admin) |
| `GET` | `/api/v1/admin/preguntas/feedback-generico` | Admin | Preguntas sin feedback genérico completo (admin) |
| `POST` | `/api/v1/admin/preguntas/feedback-generico/completar` | Admin | Completar feedback genérico con IA (admin) |
| `GET` | `/api/v1/admin/preguntas/cobertura` | Admin | Cobertura del banco frente al mercado (admin) |
| `GET` | `/api/v1/preguntas` | Usuario | Preguntas para usuarios |

### Crear pregunta de alternativas (admin)

`POST /api/v1/admin/preguntas` · Admin · responde `201`

Nace aprobada. Opción múltiple: 2 a 6 opciones y exactamente una correcta. Requiere `cargoId` o `skillId`.

```json
{
  "cargoId": "{{cargoId}}",
  "tipo": "opcion_multiple",
  "categoria": "tecnica",
  "nivel": "junior",
  "enunciado": "¿Qué hace la palabra clave suspend en Kotlin? {{$timestamp}}",
  "opciones": [
    {
      "texto": "Permite suspender sin bloquear el hilo",
      "esCorrecta": true,
      "explicacion": "Las corrutinas se suspenden sin bloquear"
    },
    {
      "texto": "Crea un hilo nuevo"
    },
    {
      "texto": "Bloquea el hilo actual"
    }
  ]
}
```

### Crear otra pregunta de alternativas (admin)

`POST /api/v1/admin/preguntas` · Admin · responde `201`

```json
{
  "cargoId": "{{cargoId}}",
  "tipo": "opcion_multiple",
  "categoria": "tecnica",
  "nivel": "junior",
  "enunciado": "¿Qué diferencia a val de var? {{$timestamp}}",
  "opciones": [
    {
      "texto": "val no se puede reasignar",
      "esCorrecta": true
    },
    {
      "texto": "No hay diferencia"
    }
  ]
}
```

### Crear pregunta abierta (admin)

`POST /api/v1/admin/preguntas` · Admin · responde `201`

Las abiertas necesitan `respuestaIdeal` o `rubrica`. Las `palabras_clave` de la rúbrica las usa el motor freemium.

```json
{
  "cargoId": "{{cargoId}}",
  "tipo": "abierta_texto",
  "categoria": "tecnica",
  "nivel": "junior",
  "enunciado": "Explica qué es una corrutina {{$timestamp}}",
  "respuestaIdeal": "Una corrutina se suspende sin bloquear el hilo y se reanuda después.",
  "rubrica": {
    "criterios": [
      "explica_suspension"
    ],
    "palabras_clave": [
      "corrutina",
      "hilo"
    ]
  }
}
```

### Crear pregunta de comportamiento (admin)

`POST /api/v1/admin/preguntas` · Admin · responde `201`

```json
{
  "cargoId": "{{cargoId}}",
  "tipo": "abierta_texto",
  "categoria": "blanda",
  "nivel": "junior",
  "enunciado": "Cuéntame de un conflicto en tu equipo {{$timestamp}}",
  "respuestaIdeal": "Situación, tarea, acción y resultado del conflicto."
}
```

### Listar preguntas (admin)

`GET /api/v1/admin/preguntas?estado=aprobada&cargoId={{cargoId}}&pagina=1&tamano=20` · Admin · responde `200`

Filtros: estado, tipo, categoria, nivel, skillId, cargoId, paraCargoId (del cargo o sin cargo: lo que cuenta la cobertura, D26), generadaPorIa, pagina, tamano.

### Ver pregunta (admin)

`GET /api/v1/admin/preguntas/{{preguntaId}}` · Admin · responde `200`

### Preguntas parecidas a una (admin)

`GET /api/v1/admin/preguntas/{{preguntaId}}/similares?umbral=0.8` · Admin · responde `200`

D37. Aprobadas y pendientes del mismo skill (o cargo), tipo, categoría y nivel cuyo enunciado se parece a esta pregunta, de mayor a menor (`similitud` 0 a 1, hasta 5). `umbral` 0.5 a 1 (por defecto `UMBRAL_SIMILITUD_PREGUNTAS`, 0.80); `400 umbral_invalido`. Solo mide palabras compartidas: una paráfrasis con otro vocabulario no aparece. No llama al LLM.

### Posibles preguntas duplicadas (admin)

`GET /api/v1/admin/preguntas/duplicadas?cargoId={{cargoId}}&nivel=junior&tipo=abierta_texto&categoria=tecnica&umbral=0.8` · Admin · responde `200`

D37. Grupos de preguntas parecidas entre sí dentro de un alcance (hasta las 200 más recientes). Exige `skillId` o `cargoId` más `nivel`, `tipo` y `categoria` (`400` si falta alguno) para no cruzar todo el banco. Cada grupo trae `preguntas` (`id`, `enunciado`, `estado`, `vecesUsada`) y `similitudMaxima`. Para dejar una, `PATCH .../rechazar` con `motivo: "duplicada"` (la usada no se puede borrar).

### Aprobar pregunta (admin)

`PATCH /api/v1/admin/preguntas/{{preguntaId}}/aprobar` · Admin · responde `200`

D37: la respuesta de crear, editar y aprobar trae `similares` (mismo formato que `/similares`, vacío si no hay): solo avisa, nunca bloquea. En el resto de las rutas `similares` va vacío.

### Editar pregunta (admin)

`PUT /api/v1/admin/preguntas/{{preguntaId}}` · Admin · responde `200`

Reemplaza el contenido; la pregunta vuelve a quedar pendiente de revisión (y su traza de IA, si la tiene, a `pendiente_revision` sin revisor).

```json
{
  "cargoId": "{{cargoId}}",
  "tipo": "opcion_multiple",
  "categoria": "tecnica",
  "nivel": "junior",
  "enunciado": "¿Para qué sirve suspend en Kotlin?",
  "opciones": [
    {
      "texto": "Suspender sin bloquear el hilo",
      "esCorrecta": true
    },
    {
      "texto": "Crear un hilo"
    }
  ]
}
```

### Borrar pregunta (admin)

`DELETE /api/v1/admin/preguntas/{{preguntaId}}` · Admin · responde `200`

Solo si nunca se usó en una prueba; si no, conviene rechazarla.

### Rechazar pregunta (admin)

`PATCH /api/v1/admin/preguntas/{{preguntaId}}/rechazar` · Admin · responde `200`

Deja la pregunta fuera de las pruebas. `PUT /api/v1/admin/preguntas/{id}` edita (vuelve a pendiente) y `DELETE` borra si nunca se usó.

```json
{
  "motivo": "Muy fácil"
}
```

### Generar preguntas con IA (admin)

`POST /api/v1/admin/preguntas/generar-ia` · Admin · responde `200`

⚠️ Llama al LLM (cuesta). Las preguntas nacen pendientes de revisión. Máximo 10. `429 tope_generacion_ia_alcanzado` si el gasto del mes (UTC, todos los admins, fallidas incluidas) llegó a `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` (5 USD por defecto; `0` la desactiva): se revisa antes de llamar al LLM y el mensaje dice cuándo se repone. `429 demasiadas_solicitudes` tras 30 generaciones en 60 min desde la misma IP (`LIMITE_GENERACIONES_IA_POR_IP`, contador común con el alias). Con cargo o skill y `usar_mercado` (por defecto `true`), el prompt lleva lo que piden las ofertas de empleo reales de ese cargo (D26): las que dejó la sincronización semanal o, si no hay, una búsqueda de hasta 6 s. La respuesta trae `contexto_mercado` (`usado`, `ofertas_analizadas`, `fuentes`, `skills_frecuentes`, `fragmentos`, o `motivo` y `mensaje` si no se usaron). D37: las que se parecen (palabras compartidas >= `UMBRAL_SIMILITUD_PREGUNTAS`, 0.80; `0` lo apaga) a una aprobada o pendiente del mismo skill/cargo, tipo, categoría y nivel, o a otra del mismo lote, no se guardan: vienen en `descartadas` (`enunciado` y `similar_a` con `id`, `enunciado`, `estado`, `similitud`) y se cuentan en `preguntas_descartadas`; se cobran igual. Si todas se descartan responde `200` con `preguntas: []` en vez de `201`. El prompt lleva hasta 30 enunciados existentes para que la IA no los repita.

```json
{
  "cargo_id": "{{cargoId}}",
  "nivel": "junior",
  "cantidad": 2,
  "tipo": "abierta_texto",
  "categoria": "tecnica",
  "modelo": "gpt-4o-mini",
  "usar_mercado": true
}
```

### Preguntas sin feedback genérico completo (admin)

`GET /api/v1/admin/preguntas/feedback-generico` · Admin · responde `200`

`pendientes`: preguntas no rechazadas a las que les falta alguno de los tres textos de `rubrica.feedback_generico` (`correcto`, `medio`, `malo`, D36). No llama al LLM.

### Completar feedback genérico con IA (admin)

`POST /api/v1/admin/preguntas/feedback-generico/completar` · Admin · responde `200`

⚠️ Llama al LLM (cuesta). La IA escribe el feedback correcto, medio y malo de las `limite` (1 a 50) preguntas más antiguas que no lo tienen completo, o de las de `pregunta_ids` (hasta 50). Manda 10 por llamada. Solo agrega los niveles que faltan; con `sobrescribir: true` reemplaza los tres. No cambia el estado de la pregunta. Usa el mismo tope mensual de gasto y límite por IP que `generar-ia`, y cada llamada queda en Costos IA. Respuesta: `actualizadas`, `omitidas` (la IA no devolvió los tres textos válidos: siguen pendientes), `pendientes`, `llamadas_ia`, `costo_usd`, `pregunta_ids` y `detenido_por` (código del error que cortó el proceso a mitad de camino; lo anterior quedó guardado). Para todo el banco, repetir hasta `pendientes: 0`. La rúbrica de crear/editar acepta el mismo objeto `feedback_generico` (`400 feedback_generico_invalido`).

```json
{
  "modelo": "gpt-4o-mini",
  "limite": 10,
  "pregunta_ids": [],
  "sobrescribir": false
}
```

### Cobertura del banco frente al mercado (admin)

`GET /api/v1/admin/preguntas/cobertura?cargoId={{cargoId}}&minimo=5` · Admin · responde `200`

Por cada cargo activo (o solo `cargoId`), las skills que pide el mercado con las preguntas aprobadas y pendientes que le sirven, por nivel, y cuántas faltan para llegar a `minimo` aprobadas (1 a 50, por defecto 5). Sirve para generar con IA justo lo que falta (D26). No llama al LLM ni a las APIs de empleo.

### Preguntas para usuarios

`GET /api/v1/preguntas?cargoId={{cargoId}}&cantidad=5` · Usuario · responde `200`

Solo aprobadas y sin la solución.

## 10 · Entrevista

Simulación pregunta a pregunta.

- Una sola entrevista en curso por usuario (`409 entrevista_en_progreso`); una abandonada más de 2 h se cancela sola.
- Preguntas aprobadas del nivel pedido: primero las del cargo, luego las de sus skills, luego generales; 60 % técnicas y 40 % blandas; evita repetir las de las últimas 3 entrevistas.
- Cada pregunta guarda una copia fija (enunciado, tipo, opciones, respuesta ideal y rúbrica): editar o borrar el banco no cambia una entrevista rendida ni su reporte.
- La corrección se muestra recién al terminar. Las entrevistas de otro usuario responden `404`.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/entrevistas` | Usuario | Iniciar entrevista |
| `GET` | `/api/v1/entrevistas/actual` | Usuario | Entrevista en curso |
| `GET` | `/api/v1/entrevistas/{{sesionId}}/siguiente` | Usuario | Siguiente pregunta |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/respuestas` | Usuario | Responder pregunta |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/metricas` | Usuario | Enviar métricas de video |
| `POST` | `/api/v1/vision/predecir` | Usuario | Predecir conductas observables (DivLara E14) |
| `GET` | `/api/v1/vision/reporte/{{sesionId}}` | Usuario | Reporte de la simulación (texto tabulado) |
| `GET` | `/api/v1/entrevistas/{{sesionId}}` | Usuario | Detalle de la entrevista |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/finalizar` | Usuario | Finalizar entrevista |
| `GET` | `/api/v1/entrevistas` | Usuario | Historial de entrevistas |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/cancelar` | Usuario | Cancelar entrevista |

### Iniciar entrevista

`POST /api/v1/entrevistas` · Usuario · responde `201`

Sin `cargoId` acepta `cargo` (nombre) o usa el objetivo del onboarding. `409 entrevista_en_progreso`, `409 preguntas_insuficientes`. La app Android (`POST /api/prueba-practica/front` con `ENT`) reemplaza la entrevista en curso en vez de responder 409, pero solo si la nueva se crea: con `409 preguntas_insuficientes` la anterior sigue en curso.

```json
{
  "cargoId": "{{cargoId}}",
  "nivel": "junior",
  "cantidadPreguntas": 3
}
```

### Entrevista en curso

`GET /api/v1/entrevistas/actual` · Usuario · responde `200`, `204`

### Siguiente pregunta

`GET /api/v1/entrevistas/{{sesionId}}/siguiente` · Usuario · responde `200`, `204`

### Responder pregunta

`POST /api/v1/entrevistas/{{sesionId}}/respuestas` · Usuario · responde `200`

En alternativas se usa `opcionId`; en abiertas `texto` (y `videoClipUrl` https en las de video). Cada pregunta se responde una vez.

```json
{
  "preguntaSesionId": "{{preguntaSesionId}}",
  "opcionId": "{{opcionId}}",
  "texto": "Una corrutina se suspende sin bloquear el hilo."
}
```

### Enviar métricas de video

`POST /api/v1/entrevistas/{{sesionId}}/metricas` · Usuario · responde `201`

Lote de 1 a 300 métricas (puntajes 0-100; un campo ausente = sin evidencia). Idempotente: una métrica por `timestampMs` de la sesión; `insertadas` cuenta solo las nuevas (un lote reenviado responde 0). Sin emociones (RNF10): `expresion` se ignora y de `gestos` solo se guardan conductas observables (toca_cara, manos_visibles, gesticula_manos, movimiento_cabeza, cabeza_inclinada, mirada_fuera, rostro_visible). Cada métrica guarda además `versionModelo` definido por el backend (RNF08; cualquier valor enviado por el cliente se ignora). Contrato del modelo guía DivLara (`divlara-e14-mlp`): ver `models/divlara-e14-mlp/MANIFIESTO.json`.

```json
{
  "metricas": [
    {
      "timestampMs": 0,
      "contactoVisual": 80.5,
      "postura": 70
    },
    {
      "timestampMs": 500,
      "contactoVisual": 78,
      "postura": 72,
      "gestos": {
        "toca_cara": 0,
        "mirada_fuera": 0.25
      }
    },
    {
      "timestampMs": 1000
    }
  ]
}
```

### Predecir conductas observables (DivLara E14)

`POST /api/v1/vision/predecir` · Usuario · responde `200`, `400`, `503`

El backend no carga el modelo: valida sesión y entrada y reenvía la solicitud al **motor de inferencia** (`MOTOR_ENDPOINT`, por defecto `http://localhost:8094/predict`, módulo `motor-vision`) con tiempo máximo de 3 s y 1 reintento; la respuesta llega sin cambios. Modelo guía `divlara-e14-mlp` con ONNX Runtime dentro del motor. Acepta dos formatos excluyentes: (1) **P-1 landmarks** `{ frames: [{ indiceFrame?, timestampMs?, landmarks: [{ id, x, y, z, visibility?, worldX?, worldY?, worldZ? }] }], fps? }` (nominalmente 20 frames @ 10 FPS, también acepta snake_case `frame_index`/`landmark_id`/`world_x`; si falta iris/cara en pose33 imputa con `scaler.mean`) y (2) **v1 compatibilidad** `{ features: [343 floats] }` (orden `feature_columns` de `models/divlara-e14-mlp/meta.json`; longitud distinta → `400 cantidad_features_invalida`). Aplica normalización `(x - mean) / scale` con `scaler.json` e infiere en memoria RAM sin inferir emociones (RNF10 / P-1); los landmarks y el video nunca se guardan. Si se envía `sesionId` (la entrevista propia) y opcionalmente `timestampMs` (ms desde el inicio de la simulación; si falta se usa la hora del servidor), las predicciones 0-8 de ese instante se guardan para el reporte (`GET /api/v1/vision/reporte/{sesion}`); reenviar el mismo instante no duplica. `404 entrevista_no_encontrada` si la sesión no es del usuario, `400 timestamp_invalido`. Responde `{ versionModelo, predicciones, escala: '0-8', mensaje }` (en modo P-1 con `frames` agrega además los campos opcionales `mano_cara_una`, `mano_cara_dos` en `[0.0, 1.0]` y `mano_cara_conteo` en `0|1|2` para desagregar contacto de una mano vs ambas sin modificar `predicciones`). Si el motor responde pero le faltan artefactos (`modelo.onnx`, `scaler.json`, `meta.json`) → `503 modelo_no_disponible`; si el motor no responde, está caído o contesta algo inutilizable → `503 MOTOR_NO_DISPONIBLE` (la app muestra «sin conexión»).

```json
{
  "features": [
    0.0,
    0.0
  ]
}
```

### Reporte de la simulación (texto tabulado)

`GET /api/v1/vision/reporte/{{sesionId}}` · Usuario · responde `200`, `404`

Responde `text/plain` (no JSON): cabecera (sesión, duración, predicciones), **línea de tiempo** de tramos continuos de una misma acción `hh:mm:ss  hh:mm:ss  ACCION  DUR` y **resumen por acción** (veces, total, máximo y promedio por tramo; orden por tiempo total descendente), con columnas de ancho fijo. Una acción es la variable del modelo con mayor valor en ese instante si llega a 4.0 de 8; antes de la primera predicción se muestra `(esperando ventana)` y sin ninguna sobre el umbral `(sin accion reconocida)`. Sin datos: `sin predicciones registradas para esta sesion`. Solo el dueño de la sesión (`404 entrevista_no_encontrada` si no). Las predicciones se guardan con `POST /api/v1/vision/predecir` + `sesionId` y se borran con `POST /api/v1/entrevistas/{id}/borrar`.

### Detalle de la entrevista

`GET /api/v1/entrevistas/{{sesionId}}` · Usuario · responde `200`

La corrección se muestra recién al terminar.

### Finalizar entrevista

`POST /api/v1/entrevistas/{{sesionId}}/finalizar` · Usuario · responde `200`

Requiere al menos una respuesta. Lanza en segundo plano el reporte de feedback.

### Historial de entrevistas

`GET /api/v1/entrevistas?pagina=1&tamano=20` · Usuario · responde `200`

### Cancelar entrevista

`POST /api/v1/entrevistas/{{sesionId}}/cancelar` · Usuario · responde `200`, `409`

Solo una entrevista en curso (`409 entrevista_no_activa` si ya terminó).

## 11 · Feedback

Reporte de la entrevista (se genera en segundo plano al finalizarla) y progreso por skill.

- **Puntaje global**: técnico 50 %, blando 30 %, lenguaje corporal 20 % (promedio de las métricas de video); si algo no se midió, su peso se reparte. Las preguntas sin responder cuentan 0.
- **Respuestas abiertas**: motor freemium (sin costo) para todos; IA para usuarios premium (hasta `LIMITE_EVALUACIONES_IA_MENSUAL` reportes por mes, 30 por defecto) si hay `OPENAI_API_KEY` o `ANTHROPIC_API_KEY`. Si se alcanza el tope o la IA falla se usa el freemium (`modoEvaluacion = freemium`): el reporte siempre sale.
- Si la generación falla, el usuario ve un mensaje claro y `puedeReintentar`; el código del error queda solo en la BD.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/api/v1/entrevistas/{{sesionId}}/reporte` | Usuario | Reporte de la entrevista |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/reporte/reintentar` | Usuario | Reintentar reporte |
| `GET` | `/api/v1/reportes` | Usuario | Mis reportes |
| `GET` | `/api/v1/me/progreso` | Usuario | Mi progreso por skill |

### Reporte de la entrevista

`GET /api/v1/entrevistas/{{sesionId}}/reporte` · Usuario · responde `200`

`estado`: generando | listo | error. Si es `generando`, volver a consultar en unos segundos. `puedeReintentar` indica si sirve reintentar. Un reporte que se cortó por un reinicio del servidor pasa a `error` reintentable (al instante si el apagado fue ordenado; si no, en unos 15 min como máximo).

### Reintentar reporte

`POST /api/v1/entrevistas/{{sesionId}}/reporte/reintentar` · Usuario · responde `202`, `409`

Solo si terminó con error (hasta 3 intentos). `409 reporte_no_reintentable`.

### Mis reportes

`GET /api/v1/reportes` · Usuario · responde `200`

### Mi progreso por skill

`GET /api/v1/me/progreso` · Usuario · responde `200`

## 12 · Práctica

Rondas de preguntas escritas (sin video) con feedback inmediato.

- Alternativas: se corrigen contra la opción correcta y devuelven su explicación. Abiertas: motor freemium; cuentan como correctas desde 60 puntos.
- Una práctica en curso a la vez: empezar otra abandona la anterior.
- Suma puntaje a cada skill en el progreso, pero no cambia su nivel (eso lo decide la nivelación).

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/practicas` | Usuario | Iniciar práctica |
| `POST` | `/api/v1/practicas/{{practicaId}}/respuestas` | Usuario | Responder (feedback inmediato) |
| `GET` | `/api/v1/practicas/{{practicaId}}` | Usuario | Detalle de la práctica |
| `POST` | `/api/v1/practicas/{{practicaId}}/finalizar` | Usuario | Finalizar práctica |
| `GET` | `/api/v1/practicas` | Usuario | Mis prácticas |
| `GET` | `/api/v1/pruebas/historial` | Usuario | Historial de todas las pruebas |
| `POST` | `/api/v1/sync/attempts` | Usuario | Sincronizar intentos offline |
| `GET` | `/api/v1/revision-local/preguntas?cantidad=20` | Usuario | Preguntas abiertas para revisar sin conexión |
| `POST` | `/api/v1/revision-local/respuestas` | Usuario | Subir respuestas revisadas sin conexión |
| `POST` | `/api/v1/llamadas` | Usuario | Guardar una llamada en vivo |
| `GET` | `/api/v1/llamadas?pagina=1&tamano=20` | Usuario | Historial de llamadas |
| `GET` | `/api/v1/llamadas/{{llamadaId}}` | Usuario | Detalle de una llamada |
| `POST` | `/api/v1/practice/evaluate-freemium` | Usuario | Evaluar texto (freemium) |

### Iniciar práctica

`POST /api/v1/practicas` · Usuario · responde `201`

Con `skillId` practica esa skill; si no, el cargo. `modo`: opcion_multiple | abierta_texto | mixto. Empezar otra abandona la anterior.

```json
{
  "cargoId": "{{cargoId}}",
  "modo": "mixto",
  "nivel": "junior",
  "cantidadPreguntas": 3
}
```

### Responder (feedback inmediato)

`POST /api/v1/practicas/{{practicaId}}/respuestas` · Usuario · responde `200`

Devuelve si es correcta, el puntaje (0-100), la opción correcta, la respuesta ideal y el feedback.

```json
{
  "preguntaId": "{{preguntaPracticaId}}",
  "opcionId": "{{opcionPracticaId}}",
  "texto": "Una corrutina se suspende sin bloquear el hilo.",
  "tiempoRespuestaMs": 4000
}
```

### Detalle de la práctica

`GET /api/v1/practicas/{{practicaId}}` · Usuario · responde `200`

### Finalizar práctica

`POST /api/v1/practicas/{{practicaId}}/finalizar` · Usuario · responde `200`

Promedio de lo respondido; suma puntaje a cada skill.

### Mis prácticas

`GET /api/v1/practicas` · Usuario · responde `200`

### Historial de todas las pruebas

`GET /api/v1/pruebas/historial` · Usuario · responde `200`

### Sincronizar intentos offline

`POST /api/v1/sync/attempts` · Usuario · responde `200`, `400`

Idempotente por `localAttemptId`. Requiere un `skillId` existente (si no hay skills, `400 skill_no_encontrada`).

```json
{
  "attempts": [
    {
      "localAttemptId": "local-{{$timestamp}}",
      "skillId": "{{skillId}}",
      "modo": "abierta_texto",
      "categoria": "tecnica",
      "nivelPreguntas": "junior",
      "fechaCreacionIso": "2026-01-15T10:00:00Z",
      "respuestas": [
        {
          "preguntaId": "pregunta-offline-1",
          "enunciado": "¿Qué es Kotlin?",
          "respuestaTexto": "Un lenguaje",
          "esCorrecta": true,
          "puntaje": 8,
          "orden": 1
        }
      ]
    }
  ]
}
```

### Preguntas abiertas para revisar sin conexión

`GET /api/v1/revision-local/preguntas?cantidad=20` · Usuario · responde `200`

T3.13, **sin IA**. Preguntas `abierta_texto` aprobadas, al azar, **con** `respuestaIdeal` y `rubrica` (a diferencia de `GET /api/v1/preguntas`, que no las manda): la app las guarda y revisa el texto en el teléfono sin internet. Filtros `nivel`, `categoria`, `cantidad` (1-50, por defecto 20; `400 cantidad_invalida`). Responde `{ versionRevisor, preguntas: [{ id, tipo, categoria, nivel, enunciado, respuestaIdeal, rubrica }] }`; `versionRevisor` cambia si cambia la fórmula.

### Subir respuestas revisadas sin conexión

`POST /api/v1/revision-local/respuestas` · Usuario · responde `200`, `400`

T3.13, **sin IA**. Solo texto (nunca audio ni video). El servidor repite la revisión léxica con la **misma fórmula** de la app (puntaje 0-100 y banda APROBADA ≥ 70 / CERCANA ≥ 40 / REVISAR) y guarda la suya: `scoreServidor` es el que vale; `coincide` avisa si el puntaje del teléfono (`scoreLocal`/`bandaLocal`, opcionales) es el mismo. Idempotente por `idLocal` y usuario: un reenvío responde `estado: ya_recibida` sin duplicar. Por respuesta `estado` es `guardada`, `ya_recibida` o `rechazada` (la pregunta ya no está disponible: la app no debe reintentar). Hasta 100 por lote; `400` con `lote_vacio`, `lote_demasiado_grande`, `id_local_invalido`, `id_local_repetido`, `pregunta_invalida`, `texto_vacio`, `texto_demasiado_largo` (4000) o `puntaje_invalido`; un lote con algo malformado no guarda nada.

```json
{
  "respuestas": [
    {
      "idLocal": "local-{{$timestamp}}",
      "preguntaId": "{{preguntaId}}",
      "texto": "Uso cache-aside con Redis y un TTL.",
      "scoreLocal": 50.0,
      "bandaLocal": "CERCANA",
      "revisadoEnMs": 1700000000000
    }
  ]
}
```

### Guardar una llamada en vivo

`POST /api/v1/llamadas` · Usuario · responde `200`, `201`

T3.15. Registra una llamada en vivo terminada (la app la guarda en el teléfono y la sube cuando hay red). Idempotente por `idLocal` y usuario: `201` la guarda, `200` si ya estaba. `resumen` es el reporte tabulado de T3.10 (`acciones`: veces/totalMs/maximoMs por acción; `linea`: tramos); si falta y viene `sesionId`, el servidor lo deriva de las predicciones de esa sesión. `resumen.segmentos` (opcional: `idTarget`, `inicioMs`, `finMs`, `valorMax` por evento) solo se usa para calcular el `feedback` y no se guarda. Solo números derivados, nunca landmarks ni video. `400` con `id_local_invalido`, `fecha_invalida`, `duracion_invalida`, `preguntas_invalidas`, `resumen_invalido`, `resumen_demasiado_grande` o `id_invalido`.

```json
{
  "idLocal": "llamada-{{$timestamp}}",
  "fechaInicioMs": 1700000000000,
  "duracionSeg": 120,
  "preguntasCnt": 3,
  "resumen": {
    "acciones": [
      {
        "accion": "head_nodding",
        "veces": 2,
        "totalMs": 9000,
        "maximoMs": 6000
      }
    ],
    "linea": [
      {
        "inicioMs": 0,
        "finMs": 3000,
        "accion": "sin_accion"
      },
      {
        "inicioMs": 3000,
        "finMs": 9000,
        "accion": "head_nodding"
      }
    ]
  }
}
```

### Historial de llamadas

`GET /api/v1/llamadas?pagina=1&tamano=20` · Usuario · responde `200`

T3.15. Llamadas del usuario, la más reciente primero. Responde `{ elementos: [{ id, idLocal, fechaInicioMs, duracionSeg, preguntasCnt, accionesCnt }], total, pagina, tamano }`.

### Detalle de una llamada

`GET /api/v1/llamadas/{{llamadaId}}` · Usuario · responde `200`

T3.15. Agrega `sesionId`, `acciones` (ocurrencias, tiempo total y máximo por pose) y `linea` (línea de tiempo) y, en las guardadas desde el feedback N1, `feedback: { ejes: [{ id, score, segundos, pctLlamada, veredicto }], resumen, peorEje }` (campo agregado: ausente en llamadas antiguas). Ejes `manos_cara`, `mirada`, `presencia_cuerpo`, `apoyo_mesa`; `score = clamp(0..10, 10 − k × tasa)` con `tasa = segundos / duración`; bajo 10 s de llamada `score` y `pctLlamada` no viajan (solo los segundos). `peorEje` (el de menor score bajo 8; ante empate, en ese orden) da 1-2 frases en `resumen`; si no hay ninguno, un mensaje positivo y `peorEje` ausente. Sin IA. `404 llamada_no_encontrada` si no existe o es de otro usuario.

### Evaluar texto (freemium)

`POST /api/v1/practice/evaluate-freemium` · Usuario · responde `200`

```json
{
  "userText": "Un deadlock bloquea procesos",
  "idealText": "Un deadlock bloquea procesos que esperan recursos",
  "expectedKeywords": [
    "deadlock",
    "recursos"
  ]
}
```

## 13 · Nivelación

Test para saber el nivel actual y las brechas contra el cargo.

- **Nivel**: se sube de junior a senior mientras el promedio del nivel llegue a 60; un nivel sin preguntas corta la subida.
- **Brecha**: por cada skill evaluada contra el nivel que pide el cargo; prioridad alta si faltan 2 niveles o la skill es obligatoria.
- No cambia el nivel del perfil: se informa como nivel sugerido.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/nivelacion` | Usuario | Iniciar nivelación |
| `GET` | `/api/v1/nivelacion/{{intentoId}}` | Usuario | Detalle de la nivelación |
| `POST` | `/api/v1/nivelacion/{{intentoId}}/respuestas` | Usuario | Rendir nivelación |
| `GET` | `/api/v1/nivelacion/resultado` | Usuario | Último resultado |
| `GET` | `/api/v1/me/niveles-skill` | Usuario | Mis niveles por skill |

### Iniciar nivelación

`POST /api/v1/nivelacion` · Usuario · responde `201`

Usa el test del admin para el cargo o 3 técnicas por nivel desde el banco. `409 preguntas_insuficientes`.

```json
{
  "cargoId": "{{cargoId}}"
}
```

### Detalle de la nivelación

`GET /api/v1/nivelacion/{{intentoId}}` · Usuario · responde `200`

### Rendir nivelación

`POST /api/v1/nivelacion/{{intentoId}}/respuestas` · Usuario · responde `200`

Se rinde una sola vez. Las no respondidas cuentan 0. Devuelve nivel global, brechas y skills que cumple.

```json
{
  "respuestas": [
    {
      "preguntaId": "{{preguntaNivelacionId}}",
      "opcionId": "{{opcionNivelacionId}}",
      "texto": "Una corrutina se suspende sin bloquear el hilo."
    }
  ]
}
```

### Último resultado

`GET /api/v1/nivelacion/resultado` · Usuario · responde `200`, `204`

### Mis niveles por skill

`GET /api/v1/me/niveles-skill` · Usuario · responde `200`

## 14 · Tests de nivelación (admin)

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/admin/tests-nivelacion` | Admin | Crear test de nivelación |
| `GET` | `/api/v1/admin/tests-nivelacion` | Admin | Listar tests |
| `GET` | `/api/v1/admin/tests-nivelacion/{{testNivelacionId}}` | Admin | Ver test |
| `PUT` | `/api/v1/admin/tests-nivelacion/{{testNivelacionId}}` | Admin | Editar test |
| `DELETE` | `/api/v1/admin/tests-nivelacion/{{testNivelacionId}}` | Admin | Desactivar test |

### Crear test de nivelación

`POST /api/v1/admin/tests-nivelacion` · Admin · responde `201`, `400`

3 a 30 preguntas aprobadas, distintas y sin video (reemplaza los ids de ejemplo). `nivelObjetivo` por defecto `mixto`.

```json
{
  "titulo": "Nivelación Postman {{$timestamp}}",
  "cargoId": "{{cargoId}}",
  "area": "backend",
  "preguntasIds": [
    "{{preguntaId}}",
    "{{preguntaId}}",
    "{{preguntaId}}"
  ]
}
```

### Listar tests

`GET /api/v1/admin/tests-nivelacion?activo=true` · Admin · responde `200`

### Ver test

`GET /api/v1/admin/tests-nivelacion/{{testNivelacionId}}` · Admin · responde `200`

### Editar test

`PUT /api/v1/admin/tests-nivelacion/{{testNivelacionId}}` · Admin · responde `200`

```json
{
  "titulo": "Nivelación Postman (editada)",
  "cargoId": "{{cargoId}}",
  "area": "backend",
  "nivelObjetivo": "mixto",
  "preguntasIds": [
    "<id de pregunta 1>",
    "<id de pregunta 2>",
    "<id de pregunta 3>"
  ]
}
```

### Desactivar test

`DELETE /api/v1/admin/tests-nivelacion/{{testNivelacionId}}` · Admin · responde `200`

Baja lógica. `GET` y `PUT /api/v1/admin/tests-nivelacion/{id}` para ver y editar.

## 15 · App Android (/api/prueba-practica)

Contrato que ya usa la app: crea y rinde cada prueba de una vez.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/prueba-practica/front` | Usuario | Crear práctica (PR) |
| `POST` | `/api/prueba-practica/{{pruebaId}}/respuestas` | Usuario | Enviar respuestas |
| `GET` | `/api/prueba-practica/intentos` | Usuario | Historial de intentos |

### Crear práctica (PR)

`POST /api/prueba-practica/front` · Usuario · responde `201`, `409`

`tipoPrueba`: ENT entrevista · PR práctica técnica · BL práctica blanda · NV nivelación. Busca el cargo por `metaCargo`. Con `ENT` la entrevista en curso se reemplaza (se cancela en la misma transacción que crea la nueva) y con `PR`/`BL` la práctica en curso queda abandonada, pero solo si la nueva se crea: con `409 preguntas_insuficientes` la anterior sigue en curso.

```json
{
  "sector": "backend",
  "nivel": "jr",
  "metaCargo": "{{nombreCargo}}",
  "tipoPrueba": "PR"
}
```

### Enviar respuestas

`POST /api/prueba-practica/{{pruebaId}}/respuestas` · Usuario · responde `200`, `400`, `404`

Acepta `respuestaTexto` (práctica, nivelación) y `respuestaAbierta` (entrevista). Las que vienen en blanco se omiten.

```json
{
  "pruebaId": "{{pruebaId}}",
  "respuestas": [
    {
      "preguntaId": "{{preguntaAppId}}",
      "opcionesSeleccionadas": [
        "{{opcionAppId}}"
      ],
      "respuestaTexto": "Mi respuesta"
    }
  ]
}
```

### Historial de intentos

`GET /api/prueba-practica/intentos` · Usuario · responde `200`

## 16 · Administración de usuarios

Solo admin.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/admin/usuarios` | Admin | Listar usuarios |
| `POST` | `/admin/usuarios` | Admin | Crear usuario |
| `PATCH` | `/admin/usuarios/{{usuarioId}}/rol` | Admin | Cambiar rol |
| `PATCH` | `/admin/usuarios/{{usuarioId}}/password` | Admin | Restablecer contraseña |
| `DELETE` | `/admin/usuarios/{{usuarioId}}` | Admin | Desactivar usuario |
| `PATCH` | `/admin/usuarios/{{usuarioId}}/activar` | Admin | Reactivar usuario |
| `GET` | `/api/v1/admin/costos-ia` | Admin | Costos de IA |

### Listar usuarios

`GET /admin/usuarios` · Admin · responde `200`

### Crear usuario

`POST /admin/usuarios` · Admin · responde `201`

Alias antiguo: `POST /admin/users`.

```json
{
  "correo": "creado.{{$timestamp}}@ejemplo.com",
  "contrasena": "Clave-segura-1",
  "nombre": "Creado por admin",
  "rol": "user"
}
```

### Cambiar rol

`PATCH /admin/usuarios/{{usuarioId}}/rol` · Admin · responde `200`

`user` | `admin`. Un admin no puede quitarse el rol a sí mismo.

```json
{
  "nuevoRol": "user"
}
```

### Restablecer contraseña

`PATCH /admin/usuarios/{{usuarioId}}/password` · Admin · responde `200`

```json
{
  "nuevaContrasena": "Clave-segura-1"
}
```

### Desactivar usuario

`DELETE /admin/usuarios/{{usuarioId}}` · Admin · responde `200`

⚠️ No borra: desactiva y cierra sus sesiones. `PATCH /admin/usuarios/{id}/activar` lo reactiva.

### Reactivar usuario

`PATCH /admin/usuarios/{{usuarioId}}/activar` · Admin · responde `200`

### Costos de IA

`GET /api/v1/admin/costos-ia?desde=2026-11-01&hasta=2026-11-30` · Admin · responde `200`

Gasto en IA por día (UTC) y por modelo: `reportes` (entrevistas evaluadas con IA, premium) y `generacionPreguntas`. Sin fechas: los últimos 30 días; máximo 92 (`400 rango_invalido`); fecha mal escrita: `400 parametro_invalido`. `fallidas` son llamadas que respondieron mal: se pagaron igual y su costo va en `costoEstimadoUsd` (tabla de precios del sistema). Costos en USD con 6 decimales. `topeGeneracionPreguntas` es el gasto del mes UTC en curso (no depende del rango) frente al tope de generación con IA: `mes`, `gastadoUsd`, `topeUsd`, `restanteUsd`, `fechaReposicion` (`null` si está desactivado), `estaDesactivado` y `estaAgotado`; la próxima generación responde `429 tope_generacion_ia_alcanzado` si uno de los dos es `true` (D28).

## 17 · Cierre de sesión

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/auth/logout` | Pública | Cerrar sesión |

### Cerrar sesión

`POST /auth/logout` · Pública · responde `200`

Revoca el refresh token.

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

## 18 · Rutas antiguas (alias)

Rutas que siguen funcionando por compatibilidad con el panel y la app anteriores. Para código nuevo, usar las de las carpetas anteriores.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/market/cargos` | Pública | Cargos (alias) |
| `GET` | `/market/cargos/{{cargoId}}/skills` | Pública | Requisitos del cargo (formato antiguo) |
| `POST` | `/admin/users` | Admin | Crear usuario (alias) |
| `POST` | `/admin/consent/text` | Admin | Publicar texto de consentimiento (alias) |
| `POST` | `/api/v1/admin/questions/generate-ai` | Admin | Generar preguntas con IA (alias) |
| `GET` | `/auth/google/callback` | Pública | Callback de Google (web) |

### Cargos (alias)

`GET /market/cargos` · Pública · responde `200`

Igual que `GET /api/v1/cargos`.

### Requisitos del cargo (formato antiguo)

`GET /market/cargos/{{cargoId}}/skills` · Pública · responde `200`

Solo la lista de requisitos (la matriz completa está en `GET /api/v1/cargos/{id}/skills`).

### Crear usuario (alias)

`POST /admin/users` · Admin · responde `201`

Igual que `POST /admin/usuarios`.

```json
{
  "correo": "alias.{{$timestamp}}@ejemplo.com",
  "contrasena": "Clave-segura-1",
  "rol": "user"
}
```

### Publicar texto de consentimiento (alias)

`POST /admin/consent/text` · Admin · responde `200`

⚠️ Igual que `POST /api/v1/legal/admin/eula`: cambia el texto vigente para todos.

```json
{
  "version": "2.0.0",
  "title": "EULA",
  "body": "Texto"
}
```

### Generar preguntas con IA (alias)

`POST /api/v1/admin/questions/generate-ai` · Admin · responde `200`

⚠️ Igual que `POST /api/v1/admin/preguntas/generar-ia` (llama al LLM), con los mismos dos 429: `tope_generacion_ia_alcanzado` (gasto del mes en el tope) y `demasiadas_solicitudes` (comparte el contador por IP con la ruta nueva).

```json
{
  "cargo_id": "{{cargoId}}",
  "nivel": "junior",
  "cantidad": 2
}
```

### Callback de Google (web)

`GET /auth/google/callback` · Pública · responde `200`

Lo llama Google al terminar el login web; no se usa desde Postman.

## Catálogo de códigos de error

Los 163 códigos que puede devolver el backend en `error`, leídos del código fuente.

| HTTP | Código | Mensaje |
|---|---|---|
| 400 | `alcance_invalido` | Hay alcances con nombre vacío o demasiado largo |
| 400 | `alcances_requeridos` | Indica qué alcances aceptas o rechazas |
| 400 | `alternativas_invalidas` | Las respuestas alternativas son hasta … textos de máximo … caracteres |
| 400 | `area_invalida` | El área es obligatoria y de hasta … caracteres |
| 400 | `cantidad_invalida` | La entrevista debe tener entre … y … preguntas |
| 400 | `cargo_id_invalido` | El id del cargo no es válido |
| 400 | `cargo_invalido` | El cargo admite hasta … caracteres |
| 400 | `cargo_requerido` | Indica el cargo o define tu objetivo en el onboarding |
| 400 | `categoria_invalida` | Categoría debe ser tecnica o blanda |
| 400 | `codigo_invalido` | Código inválido o expirado |
| 400 | `codigo_invalido_o_expirado` | El código no existe, venció o ya se usó |
| 400 | `compra_incompleta` | Faltan el producto o el token de compra |
| 400 | `compra_invalida` | Compra no válida o no activa |
| 400 | `contexto_requerido` | Indica el cargo o la skill que evalúa la pregunta |
| 400 | `contrasena_actual_incorrecta` | La contraseña actual es incorrecta |
| 400 | `contrasena_repetida` | La nueva contraseña debe ser distinta de la actual |
| 400 | `cuenta_google` | Esta cuenta fue creada con Google. No puedes cambiar la contraseña aquí. |
| 400 | `debe_haber_una_correcta` | Debe haber exactamente una opción correcta |
| 400 | `dia_invalido` | Día no válido: … |
| 400 | `dias_invalidos` | days no puede ser negativo |
| 400 | `dias_requeridos` | Elige al menos un día |
| 400 | `duracion_invalida` | duracionSeg debe estar entre 0 y … |
| 400 | `duracion_requerida` | Define days > 0 o expires_at |
| 400 | `enunciado_invalido` | El enunciado es obligatorio (hasta … caracteres) |
| 400 | `enunciado_requerido` | El enunciado es obligatorio |
| 400 | `estado_invalido` | Estado debe ser pendiente, aprobada o rechazada |
| 400 | `etiqueta_invalida` | label no puede superar … caracteres |
| 400 | `expiracion_invalida` | expires_at debe venir en ISO-8601 (ej: 2026-01-31T23:59:59Z) |
| 400 | `extension_invalida` | El archivo debe ser .mp4 |
| 400 | `fecha_invalida` | fechaInicioMs debe ser una fecha válida |
| 400 | `feedback_generico_invalido` | El feedback genérico es un objeto con correcto, medio y malo: textos de hasta … caracteres |
| 400 | `genero_invalido` | Género no válido |
| 400 | `hora_invalida` | La hora debe tener formato HH:mm (00:00 a 23:59) |
| 400 | `id_invalido` | sesionId no es un UUID válido |
| 400 | `id_local_invalido` | idLocal debe tener entre 1 y … caracteres |
| 400 | `id_local_repetido` | Hay intentos repetidos en el lote |
| 400 | `id_requerido` | Falta el parámetro … |
| 400 | `idioma_invalido` | Idioma no soportado |
| 400 | `invalid_uuid` | Identificador con formato inválido |
| 400 | `licencia_invalida` | licenseType debe ser PROM, INST o GOOG |
| 400 | `limite_invalido` | Se pueden completar hasta … preguntas por vez |
| 400 | `lote_demasiado_grande` | Se pueden subir hasta … respuestas por vez |
| 400 | `lote_invalido` | Envía entre 1 y … métricas por solicitud |
| 400 | `lote_vacio` | No hay respuestas que subir |
| 400 | `max_usos_invalido` | max_uses debe ser al menos 1 |
| 400 | `metrica_fuera_de_rango` | Los puntajes de video van de 0 a 100 |
| 400 | `metrica_invalida` | timestampMs no puede ser negativo |
| 400 | `minimo_invalido` | El mínimo de preguntas por nivel debe estar entre 1 y … |
| 400 | `missing_fields` | El título y el cuerpo son obligatorios |
| 400 | `missing_refresh` | Falta el refresh token |
| 400 | `modelo_invalido` | Modelo no soportado: usa … |
| 400 | `modo_invalido` | El modo debe ser opcion_multiple, abierta_texto o mixto |
| 400 | `motivo_invalido` | El motivo no puede superar … caracteres |
| 400 | `motivo_requerido` | Indica por qué se rechaza la pregunta |
| 400 | `must_type_eliminar` | Escribe \"…\" para confirmar |
| 400 | `nivel_experiencia_invalido` | Nivel de experiencia no válido |
| 400 | `nivel_invalido` | El nivel debe ser junior, semisenior o senior |
| 400 | `no_puede_desactivarse` | No puedes desactivar tu propia cuenta |
| 400 | `no_puede_quitarse_admin` | No puedes quitarte el rol de administrador a ti mismo |
| 400 | `nombre_cargo_invalido` | El cargo no puede superar … caracteres |
| 400 | `nombre_cargo_requerido` | El cargo es obligatorio |
| 400 | `nombre_invalido` | El nombre es obligatorio y de hasta … caracteres |
| 400 | `nothing_to_update` | No se envió ningún dato para actualizar |
| 400 | `opcion_duplicada` | Hay opciones repetidas |
| 400 | `opcion_invalida` | Elige una de las opciones de la pregunta |
| 400 | `opcion_vacia` | Todas las opciones necesitan texto |
| 400 | `opciones_invalidas` | Una pregunta de opción múltiple lleva entre … y … opciones |
| 400 | `opciones_no_permitidas` | Solo las preguntas de opción múltiple llevan opciones |
| 400 | `orden_invalido` | El orden de las respuestas debe ser único y desde 1 |
| 400 | `pagina_invalida` | La página empieza en 1 |
| 400 | `palabras_clave_invalidas` | Se admiten hasta … palabras clave |
| 400 | `parametro_invalido` | El parámetro … debe ser un número |
| 400 | `password_too_long` | La contraseña no puede superar … caracteres |
| 400 | `pregunta_invalida` | preguntaId no es un UUID |
| 400 | `pregunta_no_aprobada` | Solo se pueden usar preguntas aprobadas (…) |
| 400 | `pregunta_no_permitida` | La nivelación es escrita: no admite preguntas de video (…) |
| 400 | `pregunta_repetida` | Cada pregunta se responde una sola vez |
| 400 | `pregunta_sesion_id_invalido` | El id de la pregunta no es válido |
| 400 | `preguntas_invalidas` | preguntasCnt debe estar entre 0 y … |
| 400 | `puntaje_invalido` | scoreLocal debe estar entre 0 y 100 |
| 400 | `rango_invalido` | La fecha 'hasta' no puede ser anterior a 'desde' |
| 400 | `respuesta_ideal_o_rubrica_requerida` | Indica una respuesta ideal o una rúbrica para evaluarla |
| 400 | `respuesta_muy_larga` | La respuesta admite hasta … caracteres |
| 400 | `respuesta_requerida` | Escribe tu respuesta |
| 400 | `respuestas_invalidas` | Cada intento lleva entre 1 y … respuestas |
| 400 | `resumen_demasiado_grande` | El resumen no puede superar … elementos |
| 400 | `resumen_invalido` | El resumen trae tiempos o conteos negativos |
| 400 | `rol_invalido` | El rol debe ser 'user' o 'admin' |
| 400 | `sector_invalido` | El sector no puede superar … caracteres |
| 400 | `sin_respuestas` | Responde al menos una pregunta |
| 400 | `sinonimos_invalidos` | Los sinónimos son hasta … grupos de 2 a … términos de máximo … caracteres |
| 400 | `skill_id_invalido` | El id de la skill no es válido |
| 400 | `skill_no_encontrada` | La skill del intento … no existe |
| 400 | `tamano_invalido` | El tamaño de página debe estar entre 1 y … |
| 400 | `telefono_invalido` | El teléfono no tiene un formato válido |
| 400 | `texto_demasiado_largo` | La respuesta no puede superar … caracteres |
| 400 | `texto_muy_largo` | Los textos admiten hasta … caracteres |
| 400 | `texto_vacio` | La respuesta no puede estar vacía |
| 400 | `tiempo_invalido` | El tiempo de respuesta debe estar entre 0 y 1 hora |
| 400 | `timestamp_invalido` | timestampMs debe ser mayor o igual a cero |
| 400 | `tipo_invalido` | Tipo debe ser opcion_multiple, abierta_texto o simulacion_video |
| 400 | `tipo_practica_invalido` | El tipo de práctica es obligatorio y de hasta … caracteres |
| 400 | `tipo_prueba_no_soportado` | El tipo de prueba debe ser ENT (entrevista), PR o BL (práctica) o NV (nivelación) |
| 400 | `titulo_invalido` | El título es obligatorio (hasta … caracteres) |
| 400 | `umbral_invalido` | El umbral debe ser un número entre 0.5 y 1 |
| 400 | `version_invalida` | La versión es obligatoria y de hasta … caracteres |
| 400 | `version_requerida` | La versión es obligatoria |
| 400 | `video_demasiado_grande` | El vídeo supera el máximo de … MB |
| 400 | `video_ilegible` | No se pudo leer el vídeo enviado |
| 400 | `video_requerido` | Falta el campo multipart 'video' |
| 400 | `video_url_invalida` | El clip debe ser una URL https de hasta … caracteres |
| 401 | `bad_credentials` | Correo o contraseña incorrectos |
| 401 | `google_email_not_verified` | El correo de Google no está verificado |
| 401 | `google_exchange_failed` | Google no entregó tokens para el código recibido |
| 401 | `invalid_google_token` | El token de Google no es válido |
| 401 | `invalid_refresh` | El refresh token no es válido o ya expiró |
| 401 | `invalid_token` | El token no trae subject |
| 401 | `missing_id_token` | La respuesta de Google no trae id_token |
| 401 | `unauthorized` | Falta el token de acceso |
| 403 | `inactive_user` | La cuenta no está activa |
| 404 | `cargo_no_encontrado` | El cargo no existe |
| 404 | `cargo_not_found` | No se encontró el cargo especificado |
| 404 | `consentimiento_no_encontrado` | No hay consentimiento vigente |
| 404 | `entrevista_no_encontrada` | La entrevista no existe |
| 404 | `llamada_no_encontrada` | La llamada no existe |
| 404 | `nivelacion_no_encontrada` | El test de nivelación no existe |
| 404 | `objetivo_not_found` | El usuario no tiene un objetivo activo |
| 404 | `onboarding_not_found` | No se encontró información de onboarding |
| 404 | `practica_no_encontrada` | La práctica no existe |
| 404 | `pregunta_no_encontrada` | La pregunta no pertenece a esta entrevista |
| 404 | `profile_not_found` | El usuario aún no tiene perfil |
| 404 | `prueba_no_encontrada` | La prueba no existe |
| 404 | `recordatorio_no_configurado` | El usuario no tiene preferencias de recordatorios configuradas |
| 404 | `reporte_no_disponible` | La entrevista fue cancelada y no tiene reporte |
| 404 | `skill_no_encontrada` | La skill no existe |
| 404 | `test_no_encontrado` | El test de nivelación no existe |
| 404 | `user_not_found` | Usuario no encontrado |
| 404 | `version_no_encontrada` | No existe esa versión del texto legal |
| 409 | `cargo_existente` | Ya existe un cargo con ese nombre |
| 409 | `compra_ya_registrada` | Esta compra ya está asociada a otra cuenta |
| 409 | `email_in_use` | Ese correo ya está registrado |
| 409 | `entrevista_en_curso` | Finaliza o cancela la entrevista antes de borrar sus datos de video |
| 409 | `entrevista_en_progreso` | Ya tienes una entrevista en curso: termínala o cancélala antes de iniciar otra |
| 409 | `entrevista_no_activa` | La entrevista ya fue finalizada o cancelada |
| 409 | `entrevista_no_finalizada` | El reporte estará disponible cuando finalices la entrevista |
| 409 | `nivelacion_finalizada` | Este test de nivelación ya fue respondido |
| 409 | `practica_no_activa` | La práctica ya fue finalizada o abandonada |
| 409 | `pregunta_en_uso` | La pregunta ya se usó; recházala en vez de eliminarla |
| 409 | `pregunta_ya_respondida` | Esa pregunta ya fue respondida |
| 409 | `preguntas_insuficientes` | Aún no hay suficientes preguntas aprobadas para … (…); prueba con otro nivel o cargo |
| 409 | `reintentos_agotados` | Se alcanzó el máximo de … intentos para este reporte |
| 409 | `reporte_no_reintentable` | Solo se puede reintentar un reporte que terminó con error |
| 429 | `demasiados_intentos` | Demasiados intentos. Solicita un nuevo código |
| 429 | `tope_generacion_ia_alcanzado` | Se acabaron los Credits del Token Plan de MiMo de este periodo; se renuevan el … (UTC) |
| 502 | `provider_http_error` | El proveedor de IA no respondió |
| 502 | `provider_invalid_output` | La IA no devolvió feedback válido |
| 502 | `provider_refusal` | El modelo se negó a generar las preguntas |
| 502 | `provider_timeout` | El proveedor de IA tardó demasiado en responder |
| 502 | `provider_truncated` | La respuesta del proveedor de IA quedó incompleta |
| 503 | `documento_no_disponible` | El documento legal no está disponible |
| 503 | `eula_no_disponible` | No hay texto legal publicado ni archivo EULA disponible |
| 503 | `google_no_disponible` | No se pudo validar el token con Google |
| 503 | `provider_not_configured` | No hay API key configurada para ese proveedor de IA |
