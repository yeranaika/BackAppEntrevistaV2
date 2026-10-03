---
tipo: tecnologia
tags: [tecnologia, autenticacion, jwt, google, argon2]
version: java-jwt 4.4.0 · argon2-jvm 2.11 · google-api-client 2.x
actualizado: 2026-10-03
---

# Autenticación: JWT, Argon2id y Google

**Para qué**: identificar al usuario en cada petición sin sesiones de servidor, con contraseñas bien protegidas y login con Google.

| Pieza | Detalle | Código |
|---|---|---|
| Access token | JWT HS256, **15 min**, `sub` = usuarioId, claim de rol | `UTILIDADES/UTILIDAD_JWT.kt`, `SERVICIOS/SERVICIO_TOKEN.kt` |
| Refresh token | Opaco, **15 días**, guardado como hash; rotación atómica | `MODELOS/REPOSITORIO_REFRESH_TOKEN.kt` |
| Contraseñas | **Argon2id** | `UTILIDADES/UTILIDAD_CONTRASENA.kt` |
| Google (Android) | `POST /auth/google` con `idToken`, verificado contra `GOOGLE_CLIENT_ID` | `INTEGRACIONES/CLIENTE_GOOGLE_IDENTIDAD.kt` |
| Google (web) | OAuth de Ktor: `/auth/google/start` → `/auth/google/callback` | `MIDDLEWARES/MIDDLEWARE_AUTENTICACION.kt` |
| Admin | `soloAdmin { }` | `MIDDLEWARES/MIDDLEWARE_SOLO_ADMIN.kt` |

## Variables
`JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`.

## Reglas
- Reusar un refresh token ya rotado **revoca todas** las sesiones del usuario (posible robo).
- Cambiar o restablecer la contraseña cierra las demás sesiones.
- En un controlador, el usuario se obtiene con `call.usuarioIdDesdeJwt()`.
- Para dar rol admin en local: `update app.usuario set rol = 'admin' where correo = '…'` o desde `/admin/usuarios/{id}/rol`.

Relacionado: [[Seguridad]] · [[Correo SMTP]]
