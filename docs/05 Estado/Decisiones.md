---
tipo: estado
tags: [decisiones, adr]
actualizado: 2026-11-17
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
  UTC (20:00 o 21:00 del último día en Chile). Desde D29 `/billing/status` le informa cuántas le quedan y la app lo
  muestra en Perfil › Mi suscripción (rama `feature/offline-automatico-y-reportes-ia` de la app, sin integrar).

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

## D23 · Tope mensual de gasto en la generación de preguntas con IA (2026-11)
- **Contexto**: `POST /api/v1/admin/preguntas/generar-ia` (y su alias `/api/v1/admin/questions/generate-ai`) llama al
  LLM en cada solicitud y no tenía ningún freno: un admin con un script, un panel en bucle o una sesión robada podían
  gastar sin límite. Además, un rechazo, un corte o una respuesta sin contenido del proveedor se auditaban sin tokens,
  aunque el proveedor los cobra.
- **Decisión**: (1) tope de gasto **global** por mes calendario UTC, `LIMITE_GASTO_GENERACION_IA_MENSUAL_USD` (5 USD
  por defecto; `0` = generación con IA desactivada; negativo o no numérico se toma como `0`). El gasto se suma desde
  `pregunta_generacion_ia` con `costoTotalUsd` (la misma cuenta que `GET /api/v1/admin/costos-ia`), incluidas las
  fallidas con su costo estimado. Se revisa **después** de validar el pedido y **antes** de llamar al proveedor; al
  llegar al tope responde `429 tope_generacion_ia_alcanzado` con un mensaje que dice cuándo se repone (el día 1 del
  mes siguiente, UTC; con tope `0` el mensaje dice que está desactivada). La variable acepta coma decimal (`0,5`); un
  valor ilegible desactiva la generación con un aviso en el log en vez de caer al valor por defecto. Para sumar se leen
  solo las columnas de costo, con un índice por `fecha_generacion` (migración 022). Si el gasto no se puede leer, el
  error sube y tampoco se llama. (2) `ErrorRespuestaExterna`
  lleva `tokensEntrada`/`tokensSalida`: `provider_refusal`, `provider_truncated`, `provider_missing_content` y
  `provider_invalid_response` se auditan con los tokens que informó el proveedor (nunca viajan al cliente);
  `provider_http_error` sigue sin tokens. Un tiempo agotado es `provider_timeout` y **no se reintenta** (el pedido ya
  salió y el proveedor pudo cobrarlo; antes se reintentaba y podía pagarse dos veces). (3) Límite anti-ráfaga por IP `LIMITE_GENERACIONES_IA_POR_IP` (30 cada
  60 min, clave Redis `limite:generacion_ia:<ip>`), común a la ruta y su alias → `429 demasiadas_solicitudes`. Corre
  después de `soloAdmin`: un 403 no consume cupo.
- **Por qué**: el gasto es lo que importa frenar y ya estaba registrado por llamada; contar solicitudes no lo acota
  (una de 10 preguntas cuesta más que una de 1). Es global porque `pregunta_generacion_ia` no guarda qué admin pidió
  la generación. Sumar las fallidas evita que un proveedor que rechaza en bucle gaste sin tope. El límite por IP corta
  ráfagas antes de que el tope mensual se agote en minutos.
- **Consecuencias**: sin reserva previa (a diferencia de D17): dos llamadas simultáneas pueden pasar el tope en lo
  que cueste una de ellas. Un admin agota el cupo de todos. Un `provider_timeout` cuenta 0 en el tope (no hay
  tokens), aunque el proveedor pudo cobrarlo. El índice va en la migración 022. El panel (rama
  `fix/textos-costos-ia` de Front-web-APPENTREVISTA) explica el 429 del tope, el `provider_timeout` y que las fallidas
  con tokens aparecen en Costos IA.

## D24 · Copia de la rúbrica por pregunta de entrevista y traza de IA coherente al editar (2026-11)
- **Contexto**: el snapshot de cada pregunta de entrevista copiaba enunciado, tipo, opciones y respuesta ideal, pero
  no la rúbrica: el reporte leía las palabras clave del banco al generarse. Editar la rúbrica entre el final de la
  entrevista y el reporte (o un reintento) cambiaba la nota. Además, editar una pregunta generada con IA la devolvía
  a `pendiente`, pero su traza en `pregunta_generacion_ia` seguía `aprobada`/`rechazada` con el revisor anterior.
- **Decisión**: (1) columna `sesion_pregunta_respuesta.rubrica_snap` (JSONB): la rúbrica al servir la pregunta
  (`'{}'` si no tenía). El reporte usa la copia; solo las filas sin copia (anteriores a la migración 022) recurren al
  banco y, si la pregunta se borró, se evalúan sin palabras clave como hasta ahora. (2) Editar una pregunta deja su
  traza de IA en `pendiente_revision` con `revisado_por` NULL, en la misma transacción. (3) **Migración 022**: crea la
  columna, completa las filas existentes con la rúbrica actual del banco (la misma que el reporte leía hasta hoy) y
  devuelve a `pendiente_revision` sin revisor las trazas de preguntas `pendiente` que estaban aprobadas o rechazadas.
- **Por qué**: es el mismo principio de los snapshots (editar el banco nunca cambia una prueba ya rendida) y la
  rúbrica es la que decide la nota del motor freemium. La traza de IA tiene que decir lo mismo que la pregunta.
- **Consecuencias**: la migración 022 es aditiva e idempotente, pero el código nuevo lee `rubrica_snap`: **aplicarla
  antes de desplegar** (si no, fallan los endpoints de entrevista y el reporte) y **volver a correrla una vez
  desplegado**, para completar las filas y trazas que el código anterior dejó mientras convivían. Sin cambios en el
  contrato HTTP.

## D25 · Semilla re-ejecutable sin índice único en el banco (2026-11)
- **Contexto**: `BasedeDatos.EntrevistaApp.Seeds.sql` usaba `ON CONFLICT DO NOTHING` en preguntas y opciones, pero
  esas tablas no tienen clave natural, así que no había conflicto que evitar: cada corrida volvía a insertar las 14
  preguntas y sus opciones, y el banco quedaba con copias.
- **Decisión**: cada pregunta se inserta solo si **no existe** otra con el mismo enunciado + skill + cargo + nivel
  (`NOT EXISTS`, con `IS NOT DISTINCT FROM` para el cargo), y las opciones (un `INSERT` por pregunta) solo si la
  pregunta aún no tiene ninguna. Los catálogos siguen con `ON CONFLICT` sobre su clave única. Verificación:
  `src/DB/PROBAR_SEMILLA.ps1` (crea y borra la BD desechable `semilla_prueba`).
- **Por qué**: se descartó un índice único en `pregunta`: el admin y los lotes de IA no capturan esa violación (darían
  500), un btree sobre `TEXT` largo tiene límite de tamaño y limpiar los duplicados existentes dejaría el historial con
  `pregunta_id` NULL (los snapshots apuntan a las copias).
- **Consecuencias**: si el admin editó el enunciado, la skill o el nivel de una pregunta de la semilla, volver a
  correrla crea otra vez la versión original; una pregunta rechazada o con opciones editadas no se toca. Las BD ya
  contaminadas por la semilla vieja **no se limpian** solas. Sin migración.

## D26 · Preguntas generadas con lo que pide el mercado y cobertura del banco (2026-11)
- **Contexto**: el backend anterior tenía `POST /jobs/generate-and-save`: buscaba una oferta real en JSearch y la IA
  escribía preguntas a partir de esa oferta (así salieron 647 preguntas). En V2 la sincronización semanal solo calcula
  qué skills pide cada cargo, y `generar-ia` recibía solo el nombre del cargo y de la skill. Si el mercado agregaba
  una skill a un cargo, nada avisaba que el banco no tenía preguntas para ella.
