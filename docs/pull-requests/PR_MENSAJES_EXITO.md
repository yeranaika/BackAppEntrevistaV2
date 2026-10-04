# Mensaje de éxito en todas las respuestas de escritura

## Resumen
Los errores ya traían un texto claro para el usuario, pero los éxitos no: algunos llegaban como `{}`.
Ahora todo `POST`, `PUT`, `PATCH` o `DELETE` exitoso (todas las fases) trae `mensaje` en español.
Rama apilada sobre `refactor/documentacion-api`.

## Cambios
- **Respuestas vacías arregladas**: `POST /auth/logout`, `PUT /me`, `PUT /me/perfil`, `DELETE /me/objetivo` y
  `DELETE /api/v1/admin/preguntas/{id}` respondían `{}` porque `encodeDefaults = false` omitía `ok = true`.
  Ahora responden `{"ok": true, "mensaje": "…"}`.
- **`responderConMensaje`** (`UTILIDADES/UTILIDAD_RESPUESTA.kt`): agrega `mensaje` como campo extra junto a los datos
  del recurso, sin envolverlos. Así Android sigue leyendo los mismos campos (usa `ignoreUnknownKeys`).
- **`RespuestaMensaje`** ahora envía `mensaje` y también `message`, porque Android ya lee `message`.
- **`JSON_API`**: un solo formato JSON compartido entre ContentNegotiation y el helper.
- Los 58 endpoints de escritura traen mensaje: sesión, cuenta, perfil, onboarding, consentimientos, recordatorios, entrevista,
  feedback, mercado, nivelación, práctica, banco de preguntas, contrato `/api/prueba-practica` y billing.
- Los `GET` no cambian. El `204` ("no hay nada") sigue sin cuerpo.

## Documentación
- `API.md`: nueva sección "Respuestas de éxito".
- Colección de Postman: cada request de escritura tiene el test "Responde un mensaje de éxito".
- `GENERAR_DOCUMENTACION.py --probar` falla si un éxito de escritura no trae `mensaje`.
- Bóveda: regla en Convenciones de código y en "Cómo agregar un endpoint", decisión D15 y pendiente cerrado.

## Pruebas
- `./gradlew test`: **261 de 261**, 0 fallas. Hay una prueba nueva de los mensajes y se agregaron verificaciones en
  las pruebas de login y del banco de preguntas.
- Colección contra un servidor real en el 8093: **78 de 78**, incluida la verificación de `mensaje`. Los datos de
  prueba quedaron limpios.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
