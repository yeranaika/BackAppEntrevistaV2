---
tipo: tecnologia
tags: [tecnologia, pruebas, e2e]
actualizado: 2026-10-03
---

# Pruebas

Tres niveles. **Toda fase o cambio de comportamiento debe dejar los tres en verde.**

## 1. Unitarias e integración (`./gradlew test`)
- JUnit (kotlin-test) + **H2** en memoria en modo PostgreSQL + `ktor-server-test-host`. ~260 pruebas.
- `src/test/kotlin/PRUEBAS/DOBLES/BD_PRUEBA.kt` → **`SistemaPrueba`**: arma los **servicios reales** sobre H2, con dobles
  solo para lo externo (correo, Google, LLM, APIs de empleo, Google Play, caché, reloj ajustable). Monta las rutas igual que
  producción (misma serialización y errores).
- Archivos `PRUEBA_SERVICIO_*.kt` (reglas) y `PRUEBA_CONTROLADOR_*.kt` (contrato HTTP).
- Contrato Android: las pruebas de controlador decodifican las respuestas con **copias de los DTO de la app** y su `Json`.
- Ayudas: `fallaCon<ErrorX>("codigo") { … }`, `sembrarPregunta(...)`, `sembrarBancoGeneral(...)`, `crearCatalogo(...)`.

Reglas de las pruebas JUnit en este proyecto:
- Los métodos deben devolver `Unit`: usar `= runBlocking<Unit> { … }`.
- Los nombres con backticks no pueden tener `:` ni `;`.
- Correr la suite completa (el filtro `--tests` no funciona aquí). Repetirla si hay selección aleatoria (preguntas al azar).

## 2. E2E contra el servidor real (`PRUEBAS_E2E/`, PowerShell 7)
Un script por fase, contra Postgres y Redis reales. Crean datos `e2e_*` y **los borran al terminar**.
```bash
./gradlew run --args="-P:ktor.deployment.port=8093"          # en otra terminal
pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_1_LOGIN.ps1 -UrlBase http://127.0.0.1:8093
```
| Script | Cubre |
|---|---|
| `FASE_1_LOGIN` | Login, refresh, logout, Google |
| `FASE_2_USUARIO` | Cuenta, perfil, onboarding, contraseñas, admin |
| `FASE_3_PREGUNTAS` | Banco de preguntas (`-ConIa`: generación real, cuesta) |
| `FASE_4_INTEGRACIONES` | Caché, mercado, consentimientos, recordatorios, billing, bloqueo de login (`-ConLimites`) |
| `FASE_5_ENTREVISTA` | Entrevista por sesión y contrato Android |
| `FASE_6_PRUEBA` | Práctica, nivelación, sincronización offline |
| `FASE_7_FEEDBACK` | Reporte de feedback (`-ConIa`: evaluación real, cuesta) |
Utilidades comunes en `UTILIDAD_E2E.ps1`. Los `.ps1` deben guardarse **UTF-8 con BOM**. En PowerShell usar `${VAR}?x` (no `$VAR?x`).

## 3. Colección de Postman
`python documentacion/GENERAR_DOCUMENTACION.py --probar <url> <correo> <clave> <correoAdmin> <claveAdmin>` recorre la
colección contra un servidor. → [[Postman y documentacion de la API]]

## Prueba de mutación (recomendada en reglas críticas)
Romper a propósito la regla (ej. quitar un `WHERE estado = …`) y confirmar que alguna prueba falla; luego restaurar.

Relacionado: [[Puesta en marcha]] · [[Guia para agentes]]