- **Decisión**: (1) `OfertasEnCache` envuelve al cliente de empleo: cada búsqueda que hace la sincronización queda
  guardada por consulta (`ofertas:consulta:<cargo>`, 8 días; el dataset de respaldo, 1 hora). (2) `generar-ia`, con
  cargo o skill y `usar_mercado` (por defecto `true`), arma con `ServicioContextoMercado` lo que piden las ofertas:
  las skills más pedidas y hasta 6 frases cortas de ofertas distintas. Usa primero las guardadas; si no hay, busca con
  un tope de 6 s (el proxy del panel corta a los 55 s y además hay que esperar al LLM). Si no alcanza, genera sin
  ofertas y la búsqueda sigue en segundo plano para la próxima vez (una sola búsqueda por consulta a la vez). Solo
  sirven las ofertas que mencionan la skill o, sin skill, las que comparten con el cargo una palabra del título que no
  sea de rol (developer, engineer, manager… no bastan; siglas como ML o QA sí; español-inglés) o piden al menos dos
  skills del cargo. Lo que se entrega es de la categoría pedida: unas preguntas blandas reciben solo habilidades blandas
  (si las ofertas no nombran ninguna, se genera sin ofertas). Se busca después de revisar el tope de gasto (D23). La
  respuesta trae `contexto_mercado` y el prompt auditado guarda las ofertas usadas. (3) `GET /api/v1/admin/preguntas/cobertura`
  cruza las skills de cada cargo con el banco: aprobadas y pendientes por nivel que sirven al cargo (de la skill y del
  cargo, o de la skill sin cargo, como `SelectorPreguntas`) y cuántas faltan para un mínimo (5 por defecto).
- **Por qué**: así las preguntas nuevas se parecen a lo que se evalúa hoy, sin volver a gastar cuota de las APIs de
  empleo en cada generación. No se genera solo: cada generación cuesta y queda pendiente de revisión, así que la
  decide un admin desde la cobertura (el panel la abre con el cargo, la skill y el nivel ya puestos).
- **Consecuencias**: los fragmentos son texto de terceros: se limpian de caracteres que abren bloques, van entre
  comillas y el prompt dice que son datos y no instrucciones; igual las preguntas pasan por revisión. El prompt pide
  escribir en español (las ofertas suelen venir en inglés). Unos 400 a 700 tokens más de entrada por generación. Sin
  migración y sin cambios en el contrato de Android. Las 647 preguntas del backend anterior no están en V2. El HTML de las
  ofertas se limpia con las etiquetas de bloque como saltos de línea (así `<strong>Kotlin</strong>` no parte la frase) y
  de una descripción larga se guarda el comienzo y el final (ahí van los requisitos y los tags). `GET /api/v1/admin/preguntas`
  acepta `paraCargoId` (del cargo o sin cargo) para listar las mismas pendientes que cuenta la cobertura.

## D27 · Una compra de Google Play solo activa la cuenta de la app que la pagó (2026-11)
- **Contexto**: el backend solo impedía que un mismo token de compra activara dos cuentas (se queda con él la primera
  que lo registra). La app abre cada compra con `obfuscatedAccountId` = hash de su cuenta y filtra en el teléfono, pero
  el backend no lo revisaba: una app antigua o modificada podía activar en una cuenta la compra que pagó otra.
- **Decisión**: `ClienteGooglePlay` lee `obfuscatedExternalAccountId` de `purchases.subscriptions.get` (v3) y
  `verificarCompraGoogle`, después de la regla de compra inválida y antes de registrar, lo compara (sin distinguir
  mayúsculas) con `cuentaOfuscadaDe(usuarioId)`: SHA-256 en hex minúscula de `UUID.toString()` en UTF-8, lo mismo que
  calcula la app (`ServicioPagos.cuentaOfuscada`) con el `id` de `/me`. Si no coincide → `409 compra_ya_registrada`
  con el mismo mensaje que el token de otra cuenta (una sola función, `compraDeOtraCuenta()`): la app ya lo muestra
  como «asociada a otra cuenta» y no reintenta. Si la compra no trae el campo (hecha desde la tienda de Play, código
  promocional, app antigua o `GOOGLE_PLAY_BILLING_MOCK`), se acepta y decide solo la regla del dueño del token.
- **Por qué**: un pago solo debe dar premium a la cuenta que pagó (RNF04, control de acceso), sin cambiar el contrato
  de la app ni migrar. El rechazo ocurre fuera de la política de reintentos; la cuenta ofuscada no se guarda ni va al
  log ni al mensaje, y se siguen ignorando los datos personales que trae la respuesta de Google (RNF05).
- **Consecuencias**: la fórmula del hash es **parte del contrato con la app**: si un lado la cambia, toda compra real
  responde 409 y la persona pagaría sin recibir premium (el mismo vector fijo, `3f2504e0-…` → `d1bfaf4a…`, se prueba
  en los dos lados: `PRUEBA_SERVICIO_SUSCRIPCION` en el backend y `FlujoCompraTest` sobre `ServicioPagos.cuentaOfuscada`
  en la app, rama `feature/offline-automatico-y-reportes-ia`, sin integrar). Una cuenta borrada y creada de nuevo tiene
  otro UUID: su suscripción anterior responde 409 y hay que cancelarla en Play. Un token que otra cuenta tomó antes de
  este cambio sigue siendo de esa cuenta (liberarlo queda como decisión pendiente). Si se migra a
  `purchases.subscriptionsv2`, el campo pasa a `externalAccountIdentifiers.obfuscatedExternalAccountId`. Sin migración
  ni cambios en el contrato de Android.

## D28 · Gasto del mes frente al tope en Costos IA (2026-11)
- **Contexto**: el tope de gasto de la generación con IA (D23) solo se notaba al recibir el 429; el panel no tenía
  cómo mostrar cuánto se gastó en el mes ni cuánto queda.
- **Decisión**: `GET /api/v1/admin/costos-ia` suma el campo `topeGeneracionPreguntas` con 7 claves: `mes` (AAAA-MM,
  UTC), `gastadoUsd`, `topeUsd`, `restanteUsd`, `fechaReposicion` (AAAA-MM-DD o `null`), `estaDesactivado` y
  `estaAgotado`. Describe siempre el mes UTC en curso, sin importar `desde`/`hasta`. Una sola fuente:
  `ServicioTopeGeneracionIa` calcula `estado()` y su `verificar()` (el 429) se apoya en él; el contenedor inyecta la
  misma instancia en la generación y en Costos IA. Sin endpoint nuevo.
- **Por qué**: el panel ya pide `costos-ia` (Dashboard, Informes, Generar con IA) con el mismo permiso: un campo
  aditivo no le cuesta otra petición. Con una sola fuente, lo que ve el panel es lo que decide el 429.
- **Consecuencias**: `gastadoUsd` y `restanteUsd` van redondeados a 6 decimales y `topeUsd` tal cual se configuró;
  `estaAgotado` se calcula sin redondear, así que el panel debe usar los booleanos y no recalcularlos. `restanteUsd`
  nunca es negativo (sin reserva, dos generaciones pueden pasar el tope, D23). `fechaReposicion` viaja aunque valga
  `null`. Una consulta indexada más por llamada; un `400` de rango no la hace. Sin caché: con varias instancias dejaría
  de ser una sola fuente. El mes UTC se calcula con la misma función que el cupo de reportes (`periodoMensualIa`,
  D29). El panel lo muestra en Informes y en Generar con IA (`TopeGeneracionIa.jsx`) y resumido en el Dashboard
  (`services/topeGeneracion.js`, prueba `tests/topeGeneracion.test.mjs`; rama `feature/tope-gasto-ia` del panel, sin
  integrar).

## D29 · Cupo de reportes con IA visible para el premium (2026-11)
- **Contexto**: D17 limita los reportes con IA de cada premium por mes, pero el usuario no sabía cuántos le quedaban.
- **Decisión**: `GET /billing/status` y `POST /billing/code/redeem` suman tres campos aditivos en snake_case:
  `reportes_ia_limite` (Int), `reportes_ia_restantes` (Int, entre 0 y el límite) y `reportes_ia_renovacion` (Long, ms
  epoch del inicio del mes siguiente, UTC). Llegan juntos solo si el usuario es premium, hay LLM configurado y el
  límite es mayor que 0; si no, se omiten. Lo usado se cuenta con **la misma condición SQL que la reserva** (una
  función compartida en `RepositorioReporteExposed`): reportes con modo `ia` del mes que estén `listo`, o `generando`
  con la reserva vigente (10 min). El conteo no bloquea la fila del usuario.
