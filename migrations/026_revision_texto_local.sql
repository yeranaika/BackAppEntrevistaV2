-- Respuestas de texto libre revisadas sin conexión en el teléfono (T3.13).
--   * La app descarga preguntas abiertas (con respuesta_ideal y rúbrica), la persona responde sin internet y el teléfono
--     las revisa con la fórmula léxica (sin IA). Al volver la conexión sube lo respondido a POST /api/v1/revision-local/respuestas.
--   * El servidor vuelve a revisar el texto con la MISMA fórmula (score_servidor es el que vale) y conserva el puntaje del
--     dispositivo solo para comprobar la paridad.
--   * Idempotente por (usuario_id, id_local): un reenvío no duplica. Solo texto: nunca audio ni video (RNF10).
--   * ON DELETE CASCADE con el usuario (RNF05, eliminar cuenta); si la pregunta se borra, la respuesta se conserva.
-- Idempotente: se puede ejecutar más de una vez.
BEGIN;
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS revision_texto_local (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        UUID         NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    id_local          VARCHAR(64)  NOT NULL,
    pregunta_id       UUID         REFERENCES pregunta(pregunta_id) ON DELETE SET NULL,
    texto             TEXT         NOT NULL,
    score_dispositivo NUMERIC(5,2),
    banda_dispositivo VARCHAR(10),
    score_servidor    NUMERIC(5,2) NOT NULL CHECK (score_servidor BETWEEN 0 AND 100),
    banda_servidor    VARCHAR(10)  NOT NULL CHECK (banda_servidor IN ('APROBADA', 'CERCANA', 'REVISAR')),
    version_revisor   SMALLINT     NOT NULL DEFAULT 1,
    revisado_en       TIMESTAMPTZ,
    recibido_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT revision_texto_local_unica UNIQUE (usuario_id, id_local)
);
CREATE INDEX IF NOT EXISTS idx_revision_texto_local_usuario ON revision_texto_local(usuario_id, recibido_en DESC);

COMMIT;
