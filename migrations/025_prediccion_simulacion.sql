-- Predicciones de la simulación por instante (T3.10): alimentan el reporte en texto tabulado
-- GET /api/v1/vision/reporte/{sesion}.
--   * Una fila por variable del modelo de visión (escala 0-8) y por predicción: (sesion_id, ts, variable).
--   * ts = milisegundos desde el inicio de la simulación. Solo números derivados: nunca landmarks ni video (RNF10).
--   * nivel = valor redondeado al entero más cercano de la escala 0-8.
--   * No reutiliza prediccion_vision: esa tabla exige modelo_id (FK a modelo_vision_ia, sin filas) y guarda JSON crudo.
--   * ON DELETE CASCADE: se van con la sesión (y la purga biométrica del Art. 17 las borra también).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS prediccion_simulacion (
    id         UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id  UUID             NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    ts         BIGINT           NOT NULL CHECK (ts >= 0),
    variable   VARCHAR(60)      NOT NULL,
    valor      DOUBLE PRECISION NOT NULL,
    nivel      SMALLINT         NOT NULL CHECK (nivel BETWEEN 0 AND 8),
    CONSTRAINT prediccion_simulacion_unica UNIQUE (sesion_id, ts, variable)
);
-- El índice único ya cubre (sesion_id, ts); este nombre explícito deja claro el acceso del reporte.
CREATE INDEX IF NOT EXISTS idx_prediccion_simulacion_sesion_ts ON prediccion_simulacion(sesion_id, ts);

COMMIT;
