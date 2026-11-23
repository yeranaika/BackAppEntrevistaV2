# E6-prep · Informe del dataset de verdad del revisor SIN IA

Fecha: 2026-11-20  
Repositorio: `BackAppEntrevistaV2`  
Ubicación de entregables: `scripts/validacion_revisor/`

---

## 1. Resumen ejecutivo y conteos reales

| Fuente | Archivo | Filas de datos | Líneas físicas (incl. header) | Estado |
|---|---|---:|---:|---|
| **R1 — Historial ya evaluado (BD `DBentrevista`)** | `scripts/validacion_revisor/R1_historial.csv` | **0** | 1 | **Vacío**: no existen sesiones ni evaluaciones previas en `DBentrevista` |
| **R2 — Golden cases humanos (`Seeds.sql`)** | `scripts/validacion_revisor/R2_golden.csv` | **84** | 85 | **Completo**: 14 preguntas con `respuesta_ideal` × 6 casos/pregunta |

> **Conclusión operativa para E6:** `app.sesion_pregunta_respuesta` tiene **0 filas** en `DBentrevista` (tampoco hay filas en `app.reporte_entrevista`, `app.sesion_entrevista` ni `app.respuesta_practica`). Por lo tanto, **R1 queda vacío (solo cabecera)** y la validación del revisor léxico en E6 **usará únicamente R2** hasta que se disponga de un historial real evaluado.

---

## 2. Esquema real encontrado en `DBentrevista` (consulta de solo lectura)

Se inspeccionó la instancia local activa de PostgreSQL (`Entrevista_APP`, BD `DBentrevista`, esquema `app`) exclusivamente con consultas `SELECT` en modo `READ ONLY`.

### 2.1 `app.sesion_pregunta_respuesta` (18 columnas)

| Columna | Tipo PostgreSQL | Nullable | Default / Notas |
|---|---|---|---|
| `respuesta_id` | `uuid` | NO | `gen_random_uuid()` |
| `sesion_id` | `uuid` | NO | FK → `app.sesion_entrevista(sesion_id)` |
| `pregunta_id` | `uuid` | YES | FK → `app.pregunta(pregunta_id)` |
| `enunciado_pregunta` | `text` | NO | Snapshot del enunciado |
| `respuesta_ideal_snap` | `text` | YES | Snapshot de `respuesta_ideal` |
| `transcripcion_audio` | `text` | YES | Texto de la respuesta abierta del candidato |
| `video_clip_url` | `character varying` | YES | URL del clip (si aplica) |
| `feedback_ia_tecnico` | `jsonb` | YES | JSON escrito al guardar reporte técnico: `{"puntaje", "observacion", "mejoras", "modo"}` |
| `feedback_ia_blando` | `jsonb` | YES | JSON escrito al guardar reporte blando: `{"puntaje", "observacion", "mejoras", "modo"}` |
| `puntaje_respuesta` | `numeric` | YES | Puntaje `0.00–100.00` de la respuesta |
| `orden` | `smallint` | NO | Orden dentro de la sesión |
| `tipo_pregunta` | `character varying` | YES | `'opcion_multiple' \| 'abierta_texto' \| 'simulacion_video'` (migración `016`) |
| `categoria_habilidad` | `character varying` | YES | `'tecnica' \| 'blanda'` (migración `016`) |
| `skill_id` | `uuid` | YES | Snapshot de skill (migración `016`) |
| `opciones_snap` | `jsonb` | YES | Snapshot de opciones (migración `016`) |
| `rubrica_snap` | `jsonb` | YES | Snapshot de rúbrica (migración `022`) |
| `opcion_elegida_id` | `uuid` | YES | Solo opción múltiple (migración `016`) |
| `fecha_respuesta` | `timestamp with time zone` | YES | Marca de tiempo de respuesta (migración `016`) |

### 2.2 `app.reporte_entrevista` (20 columnas)

Incluye las columnas de `018_reporte_entrevista.sql` (`modo_evaluacion`, `modelo_llm`, `tokens_entrada`, `tokens_salida`, `costo_usd`, `intentos_generacion`), `019_rescate_reportes.sql` (`intento_id`) y `021_metricas_sin_emociones.sql` (`puntaje_lenguaje_corporal` nullable sin default):

