---
tipo: arquitectura
tags: [seguridad, privacidad]
actualizado: 2026-11-17
---

# Seguridad

| Tema | Cómo está resuelto |
|---|---|
| Contraseñas | **Argon2id** (mínimo 8 caracteres). No existe ningún camino con contraseña en claro |
| Sesión | JWT de acceso (15 min) + refresh token (15 días) guardado como **hash**; rotación atómica y detección de reutilización (revoca todo) |
| Roles | `user` / `admin`; `soloAdmin { }` responde 401 sin token y 403 sin rol. El rol se relee al rotar tokens |
| Recursos ajenos | Responden **404** (no se revela que existen) |
| Login | 5 contraseñas incorrectas bloquean el correo 15 min (contador en Redis, compartido entre instancias) |
| Límite por IP | Registro 30/10 min, recuperación 5/15 min, **compartido entre instancias en Redis** (D18); si Redis cae, cada instancia limita en memoria. La IP es la de la conexión, o la de `CABECERA_IP_CLIENTE` (X-Real-IP en Railway) solo si es una IP literal válida; nunca `X-Forwarded-For`, cuyo primer valor controla el cliente (D19) |
| Recuperación | Código de 6 dígitos, 15 min, intentos limitados; misma respuesta exista o no la cuenta |
| Pagos | Solo se guarda el **hash** del token de Google Play; un pago no activa dos cuentas, y una compra que Google atribuye a otra cuenta de la app (`obfuscatedExternalAccountId` ≠ hash de la cuenta) responde 409 (D27). No se leen ni guardan los datos personales que trae la respuesta de Google |
| IA | Las respuestas del candidato van como **datos** en el mensaje de usuario, nunca en las instrucciones (limita inyección de prompts). Modelo, tokens y costo no se exponen |
| Errores | Al cliente solo código + mensaje; trazas y detalles al log |
| Secretos | Solo en `.env` / variables de entorno (ignorado por git). La config falla al arrancar si falta uno obligatorio |
| Datos personales | Ley 19.628 (Chile), modificada por la Ley 21.719. `DELETE /cuenta` borra todo (confirmación explícita) salvo el registro de consentimiento sin IP, que se guarda 5 años desde el borrado y luego se purga a diario (D38). Consentimiento versionado: solo se guardan los alcances que la versión ofrece y una versión ya aceptada no se puede cambiar (D35) |
| Rutas de calibración | `POST /pruebas/video` y `GET /pruebas` no tienen sesión y guardan video en disco: solo se montan con `HABILITAR_PRUEBAS_VIDEO=true`, apagada por defecto y nunca en Railway (D39) |
| Transporte | TLS lo provee el despliegue (proxy / plataforma); el backend escucha HTTP en 8080 |

> [!warning] Pendiente de confirmar
> El envío de respuestas de usuarios premium a OpenAI / Anthropic debe estar cubierto por el EULA y la política de privacidad.

Relacionado: [[Autenticacion JWT y Google]] · [[Manejo de errores y resiliencia]]
