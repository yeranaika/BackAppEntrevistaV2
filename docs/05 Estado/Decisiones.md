---
tipo: estado
tags: [decisiones, adr]
actualizado: 2026-10-03
---

# Decisiones

Registro de decisiones de arquitectura (formato ADR breve). **Agregar las nuevas al final, con fecha.**
Plantilla: *Contexto → Decisión → Por qué → Consecuencias*.

## D01 · Backend propio Ktor + PostgreSQL en vez de Firebase (vigente)
- **Contexto**: el informe proponía Firebase (Auth, BD) y el requisito R07 pide Ktor + PostgreSQL.
- **Decisión**: backend propio en Kotlin/Ktor con PostgreSQL, JWT propio y login con Google verificado en el servidor.
- **Por qué**: control del modelo de datos relacional (pruebas, reportes, mercado), mismo lenguaje que la app, sin dependencia de un BaaS.
- **Consecuencias**: el backend es responsable de seguridad, sesiones y migraciones; TLS lo pone el despliegue.

## D02 · OpenAI / Anthropic detrás de una interfaz, no Gemini (vigente)
- **Contexto**: el informe nombra Gemini 2.5 Flash; la implementación ya integraba OpenAI y Anthropic.
- **Decisión**: `ProveedorPreguntasIa` con implementaciones OpenAI (`gpt-4o-mini`) y Anthropic (`claude-haiku-4-5`).
- **Consecuencias**: agregar Gemini = una implementación más de la interfaz; la lógica no cambia.

## D03 · Capas MVC + servicios con nombres en español (vigente)
Estándar del equipo (`programacion-mvc`). Carpetas y archivos en MAYÚSCULAS, identificadores en español. → [[Convenciones de codigo]]

## D04 · Errores de dominio con código estable + mensaje en español (vigente)
Un solo manejador traduce a HTTP; el cliente decide por `error`, muestra `mensaje`. Se mantiene `message` por la app.

## D05 · El servidor no crea ni altera tablas; migraciones idempotentes (2026-10, fase 4)
`createMissingTablesAndColumns` había desalineado el esquema (columna extra, fechas sin zona). Se retiró; migración 015 lo reparó.

## D06 · Respetar el contrato de la app Android (2026-10, fases 2–6)
La app usa `/api/prueba-practica`, JSON en snake_case en billing y errores de registro antiguos. Se implementaron adaptadores
sobre los servicios nuevos en vez de cambiar la app. Pruebas con copias de sus DTO.

## D07 · Snapshot de preguntas en cada prueba (2026-10, fases 5–6)
Entrevista, práctica y nivelación guardan la pregunta servida. Editar el banco no cambia pruebas rendidas.

## D08 · Concurrencia resuelta en la BD (2026-10)
Índices únicos parciales, `UPDATE` condicionales y `FOR UPDATE`, en vez de candados en memoria (funciona con varias instancias).

## D09 · IA solo para premium, freemium como respaldo (2026-10, fase 7)
- **Decisión**: el motor freemium corrige para todos; la IA evalúa el reporte de entrevista solo a premium, una llamada por entrevista.
- **Por qué**: costo por token (restricción de presupuesto) y el reporte debe salir siempre.
- **Pendiente**: confirmar que el EULA cubre el envío de respuestas a OpenAI / Anthropic.

## D10 · La nivelación decide el nivel; práctica y entrevista solo suman puntaje (2026-10, fases 6–7)
Evita que una práctica fácil suba el nivel. El nivel del perfil no se cambia: se informa como sugerido.

## D11 · `onboarding_usuario` no se usa; el objetivo vive en `objetivo_carrera` (2026-10, fase 6)
`resultado_nivelacion.onboarding_id` pasó a opcional y se guarda `cargo_id`. `nivel_verificado` no se marca.

## D12 · La app no graba video: las preguntas `simulacion_video` se le muestran como abiertas (2026-10, fase 5)

## D13 · Documentación generada desde una sola definición (2026-10)
`docs/documentacion/GENERAR_DOCUMENTACION.py` produce `API.md` y la colección de Postman; el catálogo de errores se lee del código.

## D14 · Alcance del trabajo: solo backend (2026-10)
La app Android es solo referencia del contrato; no se proponen ni hacen cambios en ella desde este repositorio.

## D15 · Mensaje de éxito como campo extra, sin envolver (2026-10)
- **Contexto**: los errores traían texto para el usuario y los éxitos no; algunos llegaban como `{}`.
- **Decisión**: todo éxito de `POST`/`PUT`/`PATCH`/`DELETE` agrega `mensaje` junto a los datos (`responderConMensaje`).
- **Por qué**: envolver en `{datos, mensaje}` rompería a Android; un campo extra lo ignora (`ignoreUnknownKeys`).
- **Consecuencias**: los `GET` no llevan mensaje; `message` se mantiene donde Android ya lo leía.

Relacionado: [[Arquitectura general]] · [[Estado del proyecto]]