| Columna | Tipo PostgreSQL | Nullable | Default |
|---|---|---|---|
| `reporte_id` | `uuid` | NO | `gen_random_uuid()` |
| `sesion_id` | `uuid` | NO | — |
| `puntaje_global` | `numeric` | NO | `0` |
| `puntaje_tecnico` | `numeric` | NO | `0` |
| `puntaje_blando` | `numeric` | NO | `0` |
| `puntaje_lenguaje_corporal` | `numeric` | **YES** | *(sin default, migración `021`)* |
| `fortalezas` | `jsonb` | NO | `'[]'::jsonb` |
| `areas_mejora` | `jsonb` | NO | `'[]'::jsonb` |
| `recomendaciones_skills` | `jsonb` | NO | `'[]'::jsonb` |
| `resumen_ia` | `text` | YES | — |
| `estado_generacion` | `character varying` | NO | `'generando'` |
| `error_detalle` | `text` | YES | — |
| `fecha_generacion` | `timestamp with time zone` | NO | `now()` |
| `modo_evaluacion` | `character varying` | NO | `'freemium'` (`'freemium' \| 'ia'`, migración `018`) |
| `modelo_llm` | `character varying` | YES | *(migración `018`)* |
| `tokens_entrada` | `integer` | YES | *(migración `018`)* |
| `tokens_salida` | `integer` | YES | *(migración `018`)* |
| `costo_usd` | `numeric` | YES | *(migración `018`)* |
| `intentos_generacion` | `smallint` | NO | `0` *(migración `018`)* |
| `intento_id` | `uuid` | YES | *(migración `019`)* |

### 2.3 Conteo real de tablas de evaluación en `DBentrevista`

| Tabla | Filas reales |
|---|---:|
| `app.sesion_entrevista` | 0 |
| `app.sesion_pregunta_respuesta` | 0 |
| `app.reporte_entrevista` | 0 |
| `app.reporte_skill_detalle` | 0 |
| `app.respuesta_practica` | 0 |
| `app.intento_test` | 0 |
| `app.pregunta` | 14 (14 con `respuesta_ideal` no vacía) |

---

## 3. Detalle de R1 (`R1_historial.csv`)

- **Script reproducible (solo lectura):** `scripts/validacion_revisor/extraer_r1.py` (abre conexión `psycopg2` con `readonly=True`).
- **Archivo generado:** `scripts/validacion_revisor/R1_historial.csv`
- **Cabecera:** `pregunta_id,respuesta_usuario,score_o_veredicto_previo,modo`
- **Criterio de extracción:** filas de `app.sesion_pregunta_respuesta` donde `tipo_pregunta <> 'opcion_multiple'`, `transcripcion_audio` no está vacío, y existe al menos un campo de evaluación previa (`puntaje_respuesta`, `feedback_ia_tecnico` o `feedback_ia_blando`), resolviendo `modo` desde el JSON de feedback (`->>'modo'`) con fallback a `app.reporte_entrevista.modo_evaluacion`.
- **Resultado real:** **0 filas de datos** (1 línea física correspondiente a la cabecera). Si en el futuro se restaura un respaldo con sesiones reales evaluadas, basta re-ejecutar `python scripts/validacion_revisor/extraer_r1.py`.

---

## 4. Detalle de R2 (`R2_golden.csv`)

- **Script reproducible:** `scripts/validacion_revisor/generar_r2.py`
- **Fuente:** las **14 preguntas** con `respuesta_ideal` de `src/DB/BasedeDatos.EntrevistaApp.Seeds.sql`, cruzadas por `enunciado` contra `DBentrevista.app.pregunta` (**14/14 coincidencias exactas**, **0 diferencias** en `respuesta_ideal`).
- **Archivo generado:** `scripts/validacion_revisor/R2_golden.csv`
- **Columnas (15):** `caso_id`, `pregunta_seed_num`, `pregunta_id`, `tipo_pregunta`, `categoria_habilidad`, `nivel_dificultad`, `skill`, `enunciado`, `respuesta_ideal`, `rubrica_evaluacion`, `tipo_caso`, `respuesta_usuario`, `etiqueta_esperada`, `puntaje_min_esperado`, `puntaje_max_esperado`.

### 4.1 Distribución por tipo de caso y etiqueta esperada

| `tipo_caso` | Descripción | Casos | `etiqueta_esperada` | Rango esperado (0–100) |
|---|---|---:|---|---|
| `IDENTICA` | `respuesta_ideal` literal de `Seeds.sql` | 14 | `EXCELENTE` | `85–100` |
| `CASI_IDENTICA` | Paráfrasis fiel con sinónimos menores y reordenamiento | 14 | `EXCELENTE` | `75–98` |
| `PARCIAL` | Cubre ~50 % de los conceptos clave y omite la otra mitad | 14 | `PARCIAL` | `35–65` |
| `ALEATORIA` | `respuesta_ideal` literal de otra pregunta distinta | 14 | `INCORRECTA` | `0–25` |
| `OFF_TOPIC` | Párrafo fluido en español sin relación con software/entrevistas | 14 | `INCORRECTA` | `0–20` |
| `EVASIVA_CORTA` | Respuesta breve evasiva o de desconocimiento típico de candidato | 14 | `INCORRECTA` | `0–15` |
| **Total** | **6 casos × 14 preguntas** | **84** | `EXCELENTE: 28`, `PARCIAL: 14`, `INCORRECTA: 42` | — |

