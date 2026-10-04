# Documentación: API, colección de Postman y bóveda de Obsidian del proyecto

## Resumen
Documentación del backend para el equipo y para los agentes de IA que trabajan en él. No cambia código de la aplicación. Rama apilada sobre `refactor/fase-7-feedback`.

## Qué incluye
- **`docs/documentacion/API.md`**: autenticación, formato de error, convenciones, los 101 endpoints con su body de ejemplo, reglas de negocio por módulo y el catálogo de los 138 códigos de error (leído del código fuente).
- **`docs/documentacion/postman/`**: colección (19 carpetas, 105 requests en el orden del flujo, con scripts que guardan tokens e ids) y entorno local. Reemplaza las colecciones antiguas de `postman/`, que describían endpoints que ya no existen.
- **`docs/documentacion/GENERAR_DOCUMENTACION.py`**: genera `API.md` y la colección desde una sola definición; `--probar` recorre la colección contra un servidor.
- **Bóveda de Obsidian en `docs/`**: visión, contexto, alcance, requisitos (R01–R14, C01–C15 con su estado), arquitectura, modelo de datos, flujos, una nota por tecnología, guías (puesta en marcha, convenciones, cómo agregar un endpoint, guía para agentes, glosario) y estado (fases, decisiones, pendientes, riesgos).
- **`AGENTS.md` y `CLAUDE.md`** en la raíz: apuntan a la bóveda y fijan las reglas para agentes.
- El plan de refactorización pasa a `docs/`; el README apunta a la documentación nueva.
- Reglas de Obsidian en el `.gitignore` principal, acotadas a `docs/.obsidian/`.

## Pruebas
- Colección probada contra el servidor real: **78 de 78** requests responden lo esperado (las marcadas ⚠️ —costo, cuota, correos, borrados— se omiten). Se corrigió el `area` de los ejemplos, que el backend rechazaba.
- Todas las rutas del servidor (101) tienen su request en la colección; los 40 enlaces internos de la bóveda resuelven.

## Pendiente registrado
Mensajes de éxito: varias respuestas OK salen como `{}` (`encodeDefaults = false` omite `ok = true`). Queda en `docs/05 Estado/Pendientes y deuda tecnica.md` para una próxima tarea.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
