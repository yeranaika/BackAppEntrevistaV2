---
name: arquitectura-software
description: Lineamientos de arquitectura de software para sistemas que soportan muchas consultas y usuarios en paralelo, múltiples APIs e integración de modelos LLM y BLM. Usar SIEMPRE que se diseñe un sistema nuevo, se agregue un módulo, API o integración con modelos de IA, se hable de escalabilidad, concurrencia, rendimiento, colas, caché o despliegue.
---

# Arquitectura de software

Objetivo: sistemas **escalables, concurrentes y multi-uso**, donde muchos usuarios trabajan en paralelo y se integran varias APIs y modelos de IA sin que una falla tumbe todo.

Base: la estructura MVC y el estándar de nombres de la skill `programacion-mvc`, y el manejo de errores de la skill `manejo-errores`.

## 1. Vista general

```
             ┌──────────────────────────────┐
 Usuarios ──▶│ Balanceador / Puerta de APIs │  (límite de solicitudes, autenticación, CORS)
             └──────────────┬───────────────┘
                            ▼
        ┌──────── Instancias de la API (sin estado, N réplicas) ────────┐
        │  CONTROLADORES → SERVICIOS → MODELOS / ADAPTADORES             │
        └───┬───────────┬──────────────┬──────────────┬─────────────────┘
            ▼           ▼              ▼              ▼
        Base de      Caché         Cola de        PASARELA_MODELOS
        datos       (Redis)        trabajos  ──▶  (LLM / BLM)
     (pool + réplicas              (workers)       + APIs externas
       de lectura)
```

## 2. Preparado para muchas consultas

- **API sin estado (stateless):** nada de sesión en memoria del proceso. Sesión/tokens en JWT o Redis. Así se pueden levantar N réplicas detrás del balanceador.
- **Pool de conexiones** a la db con tamaño configurado; nunca abrir una conexión por consulta.
- **Índices** en columnas de filtro/orden; revisar consultas lentas (`EXPLAIN`). Evitar el problema N+1.
- **Paginación obligatoria** en todo listado (`limite`, `cursor`/`pagina`), con máximo permitido.
- **Caché** (Redis) para lecturas frecuentes con tiempo de expiración e invalidación al escribir.
- **Réplicas de lectura** para separar lecturas pesadas (reportes) de escrituras.
- **Límite de solicitudes** por usuario/IP (`LIMITE_SOLICITUDES`, HTTP 429).
- **Asíncrono** para E/S (async/await, FastAPI, Node) — no bloquear el hilo esperando la db o un LLM.

## 3. Varias APIs

- **Patrón adaptador:** cada API externa vive en `ADAPTADORES/ADAPTADOR_<PROVEEDOR>` e implementa una interfaz común. El servicio no sabe qué proveedor usa.
- **Versionado** de nuestras APIs: `/api/v1/...`. No romper contratos; crear `v2`.
- **Contrato documentado** con OpenAPI/Swagger.
- Respuesta estándar en todas las APIs:
  ```json
  { "exito": true, "datos": { }, "meta": { "pagina": 1, "total": 120 }, "id_solicitud": "a1b2c3" }
  ```
- Toda llamada externa con tiempo de espera, reintentos y cortacircuitos (ver `manejo-errores`).
- Configuración de cada proveedor (URL, llaves, límites) en variables de entorno, centralizada en `CONFIGURACION/`.

## 4. Modelos LLM y BLM

- **PASARELA_MODELOS** (`SERVICIOS/PASARELA_MODELOS`): único punto por donde pasa toda llamada a modelos. Responsabilidades:
  - Elegir modelo según tarea, costo y latencia (enrutamiento).
  - Proveedor/modelo de **respaldo** si el principal falla.
  - Control de **límites de tokens y de solicitudes** por proveedor (cola/semáforo).
  - **Registro** de uso: modelo, tokens, costo, latencia, `id_solicitud`, usuario.
  - **Caché** de respuestas para entradas idénticas cuando aplique (y caché de prompts del proveedor).
- **Interfaz común** `InterfazModelo` con métodos como `generar`, `generar_estructurado`, `generar_flujo` (streaming), `embeber`; cada proveedor/modelo es un adaptador (`ADAPTADOR_LLM_<PROVEEDOR>`, `ADAPTADOR_BLM_<NOMBRE>`).
- **Prompts versionados** como archivos/plantillas en `PROMPTS/`, no strings sueltos en el código.
- **Salida estructurada** (JSON/herramientas) validada con esquema.
- **Respuestas largas → streaming** al usuario (SSE/WebSocket) para no bloquear.
- **Trabajos pesados (documentos grandes, lotes) → cola de trabajos**, el usuario consulta el estado o recibe notificación.
- Nunca enviar secretos ni datos sensibles innecesarios al modelo; anonimizar cuando sea posible.

## 5. Multi-uso y usuarios en paralelo

- **Multi-inquilino (multi-tenant)** si varios clientes/empresas usan el sistema: `id_organizacion` en cada tabla y filtro obligatorio en el repositorio.
- **Aislamiento entre usuarios:** el contexto (usuario, permisos, `id_solicitud`) viaja por la solicitud, nunca en variables globales.
- **Concurrencia en datos:** transacciones cortas, bloqueo optimista con columna `version`, operaciones **idempotentes** (llave de idempotencia en POST críticos).
- **Cola de trabajos** (Celery/RQ/BullMQ/RabbitMQ) con workers escalables horizontalmente; prioridades por tipo de tarea.
- **Eventos** para desacoplar módulos (ej: `USUARIO_CREADO` → enviar correo, actualizar métricas) en vez de llamadas encadenadas.
- **Límites por usuario** (cuotas de uso de LLM, solicitudes por minuto) para que uno no acapare recursos.

## 6. Observabilidad y despliegue

- Registro estructurado JSON con `id_solicitud` propagado entre servicios.
- Métricas: latencia p95, tasa de errores, uso de tokens/costo, tamaño de colas.
- Endpoints `GET /salud` (vive) y `GET /listo` (dependencias ok).
- Contenedores Docker, configuración por entorno (`desarrollo`, `pruebas`, `produccion`), CI con pruebas antes de desplegar.
- Escalado horizontal de API y workers por separado.

## Estructura sugerida (extiende la de MVC)

```
proyecto/
├── CONTROLADORES/
├── SERVICIOS/
│   └── PASARELA_MODELOS.py
├── MODELOS/
├── VISTAS/
├── ESQUEMAS/
├── ADAPTADORES/
│   ├── ADAPTADOR_LLM_ANTHROPIC.py
│   ├── ADAPTADOR_BLM_LOCAL.py
│   └── ADAPTADOR_API_PAGOS.py
├── TRABAJOS/            # tareas de la cola
│   └── TRABAJO_PROCESAR_DOCUMENTO.py
├── PROMPTS/
├── EVENTOS/
├── ERRORES/
├── CONFIGURACION/
└── PRUEBAS/
```

## Lista de revisión de diseño
- [ ] ¿La API es sin estado y escalable a N réplicas?
- [ ] ¿Pool de conexiones, índices, paginación y caché?
- [ ] ¿Cada API externa detrás de un adaptador con interfaz común?
- [ ] ¿Todo uso de LLM/BLM pasa por la pasarela, con respaldo, límites y registro de costo?
- [ ] ¿Tareas largas en cola, idempotentes?
- [ ] ¿Aislamiento entre usuarios/organizaciones y control de concurrencia?
- [ ] ¿Salud, métricas y registro con `id_solicitud`?
