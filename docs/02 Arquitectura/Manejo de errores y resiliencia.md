---
tipo: arquitectura
tags: [errores, resiliencia, cache]
actualizado: 2026-10-03
---

# Manejo de errores y resiliencia

Sigue la skill del equipo `manejo-errores` (`.claude/skills/manejo-errores` en `proyectos_internos`).

## Errores de dominio → HTTP
Los servicios lanzan errores de `ERRORES/ERRORES_APLICACION.kt`; **nunca** códigos HTTP.
`CONFIGURACION_ERRORES.kt` (StatusPages) los traduce:

| Error | HTTP | Uso |
|---|---|---|
| `ErrorValidacion` | 400 | Entrada inválida o regla no cumplida |
| `ErrorNoAutorizado` | 401 | Sin sesión / credenciales |
| `ErrorProhibido` | 403 | Sin permiso |
| `ErrorNoEncontrado` | 404 | No existe **o es de otro usuario** |
| `ErrorConflicto` | 409 | Choca con el estado actual |
| `ErrorDemasiadosIntentos` | 429 | Bloqueo de login |
| `ErrorRespuestaExterna` | 502 | Proveedor respondió algo inutilizable |
| `ErrorServicioExterno` | 503 | Proveedor o BD no disponible |
| cualquier otra excepción | 500 `error_interno` | Se registra con traza; al cliente sin detalles |

Cuerpo: `{"error": "<codigo_snake_case>", "mensaje": "<texto en español>", "message": "<igual>"}`.
El código es **estable** (lo usa la app); el mensaje es para mostrar. Catálogo completo de códigos en [[API]].

## Resiliencia de integraciones
`UTILIDADES/UTILIDAD_RESILIENCIA.kt` → `PoliticaResiliencia`: **timeout por intento**, **reintentos con espera
exponencial + aleatoriedad** solo ante fallas transitorias (IO, timeout, 429/5xx), **cortocircuito** tras N fallos, y
nunca reintenta una cancelación real. Cada integración tiene su política (LLM, correo, Google Play, cada API de empleo, Redis).

- **LLM**: solo reintenta errores HTTP del proveedor; una respuesta con formato inválido no se reintenta (cuesta tokens).
  En el reporte, si la IA falla se usa el motor freemium.
- **Redis**: *fail-open* — si cae, la app sigue sin caché ni contador compartido.
- **APIs de empleo**: cadena de respaldo hasta un dataset local de contingencia.
- **Google Play caído** → `503` (no se informa como "compra inválida").

## Base de datos
Pool **HikariCP** con timeout de conexión de 5 s. Toda escritura múltiple en una transacción (`transaccion { }`, en el
dispatcher de IO). `GET /health` hace `SELECT 1` (503 si falla).

## Procesos en segundo plano
Nunca lanzan hacia afuera: registran el error y dejan el estado persistido (ej. reporte en `error` con su código).

Relacionado: [[Seguridad]] · [[Arquitectura general]] · [[Redis]]
