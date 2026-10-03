---
tipo: tecnologia
tags: [tecnologia, kotlin, ktor]
version: Kotlin 2.2.21 · Ktor 3.3.1 · JVM 21
actualizado: 2026-10-03
---

# Kotlin y Ktor

**Para qué**: lenguaje y framework HTTP del backend. Mismo lenguaje que la app Android (se comparten conceptos y DTO).

| | |
|---|---|
| Lenguaje | Kotlin **2.2.21**, compila para **JVM 21** (`jvmToolchain(21)`) |
| Servidor | Ktor **3.3.1** con motor Netty, puerto `8080` (`src/main/resources/application.yaml`) |
| Build | Gradle (wrapper incluido: `./gradlew` / `.\gradlew.bat`) |
| JSON | `kotlinx.serialization` (DTO `@Serializable` en `ESQUEMAS/`) |
| Corrutinas | Todo el acceso a BD y red es `suspend`; JDBC corre en `Dispatchers.IO` |

## Plugins de Ktor usados
`ContentNegotiation` (JSON), `Authentication` + `auth-jwt` + OAuth (Google web), `StatusPages` (errores),
`RateLimit` (límites por IP), `CORS`, `CallLogging`. Se configuran en `CONFIGURACION/CONFIGURACION_*.kt`.

## Detalles que hay que saber
- **Todas las dependencias de Ktor fijas en 3.3.1.** Con versiones dinámicas (`3.+`) se mezclaron 3.6 y 3.3 y las
  pruebas fallaban con `NoSuchMethodError`. No usar `+` en dependencias de Ktor.
- Serialización de producción: `ignoreUnknownKeys = true`, `encodeDefaults = false`. Consecuencia: un campo con valor
  por defecto igual al valor **no se envía**. Si el cliente necesita el campo siempre (ej. `puedeReintentar: false`,
  o `area`/`nivel`/`metadata` que la app declara sin default), **declararlo sin valor por defecto**.
- Rutas con el mismo prefijo pueden declararse en controladores distintos (Ktor las une).
- `call.parameters` incluye ruta y query; para query usar `enteroDeConsulta`, `booleanoDeConsulta`, `uuidDeConsulta`
  (`UTILIDADES/UTILIDAD_PARAMETROS.kt`), que responden 400 con código claro.

## Ejecutar
```bash
./gradlew run                                       # puerto 8080
./gradlew run --args="-P:ktor.deployment.port=8093" # otro puerto
./gradlew test                                      # pruebas
```
> [!note] El filtro `--tests` de Gradle no encuentra las clases en este proyecto: correr la suite completa.

Relacionado: [[Arquitectura general]] · [[Pruebas]] · [[Puesta en marcha]]
