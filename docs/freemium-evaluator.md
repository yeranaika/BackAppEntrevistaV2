# Evaluador freemium

El endpoint `POST /api/v1/practice/evaluate-freemium` usa el evaluador determinista local. Al iniciar, el servidor envía solicitudes HTTP locales para calentar sus rutas y el runtime de Kotlin. La cantidad predeterminada es 2000 y se puede ajustar con `FREEMIUM_HTTP_WARMUP`; al configurarlo en `0`, se desactiva el calentamiento.

`GET /health` es una comprobación de liveness: responde `OK` mientras el proceso siga vivo. `GET /ready` es la comprobación de disponibilidad: responde `503 {"status":"warming"}` durante el calentamiento y `200 {"status":"ready"}` cuando termina. Si el calentamiento falla, se registra un `WARN` con el motivo y el servicio pasa igualmente a estado listo.

Configura el healthcheck del despliegue en Railway para consultar **`/ready`**. No uses `/health` para decidir cuándo enviar tráfico.
