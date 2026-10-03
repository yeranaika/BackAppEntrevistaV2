# API de EntrevistaAPP

> Archivo generado por `documentacion/GENERAR_DOCUMENTACION.py`. No editar a mano: cambiar la definición y volver a generar.

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
| 429 | Límite superado: `demasiadas_solicitudes` (por IP) o `demasiados_intentos` (login) |
| 500 | Error inesperado (`error_interno`); el detalle queda en el log, nunca en la respuesta |
| 502 | Un proveedor externo respondió algo inutilizable (ej: el LLM) |
| 503 | Un proveedor externo o la base de datos no está disponible |

### Convenciones
- **Fechas**: ISO-8601 en UTC (`2026-10-03T14:05:00Z`); fechas sin hora `YYYY-MM-DD`.
- **Ids**: UUID. Un id mal formado en la ruta responde `400 id_invalido`.
- **Niveles**: `junior | semisenior | senior` (también se aceptan `jr | mid | sr`).
- **Paginación**: `?pagina=1&tamano=20` donde aplica (tamaño máximo 100).
- **Límites por IP**: registro 30 cada 10 min; recuperación de contraseña 5 cada 15 min. **Login**: 5 contraseñas
  incorrectas bloquean el correo 15 min.
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

### Salud

`GET /health` · Pública · responde `200`

Responde `OK` si la base de datos responde y `503 db_no_disponible` si no. Lo usa el supervisor / balanceador.

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
Errores con el formato antiguo que lee Android: `409 {"error":"email_in_use"}`, `422 {"error":"invalid_country"|"invalid_birthdate"}`.
Límite: 30 registros cada 10 minutos por IP → `429 demasiadas_solicitudes`.

