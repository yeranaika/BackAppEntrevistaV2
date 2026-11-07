---
tipo: estado
tags: [decisiones, adr]
actualizado: 2026-11-06
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

## D16 · Readiness separada de liveness para el calentamiento HTTP (2026-11)
- **Decisión**: `/health` permanece como liveness; `/ready` informa 503 durante el calentamiento del evaluador freemium y 200 cuando termina o falla.
- **Por qué**: el despliegue debe enviar tráfico tras el calentamiento, pero un calentamiento fallido no debe dejar el servicio indisponible permanentemente.
- **Consecuencias**: Railway debe usar `/ready` como healthcheck; `FREEMIUM_HTTP_WARMUP=0` desactiva el calentamiento. `CallLogging` excluye `/health`, `/ready` y `/api/v1/practice/evaluate-freemium`.

## D17 · Tope mensual de evaluaciones con IA para premium (2026-11)
- **Contexto**: cada reporte evaluado con IA cuesta tokens y un usuario premium no tenía límite.
- **Decisión**: `LIMITE_EVALUACIONES_IA_MENSUAL` (por defecto 30; `0` = sin IA) por usuario y mes calendario UTC. Al
  llegar al tope el reporte se genera igual con el motor freemium: sin error y sin cambios en el contrato de Android.
- **Por qué**: el reporte siempre debe salir (D09). El cupo se **reserva** en la BD antes de llamar al LLM, bloqueando
  la fila del usuario (D08) con `READ COMMITTED` explícito, para que dos reportes simultáneos no superen el tope.
- **Consecuencias**: se cuentan evaluaciones con IA **entregadas** (reportes `listo` con modo `ia`), no llamadas pagadas;
  si la IA cae al freemium o el reporte termina en error, el cupo se devuelve. Un reporte `generando` con modo `ia` es
  una reserva que caduca a los 10 minutos (servidor reiniciado a mitad). Una evaluación con IA queda con la fecha de su
  reserva, así cuenta en el mes en que tomó el cupo aunque termine pasada la medianoche. Sin migración. El mes se corta a medianoche
  UTC (20:00 o 21:00 del último día en Chile). El usuario todavía no ve cuántas le quedan (pendiente con Android).

## D18 · Límite por IP compartido en Redis, con respaldo en memoria (2026-11)
- **Contexto**: el límite por IP vivía en la memoria de cada instancia: con N instancias el cupo real era N veces el configurado.
- **Decisión**: un contador de ventana fija en Redis (`limite:<nombre>:<ip>`, script Lua atómico) que comparten todas
  las instancias, a través de un `RateLimiter` propio para el plugin de Ktor. Si Redis no responde, cada instancia
  aplica el mismo límite en su memoria (el comportamiento anterior).
- **Por qué**: dejar pasar todo con Redis caído (*fail-open*) quitaría la protección contra registros masivos y envío
  de correos justo cuando hay problemas; bloquear todo (*fail-closed*) impediría registrarse. El respaldo en memoria
  degrada a lo que ya había. Un error del script no abre el cortocircuito de la caché (no apaga el mercado ni el login).
- **Consecuencias**: el contrato del 429 (`demasiadas_solicitudes`) no cambia; `Retry-After` se redondea hacia arriba.
  El bloqueo de login (`Cache.incrementar`) usa el mismo script y deja de tener la carrera INCR/EXPIRE.
  Hay **un limitador por límite, no uno por IP**: la IP viaja en el contexto de la corrutina de la solicitud. Así el
  plugin de Ktor, que solo limpia su registro cuando acepta una solicitud, no acumula en memoria las IPs que una
  instancia ve ya bloqueadas; el respaldo en memoria por IP se purga solo tras una ventana sin uso.

## D19 · IP del cliente: la conexión, o una cabecera de confianza validada (2026-11)
- **Contexto**: detrás del proxy de Railway, la conexión TCP trae la IP del proxy: todos los usuarios compartirían un cupo.
- **Decisión**: por defecto se usa la conexión (`request.local.remoteAddress`, sin DNS inverso). Con
  `CABECERA_IP_CLIENTE=X-Real-IP` se usa esa cabecera **solo si trae una IP literal válida**; `X-Forwarded-For` no se
  admite porque su primer valor lo controla el cliente. IPv6 cuenta por su `/64`.
- **Por qué**: si el proxy no sobrescribe la cabecera, cualquiera podría inventarse una IP nueva en cada solicitud y
  saltarse el límite. Hay que comprobarlo antes de activarla (prueba de humo en [[Puesta en marcha]]).
- **Consecuencias**: mientras la variable esté vacía, en Railway el cupo es global (compartido por todos los usuarios).
  La IP se resuelve una vez por solicitud y también es la que se guarda como evidencia del consentimiento
  (`ip_origen`). Por lo mismo, **no** se agrega límite por IP al login hasta activar la cabecera.

## D20 · Apagado ordenado: espera con tope en ApplicationStopping y rescate persistente (2026-11)
- **Contexto**: al reiniciar (cada despliegue en Railway) se cancelaban de golpe los reportes y correos en curso. Un
  reporte cortado quedaba en `generando` **para siempre**: nada lo retomaba y la app no permitía reintentarlo.
