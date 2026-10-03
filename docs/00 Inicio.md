---
tipo: indice
tags: [inicio, mapa]
actualizado: 2026-10-03
---

# EntrevistaAPP · Backend — Empieza aquí

Bóveda de Obsidian con todo lo necesario para trabajar en el backend de **Entrevistas IA**: qué es el producto,
qué se quiere lograr, cómo está construido y cómo trabajar en él. Está pensada para que una persona o un agente
de IA que llega por primera vez pueda ponerse a trabajar sin preguntar.

> [!tip] Para abrirla
> En Obsidian: *Open folder as vault* → seleccionar la carpeta `docs/` del repositorio.
> Fuera de Obsidian se lee igual: son archivos Markdown; cada enlace entre corchetes dobles apunta al archivo `.md` con ese nombre.

## En 1 minuto

**Entrevistas IA** es una app móvil (Android) para **practicar entrevistas laborales** técnicas y de comportamiento,
con **tests de nivelación**, **práctica guiada** y **feedback** automático (motor propio o IA). Este repositorio es el
**backend**: una API REST en **Kotlin + Ktor** sobre **PostgreSQL** y **Redis**, organizada en capas MVC con nombres en español.

- Producto y objetivos → [[Vision y objetivos]]
- Qué entra y qué no → [[Alcance y limites]]
- Cómo está hecho → [[Arquitectura general]]
- Levantarlo en local → [[Puesta en marcha]]
- Reglas para escribir código → [[Convenciones de codigo]]
- **Si eres un agente de IA** → [[Guia para agentes]] (obligatorio antes de tocar nada)

## Mapa de la bóveda

### 01 · Proyecto
- [[Vision y objetivos]] — problema, propuesta de valor, objetivos SMART
- [[Contexto del proyecto]] — origen académico, equipo, plazos, presupuesto
- [[Alcance y limites]] — qué incluye, qué no (L01–L10), restricciones
- [[Requisitos y criterios de aceptacion]] — R01–R14, C01–C15 y cómo los cubre el backend
- [[Usuarios y modelo de negocio]] — perfiles de usuario, freemium / premium

### 02 · Arquitectura
- [[Arquitectura general]] — capas, carpetas, flujo de una petición, dependencias
- [[Modelo de datos]] — tablas por dominio, migraciones
- [[Flujos de negocio]] — entrevista, práctica, nivelación, reporte, sincronización offline
- [[Manejo de errores y resiliencia]] — errores de dominio, reintentos, cortocircuito, caché
- [[Seguridad]] — JWT, contraseñas, límites, datos personales

### 03 · Tecnologías
- [[Kotlin y Ktor]] · [[PostgreSQL y Exposed]] · [[Redis]] · [[Autenticacion JWT y Google]]
- [[LLM OpenAI y Anthropic]] · [[APIs de empleo]] · [[Google Play Billing]] · [[Correo SMTP]]
- [[Docker]] · [[Pruebas]] · [[Postman y documentacion de la API]]

### 04 · Guías
- [[Puesta en marcha]] — requisitos, `.env`, base de datos, ejecutar, probar
- [[Convenciones de codigo]] — MVC, nombres, SOLID, errores
- [[Como agregar un endpoint]] — checklist de principio a fin
- [[Guia para agentes]] — reglas de trabajo seguro para agentes de IA
- [[Glosario]]

### 05 · Estado
- [[Estado del proyecto]] — fases terminadas, ramas, PR
- [[Decisiones]] — decisiones de arquitectura y por qué
- [[Pendientes y deuda tecnica]]
- [[Riesgos]]

## Otros documentos del repositorio
- `../documentacion/API.md` — referencia de todos los endpoints (generada) y `../documentacion/postman/` — colección de Postman.
- [[PLAN_REFACTORIZACION]] — plan y bitácora detallada de las fases 0–7.
- `legal/` — EULA, términos y privacidad (**los lee el servidor**: no mover).
- `pull-requests/` — borradores de descripción de PR.
- La app Android está en el repositorio hermano `EntrevistaAPPAndroid`; la investigación original en `DOCUMENTOS_INVESTIGACION/`
  y una bóveda de producto previa en `Knowledge-Base/` (ambos fuera de este repositorio, en `proyecto_titulo/`).
