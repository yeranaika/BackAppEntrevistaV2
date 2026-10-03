-- Fase 4: deja la BD igual a src/DB/BasedeDatos.EntrevistaApp.sql y retira la dependencia de
-- SchemaUtils.createMissingTablesAndColumns, que había alterado el esquema real:
--   * consentimiento: columna extra "alcances" (el código guardaba ahí y alcances_aceptados quedaba en []),
--     y fecha_otorgado sin zona horaria;
--   * usuario.fecha_creacion sin zona horaria; perfil_usuario.nivel_experiencia ampliado a VARCHAR(40);
--   * índices duplicados; objetivo_carrera solo existía porque la creaba Exposed.
-- Además agrega el hash del token de compra de Google Play (un pago no puede activar dos cuentas).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

-- Objetivo de carrera (usado por /me/objetivo y el onboarding)
CREATE TABLE IF NOT EXISTS objetivo_carrera (
    objetivo_id  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID         NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    nombre_cargo VARCHAR(120) NOT NULL,
    sector       VARCHAR(50),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_objetivo_carrera_usuario_activo
    ON objetivo_carrera(usuario_id) WHERE activo = TRUE;

-- Consentimiento: rescatar lo guardado en la columna extra antes de eliminarla
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'app' AND table_name = 'consentimiento' AND column_name = 'alcances') THEN
        UPDATE consentimiento c
           SET alcances_aceptados = COALESCE(
                   (SELECT jsonb_agg(clave ORDER BY clave)
                      FROM jsonb_each_text(c.alcances) AS a(clave, valor)
                     WHERE valor = 'true'), '[]'::jsonb),
               acepta_entrenamiento_ia = COALESCE((c.alcances ->> 'ia_entrenamiento')::boolean, FALSE)
         WHERE c.alcances IS NOT NULL;
        ALTER TABLE consentimiento DROP COLUMN alcances;
    END IF;
END $$;

ALTER TABLE consentimiento ALTER COLUMN fecha_otorgado TYPE TIMESTAMPTZ USING fecha_otorgado AT TIME ZONE 'UTC';
ALTER TABLE consentimiento ALTER COLUMN fecha_otorgado SET DEFAULT now();
ALTER TABLE usuario ALTER COLUMN fecha_creacion TYPE TIMESTAMPTZ USING fecha_creacion AT TIME ZONE 'UTC';
ALTER TABLE usuario ALTER COLUMN fecha_creacion SET DEFAULT now();

-- El CHECK solo admite junior/semisenior/senior
ALTER TABLE perfil_usuario ALTER COLUMN nivel_experiencia TYPE VARCHAR(20);

-- Índices que Exposed creó encima de los del SQL
DROP INDEX IF EXISTS consentimiento_usuario_id;
DROP INDEX IF EXISTS perfil_usuario_usuario_id;

-- Google Play: un token de compra pertenece a una sola cuenta
ALTER TABLE suscripcion ADD COLUMN IF NOT EXISTS token_compra_hash TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_suscripcion_token_compra
    ON suscripcion(token_compra_hash) WHERE token_compra_hash IS NOT NULL;

COMMIT;
