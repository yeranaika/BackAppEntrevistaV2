-- Límite de intentos para el código de recuperación de contraseña (Fase 2).
-- Sin límite, un código de 6 dígitos vigente 15 minutos se puede adivinar por fuerza bruta.
-- Aditiva y segura de re-ejecutar.
BEGIN;
SET search_path TO app, public;

ALTER TABLE password_reset
    ADD COLUMN IF NOT EXISTS intentos_fallidos SMALLINT NOT NULL DEFAULT 0;

-- Búsqueda del código vigente de un usuario en cada intento.
CREATE INDEX IF NOT EXISTS idx_password_reset_usuario_vigente
    ON password_reset(usuario_id, expires_at) WHERE used = FALSE;

COMMIT;
