---
tipo: guia
tags: [guia, endpoint, checklist]
actualizado: 2026-10-03
---

# Cómo agregar un endpoint (checklist)

Ejemplo: `GET /api/v1/me/estadisticas`.

1. **Esquema** — DTO de entrada/salida en `ESQUEMAS/ESQUEMA_<ENTIDAD>.kt` (`@Serializable`).
   Campos que el cliente necesita siempre: **sin valor por defecto** (por `encodeDefaults = false`).
2. **Modelo / repositorio** (si hay datos nuevos) — tabla en `MODELOS/TABLA_*.kt`, modelo en `MODELO_*.kt`, interfaz +
   `*Exposed` en `REPOSITORIO_*.kt`. Si cambia el esquema: **migración nueva** `migrations/0NN_*.sql` (idempotente) +
   actualizar `src/DB/BasedeDatos.EntrevistaApp.sql` + verificar sobre BD vacía. → [[Modelo de datos]]
3. **Servicio** — reglas en `SERVICIOS/SERVICIO_<ENTIDAD>.kt`; errores de dominio con código estable; dependencias por interfaz.
4. **Vista** — `VISTAS/VISTA_<ENTIDAD>.kt`: `fun Modelo.aRespuesta() = RespuestaX(…)`.
5. **Controlador** — ruta en `CONTROLADORES/CONTROLADOR_<ENTIDAD>.kt` con el comentario de rutas arriba; `authenticate("auth-jwt")`
   y `soloAdmin { }` según corresponda. Si escribe datos, responder con `mensaje` (`responderConMensaje` o
   `RespuestaOk("…")`) → [[Convenciones de codigo#Respuestas de éxito]].
6. **Cablear** — instanciar en `CONFIGURACION/CONTENEDOR_DEPENDENCIAS.kt`, montar en `CONFIGURACION/CONFIGURACION_RUTAS.kt`
   y en `SistemaPrueba.montar` (`src/test/kotlin/PRUEBAS/DOBLES/BD_PRUEBA.kt`, incluida la tabla nueva).
7. **Pruebas** — `PRUEBA_SERVICIO_*` (reglas, errores, concurrencia si aplica) y `PRUEBA_CONTROLADOR_*` (status, JSON, 401/403/404).
   `./gradlew test` completo en verde.
8. **E2E** — agregar verificaciones al script de la fase o uno nuevo en `PRUEBAS_E2E/` (UTF-8 con BOM; limpia sus datos).
9. **Documentación** — agregar la request en `CARPETAS` de `docs/documentacion/GENERAR_DOCUMENTACION.py` y ejecutar
   `python docs/documentacion/GENERAR_DOCUMENTACION.py` (actualiza `API.md` y Postman). → [[Postman y documentacion de la API]]
10. **Bóveda** — si cambia un flujo, una decisión o el estado: actualizar [[Flujos de negocio]], [[Decisiones]], [[Estado del proyecto]].

> [!warning] Contrato de la app Android
> Si el endpoint ya lo usa la app, no cambiar rutas ni nombres de campos. Agregar una prueba que decodifique la respuesta
> con una copia del DTO de la app (ver `PRUEBA_CONTROLADOR_PRUEBA.kt`).

Relacionado: [[Convenciones de codigo]] · [[Pruebas]]
