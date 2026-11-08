-- =============================================================================
-- BasedeDatos.EntrevistaApp.Seeds.sql
-- Plataforma de Simulación de Entrevistas con IA
-- Versión: 3.0
--
-- EJECUTAR DESPUÉS de BasedeDatos.EntrevistaApp.sql
-- Re-ejecutable sin duplicar: los catálogos usan ON CONFLICT sobre su clave única
-- (version, nombre, (cargo_id, skill_id)); preguntas y opciones no tienen clave natural
-- en el esquema, así que una pregunta se inserta solo si NO EXISTE otra con el mismo
-- enunciado + skill + cargo + nivel, y sus opciones solo si aún no tiene ninguna.
-- Limitación: si el admin BORRÓ una pregunta de esta semilla o le cambió el enunciado, la
-- skill, el cargo o el nivel, volver a correrla crea otra vez la versión original
-- (aprobada). Para retirar una pregunta de la semilla, rechazarla en vez de borrarla.
--
-- CONTENIDO:
--   S1. Consentimiento legal vigente
--   S2. Skills del mercado (técnicas y blandas)
--   S3. Cargos (job positions)
--   S4. Relaciones Cargo ↔ Skill con pesos
--   S5. Banco de preguntas con respuesta ideal y rúbrica
--   S6. Opciones para preguntas de opción múltiple
--   S7. Modelo de visión IA inicial (placeholder)
-- =============================================================================

SET search_path TO app, public;

-- =============================================================================
-- S1: CONSENTIMIENTO LEGAL
-- =============================================================================

INSERT INTO consentimiento_texto (version, titulo, cuerpo, alcances_posibles, vigente)
VALUES (
    'v1.0',
    'Consentimiento de uso de datos y análisis con IA',
    'Al usar esta plataforma aceptas que tus datos de sesión, respuestas de texto '
    'y grabaciones de video sean procesados por modelos de IA para generar tu '
    'reporte de desempeño personalizado. Puedes revocar este consentimiento en '
    'cualquier momento desde Configuración > Privacidad.',
    '["uso_datos_sesion","analisis_respuestas_texto","analisis_video_expresiones","mejora_modelos_ia","comunicaciones_plataforma"]',
    TRUE
)
ON CONFLICT (version) DO NOTHING;

-- =============================================================================
-- S2: SKILLS DEL MERCADO
-- =============================================================================

