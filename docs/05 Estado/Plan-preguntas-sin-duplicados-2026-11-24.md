# PLAN — Preguntas sin duplicados + vista para el panel de administrador (24-nov-2026)

> Estado: **fases 1-4 implementadas en el backend (D37)**; falta calibrar el umbral con datos reales y la pantalla del panel. Alcance: solo backend; el panel es otro repo y aquí solo se deja su contrato.
> Propuesta de decisión: **D37**.

## 1. Situación hoy (verificado en el código)

| Qué | Dónde | Efecto |
|---|---|---|
| Enunciados iguales dentro del mismo lote → se descarta **todo** el lote (`enunciado_duplicado`) | `VALIDADOR_LOTE_PREGUNTA_IA.kt:49` | Solo atrapa copias exactas (tras quitar tildes/mayúsculas) y solo dentro del lote |
| Opciones repetidas en una pregunta | `VALIDADOR_LOTE_PREGUNTA_IA.kt:91` | Solo opción múltiple |
| Comparación contra el banco | **no existe**. `ServicioGeneracionPregunta.generarConMercado` valida → `guardarLote` | Se puede generar 10 veces el mismo tema y se guardan 10 casi iguales |
| El prompt a la IA | `construirPrompt` | No lleva las preguntas existentes, así que la IA no tiene cómo evitarlas |
| Índice único sobre enunciado | no hay (`app.pregunta` solo tiene índices de consulta) | Nada lo impide en BD |
| Medidor de parecido reutilizable | `ComparadorTextos.similitud` (TF-IDF + cobertura + Jaccard + orden, sin IA, sin tokens) | Ya probado con respuestas; sirve para enunciados |

## 2. Diseño para que no choque

Reglas de diseño (por qué cada una):

1. **Se filtra después de validar, nunca dentro del validador.** El validador exige `cantidad` exacta; si descartara
   duplicadas ahí, rompería `cantidad_incorrecta` y tiraría un lote ya pagado. El lote válido se parte en
   *nuevas* y *descartadas*.
2. **Un duplicado descarta solo esa pregunta**, no el lote. Las demás se guardan como hoy (pendientes).
3. **Las descartadas igual se cobran**: sus tokens cuentan para el tope mensual D23. Si **todas** se descartan, la
   llamada se audita como el resto de fallos (`registrarFallo`, `parse_exitoso=false`) con los tokens, para que el
   gasto no desaparezca de Costos IA (D28).
4. **Alcance de la comparación** = misma `tipo_pregunta` + `categoria_habilidad` + `nivel_dificultad` y mismo
   cargo/skill (o sin cargo, como `paraCargoId`, D26). Estados `aprobada` y `pendiente` (las pendientes cuentan:
   evita duplicar contra lo que otro admin generó hace 5 minutos). `rechazada` no cuenta.
5. **Doble defensa**: (a) el prompt lleva hasta 30 enunciados existentes del alcance (cortados a 120 caracteres,
   "no repitas ni parafrasees estos") para gastar menos en descartes; (b) el filtro posterior es la garantía, porque
   la IA igual repite.
6. **Concurrencia**: dos generaciones simultáneas del mismo alcance leerían el banco antes de que la otra guarde.
   Solución sin migración: la comprobación **y** el `guardarLote` van en la misma transacción con
   `pg_advisory_xact_lock(hash(cargoId, skillId, nivel, tipo))`. El LLM se llama *fuera* del candado; solo
   lectura+inserción (milisegundos) quedan serializadas.
7. **Compatibilidad**: solo campos **nuevos y aditivos** en las respuestas del panel. `GET /api/v1/preguntas` (Android)
   no cambia. Sin migración de BD.
8. **Creación/edición manual y aprobar: solo aviso**, no bloqueo. Un admin puede querer dos preguntas parecidas a
   propósito; bloquear cambiaría el contrato de `POST/PUT/PATCH`. Se devuelve `similares` y el panel decide.

### Medida de parecido

- Nuevo `DETECTOR_PREGUNTAS_SIMILARES.kt` (SERVICIOS) que envuelve `ComparadorTextos.similitud`.
- `similitud` es asimétrica (la cobertura mira la referencia), así que se toma el **máximo de ambas direcciones**.
- Se compara **solo el enunciado** (en opción múltiple no se comparan opciones: límite conocido).
- Se usan los `sinonimos` de la pregunta existente para que "interbloqueo" = "deadlock".
- Umbral por env `UMBRAL_SIMILITUD_PREGUNTAS` (por defecto **0.80**, `0` = apagado, igual que otras variables de
  `.env.example`). Tope de 200 candidatas por comparación para acotar el costo.
- **El 0.80 es una suposición.** La BD local tiene 14 preguntas, insuficiente para calibrar. Por eso la Fase 1 es
  *modo sombra* (ver más abajo).
- Límite: solo detecta parecido de palabras. Una paráfrasis con otro vocabulario pasa. Mejora futura: embeddings
  (`shortcut:` comentar en el código).

## 3. Contrato para el panel de administrador

Todos bajo `soloAdmin`, rutas nuevas en `CONTROLADOR_PREGUNTA.kt`, aditivas.

### 3.1 `POST /api/v1/admin/preguntas/generar-ia` (y alias `/admin/questions/generate-ai`)

Respuesta actual + campos nuevos:

```json
{
  "preguntas": [ { "id": "…", "enunciado": "…" } ],
  "descartadas": [
    {
      "enunciado": "¿Qué es un deadlock?",
      "similitud": 0.91,
      "similarA": { "id": "…", "enunciado": "Explica qué es un interbloqueo", "estado": "aprobada" }
    }
  ],
  "resumen": { "pedidas": 5, "guardadas": 4, "descartadasPorSimilitud": 1 }
}
```

