-- Historial de llamadas en vivo (T3.15): cada llamada queda registrada con su fecha, duración, nº de preguntas y el
-- resumen del reporte tabulado de la simulación (T3.10: acciones reconocidas y línea de tiempo).
--   * La app guarda la llamada en el teléfono (Room) y la sube cuando hay red; el servidor la conserva para consultarla
--     desde otro dispositivo. Idempotente por (usuario_id, id_local): un reenvío no duplica.
--   * resumen es JSONB { acciones:[{accion, veces, totalMs, maximoMs}], linea:[{inicioMs, finMs, accion}] }.
--     Solo números derivados y nombres de acciones: nunca landmarks ni video (RNF10).
--   * sesion_id es opcional (la sesión de entrevista de la que se derivó el reporte); si la sesión se borra la llamada
--     se conserva. ON DELETE CASCADE con el usuario (RNF05, eliminar cuenta).
-- Idempotente: se puede ejecutar más de una vez.
BEGIN;
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS llamada (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    id_local      VARCHAR(64)  NOT NULL,
    sesion_id     UUID         REFERENCES sesion_entrevista(sesion_id) ON DELETE SET NULL,
    fecha_inicio  TIMESTAMPTZ  NOT NULL,
    duracion_seg  INTEGER      NOT NULL CHECK (duracion_seg >= 0),
    preguntas_cnt INTEGER      NOT NULL DEFAULT 0 CHECK (preguntas_cnt >= 0),
    resumen       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    recibida_en   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT llamada_unica UNIQUE (usuario_id, id_local)
);
CREATE INDEX IF NOT EXISTS idx_llamada_usuario_fecha ON llamada(usuario_id, fecha_inicio DESC);

COMMIT;
