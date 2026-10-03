# Documentación de la API

| Archivo | Qué es |
|---|---|
| [API.md](API.md) | Referencia completa: autenticación, formato de error, cada endpoint con su body de ejemplo y el catálogo de códigos de error. |
| [postman/EntrevistaAPP.postman_collection.json](postman/EntrevistaAPP.postman_collection.json) | Colección de Postman con todos los endpoints, en el orden del flujo. |
| [postman/EntrevistaAPP_local.postman_environment.json](postman/EntrevistaAPP_local.postman_environment.json) | Entorno local (URL, correo y contraseña de prueba). |
| [GENERAR_DOCUMENTACION.py](GENERAR_DOCUMENTACION.py) | Genera los tres archivos anteriores desde una sola definición. |

Esta carpeta es parte de la bóveda de Obsidian de [`docs/`](..) (empieza por `docs/00 Inicio.md`), donde está el contexto del proyecto.

## Usar la colección en Postman

1. **Importar**: en Postman, *Import* → arrastrar los dos archivos de [`postman/`](postman/) (colección y entorno).
2. **Elegir el entorno** `EntrevistaAPP local` (arriba a la derecha) y revisar sus variables:
   - `baseUrl`: `http://localhost:8080` (o la URL del servidor que quieras usar).
   - `correo` / `contrasena`: la cuenta con la que vas a probar (si no existe, ejecutar **Registrar usuario**).
   - `correoAdmin` / `contrasenaAdmin`: una cuenta con rol `admin` (para las carpetas de administración).
3. **Iniciar sesión**: ejecutar **01 · Autenticación → Login** (y **Login admin** si vas a usar lo de admin).
   Los tokens se guardan solos en las variables de la colección; el resto de las requests ya los usan.
4. **Seguir el orden de las carpetas**: cada request guarda en variables los ids que necesita la siguiente
   (por ejemplo, *Crear cargo* guarda `cargoId`, *Iniciar entrevista* guarda `sesionId`, *Siguiente pregunta* guarda
   `preguntaSesionId` y `opcionId`). Se pueden ver y cambiar en la pestaña *Variables* de la colección.

> Cuando el `accessToken` vence (15 minutos) la API responde `401`: ejecutar **Renovar tokens** o volver a hacer **Login**.

### Flujo sugerido para probar todo

**Login admin** → **08 · Crear cargo** → **09 · Crear preguntas** (las cuatro de creación) → **Login** →
**10 · Iniciar entrevista** → *Siguiente pregunta* → *Responder* → *Finalizar* → **11 · Reporte** →
**12 · Práctica** → **13 · Nivelación** → **15 · App Android**.

También se puede ejecutar la colección completa con el *Collection Runner* de Postman.

### Requests con ⚠️

Están marcadas en su descripción. **No ejecutarlas sin querer**:
- *Generar preguntas con IA* llama al LLM (cuesta dinero).
- *Sincronizar tendencias* y *Regenerar requisitos* consumen cuota de las APIs de empleo.
- *Olvidé mi contraseña* manda un correo real.
- *Eliminar mi cuenta* y *Desactivar usuario* borran o desactivan datos.

## Actualizar la documentación

La colección y `API.md` salen de la definición de endpoints en [GENERAR_DOCUMENTACION.py](GENERAR_DOCUMENTACION.py).
Al agregar o cambiar un endpoint, editar la lista `CARPETAS` (método, ruta, auth, body de ejemplo, descripción y
qué variables guarda) y regenerar:

```bash
python docs/documentacion/GENERAR_DOCUMENTACION.py
```

El catálogo de códigos de error se arma leyendo el código fuente, así que siempre está al día.

Para comprobar que la colección funciona contra un servidor (recorre las requests en orden, salvo las marcadas ⚠️):

```bash
python docs/documentacion/GENERAR_DOCUMENTACION.py --probar http://localhost:8080 usuario@ejemplo.com Clave-segura-1 admin@ejemplo.com Clave-segura-1
```
