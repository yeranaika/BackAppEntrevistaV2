# Instrucciones para agentes

Backend de **Entrevistas IA** (Kotlin + Ktor + PostgreSQL + Redis). Todo el contexto está en la bóveda de Obsidian
de [`docs/`](docs/): empieza por [`docs/00 Inicio.md`](docs/00%20Inicio.md).

Antes de cambiar código, lee obligatoriamente:
1. [`docs/04 Guias/Guia para agentes.md`](docs/04%20Guias/Guia%20para%20agentes.md) — reglas de trabajo seguro (servicios con costo, correos, BD compartida, contrato Android).
2. [`docs/04 Guias/Convenciones de codigo.md`](docs/04%20Guias/Convenciones%20de%20codigo.md) — capas MVC, archivos en MAYÚSCULAS, nombres en español.
3. [`docs/05 Estado/Estado del proyecto.md`](docs/05%20Estado/Estado%20del%20proyecto.md) — última rama y qué está hecho.

Reglas que nunca se rompen:
- Alcance: **solo backend**. La app Android (`EntrevistaAPPAndroid`) es solo referencia de su contrato JSON, que no se puede romper.
- No llamar al LLM real, a las APIs de empleo ni mandar correos sin que la persona lo pida.
- No usar el servidor del puerto 8080 del desarrollador: levantar otro en 8093 para probar. Limpiar los datos de prueba.
- `./gradlew test` completo en verde antes de cerrar una tarea; documentación de la API regenerada con `python docs/documentacion/GENERAR_DOCUMENTACION.py`.
- No hacer push, PR ni acciones irreversibles sin pedido explícito.
