---
tipo: tecnologia
tags: [tecnologia, redis, cache]
version: Redis 7 · Jedis 5.2.0
actualizado: 2026-10-03
---

# Redis

**Para qué**: caché del catálogo del mercado y **contador compartido** del bloqueo de login. No es fuente de verdad:
si Redis cae, la app sigue funcionando (*fail-open*).

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

Crear un cargo o sincronizar tendencias **invalida** `mercado:*`.

## Detalles
- Las llamadas a Jedis corren en `Dispatchers.IO` (no bloquean los hilos de Ktor) con política de resiliencia
  (1 intento, timeout 1 s, cortocircuito tras 3 fallos).
- Borrado por prefijo con `SCAN` (nunca `KEYS` en producción).
- En pruebas se usa `CacheEnMemoria` (doble de prueba).

```bash
docker exec Entrevista_Redis redis-cli KEYS "mercado:*"
docker exec Entrevista_Redis redis-cli TTL "login:fallos:usuario@ejemplo.com"
```

Relacionado: [[Manejo de errores y resiliencia]] · [[APIs de empleo]]
