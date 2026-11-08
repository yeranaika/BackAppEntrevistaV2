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

## 📖 Documentación de la API y Postman

Toda la documentación de la API está en [`docs/documentacion/`](docs/documentacion/):

- [`docs/documentacion/API.md`](docs/documentacion/API.md): referencia completa (autenticación, formato de error, cada endpoint con su body de ejemplo y el catálogo de códigos de error).
- [`docs/documentacion/postman/`](docs/documentacion/postman/): colección y entorno de Postman, listos para importar. Cómo usarlos: [`docs/documentacion/README.md`](docs/documentacion/README.md).

Ambos se generan desde una sola definición con `python docs/documentacion/GENERAR_DOCUMENTACION.py`, así que no se desincronizan.
**Contexto del proyecto (para devs y agentes):** [`docs/`](docs/) es una bóveda de Obsidian con la visión, el alcance,
la arquitectura, una nota por tecnología, guías (puesta en marcha, convenciones, cómo agregar un endpoint), el estado y las
decisiones. Empieza por [`docs/00 Inicio.md`](docs/00%20Inicio.md). Los agentes de IA leen además [`AGENTS.md`](AGENTS.md).

---

## ⚙️ Variables de entorno de integraciones
| Variable | Uso |
|---|---|
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Caché y bloqueo de login. Si Redis cae la app sigue funcionando (sin caché). |
| `JSEARCH_API_HOST`, `JSEARCH_API_KEY` | API de empleo principal; sin ella se usan las fuentes gratuitas. |
| `GOOGLE_PLAY_PACKAGE`, `GOOGLE_PLAY_SERVICE_JSON_B64` | Verificación real de compras (se validan al arrancar). |
| `GOOGLE_PLAY_BILLING_MOCK` | `true` para simular compras válidas por 30 días (desarrollo). |
| `LIMITE_REGISTROS_POR_IP`, `LIMITE_RECUPERACIONES_POR_IP` | Cambian los límites por IP (por defecto 30 y 5). Se comparten entre instancias en Redis. |
| `LIMITE_EVALUACIONES_IA_MENSUAL` | Reportes con IA por usuario premium y mes calendario UTC (por defecto 30; 0 = sin IA). Al llegar al tope el reporte sale con el motor freemium. |
| `APAGADO_ESPERA_TAREAS_SEGUNDOS` | Al apagar, cuánto se espera a los reportes y correos en curso (por defecto 150; máx. 600). Debe caber en el drenado de Railway (`railway.json`: 200 s). |
| `CABECERA_IP_CLIENTE` | Cabecera con la IP real del cliente puesta por el proxy (en Railway `X-Real-IP`, tras la prueba de humo). Vacía = IP de la conexión. |
| `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` | Gasto estimado máximo (USD) en generar preguntas con IA por mes calendario UTC, sumando todos los admins y las llamadas fallidas (por defecto 5; 0 = generación con IA desactivada; negativo o no numérico = 0). Al llegar: `429 tope_generacion_ia_alcanzado` (D23). |
| `LIMITE_GENERACIONES_IA_POR_IP` | Generaciones de preguntas con IA por IP cada 60 min (por defecto 30), común a `generar-ia` y su alias. Al exceder: `429 demasiadas_solicitudes`. |

**Migraciones:** el servidor ya no crea ni altera tablas al arrancar. Aplicar `migrations/014` a `022` (idempotentes) sobre una BD existente.
La **022** (`rubrica_snap`, D24) debe estar aplicada **antes** de desplegar este código (si no, fallan la entrevista y el reporte), y conviene volver a correrla una vez desplegado (es idempotente):
```powershell
Get-Content migrations/015_alinear_esquema.sql | docker exec -i Entrevista_APP psql -U root -d DBentrevista
Get-Content migrations/022_rubrica_snap_y_traza_editada.sql | docker exec -i Entrevista_APP psql -U root -d DBentrevista -v ON_ERROR_STOP=1
```

**Semilla re-ejecutable (D25):** `src/DB/BasedeDatos.EntrevistaApp.Seeds.sql` se puede volver a correr sin duplicar
preguntas ni opciones. Para comprobarlo en una BD desechable (`semilla_prueba`, no toca `DBentrevista`), con los
binarios de PostgreSQL y `PGUSER`/`PGPASSWORD` definidos:
```powershell
pwsh src/DB/PROBAR_SEMILLA.ps1 -Bin C:\ruta\pgsql\bin -Puerto 5432
```
Aún no se ha ejecutado. Una BD que ya tenga duplicados de la semilla vieja no se limpia sola.
