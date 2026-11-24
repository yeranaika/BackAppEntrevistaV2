> **Modo Review / Solo Lectura:** No se modificaron ni crearon archivos (tampoco se escribi├│ `A:\programacion\proyectos\proyecto-de-titulo\BackAppEntrevistaV2\docs\05 Estado\Review-cajas-multiples-2026-11-23.md`). El contenido completo queda listo abajo para que Hermes lo persista si corresponde.

---

# Review t├®cnica ÔÇö Multi-cajas en vivo Android (`EntrevistaAPPAndroid`)

## 1. Conclusi├│n t├®cnica

La tuber├¡a de datos **`RespuestaPrediccionVisionDto` ÔåÆ `mapearRespuestaAIndicadores` ÔåÆ `CONTROLADOR_LLAMADA_EN_VIVO.kt:301` (`eventosCaja: Set<String>`) ÔåÆ `PANTALLA_LLAMADA_EN_VIVO.kt:121` (`eventosActivosCon`) ÔåÆ `OVERLAY_VISION.kt:123` (`eventos.forEachIndexed`)** **NO tiene ning├║n `.take(1)` / `.first()` / `.maxByOrNull()` que colapse `eventos` a 1**.

El s├¡ntoma de **"solo aparece UNA caja en vivo aunque se detecten varios eventos"** se explica en Android por **tres puntos concretos de colapso/ocultamiento** (m├ís la frontera con el backend):

1. **Colapso en el marco corporal (`GEOMETRIA_OVERLAY_VISION.kt:124` + `OVERLAY_VISION.kt:64-86`)**: `OverlayVisionCanvas` dibuja **una sola bounding box** (la caja corporal completa de 1 persona) con **una sola etiqueta** calculada por `etiquetaOverlayDesde(...)` mediante `.maxByOrNull { it.valor ?: 0.0 }`. Los m├║ltiples eventos activos (`eventos`) no se dibujan como cajas espaciales por regi├│n, sino como una columna apilada de etiquetas al costado. Adem├ís, `etiquetaOverlayDesde` ignora `eventosCaja` y `polaridadDe` (`gaze_direction <= 2.0`, `torso_lean |v-4| >= 1.0`), por lo que variables est├íticas de score alto (`framing_quality`, `camera_orientation`, `posture_stability`) secuestran permanentemente esa ├║nica etiqueta.
2. **Colapso total a 1 caja en `releaseLocal`/`release` + z-order en `debug` (`app/build.gradle.kts:113,131,147-152` + `BANDERA_OBSERVACION.kt:10` + `OVERLAY_OBSERVACION.kt:28` + `PANTALLA_LLAMADA_EN_VIVO.kt:116-138`)**:
   - `BuildConfig.MOSTRAR_OVERLAY_VISION` es `false` por defecto en `defaultConfig`, `release` y **`releaseLocal`** (solo `true` en `debug`, salvo que se compile con `-PmostrarOverlayVision=true`).
   - En cambio `OBSERVAR_RECONOCIMIENTO = true` est├í fijo en **todas** las variantes y renderiza `OverlayObservacionReconocimiento`, que colapsa a **1 sola p├¡ldora** usando `.maxByOrNull { it.valor ?: 0.0 }` (`OVERLAY_OBSERVACION.kt:28`).
   - Resultado: en `releaseLocal` sin flag, `OverlayVisionCanvas` **no se compone** y solo aparece **1 caja** (`TopStart`). Incluso en `debug`, `OverlayObservacionReconocimiento` est├í declarado *despu├®s* del `Canvas` (`PANTALLA_LLAMADA_EN_VIVO.kt:125-138`), por lo que tiene **z-order superior** y tapa las primeras filas del stack cuando la columna cae a la izquierda.
3. **Colapso/descarte silencioso dentro de `OVERLAY_VISION.kt:64` y `108-125`**:
   - Si `calcularCajaCorporal(landmarks) == null` (`< 3` landmarks visibles ÔëÑ `0.5`), `OVERLAY_VISION.kt:64` hace `return@Canvas` **antes** de dibujar `eventos` (`l├¡nea 88`): el stack desaparece completo (0 filas) y queda visible ├║nicamente la p├¡ldora ├║nica de `OverlayObservacionReconocimiento`.
   - Si `etiqueta` solapa en X con la columna (`solapaX`), `OVERLAY_VISION.kt:116` empuja `yInicio` debajo de `yEt + altoEt + hueco` (cuando el sujeto est├í encuadrado abajo en el frame, desperdicia toda la parte superior) y `l├¡nea 125` (`if (y + altoUsado > size.height) return@forEachIndexed`) descarta silenciosamente las filas que no caben, pudiendo dejar 0ÔÇô1 fila visible.