```json
{
  "email": "{{correo}}",
  "password": "{{contrasena}}",
  "nombre": "Usuario Prueba",
  "nivelExperiencia": "junior",
  "area": "backend"
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

```json
{
  "nivelExperiencia": "junior",
  "area": "backend",
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

Área, cargo meta y nivel (acepta `jr|mid|sr` o `junior|semisenior|senior`).

```json
{
  "area": "backend",
  "metaCargo": "Backend Developer",
  "nivel": "jr"
}
```

### Onboarding

`POST /onboarding` · Usuario · responde `200`

```json
{
  "area": "backend",
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

Atómico: nunca supera `max_uses`. `400 codigo_invalido_o_expirado`.

```json
{
  "code": "{{codigoPremium}}"
}
```

### Verificar compra de Google Play

`POST /billing/google/verify` · Usuario · responde `200`

Un token pertenece a una sola cuenta (`409 compra_ya_registrada`). Google caído → `503`. Con `GOOGLE_PLAY_BILLING_MOCK=true` se simula.

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
| `PATCH` | `/api/v1/admin/preguntas/{{preguntaId}}/aprobar` | Admin | Aprobar pregunta (admin) |
| `PUT` | `/api/v1/admin/preguntas/{{preguntaId}}` | Admin | Editar pregunta (admin) |
| `DELETE` | `/api/v1/admin/preguntas/{{preguntaId}}` | Admin | Borrar pregunta (admin) |
| `PATCH` | `/api/v1/admin/preguntas/{{preguntaId}}/rechazar` | Admin | Rechazar pregunta (admin) |
| `POST` | `/api/v1/admin/preguntas/generar-ia` | Admin | Generar preguntas con IA (admin) |
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

Filtros: estado, tipo, categoria, nivel, skillId, cargoId, generadaPorIa, pagina, tamano.

### Ver pregunta (admin)

`GET /api/v1/admin/preguntas/{{preguntaId}}` · Admin · responde `200`

### Aprobar pregunta (admin)

`PATCH /api/v1/admin/preguntas/{{preguntaId}}/aprobar` · Admin · responde `200`

### Editar pregunta (admin)

`PUT /api/v1/admin/preguntas/{{preguntaId}}` · Admin · responde `200`

Reemplaza el contenido; la pregunta vuelve a quedar pendiente de revisión.

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

⚠️ Llama al LLM (cuesta). Las preguntas nacen pendientes de revisión. Máximo 10.

```json
{
  "cargo_id": "{{cargoId}}",
  "nivel": "junior",
  "cantidad": 2,
  "tipo": "abierta_texto",
  "categoria": "tecnica",
  "modelo": "gpt-4o-mini"
}
```

### Preguntas para usuarios

`GET /api/v1/preguntas?cargoId={{cargoId}}&cantidad=5` · Usuario · responde `200`

Solo aprobadas y sin la solución.

## 10 · Entrevista

Simulación pregunta a pregunta.

- Una sola entrevista en curso por usuario (`409 entrevista_en_progreso`); una abandonada más de 2 h se cancela sola.
- Preguntas aprobadas del nivel pedido: primero las del cargo, luego las de sus skills, luego generales; 60 % técnicas y 40 % blandas; evita repetir las de las últimas 3 entrevistas.
- Cada pregunta guarda una copia fija (enunciado, tipo, opciones): editar o borrar el banco no cambia una entrevista rendida.
- La corrección se muestra recién al terminar. Las entrevistas de otro usuario responden `404`.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `POST` | `/api/v1/entrevistas` | Usuario | Iniciar entrevista |
| `GET` | `/api/v1/entrevistas/actual` | Usuario | Entrevista en curso |
| `GET` | `/api/v1/entrevistas/{{sesionId}}/siguiente` | Usuario | Siguiente pregunta |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/respuestas` | Usuario | Responder pregunta |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/metricas` | Usuario | Enviar métricas de video |
| `GET` | `/api/v1/entrevistas/{{sesionId}}` | Usuario | Detalle de la entrevista |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/finalizar` | Usuario | Finalizar entrevista |
| `GET` | `/api/v1/entrevistas` | Usuario | Historial de entrevistas |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/cancelar` | Usuario | Cancelar entrevista |

### Iniciar entrevista

`POST /api/v1/entrevistas` · Usuario · responde `201`

Sin `cargoId` acepta `cargo` (nombre) o usa el objetivo del onboarding. `409 entrevista_en_progreso`, `409 preguntas_insuficientes`.

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

Lote de 1 a 300 métricas (puntajes 0-100; expresión seguro|nervioso|distraido|neutral|confuso).

```json
{
  "metricas": [
    {
      "timestampMs": 0,
      "contactoVisual": 80.5,
      "postura": 70,
      "confianza": 65,
      "expresion": "seguro"
    },
    {
      "timestampMs": 500,
      "contactoVisual": 78,
      "postura": 72,
      "confianza": 66,
      "gestos": {
        "toca_cara": false
      },
      "expresion": "neutral"
    }
  ]
}
```

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
- **Respuestas abiertas**: motor freemium (sin costo) para todos; IA para usuarios premium si hay `OPENAI_API_KEY` o `ANTHROPIC_API_KEY`. Si la IA falla se usa el freemium: el reporte siempre sale.
- Si la generación falla, el usuario ve un mensaje claro y `puedeReintentar`; el código del error queda solo en la BD.

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/api/v1/entrevistas/{{sesionId}}/reporte` | Usuario | Reporte de la entrevista |
| `POST` | `/api/v1/entrevistas/{{sesionId}}/reporte/reintentar` | Usuario | Reintentar reporte |
| `GET` | `/api/v1/reportes` | Usuario | Mis reportes |
| `GET` | `/api/v1/me/progreso` | Usuario | Mi progreso por skill |

### Reporte de la entrevista

`GET /api/v1/entrevistas/{{sesionId}}/reporte` · Usuario · responde `200`

`estado`: generando | listo | error. Si es `generando`, volver a consultar en unos segundos. `puedeReintentar` indica si sirve reintentar.

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

`tipoPrueba`: ENT entrevista · PR práctica técnica · BL práctica blanda · NV nivelación. Busca el cargo por `metaCargo`.

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

⚠️ Igual que `POST /api/v1/admin/preguntas/generar-ia` (llama al LLM).

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

Los 0 códigos que puede devolver el backend en `error`, leídos del código fuente.

| HTTP | Código | Mensaje |
|---|---|---|
