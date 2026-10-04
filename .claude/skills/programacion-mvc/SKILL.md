---
name: programacion-mvc
description: Estándar de programación del equipo. Usar SIEMPRE que se cree, modifique, refactorice o revise código en estos proyectos: estructura MVC (modelo-vista-controlador), principios SOLID, código limpio, nombres en español para desarrolladores latinoamericanos y archivos en MAYÚSCULAS_CON_GUION_BAJO (ej: CONTROLADOR_USUARIO).
---

# Estándar de programación

Público: equipo de desarrolladores latinoamericanos. El código debe leerse en **español**.

## 1. Arquitectura MVC (siempre)

Todo proyecto se organiza en capas. Ninguna capa se salta a otra.

```
proyecto/
├── CONTROLADORES/      # Reciben la solicitud, validan entrada, llaman al servicio, devuelven respuesta
│   └── CONTROLADOR_USUARIO.py
├── SERVICIOS/          # Lógica de negocio (reglas, cálculos, orquestación)
│   └── SERVICIO_USUARIO.py
├── MODELOS/            # Entidades y acceso a datos (repositorios)
│   ├── MODELO_USUARIO.py
│   └── REPOSITORIO_USUARIO.py
├── VISTAS/             # Plantillas, componentes de interfaz o serializadores de respuesta
│   └── VISTA_USUARIO.py
├── ESQUEMAS/           # Validación de entrada/salida (DTO)
│   └── ESQUEMA_USUARIO.py
├── ERRORES/
│   └── ERRORES_APLICACION.py
├── CONFIGURACION/
│   └── CONFIGURACION_GENERAL.py
├── UTILIDADES/
└── PRUEBAS/
    └── PRUEBA_SERVICIO_USUARIO.py
```

Reglas por capa:
- **Controlador:** delgado. Sin SQL, sin reglas de negocio. Solo: validar → llamar servicio → devolver vista/respuesta.
- **Servicio:** la lógica de negocio. No conoce HTTP (no recibe `request`, no devuelve códigos HTTP).
- **Modelo / Repositorio:** único lugar con consultas a la base de datos.
- **Vista:** solo presentación. Sin lógica de negocio ni consultas.
- Flujo permitido: `VISTA ⇄ CONTROLADOR → SERVICIO → MODELO`. Nunca `CONTROLADOR → MODELO` directo ni `VISTA → MODELO`.

## 2. Nombres de archivos y carpetas

- **MAYÚSCULAS con guion bajo (SNAKE mayúscula):** `CONTROLADOR_USUARIO`, `SERVICIO_PAGO`, `REPOSITORIO_PRODUCTO`, `ESQUEMA_PEDIDO`.
- Formato: `<TIPO>_<ENTIDAD>` en singular → `CONTROLADOR_USUARIO.py`, `MODELO_USUARIO.ts`.
- Excepciones obligatorias del ecosistema (no renombrar): `package.json`, `requirements.txt`, `Dockerfile`, `README.md`, `.env`, `__init__.py`, `index.ts` de punto de entrada si el framework lo exige.

## 3. Nombres en el código: español

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases | PascalCase | `ServicioUsuario`, `RepositorioPedido` |
| Funciones / métodos | snake_case (Python) o camelCase (JS/TS) | `obtener_usuario`, `calcularTotal` |
| Variables | igual que funciones | `total_pedido`, `listaProductos` |
| Constantes | MAYÚSCULAS | `MAXIMO_REINTENTOS`, `TIEMPO_ESPERA_DB` |
| Rutas / acciones | técnicas permitidas | `GET /usuarios`, `GET_USUARIOS`, `UPDATE_USUARIO` |

