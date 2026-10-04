---
name: manejo-errores
description: Reglas para manejar errores de forma robusta. Usar SIEMPRE que se escriba o revise código que accede a base de datos, llama APIs externas o modelos LLM, procesa datos de entrada, ejecuta procesos largos o en segundo plano, o cuando el usuario pide "manejo de errores", "reintentos", "caída de la db", "timeout", "resultado inesperado" o "proceso caído".
---

# Manejo de errores

Objetivo: que el sistema **nunca se caiga en silencio**, **nunca muestre errores internos al usuario final** y **siempre deje rastro** (registro) para diagnosticar.

## Principios generales

1. **Nunca tragar errores.** Prohibido `except: pass` o `catch {}` vacío. Si se captura, se registra y se decide: reintentar, degradar o propagar.
2. **Capturar errores específicos**, no genéricos, salvo en la capa más externa (controlador / punto de entrada).
3. **Errores propios del dominio.** Definir una jerarquía en `ERRORES/ERRORES_APLICACION`:
   - `ErrorAplicacion` (base)
   - `ErrorBaseDatos`, `ErrorServicioExterno`, `ErrorValidacion`, `ErrorResultadoInesperado`, `ErrorTiempoAgotado`, `ErrorNoEncontrado`
4. **Mensaje al usuario ≠ mensaje al registro.** Al usuario: mensaje claro en español y un `codigo_error`. Al registro: traza completa, contexto e identificador de solicitud.
5. **Respuesta de error estándar** para todas las APIs:
   ```json
   { "exito": false, "codigo_error": "DB_NO_DISPONIBLE", "mensaje": "El servicio no está disponible, intente más tarde.", "id_solicitud": "a1b2c3" }
   ```
6. **Registro estructurado** (JSON) con: fecha, nivel, módulo, `id_solicitud`, usuario (sin datos sensibles), mensaje y traza. Nunca registrar contraseñas, tokens ni datos personales completos.

## Escenarios obligatorios

### 1. La base de datos se cae / no responde
- Usar **pool de conexiones** con tiempo de espera configurado (nunca infinito).
- **Reintentos con espera exponencial + aleatoriedad** (ej: 0.5s, 1s, 2s, máximo 3 intentos) solo para errores transitorios (conexión perdida, bloqueo, tiempo agotado). No reintentar errores de sintaxis o de integridad.
- **Cortacircuitos (circuit breaker):** tras N fallos seguidos, dejar de intentar durante X segundos y responder rápido con `DB_NO_DISPONIBLE` (HTTP 503).
- **Transacciones:** toda escritura múltiple va en transacción; ante error → `rollback` y liberar la conexión (usar `with` / `try-finally`).
- Si existe caché, **degradar**: servir datos de lectura desde caché marcándolos como posiblemente desactualizados.
- Exponer un endpoint `GET /salud` que verifique la conexión a la db.

```python
# MODELOS/REPOSITORIO_USUARIO.py
def obtener_usuario(self, id_usuario: int) -> Usuario:
    try:
        with self.pool.conexion() as conexion:
            fila = conexion.ejecutar(CONSULTA_USUARIO_POR_ID, (id_usuario,))
    except (ConexionPerdida, TiempoAgotadoDb) as error:
        registro.error("db_no_disponible", extra={"id_usuario": id_usuario})
        raise ErrorBaseDatos("DB_NO_DISPONIBLE") from error
    if fila is None:
        raise ErrorNoEncontrado("USUARIO_NO_ENCONTRADO")
    return Usuario.desde_fila(fila)
```

### 2. Resultado no esperado
Aplica a respuestas de db, APIs externas y **especialmente modelos LLM**.
- **Validar siempre la forma de los datos** que entran al sistema (esquemas: Pydantic, Zod, Joi, etc.). Nunca confiar en que una API externa o un LLM devuelve lo prometido.
- Comprobar: `None`/`null`, listas vacías, tipos incorrectos, campos faltantes, valores fuera de rango, JSON mal formado.
- **LLM:** pedir salida estructurada (JSON / herramientas), validar contra esquema, y si falla: reintentar 1–2 veces indicando el error de validación; luego valor por defecto seguro o `ErrorResultadoInesperado`.
- Usar **cláusulas de guarda** (retorno temprano) en vez de `if` anidados.
- Nunca dejar que un resultado inválido llegue a la vista: se corta en el modelo/servicio.