- **Por qué**: si las reservas vigentes cuentan, «la próxima reserva funciona si y solo si quedan más de 0» se cumple.
  `ServicioCupoIa` va aparte del reporte para no crear el ciclo suscripción ↔ reporte. La respuesta de un usuario
  gratis queda igual que antes (minimización, RNF05) y una app antigua ignora las claves nuevas (`ignoreUnknownKeys`).
- **Consecuencias**: el número puede subir (el cupo se devuelve si la IA cae al freemium o el reporte termina en
  error). Si `LIMITE_EVALUACIONES_IA_MENSUAL` se baja a mitad de mes, lo ya usado en el mes sigue contando y
  `reportes_ia_restantes` es el nuevo límite menos lo usado; queda en 0 (nunca negativo) solo si lo usado alcanza o
  supera el nuevo límite (`CupoIaMensual.restantes`). Por ejemplo, de 30 a 20 con 5 usados quedan 15, y con 25 usados
  quedan 0. La reserva usa el mismo límite nuevo, así que sigue valiendo «funciona si y solo si quedan más de 0».
  Si el conteo falla, el estado sale premium sin los campos (queda en el log). `estado()` sigue sin el cupo porque lo
  usa cada reporte.
  La app lo muestra en Perfil › Mi suscripción con la fecha formateada en UTC (en la hora de Chile mostraría el día
  anterior) y, si faltan los campos, no muestra nada (`textoCupoReportesIa`, prueba `CupoReportesIaTest`; rama
  `feature/offline-automatico-y-reportes-ia` de la app, sin integrar). Sin migración.

## D30 · Xiaomi MiMo como tercer proveedor de IA (2026-11)
- **Contexto**: el equipo decidió usar Xiaomi MiMo para generar preguntas. Su API de pago por uso es compatible con
  Chat Completions de OpenAI, pero no acepta `response_format: json_schema` (solo `json_object`). Además, la serie
  v2.6 trae el razonamiento encendido por defecto.
- **Decisión**:
  - **Proveedor:** `ProveedorMimo` llama a `https://api.xiaomimimo.com/v1/chat/completions`, que se puede cambiar
    con `MIMO_BASE_URL` (solo https). La clave va en la cabecera `api-key`, como en la documentación oficial.
  - **Cuerpo del pedido:**
    - `response_format: {type: json_object}`;
    - `thinking: {type: disabled}`, porque el razonamiento cobra y no aporta;
    - `max_completion_tokens: 8192`;
    - el JSON Schema del lote escrito en el prompt de sistema.
  - **Validación:** la respuesta se valida igual que la de los otros proveedores. Si no cumple, es
    `provider_invalid_response` y queda auditada con sus tokens. Si el modelo envuelve el JSON en un cerco de código
    o le suma texto, se recorta de la primera a la última llave. `content: null` es `provider_missing_content`;
    `finish_reason` `content_filter` es `provider_refusal`, y `length` u otro corte, `provider_truncated`.
  - **Modelos y precios:** `mimo-v2.6-flash` (por defecto, 0,14/0,28 US$ por millón de tokens de entrada/salida) y
    `mimo-v2.6-pro` (0,435/0,87). Son los precios de tiempo real sin caché de noviembre de 2026; con caché la
    entrada es más barata, así que el gasto queda estimado hacia arriba (lado seguro del tope D23).
  - **Enrutamiento:** el modelo elige el proveedor: `gpt-*` va a OpenAI, `mimo-*` a MiMo y el resto a Anthropic.
    Solo se monta el proveedor que tiene clave: sin ella, la respuesta es `503 provider_not_configured` antes del tope
    y de consultar las APIs de empleo, sin costo.
  - **Evaluación premium (D17):** usa el primer proveedor con clave, en el orden OpenAI, Anthropic y MiMo.
  - **Panel:** muestra MiMo V2.6 Flash como primer modelo del selector. Orden de despliegue: primero el backend con
    `MIMO_API_KEY` cargada y después el panel; si no, el modelo por defecto del panel daría `400 modelo_invalido` o `503`.
  - **Arreglo de paso (OpenAI):** Chat Completions manda `"refusal": null` en toda respuesta normal y se leía como el
    texto `"null"`, o sea como rechazo. Ahora se lee con `contentOrNull`, igual que `content`.
- **Pruebas**: `PruebaProveedorLlm` cubre el cuerpo enviado, la cabecera, la URL, el cerco, el corte con tokens, la
  falta de clave sin red, el error HTTP, la URL configurable, `content: null`, `content_filter`, otros cortes y el
  `refusal: null` de OpenAI. `PruebaServicioGeneracionPregunta` cubre el
  enrutamiento y el costo. Ninguna prueba llama a la API real.

## D31 · Credits del Token Plan de MiMo en vez del tope en USD (2026-11)
- **Contexto**: producción usa el Token Plan de MiMo (clave `tp-`, plan Lite: US$6 al mes por 4.100 millones de
  Credits). Es de precio fijo, así que el tope en USD de D23 (que estima el costo como si se pagara por uso) no dice lo
  que de verdad queda: el límite real son los Credits del plan. MiMo no tiene una API para leer el saldo; solo su
  consola lo muestra.
- **Decisión**:
  - **Cálculo propio:** `ServicioCreditosTokenPlan` suma los tokens de cada llamada a un modelo `mimo-*` registrada por
    el servidor (reportes de entrevista y generación de preguntas, también las fallidas) y los convierte con la tabla
    oficial de Credits por token: flash 100 de entrada y 200 de salida, pro 300 y 600 (un `mimo-*` desconocido cuenta
    como pro). La respuesta no dice qué parte de la entrada vino de caché, así que todo se cuenta sin caché: el gasto
    queda estimado hacia arriba. Lo que se use con la misma clave fuera de la app no aparece.
  - **Configuración:** `MIMO_TOKEN_PLAN_CREDITOS` (acepta separadores de miles; 0 lo apaga). Sin definir, se asume el
    plan Lite si `MIMO_API_KEY` empieza con `tp-`, y nada si no. `MIMO_TOKEN_PLAN_DIA_RENOVACION` (1–28, por defecto 1)
    es el día del mes en que se renueva el plan; el periodo va de ese día al mismo día del mes siguiente (UTC).
  - **Tope:** con Token Plan activo, `generar-ia` con un modelo `mimo-*` se frena por Credits (`429
    tope_generacion_ia_alcanzado`, el mismo código) y no por USD; los demás modelos siguen con el tope en USD (D23).
  - **Panel:** `GET /api/v1/admin/costos-ia` trae `planTokenMimo` (7 claves, o `null` sin Token Plan). El panel lo
    muestra en lugar del tope en pesos.
- **Pruebas**: `PruebaServicioCreditosTokenPlan` (tabla, periodo con día de renovación, borde de agotado, enrutamiento
  del tope por modelo, lectura de la variable) y `PruebaControladorCostosIa` (el JSON con y sin plan). Ninguna llama a
  MiMo.

