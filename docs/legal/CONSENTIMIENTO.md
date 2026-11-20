# Consentimiento de uso de datos (texto que muestra la app)

Fuente del texto que se publica con `POST /api/v1/legal/admin/eula` y que la app muestra en la pantalla de
consentimiento. La app lo muestra como **texto plano** (sin Markdown): el cuerpo va entre las líneas `---8<---`.
Al publicar una versión nueva, la app le pide a cada usuario que la acepte otra vez.

- **Versión:** `v1.1`
- **Título:** `Consentimiento de uso de datos`
- **Alcances (opciones que el usuario puede aceptar o rechazar):** `["analytics", "marketing"]`, los mismos que envía
  `ConsentViewModel` de la app.

Cambios frente a la v1.0: ya no habla de "grabaciones de video" ni del alcance `analisis_video_expresiones`, que
contradecían RNF09 (el video no se graba y el dictado se hace en el teléfono) y RNF10 (feedback sin emociones); dice
qué va al proveedor de IA y qué no; explica las dos opciones; y lleva a Perfil › Privacidad y consentimiento, donde la app (rama
`feature/gestionar-consentimiento`) permite cambiar las opciones y retirar el consentimiento. Publicar esta versión
**después** de que esa versión de la app esté instalada.

---8<---
Para practicar entrevistas en EntrevistaApp necesitamos tratar algunos de tus datos. Aquí te contamos cuáles, para qué y qué no hacemos con ellos.

1. Qué datos usamos
• Tu cuenta: correo, nombre y lo que eliges al empezar (área, cargo objetivo y nivel de experiencia).
• Tus prácticas: las respuestas que escribes o dictas, las opciones que marcas, tus puntajes y tu historial.
• El dictado por voz se convierte en texto dentro de tu teléfono. No enviamos ni guardamos tu audio.

2. La cámara en la simulación de entrevista
• La cámara solo se usa en la simulación tipo videollamada y solo si le das permiso a tu teléfono.
• No grabamos ni enviamos video ni fotos.
• Cuando la app mide conductas, como si tus manos están a la vista o cuánto mueves la cabeza, lo calcula en tu teléfono y solo envía números resumidos.
• No reconocemos tu rostro ni deducimos tus emociones.

3. Para qué los usamos
• Para evaluar tus respuestas y darte un reporte con tus fortalezas, lo que te falta reforzar y recomendaciones.
• En el plan gratuito, tus respuestas se evalúan con reglas en nuestro servidor.
• En el plan Premium, el texto de tus respuestas y las preguntas se envían a un proveedor de inteligencia artificial (hoy Xiaomi MiMo; también podríamos usar OpenAI o Google) para escribir tu reporte. Estos proveedores pueden procesar los datos fuera de Chile. No les enviamos tu nombre ni tu correo.
• No usamos tus respuestas para entrenar modelos de inteligencia artificial.

4. Dos opciones que tú decides
• Estadísticas de uso y errores: datos sobre qué partes de la app se usan y cuándo falla, para mejorarla. No incluyen tu nombre, tu correo ni lo que respondes.
• Comunicaciones: novedades y consejos de la app.
Si no las aceptas, la app funciona igual. Puedes cambiarlas cuando quieras en Perfil › Privacidad y consentimiento.

5. Seguridad y conservación
• Tus datos viajan cifrados y solo tú puedes ver tu historial.
• Los guardamos mientras tengas tu cuenta. Si la eliminas (Perfil › Editar perfil › Eliminar cuenta), se borran tu cuenta y tus prácticas.

6. Tus derechos
Puedes retirar este consentimiento cuando quieras en Perfil › Privacidad y consentimiento; para seguir practicando tendrás que aceptarlo de nuevo. También puedes pedir acceso, corrección o eliminación de tus datos, u oponerte a su uso, según la ley chilena de protección de datos personales (Ley 19.628, modificada por la Ley 21.719), escribiendo a privacidad@entrevistaapp.com.
---8<---
