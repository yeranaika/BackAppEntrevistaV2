-- Voz en la llamada (VOZ-3/VOZ-4): transcripción por pregunta y diccionarios de terminología editables.
--   * transcripcion_respuesta: solo TEXTO. El audio nunca llega al servidor (ASR en el teléfono, RNF09/VOZ-5). Una fila por
--     (usuario, sesión, pregunta): reenviar la misma respuesta la reemplaza, salvo que lo guardado sea final y lo que llega
--     sea parcial. Se borra con la sesión, con la cuenta y con la purga biométrica.
--   * diccionario_terminologia: un paquete por área (programador-front, ...) con términos que sesgan el reconocedor de la app
--     y correcciones posteriores ("cotlin" -> "Kotlin"). version sube en cada edición: la app solo baja los que cambiaron.
-- Idempotente: se puede ejecutar más de una vez.
BEGIN;
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS transcripcion_respuesta (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   UUID         NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    sesion_id    UUID         NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    pregunta_id  UUID         NOT NULL,
    inicio_ms    BIGINT       NOT NULL CHECK (inicio_ms >= 0),
    fin_ms       BIGINT       NOT NULL CHECK (fin_ms >= inicio_ms),
    texto        TEXT         NOT NULL,
    idioma       VARCHAR(10)  NOT NULL DEFAULT 'es',
    confianza    REAL         CHECK (confianza IS NULL OR (confianza >= 0 AND confianza <= 1)),
    parcial      BOOLEAN      NOT NULL DEFAULT FALSE,
    recibida_en  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT transcripcion_respuesta_unica UNIQUE (usuario_id, sesion_id, pregunta_id)
);
CREATE INDEX IF NOT EXISTS idx_transcripcion_sesion ON transcripcion_respuesta(sesion_id);

CREATE TABLE IF NOT EXISTS diccionario_terminologia (
    area           VARCHAR(40)  PRIMARY KEY,
    nombre         VARCHAR(80)  NOT NULL,
    version        INTEGER      NOT NULL DEFAULT 1 CHECK (version >= 1),
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    terminos       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    correcciones   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Paquetes iniciales (tecnología). Se pueden editar desde el panel: ON CONFLICT no pisa lo que ya se editó.
INSERT INTO diccionario_terminologia (area, nombre, terminos, correcciones) VALUES
 ('base', 'Base común',
  '["API","REST","JSON","Git","GitHub","SQL","backend","frontend","framework","deploy","pull request","testing","CI/CD","Docker","scrum","sprint"]',
  '[{"de":"yeison","a":"JSON"},{"de":"guit","a":"Git"},{"de":"dóquer","a":"Docker"},{"de":"escrum","a":"Scrum"}]'),
 ('programador-front', 'Programador Front-end',
  '["JavaScript","TypeScript","React","Vue","Angular","HTML","CSS","Tailwind","Redux","Next.js","Vite","webpack","DOM","componentes","hooks","responsive","accesibilidad","Jest","Cypress"]',
  '[{"de":"java escript","a":"JavaScript"},{"de":"taipscript","a":"TypeScript"},{"de":"riact","a":"React"},{"de":"tailwin","a":"Tailwind"},{"de":"redax","a":"Redux"},{"de":"cipres","a":"Cypress"}]'),
 ('programador-back', 'Programador Back-end',
  '["Kotlin","Ktor","Spring","Node.js","PostgreSQL","Redis","microservicios","ORM","JWT","OAuth","endpoint","middleware","Kafka","RabbitMQ","GraphQL","migración","índice","transacción"]',
  '[{"de":"cotlin","a":"Kotlin"},{"de":"postgres ql","a":"PostgreSQL"},{"de":"micro servicios","a":"microservicios"},{"de":"graf ql","a":"GraphQL"}]')
ON CONFLICT (area) DO NOTHING;

COMMIT;
