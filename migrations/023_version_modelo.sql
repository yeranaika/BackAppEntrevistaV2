-- Versión del modelo de visión por métrica de video (T3.4, RNF08).
--   * metrica_video.version_modelo: identifica qué versión del modelo produjo cada fila (ej. 'divlara-resnet-v1').
--     Admite NULL para filas históricas anteriores a esta migración.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE metrica_video ADD COLUMN IF NOT EXISTS version_modelo VARCHAR(50);

COMMIT;
