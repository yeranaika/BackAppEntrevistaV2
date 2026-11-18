---
tipo: tecnologia
tags: [tecnologia, pagos, google-play, premium]
actualizado: 2026-11-17
---

# Google Play Billing y códigos premium

**Para qué**: activar **premium** (hoy: evaluación con IA del reporte de entrevista). Ver [[Usuarios y modelo de negocio]].

| | |
|---|---|
| Endpoints | `GET /billing/status`, `POST /billing/google/verify`, `POST /billing/code/redeem`, `POST /billing/admin/codes` (JSON **snake_case**, contrato de Android) |
| Config | `GOOGLE_PLAY_PACKAGE`, `GOOGLE_PLAY_SERVICE_JSON_B64` (cuenta de servicio en base64), `GOOGLE_PLAY_BILLING_MOCK` |
| Código | `INTEGRACIONES/CLIENTE_GOOGLE_PLAY.kt`, `SERVICIOS/SERVICIO_SUSCRIPCION.kt`, `MODELOS/REPOSITORIO_SUSCRIPCION.kt` |

## Reglas
- `GOOGLE_PLAY_BILLING_MOCK=true` → `VerificadorCompraSimulado` (toda compra válida por 30 días). **Usar en desarrollo.**
- Se guarda solo el **SHA-256 del token** con índice único: el mismo pago en otra cuenta → `409 compra_ya_registrada`.
- **La cuenta que pagó** ([[Decisiones]] D27): la app abre la compra con `obfuscatedAccountId` = SHA-256 en hex
  minúscula del `id` de `/me` (UUID canónico, UTF-8). El backend lee `obfuscatedExternalAccountId` de la respuesta de
  Google y, si no es el hash de la cuenta que pide activar (sin distinguir mayúsculas), responde `409
  compra_ya_registrada` con el mismo mensaje. Una compra sin ese campo (desde la tienda de Play, código promocional, app
  antigua o simulada) se acepta y decide solo el dueño del token. La fórmula del hash es contrato con la app: cambiarla
  en un solo lado deja sin premium a quien pagó. La cuenta ofuscada no se guarda ni va al log.
- Google responde 4xx → compra inválida (`400`); 429/5xx → reintento y luego `503` (nunca "compra inválida").
- **Códigos**: tipos `PROM`, `INST`, `GOOG`; formato `PROM-XXXXXXXX` (alfabeto sin caracteres ambiguos). El canje es
  **atómico** (UPDATE condicional): nunca supera `max_uses`; los días se suman al premium vigente.
- El estado elige la **mejor suscripción activa**, no la última.
- **Cupo de reportes con IA** ([[Decisiones]] D29): a un premium con IA activa, `GET /billing/status` y el canje le
  suman `reportes_ia_limite`, `reportes_ia_restantes` y `reportes_ia_renovacion` (ms epoch, inicio del mes siguiente
  UTC), contados con la misma condición que la reserva del reporte. A un usuario gratis no le llegan.
- Se lee `purchases.subscriptions.get` (v3, que Google marca obsoleta); al migrar a `subscriptionsv2` la cuenta que
  pagó está en `externalAccountIdentifiers.obfuscatedExternalAccountId` ([[Pendientes y deuda tecnica]]).
- La app pide la verificación; el backend **decide** premium (`VerificadorPremium` en el reporte).

Relacionado: [[Seguridad]]
