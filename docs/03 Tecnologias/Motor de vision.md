---
tipo: tecnologia
tags: [tecnologia, vision, onnx, motor]
version: ONNX Runtime 1.20.0 · Ktor 3.3.1
actualizado: 2026-11-20
---

# Motor de visión (T3.11)

**Para qué**: el modelo DivLara E14 ya **no se carga en el backend**. Corre en un proceso aparte (`motor-vision`) y el
backend lo consulta por HTTP. Cambiar de modelo, mejorarlo o cambiar de proveedor = redesplegar el motor (o apuntar
`MOTOR_ENDPOINT` a otro servicio con el mismo contrato), sin tocar el backend. El video nunca sale del teléfono: solo viajan
landmarks.

## Módulos y puertos
| Módulo | Qué es | Puerto |
|---|---|---|
| raíz (backend) | API de la app; `ClienteMotorVision` reenvía a `MOTOR_ENDPOINT` | 8093 |
| `:motor-vision` | App Ktor con el modelo ONNX: `POST /predict`, `GET /health` | 8094 |
| `:nucleo-vision` | Compartido: contrato JSON, errores, validación, extractor de 343 features (sin ONNX) | — |

Mismo código en el motor que antes en el backend ⇒ misma paridad (golden `golden.json` y `golden_features.json`).

## Contrato
- `POST /predict`: mismo cuerpo que `/api/v1/vision/predecir` de la app (`features` o `frames`) → `{versionModelo, predicciones (20), escala}`.
- Backend: valida sesión y entrada, reenvía, y devuelve lo mismo + `mensaje`. Guarda la predicción si viene `sesionId`.
- Tiempo máximo 3 s por intento (`MOTOR_TIMEOUT_MS`) y 1 reintento (solo red/5xx); cortocircuito tras 5 caídas seguidas.
- Motor caído, lento o con respuesta ilegible ⇒ `503 MOTOR_NO_DISPONIBLE`. Motor arriba sin modelo ⇒ `503 modelo_no_disponible`.
- El motor **no tiene autenticación**: es interno, no se expone a internet (red privada / mismo host).

## Cómo se usa
```bash
./gradlew :motor-vision:run                       # motor en 8094 (lee models/<MODELO_VERSION>/ de la raíz)
MOTOR_ENDPOINT=http://localhost:8094/predict      # en el backend (por defecto ese valor); MOTOR_TIMEOUT_MS opcional
MODELOS_DIRECTORIO=models MODELO_VERSION=divlara-e14-mlp   # en el motor, para otro modelo
```
`/health` del backend informa el último estado conocido del motor (sin bloquear).
