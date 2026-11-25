---
tipo: guia
tags: [guia, setup, local]
actualizado: 2026-11-24
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
| `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `MIMO_API_KEY` | No | IA (generación de preguntas y evaluación premium). Basta una; con varias, la evaluación usa OpenAI, luego Anthropic, luego MiMo (D30) |
| `MIMO_BASE_URL` | No | Base de la API de MiMo (por defecto `https://api.xiaomimimo.com/v1`) |
| `MIMO_TOKEN_PLAN_CREDITOS` | No | Credits por periodo del Token Plan de MiMo. Sin definir: 4.100.000.000 (plan Lite) si `MIMO_API_KEY` empieza con `tp-`; 0 lo apaga (D31) |
| `MIMO_TOKEN_PLAN_DIA_RENOVACION` | No | Día del mes (1–28, UTC) en que se renueva el Token Plan; por defecto 1 (D31) |
| `LIMITE_REGISTROS_POR_IP`, `LIMITE_RECUPERACIONES_POR_IP` | No (30 / 5) | Límites por IP (compartidos entre instancias en Redis) |
| `LIMITE_EVALUACIONES_IA_MENSUAL` | No (30) | Reportes con IA por usuario premium y mes (UTC); al llegar al tope el reporte sale con el motor freemium; `0` = sin IA |
| `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` | No (5) | Gasto estimado máximo (USD) en generar preguntas con IA por mes calendario UTC, sumando todos los admins y las llamadas fallidas; al llegar responde `429 tope_generacion_ia_alcanzado`. `0` = generación con IA desactivada; negativo o no numérico cuenta como `0` (D23) |
| `LIMITE_GENERACIONES_IA_POR_IP` | No (30) | Generaciones de preguntas con IA por IP cada 60 min (anti-ráfaga, común a `generar-ia` y su alias) → `429 demasiadas_solicitudes` |
| `APAGADO_ESPERA_TAREAS_SEGUNDOS` | No (150) | Al apagar, espera a los reportes y correos en curso antes de cortarlos (D20) |
| `CABECERA_IP_CLIENTE` | No (vacía) | Cabecera con la IP real del cliente puesta por el proxy. En Railway `X-Real-IP`, **solo tras la prueba de humo** (abajo) |
| `FREEMIUM_HTTP_WARMUP` | No (2000) | Cantidad de solicitudes locales de calentamiento HTTP al arrancar; `0` lo desactiva |
| `HABILITAR_PRUEBAS_VIDEO` | No (false) | `true` monta `POST /pruebas/video` y `GET /pruebas` (calibración del modelo de visión, **sin sesión**, guardan `.mp4` en `pruebas/`). Solo en una máquina propia: en Railway el servidor no arranca con ella activa (D39) |
| `TAREAS_SEGUNDO_PLANO` | No (true) | `false` no arranca la sincronización de mercado, el rescate de reportes ni la purga de consentimientos. **Obligatorio en un backend local conectado a la BD de Railway** (ver abajo). En Railway el servidor no arranca con `false` (D43) |

Si falta una obligatoria, el servidor **no arranca** y dice cuál falta.

## 2. Base de datos
```bash
cd src/DB && docker compose up -d        # primera vez: crea el esquema y carga los seeds
```
Si la BD ya existía de antes, aplicar las migraciones pendientes (son idempotentes):
```bash
for m in migrations/0{14,15,16,17,18,19,20,21,22,23}_*.sql; do docker exec -i Entrevista_APP psql -U root -d DBentrevista -v ON_ERROR_STOP=1 < "$m"; done
```
La **022** (`rubrica_snap`, D24) tiene que estar aplicada **antes** de desplegar el código que la lee, en local y en
Railway: sin ella fallan los endpoints de entrevista y el reporte. **Volver a correrla una vez desplegado**: es
idempotente y completa las copias y trazas que el código anterior dejó mientras convivían.

### Verificar la semilla (D25)
La semilla se puede correr varias veces sin duplicar preguntas ni opciones. `src/DB/PROBAR_SEMILLA.ps1` lo comprueba
en una BD desechable (`semilla_prueba`, que crea y borra; no toca `DBentrevista`): corre la semilla tres veces y
espera 14 preguntas / 24 opciones, sin repetidas, y que una pregunta rechazada o con opciones editadas no se toque.

