---
tipo: arquitectura
tags: [base-de-datos, modelo, migraciones]
actualizado: 2026-10-03
---

# Modelo de datos

Base **PostgreSQL 16**, esquema **`app`**. Esquema completo en `src/DB/BasedeDatos.EntrevistaApp.sql` (fuente de verdad
para una BD nueva) y datos de ejemplo en `BasedeDatos.EntrevistaApp.Seeds.sql`. Los cambios sobre una BD existente
van en `migrations/` (ver más abajo). Tecnología: [[PostgreSQL y Exposed]].

## Tablas por dominio
| Dominio | Tablas | Notas |
|---|---|---|
| Identidad | `usuario`, `refresh_token`, `cuenta_oauth`, `password_reset` | Contraseñas Argon2id; refresh tokens guardados como hash |
| Perfil y objetivo | `perfil_usuario`, `objetivo_carrera` | El objetivo activo (cargo meta) alimenta entrevista, práctica y nivelación |
| Legal | `consentimiento_texto`, `consentimiento` | Una versión vigente; un consentimiento nuevo revoca el anterior |
| Recordatorios | `recordatorio_preferencia` | |
| Suscripción | `suscripcion`, `codigo_suscripcion` | `token_compra_hash` único: un pago, una cuenta |
| Mercado | `cargo`, `skill`, `cargo_skill`, `skill_tendencia` | `cargo_skill.nivel_requerido`, `peso`, `obligatoria` |
| Banco de preguntas | `pregunta`, `opcion_pregunta`, `pregunta_generacion_ia` | Solo `estado = 'aprobada'` se sirve a usuarios; trazabilidad y costo de la IA |
| Entrevista | `sesion_entrevista`, `sesion_pregunta_respuesta`, `metrica_video` | Snapshot de cada pregunta; índice único de una sesión en curso por usuario |
| Feedback | `reporte_entrevista`, `reporte_skill_detalle` | `estado_generacion`, `modo_evaluacion`, modelo/tokens/costo, `intentos_generacion` |
| Nivelación | `test_nivelacion`, `intento_test`, `resultado_nivelacion` | `respuestas_detalle` (JSONB) guarda preguntas servidas y corrección |
| Práctica | `sesion_practica`, `respuesta_practica` | `preguntas_snap` (JSONB); `id_local` único para sincronización offline |
| Progreso | `nivel_skill_usuario` | Puntaje acumulado (promedio) y nivel evaluado por skill |
| Visión (futuro) | `modelo_vision_ia`, `frame_entrenamiento`, `prediccion_vision`, `onboarding_usuario` | Existen en el esquema; el backend actual no las usa |

## Principios
- **Snapshots**: entrevista, práctica y nivelación copian la pregunta servida (enunciado, opciones, respuesta ideal).
  Editar o borrar el banco nunca cambia una prueba ya rendida.
- **Concurrencia por la BD**: índices únicos parciales + actualizaciones condicionales (`UPDATE … WHERE estado = …`)
  y `SELECT … FOR UPDATE` sobre el usuario o la sesión. Ejemplos: una sola entrevista en curso, un canje no supera
  `max_uses`, una nivelación se rinde una vez, un reporte se genera una vez.
- **Fechas** `TIMESTAMPTZ` (UTC). **Ids** UUID (`gen_random_uuid()`).
- Las tablas de Exposed **no declaran FK**: los joins indican las columnas (`join(…, JoinType.INNER, a, b)`).
- El servidor **no crea ni altera tablas al arrancar** (se quitó `createMissingTablesAndColumns`, que había desalineado el esquema).

## Migraciones
| Archivo | Qué hace |
|---|---|
| `013_remove_question_domain.sql` | Limpieza del dominio de preguntas antiguo |
| `014_password_reset_intentos.sql` | Intentos fallidos del código de recuperación |
| `015_alinear_esquema.sql` | Repara lo que alteró `createMissingTablesAndColumns`; hash del token de Google Play |
| `016_sesion_entrevista.sql` | Snapshot completo por pregunta; una sesión en curso por usuario |
| `017_practica_nivelacion.sql` | Práctica por cargo, preguntas servidas, `id_local`, nivelación sin onboarding; corrige default de `skill_tendencia` |
| `018_reporte_entrevista.sql` | Modo de evaluación, modelo, tokens, costo e intentos del reporte |

Todas son **idempotentes** (se pueden correr dos veces). Al crear una nueva: actualizar también el esquema base y
verificar sobre una BD vacía (`createdb`, esquema, migraciones, `dropdb`). Ver [[Puesta en marcha]].

Relacionado: [[Flujos de negocio]] · [[Arquitectura general]]
