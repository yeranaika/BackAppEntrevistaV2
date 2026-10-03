-- Fase 5: simulación de entrevista.
--   * sesion_pregunta_respuesta guarda un snapshot completo de la pregunta (tipo, categoría, skill y opciones)
--     para que editar o borrar el banco no cambie una sesión ya rendida, y la opción elegida y la fecha de respuesta.
--   * Un usuario tiene como máximo una sesión en_progreso (antes el índice no era único).
--   * Cada sesión tiene un solo slot por orden.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS tipo_pregunta       VARCHAR(20);
ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS categoria_habilidad VARCHAR(10);
ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS skill_id            UUID REFERENCES skill(skill_id) ON DELETE SET NULL;
-- [{ "id": "...", "texto": "...", "es_correcta": true }]
ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS opciones_snap       JSONB;
-- Sin FK: apunta al snapshot, la opción original puede haberse editado o borrado
ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS opcion_elegida_id   UUID;
ALTER TABLE sesion_pregunta_respuesta ADD COLUMN IF NOT EXISTS fecha_respuesta     TIMESTAMPTZ;

DROP INDEX IF EXISTS idx_sesion_activa;
CREATE UNIQUE INDEX IF NOT EXISTS idx_sesion_activa_unica
    ON sesion_entrevista(usuario_id) WHERE estado = 'en_progreso';

DROP INDEX IF EXISTS idx_spr_sesion;
CREATE UNIQUE INDEX IF NOT EXISTS idx_spr_sesion_orden
    ON sesion_pregunta_respuesta(sesion_id, orden);

COMMIT;