### Semilla de áreas TI (D44)
`src/DB/BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql` suma 6 áreas (QA/Testing, Ciberseguridad, Diseño UX/UI, Soporte TI y
Redes, Datos/BI y Cloud): 13 skills, 12 cargos con sus skills y 234 preguntas aprobadas (18 por skill nueva, 6 por nivel).
Va **después** de la semilla base (usa sus skills; si falta una, no carga nada) y es re-ejecutable. Docker la carga sola
en una BD nueva (`003-seeds-areas-ti.sql`); en una BD que ya existe:
```powershell
psql -v ON_ERROR_STOP=1 -d DBentrevista -f src/DB/BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql
```
`PROBAR_SEMILLA.ps1` también la corre dos veces y espera 13 skills / 12 cargos / 234 preguntas / 468 opciones.
**Ya aplicada en Railway (producción) el 2026-11-24** con `CARGAR_SQL_EN_RAILWAY.ps1`: pasó de 36 skills / 16 cargos /
514 preguntas a 49 / 28 / 748 (respaldo previo en `~/respaldos_railway/2026-11-24_224038` del PC que la aplicó).
Necesita los binarios de PostgreSQL (`psql`, `createdb`, `dropdb`) y `PGUSER`/`PGPASSWORD` definidos:
```powershell
pwsh src/DB/PROBAR_SEMILLA.ps1 -Bin C:\ruta\pgsql\bin -Puerto 5432   # termina con "Semilla re-ejecutable: todo OK"
```
> [!note] Aún no se ha ejecutado (2026-11-07). Una BD que ya tenga duplicados de la semilla vieja no se limpia sola.

### Usar la BD de Railway desde tu PC (mismo banco de preguntas, D43)
Para que todo el equipo trabaje con el mismo banco, el backend local puede conectarse a la BD de producción de Railway
por **Tailscale**: el servicio `tailscale-rw` del proyecto EntrevistaAPPBack (plantilla «Tailscale Subnet Router») publica
la red privada de Railway en la tailnet del proyecto, así la BD no queda expuesta a internet. Son datos reales: lo que
crees desde tu PC (usuarios, entrevistas) queda en producción.
1. Pide que te inviten a la tailnet del proyecto, instala Tailscale y entra con esa cuenta (en Windows, con «Use
   Tailscale subnets» activo). Prueba: `Test-NetConnection postgres.entrevistaappback-production-railway.internal -Port 5432`
   debe dar `TcpTestSucceeded : True`.
2. En `.env`:
   ```properties
   DB_URL=jdbc:postgresql://postgres.entrevistaappback-production-railway.internal:5432/railway?currentSchema=app
   DB_USER=postgres
   DB_PASS=<PGPASSWORD del servicio Postgres en Railway → Variables; nunca al repo ni al chat>
   TAREAS_SEGUNDO_PLANO=false
   ```
3. `TAREAS_SEGUNDO_PLANO=false` es **obligatorio**: con las tareas encendidas, tu PC rescataría los reportes de usuarios
   reales (con tu configuración, sin IA), llamaría a las APIs de empleo y purgaría consentimientos. Las tareas las corre
   el backend de Railway.
4. Nunca corras migraciones ni `UPDATE`/`DELETE` a mano contra esa BD sin acordarlo con el equipo, y no uses
   `e2e_*` ni `PROBAR_SEMILLA.ps1` contra ella. Para aplicar una semilla o migración acordada:
   `powershell -ExecutionPolicy Bypass -File src/DB/CARGAR_SQL_EN_RAILWAY.ps1 -Archivo <archivo.sql>` (pide la contraseña sin
   mostrarla, respalda el banco en CSV en `~/respaldos_railway`, muestra los conteos y pide escribir `SI`).

Montaje de Tailscale (una vez, ya hecho el 2026-11-24): en la política de la tailnet, `tagOwners` con `tag:railway` y
`autoApprovers` de `fd00::/8` y `10.0.0.0/8` para `tag:railway`; clave de acceso reutilizable y efímera **con la etiqueta
`tag:railway`** en la variable `TAILSCALE_AUTHKEY` de `tailscale-rw` (sin la etiqueta, cada redeploy deja las rutas
pendientes de aprobar a mano en Machines); y en DNS un servidor `100.102.137.20` (la IP de `tailscale-rw` en la tailnet;
el volumen la mantiene entre despliegues) restringido al dominio `entrevistaappback-production-railway.internal`.
En `tailscale-rw` va también `TS_DEBUG_MTU=1200`: entre la PC y Railway se pierden los paquetes del túnel de más de
~1250 bytes (medido el 2026-11-24: un `INSERT` de 1,4 KB cortaba la conexión y el saludo SSL se colgaba); con MTU 1200
el puente pide segmentos TCP más chicos y pasan. Si vuelve a pasar, probar con un `ping -f -l 1220 <IP de tailscale-rw>`.
Antes de invitar al equipo, limitar en la política el acceso a esas rutas al puerto 5432.

## 3. Ejecutar
```bash
./gradlew run                       # http://localhost:8080 ; GET /health → 200 JSON (con modeloDivlara) ; GET /ready → 200 al terminar el calentamiento
```
En Railway, configura el healthcheck del despliegue para usar **`/ready`**. `/health` es solo liveness y responde `200` con `{"status":"OK","modeloDivlara":{"version":"divlara-resnet-v1","cargado":false}}` mientras el proceso esté vivo (incluso si falta el artefacto del modelo o falla su carga). `FREEMIUM_HTTP_WARMUP=0` desactiva el calentamiento y deja `/ready` disponible desde el arranque.

### Modelo de visión DivLara (instalación del artefacto y contrato)
El modelo DivLara se empaqueta **dentro del proyecto del backend** en `models/divlara-resnet-v1/` (nunca en carpetas externas). Al arrancar, `CargadorModeloDivlara` busca una sola vez `models/divlara-resnet-v1/modelo.onnx`:

- **Sin artefacto instalado** (estado mientras R-2 termina el entrenamiento en `training_runs/resnet_v1/`): el cargador queda en `NO_DISPONIBLE`, el backend arranca sin errores y `GET /health` reporta `modeloDivlara.cargado = false`.
- **Instalación al recibir el artefacto de R-2**:
  1. Si R-2 entrega un checkpoint PyTorch (`.pt`/`.pth`), exportarlo a **ONNX** (para usar `onnxruntime` liviano en JVM en T3.1).
  2. Copiar el archivo en `models/divlara-resnet-v1/modelo.onnx` (los binarios están en `.gitignore`, no se suben a Git).
  3. Actualizar `models/divlara-resnet-v1/MANIFIESTO.json` con `sha256`, `tamanoEsperadoBytes`, dimensiones `T`/`N` y **CRÍTICO**: la `media` y `std` exactas usadas en el entrenamiento de DivLARA.
  4. Reiniciar el backend y verificar con `curl http://localhost:8080/health` que `modeloDivlara.cargado` sea `true` (sin campo `error`).
- **Contrato de inferencia** (detalle completo en `models/divlara-resnet-v1/MANIFIESTO.json` y `models/divlara-resnet-v1/README.md`):
  - **Entrada**: tensor `float32` de landmarks faciales MediaPipe por ventana temporal (~500 ms) con forma `[T, N, 3]`, orden canónico `0..N-1` y coordenadas `(x, y)` normalizadas `[0, 1]` + `z` relativa al ancho; procesamiento efímero en RAM (0 días, sin video crudo, P-1 / P-4).
  - **Normalización**: z-score `(x - media) / (std + eps)` con parámetros exactos del entrenamiento.
  - **Salidas**: métricas observables sin emociones (`RNF10` / `D22`) etiquetadas con `versionModelo = divlara-resnet-v1` (`RNF08`): `contactoVisual` (`0..100`), `postura` (`0..100`), `confianza` (`0..100`, nullable) y `gestos` restringidos a `GESTOS_OBSERVABLES` (`toca_cara`, `manos_visibles`, `gesticula_manos`, `movimiento_cabeza`, `cabeza_inclinada`, `mirada_fuera`, `rostro_visible`).

### Calibrar el modelo de visión con video (`/pruebas/video`, D39)
1. En el backend de pruebas (por ejemplo el del puerto 8093, nunca el 8080 del desarrollador ni Railway) poner
   `HABILITAR_PRUEBAS_VIDEO=true` y arrancar: el log avisa que la ruta está abierta sin sesión.
2. Compilar la app con `-PgrabarPrueba=true -PbackendHost=<ip> -PbackendPort=8093` y hacer la llamada de prueba.
3. Con `GET /pruebas` en ese puerto, comprobar que el `.mp4` llegó a `pruebas/`.
4. Al terminar: quitar la variable (o dejarla en `false`), reiniciar y borrar los videos de `pruebas/` que ya no sirvan.

### Apagado ordenado en Railway (D20)
- `railway.json` fija `deploy.drainingSeconds: 200`: Railway espera hasta 200 s entre SIGTERM y SIGKILL (por defecto da 0).
  La espera de tareas (150 s) más los cierres caben con margen. Si se cambia una, revisar la otra.
- El proceso de **Java tiene que recibir la señal**: el comando de inicio debe ejecutar java directamente (`java -jar …`
  o el script de `installDist`, que termina en `exec`), nunca un `sh -c` que no la reenvíe.
- Aunque el proceso muera sin aviso, nada queda colgado: al arrancar y cada 5 min los reportes interrumpidos pasan a
  `error` reintentable.

### Prueba de humo antes de activar `CABECERA_IP_CLIENTE` (Railway, staging)
Comprueba que el proxy **sobrescribe** `X-Real-IP` (si no, cualquiera podría inventarse una IP y saltarse el límite, D19):
1. Con `CABECERA_IP_CLIENTE=X-Real-IP`, enviar 31 `POST /auth/register` con cuerpo inválido (responde 422: no crea
   datos ni manda correos; el límite corre antes y cuenta igual), cada uno con un `X-Real-IP` falso distinto.
2. Si la 31.ª responde **429**, el proxy reemplaza la cabecera: es seguro dejarla. Si no, **volver a dejarla vacía**.
3. Desbloquear la IP al terminar: `redis-cli --scan --pattern 'limite:*'` y `DEL` de esa clave.

## 4. Probar
```bash
./gradlew test                                                       # ~360 pruebas
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
| `503 provider_not_configured` | Falta la clave del proveedor del modelo pedido (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY` o `MIMO_API_KEY`) |
| `429 tope_generacion_ia_alcanzado` | Se llegó a `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` en el mes (UTC) o vale `0`; se repone el día 1 |
| Columna inexistente | Falta aplicar una migración |

Relacionado: [[Kotlin y Ktor]] · [[Pruebas]] · [[Docker]]
