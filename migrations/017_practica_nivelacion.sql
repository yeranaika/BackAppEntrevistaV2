-- Fase 6: práctica y nivelación.
--   * La app practica por cargo (no por una skill): sesion_practica.skill_id pasa a ser opcional y se admite el modo 'mixto'.
--   * Las preguntas servidas se guardan al crear la sesión (preguntas_snap) para validar y corregir las respuestas después.
--   * Una respuesta por pregunta servida (índice único por orden).
--   * Sincronización offline idempotente: id_local único por usuario.
--   * V2 no usa onboarding_usuario (el objetivo vive en objetivo_carrera): la nivelación guarda el cargo directo.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

-- ─── Práctica ────────────────────────────────────────────────────────────────
ALTER TABLE sesion_practica ALTER COLUMN skill_id DROP NOT NULL;
ALTER TABLE sesion_practica DROP CONSTRAINT IF EXISTS sesion_practica_modo_check;
ALTER TABLE sesion_practica ADD CONSTRAINT sesion_practica_modo_check
    CHECK (modo IN ('opcion_multiple', 'abierta_texto', 'mixto'));
ALTER TABLE sesion_practica ADD COLUMN IF NOT EXISTS cargo_objetivo VARCHAR(120);
-- [{ "id", "pregunta_id", "orden", "enunciado", "tipo", "categoria", "nivel", "skill_id", "opciones", "respuesta_ideal", "palabras_clave" }]
ALTER TABLE sesion_practica ADD COLUMN IF NOT EXISTS preguntas_snap JSONB NOT NULL DEFAULT '[]';
-- Id que asigna la app a un intento hecho sin conexión
ALTER TABLE sesion_practica ADD COLUMN IF NOT EXISTS id_local VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sesion_practica_id_local
    ON sesion_practica(usuario_id, id_local) WHERE id_local IS NOT NULL;

DROP INDEX IF EXISTS idx_respuesta_practica_sesion;
CREATE UNIQUE INDEX IF NOT EXISTS idx_respuesta_practica_sesion_orden
    ON respuesta_practica(sesion_practica_id, orden);

-- ─── Nivelación ──────────────────────────────────────────────────────────────
ALTER TABLE intento_test ADD COLUMN IF NOT EXISTS cargo_id       UUID REFERENCES cargo(cargo_id) ON DELETE SET NULL;
ALTER TABLE intento_test ADD COLUMN IF NOT EXISTS cargo_objetivo VARCHAR(120);

ALTER TABLE resultado_nivelacion ALTER COLUMN onboarding_id DROP NOT NULL;
ALTER TABLE resultado_nivelacion ADD COLUMN IF NOT EXISTS cargo_id UUID REFERENCES cargo(cargo_id) ON DELETE SET NULL;

-- ─── Corrección de esquema ───────────────────────────────────────────────────
-- El valor por defecto 'intermedio' no lo admite el propio CHECK (un INSERT sin nivel fallaba).
ALTER TABLE skill_tendencia ALTER COLUMN nivel_requerido SET DEFAULT 'semisenior';

COMMIT;
