---
tipo: guia
tags: [guia, setup, local]
actualizado: 2026-10-03
---

# Puesta en marcha

## Requisitos
- **JDK 21** (Gradle descarga el resto con el wrapper).
- **Docker Desktop** (PostgreSQL 16 y Redis 7) → [[Docker]].
- **PowerShell 7** (`pwsh`) para las E2E · **Python 3** para generar la documentación · **Postman** para explorar la API.

## 1. Variables de entorno
Copiar `.env.example` a `.env` (está en `.gitignore`; **nunca** subirlo) y completar:

| Variable | Obligatoria | Para qué |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | Sí | PostgreSQL. Local: `jdbc:postgresql://localhost:5432/DBentrevista?currentSchema=app`, `root`/`root` |
| `DB_POOL_MAXIMO` | No (10) | Conexiones del pool |
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` | Sí | Firma de tokens |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Sí | Login con Google |
| `GOOGLE_PLAY_PACKAGE`, `GOOGLE_PLAY_SERVICE_JSON_B64` | Sí | Verificación de compras |
| `GOOGLE_PLAY_BILLING_MOCK` | No | `true` en desarrollo (simula compras) |
| `GMAIL_USER`, `GMAIL_APP_PASSWORD`, `SMTP_HOST`, `SMTP_PORT` | Sí (correo) | Recuperación de contraseña |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | No | Caché (por defecto `localhost:6379`) |
| `JSEARCH_API_HOST`, `JSEARCH_API_KEY` | No | API de empleo principal |
| `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` | No | IA (generación de preguntas y evaluación premium) |
| `LIMITE_REGISTROS_POR_IP`, `LIMITE_RECUPERACIONES_POR_IP` | No (30 / 5) | Límites por IP |

Si falta una obligatoria, el servidor **no arranca** y dice cuál falta.

## 2. Base de datos
```bash
cd src/DB && docker compose up -d        # primera vez: crea el esquema y carga los seeds
```
Si la BD ya existía de antes, aplicar las migraciones pendientes (son idempotentes):
```bash
for m in migrations/01{4,5,6,7,8}_*.sql; do docker exec -i Entrevista_APP psql -U root -d DBentrevista -v ON_ERROR_STOP=1 < "$m"; done
```

## 3. Ejecutar
```bash
./gradlew run                       # http://localhost:8080 ; GET /health → OK
```

## 4. Probar
```bash
./gradlew test                                                       # ~260 pruebas
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_7_FEEDBACK.ps1 -UrlBase http://127.0.0.1:8080
```
Y en Postman: importar `docs/documentacion/postman/` → [[Postman y documentacion de la API]].

## Primer usuario admin
Registrarse (Postman *Registrar usuario* o la app) y luego:
```bash
docker exec Entrevista_APP psql -U root -d DBentrevista -c "update app.usuario set rol='admin' where correo='tu@correo.com'"
```

## Problemas frecuentes
| Síntoma | Causa |
|---|---|
| `db_no_disponible` / no arranca | Docker Desktop cerrado o contenedor caído |
| `NoSuchMethodError` en Ktor | Dependencias Ktor con versión dinámica → fijarlas en 3.3.1 |
| `409 preguntas_insuficientes` | El banco no tiene preguntas aprobadas para ese cargo/nivel: crearlas (admin) |
| `503 provider_not_configured` | Falta `OPENAI_API_KEY`/`ANTHROPIC_API_KEY` |
| Columna inexistente | Falta aplicar una migración |

Relacionado: [[Kotlin y Ktor]] · [[Pruebas]] · [[Docker]]
