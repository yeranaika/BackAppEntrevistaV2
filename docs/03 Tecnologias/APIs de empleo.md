---
tipo: tecnologia
tags: [tecnologia, mercado, apis-externas]
actualizado: 2026-10-03
---

# APIs de empleo (mercado laboral)

**Para qué**: saber qué skills piden las ofertas reales, para armar los **requisitos de cada cargo** (`cargo_skill`)
y las **tendencias** (`skill.demanda_score`, `skill_tendencia`). Así las pruebas apuntan a lo que pide el mercado.

| Fuente | Tipo | Config |
|---|---|---|
| **JSearch** (RapidAPI) | Principal, de pago por cuota | `JSEARCH_API_HOST`, `JSEARCH_API_KEY` (opcional) |
| Remotive | Gratuita | — |
| Arbeitnow | Gratuita | — |
| Dataset de contingencia | Local (`CONFIGURACION/DATASET_OFERTAS_CONTINGENCIA.kt`) | — |

Se prueban en ese orden; cada una con su política de resiliencia (2 intentos, 20 s, cortocircuito 5 min).

## Código
- `INTEGRACIONES/CLIENTE_MERCADO_LABORAL.kt` — cliente con la cadena de respaldo.
- `SERVICIOS/NORMALIZADOR_SKILL.kt` — extrae y normaliza skills técnicas y blandas del texto, detecta nivel.
- `SERVICIOS/SERVICIO_REQUISITOS_CARGO.kt`, `SERVICIO_TENDENCIAS_SKILL.kt`, `SERVICIO_MERCADO.kt`.
- `TareaSincronizacionMercado` — corre **cada 7 días**; al reiniciar espera lo que falte desde la última sincronización.

> [!danger] Consume cuota
> `POST /admin/market/sync-trends`, `…/generate-requirements`, `…/generate-all` y crear un cargo con
> `autoGenerateSkills: true` llaman a las APIs. En pruebas, crear cargos con `autoGenerateSkills: false`.

Relacionado: [[Redis]] · [[Flujos de negocio]]