### 4.2 Distribución por `tipo_pregunta`

| `tipo_pregunta` | Preguntas | Casos en R2 | ¿Evaluada como abierta en `ServicioReporteEntrevista`? |
|---|---:|---:|---|
| `abierta_texto` | 4 | 24 | Sí |
| `simulacion_video` | 4 | 24 | Sí |
| `opcion_multiple` | 6 | 36 | No en entrevista (se corrige por opción), pero tiene `respuesta_ideal` y rúbrica en `Seeds.sql` |
| **Total** | **14** | **84** | **48 casos abiertos/video + 36 casos de banco con `respuesta_ideal`** |

### 4.3 Catálogo de las 14 preguntas incluidas en R2

| # | `pregunta_id` (`DBentrevista`) | `tipo_pregunta` | `categoria` | `nivel` | `skill` |
|---:|---|---|---|---|---|
| 1 | `bfe9c400-58cd-4eb8-a0c8-b4325d6ffb20` | `opcion_multiple` | `tecnica` | `junior` | Kotlin |
| 2 | `51ee1cd2-fbfe-46ea-8c2c-54ce5bc2aeaf` | `opcion_multiple` | `tecnica` | `semisenior` | Kotlin |
| 3 | `fce5c7f8-a3a1-4f26-ae7d-13249338240a` | `abierta_texto` | `tecnica` | `senior` | Kotlin |
| 4 | `ddbc7f4c-c845-40cc-b870-fea82c6fbc37` | `opcion_multiple` | `tecnica` | `junior` | SQL |
| 5 | `cfed55b8-9be5-4233-86af-557a5a1bf589` | `opcion_multiple` | `tecnica` | `semisenior` | SQL |
| 6 | `816c1b57-4687-45cf-b0a4-56e30e0e4ce2` | `abierta_texto` | `tecnica` | `senior` | PostgreSQL |
| 7 | `e5f7fd5f-823f-45d3-ae86-695cc424eefa` | `opcion_multiple` | `tecnica` | `junior` | REST APIs |
| 8 | `b429b3d5-56e9-4020-9e9d-f7c82e9fdc2c` | `abierta_texto` | `tecnica` | `semisenior` | REST APIs |
| 9 | `4ee0f17d-c69a-422f-b644-8368a3d0d877` | `simulacion_video` | `blanda` | `junior` | Comunicación |
| 10 | `c4c8d31b-ad12-4aad-9524-a7afb816b447` | `simulacion_video` | `blanda` | `semisenior` | Trabajo en equipo |
| 11 | `2a75afe5-3309-43ac-a6d0-dc33cb6c93a8` | `simulacion_video` | `blanda` | `senior` | Liderazgo |
| 12 | `4585b536-3280-4568-b76c-6137f04e7802` | `opcion_multiple` | `tecnica` | `junior` | Android (Kotlin) |
| 13 | `474da592-641a-456d-911a-077144289d61` | `abierta_texto` | `tecnica` | `senior` | Android (Kotlin) |
| 14 | `3459a6fd-741c-45bb-8eb5-12a5566e5578` | `simulacion_video` | `blanda` | `semisenior` | Resolución de problemas |

---

## 5. Riesgos identificados para la validación E6