- Usar **verbos en español**: `obtener`, `crear`, `actualizar`, `eliminar`, `listar`, `validar`, `calcular`, `enviar`, `procesar`.
- **Palabras técnicas en inglés SÍ permitidas** cuando son el término estándar: `GET`, `POST`, `PUT`, `DELETE`, `update`, `insert`, `select`, `token`, `hash`, `cache`, `request`, `response`, `id`, `endpoint`, `middleware`, `query`, `commit`, `rollback`, `log`, `email`, `JSON`, `API`. Ej: `GET_USUARIOS`, `token_acceso`, `update_estado`.
- **Evitar inglés innecesario:** `getUserData` ❌ → `obtenerDatosUsuario` ✅; `isValid` ❌ → `esValido` ✅; `items` ❌ → `elementos` / `productos` ✅.
- Sin tildes ni `ñ` en identificadores (compatibilidad): `anio`, `contrasena`, `direccion`, `numero`. En textos y comentarios sí usar tildes.
- Booleanos con prefijo `es_`, `tiene_`, `puede_`: `es_activo`, `tiene_permiso`.
- Comentarios y mensajes al usuario en español.

## 4. SOLID

- **S – Responsabilidad única:** una clase/archivo = una razón para cambiar. `SERVICIO_USUARIO` no envía correos; usa `SERVICIO_CORREO`.
- **O – Abierto/cerrado:** agregar comportamiento con nuevas clases (estrategias), no con más `if/elif` sobre tipos.
- **L – Sustitución de Liskov:** las implementaciones de una interfaz se pueden intercambiar sin romper al que las usa.
- **I – Segregación de interfaces:** interfaces pequeñas (`LectorUsuario`, `EscritorUsuario`) en vez de una gigante.
- **D – Inversión de dependencias:** los servicios dependen de **interfaces**, y reciben sus dependencias por constructor (inyección). Nunca instanciar la db o el cliente LLM dentro del servicio.

```python
# SERVICIOS/SERVICIO_USUARIO.py
class ServicioUsuario:
    def __init__(self, repositorio: InterfazRepositorioUsuario, notificador: InterfazNotificador):
        self.repositorio = repositorio
        self.notificador = notificador

    def crear_usuario(self, datos: EsquemaCrearUsuario) -> Usuario:
        if self.repositorio.existe_correo(datos.correo):
            raise ErrorValidacion("CORREO_YA_REGISTRADO")
        usuario = self.repositorio.insertar(datos)
        self.notificador.enviar_bienvenida(usuario)
        return usuario
```

```python
# CONTROLADORES/CONTROLADOR_USUARIO.py
@ruta.post("/usuarios")
def POST_USUARIO(datos: EsquemaCrearUsuario, servicio: ServicioUsuario = Depende(obtener_servicio_usuario)):
    usuario = servicio.crear_usuario(datos)
    return VistaUsuario.desde_modelo(usuario)
```

## 5. Código limpio

- Funciones **cortas** (idealmente < 30 líneas) que hacen **una sola cosa**.
- Máximo 3–4 parámetros; si son más, usar un objeto/esquema.
- **Cláusulas de guarda** en vez de `if` anidados (máximo 2 niveles de anidación).
- **Sin números ni textos mágicos:** usar constantes en `CONFIGURACION/`.
- **Sin código duplicado:** extraer a función o utilidad.
- **Sin código comentado ni muerto.** Para eso está git.
- Comentarios explican el **por qué**, no el **qué**.
- Tipado siempre (type hints en Python, TypeScript estricto).
- Secretos solo en variables de entorno, nunca en el código.
- Cada servicio tiene pruebas en `PRUEBAS/` (usar dobles de prueba de las interfaces).
- Manejo de errores según la skill `manejo-errores`.

## Lista de revisión
- [ ] ¿Respeta capas MVC + servicio? ¿Controlador delgado?
- [ ] ¿Archivos en `TIPO_ENTIDAD` mayúsculas?
- [ ] ¿Nombres en español (salvo términos técnicos)?
- [ ] ¿Dependencias inyectadas por interfaz (SOLID)?
- [ ] ¿Funciones cortas, sin duplicados ni valores mágicos?
- [ ] ¿Tiene pruebas?
