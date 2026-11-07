-- Apagado ordenado (D20).
--   * intento_id: identifica el intento de generación en curso; solo ese intento puede escribir el resultado, así una
--     generación que el rescate dio por abandonada no pisa a un reintento posterior.
--   * Índices para que el rescate periódico de reportes interrumpidos sea barato: reportes en 'generando' (pocos) por
--     fecha de inicio del intento, y entrevistas finalizadas recientes por fecha de fin.
-- No cambia datos existentes. Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS intento_id UUID;

CREATE INDEX IF NOT EXISTS idx_reporte_generando
    ON reporte_entrevista (fecha_generacion) WHERE estado_generacion = 'generando';

CREATE INDEX IF NOT EXISTS idx_sesion_entrevista_finalizada
    ON sesion_entrevista (fecha_fin) WHERE estado = 'finalizada';

COMMIT;
