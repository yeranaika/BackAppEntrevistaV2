---
tipo: estado
tags: [riesgos]
fuente: DOCUMENTOS_INVESTIGACION/final.docx (gestión de riesgos) + estado técnico actual
actualizado: 2026-10-03
---

# Riesgos

| Riesgo | Impacto | Prob. | Mitigación en el backend | Estado |
|---|---|---|---|---|
| Caída o cambio de un proveedor de IA | Muy alto | Media | Interfaz intercambiable (OpenAI/Anthropic); motor freemium de respaldo; el reporte siempre sale | Mitigado |
| Costo de tokens fuera de presupuesto | Alto | Media | IA solo premium, 1 llamada por entrevista, costo registrado, E2E con IA solo opt-in | Parcial → falta tope mensual ([[Pendientes y deuda tecnica]]) |
| Latencia del feedback > 5 s | Alto | Alta | Práctica/nivelación con corrección local (ms); entrevista asíncrona con estado `generando` | Mitigado |
| Filtración de datos personales | Alto | Baja | Argon2id, JWT, 404 en recursos ajenos, hash de tokens, errores sin detalles → [[Seguridad]] | Mitigado; falta TLS en el despliegue |
| APIs de empleo caídas o sin cuota | Medio | Media | Cadena de respaldo + dataset local; sincronización semanal | Mitigado |
| Redis caído | Bajo | Media | Fail-open (sin caché ni contador compartido) | Mitigado |
| Esquema de BD desalineado | Alto | Baja | Sin auto-DDL; migraciones idempotentes verificadas sobre BD vacía | Mitigado |
| Romper la app Android con un cambio | Alto | Media | Pruebas de contrato con copias de los DTO de la app; adaptadores | Mitigado |
| Varios agentes cambiando lo mismo | Medio | Media | Una rama por tarea; reglas en [[Guia para agentes]] | Proceso |
| Feedback de IA sesgado o confuso (C15) | Medio | Baja | Instrucciones explícitas, salida validada, textos freemium revisados | Aceptado (revisión humana pendiente) |

Relacionado: [[Alcance y limites]] · [[Manejo de errores y resiliencia]]
