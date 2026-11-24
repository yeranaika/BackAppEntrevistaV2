-- Plazo de conservación del consentimiento de una cuenta eliminada (D38, Ley 19.628 modificada por la Ley 21.719).
--   * cuenta_eliminada_en: cuándo se eliminó la cuenta. Desde ahí corren los 5 años; después el backend borra la fila
--     (TareaPurgaConsentimientos, una vez al día).
--   * retencion_legal: TRUE si hay un reclamo, procedimiento de la Agencia o juicio en curso que necesita esa prueba.
--     Esa fila no se borra aunque venza el plazo. Se marca y desmarca a mano, por SQL.
--   * Al eliminar la cuenta se borra la IP (ip_origen) y se cierra el consentimiento (fecha_revocado): para probar que
--     aceptó bastan la versión, las opciones, las fechas y el correo.
-- Para las cuentas eliminadas antes de esta migración el plazo se cuenta desde hoy (no se sabe la fecha real).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE consentimiento ADD COLUMN IF NOT EXISTS cuenta_eliminada_en TIMESTAMPTZ;
ALTER TABLE consentimiento ADD COLUMN IF NOT EXISTS retencion_legal BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE consentimiento
SET cuenta_eliminada_en = COALESCE(cuenta_eliminada_en, now()),
    fecha_revocado      = COALESCE(fecha_revocado, now()),
    ip_origen           = NULL
WHERE usuario_id IS NULL
  AND (cuenta_eliminada_en IS NULL OR fecha_revocado IS NULL OR ip_origen IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_consentimiento_cuenta_eliminada
    ON consentimiento(cuenta_eliminada_en) WHERE usuario_id IS NULL;

COMMIT;
