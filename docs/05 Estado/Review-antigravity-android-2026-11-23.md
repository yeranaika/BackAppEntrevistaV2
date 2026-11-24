# Review Antigravity — Android (T5.2,23-nov-202601:44-02:08)

Review1 de3 (Android). Resultado:16 hallazgos P1/P2/P3 + verificación de contrato JSON + riesgos.

## P1 (críticos)
1. `calcularCajaCorporal` (GEOMETRIA_OVERLAY_VISION.kt:89-109) no filtra `id in0..32` → con2 iris (468,473)+1 pose dibuja caja espuria.
2. Consentimiento guardado sin verificar `CAMERA` al entrar a llamada en vivo → falla silenciosa.
3. `TrabajoSincronizarPracticas`: sube llamadas/revisiones aunque `GET /me`=503 con consentimiento pendiente.
4. **Contrato JSON**: backend NO persiste ni retorna `promedios`/`segmentos` de `ResumenLlamadaDto` → al cerrar sesión se pierden las métricas detalladas del historial (ESQUEMA_LLAMADA.kt vs REMOTO_LLAMADAS.kt).
5. `GRABAR_PRUEBA`/`OBSERVAR_RECONOCIMIENTO` sin blindar en `buildTypes.release` → riesgo Ley21.719/Play Store.

## P2
- `manos_juntas` en vivo SIN debounce (parpadea) vs reporte CON debounce3 frames; supresión `table_rest`3.0 vs4.0; score sintético8.0 vs "—" en reporte.
- `etiquetaOverlayDesde` (GEOMETRIA:124) usa maxByOrNull de los20 indicadores → casi siempre muestra "Encuadre"/"Orientación" en vez del gesto.
- Dos diccionarios de nombres duplicados: PALETA_EVENTOS ("Cruce de brazos","Manos en la mesa","Toques en la cara") vs CONTROLADOR_LLAMADA_EN_VIVO ("Brazos cruzados","Apoyo en mesa","Manos a la cara").
- Paint() instanciado por frame en OverlayVisionCanvas; `ancho` calculado antes de reducir textSize.
- Variables sin `@Volatile` entre hilos (ultimaPoseMs, analizador, inicioMs).
- `LLAMADA_EN_VIVO` ausente en `PANTALLAS_DE_PRUEBA` → consentimiento nuevo corta la llamada a mitad.
- DDL duplicado manual en SERVICIO_SIN_CONEXION.kt:88-136 vs3.sqm/4.sqm.
- ConsentScreen: casilla fuera del scroll → en pantallas pequeñas comprime el texto legal.

## P3
- `versionModelo` enviado a /vision/predecir no existe en esquema backend (ignorado).
- Código muerto: ANONIMIZADOR_VIDEO.kt (907 líneas sin invocaciones); task-t31*.md y .idea en commit.

## Contrato JSON (tabla del review)
| Endpoint | Estado |
|---|---|
| POST /vision/predecir | Compatible (menor: versionModelo ignorado) |
| POST/GET /api/v1/llamadas | **Desalineado: pierde promedios+segmentos** |
| POST /pruebas/video | Coincide multipart; **sin auth** |
| POST /entrevistas/{id}/metricas | Compatible |
| /revision-local/* | Compatible campo por campo |

## Riesgos citados
- Ley21.719 / rechazo Play Store por GRABAR_PRUEBA en release.
- Pérdida de métricas de historial (promedios/segmentos).
- Overlay "esperando ventana (N/20)" visible en usuarios de release.

## Recomendaciones clave (resumen)
Blindar flags en release; borrar .mp4 tras subida + auth en /pruebas/video; gate de consentimiento en sync; agregar promedios/segmentos al backend; unificar tabla de nombres de eventos; debounce manos_juntas uniforme a10FPS; filtrar caja corporal a0..32; no abrir reporte en onError de cámara.
