-- Métricas de video sin emociones y lenguaje corporal "sin medir" (D22).
--   * puntaje_lenguaje_corporal admite NULL y pierde el default 0: NULL significa que no se midió (la persona no usó
--     la cámara o ninguna ventana tuvo evidencia suficiente). Antes se guardaba 0 y el reporte mostraba 0/100.
--   * Los reportes ya generados sin ninguna métrica de video con datos pasan de 0 a NULL.
--   * Se borran las expresiones (emociones) ya guardadas: el backend dejó de aceptarlas (RNF10).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

ALTER TABLE reporte_entrevista ALTER COLUMN puntaje_lenguaje_corporal DROP NOT NULL;
ALTER TABLE reporte_entrevista ALTER COLUMN puntaje_lenguaje_corporal DROP DEFAULT;

UPDATE reporte_entrevista r
   SET puntaje_lenguaje_corporal = NULL
 WHERE r.puntaje_lenguaje_corporal = 0
   AND NOT EXISTS (
       SELECT 1 FROM metrica_video m
        WHERE m.sesion_id = r.sesion_id
          AND (m.contacto_visual IS NOT NULL OR m.postura_score IS NOT NULL OR m.confianza_score IS NOT NULL)
   );

UPDATE metrica_video SET expresion_dominante = NULL WHERE expresion_dominante IS NOT NULL;

COMMIT;
