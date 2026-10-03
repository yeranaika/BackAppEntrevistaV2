---
tipo: tecnologia
tags: [tecnologia, postgresql, exposed, base-de-datos]
version: PostgreSQL 16 · Exposed 0.55.0 · HikariCP 5.1.0 · driver 42.7.4
actualizado: 2026-10-03
---

# PostgreSQL y Exposed

**Para qué**: PostgreSQL es la fuente de verdad (usuarios, pruebas, reportes, mercado…). **Exposed** (DSL de JetBrains)
es la capa de acceso: tablas como objetos Kotlin y consultas tipadas, solo dentro de `MODELOS/`.

| | |
|---|---|
| Contenedor local | `Entrevista_APP` (puerto 5432), BD `DBentrevista`, esquema `app` → [[Docker]] |
| Conexión | `DB_URL`, `DB_USER`, `DB_PASS`, `DB_POOL_MAXIMO` (pool HikariCP, timeout 5 s) |
| Esquema | `src/DB/BasedeDatos.EntrevistaApp.sql` + migraciones `migrations/0NN_*.sql` → [[Modelo de datos]] |
| JSON | Columnas `jsonb` mapeadas con `kotlinx.serialization` (`exposed-json`) |

## Patrón de repositorio
```kotlin
interface RepositorioX { suspend fun buscar(id: UUID): X? }          // lo que usa el servicio
class RepositorioXExposed : RepositorioX {                            // la implementación
    override suspend fun buscar(id: UUID) = transaccion { TablaX.selectAll().where { … }.firstOrNull()?.aX() }
}
```
- `transaccion { }` (`UTILIDADES/UTILIDAD_TRANSACCION.kt`) = transacción suspendida en `Dispatchers.IO`.
- Concurrencia: `forUpdate()` para bloquear filas, `UPDATE … WHERE estado = …` condicional, e índices únicos parciales.
  Violación de índice único = SQLSTATE `23505` (`SQLSTATE_VALOR_DUPLICADO`) → se traduce a `ErrorConflicto`.
- `insertIgnore` = `ON CONFLICT DO NOTHING` (ej. crear el reporte una sola vez).
- Agregaciones en SQL (`avg()`, `count()`, `groupBy`) en vez de traer miles de filas.

## Cuidados
- Las tablas Exposed **no declaran FK** → los joins deben indicar columnas: `TablaA.join(TablaB, JoinType.INNER, TablaA.x, TablaB.x)`.
- **Nunca** `SchemaUtils.createMissingTablesAndColumns` en producción (alteró el esquema real; ver migración 015).
  En pruebas sí se usa, sobre H2 → [[Pruebas]].
- Toda migración nueva: idempotente, reflejada en el esquema base, verificada sobre BD vacía.

```bash
# aplicar una migración a la BD local
docker exec -i Entrevista_APP psql -U root -d DBentrevista -v ON_ERROR_STOP=1 < migrations/018_reporte_entrevista.sql
```

Relacionado: [[Modelo de datos]] · [[Manejo de errores y resiliencia]]
