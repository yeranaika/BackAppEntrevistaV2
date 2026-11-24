-- La fila de ejemplo del modelo de visión que dejó la semilla deja de figurar como 'activo' y de "detectar expresiones
-- faciales" (D37). No es un modelo entregado ni la usa el backend, pero contradice RNF10 y D22 (sin emociones) y una
-- auditoría de privacidad podría leerla como prueba de que se infieren emociones. El modelo real se registrará cuando el
-- compañero lo entregue.
--   * Solo toca la fila de ejemplo ('v1.0.0-mediapipe'); si no existe, no hace nada.
--   * No borra nada: la fila queda 'deprecado', sin la etiqueta expresion_dominante y sin las métricas inventadas.
-- Se puede aplicar antes o después de desplegar (el código no lee esta tabla).
-- Idempotente: se puede ejecutar más de una vez y sobre una BD creada desde cero.
BEGIN;
SET search_path TO app, public;

UPDATE modelo_vision_ia
   SET nombre               = 'MediaPipe Base (ejemplo, sin uso)',
       descripcion          = 'Fila de ejemplo heredada: no es un modelo entregado ni está en uso. No detecta expresiones ni emociones (RNF10).',
       labels_detectados    = '["contacto_visual","postura_score","gestos_detectados"]'::jsonb,
       metricas_rendimiento = NULL,
       estado               = 'deprecado'
 WHERE version = 'v1.0.0-mediapipe';

COMMIT;
