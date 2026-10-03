---
tipo: tecnologia
tags: [tecnologia, correo]
version: jakarta.mail 2.0.1
actualizado: 2026-10-03
---

# Correo (SMTP)

**Para qué**: enviar el **código de recuperación de contraseña** (6 dígitos, 15 min).

| | |
|---|---|
| Config | `GMAIL_USER`, `GMAIL_APP_PASSWORD` (contraseña de aplicación de Gmail), `SMTP_HOST`, `SMTP_PORT` |
| Código | `INTEGRACIONES/CLIENTE_CORREO.kt` (`EnviadorCorreo`, `ClienteCorreoSmtp`), `SERVICIOS/SERVICIO_CONTRASENA.kt` |
| Resiliencia | 3 intentos, 30 s, reintenta `MessagingException` |

- El envío corre en segundo plano: `POST /auth/forgot-password` responde de inmediato y **igual exista o no la cuenta**.
- Límite: 5 solicitudes cada 15 min por IP.

> [!danger] Manda correos reales
> No ejecutar `forgot-password` en pruebas automáticas. Las E2E insertan el código directamente en la BD; las pruebas
> unitarias usan `CorreoEnMemoria`.

Relacionado: [[Autenticacion JWT y Google]]