```python
def interpretar_respuesta_llm(texto: str) -> ResumenDocumento:
    try:
        return ResumenDocumento.model_validate_json(texto)
    except ValidationError as error:
        registro.warning("respuesta_llm_invalida", extra={"detalle": str(error)[:500]})
        raise ErrorResultadoInesperado("RESPUESTA_LLM_INVALIDA") from error
```

### 3. Caída del proceso
- **Manejador global** de excepciones en el punto de entrada (middleware del framework) que registra y devuelve la respuesta estándar 500, sin exponer trazas.
- Capturar señales `SIGTERM`/`SIGINT` para **apagado ordenado**: dejar de aceptar solicitudes, terminar las que están en curso, cerrar conexiones.
- Ejecutar con **supervisor** que reinicie el proceso (Docker `restart: always`, systemd, PM2, Kubernetes) con `livenessProbe` / `readinessProbe`.
- **Tareas largas → cola de trabajos** (Celery, RQ, BullMQ, etc.) con estado persistido (`PENDIENTE`, `EN_PROCESO`, `COMPLETADO`, `FALLIDO`), reintentos y **cola de errores** (dead letter). Las tareas deben ser **idempotentes** para poder reejecutarse sin duplicar efectos.
- Guardar **puntos de control** en procesos por lotes para retomar desde donde quedó.

### 4. Servicios externos / APIs / LLM
- **Tiempo de espera obligatorio** en toda llamada HTTP (ej: conexión 5s, lectura 30s; LLM según modelo).
- Reintentos con espera exponencial ante 429, 500, 502, 503, 504 y errores de red. Respetar la cabecera `Retry-After`.
- Cortacircuitos por proveedor y **proveedor de respaldo** cuando aplique (ej: otro modelo LLM).
- No reintentar 400/401/403/422: son errores del cliente, se registran y se propagan.

### 5. Entrada inválida del usuario
- Validar en el controlador antes de llegar al modelo. Responder 400/422 con la lista de campos inválidos en español.

### 6. Otros casos a considerar siempre
- Disco lleno / archivo inexistente / permisos.
- Concurrencia: dos usuarios editan el mismo registro → bloqueo optimista (`version`) y error `CONFLICTO_EDICION` (409).
- Límite de memoria: procesar archivos grandes por partes (streaming), nunca cargar todo en memoria.
- Configuración faltante: **fallar al iniciar** (validar variables de entorno al arrancar), no en mitad de una solicitud.

## Tabla de códigos sugerida

| codigo_error | HTTP | ¿Reintentar? |
|---|---|---|
| ENTRADA_INVALIDA | 422 | No |
| NO_AUTORIZADO | 401 | No |
| NO_ENCONTRADO | 404 | No |
| CONFLICTO_EDICION | 409 | No |
| LIMITE_SOLICITUDES | 429 | Sí, con espera |
| DB_NO_DISPONIBLE | 503 | Sí |
| SERVICIO_EXTERNO_FALLO | 502 | Sí |
| TIEMPO_AGOTADO | 504 | Sí |
| RESPUESTA_LLM_INVALIDA | 502 | Sí (1–2 veces) |
| ERROR_INTERNO | 500 | No |

## Lista de revisión
- [ ] ¿Ningún `catch` vacío?
- [ ] ¿Toda llamada externa tiene tiempo de espera?
- [ ] ¿Reintentos solo en errores transitorios y con límite?
- [ ] ¿Transacciones con `rollback`?
- [ ] ¿Datos externos/LLM validados contra esquema?
- [ ] ¿Manejador global y apagado ordenado?
- [ ] ¿Mensajes al usuario en español y sin detalles internos?
- [ ] ¿Registro con `id_solicitud` y sin datos sensibles?