*(Frontera externa: Android recibe `predicciones: Map<String, Double>` sin truncar en `REMOTO_VISION_PREDICCION.kt:105`. Si el backend solo devolviera 1 clave o solo 1 target cruzara umbral/debounce, habr├¡a que inspeccionar `POST /api/v1/vision/predecir` en `BackAppEntrevistaV2`, fuera del workspace inspeccionado.)*

---

## 2. Archivos relevantes

| Archivo | L├¡neas clave | Rol en la cadena |
|---|---|---|
| [REMOTO_VISION_PREDICCION.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/data/remote/videoEntrevista/REMOTO_VISION_PREDICCION.kt#L103-L108) | 103ÔÇô108 | `RespuestaPrediccionVisionDto.predicciones: Map<String, Double>` (multi-target, sin recorte). |
| [CONTROLADOR_LLAMADA_EN_VIVO.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/ui/llamadaEnVivo/CONTROLADOR_LLAMADA_EN_VIVO.kt#L123-L135) | 123ÔÇô135, 296ÔÇô321, 333ÔÇô348 | `mapearRespuestaAIndicadores`, `alRecibirRespuesta` (`eventosCaja: Set<String>`), `ExtractorObservado` (`debounceManosJuntas`). |
| [PALETA_EVENTOS.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/core/vision/PALETA_EVENTOS.kt#L33-L271) | 33ÔÇô40, 80ÔÇô103, 169ÔÇô203, 257ÔÇô271 | `UMBRALES_CAJA_VIVA`, `DEBOUNCE_EVENTO`, `DebounceEventos`, `eventosActivosCon` (multi-evento). |
| [PANTALLA_LLAMADA_EN_VIVO.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/ui/llamadaEnVivo/PANTALLA_LLAMADA_EN_VIVO.kt#L91-L139) | 91ÔÇô139 | Composici├│n del `Box` de c├ímara, flags `MOSTRAR_OVERLAY_VISION` vs `OBSERVAR_RECONOCIMIENTO`, z-order. |
| [OVERLAY_VISION.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/ui/llamadaEnVivo/OVERLAY_VISION.kt#L38-L135) | 38ÔÇô135 | Dibujo de esqueleto, 1 caja corporal + 1 `etiqueta`, y columna apilada de `eventos`. |
| [GEOMETRIA_OVERLAY_VISION.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/core/vision/GEOMETRIA_OVERLAY_VISION.kt#L89-L129) | 89ÔÇô129 | `calcularCajaCorporal` (1 caja) y `etiquetaOverlayDesde` (colapso por `maxByOrNull`). |
| [OVERLAY_OBSERVACION.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/ui/llamadaEnVivo/OVERLAY_OBSERVACION.kt#L20-L58) | 20ÔÇô58 | P├¡ldora `TopStart` que colapsa a 1 indicador por `maxByOrNull`. |
| [BANDERA_OBSERVACION.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/core/vision/BANDERA_OBSERVACION.kt#L10) | 10 | `OBSERVAR_RECONOCIMIENTO = true` fijo en todos los build types. |
| [app/build.gradle.kts](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/build.gradle.kts#L113-L153) | 113, 131, 147ÔÇô152 | `MOSTRAR_OVERLAY_VISION` = `true` solo en `debug`; `false` en `release` y `releaseLocal`. |
| [EXTRACTOR_LANDMARKS_MEDIAPIPE.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/core/vision/EXTRACTOR_LANDMARKS_MEDIAPIPE.kt#L90-L99) | 90, 99 | `setNumPoses(1)` / `setNumFaces(1)` (1 persona por dise├▒o; no colapsa eventos). |
| [REPORTE_LLAMADA.kt](file:///A:/programacion/proyectos/proyecto-de-titulo/EntrevistaAPPAndroid/app/src/main/java/com/example/entrevista_app_android/core/llamada/REPORTE_LLAMADA.kt#L160-L171) | 160ÔÇô171 | Contexto post-llamada: `accionDe` usa `maxByOrNull` para `linea`/`acciones`, mientras `segmentos` es multi-evento (no afecta al vivo). |

---

## 3. Arquitectura y causa ra├¡z detallada (archivo:l├¡nea)

### 3.1 Rastreo paso a paso de la cadena

1. **`REMOTO_VISION_PREDICCION.kt:103-108` (`RespuestaPrediccionVisionDto`)** ÔÇö OK (multi).
   `predicciones: Map<String, Double>` deserializa todas las claves del backend sin l├¡mite ni prioridad.
2. **`CONTROLADOR_LLAMADA_EN_VIVO.kt:123-135` (`mapearRespuestaAIndicadores`)** ÔÇö OK (multi).
   Mapea todas las entradas, acota finitos a `0.0..8.0` y ordena por `nombre`. Sin `take`/`first`/`maxBy`.
3. **`CONTROLADOR_LLAMADA_EN_VIVO.kt:301-306` (`eventosCaja`) + `PALETA_EVENTOS.kt:169-194` (`DebounceEventos`)** ÔÇö Multi-evento, pero con 3 defectos de borde:
   - Devuelve `Set<String>` con todos los `id` cuyo `debounceCaja.actualizar(id, superaUmbral(id, valor, umbralCajaVivaDe(id)))` retorna `true`.
   - **Defecto A (`CONTROLADOR_LLAMADA_EN_VIVO.kt:302-305`)**: `eventosCaja` se reconstruye ├║nicamente desde `filas.mapNotNull { f -> f.valor?.let { f.clave to it } }`. Si una respuesta trae `Double.NaN` o omite un target ya activo, ese target desaparece de `eventosCaja` al instante (rompiendo el debounce de cierre `M` s), mientras sigue en `debounceCaja.activos`.
   - **Defecto B (`CONTROLADOR_LLAMADA_EN_VIVO.kt:339-343` vs `PALETA_EVENTOS.kt:264-267`)**: `debounceManosJuntas` (`DebounceBinario(3, 3)`) solo alimenta `segmentos` (reporte), NO el overlay vivo. `eventosActivosCon` llama a `manosJuntas(landmarks, mesa)` en crudo en cada frame (parpadeo y divergencia vivo vs reporte).
   - **Defecto C (`PALETA_EVENTOS.kt:95, 182`)**: si la latencia de red entre ventanas supera `PAUSA_MAXIMA_RACHA_MS` (`5_000L`), `inicioRacha.remove(idTarget)` reinicia la racha en cada llegada: los **6 eventos calibrados** con `abrir >= 2` (`hand_to_face_adaptors` 2/4, `table_rest` 2/1, `torso_lean` 2/1, `gaze_direction` 3/4, `gaze_alignment` 4/2, `arm_crossing` 4/1) **nunca abren**, mientras los otros 14 (`1/1`) s├¡ abren de inmediato.
4. **`PANTALLA_LLAMADA_EN_VIVO.kt:116-138` ÔåÆ `OVERLAY_VISION.kt:38-135` + `GEOMETRIA_OVERLAY_VISION.kt:120-129` + `OVERLAY_OBSERVACION.kt:28`** ÔÇö **AQU├ì EST├ü EL COLAPSO Y EL OCULTAMIENTO**:
   - **`GEOMETRIA_OVERLAY_VISION.kt:124`**: `etiquetaOverlayDesde` colapsa todos los indicadores a **1** con `.maxByOrNull { it.valor ?: 0.0 }`, ignorando `eventosCaja` y `polaridadDe`.
   - **`OVERLAY_OBSERVACION.kt:28`**: `textoObservacionReconocimiento` hace exactamente el mismo colapso a **1** con `.maxByOrNull { it.valor ?: 0.0 }`.
   - **`app/build.gradle.kts:113, 131, 147-152` + `BANDERA_OBSERVACION.kt:10`**: en `releaseLocal` y `release` sin `-PmostrarOverlayVision=true`, `BuildConfig.MOSTRAR_OVERLAY_VISION` es `false` pero `OBSERVAR_RECONOCIMIENTO` es `true`, dejando **literalmente 1 sola caja** en vivo (`OverlayObservacionReconocimiento`).
   - **`OVERLAY_VISION.kt:64`**: `calcularCajaCorporal(landmarks) ?: return@Canvas` corta antes de dibujar la columna `eventos` (`l├¡nea 88`). Sin 3 landmarks visibles ÔëÑ `0.5`, se dibujan **0** cajas de evento.
   - **`OVERLAY_VISION.kt:108-125`**: si `solapaX` es `true` con `arr` bajo en pantalla, `yInicio` salta por debajo de `yEt + altoEt + hueco` y la l├¡nea 125 (`if (y + altoUsado > size.height) return@forEachIndexed`) recorta silenciosamente las filas que exceden `size.height`.

### 3.2 Otros l├¡mites detectados (z-order, solape, rendimiento)

| Categor├¡a | Ubicaci├│n | Problema |
|---|---|---|
| **Z-order** | `PANTALLA_LLAMADA_EN_VIVO.kt:108-138` | `OverlayObservacionReconocimiento` (`TopStart`, l├¡nea 133) se dibuja **encima** de `OverlayVisionCanvas` (l├¡nea 118) y tapa las primeras filas del stack cuando `xCol` cae a la izquierda (`espacioIzq > espacioDer`). Al rev├®s, `OverlayVisionCanvas` se dibuja **encima** de `"ÔùÅ GRABANDO PRUEBA"` (`TopEnd`, l├¡nea 108) cuando `xCol` cae a la derecha. |
| **Solape y jitter horizontal** | `OVERLAY_VISION.kt:102-106` | `if (espacioDer >= espacioIzq)` no tiene hist├®resis: con la persona centrada, toda la columna salta izquierda Ôåö derecha a 10 FPS. |
| **Ancho desfasado / clipping con `fontScale`** | `OVERLAY_VISION.kt:98, 122` | `ancho` se mide con `11.sp` **antes** de ajustar `pincel.textSize = minOf(11.sp.toPx(), altoUsado * 0.62f)` y nunca se acota a `size.width - 2 * margen`. Con fuente del sistema ampliada (`fontScale >= 1.3`), las cajas quedan sobredimensionadas y se recortan al borde derecho. |
| **Sin indicador de overflow** | `OVERLAY_VISION.kt:125` | Si las filas no caben verticalmente, se descartan en silencio (sin chip `+N m├ís`). |
| **Rendimiento (10 FPS + 4 Hz)** | `PANTALLA_LLAMADA_EN_VIVO.kt:117-132`, `OVERLAY_VISION.kt:47, 74, 89, 97, 110` | `landmarksOverlay` emite a 10 FPS y `LaunchedEffect` recompone cada 250 ms. En cada draw del `Canvas` se asignan **3 instancias nativas de `android.graphics.Paint`** (l├¡neas 74, 89 y 110; la 110 adem├ís re-mide `etiqueta.texto` ya medida en l├¡nea 80), un `LinkedHashMap` (`associateBy`), y `String.format` por cada evento activo. Adem├ís `etiquetaOverlayDesde` y `eventosActivosCon` no usan `remember`. |

---

## 4. Cambios recomendados (patch m├¡nimo recomendado)

> **Prioridad 1 ÔÇö Eliminar colapso/ocultamiento de multi-cajas:**

1. **Alinear flags de build (`app/build.gradle.kts:147-152` y `BANDERA_OBSERVACION.kt:10`)**:
   - En `releaseLocal` (que se usa para probar APK optimizado contra backend local), habilitar `MOSTRAR_OVERLAY_VISION` igual que `debug` (`overlayVisionForzado ?: "true"`), o bien derivar `OBSERVAR_RECONOCIMIENTO` de `BuildConfig.DEBUG` / apagarlo cuando `BuildConfig.MOSTRAR_OVERLAY_VISION` ya est├í activo (as├¡ no compite ni tapa el stack).
   ```kotlin
   // app/build.gradle.kts (en create("releaseLocal"))
   buildConfigField("boolean", "MOSTRAR_OVERLAY_VISION", overlayVisionForzado ?: "true")
   ```
   ```kotlin
   // PANTALLA_LLAMADA_EN_VIVO.kt:125 (evitar solape con OverlayVisionCanvas)
   if (OBSERVAR_RECONOCIMIENTO && !BuildConfig.MOSTRAR_OVERLAY_VISION) { ... }
   ```
2. **No abortar el dibujo de `eventos` si falta la caja corporal (`OVERLAY_VISION.kt:64-88`)**:
   - Cambiar `val caja = calcularCajaCorporal(landmarks) ?: return@Canvas` por `val caja = calcularCajaCorporal(landmarks)` y envolver el dibujo del marco/etiqueta en `if (caja != null) { ... }`. Si `caja == null`, anclar la columna de `eventos` en la esquina superior derecha (`xCol = (size.width - ancho - margen).coerceAtLeast(0f)`, `yInicio = margen`) para que nunca desaparezcan los eventos activos por p├®rdida moment├ínea de pose.
3. **Respetar `eventosCaja` y polaridad en `etiquetaOverlayDesde` (`GEOMETRIA_OVERLAY_VISION.kt:120-129`, o retirar la p├¡ldora duplicada cuando hay `eventos`)**:
   - Si se mantiene la etiqueta sobre el marco corporal, que priorice el evento activo de `eventos` con mayor `intensidadEvento(id, valor)` (o que muestre `"${eventos.size} eventos"` / el evento principal activo) en vez de `.maxByOrNull { it.valor ?: 0.0 }` sobre m├®tricas de encuadre.
4. **Arreglar layout, z-order y rendimiento en `OVERLAY_VISION.kt:73-133`**:
   - Reutilizar `Paint` con `remember` (1 `pincelEtiqueta`, 1 `pincelEvento`) y reutilizar `anchoEt`/`xEt`/`yEt` ya calculados en l├¡nea 80ÔÇô83 (eliminar el tercer `Paint` de l├¡nea 110).
   - Calcular primero `altoUsado` y `pincelEvento.textSize` **antes** de medir `ancho`, y acotar `ancho = minOf(..., size.width - 2 * margen)`.
   - Evitar que `solapaX` desperdicie todo el tercio superior: si `yEt > altoFila + 2 * margen`, colocar las filas arriba o en el lado opuesto a `xEt`; y si hay overflow en l├¡nea 125, pintar la ├║ltima fila visible como `"+N m├ís"`.
   - Agregar hist├®resis (p. ej. `┬▒24.dp`) o fijar un lado preferido (`TopEnd`) para la columna de eventos y evitar saltos izquierda Ôåö derecha a 10 FPS.
   - Memoizar `etiqueta` y el c├ílculo de eventos en `PANTALLA_LLAMADA_EN_VIVO.kt:120-121` con `remember(estado.indicadores, estado.eventosCaja, landmarks)`.

> **Prioridad 2 ÔÇö Consistencia de estado en `CONTROLADOR_LLAMADA_EN_VIVO.kt` y `PALETA_EVENTOS.kt`:**

5. **Conservar eventos activos si falta una clave o llega `NaN` (`CONTROLADOR_LLAMADA_EN_VIVO.kt:301-306`)**:
   - Actualizar `debounceCaja` con los valores finitos de `filas`, pero leer `eventosCaja` desde `debounceCaja.activosSnapshot()` (en vez de filtrar `filas`), o mantener el estado anterior de cualquier `id` ausente/no finito.
6. **Unificar `debounceManosJuntas` entre overlay vivo y reporte (`CONTROLADOR_LLAMADA_EN_VIVO.kt:339-343` + `PALETA_EVENTOS.kt:257-271`)**:
   - Exponer `manosJuntasActivo` ya debounced (o incluir `ID_MANOS_JUNTAS` en `eventosCaja` / un `StateFlow<Boolean>`) y pasarlo a `eventosActivosCon` en lugar de evaluar `manosJuntas(landmarks, mesa)` sin debounce en cada frame.

---

## 5. Riesgos y edge cases

- **Backend / motor enviando < 2 eventos activos reales**: Incluso tras corregir todo en Android, si en vivo el modelo `divlara-e14-mlp` solo supera umbral + debounce en 1 variable a la vez (o si la latencia RTT > `PAUSA_MAXIMA_RACHA_MS = 5 s` impide abrir los 6 eventos con `abrir >= 2`), en pantalla se ver├í 1 fila. Conviene verificar con log de conteo (`eventosCaja.size` + RTT ms, sin coordenadas para respetar T3.3) en pruebas reales.
- **Saturaci├│n visual con los 14 eventos no calibrados**: Hoy `UMBRALES_CAJA_VIVA` calibra 6 eventos y los otros 14 usan `>= 3.5` con debounce `1/1`. Al habilitar/destapar el stack completo, variables de calidad/postura (`framing_quality`, `camera_orientation`, `posture_stability`, `shoulder_symmetry`, `upper_body_openness`) pueden encender 5ÔÇô8 filas simult├íneas de forma casi permanente, encogiendo `altoUsado` hacia `12.dp` y ocultando gestos puntuales. Conviene validar si la columna viva debe mostrar los 21 o priorizar/filtrar conductas puntuales.
- **Accesibilidad (`fontScale > 1.3`) y pantallas angostas/landscape**: Si `ancho` no se acota ni se mide con el `textSize` final, las etiquetas se truncan al borde del `Box`.
- **`release` publicable**: `OBSERVAR_RECONOCIMIENTO = true` en `BANDERA_OBSERVACION.kt:10` actualmente se compila encendido incluso en `release` de producci├│n (solo `MOSTRAR_OVERLAY_VISION` se apaga). Debe confirmarse que `release` no exponga p├¡ldoras de depuraci├│n.
- **Compatibilidad de tests existentes**: `ObservacionReconocimientoTest.banderaEstaEncendidaPorDefecto` (`ObservacionReconocimientoTest.kt:19-21`) aserta `assertTrue(OBSERVAR_RECONOCIMIENTO)`; si se apaga esa bandera en `BANDERA_OBSERVACION.kt`, ese test debe actualizarse conscientemente (en cambio, ocultarlo en `PANTALLA_LLAMADA_EN_VIVO.kt` cuando `MOSTRAR_OVERLAY_VISION == true` preserva la constante y el test).

---

## 6. Pruebas recomendadas (tests que faltan)

Ning├║n test actual (`OverlayVisionTest.kt`, `UmbralesCajaVivaTest.kt`, `DebounceTiempoV19Test.kt`, `LlamadaEnVivoTest.kt`, `EventosSegmentosTest.kt`) valida multi-evento de extremo a extremo ni geometr├¡a de la columna de eventos:

1. **Multi-evento end-to-end en `ControladorLlamadaEnVivo` (`LlamadaEnVivoTest` o nuevo `MultiCajasVivoTest`)**:
   - Enviar ventanas consecutivas con ÔëÑ 3 eventos simult├íneos de distinta polaridad y debounce (p. ej. `hand_to_face_adaptors = 1.0`, `gaze_direction = 1.5`, `torso_lean = 5.5`, `arm_crossing = 4.5`) y verificar que `estado.eventosCaja` contiene todos ellos al cumplirse sus tiempos `N` s, y que `eventosActivosCon(estado.indicadores, landmarks) { id, _ -> id in estado.eventosCaja }` devuelve los N `EventoActivo` en el orden estable de `EVENTOS`.
2. **Coexistencia de eventos backend + `manos_juntas` (con debounce)**:
   - Verificar que `manos_juntas` aparece junto a otros eventos activos cuando `table_rest` est├í inactivo, desaparece cuando `table_rest` se activa, y no parpadea con 1 solo frame aislado.
3. **Resiliencia ante `Double.NaN` o clave faltante**:
   - Con 2 eventos activos en `eventosCaja`, recibir una ventana donde uno viene `Double.NaN` o ausente y confirmar que no se desincroniza `estado.eventosCaja` respecto a `DebounceEventos`.
4. **Geometr├¡a pura de la columna apilada (`OverlayVisionTest`)**:
   - Extraer el c├ílculo de rect├íngulos de la columna a funci├│n pura en `GEOMETRIA_OVERLAY_VISION.kt` y probar:
     - `1`, `3`, `10` y `21` eventos activos ÔåÆ todos los rect├íngulos tienen `0 <= x`, `x + ancho <= width`, `0 <= y`, `y + alto <= height` y no se solapan entre s├¡ ni con la p├¡ldora `etiqueta`.
     - `calcularCajaCorporal == null` ÔåÆ los eventos siguen recibiendo posiciones v├ílidas (no desaparecen).
     - Cuerpo encuadrado abajo (`arr = 0.85 * height`) ÔåÆ no colapsa la lista a 0ÔÇô1 filas.
     - `fontScale = 1.5f` en ancho angosto (`320dp`) ÔåÆ `ancho` acotado dentro de la vista.
5. **Configuraci├│n de build (`ConfiguracionReleaseTest`)**:
   - Verificar expl├¡citamente el contrato de `MOSTRAR_OVERLAY_VISION` y `OBSERVAR_RECONOCIMIENTO` en `debug`, `releaseLocal` y `release` para evitar regresiones silenciosas donde `releaseLocal` vuelva a mostrar solo 1 p├¡ldora.
