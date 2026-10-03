---
tipo: tecnologia
tags: [tecnologia, docker, infraestructura]
actualizado: 2026-10-03
---

# Docker

**Para qué**: levantar **PostgreSQL** y **Redis** en local con un comando. El backend corre con Gradle (no está en contenedor).

`src/DB/docker-compose.yml`:
| Servicio | Contenedor | Imagen | Puerto | Datos |
|---|---|---|---|---|
| postgres | `Entrevista_APP` | postgres:16 | 5432 | volumen `pgdata`; al crearse ejecuta el esquema y los seeds |
| redis | `Entrevista_Redis` | redis:7-alpine | 6379 | volumen `redisdata` |

Credenciales locales: BD `DBentrevista`, usuario `root`, contraseña `root`.

```bash
cd src/DB
docker compose up -d                 # levantar (requiere Docker Desktop abierto)
docker compose down -v               # borrar TODO y empezar de cero (se vuelve a cargar esquema + seeds)
docker exec -it Entrevista_APP psql -U root -d DBentrevista
```

> [!warning] Esquema y seeds solo al crear el volumen
> Con un volumen existente, los cambios de esquema se aplican con `migrations/` (no recreando el contenedor). Ver [[Modelo de datos]].

Si Docker Desktop está cerrado, el backend no arranca (`db_no_disponible`) y las E2E fallan al conectar.

Relacionado: [[Puesta en marcha]] · [[PostgreSQL y Exposed]] · [[Redis]]
