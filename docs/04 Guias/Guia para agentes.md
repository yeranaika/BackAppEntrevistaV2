---
tipo: guia
tags: [guia, agentes, ia, reglas]
actualizado: 2026-10-03
---

# Guía para agentes de IA

Este backend lo trabajan **varios agentes** (y personas) en paralelo. Leer esta nota completa antes de cambiar algo.

## Antes de empezar
1. Leer [[00 Inicio]], [[Arquitectura general]], [[Convenciones de codigo]] y la nota de la tecnología que vas a tocar.
2. Revisar [[Estado del proyecto]] (qué rama es la última) y [[Pendientes y deuda tecnica]].
3. Si tu entorno tiene skills, cargar `programacion-mvc`, `manejo-errores` y `arquitectura-software`.
4. `git status` y `git log --oneline -5`: trabajar sobre la última rama, en una **rama nueva** para tu tarea.

## Reglas obligatorias
| Regla | Por qué |
|---|---|
| **Alcance: solo backend.** La app Android (`EntrevistaAPPAndroid`) se lee solo como referencia del contrato JSON | Decisión del equipo |
| **No romper el contrato de Android** (rutas, nombres de campos, códigos de error que lee) | La app está en uso |
| **No llamar servicios con costo** sin pedido explícito: LLM (`-ConIa`, generar-ia), APIs de empleo (sync, generate) | Cuestan dinero / cuota |
| **No mandar correos reales** (`forgot-password`) en pruebas | Usa una cuenta real de Gmail |
| **No usar el servidor del puerto 8080** si es el del desarrollador; levantar otro en **8093** para probar | No interferir |
| **Limpiar los datos de prueba** que crees (usuarios `e2e_*`, cargos, preguntas, códigos) | BD compartida en local |
| **Respaldo antes de migrar** la BD local (`pg_dump -n app`) y migraciones idempotentes | Datos del desarrollador |
| No editar a mano `docs/documentacion/API.md` ni la colección de Postman: regenerarlas | Se desincronizan |
| No guardar secretos en el código ni en la bóveda; `.env` nunca al repo | Seguridad |
| No hacer `push`, PR, borrar ramas ni otras acciones visibles sin que la persona lo pida | Son irreversibles o públicas |
| Si Docker está cerrado, **no** encenderlo por tu cuenta: avisar | Es el entorno de la persona |

## Definición de "terminado"
- `./gradlew test` completo en verde (repetir si hay selección aleatoria).
- E2E de la fase afectada en verde contra un servidor en 8093, y la regresión de las demás fases.
- Reglas críticas con prueba de mutación (romper la regla → una prueba falla).
- Documentación regenerada y bóveda actualizada si cambió un flujo, decisión o estado.
- Commit con mensaje claro en la rama de la tarea.

## Coordinación entre agentes
- Una tarea = una rama. No trabajar dos agentes en la misma rama a la vez.
- Antes de editar un archivo que otro agente pudo cambiar, volver a leerlo.
- Registrar decisiones nuevas en [[Decisiones]] y cambios de estado en [[Estado del proyecto]] (con fecha).
- Dejar en el resumen final: qué se hizo, cómo se probó, qué quedó pendiente y qué decisiones debe tomar la persona.

## Comandos útiles
```bash
./gradlew test
./gradlew run --args="-P:ktor.deployment.port=8093"
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_N_*.ps1 -UrlBase http://127.0.0.1:8093
python docs/documentacion/GENERAR_DOCUMENTACION.py
docker exec Entrevista_APP pg_dump -U root -d DBentrevista -n app > respaldo.sql
```

Relacionado: [[Como agregar un endpoint]] · [[Pruebas]] · [[Riesgos]]
