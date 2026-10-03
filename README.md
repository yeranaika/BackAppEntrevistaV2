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

Toda la documentación de la API está en [`documentacion/`](documentacion/):

- [`documentacion/API.md`](documentacion/API.md): referencia completa (autenticación, formato de error, cada endpoint con su body de ejemplo y el catálogo de códigos de error).
- [`documentacion/postman/`](documentacion/postman/): colección y entorno de Postman, listos para importar. Cómo usarlos: [`documentacion/README.md`](documentacion/README.md).

Ambos se generan desde una sola definición con `python documentacion/GENERAR_DOCUMENTACION.py`, así que no se desincronizan.
El plan de refactorización y otros documentos internos están en [`docs/`](docs/).

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
