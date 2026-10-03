---
tipo: tecnologia
tags: [tecnologia, postman, documentacion, api]
actualizado: 2026-10-03
---

# Postman y documentación de la API

El equipo usa **Postman** para ver y probar la API.

| Archivo | Contenido |
|---|---|
| `docs/documentacion/API.md` | Referencia completa: autenticación, formato de error, cada endpoint con body de ejemplo, catálogo de códigos de error |
| `docs/documentacion/postman/EntrevistaAPP.postman_collection.json` | Colección: carpetas en el orden del flujo, scripts que guardan tokens e ids |
| `docs/documentacion/postman/EntrevistaAPP_local.postman_environment.json` | Entorno local |
| `docs/documentacion/GENERAR_DOCUMENTACION.py` | **Genera los tres** desde una sola definición |
| `docs/documentacion/README.md` | Cómo importar y usar la colección |

## Regla de oro
**No editar `API.md` ni la colección a mano.** Al agregar o cambiar un endpoint: editar `CARPETAS` en
`GENERAR_DOCUMENTACION.py` (método, ruta, auth, body de ejemplo, descripción, variables que guarda) y ejecutar
`python docs/documentacion/GENERAR_DOCUMENTACION.py`. El catálogo de errores se lee del código fuente.

Marcar con `omitir_en_prueba=True` y ⚠️ en la descripción lo que cuesta dinero, consume cuota, manda correos o borra datos.

Relacionado: [[Como agregar un endpoint]] · [[Pruebas]]
