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
| `GET` | `/health` | Pública | Healthcheck del servicio (retorna `OK`). |

---

### 1. Autenticación Local & Sesiones (`/auth`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/auth/register` | Pública | Registro de usuario nuevo y perfil opcional. Retorna tokens JWT. | `{"email", "password", "nombre"?, "idioma"?, "telefono"?, "fechaNacimiento"?, "genero"?, "nivelExperiencia"?, "area"?, "pais"?, "notaObjetivos"?}` |
| `POST` | `/auth/login` | Pública | Inicio de sesión con correo y contraseña. Retorna `accessToken` y `refreshToken`. | `{"email", "password"}` |
| `POST` | `/auth/refresh` | Pública | Rotación de token: envía `refreshToken` y recibe un nuevo par de tokens. | `{"refreshToken"}` |
| `POST` | `/auth/logout` | Pública | Cierre de sesión y revocación del `refreshToken`. | `{"refreshToken"}` |
| `POST` | `/auth/request-reset` | Pública | Genera token de reseteo para desarrollo. | `{"email"}` |
| `POST` | `/auth/confirm-reset` | Pública | Confirma reseteo en desarrollo con token y nueva contraseña. | `{"token", "code", "newPassword"}` |

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
| `PUT` | `/recordatorios/preferencias` | `Bearer JWT` | Guarda las preferencias de recordatorios y notificaciones. | `{"diasSemana": ["lunes",...], "hora": "19:00", "tipoPractica": "simulacion_ia", "habilitado": true}` |

---

### 7. Consentimientos Legales (`/consent` y `/me/consent`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/consent/current` | Pública | Obtiene el texto y versión del consentimiento legal vigente. | - |
| `POST` | `/me/consent` | `Bearer JWT` | Registra la aceptación del consentimiento con sus alcances. | `{"version": "v1.0", "alcances": {"uso_datos_sesion": true, ...}}` |
| `GET` | `/me/consent/latest` | `Bearer JWT` | Retorna el último consentimiento activo del usuario. | - |
| `POST` | `/me/consent/revoke` | `Bearer JWT` | Revoca el consentimiento activo del usuario. | - |

---

### 8. Billing y Suscripciones (`/billing`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `GET` | `/billing/status` | `Bearer JWT` | Estado actual de suscripción (es Premium, plan, vencimiento). | - |
| `POST` | `/billing/google/verify` | `Bearer JWT` | Valida y activa compras realizadas vía Google Play Billing. | `{"product_id", "purchase_token", "purchase_time"}` |
| `POST` | `/billing/code/redeem` | `Bearer JWT` | Canjea un código promocional o institucional. | `{"code"}` |
| `POST` | `/billing/admin/codes` | `Bearer JWT (Admin)` | Crea códigos de suscripción (PROM, INST, GOOG). | `{"days", "label"?, "max_uses", "license_type", "expires_at"?}` |

---

### 9. Sincronización Offline y Freemium (`/api/v1`)
| Método | Ruta | Auth | Descripción | Body (JSON) |
|---|---|---|---|---|
| `POST` | `/api/v1/practice/evaluate-freemium` | Pública | Evaluación algorítmica de texto sin consumo de tokens de IA. | `{"preguntaId"?, "userText", "idealText", "expectedKeywords": [...]}` |
| `POST` | `/api/v1/sync/attempts` | `Bearer JWT` | Sincronización por lotes de intentos de práctica realizados offline. | `{"attempts": [{"localAttemptId", "skillId", "modo", "puntajeTotal", "respuestas": [...]}]}` |

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