## D32 · El feedback debe estar listo en menos de 5 min (2026-11)
- **Pedido:** el reporte de feedback (con o sin IA) tiene que estar disponible a menos de 5 min de finalizar la entrevista.
- **Decisión:**
  - **Tope de la IA:** `TIEMPO_MAXIMO_EVALUACION_IA` = 45 s para toda la evaluación (reintentos del proveedor incluidos).
    Pasado el tope sale el reporte con el motor freemium y **no** consume cupo (D17). Antes el peor caso era ~2,3 min
    (70 s × 2 intentos) y el rescate dejaba un reporte atascado hasta ~15 min.
  - **Rescate:** cada 1 min (antes 5) y un reporte en "generando" se da por abandonado a los 3 min
    (`VIGENCIA_RESERVA_IA`, antes 10). Debe ser mayor que el tope de la IA (45 s).
  - **Palabras clave del motor freemium:** `C#`, `C++`, `F#` y `.NET` se normalizan a letras (antes `C#` quedaba como
    `c` y coincidía con casi cualquier texto); la coincidencia empieza en inicio de palabra, y las claves de 3 letras
    o menos (`go`, `sql`) exigen la palabra completa.
  - **Tiempos por reporte** en el log (`Reporte … listo en … ms (cálculo, guardado, modo)`) para medir p50/p95.
- **Medido (2026-11-19, MiMo `mimo-v2.6-flash`, 5 respuestas abiertas):** freemium 60–120 ms; con IA 24 s. E2E fase 7
  con `-ConIa` 25/25.
