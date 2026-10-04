---
tipo: guia
tags: [guia, convenciones, mvc, estilo]
actualizado: 2026-10-03
---

# Convenciones de código

Se derivan de las skills del equipo en `proyectos_internos/.claude/skills/`: **`programacion-mvc`**, **`manejo-errores`**
y **`arquitectura-software`**. Si eres un agente con acceso a skills, cárgalas antes de escribir código.

## Estructura y nombres de archivos
- Carpetas por capa en **MAYÚSCULAS**: `CONTROLADORES/`, `SERVICIOS/`, `MODELOS/`… → [[Arquitectura general]]
- Archivos `TIPO_ENTIDAD.kt` en MAYÚSCULAS con guion bajo y en singular: `CONTROLADOR_USUARIO.kt`, `SERVICIO_PAGO.kt`,
  `REPOSITORIO_PRODUCTO.kt`, `TABLA_PREGUNTA.kt`, `ESQUEMA_PEDIDO.kt`, `VISTA_REPORTE.kt`, `PRUEBA_SERVICIO_X.kt`.
- El paquete Kotlin es el nombre de la carpeta (`package SERVICIOS`).

## Nombres en el código: español
| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases | PascalCase | `ServicioEntrevista`, `RepositorioPractica` |
| Funciones y variables | camelCase, verbos en español | `obtener`, `crearCodigo`, `listarResumenes` |
| Constantes | MAYÚSCULAS | `INTENTOS_MAXIMOS_REPORTE` (en `CONFIGURACION/CONSTANTES_*.kt`) |
| Booleanos | `es`, `tiene`, `puede` | `esPremium`, `estaRespondida`, `puedeReintentar` |
| Identificadores | **sin tildes ni ñ** | `anio`, `contrasena`, `nivelacion` |
- Términos técnicos en inglés permitidos cuando son el estándar: `token`, `hash`, `cache`, `endpoint`, `JSON`, `id`.
- **Excepción obligatoria**: los nombres JSON que ya usa la app Android se mantienen con `@SerialName` aunque estén en
  inglés o snake_case (`@SerialName("email") val correo`).
- Comentarios y mensajes al usuario en español con tildes. Los comentarios explican el **por qué**, no el qué.

## Reglas de capas (MVC + servicios)
- **Controlador**: deserializa, llama al servicio, responde la vista. Sin SQL, sin reglas, sin `if` de negocio.
- **Servicio**: reglas de negocio; no conoce HTTP; dependencias por constructor como **interfaces** (SOLID / DIP).
- **Repositorio**: único lugar con SQL; interfaz + `*Exposed`.
- **Vista**: solo convierte modelo → DTO.
- Nada se instancia fuera de `CONTENEDOR_DEPENDENCIAS.kt` (y `SistemaPrueba` en pruebas).

## Código limpio
Funciones cortas (≈30 líneas), máximo 3–4 parámetros (si no, un objeto), cláusulas de guarda, sin números ni textos
mágicos (constantes), sin código comentado ni muerto, sin duplicados. Secretos solo en variables de entorno.

## Errores
Lanzar errores de dominio con **código estable en snake_case** y mensaje en español: `throw ErrorConflicto("entrevista_en_progreso", "Ya tienes…")`.
Nunca `catch` vacío. Detalle en [[Manejo de errores y resiliencia]].

## Respuestas de éxito
Todo `POST`/`PUT`/`PATCH`/`DELETE` exitoso responde un `mensaje` en español para el usuario:
- Con recurso: `call.responderConMensaje(recurso.aRespuesta(), "Pregunta aprobada")` (agrega `mensaje` junto a los datos;
  estado opcional, ej. `HttpStatusCode.Created`).
- Sin recurso: `call.respond(RespuestaOk("Perfil actualizado"))` → `{"ok": true, "mensaje": "…"}`.
- `RespuestaMensaje("…")` cuando Android ya lee `message` (viajan `mensaje` y `message`).
- Ojo: con `encodeDefaults = false` un campo con valor por defecto no viaja si no cambia; por eso `RespuestaOk` no
  tiene defaults. `docs/documentacion/GENERAR_DOCUMENTACION.py --probar` falla si un éxito de escritura no trae `mensaje`.

## Pruebas
Cada servicio con su `PRUEBA_SERVICIO_*` y cada contrato HTTP con su `PRUEBA_CONTROLADOR_*` → [[Pruebas]].

## Git
- Una rama por fase o tema (`refactor/fase-N-…`, `refactor/documentacion-api`); las ramas actuales están **apiladas**.
- Mensajes de commit en español, describiendo el porqué. Si el commit lo hace un agente de Claude, terminar con
  `Co-Authored-By: Claude …` según la instrucción vigente del entorno.

Relacionado: [[Como agregar un endpoint]] · [[Guia para agentes]]