Si `guardadas = 0`: sigue siendo `200` con `preguntas: []` y `descartadas` completas (no es un error del servidor:
el panel debe mostrar "todas se parecían a preguntas ya existentes, prueba otro skill o nivel").

### 3.2 `GET /api/v1/admin/preguntas/{id}/similares?umbral=0.7`

Para la pantalla de revisión de pendientes: preguntas parecidas a esta (mismo alcance), mayor a menor.

```json
{ "preguntaId": "…", "umbral": 0.7,
  "similares": [ { "id": "…", "enunciado": "…", "estado": "aprobada", "similitud": 0.83 } ] }
```

### 3.3 `GET /api/v1/admin/preguntas/duplicadas?cargoId=|skillId=&nivel=&umbral=0.8`

Para limpiar el banco existente. Exige `cargoId` o `skillId` (evita el cruce de todo contra todo, que es O(n²)).
Devuelve grupos: `[{ "preguntas": [{id, enunciado, estado, vecesUsada}], "similitudMaxima": 0.93 }]`.
`vecesUsada` ayuda a decidir cuál conservar (la usada no se puede borrar, `DELETE` ya lo impide).

### 3.4 Aviso en `POST/PUT /admin/preguntas` y `PATCH …/aprobar`

La respuesta suma `similares` (mismo formato que 3.2, vacío si no hay). Sin cambio de código HTTP.

### 3.5 Para rechazar una duplicada

No hace falta endpoint: se usa `PATCH …/rechazar` con `{"motivo":"duplicada"}`.

### Propuesta de pantallas (referencia para el repo del panel)

- **Generar con IA**: tras generar, banner "Se descartaron N por parecerse a preguntas existentes" con lista
  expandible (enunciado nuevo vs. existente lado a lado y % de similitud).
- **Pendientes de revisión**: insignia "Parecida a N" por fila (usa 3.2); al abrir la pregunta, panel lateral con las
  similares y botones **Rechazar como duplicada** (3.5) y **Aprobar igual**.
- **Banco**: pestaña "Posibles duplicadas" (3.3) con filtros cargo/skill/nivel y slider de umbral.
- **Costos IA**: sin cambio, pero el gasto de las descartadas ya cuenta.

## 4. Fases

| Fase | Qué | Criterio de salida |
|---|---|---|
| **0 · Datos** | Contar preguntas reales en Railway por alcance; sacar 30-50 pares "mismo tema" etiquetados a mano para calibrar | Tabla de pares con etiqueta duplicada / no |
| **1 · Detector en modo sombra** | `DETECTOR_PREGUNTAS_SIMILARES` + endpoints 3.2 y 3.3 (solo lectura). Generar-ia **aún no filtra**: solo devuelve `descartadas` como "habrían sido descartadas" | Umbral elegido con los pares de la Fase 0 (falsos positivos < 5 %) |
| **2 · Filtro al generar** | Paso entre validar y guardar, `descartadas`/`resumen`, candado advisory, auditoría del caso "todas descartadas" | Pruebas de abajo en verde |
| **3 · Prompt con existentes** | Bloque "no repitas" con ≤ 30 enunciados | Tasa de descarte baja frente a la Fase 1 |
| **4 · Avisos manuales** | `similares` en crear/editar/aprobar (3.4) | — |
| **5 · Cierre** | `./gradlew test` completo, `python docs/documentacion/GENERAR_DOCUMENTACION.py`, Postman, D37 en `Decisiones.md`, `Estado del proyecto.md` | Regla de AGENTS.md |

## 5. Pruebas necesarias

- **Detector**: copia exacta; mismas palabras en otro orden; con/sin tildes y mayúsculas; sinónimo del grupo
  (`deadlock`/`interbloqueo`); mismo tema pero pregunta distinta (debe quedar bajo el umbral); texto vacío → 0.
- **Servicio de generación** (proveedor falso): lote con 1 duplicada → se guardan N-1 y `descartadas` trae 1;
  lote con todas duplicadas → 0 guardadas, 200, tokens auditados y contados en el tope D23; `UMBRAL=0` → igual que hoy;
  pregunta rechazada en el banco **no** cuenta; pendiente **sí** cuenta.
- **Concurrencia** (BD de prueba): dos `generar` en paralelo con el mismo lote → solo uno persiste cada enunciado.
- **Contrato**: las respuestas existentes conservan todos sus campos; `GET /api/v1/preguntas` idéntico.
- **Mutaciones** al umbral y al `>=`/`>` (borde exacto), como en las rondas anteriores.

## 6. Riesgos y qué no se resuelve

- **Umbral mal puesto**: alto deja pasar duplicadas; bajo descarta preguntas válidas y gasta tokens. Por eso la
  Fase 1 en sombra y la variable de entorno (se ajusta en Railway sin redeploy de código).
- **Paráfrasis con otras palabras** no se detecta (léxico, no semántico).
- **Costo**: el comparador recalcula IDF por llamada; con tope de 200 candidatas es barato, pero `duplicadas` sin
  filtro de cargo/skill no se ofrece a propósito.
- **Preguntas viejas ya duplicadas**: este plan evita nuevas; las existentes se limpian a mano con 3.3.
- **Migración 022 pendiente en Railway** (ver Estado del proyecto): este plan no agrega migraciones, pero no
  mezclar el despliegue.
- **Reglas de AGENTS.md**: probar en el puerto 8093 con proveedor falso (no llamar al LLM real), limpiar datos de
  prueba, sin push ni PR sin pedido.
