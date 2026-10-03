-- Fase 7: reporte de feedback de la entrevista.
--   * Cómo se evaluaron las respuestas abiertas (motor freemium o IA) y, si fue con IA, modelo, tokens y costo.
--   * Cuántas veces se intentó generar el reporte (el reintento manual tiene un tope).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS modo_evaluacion     VARCHAR(10)  NOT NULL DEFAULT 'freemium';
ALTER TABLE reporte_entrevista DROP CONSTRAINT IF EXISTS reporte_entrevista_modo_evaluacion_check;
ALTER TABLE reporte_entrevista ADD CONSTRAINT reporte_entrevista_modo_evaluacion_check
    CHECK (modo_evaluacion IN ('freemium', 'ia'));
ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS modelo_llm          VARCHAR(60);
ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS tokens_entrada      INTEGER;
ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS tokens_salida       INTEGER;
ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS costo_usd           NUMERIC(8,6);
ALTER TABLE reporte_entrevista ADD COLUMN IF NOT EXISTS intentos_generacion SMALLINT     NOT NULL DEFAULT 0;

COMMIT;
