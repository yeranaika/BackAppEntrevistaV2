---
tipo: tecnologia
tags: [tts, voz, mimo, android, propuesta]
actualizado: 2026-11-22
---

# TTS: voz del entrevistador (propuesta, sin implementar)

> [!info] Estado
> Investigación y prueba real de MiMo del 2026-11-22. **No hay código todavía.** Hoy la app tiene dictado (voz a texto, `core/voz/MOTOR_DICTADO.kt`)
> pero no lee nada en voz alta. Los precios y límites son de la documentación pública a esa fecha: verificarlos antes de usarlos.

## Qué se quiere leer en voz alta
- El **enunciado de cada pregunta** (simulación de entrevista y práctica).
- Opcional: el **feedback genérico** de la pregunta (`correcto`, `medio`, `malo`, D36 en [[Decisiones]]).

Los dos textos son **fijos por pregunta**, así que si el audio se genera en el servidor se genera **una sola vez** y se
reutiliza para todos los usuarios. El texto variable (lo que responde el usuario) no se lee.

## Opciones

| | A. Android `TextToSpeech` (en el teléfono) | B. MiMo TTS desde el backend | C. OpenAI TTS desde el backend |
|---|---|---|---|
| Costo | Gratis | **Gratis por tiempo limitado**, sin fecha de término; no gasta Credits del Token Plan | `tts-1`: USD 15 por millón de caracteres; `gpt-4o-mini-tts`: USD 0,60/M tokens de texto + USD 12/M tokens de audio |
| Español | Sí (voces de Google TTS: es-US, es-ES y según el equipo es-419) | **Malo en la prueba del 2026-11-22** (ver abajo): las voces de `mimo-v2.5-tts` son 4 en chino y 4 en inglés | Sí |
| Sin conexión (RNF11) | Sí, si el equipo tiene la voz descargada | Solo si la app guarda el audio ya descargado | Igual que B |
| Calidad | Correcta, depende del equipo | Muy natural; tiene control de estilo con texto | Natural |
| Trabajo | Solo app | Backend (endpoint, almacenamiento) + app (reproducir y guardar) | Igual que B |
| Privacidad | No sale nada del teléfono | Solo sale el texto de la pregunta (no hay datos del usuario) | Igual que B |

### Detalles de MiMo TTS (de la documentación oficial)
- Misma API que ya usamos: `POST https://api.xiaomimimo.com/v1/chat/completions` (compatible con OpenAI), modelo
  `mimo-v2.5-tts`. **El texto a leer va en un mensaje `assistant`**; un mensaje `user` opcional da el estilo ("tono
  profesional y cordial, ritmo pausado") y no se lee.
- `audio: {"format": "wav", "voice": "<voz>"}`. Voces: `mimo_default`, `Mia`, `Chloe`, `Milo`, `Dean` (inglés) y cuatro en
  chino. No hay parámetro de velocidad: se pide con el texto de estilo.
- Respuesta: `choices[0].message.audio.data` en base64. Con `stream: true` llega PCM16 de 24 kHz mono por trozos.
- Límites: 100 solicitudes por minuto por cuenta. No dice largo máximo del texto.
- No está documentado si una clave del Token Plan (`tp-…`) sirve para TTS; la de pago por uso sí.
- WAV sin comprimir pesa unos 48 KB por segundo: una pregunta de 15 s son ~700 KB. Convendría convertirlo a Opus/AAC
  antes de guardarlo.

## Prueba real de MiMo en español (2026-11-22)
Se generaron 7 audios con `mimo-v2.5-tts` (clave del Token Plan `tp-…` y `MIMO_BASE_URL` del `.env`: **funcionó**, sin
cambiar el endpoint) y se transcribieron de vuelta con Whisper `small` en el computador, sin red. Como **control**, el mismo
texto se leyó con la voz española de Windows (Microsoft Helena) y se transcribió igual. WER = porcentaje de palabras que el
transcriptor no entiende (0 % = perfecto).

| Audio | Voz y estilo | WER |
|---|---|---:|
| Control (3 textos) | Windows Helena es-ES | **0–3 %** |
| 01 pregunta blanda | `mimo_default`, sin estilo | 33 % |
| 02 pregunta blanda | `Mia` + estilo en español | 42 % |
| 05 pregunta blanda | `mimo_default` + estilo en inglés ("native Latin American Spanish") | 58 % |
| 07 pregunta blanda | `冰糖` (china) + estilo en español | 96 % |
| 04 feedback | `Chloe` + estilo en español | 57 % |
| 03 pregunta técnica | `Dean` + estilo en español | 74 % |
| 06 pregunta técnica | `mimo_default` + estilo en inglés | 71 % |

- El control demuestra que el transcriptor entiende bien el español: los errores son de la **pronunciación de MiMo**
  (por ejemplo, "compañero de equipo" → "compiedro de épico", "varios hilos" → "fallos cielos").
- Ni la instrucción de estilo (en español o en inglés) ni otra voz lo mejoran; con voz china es ininteligible.
- Tiempos: 7 a 11 s por audio de 12 a 19 s (sin streaming). WAV de 24 kHz, 16 bits, mono (~48 KB por segundo).
- Límite de la prueba: Whisper es una medida automática; conviene que una persona escuche los audios
  (`entrevistaApp/prueba_tts_mimo/`, fuera de los repos). El reconocimiento de voz de MiMo (`mimo-v2.5-asr`) no se usó
  para medir porque solo admite chino e inglés y sí gasta Credits.

**Conclusión: MiMo TTS hoy no sirve para leer preguntas en español.** Se descarta la opción B mientras MiMo no publique
voces en español.

## Recomendación
1. **Ahora: opción A (Android `TextToSpeech`).** Es gratis, funciona sin conexión, habla español y no toca el backend ni
   el contrato. Va detrás de una interfaz en la app (por ejemplo `LectorVoz`, como `AnalizadorConductas`), para poder
   cambiar el motor sin tocar las pantallas.
2. ~~Probar MiMo en español~~: hecho el 2026-11-22, **no sirve** (ver la prueba arriba).
3. **Si se quiere una voz más natural que la del teléfono, opción C (OpenAI u otro TTS con español) con caché**:
   - `GET /api/v1/preguntas/{id}/audio` (autenticado): si ya existe lo devuelve; si no, lo genera, lo guarda y lo devuelve.
     Mejor aún: generarlo al **aprobar** la pregunta, en segundo plano.
   - Guardarlo fuera de la BD (bucket de Railway o similar) con clave `pregunta_id + hash(texto) + voz`; si el admin
     edita el enunciado, el hash cambia y se regenera.
   - Contar cada llamada en `pregunta_generacion_ia` o en una tabla propia, para que aparezca en Costos IA y respete
     el tope mensual (D23).
   - La app descarga el audio junto con las preguntas del banco para el ciclo sin conexión, y si no lo tiene usa la
     opción A.
4. Costo de referencia de C con `tts-1`: un enunciado de ~200 caracteres cuesta ~USD 0,003; un banco de 1.000
   preguntas, ~USD 3 **una sola vez** gracias a la caché.

## Fuentes
- MiMo, síntesis de voz v2.5: https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/speech-synthesis-v2.5
- MiMo, precios de pago por uso: https://mimo.mi.com/docs/en-US/price/pay-as-you-go
- MiMo, límites: https://mimo.mi.com/docs/en-US/api/guidance/rate-limit
- OpenAI `tts-1`: https://developers.openai.com/api/docs/models/tts-1
- OpenAI `gpt-4o-mini-tts`: https://developers.openai.com/api/docs/models/gpt-4o-mini-tts