- **Decisión**: (1) en `ApplicationStopping` —Netty ya no acepta solicitudes y la BD y Redis siguen abiertos— se
  esperan las tareas en curso hasta `APAGADO_ESPERA_TAREAS_SEGUNDOS` (150) y después se cancelan; (2) un reporte
  cancelado por el apagado queda en `error` con `generacion_interrumpida`, el intento descontado y el cupo de IA
  devuelto; (3) `TareaRescateReportes`, al arrancar y cada 5 min, hace lo mismo con los reportes en `generando` cuyo
  intento empezó hace más de 10 min y crea el reporte en `error` de las entrevistas finalizadas (10 min – 24 h) que se
  quedaron sin él; (4) `railway.json` da 200 s de drenado (Railway da 0 por defecto).
- **Por qué**: la espera evita perder trabajo, pero el proceso puede morir igual (SIGKILL, OOM): la consistencia la
  garantiza el rescate, apoyado en la BD. No se regenera solo: no se gasta IA sin que el usuario lo pida y no hace
  falta coordinar instancias; el usuario ve el mismo «Generar de nuevo» de siempre (sin cambios en Android).
- **Consecuencias**: `tomarParaGenerar` fecha el inicio del intento (si no, el rescate podría robar un reintento
  vivo). Cada intento tiene un `intento_id`: el resultado y el error solo los escribe el intento dueño, así una
  generación que el rescate dio por abandonada no pisa a un reintento posterior. El intento cortado no cuenta para el
  máximo de 3. Un despliegue puede tardar hasta ~3 min en retirar la instancia vieja si hay reportes con IA en curso.
  El correo de recuperación cortado no se reenvía solo (el usuario pide otro). **Migración 019** (columna `intento_id`
  e índices): aplicarla **antes** de desplegar este código.

## D21 · Métricas de video idempotentes por (sesión, timestampMs) (2026-11)
- **Contexto**: la app reenvía un lote de métricas si no recibe la respuesta (red lenta, tiempo máximo al tocar
  «Terminar»). El backend generaba un `metrica_id` nuevo por fila, así que el lote quedaba guardado dos veces y
  pesaba el doble en el promedio de lenguaje corporal del reporte.
- **Decisión**: una métrica por instante de la sesión. Índice único `(sesion_id, timestamp_ms)` (migración 020, que
  también borra los duplicados existentes y el índice simple que queda redundante). `insertarLote` filtra los
  `timestampMs` que la sesión ya tiene y, dentro del lote, se queda con la primera de cada `timestampMs`; si otro
  envío entra justo entre la lectura y la inserción (violación del índice), reintenta una vez.
- **Por qué**: la app ya manda una métrica por ventana de 500 ms con `timestampMs` = inicio de la ventana, así que el
  par identifica la métrica sin cambiar el contrato (no hace falta un `loteId`). Una métrica ya guardada no se pisa.
- **Consecuencias**: `POST /metricas` sigue respondiendo `201`; `insertadas` cuenta solo las nuevas (0 si el lote ya
  estaba). Un cliente que mande dos métricas distintas con el mismo `timestampMs` pierde la segunda. **Migración
  020**: aplicarla antes de desplegar.

## D22 · Métricas de video sin emociones y lenguaje corporal «sin medir» (2026-11)
- **Contexto**: la app Android ya no envía emociones (RNF10) y filtra los gestos, pero el backend seguía aceptando
  `expresion` (seguro, nervioso…) y escribía «Se te notó nervioso…» en el reporte: cualquier otro cliente podía
  reintroducir la inferencia de emociones. Además, una entrevista sin video o en la que ninguna ventana tuvo evidencia
  (la app manda ventanas vacías cuando el detector no está seguro) guardaba `puntaje_lenguaje_corporal = 0` y el
  reporte mostraba «Lenguaje corporal 0/100» por un problema de la cámara y no de la persona.
- **Decisión**: (1) `expresion` se acepta por compatibilidad pero se ignora; se quita la frase del reporte y el
  cálculo de la expresión más frecuente. (2) De `gestos` solo se guardan las claves de `GESTOS_OBSERVABLES` (la misma
  lista que `GESTOS_PERMITIDOS` en la app); el resto se descarta sin error. (3) `puntajeLenguajeCorporal` es `null`
  cuando no se midió; el peso del 20 % se reparte como ya se hacía.
- **Por qué**: ignorar en vez de rechazar no rompe a clientes viejos y cierra la puerta a guardar emociones. `null`
  ya era el contrato de la vista (`puntajeLenguajeCorporal: Double?`) y Android ya oculta la barra cuando es `null`.
- **Consecuencias**: `confianza` sigue existiendo como puntaje de la persona, pero la app la manda en `null` (la
  confianza del detector no es eso). **Migración 021**: columna nullable sin default, reportes sin métricas con datos
  pasan de 0 a `null` y se borran las expresiones guardadas; aplicarla **antes** de desplegar este código.
