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

