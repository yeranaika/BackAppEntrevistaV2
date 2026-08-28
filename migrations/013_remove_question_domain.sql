-- Retira el dominio anterior en bases ya existentes.
-- Hacer backup antes de ejecutar: esta migracion elimina datos definitivamente.
BEGIN;
SET search_path TO app, public;

DROP TABLE IF EXISTS retroalimentacion CASCADE;
DROP TABLE IF EXISTS respuesta CASCADE;
DROP TABLE IF EXISTS sesion_pregunta CASCADE;
DROP TABLE IF EXISTS sesion_entrevista CASCADE;
DROP TABLE IF EXISTS respuesta_prueba CASCADE;
DROP TABLE IF EXISTS respuesta_guardar CASCADE;
DROP TABLE IF EXISTS pregunta_mostrada CASCADE;
DROP TABLE IF EXISTS intento_practica CASCADE;
DROP TABLE IF EXISTS intento_prueba CASCADE;
DROP TABLE IF EXISTS prueba_pregunta CASCADE;
DROP TABLE IF EXISTS prueba CASCADE;
DROP TABLE IF EXISTS pregunta_nivelacion CASCADE;
DROP TABLE IF EXISTS pregunta CASCADE;
DROP TABLE IF EXISTS plan_practica_paso CASCADE;
DROP TABLE IF EXISTS plan_practica CASCADE;
DROP TABLE IF EXISTS job_requisito CASCADE;
DROP TABLE IF EXISTS skills_cargo CASCADE;
DROP TABLE IF EXISTS ticket CASCADE;

COMMIT;