INSERT INTO skill (nombre, categoria, tipo_area, demanda_score, descripcion) VALUES
    -- ── BACKEND ───────────────────────────────────────────────────────────
    ('Kotlin',             'tecnica', 'backend',  90, 'Lenguaje JVM moderno. Lenguaje principal del proyecto.'),
    ('Java',               'tecnica', 'backend',  85, 'Ecosistema empresarial JVM ampliamente usado.'),
    ('Python',             'tecnica', 'backend',  89, 'Backend, scripting, ML y pipelines de datos.'),
    ('Node.js',            'tecnica', 'backend',  83, 'JavaScript en servidor, ideal para APIs rápidas.'),
    ('Spring Boot',        'tecnica', 'backend',  82, 'Framework empresarial para Java/Kotlin.'),
    ('Ktor',               'tecnica', 'backend',  70, 'Framework HTTP asíncrono para Kotlin.'),
    ('REST APIs',          'tecnica', 'backend',  87, 'Diseño y consumo de APIs RESTful con JSON.'),
    ('GraphQL',            'tecnica', 'backend',  73, 'API query language. Alternativa flexible a REST.'),
    ('Arquitectura Microservicios', 'tecnica', 'backend', 80, 'Diseño de sistemas distribuidos en servicios independientes.'),
    -- ── DATA ──────────────────────────────────────────────────────────────
    ('SQL',                'tecnica', 'data',     90, 'Lenguaje de consulta relacional estándar.'),
    ('PostgreSQL',         'tecnica', 'data',     84, 'Motor relacional robusto. Motor principal del proyecto.'),
    ('Redis',              'tecnica', 'data',     81, 'Caché en memoria, pub/sub y colas.'),
    ('MongoDB',            'tecnica', 'data',     76, 'Base de datos documental NoSQL.'),
    ('Modelado de Datos',  'tecnica', 'data',     78, 'Diseño de esquemas relacionales y no relacionales.'),
    -- ── FRONTEND ──────────────────────────────────────────────────────────
    ('React',              'tecnica', 'frontend', 88, 'Librería UI basada en componentes funcionales.'),
    ('TypeScript',         'tecnica', 'frontend', 86, 'JavaScript con tipado estático.'),
    ('Next.js',            'tecnica', 'frontend', 80, 'Framework full-stack sobre React con SSR.'),
    ('CSS / Tailwind',     'tecnica', 'frontend', 77, 'Estilos web modernos y sistemas de diseño.'),
    -- ── DEVOPS ────────────────────────────────────────────────────────────
    ('Docker',             'tecnica', 'devops',   86, 'Contenedores de aplicaciones.'),
    ('AWS',                'tecnica', 'devops',   85, 'Nube principal del mercado (EC2, S3, RDS, Lambda).'),
    ('CI/CD',              'tecnica', 'devops',   82, 'Integración y despliegue continuo (GitHub Actions, Jenkins).'),
    ('Kubernetes',         'tecnica', 'devops',   78, 'Orquestación de contenedores a escala.'),
    -- ── MOBILE ────────────────────────────────────────────────────────────
    ('Android (Kotlin)',   'tecnica', 'mobile',   89, 'Desarrollo nativo Android con Jetpack Compose.'),
    ('Flutter',            'tecnica', 'mobile',   79, 'Framework cross-platform de Google.'),
    ('Jetpack Compose',    'tecnica', 'mobile',   85, 'UI toolkit declarativo para Android.'),
    -- ── IA / ML ───────────────────────────────────────────────────────────
    ('Python (ML/AI)',     'tecnica', 'data',     91, 'Python para machine learning y análisis de datos.'),
    ('Computer Vision',    'tecnica', 'data',     80, 'Visión por computador con OpenCV y MediaPipe.'),
    ('LLM / Prompt Engineering', 'tecnica', 'data', 85, 'Integración y prompt engineering con modelos de lenguaje.'),
    -- ── BLANDAS ───────────────────────────────────────────────────────────
    ('Comunicación',              'blanda', 'hr',       96, 'Expresión clara, asertiva y empática.'),
    ('Trabajo en equipo',         'blanda', 'hr',       94, 'Colaboración, sinergia y apoyo mutuo.'),
    ('Liderazgo',                 'blanda', 'liderazgo',89, 'Guiar, motivar y desarrollar equipos.'),
    ('Resolución de problemas',   'blanda', 'hr',       93, 'Pensamiento analítico y creativo bajo presión.'),
    ('Adaptabilidad',             'blanda', 'hr',       88, 'Flexibilidad ante cambios y nuevos contextos.'),
    ('Gestión del tiempo',        'blanda', 'gestion',  85, 'Organización, priorización y cumplimiento de plazos.'),
    ('Inteligencia emocional',    'blanda', 'hr',       87, 'Autoconciencia, empatía y manejo de conflictos.'),
    ('Pensamiento crítico',       'blanda', 'gestion',  86, 'Análisis objetivo y toma de decisiones fundamentadas.')
ON CONFLICT (nombre) DO NOTHING;

-- =============================================================================
-- S3: CARGOS (JOB POSITIONS)
-- =============================================================================

