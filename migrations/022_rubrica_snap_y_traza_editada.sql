-- Copia de la rúbrica por pregunta de entrevista y traza de IA coherente al editar (D24).
--   * sesion_pregunta_respuesta.rubrica_snap: la rúbrica de evaluación tal como estaba al servir la pregunta. El reporte
--     saca de ahí las palabras clave, así editar la rúbrica en el banco ya no cambia la evaluación de una entrevista
--     que todavía no tiene reporte (o que lo reintenta). '{}' = la pregunta no tenía rúbrica; NULL = fila anterior a
--     esta migración cuya pregunta ya se borró (el reporte la evalúa sin palabras clave, como hasta ahora).
--   * Las filas existentes se completan con la rúbrica actual del banco: es la misma que el reporte leía hasta hoy.
--   * Trazas de IA de preguntas que se editaron después de revisarlas: vuelven a 'pendiente_revision' sin revisor,
--     igual que la pregunta (que ya está en 'pendiente').
--   * Índice por fecha en pregunta_generacion_ia: el tope mensual de gasto (D23) suma el mes en cada pedido.
-- Aplicarla ANTES de desplegar el código que lee rubrica_snap (es aditiva: el código anterior ignora la columna) y
-- VOLVER A CORRERLA una vez desplegado: completa las filas y trazas que el código anterior dejó en ese intervalo.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS rubrica_snap JSONB;

UPDATE sesion_pregunta_respuesta s
   SET rubrica_snap = COALESCE(p.rubrica_evaluacion, '{}'::jsonb)
  FROM pregunta p
 WHERE s.pregunta_id = p.pregunta_id
   AND s.rubrica_snap IS NULL;

CREATE INDEX IF NOT EXISTS idx_gen_ia_fecha ON pregunta_generacion_ia(fecha_generacion);

UPDATE pregunta_generacion_ia g
   SET estado_revision = 'pendiente_revision', revisado_por = NULL
  FROM pregunta p
 WHERE g.pregunta_id = p.pregunta_id
   AND p.estado = 'pendiente'
   AND g.estado_revision IN ('aprobada', 'rechazada');

COMMIT;
