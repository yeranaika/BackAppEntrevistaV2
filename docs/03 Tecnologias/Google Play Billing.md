---
tipo: tecnologia
tags: [tecnologia, pagos, google-play, premium]
actualizado: 2026-10-03
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
- Google responde 4xx → compra inválida (`400`); 429/5xx → reintento y luego `503` (nunca "compra inválida").
- **Códigos**: tipos `PROM`, `INST`, `GOOG`; formato `PROM-XXXXXXXX` (alfabeto sin caracteres ambiguos). El canje es
  **atómico** (UPDATE condicional): nunca supera `max_uses`; los días se suman al premium vigente.
- El estado elige la **mejor suscripción activa**, no la última.
- La app pide la verificación; el backend **decide** premium (`VerificadorPremium` en el reporte).

Relacionado: [[Seguridad]]