INSERT INTO cargo (nombre, area, descripcion, nivel_base) VALUES
    ('Backend Developer Junior',     'backend',    'Desarrollador backend en etapa inicial. Trabaja en tareas guiadas.',          'junior'),
    ('Backend Developer SemiSenior', 'backend',    'Backend con autonomía para diseñar y resolver módulos completos.',            'semisenior'),
    ('Backend Developer Senior',     'backend',    'Backend con liderazgo técnico y toma de decisiones arquitecturales.',         'senior'),
    ('Frontend Developer Junior',    'frontend',   'Desarrollador UI en etapa inicial. Implementa diseños guiados.',             'junior'),
    ('Frontend Developer SemiSenior','frontend',   'Frontend con autonomía para construir componentes y manejar estado.',        'semisenior'),
    ('Frontend Developer Senior',    'frontend',   'Frontend con arquitectura de aplicación y optimización de rendimiento.',     'senior'),
    ('Full Stack Developer Jr',      'backend',    'Desarrollador web con conocimientos de front y back en etapa inicial.',      'junior'),
    ('Full Stack Developer SemiSr',  'backend',    'Full Stack autónomo capaz de trabajar end-to-end.',                          'semisenior'),
    ('Mobile Developer (Android) Jr','mobile',     'Desarrollador Android en etapa inicial con Kotlin y Compose básico.',        'junior'),
    ('Mobile Developer (Android) Sr','mobile',     'Android con dominio de arquitectura, rendimiento y publicación en Play.',    'senior'),
    ('Data Engineer Junior',         'data',       'Ingeniero de datos en etapa inicial. ETL básico y SQL.',                    'junior'),
    ('Data Engineer Senior',         'data',       'Ingeniería de datos avanzada. Pipelines, orquestación y optimización.',      'senior'),
    ('ML Engineer',                  'data',       'Ingeniería de machine learning. Modelos, despliegue y monitoreo.',           'senior'),
    ('DevOps Engineer',              'devops',     'Infraestructura, CI/CD, contenedores y nube.',                              'semisenior'),
    ('Tech Lead',                    'backend',    'Liderazgo técnico de equipo. Arquitectura, mentoring y decisiones clave.',  'senior'),
    ('Product Manager',              'gestion',    'Gestión de producto. Roadmap, stakeholders y métricas de negocio.',         'semisenior')
ON CONFLICT (nombre) DO NOTHING;

-- =============================================================================
-- S4: RELACIONES CARGO ↔ SKILL (con peso y nivel requerido)
-- =============================================================================

-- Backend Developer Junior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'Kotlin'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'SQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'REST APIs'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 70, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'PostgreSQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'Comunicación'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 75, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Junior' AND s.nombre = 'Trabajo en equipo'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Backend Developer SemiSenior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'Kotlin'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 88, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'SQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'REST APIs'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'PostgreSQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 75, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'Docker'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'Comunicación'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'junior', 70, FALSE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer SemiSenior' AND s.nombre = 'Arquitectura Microservicios'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Backend Developer Senior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'Kotlin'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'Arquitectura Microservicios'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 88, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'PostgreSQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'Docker'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'Liderazgo'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 88, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Backend Developer Senior' AND s.nombre = 'Comunicación'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Android Developer
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 95, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Mobile Developer (Android) Sr' AND s.nombre = 'Android (Kotlin)'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Mobile Developer (Android) Sr' AND s.nombre = 'Jetpack Compose'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Mobile Developer (Android) Sr' AND s.nombre = 'REST APIs'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'Mobile Developer (Android) Sr' AND s.nombre = 'Resolución de problemas'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- ML Engineer
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 95, TRUE
FROM cargo c, skill s WHERE c.nombre = 'ML Engineer' AND s.nombre = 'Python (ML/AI)'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 85, TRUE
FROM cargo c, skill s WHERE c.nombre = 'ML Engineer' AND s.nombre = 'Computer Vision'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 80, TRUE
FROM cargo c, skill s WHERE c.nombre = 'ML Engineer' AND s.nombre = 'SQL'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'semisenior', 75, TRUE
FROM cargo c, skill s WHERE c.nombre = 'ML Engineer' AND s.nombre = 'Docker'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, 'senior', 90, TRUE
FROM cargo c, skill s WHERE c.nombre = 'ML Engineer' AND s.nombre = 'LLM / Prompt Engineering'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- =============================================================================
-- S5: BANCO DE PREGUNTAS CON RESPUESTA IDEAL Y RÚBRICA
-- =============================================================================
-- Cada pregunta incluye:
--   · respuesta_ideal: lo que un candidato EXCELENTE respondería
--   · rubrica_evaluacion: criterios JSON que el LLM usa para puntuar

