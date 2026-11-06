---
tipo: tecnologia
tags: [tecnologia, redis, cache]
version: Redis 7 · Jedis 5.2.0
actualizado: 2026-11-06
---

# Redis

**Para qué**: caché del catálogo del mercado y **contadores compartidos** entre instancias: bloqueo de login y límite
por IP (D18). No es fuente de verdad: si Redis cae, la app sigue funcionando (la caché y el bloqueo de login hacen
*fail-open*; el límite por IP pasa a la memoria de cada instancia, nunca se quita).

| | |
|---|---|
| Contenedor local | `Entrevista_Redis` (puerto 6379) → [[Docker]] |
| Config | `REDIS_HOST` (por defecto `localhost`), `REDIS_PORT`, `REDIS_PASSWORD` |
| Código | `INTEGRACIONES/CLIENTE_CACHE.kt` (`Cache`, `CacheRedis`, `obtenerOCalcular`), `INTEGRACIONES/CONTADOR_INTENTOS.kt` |

## Qué se guarda
| Clave | Contenido | TTL |
|---|---|---|
| `mercado:cargos` | Lista de cargos | 6 h |
| `mercado:matriz:*` | Matriz de skills de un cargo | 12 h |
| `mercado:tendencias*` | Skills en tendencia | según consulta |
| `login:fallos:<correo>` | Contraseñas incorrectas seguidas | 15 min |
| `limite:registro:<ip>` | Registros desde esa IP (`POST /auth/register`) | 10 min desde el primero |
| `limite:recuperacion:<ip>` | Códigos de recuperación pedidos desde esa IP | 15 min desde el primero |

Crear un cargo o sincronizar tendencias **invalida** `mercado:*`.

**Contadores** (`login:fallos:*`, `limite:*`): un script Lua atómico (`INCRBY` + `PTTL`, y `PEXPIRE` solo si la clave
no tiene vencimiento) en `CacheRedis.contarEnVentana`. Es una ventana fija: los rechazos suman pero no la alargan, y
una clave huérfana sin TTL se repara sola. Una IPv6 cuenta por su bloque `/64`.

## Detalles
- Las llamadas a Jedis corren en `Dispatchers.IO` (no bloquean los hilos de Ktor) con política de resiliencia
  (1 intento, timeout 1 s, cortocircuito tras 3 fallos).
- Borrado por prefijo con `SCAN` (nunca `KEYS` en producción).
- En pruebas se usa `CacheEnMemoria` (doble de prueba).

```bash
docker exec Entrevista_Redis redis-cli KEYS "mercado:*"
docker exec Entrevista_Redis redis-cli TTL "login:fallos:usuario@ejemplo.com"
docker exec Entrevista_Redis redis-cli --scan --pattern "limite:*"      # IPs con contador activo
docker exec Entrevista_Redis redis-cli PTTL "limite:registro:203.0.113.7"
docker exec Entrevista_Redis redis-cli DEL "limite:registro:203.0.113.7" # desbloquear una IP a mano
```

Relacionado: [[Manejo de errores y resiliencia]] · [[APIs de empleo]]
