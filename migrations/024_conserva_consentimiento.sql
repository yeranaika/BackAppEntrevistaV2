-- El registro de consentimiento sobrevive al borrado de la cuenta (T0.9, regla de privacidad).
--   * Al eliminar la cuenta se borran TODOS los datos del usuario, salvo el consentimiento: es la prueba de que esa
--     persona aceptó los términos y condiciones (versión, alcances, fechas) en su momento.
--   * consentimiento.usuario_id pasa a ser NULLABLE con ON DELETE SET NULL (antes NOT NULL ... ON DELETE CASCADE).
--   * consentimiento.identidad_ref: copia del correo del usuario, que el backend guarda justo antes de borrarlo, para
--     saber QUIÉN aceptó aunque ya no exista la fila de usuario. NULL mientras la cuenta existe.
--   * consentimiento_texto (los textos legales) es global y no se toca.
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE consentimiento ADD COLUMN IF NOT EXISTS identidad_ref VARCHAR(320);
ALTER TABLE consentimiento ALTER COLUMN usuario_id DROP NOT NULL;

-- Reemplaza la FK con CASCADE por una con SET NULL (el nombre por defecto lo puso PostgreSQL al crear la tabla).
ALTER TABLE consentimiento DROP CONSTRAINT IF EXISTS consentimiento_usuario_id_fkey;
ALTER TABLE consentimiento DROP CONSTRAINT IF EXISTS fk_consentimiento_usuario;
ALTER TABLE consentimiento
    ADD CONSTRAINT fk_consentimiento_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuario(usuario_id) ON DELETE SET NULL;

COMMIT;