1. **R1 vacío (0 filas):** `DBentrevista` no contiene historial previo de entrevistas ni respuestas evaluadas (`sesion_pregunta_respuesta = 0`, `reporte_entrevista = 0`). No existe línea base histórica de puntajes IA vs. freemium; la validación dependerá 100 % de R2 hasta incorporar datos reales.
2. **Descalce entre `respuesta_ideal` meta-descriptiva y respuestas STAR reales (`simulacion_video`):** en las 4 preguntas blandas (Q09, Q10, Q11, Q14), `respuesta_ideal` es una guía/plantilla (*"Respuesta STAR ideal: S: Contexto claro..."*) y no un relato en primera persona. Aunque `CASI_IDENTICA` evalúa paráfrasis de la plantilla, un candidato real en producción responderá con una anécdota concreta que tendrá bajo solapamiento léxico con esa plantilla — riesgo alto de falsos negativos en el motor freemium.
3. **Subconjunto operativo vs. banco completo:** de las 14 preguntas de la semilla con `respuesta_ideal`, 6 son `opcion_multiple` y solo 8 (`abierta_texto` + `simulacion_video`, **48 casos**) pasan por el evaluador de respuestas abiertas en `ServicioReporteEntrevista`. En E6 conviene reportar métricas globales (84 casos) y métricas desagregadas solo sobre abiertas/video (48 casos).
4. **Rúbricas en `Seeds.sql` sin `palabras_clave` y sin tildes:** todas las semillas definen `"criterios"` en `snake_case` sin tildes (p. ej. `situacion_clara`, `describe_btree_correctamente`, `postmortem_mencionado`). El evaluador actual (`EvaluadorRespuestaFreemium`) conserva tildes en `limpiar()` y extrae tokens de `"criterios"`, lo que puede penalizar incluso respuestas `IDENTICA` (por ejemplo `"situacion"` no coincide con `"situación"`, ni `"correctamente"` aparece en el texto).
5. **Sesgo de longitud y trigramas del español en casos negativos:** `ALEATORIA` y `OFF_TOPIC` tienen longitudes realistas (~35–75 palabras) para probar si el componente `PESO_LARGO = 0.20` y los trigramas comunes del español inflan puntajes de respuestas completamente incorrectas por encima de 20–25 puntos.
6. **UUIDs dependientes de la instancia de BD:** los `pregunta_id` de `Seeds.sql` se generan con `gen_random_uuid()` al poblar cada entorno. Si E6 corre contra otra BD, debe cruzar por `pregunta_seed_num` o `enunciado` además de `pregunta_id`.

---

## 6. Verificación de los CSV (salida real)

Comando ejecutado para validar generación, esquema, invariantes y conteo exacto:

```text
DIAGNOSTICO_BD: {"total_sesiones": 0, "total_spr": 0, "spr_con_evaluacion": 0, "spr_abiertas_evaluadas": 0, "total_reportes": 0, "total_respuestas_practica": 0, "total_preguntas": 14, "preguntas_con_ideal": 14}
R1_ESCRITO: A:\programacion\proyectos\proyecto-de-titulo\BackAppEntrevistaV2\scripts\validacion_revisor\R1_historial.csv
R1_FILAS_DATOS: 0
RESUMEN_R2: {"preguntas_seeds": 14, "coincidencias_bd": 14, "diferencias_respuesta_ideal_bd_vs_seeds": 0, "total_casos_r2": 84, "por_tipo_caso": {"IDENTICA": 14, "CASI_IDENTICA": 14, "PARCIAL": 14, "ALEATORIA": 14, "OFF_TOPIC": 14, "EVASIVA_CORTA": 14}, "por_etiqueta_esperada": {"EXCELENTE": 28, "PARCIAL": 14, "INCORRECTA": 42}, "por_tipo_pregunta": {"opcion_multiple": 36, "abierta_texto": 24, "simulacion_video": 24}}
R2_ESCRITO: A:\programacion\proyectos\proyecto-de-titulo\BackAppEntrevistaV2\scripts\validacion_revisor\R2_golden.csv
=== R1_historial.csv ===
lineas_totales=1 | filas_datos=0 | columnas=4
header: ['pregunta_id', 'respuesta_usuario', 'score_o_veredicto_previo', 'modo']
=== R2_golden.csv ===
lineas_totales=85 | filas_datos=84 | columnas=15
header: ['caso_id', 'pregunta_seed_num', 'pregunta_id', 'tipo_pregunta', 'categoria_habilidad', 'nivel_dificultad', 'skill', 'enunciado', 'respuesta_ideal', 'rubrica_evaluacion', 'tipo_caso', 'respuesta_usuario', 'etiqueta_esperada', 'puntaje_min_esperado', 'puntaje_max_esperado']
preguntas_distintas: 14 min_casos_por_preg: 6 max_casos_por_preg: 6
por_tipo_caso: {'IDENTICA': 14, 'CASI_IDENTICA': 14, 'PARCIAL': 14, 'ALEATORIA': 14, 'OFF_TOPIC': 14, 'EVASIVA_CORTA': 14}
por_etiqueta_esperada: {'EXCELENTE': 28, 'PARCIAL': 14, 'INCORRECTA': 42}
por_tipo_pregunta: {'opcion_multiple': 36, 'abierta_texto': 24, 'simulacion_video': 24}
invariantes: identicas_exactas=14/14 | aleatorias_distintas=14/14 | respuestas_vacias=0
```
