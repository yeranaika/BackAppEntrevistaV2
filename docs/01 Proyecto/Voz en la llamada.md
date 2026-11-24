---
tipo: proyecto
tags: [voz, llamada, transcripcion, diccionario, privacidad]
actualizado: 2026-11-24
---

# Voz en la llamada

Que la persona **responda hablando** durante la llamada en vivo y que lo dicho quede como texto en el reporte y en el
backend, para que el feedback (N3/N4) se apoye en lo que realmente respondió.

## Criterios de aceptación
| Código | Criterio | Dónde se cubre |
|---|---|---|
| VOZ-1 | La pregunta actual se lee en la llamada (texto, `preguntaId` y avance) | App. El backend ya entrega las preguntas de la sesión |
| VOZ-2 | La voz se captura solo durante la ventana de respuesta | App (botón "Listo"; el motor se libera al terminar) |
| VOZ-3 | La transcripción llega asociada a `preguntaId` y `sesionId` | `POST /api/v1/transcripciones` |
| VOZ-4 | Se guarda en el reporte de la app y en el backend | Tabla `transcripcion_respuesta`; la app la pinta en el reporte de la llamada |
| VOZ-5 | **Privacidad**: el audio no sale del teléfono | ASR en el dispositivo (RNF09). El servidor solo recibe **texto** |

## Decisiones de diseño
- **El audio nunca llega al servidor.** El reconocimiento corre en el teléfono (`MotorDictado`, ya existente). Si un día se
  agrega un ASR en servidor o en la nube, necesita un consentimiento aparte y no pre-marcado (regla T0.8); el endpoint de
  transcripciones no cambia porque solo recibe texto.
- **El motor es intercambiable** en la app (interfaz `MotorTranscripcion`); el backend no sabe cuál se usó.
- **Los diccionarios son datos, no código.** Un paquete por área (`programador-front`, `programador-back`, ...) con
  `terminos` (sesgan al reconocedor) y `correcciones` ("cotlin" → "Kotlin"). Se editan desde el panel y la app los
  sincroniza por versión: agregar un área nueva no requiere publicar la app.
- **Una transcripción por (usuario, sesión, pregunta).** Reenviar la reemplaza; un parcial nunca pisa a un final.
- **Se borra con la purga biométrica** (retiro del permiso de cámara, Art. 17 RGPD), con la sesión y con la cuenta.

## API (contrato JSON en camelCase, como el resto de la app)
| Ruta | Quién | Qué hace |
|---|---|---|
| `POST /api/v1/transcripciones` | usuario | `{sesionId, preguntaId, inicioMs, finMs, texto, idioma?, confianza?, parcial?}`. 201 si es nueva; 200 si reemplaza. La pregunta debe ser de esa sesión |
| `GET /api/v1/transcripciones?sesionId=` | usuario | Las de su sesión, en orden |
| `GET /api/v1/diccionarios` | usuario | Manifiesto de los paquetes activos: `{area, nombre, version, totalTerminos, totalCorrecciones}` |
| `GET /api/v1/diccionarios/{area}` | usuario | Paquete completo |
| `GET /api/v1/admin/diccionarios[/{area}]` | admin | Igual, incluyendo desactivados |
| `PUT /api/v1/admin/diccionarios/{area}` | admin | Crea (201) o reemplaza (200, sube la versión y reactiva). `versionEsperada` evita pisar a otro editor (409 `diccionario_version_desfasada`) |
| `DELETE /api/v1/admin/diccionarios/{area}` | admin | Desactiva: la app deja de recibirlo y la versión sube para que lo note |

### Sincronización con la app
1. La app pide el manifiesto (barato) al abrir una llamada o al volver la red.
2. Baja solo los paquetes cuya `version` es mayor a la que tiene guardada, más el área `base`.
3. Si un área ya no aparece en el manifiesto, la borra de su cache.
4. Sin conexión usa lo que tiene guardado.

### Edición desde el panel
El cuerpo del `PUT` es el mismo JSON que devuelve el `GET`, así que un paquete se puede **exportar de un ambiente e
importar en otro**. El servidor limpia espacios, vacíos y repetidos (sin distinguir mayúsculas). Un panel puede agregar un
término trayendo el paquete, sumándolo y guardando con la `versionEsperada` que recibió.

Relacionado: [[Flujos de negocio]] · [[Seguridad]] · [[Decisiones]]