-- ── KOTLIN / TÉCNICA ──────────────────────────────────────────────────────────

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, c.cargo_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    'val declara una referencia de solo lectura (inmutable): una vez asignada no puede reasignarse. '
    'var declara una referencia mutable que puede cambiar de valor. '
    'Ambos pueden referenciar objetos cuyo estado interno puede mutar; la diferencia está en la referencia, no en el objeto.',
    '{"criterios": ["menciona_inmutabilidad", "menciona_reasignacion", "distingue_referencia_vs_estado"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s, cargo c,
     (VALUES ('junior', '¿Cuál es la diferencia entre val y var en Kotlin?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Kotlin' AND c.nombre = 'Backend Developer Junior'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NOT DISTINCT FROM c.cargo_id
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, c.cargo_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    'launch inicia una coroutine de tipo fire-and-forget y retorna un Job; no produce un resultado. '
    'async inicia una coroutine que devuelve un Deferred<T>; se usa cuando se necesita el resultado mediante .await(). '
    'Ambos son constructores de coroutines del mismo scope y pueden correr en paralelo.',
    '{"criterios": ["menciona_Job_vs_Deferred", "menciona_await", "ejemplo_uso_paralelo"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s, cargo c,
     (VALUES ('semisenior', '¿Cuál es la diferencia entre launch y async en Kotlin Coroutines?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Kotlin' AND c.nombre = 'Backend Developer SemiSenior'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NOT DISTINCT FROM c.cargo_id
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, c.cargo_id, 'abierta_texto', 'tecnica', v.nivel,
    v.enunciado,
    'Propuesta ideal: usar Redis como caché L1 con TTL configurable. '
    'Al recibir el request, el service consulta Redis primero (Cache-Aside pattern). '
    'Si hay cache miss, consulta PostgreSQL con HikariCP pool, guarda en Redis y emite el resultado. '
    'Para reactivo: usar Flow para emitir los datos y StateFlow/SharedFlow para compartir el estado del caché entre coroutines. '
    'Considerar invalidación de caché al actualizar preguntas (CQRS). Mencionar TTL, serialización JSON, y manejo de errores.',
    '{"metodo": "arquitectura_libre", "criterios": ["menciona_cache_aside", "menciona_TTL", "menciona_HikariCP_o_pool", "menciona_invalidacion", "considera_concurrencia", "ejemplo_codigo_o_diagrama"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s, cargo c,
     (VALUES ('senior', 'Diseña un sistema de caché reactivo usando Kotlin Flow y Redis para un endpoint de preguntas con alta concurrencia. Explica tu arquitectura.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Kotlin' AND c.nombre = 'Backend Developer Senior'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NOT DISTINCT FROM c.cargo_id
        AND p.nivel_dificultad = v.nivel);

-- ── SQL / DATA ────────────────────────────────────────────────────────────────

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    'HAVING. A diferencia de WHERE que filtra filas individuales antes del agrupamiento, '
    'HAVING filtra grupos ya calculados y puede referenciar funciones de agregación como COUNT, SUM o AVG.',
    '{"criterios": ["respuesta_correcta_HAVING", "distingue_WHERE_HAVING"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s,
     (VALUES ('junior', '¿Qué cláusula SQL se usa para filtrar grupos de filas resultantes de un GROUP BY?')) AS v(nivel, enunciado)
WHERE s.nombre = 'SQL'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    'LEFT JOIN (también llamado LEFT OUTER JOIN). Retorna todas las filas de la tabla izquierda; '
    'cuando no hay coincidencia en la tabla derecha, las columnas de ésta se rellenan con NULL.',
    '{"criterios": ["respuesta_correcta_LEFT_JOIN", "menciona_NULL_sin_coincidencia"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s,
     (VALUES ('semisenior', '¿Qué tipo de JOIN retorna todas las filas de la tabla izquierda aunque no exista coincidencia en la derecha?')) AS v(nivel, enunciado)
WHERE s.nombre = 'SQL'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, 'abierta_texto', 'tecnica', v.nivel,
    v.enunciado,
    'B-Tree: estructura de árbol balanceado, ideal para comparaciones de igualdad y rango (=, <, >, BETWEEN, LIKE ''foo%''). '
    'Funciona con casi todos los tipos de datos. Es el tipo por defecto. '
    'GIN (Generalized Inverted Index): óptimo para búsquedas dentro de valores compuestos como JSONB, arrays, texto completo (tsvector) y hstore. '
    'Indexa cada elemento individual del valor compuesto, permitiendo buscar si un JSONB contiene una clave específica en O(log n). '
    'Elijo B-Tree para filtros por columnas escalares (fecha, UUID, estado). '
    'Elijo GIN cuando necesito buscar dentro de JSONB (ej: contexto_evaluacion_ia @> ''{"metodo":"STAR"}'') o arrays.',
    '{"criterios": ["describe_btree_correctamente", "describe_gin_correctamente", "da_ejemplo_uso_btree", "da_ejemplo_uso_gin", "menciona_JSONB_o_arrays_para_gin"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s,
     (VALUES ('senior', 'Explica la diferencia entre un índice B-Tree y un índice GIN en PostgreSQL. ¿Cuándo elegirías cada uno?')) AS v(nivel, enunciado)
WHERE s.nombre = 'PostgreSQL'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

-- ── REST APIs ─────────────────────────────────────────────────────────────────

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    '201 Created. El código 201 indica que la solicitud fue procesada exitosamente y que un nuevo recurso fue creado. '
    'El cuerpo de la respuesta típicamente incluye el recurso creado o su ubicación en el header Location.',
    '{"criterios": ["respuesta_correcta_201", "distingue_200_vs_201"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s,
     (VALUES ('junior', '¿Qué código HTTP se debe retornar cuando se crea un recurso correctamente en una API REST?')) AS v(nivel, enunciado)
WHERE s.nombre = 'REST APIs'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, 'abierta_texto', 'tecnica', v.nivel,
    v.enunciado,
    'Propuesta ideal: '
    'POST /sessions → inicia la sesión, retorna 201 con { sesion_id, webrtc_room_id, primera_pregunta }. '
    'GET /sessions/{id}/question → obtiene la siguiente pregunta de la sesión. '
    'POST /sessions/{id}/answers → envía la respuesta a la pregunta actual (audio transcrito o texto). '
    'POST /sessions/{id}/finish → finaliza la sesión, lanza generación asíncrona del reporte, retorna 202 Accepted. '
    'GET /sessions/{id}/report → retorna el reporte (200 si listo, 202 si aún procesando con estado "generando"). '
    'Consideraciones: JWT para autenticación, versionado /api/v1/, paginación si aplica.',
    '{"criterios": ["propone_POST_para_crear", "incluye_endpoint_pregunta", "incluye_endpoint_respuesta", "incluye_endpoint_finish", "incluye_endpoint_reporte", "considera_estado_asincrono", "menciona_autenticacion"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s,
     (VALUES ('semisenior', 'Diseña el esquema de endpoints REST para un módulo de simulación de entrevistas que soporte: inicio de sesión, respuesta a preguntas en vivo y consulta del reporte final.')) AS v(nivel, enunciado)
WHERE s.nombre = 'REST APIs'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

-- ── COMUNICACIÓN (BLANDA / VIDEO) ─────────────────────────────────────────────

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, contexto_evaluacion_ia, estado)
SELECT s.skill_id, 'simulacion_video', 'blanda', v.nivel,
    v.enunciado,
    'Respuesta ideal: narrativa estructurada de 60-90 segundos que incluye '
    '(1) presentación profesional breve, (2) experiencia o formación relevante, '
    '(3) motivación específica y genuina por la empresa/rol, (4) qué puede aportar. '
    'Tono seguro, contacto visual, sin muletillas excesivas.',
    '{"metodo":"narrativa_libre","criterios":["estructura_presentacion","menciona_experiencia","motivacion_especifica","valor_aportado","duracion_adecuada"],"duracion_esperada_seg":90,"puntaje_maximo":10}',
    '{"evaluar": ["claridad_expresion", "contacto_visual", "postura", "ausencia_muletillas", "estructura_narrativa"]}',
    'aprobada'
FROM skill s,
     (VALUES ('junior', 'Cuéntame sobre ti y por qué quieres trabajar en esta empresa.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Comunicación'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, contexto_evaluacion_ia, estado)
SELECT s.skill_id, 'simulacion_video', 'blanda', v.nivel,
    v.enunciado,
    'Respuesta STAR ideal: '
    'S: Contexto claro del proyecto y relación con el compañero. '
    'T: Conflicto específico (diferencia de criterio técnico, prioridades, comunicación). '
    'A: Acciones concretas tomadas: conversación directa, mediación, búsqueda de acuerdo, datos o argumentos usados. '
    'R: Resultado positivo: acuerdo alcanzado, relación mantenida, aprendizaje. '
    'No hablar mal del compañero. Mostrar empatía y enfoque en solución.',
    '{"metodo":"STAR","criterios":["situacion_clara","tarea_o_conflicto_especifico","accion_propia_descrita","resultado_positivo","empatia_demostrada","no_culpa_al_otro"],"duracion_esperada_seg":120,"puntaje_maximo":10}',
    '{"evaluar": ["estructura_STAR", "empatia_tono_voz", "contacto_visual", "gestos_nerviosos", "conclusion_positiva"]}',
    'aprobada'
FROM skill s,
     (VALUES ('semisenior', 'Describe una situación donde tuviste un conflicto con un compañero de equipo y cómo lo resolviste.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Trabajo en equipo'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, contexto_evaluacion_ia, estado)
SELECT s.skill_id, 'simulacion_video', 'blanda', v.nivel,
    v.enunciado,
    'Respuesta STAR ideal: '
    'S: Proyecto crítico con presión de tiempo o recursos. '
    'T: Decisión técnica que debía tomarse (arquitectura, tecnología, deuda técnica). '
    'A: Proceso de decisión: datos recopilados, pros/contras evaluados, comunicación con el equipo, asunción de responsabilidad. '
    'R: Resultado concreto (éxito, ajuste necesario) y aprendizaje genuino sobre liderazgo y comunicación. '
    'Demuestra ownership, transparencia y capacidad de aprender de errores.',
    '{"metodo":"STAR","criterios":["situacion_de_presion","decision_tecnica_especifica","proceso_decisorio_descrito","resultado_medible","aprendizaje_genuino","ownership_sin_excusas"],"duracion_esperada_seg":150,"puntaje_maximo":10}',
    '{"evaluar": ["estructura_STAR", "liderazgo_expresion_corporal", "confianza_vs_arrogancia", "contacto_visual", "manejo_pausa_reflexiva"]}',
    'aprobada'
FROM skill s,
     (VALUES ('senior', 'Cuéntame sobre un proyecto donde tomaste una decisión técnica difícil sin consenso del equipo. ¿Qué pasó y qué aprendiste?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Liderazgo'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

-- ── ANDROID / MOBILE ──────────────────────────────────────────────────────────

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, c.cargo_id, 'opcion_multiple', 'tecnica', v.nivel,
    v.enunciado,
    'El ciclo de vida de un Activity define los estados que atraviesa desde que se crea hasta que se destruye. '
    'Métodos principales: onCreate (inicialización), onStart (visible), onResume (interactuable), '
    'onPause (pierde foco), onStop (no visible), onDestroy (destruida). '
    'Además: onRestart (si vuelve desde stopped) y onSaveInstanceState (guardar estado antes de destruction).',
    '{"criterios": ["menciona_onCreate", "menciona_onResume_onPause", "menciona_onDestroy", "explica_proposito_general"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s, cargo c,
     (VALUES ('junior', '¿Qué es el ciclo de vida de un Activity en Android y cuáles son sus métodos principales?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Android (Kotlin)' AND c.nombre = 'Mobile Developer (Android) Jr'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NOT DISTINCT FROM c.cargo_id
        AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, c.cargo_id, 'abierta_texto', 'tecnica', v.nivel,
    v.enunciado,
    'Capas: Presentation (ViewModel + Compose UI), Domain (UseCases + interfaces), Data (Repositories + datasources). '
    'ViewModel expone StateFlow/SharedFlow. El UseCase de simulación coordina: '
    '(1) CameraUseCase (CameraX + WebRTC), (2) QuestionUseCase (API), (3) MetricaUseCase (envío de frames). '
    'Repository pattern oculta si los datos vienen de API, Room o caché. '
    'Hilt para inyección de dependencias. Manejo del ciclo de vida de la cámara en onStart/onStop. '
    'Procesamiento de frames en Dispatchers.Default para no bloquear el UI thread.',
    '{"criterios": ["describe_3_capas_correctamente", "menciona_ViewModel_StateFlow", "describe_CameraX_o_WebRTC", "menciona_DI_Hilt", "considera_ciclo_vida_camara", "considera_threads"], "puntaje_maximo": 10}',
    'aprobada'
FROM skill s, cargo c,
     (VALUES ('senior', 'Explica cómo implementarías la arquitectura MVVM con Clean Architecture en una app Android de simulación de entrevistas con video en vivo.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Android (Kotlin)' AND c.nombre = 'Mobile Developer (Android) Sr'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NOT DISTINCT FROM c.cargo_id
        AND p.nivel_dificultad = v.nivel);

-- ── RESOLUCIÓN DE PROBLEMAS ───────────────────────────────────────────────────

INSERT INTO pregunta (skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, contexto_evaluacion_ia, estado)
SELECT s.skill_id, 'simulacion_video', 'blanda', v.nivel,
    v.enunciado,
    'Respuesta STAR estructurada: '
    'S: Sistema crítico en producción con usuarios afectados. '
    'T: Diagnóstico y restauración en tiempo mínimo. '
    'A: 1) Revisar logs inmediatamente (app + infra). 2) Verificar métricas: CPU, memoria, DB, red. '
    '3) Identificar último despliegue o cambio. 4) Si hay rollback rápido disponible, ejecutarlo. '
    '5) Comunicar estado al equipo y stakeholders. 6) Resolver causa raíz y documentar postmortem. '
    'R: Sistema restaurado, equipo informado, lección documentada.',
    '{"metodo":"STAR","criterios":["menciona_logs_primero","menciona_metricas","considera_rollback","comunicacion_stakeholders","postmortem_mencionado","calma_bajo_presion"],"duracion_esperada_seg":120,"puntaje_maximo":10}',
    '{"evaluar": ["calma_expresion_corporal", "claridad_pasos", "postura_bajo_presion", "voz_segura"]}',
    'aprobada'
FROM skill s,
     (VALUES ('semisenior', 'El sistema de producción está caído un lunes a las 9 AM. Tienes 30 minutos para diagnosticarlo. ¿Qué haces?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Resolución de problemas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado
        AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL
        AND p.nivel_dificultad = v.nivel);

-- =============================================================================
-- S6: OPCIONES PARA PREGUNTAS DE OPCIÓN MÚLTIPLE
-- =============================================================================

-- val vs var
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('val es de solo lectura (inmutable) y var es mutable (reasignable)', TRUE,
     'Correcto. val crea una referencia de solo lectura; var permite reasignar la referencia.', 1),
    ('val es para tipos primitivos y var es para objetos', FALSE,
     'Incorrecto. Ambos pueden usarse con cualquier tipo: primitivos y objetos.', 2),
    ('val es público y var es privado por defecto', FALSE,
     'Incorrecto. val/var no determinan visibilidad. Eso lo hacen private, public, internal, protected.', 3),
    ('No hay diferencia, son sinónimos en Kotlin', FALSE,
     'Incorrecto. Son conceptos distintos con implicaciones de inmutabilidad.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Cuál es la diferencia entre val y var en Kotlin?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- launch vs async
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('launch retorna Job (sin resultado) y async retorna Deferred<T> (con resultado via .await())', TRUE,
     'Correcto. Use launch para efectos secundarios y async cuando necesite el resultado de la coroutine.', 1),
    ('launch es síncrono y async es asíncrono', FALSE,
     'Incorrecto. Ambos son asíncronos. La diferencia está en si retornan o no un resultado.', 2),
    ('async solo funciona en el Main dispatcher', FALSE,
     'Incorrecto. async funciona en cualquier dispatcher: Main, IO, Default o uno custom.', 3),
    ('launch retorna el resultado directamente sin .await()', FALSE,
     'Incorrecto. launch retorna Job, que no tiene valor de resultado. Para obtener resultados se usa async + .await().', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Cuál es la diferencia entre launch y async en Kotlin Coroutines?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- HAVING vs WHERE
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('HAVING', TRUE,
     'Correcto. HAVING filtra grupos resultantes del GROUP BY y puede usar funciones de agregación como COUNT() o SUM().', 1),
    ('WHERE', FALSE,
     'WHERE filtra filas individuales ANTES del GROUP BY. No puede referenciar funciones de agregación.', 2),
    ('FILTER', FALSE,
     'FILTER es una cláusula de funciones de ventana, no se usa para filtrar grupos de GROUP BY.', 3),
    ('GROUP HAVING', FALSE,
     'No existe la cláusula GROUP HAVING en SQL estándar.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Qué cláusula SQL se usa para filtrar grupos de filas resultantes de un GROUP BY?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- LEFT JOIN
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('LEFT JOIN (LEFT OUTER JOIN)', TRUE,
     'Correcto. Retorna todas las filas de la tabla izquierda; las columnas de la derecha serán NULL si no hay coincidencia.', 1),
    ('INNER JOIN', FALSE,
     'INNER JOIN solo retorna filas con coincidencia en AMBAS tablas.', 2),
    ('FULL OUTER JOIN', FALSE,
     'FULL OUTER JOIN retorna todas las filas de AMBAS tablas, no solo la izquierda.', 3),
    ('CROSS JOIN', FALSE,
     'CROSS JOIN produce el producto cartesiano de ambas tablas (todas las combinaciones posibles).', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Qué tipo de JOIN retorna todas las filas de la tabla izquierda aunque no exista coincidencia en la derecha?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- HTTP 201
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('201 Created', TRUE,
     'Correcto. 201 indica que la solicitud fue exitosa y se creó un nuevo recurso. Generalmente incluye Location header o el recurso en el body.', 1),
    ('200 OK', FALSE,
     '200 OK indica éxito genérico (GET, PUT). Para creaciones, 201 es el código semánticamente correcto.', 2),
    ('204 No Content', FALSE,
     '204 se usa cuando la operación fue exitosa pero no hay body que retornar (ej: DELETE exitoso).', 3),
    ('202 Accepted', FALSE,
     '202 indica que la solicitud fue recibida y se procesará de forma asíncrona (no necesariamente creó el recurso aún).', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Qué código HTTP se debe retornar cuando se crea un recurso correctamente en una API REST?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- Ciclo de vida Android
INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
CROSS JOIN (VALUES
    ('onCreate → onStart → onResume → onPause → onStop → onDestroy', TRUE,
     'Correcto. Este es el orden principal del ciclo de vida de un Activity en Android.', 1),
    ('onInit → onVisible → onInteract → onHide → onKill', FALSE,
     'Incorrecto. Estos no son métodos reales del ciclo de vida de Activity en Android.', 2),
    ('onBind → onUnbind → onRebind', FALSE,
     'Estos son métodos del ciclo de vida de un Service, no de un Activity.', 3),
    ('start → run → stop', FALSE,
     'Incorrecto. Estos son nombres de métodos de Thread, no del ciclo de vida de Activity.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE p.enunciado = '¿Qué es el ciclo de vida de un Activity en Android y cuáles son sus métodos principales?'
  AND p.tipo_pregunta = 'opcion_multiple'
  -- Solo si la pregunta aún no tiene opciones: no se duplican ni se completan las que editó el admin.
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

-- =============================================================================
-- S7: MODELO DE VISIÓN IA INICIAL (PLACEHOLDER)
-- =============================================================================
-- Registro inicial del modelo base (MediaPipe). El equipo de AI lo actualiza
-- cuando entrena versiones custom.

INSERT INTO modelo_vision_ia (nombre, version, descripcion, tipo_arquitectura,
    labels_detectados, metricas_rendimiento, estado)
VALUES (
    'MediaPipe Base',
    'v1.0.0-mediapipe',
    'Modelo base usando MediaPipe Face Mesh + Pose. No requiere entrenamiento propio. '
    'Detecta contacto visual, postura y expresiones faciales en tiempo real.',
    'mediapipe',
    '["contacto_visual","postura_score","expresion_dominante","gestos_detectados"]',
    '{"precision_expresion": 0.78, "latencia_ms": 45, "fps_soportados": 30}',
    'activo'
)
ON CONFLICT (version) DO NOTHING;

-- =============================================================================
-- FIN DE SEEDS v3.0
--
-- Para agregar más preguntas sin necesidad de SQL directo:
--   POST /api/v1/admin/questions           → creación manual
--   POST /api/v1/admin/questions/generate  → generación con LLM
-- =============================================================================
