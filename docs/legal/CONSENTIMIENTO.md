# Consentimiento de uso de datos (texto que muestra la app)

Fuente del texto que se publica con `POST /api/v1/legal/admin/eula` y que la app muestra en la pantalla de
consentimiento. La app lo muestra como **texto plano** (sin Markdown): el cuerpo va entre las líneas `---8<---`.
Al publicar una versión nueva, la app le pide a cada usuario que la acepte otra vez.

- **Versión:** `v1.1`
- **Título:** `Consentimiento de uso de datos`
- **Alcances (opciones que el usuario puede aceptar o rechazar):** `["estadisticas_uso", "marketing", "analisis_conducta_camara"]`.
  - `estadisticas_uso` (punto 4, estadísticas de uso y errores: Firebase en la app) **reemplaza a `analytics`**: la app
    anterior a la gestión del consentimiento enviaba `analytics` **premarcado**, así que esa clave no prueba que la
    persona lo eligiera. Con la `v1.1` vigente, un `analytics` se descarta y nadie queda con estadísticas encendidas sin
    haberlas marcado. La app (PR #20/#21, `ALCANCE_ANALYTICS`) debe enviar y leer `estadisticas_uso`.
  - `marketing` (punto 4, comunicaciones) lo envía `ConsentViewModel` desmarcado por defecto.
  - `analisis_conducta_camara` es el permiso aparte para medir conductas con la cámara: la app debe registrarlo (misma
    versión, con los demás alcances que ya tenía la persona) al tocar "Acepto, activar cámara" en el consentimiento de
    video. Hoy ninguna versión de la app lo envía (checklist C09). Con la `v1.1` vigente, sin ese permiso el backend
    no guarda nada medido con la cámara, y al retirarlo (`false` explícito o retirar el consentimiento) borra lo
    medido. Si el pedido no trae la clave, el backend conserva el permiso que la persona ya tenía (D38).
  - Una clave que la versión no ofrece se descarta al aceptar, y `GET /me/consent/latest` y la respuesta de
    `POST /me/consent` solo traen las que ofrece: la app debe tomar el estado de esa respuesta, no de lo que envió (D35).

Cambios frente a la v1.0: ya no habla de "grabaciones de video" ni del alcance `analisis_video_expresiones`, que
contradecían RNF09 (el video no se graba y el dictado se hace en el teléfono) y RNF10 (feedback sin emociones); dice
qué va al proveedor de IA y qué no; explica las dos opciones; y lleva a Perfil › Privacidad y consentimiento, donde la app (rama
`feature/gestionar-consentimiento`) permite cambiar las opciones y retirar el consentimiento. Publicar esta versión
**después** de que esa versión de la app (PR #21, que incluye el #20, ya con `estadisticas_uso`) esté publicada, porque el
texto promete esas pantallas. Publicarla con los tres alcances y con el backend de D35 y D38 ya desplegado (filtra
las claves y el mapa, exige el permiso de cámara y tiene la migración 029 aplicada), y con una app que ya registre
`analisis_conducta_camara` (C09): si no, nadie tendrá métricas de cámara. Antes de eso no hay riesgo de opt-ins falsos:
la app actual no envía `estadisticas_uso`. El punto 5 fija el plazo de 5 años del registro de una cuenta eliminada
(D38).

---8<---
Para practicar entrevistas en EntrevistaApp necesitamos tratar algunos de tus datos. Aquí te contamos cuáles, para qué y qué no hacemos con ellos.

1. Qué datos usamos
• Tu cuenta: correo, nombre y lo que eliges al empezar (área, cargo objetivo y nivel de experiencia).
• Tus prácticas: las respuestas que escribes o dictas, las opciones que marcas, tus puntajes y tu historial.
• El registro de este consentimiento: la versión, las opciones que eliges, las fechas y la dirección IP desde la que lo aceptas. La IP se borra si eliminas tu cuenta.
• El dictado por voz se convierte en texto dentro de tu teléfono. No enviamos ni guardamos tu audio.

2. La cámara en la simulación de entrevista
• La cámara solo se usa en la simulación tipo videollamada y solo si le das permiso a tu teléfono.
• No grabamos ni enviamos video ni fotos.
• Cuando la app mide conductas, como si tus manos están a la vista o cuánto mueves la cabeza, tu teléfono envía solo números: medidas ya calculadas o la posición de algunos puntos del cuerpo y de los ojos, nunca imágenes ni video. Esos puntos se procesan al momento y no se guardan; solo guardamos los resultados numéricos de tu práctica.
• No reconocemos tu rostro ni deducimos tus emociones.
• Para medir esas conductas te pedimos un permiso aparte la primera vez que activas la cámara. Si no lo das, puedes practicar sin cámara y no guardamos nada medido con ella. Si lo retiras, o retiras este consentimiento (punto 6), borramos lo que se había medido con la cámara.

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
• Los guardamos mientras tengas tu cuenta. Si la eliminas (Perfil › Editar perfil › Eliminar cuenta), se borran tu cuenta y tus prácticas. Solo guardamos el registro de que aceptaste este consentimiento (versión, opciones, fechas y tu correo) durante 5 años desde que eliminas la cuenta, como prueba ante un posible reclamo, y después lo borramos. Si hay un reclamo en curso, lo guardamos hasta que termine.

6. Tus derechos
Puedes retirar este consentimiento cuando quieras en Perfil › Privacidad y consentimiento; para seguir practicando tendrás que aceptarlo de nuevo. También puedes pedir acceso, corrección o eliminación de tus datos, u oponerte a su uso, según la ley chilena de protección de datos personales (Ley 19.628, modificada por la Ley 21.719), escribiendo a privacidad@entrevistaapp.com.
---8<---