- **Pendiente (P1):** feedback por niveles (freemium básico con respuesta ideal; premium detallado con "por qué
  falló"), reporte en dos fases, evaluación en lotes paralelos. Ver `Analisis feedback lento.md`.
- **Pruebas**: `PruebaEvaluadorRespuesta` (símbolos y palabras cortas) y `PruebaServicioReporte` (IA lenta → freemium
  sin consumir cupo; tiempos del rescate).

## D33 · Feedback freemium con comparador de textos y feedback general por niveles (2026-11)
- **Pedido:** el feedback gratuito no usa IA ni tokens; da un feedback general (malo / medio / bueno) que invita a premium
  (esquema "tipos de feedback": freemium → general → malo, medio, bueno; fomento de uso → premium → IA → personalizado).
- **Comparador (`ComparadorTextos`, sin IA):** mezcla coseno TF-IDF (40 %), cobertura (30 %), Jaccard (20 %) y parecido de
  orden por Levenshtein (10 %); sin tildes, sin palabras vacías y con el plural quitado; se queda con la mejor referencia.
  El motor freemium toma `max(trigramas con la ideal, comparador)` como similitud: nunca puntúa menos que antes.
- **Rúbrica, campos opcionales:** `respuestas_alternativas` (hasta 5 textos: otras formas correctas de responder) y
  `sinonimos` (hasta 20 grupos de 2 a 10 términos: "deadlock" = "interbloqueo" = "bloqueo mutuo"). Una palabra clave
  cuenta si aparece cualquiera de sus sinónimos. Se validan al crear la pregunta (`alternativas_invalidas`,
  `sinonimos_invalidos`); la IA los genera (esquema y prompt de `generar-ia`), pero un lote sin ellos sigue siendo válido.
- **Resumen freemium del reporte:** nivel general (bueno ≥ 75, medio ≥ 60, bajo), skills a reforzar, hasta 4 conceptos
  que más faltaron y un aviso de lo que suma el premium. El contrato JSON de Android no cambia (solo el texto de `resumen`).
- **Pendiente:** la práctica (`CorrectorRespuestas`) aún no usa alternativas ni sinónimos; calibrar pesos con respuestas
  reales; arreglar la validación STAR obligatoria y las palabras clave de 2 letras del lote de IA (ver
  `Analisis feedback lento.md`).
- **Pruebas**: `PruebaComparadorTextos` (11 casos nuevos en total con las de validación, reporte y esquema de IA).

## D34 · STAR solo para preguntas de comportamiento y claves de 2 letras en la generación con IA (2026-11)
- **Problema:** el validador del lote de IA exigía método STAR (y las palabras Situación/Tarea/Acción/Resultado en la
  respuesta ideal) a todas las preguntas, también a las técnicas: la IA fallaba (llamada pagada perdida) o escribía
  respuestas artificiales que luego se mostraban como "respuesta ideal". Además una palabra clave de menos de 3 letras
  ("Go", "C#") invalidaba el lote completo.
- **Decisión:** la categoría `blanda` mantiene STAR completo (3 oraciones, método `STAR`); la `tecnica` pide método
  `TECNICA` (se acepta `STAR` por compatibilidad), 2 oraciones y 4 criterios. El esquema de `generar-ia` y el prompt
  cambian según la categoría. Las palabras clave admiten 2 letras (`Go`, `C#`); una sola letra sigue siendo inválida.
- **Pruebas**: `PruebaValidadorLotePreguntaIa` (5 casos). Sigue pendiente aceptar lo válido de un lote parcial y rechazar
  preguntas abiertas manuales con rúbrica vacía.

## D35 · Consentimiento v1.1 y documentos legales alineados con RNF09/RNF10 (2026-11)
- **Contexto**: el consentimiento v1.0 de la semilla hablaba de "grabaciones de video" y del alcance
  `analisis_video_expresiones`, y prometía revocar "desde Configuración > Privacidad", que no existe en la app. La
  Política de Privacidad y los Términos (1.0.0) hablaban de "transcripciones de audio", "respuestas grabadas" y
  "análisis multimodal", sin nombrar la cámara, el proveedor de IA ni Firebase. Todo eso contradice RNF09 (el video no se
  graba y el dictado se hace en el teléfono) y RNF10 (feedback sin emociones). Además, `POST /api/v1/legal/admin/eula`
  no permitía indicar los alcances, así que una versión publicada por la API quedaba sin `alcances_posibles` y el mapa
  de `GET /me/consent/latest` no traía en `false` lo que el usuario rechazó.
- **Decisión**:
  - Texto nuevo `v1.1` en `docs/legal/CONSENTIMIENTO.md` (fuente única; la app lo muestra como texto plano), con los
    alcances que ya envía la app: `["analytics", "marketing"]`. Dice qué datos se usan, que el audio y el video no salen
    del teléfono, que no se reconoce el rostro ni se deducen emociones, qué va al proveedor de IA en Premium (sin
    nombre ni correo, posiblemente fuera de Chile), que no se entrena IA con las respuestas, y los derechos según la Ley
    19.628 modificada por la Ley 21.719.
  - `SolicitudPublicarTexto` acepta `alcances` (opcional): en una versión nueva sin `alcances` queda la lista vacía; al
    republicar una existente sin `alcances` se conservan los que tenía.
  - Política de Privacidad y Términos pasan a `1.1.0` (`VERSION_DOCUMENTOS_ESTATICOS`); la semilla carga la `v1.1`.
  - En producción la `v1.1` se publica por la API (no por la semilla, que no pisa la `v1.0` existente): al publicarla,
    la app pide a cada usuario aceptar otra vez. Publicarla **con** `alcances: ["estadisticas_uso", "marketing",
    "analisis_conducta_camara"]` (si no, queda sin opciones y se descarta todo lo que la app envíe), **después** de
    publicar la app con el PR #21 (que incluye el #20) y con el backend de D35 ya desplegado: el texto promete cambiar
    las opciones y retirar el consentimiento en Perfil, y eso llega con el #21.
- **`estadisticas_uso` en vez de `analytics`**: la app de `dev` envía `analytics` **premarcado** (`ConsentViewModel`), así
  que esa clave no prueba un opt-in real (Ley 21.719). Si la `v1.1` ofreciera `analytics`, quien la aceptara con esa app
  quedaría guardado como que eligió las estadísticas y, al actualizar a la app con Firebase (PR #20), se encendería el
  monitoreo. Con una clave que la app vieja nunca envía, su `analytics` se descarta, sin importar el orden de los
  despliegues. La app (PR #20/#21, `ALCANCE_ANALYTICS`) debe pasar a `estadisticas_uso`; mientras no lo haga, Firebase
  queda apagado (falla del lado seguro).
- **Permiso aparte para la cámara (checklist C09)**: la `v1.1` ofrece además `analisis_conducta_camara`
  (`ALCANCE_ANALISIS_CAMARA`): medir conductas con la cámara en la simulación tipo videollamada. El texto lo explica
  ("te pedimos un permiso aparte la primera vez que activas la cámara") y el consentimiento general no lo marca. La app
  debe registrarlo al tocar "Acepto, activar cámara": `POST /me/consent` con la misma versión, los alcances que ya
  tenía la persona y este en `true` (y en `false` si lo retira), sin perderlo al cambiar las otras opciones. Va en la
  misma `v1.1` para que nadie tenga que aceptar dos versiones seguidas. Exigirlo en `/metricas` y borrar las métricas al
  retirarlo es la tarea C10 (cuando la app ya lo registre, para no rechazar las métricas de todos). Hecho en D38.
- **Solo cuentan los alcances que ofrece la versión**: en `POST /me/consent` una clave que el texto no ofrece
  (`ia_entrenamiento`, `analytics`, una inventada) **se descarta** y queda en el log; no se rechaza el pedido, para que
  la app actual pueda seguir aceptando aunque en producción rija una versión sin sus opciones (un 400 la habría dejado
  sin poder practicar). Y el mapa de `alcances` que devuelven `POST /me/consent` y `GET /me/consent/latest` trae **solo**
  los que ofrece la versión: un `analytics` que se guardó antes de este filtro (sobre la `v1.0`, que no lo ofrece) ya
  no sale en `true`. La app debe tomar el estado de ese mapa, no de lo que envió.
- **Una versión ya aceptada no se cambia**: publicar una versión existente con otro título, cuerpo o alcances responde
  `409 version_con_aceptaciones` si alguien ya la aceptó (cambiaría lo que esa persona aceptó: va en una versión nueva).
  Republicarla igual solo la vuelve a dejar vigente, y una que nadie aceptó se puede corregir.
- **EULA de respaldo `1.1.0`** (`docs/legal/EULA.md`, se publica solo si la BD no tiene ningún texto): ya no ofrece
  usar los datos "con fines de mejora del modelo"; dice que no se entrena IA con las respuestas, la voz ni la imagen,
  que la cámara no graba y que rige la Ley 19.628 modificada por la Ley 21.719.
- **App**: Perfil › Privacidad y consentimiento permite cambiar las opciones (vuelve a aceptar la misma versión con
  los alcances nuevos) y retirar el consentimiento (`POST /me/consent/revoke`; la app lo pide de nuevo antes de
  seguir). Rama `feature/gestionar-consentimiento` de la app: publicar la `v1.1` después de instalar esa versión.
- **Pendiente**: el correo `privacidad@entrevistaapp.com` debe ser uno real que el equipo revise; evaluación de impacto
  breve según la Ley 21.719 y fecha de vigencia de esa ley (checklist C08); app: registrar el permiso de cámara (C09).
- **Pruebas**: `PruebaServicioConsentimiento` (alcances guardados, conservados al republicar, mapa con los rechazados
  en `false`, alcance vacío rechazado, claves no ofrecidas descartadas, `analytics` premarcado descartado en la `v1.1`,
  fila anterior al filtro que no sale en el mapa, versión sin alcances, permiso de cámara aparte, 409 al cambiar una
  versión aceptada y reactivación) y el flujo HTTP de `PruebaControladorFase4` (lo que manda la app
  y el 409). `PROBAR_SEMILLA.ps1` con la `v1.1` cargada con saltos de línea y sus tres alcances.

## D36 · Feedback genérico por pregunta: correcto, medio y malo (2026-11)
- **Pedido:** que cada pregunta generada traiga 3 feedbacks genéricos (correcto, medio, malo) y un método para completar
  las preguntas que ya están en el banco y no tienen alguno de los tres.
- **Dónde vive:** en la rúbrica, `rubrica_evaluacion.feedback_generico = {"correcto": "...", "medio": "...", "malo": "..."}`
  (textos de hasta 500 caracteres). Sin migración: la rúbrica ya es JSONB. Puede estar incompleto (falta un nivel).
- **Generación (`generar-ia`):** el esquema estricto y el prompt piden los tres textos (específicos de la pregunta, en
  segunda persona, sin revelar la respuesta). Un texto vacío o de más de 500 caracteres no invalida el lote (la llamada
  ya se pagó): se guarda lo que sirve y queda pendiente lo demás. Un lote sin feedback sigue siendo válido.
- **Admin (crear/editar):** `feedback_generico` es opcional; si viene, debe ser un objeto con solo esas claves y textos no
  vacíos de hasta 500 caracteres (`feedback_generico_invalido`).
- **Completar las existentes:** `GET /api/v1/admin/preguntas/feedback-generico` (cuántas no rechazadas tienen algún nivel
  faltante) y `POST /api/v1/admin/preguntas/feedback-generico/completar` con `{modelo, limite (1..50), pregunta_ids,
  sobrescribir}`. Toma las más antiguas que faltan (o las de `pregunta_ids`), manda 10 por llamada al LLM, **solo agrega
  los niveles que faltan** (con `sobrescribir` reemplaza los tres) y **no cambia el estado** de la pregunta (una
  aprobada sigue aprobada). Cada llamada pasa por el tope mensual (D23), queda en `pregunta_generacion_ia` sin
  `pregunta_id` (por eso cuenta en Costos IA) y comparte el límite por IP de `generar-ia`. Lo que la IA devuelve mal
  (número repetido o fuera de rango, texto vacío o largo) se omite y queda pendiente; si falla una llamada a mitad de
  camino se responde lo ya guardado con `detenido_por`. Para todo el banco: llamar varias veces hasta `pendientes = 0`.
- **Uso al corregir (freemium, sin IA):** nivel según el puntaje con los cortes del texto estándar (correcto ≥ 80, medio
  ≥ 60, malo < 60). La respuesta abierta muestra el texto propio del nivel y, si no es correcta, "Repasa: <conceptos que
  faltaron>". La opción múltiple antepone el texto correcto o malo a la explicación de la opción. Sin texto para ese
  nivel se usa el feedback estándar de antes. La entrevista lo toma de la copia de la rúbrica (`rubrica_snap`, D24):
  completar el banco no cambia entrevistas ya servidas. La práctica lo guarda en la pregunta servida (`feedback_generico`).
- **Contrato Android:** sin cambios de forma; solo cambia el texto de `feedback`/`observacion`.
- **Pendiente:** mandar `feedback_generico` en la descarga del banco para el ciclo sin conexión de la app; que el panel
  web muestre y edite los tres textos y tenga un botón para completar.
- **Pruebas**: `PruebaFeedbackGenerico` (17 casos: modelo, esquema y prompt de IA, validación del admin, corrección
  abierta y de opción múltiple, completar por lote o por id, omitidos, fallo a mitad de camino, límites y permisos).

## D37 · Sin clips de video en las respuestas y modelo de visión de ejemplo deprecado (2026-11)
- **Contexto**: `POST /api/v1/entrevistas/{id}/respuestas` aceptaba y guardaba `videoClipUrl` (una URL https), y una
  pregunta de simulación en video se podía responder solo con el clip. El consentimiento `v1.1` (D35) dice que no se
  graba ni se envía video (RNF09) y la app no lo envía. La semilla registraba un modelo de visión `activo` que
  "detecta expresiones faciales", con una precisión inventada, contra RNF10 y D22 (checklist C53 y parte de C47).
- **Decisión**:
  - `videoClipUrl` se sigue aceptando en el JSON (para no romper un cliente antiguo con 400) pero **se ignora y no se
    guarda**. Toda pregunta abierta o de video se responde con `texto` (escrito o dictado); sin texto, `400
    respuesta_requerida`. La columna `video_clip_url` queda para las respuestas anteriores (no se borran datos) y
    `respuesta.videoClipUrl` sale `null` en las nuevas.
  - La fila de ejemplo del modelo de visión queda `deprecado`, sin la etiqueta `expresion_dominante` ni métricas.
    Semilla corregida y `migrations/028_modelo_vision_semilla_sin_expresiones.sql` para una BD ya sembrada
    (idempotente; el código no lee esa tabla, así que se puede aplicar antes o después de desplegar). Era la 023 en
    el PR #7; pasó a 028 porque `main` ya traía `023_version_modelo.sql`. El modelo real llegó con `main`
    (`motor-vision`) y se identifica con el texto `metrica_video.version_modelo` (opción (a) de C02), no con esta tabla.
- **Contrato Android**: sin cambios (la app nunca envió `videoClipUrl`).
- **Pruebas**: `PruebaServicioEntrevista` (la pregunta de video exige texto y no guarda el clip) y
  `PruebaControladorEntrevista` (el JSON con `videoClipUrl` responde 200 y el clip no aparece).

## D38 · Permiso de cámara exigido y plazo de 5 años para el consentimiento de una cuenta eliminada (2026-11)
- **Contexto**: al juntar `main` con `DEV` (PR #10), la revisión encontró que el texto `v1.1` (D35) prometía dos cosas
  que el código no cumplía, y el 409 de D35 impide corregirlo cuando alguien lo acepta:
  - `POST /api/v1/vision/predecir` (de `main`) recibe puntos del cuerpo y de los ojos por cuadro, infiere en el
    servidor y guarda en `prediccion_simulacion` sin mirar el permiso `analisis_conducta_camara`; y `/metricas` tampoco
    lo miraba (era la tarea C10).
  - La migración 024 conserva el consentimiento al borrar la cuenta (con el correo y la IP), sin plazo, y el texto
    decía que se borraba todo.
- **Decisión — permiso de cámara** (cierra C10 en el backend):
  - `ServicioConsentimiento.permite(usuario)`: si la versión del consentimiento de la persona (o la vigente, si no
    aceptó ninguna) ofrece `analisis_conducta_camara`, hace falta haberlo aceptado. Una versión que no lo ofrece (la
    `v1.0` de Railway) sigue como antes, para no cortar las métricas de todos mientras rige.
  - Sin el permiso, `POST /entrevistas/{id}/metricas` responde `201` con `insertadas: 0`, `/vision/predecir` responde
    `403 permiso_camara_requerido` sin procesar los puntos, y `POST /llamadas` guarda la llamada solo con la duración y
    las preguntas: la práctica sigue sin cámara, como dice el texto.
  - **Qué cuenta como retirarlo:** `analisis_conducta_camara: false` en `POST /me/consent`, retirar el consentimiento,
    o pasar de una versión sin el permiso aparte (v1.0, permiso implícito) a una que lo ofrece sin aceptarlo. Si el
    pedido no trae la clave, se conserva el permiso que la persona ya tenía, también al aceptar una versión nueva que lo
    ofrece: la pantalla general y la de Perfil no lo editan, se da y se quita aparte.
  - Al retirarlo se borra lo medido con la cámara en todo lo de la persona (`BorradorDatosCamara`): métricas de video,
    predicciones de la simulación, el resumen de sus llamadas en vivo (acciones, línea y feedback) y, en sus reportes,
    el puntaje de lenguaje corporal y sus dos frases. Como en el borrado biométrico por sesión, el puntaje global y el
    resumen del reporte se quedan.
  - El borrado va **antes** de guardar el consentimiento o de revocarlo, y revocar borra siempre (también si responde
    404): si algo falla, un reintento vuelve a borrar. Después de guardar métricas, predicciones o una llamada se vuelve
    a mirar el permiso (`confirmarDespuesDeGuardar`): si se retiró mientras tanto, se borra lo recién guardado.
  - El texto `v1.1` ya no dice "solo envía números resumidos": dice que el teléfono envía solo números (medidas o la
    posición de algunos puntos del cuerpo y de los ojos, nunca imágenes), que esos puntos no se guardan y que al
    retirar el permiso se borra lo medido. La Política (1.2.0) y el EULA de respaldo (1.2.0) dicen lo mismo, y el
    punto 1 del texto `v1.1` nombra la IP del registro del consentimiento.
- **Decisión — plazo del consentimiento de una cuenta eliminada: 5 años desde el borrado.** Investigado y verificado
  en el texto publicado en el Diario Oficial (Ley 19.628 modificada por la Ley 21.719):
  - La ley no fija años: los datos se guardan solo lo necesario y después se suprimen o anonimizan (art. 3 c). Guardar la
    prueba es necesario porque el responsable debe probar que tuvo el consentimiento (art. 12, inciso final) y la
    supresión no procede si hace falta para defender un reclamo (art. 7, excepción vi; art. 13 e).
  - Los 5 años cubren los 4 que tiene la Agencia para perseguir una infracción (art. 40, desde que cesa: el borrado) y
    los 5 de la acción ordinaria del Código Civil (art. 2515). Hasta que rija el régimen nuevo, el art. 2332 del Código
    Civil da 4 años. Los 5 años del art. 47 corren desde una multa firme, así que se cubren con la retención legal.
  - La Ley 21.855 (Diario Oficial del 23-11-2026) posterga la vigencia de la Ley 21.719 al **15-10-2027**. Se aplica
    desde ya porque también cumple el régimen actual.
  - Al borrar la cuenta se borra la IP (ninguna norma la exige para probar el consentimiento y el art. 3 c pide solo lo
    necesario) y se cierra el consentimiento (`fecha_revocado`). Se guardan la versión, las opciones, las fechas y el
    correo. `cuenta_eliminada_en` marca desde cuándo corre el plazo.
  - `TareaPurgaConsentimientos` borra cada día los registros vencidos. `retencion_legal = TRUE` (a mano, por SQL) los
    conserva mientras haya un reclamo, un procedimiento de la Agencia o un juicio. Una fila de cuenta borrada sin fecha
    (borrada por SQL o por el backend anterior con la 029 ya aplicada) se completa en la purga como en la 029.
  - El plazo está en el punto 5 del texto `v1.1`, en la Política de Privacidad 1.2.0 (el art. 14 ter i obliga a
    informarlo) y en `DELETE /cuenta`.
  - Lo que debe validar un abogado: que el plazo del art. 40 corra desde el borrado y que la vía contractual de 5 años
    aplique. Con 4 años también se cumpliría la ley si se descarta la vía contractual.
- **Migración 029** (idempotente): `cuenta_eliminada_en`, `retencion_legal` e índice; para las cuentas eliminadas antes
  de ella borra la IP, cierra el consentimiento y cuenta el plazo desde el día en que se aplica.
- **Contrato Android**: sin cambios de forma. Con la `v1.1` vigente, la app tiene que registrar
  `analisis_conducta_camara` (C09) para que se guarden las métricas: sin eso `insertadas` sale en 0.
- **Pruebas**: `PruebaPermisoCamara` (12: guardar sin permiso, qué cuenta como retirarlo, llamadas y reportes,
  reintento del retiro), `PruebaPurgaConsentimientos` (2), `PruebaEliminarCuentaConsentimiento` (IP, cierre y fecha de
  borrado), el 403 en `PruebaControladorVision`, versiones de la Política y el EULA, y `PROBAR_SEMILLA.ps1` con la 029.
  Una revisión adversarial de 4 ángulos (lógica, BD, contrato con la app, textos legales) encontró las llamadas, los
  reportes, el retiro implícito de la v1.0, la atomicidad del borrado y la IP del punto 1; todo quedó corregido.

## D39 · `/pruebas/video` solo con una variable, apagada por defecto (2026-11)
- **Contexto**: `POST /pruebas/video` y `GET /pruebas` (T4.6, de `main`) sirven para calibrar los umbrales del modelo de
  visión: la app compilada con `-PgrabarPrueba=true` sube el `.mp4` de una llamada de prueba. Se montaban siempre, sin
  sesión: cualquiera podía subir videos de hasta 200 MB a la carpeta `pruebas/` del servidor y listar los nombres. Eso
  contradice D37 y el texto del consentimiento (no se graba ni se envía video) y abre la puerta a llenar el disco.
- **Decisión**: las dos rutas se montan solo con `HABILITAR_PRUEBAS_VIDEO=true` (`ConfiguracionGeneral.pruebasVideoHabilitadas`,
  `controladorPruebaVideoSiHabilitado`). Por defecto no existen (404) y en Railway la variable no se define. Al
  arrancar con la variable activa queda un aviso en el log. Solo el literal `true` la activa, y dentro de Railway
  (`RAILWAY_PROJECT_ID` o `RAILWAY_ENVIRONMENT_ID`) el servidor no arranca con ella activa. Se mantienen sin sesión
  porque la app de calibración no manda token; son una herramienta local.
- **Riesgo aceptado**: con la variable activa no hay sesión, tope de disco ni límite de frecuencia: cualquiera en la
  misma red puede subir varios `.mp4` de 200 MB. Se acepta porque se activa solo mientras se calibra, en una máquina
  propia, y `pruebas/` se vacía al terminar (`docs/04 Guias/Puesta en marcha.md`).
- **Contrato Android**: sin cambios para la app normal; la grabación de prueba (`-PgrabarPrueba=true`) solo funciona
  contra un backend propio fuera de Railway con la variable activa.
- **Después de desplegar en Railway**: revisado el 2026-11-24, los despliegues de `main` con las rutas abiertas fallaron
  por el puerto 8093 y nunca llegaron a producción. Igual, comprobar que
  `POST /pruebas/video` responde 404 y, si el servicio tiene un volumen en el directorio de trabajo, borrar a mano
  `pruebas/*.mp4` (sin volumen, el redeploy ya los elimina).
- **Pruebas**: `PruebaControladorPruebaVideo` (404 y nada escrito sin la variable; montadas con ella) y
  `PruebaConfiguracionPruebasVideo` (solo `true`, falla en Railway y las rutas de producción usan la condición).

## D40 · Respuesta ideal en el formato que ordena la app (2026-11)
- **Contexto**: la app (`RESPUESTA_IDEAL.kt`, en `DEV` de la app) ordena la tarjeta "Respuesta ideal": `S: … T: … A: … R: …`
  como cuatro bloques y los pasos `1) 2) 3)` como viñetas; lo demás lo muestra como párrafo. En Railway (2026-11-24) casi
  ninguna respuesta lo seguía: 0 de 73 blandas abiertas con STAR y 0 de 178 técnicas abiertas con pasos. La generación
  con IA no lo pedía y el validador exigía las palabras "situación", "tarea", "acción" y "resultado".
- **Decisión**:
  - El prompt de `generar-ia` pide el formato en las abiertas y de video: blandas con `S:`, `T:`, `A:` y `R:` (pasos de
    la acción numerados) y técnicas con una frase de resumen y pasos numerados, sin encabezados, flechas ni llaves.
    Opción múltiple sigue igual.
  - El validador normaliza lo que devuelve la IA (`FormatoRespuestaIdeal.normalizar`): quita el encabezado ("Respuesta
    ideal:", "Respuesta ideal (STAR):", con o sin negrita), cambia "Situación:", "Tarea:", "Acción:" y "Resultado:" por
    las letras si vienen las cuatro en orden (también "S: Situación:" y "**Situación:**"), y quita negritas y viñetas
    alrededor de las marcas. En una blanda abierta o de video exige las cuatro marcas en orden y una sola vez cada una
    (`respuesta_sin_formato_star`): una "R:" de más ("el lenguaje R: …") partiría mal la tarjeta. Con las marcas ya no
    pide las palabras completas, y los pasos "1) 2)" cuentan como oraciones aunque no terminen en punto.
  - Las preguntas que crea o edita el admin se guardan tal cual: cambiar su texto rompería la paridad del revisor local
    con la app (casos dorados), que calculan sobre el mismo texto descargado.
  - La corrección freemium compara **sin las marcas** (`sinMarcas`), en la ideal y en lo que escribe la persona (que
    puede copiar el formato de la tarjeta): "S:" o "1)" no cuentan como palabras ni alargan el texto, así que el formato
    no cambia el puntaje. Igual que la app, los números de paso se quitan por bloque y solo donde hay dos o más.
- **Pendiente**: reescribir al formato las 255 respuestas abiertas que ya están en Railway (73 blandas, 4 de video y 178
  técnicas), sin cambiar su contenido. El revisor local (`REVISOR_TEXTO_LIBRE`, backend y app) todavía cuenta las marcas
  como palabras: quitarlas a la vez en los dos para no romper la paridad.
- **Contrato Android**: sin cambios.
- **Pruebas**: `PruebaFormatoRespuestaIdeal`, el validador (STAR con letras, palabras completas convertidas, rechazo sin
  marcas, opción múltiple sin exigirlas, técnica con pasos sin punto), el prompt y la corrección freemium (mismo puntaje
  con o sin marcas, también si la persona las copia). Una revisión adversarial de 3 ángulos (paridad con la app,
  validador y generación, puntaje) encontró 9 casos, todos corregidos.


## D41 · Voz en la llamada: transcripción por pregunta y diccionarios de terminología (2026-11)
- **Qué**: `POST /api/v1/transcripciones` guarda el texto de lo que la persona respondió a cada pregunta de la llamada, y
  `/api/v1/diccionarios` (+ `/admin/diccionarios`) sirve paquetes de terminología por área, editables desde un panel.
  Migración `030`. Detalle y criterios VOZ-1 a VOZ-5 en [[Voz en la llamada]].
- **Por qué texto y no audio**: RNF09/VOZ-5. El reconocimiento corre en el teléfono; el servidor no recibe voz, así que no hace
  falta un consentimiento nuevo. Un ASR en servidor o nube sí lo necesitaría (T0.8) y no cambia este contrato.
- **Por qué diccionarios en datos y versionados**: sumar áreas o términos no debe exigir una app nueva. La `version` sube en
  cada edición y al desactivar; la app solo baja lo que cambió. `versionEsperada` evita pisar la edición de otra persona.
- **Una fila por pregunta**: reenviar reemplaza; un parcial nunca pisa a un final (llegan fuera de orden si hay mala red).
- **Privacidad**: las transcripciones entran en `purgarDatosBiometricos` junto con las de la entrevista.
- **Contrato Android**: solo rutas nuevas; no se tocó ninguna existente.
- **Pruebas**: `PruebaControladorVoz` (11).

## D42 · La nivelación solo dice que "cumple" con evidencia (2026-11)
- **Qué**: una skill alcanza el nivel mínimo (junior) solo si algún nivel con preguntas llega al umbral de 60 (aprobar uno
  más difícil también cuenta). Sin esa evidencia, `brecha` suma un nivel: un cargo junior queda con brecha 1 y la skill pasa
  a "por reforzar"; uno semisenior, con brecha 2 (prioridad alta). El resumen dice "Todavía no alcanzas el nivel Junior en
  este test" en vez de "Tu nivel actual es Junior".
- **Por qué**: `CalculadoraNivel` parte en junior porque es el mínimo que se puede guardar, así que con 0 % la brecha contra
  un cargo junior daba 0 y la app mostraba "Ya cumples". Visto en producción el 2026-11-24 con Analista QA Junior: Testing y QA
  7/100 (dos preguntas semisenior), Automatización de pruebas 12/100 y SQL 38/100 salieron como "cumple".
- **Por qué sin campo nuevo**: `nivel_actual` sigue en "junior" (la BD no tiene un nivel menor) y el JSON guardado en
  `resultado_nivelacion` no cambia de forma, así que revertir el despliegue no rompe la lectura de los resultados nuevos.
  La app reconoce el caso con lo que ya recibe: `brecha` mayor que la distancia entre `nivelRequerido` y `nivelActual`.
- **App (pendiente, opcional)**: con ese caso, `textoNivelSkill` hoy muestra "Junior → el cargo pide Junior"; conviene
  "Aún no alcanzas Junior". Sin ese cambio la skill igual aparece en "por reforzar", que es lo importante.
- **No cambia**: el nivel que se acumula en `nivel_skill_usuario` ni la regla de subir de nivel sin saltarse ninguno.
- **Pruebas**: `PruebaServicioNivelacion` (3 nuevas: calculadora, todo mal y skill solo con preguntas semisenior falladas).
## D43 · Tareas periódicas apagables para un backend local sobre la BD compartida (2026-11)
- **Pedido:** que el backend de cada integrante apunte a la BD de Railway para que todos usen el mismo banco de
  preguntas, conectándose por Tailscale (subnet router en el proyecto de Railway; la BD no se expone a internet).
- **Problema:** el backend arranca tres tareas periódicas que escriben en la BD: el rescate de reportes (cada 1 min deja
  reintentables los atascados), la sincronización semanal de mercado (llama a las APIs de empleo) y la purga diaria de
  consentimientos (D38). Un backend local conectado a producción tomaría reportes de usuarios reales y los terminaría con
  su propia configuración (sin IA), y duplicaría llamadas a las APIs de empleo.
- **Decisión:** `TAREAS_SEGUNDO_PLANO=false` no arranca esas tres tareas (`ConfiguracionGeneral.tareasSegundoPlanoActivas`;
  `Application.module` llama a `iniciarTareasSegundoPlano()` solo si están activas) y deja un aviso en el log. Por
  defecto siguen encendidas y solo el literal `false` las apaga. Dentro de Railway (`RAILWAY_PROJECT_ID` o
  `RAILWAY_ENVIRONMENT_ID`) el servidor no arranca con `false`, igual que D39: sin rescate los reportes cortados quedan
  en `generando` y sin purga se incumple el plazo legal. El reporte que pide una persona desde el backend local se sigue
  generando en ese proceso (no es una tarea periódica).
- **Riesgo aceptado:** lo que se crea desde un backend local (usuarios, entrevistas, reportes) queda en producción.
  Las migraciones y los cambios a mano contra esa BD se acuerdan con el equipo (`docs/04 Guias/Puesta en marcha.md`).
- **Contrato Android:** sin cambios.
- **Pruebas:** `PruebaConfiguracionTareasSegundoPlano` (solo `false` las apaga, falla en Railway y el arranque llama a
  las tareas solo dentro de la condición).

## D44 · Seis áreas TI nuevas en una semilla aparte (2026-11)
- **Pedido:** más preguntas y más áreas para tener variedad, guardadas como semilla. El catálogo tenía 16 cargos, casi
  todos de desarrollo (backend, frontend, móvil, datos, DevOps) más Product Manager.
- **Decisión:** `src/DB/BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql`, separada de la semilla base para no tocar sus
  14 preguntas ni las comprobaciones de D25. Áreas del catálogo (`cargo.area` y `skill.tipo_area`, texto libre):
  `qa`, `seguridad`, `diseno`, `soporte` y `cloud`; los cargos de datos/BI usan `data`, que ya existía.
  - 13 skills técnicas: Testing y QA, Automatización de pruebas, Seguridad de aplicaciones (OWASP), Seguridad de redes y
    SOC, Diseño UX, Diseño UI y Figma, Soporte técnico y Help Desk, Redes TCP/IP, Linux, Power BI y visualización de
    datos, Excel para análisis de datos, Azure, y Terraform e Infraestructura como Código.
  - 12 cargos (2 por área, de junior a senior) con 69 relaciones cargo↔skill; también usan skills de la base (SQL,
    Python, Java, AWS, Docker, Kubernetes, CI/CD, CSS, Modelado de Datos, Arquitectura Microservicios y blandas).
  - 234 preguntas aprobadas **sin cargo** (sirven a todo cargo con la skill): 18 por skill nueva, 6 por nivel (3 de opción
    múltiple con 4 opciones y 3 abiertas). Así cada skill nueva llega al mínimo de cobertura (5 por nivel) en las 3
    pruebas que eligen por skill y nivel: nivelación, práctica y entrevista (texto y video).
- **Contenido:** escritas con Claude fuera del backend (sin gasto del tope de D23 ni traza en `pregunta_generacion_ia`;
  `generada_por_ia` queda en `false`, como las demás de las semillas) con las reglas de `generar-ia`: rúbrica `TECNICA`
  con 4–5 criterios, 3–6 palabras clave, 2 respuestas alternativas, sinónimos (D33), feedback genérico correcto/medio/malo
  (D36) y, en las abiertas, resumen y pasos `1) 2) 3)` (D40). Un validador revisó forma, cantidades, opciones repetidas y
  la posición de la correcta; se pueden rechazar en el panel como cualquier otra.
- **Re-ejecutable (como D25):** `ON CONFLICT` en skills, cargos y relaciones; una pregunta solo si no existe otra con el
  mismo enunciado + skill + sin cargo + nivel, y sus opciones solo si no tiene ninguna. Todo en una transacción que
  primero exige las skills de la semilla base. Docker la carga en una BD nueva como `003-seeds-areas-ti.sql`.
- **App:** el onboarding reparte las áreas nuevas en sus cuatro áreas (TI → devops, cloud, soporte, seguridad;
  Desarrollador → + qa, diseño; Analista → data, qa, gestión), en la rama `feature/areas-ti-onboarding` de la app.
  Una app sin ese cambio no sugiere los cargos nuevos, pero sí puede escribirlos con «Otro cargo».
- **Contrato Android:** sin cambios.
- **Pruebas:** `src/DB/PROBAR_SEMILLA.ps1` la corre dos veces sobre la base: 13/12/234/468, 69 relaciones, 6 aprobadas por
  skill y nivel, opciones correctas y abiertas con pasos y rúbrica completa (20/20 en PostgreSQL 16, 2026-11-24).

## D45 · Preguntas generadas sin repetir las del banco (2026-11)
- **Pedido:** evitar que `generar-ia` guarde preguntas iguales o casi iguales a las que ya existen. Plan en
  `Plan-preguntas-sin-duplicados-2026-11-24.md`.
- **Medida:** `DetectorPreguntasSimilares` reutiliza `ComparadorTextos` (palabras compartidas, sin IA ni tokens) y toma el
  mayor de ambos sentidos. Solo el enunciado. Una paráfrasis con otro vocabulario no se detecta (haría falta embeddings).
- **Filtro al generar:** después de validar el lote (así no choca con `cantidad_incorrecta`), cada pregunta se compara con las
  aprobadas y pendientes del mismo skill (o, sin skill, cargo), tipo, categoría y nivel (las 200 más recientes) y con las
  ya aceptadas del lote. Si parecido >= `UMBRAL_SIMILITUD_PREGUNTAS` (0.80; 0 apaga) no se guarda: sale en `descartadas`
  de la respuesta. Las demás se guardan igual. Se cobran todas; si se descartan todas, la llamada se audita como fallo
  (cuenta en el tope D23 y en Costos IA) y la respuesta es `200` con `preguntas: []`.
- **Concurrencia:** comparar y guardar van en una sola transacción con `pg_advisory_xact_lock` por alcance (solo Postgres;
  H2 de pruebas no lo necesita), así dos generaciones simultáneas del mismo alcance no se duplican entre sí.
- **Prompt:** lleva hasta 30 enunciados existentes (120 caracteres cada uno, como datos) con "no repitas ni parafrasees".
- **Panel (solo avisa):** `GET /api/v1/admin/preguntas/{id}/similares?umbral=`, `GET /api/v1/admin/preguntas/duplicadas`
  (exige skill o cargo + nivel + tipo + categoría) y `similares` en las respuestas de crear, editar y aprobar. Nada bloquea
  a un admin. Rechazar una duplicada usa `rechazar` con `motivo: "duplicada"`.
- **Contrato:** campos nuevos; `RespuestaPregunta` suma `similares` (vacío salvo crear/editar/aprobar) y `generar-ia` suma
  `preguntas_descartadas` y `descartadas`. Android (`GET /api/v1/preguntas`) sin cambios. Sin migración.
- **Pendiente:** calibrar el 0.80 con pares reales del banco de Railway (la BD local tiene 14 preguntas); la pantalla del
  panel está en el plan, fuera de este repo.
- **Pruebas:** `PruebaPreguntasSimilares` (11 casos: detector, filtro, todo descartado, umbral 0, prompt) y dos casos nuevos
  en `PruebaControladorPregunta` (repetir un lote real sobre H2; `similares` y `duplicadas`).
