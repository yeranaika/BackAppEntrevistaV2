-- Métricas de video idempotentes (D21).
--   La app reenvía un lote si no recibió la respuesta (red lenta, tiempo máximo al terminar la entrevista). Sin esta
--   restricción el lote quedaba guardado dos veces y pesaba el doble en el promedio del lenguaje corporal.
--   * Borra los duplicados que ya existan (misma sesión y mismo timestamp_ms), dejando una sola fila.
--   * Índice único (sesion_id, timestamp_ms): reemplaza al índice simple idx_metrica_video, que queda redundante.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

DELETE FROM metrica_video m
 USING metrica_video otra
 WHERE m.sesion_id = otra.sesion_id
   AND m.timestamp_ms = otra.timestamp_ms
   AND m.ctid > otra.ctid;

CREATE UNIQUE INDEX IF NOT EXISTS uq_metrica_video_sesion_ms
    ON metrica_video (sesion_id, timestamp_ms);

DROP INDEX IF EXISTS idx_metrica_video;

COMMIT;
