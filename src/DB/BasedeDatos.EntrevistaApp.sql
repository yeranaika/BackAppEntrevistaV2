-- =============================================================================
-- BasedeDatos.EntrevistaApp.sql
-- Plataforma de Simulación de Entrevistas con IA
-- Versión: 3.0 – Esquema DDL Completo
--
-- ARQUITECTURA DE MÓDULOS:
--   B1.  Identidad y Autenticación
--   B2.  Consentimientos legales (GDPR-ready)
--   B3.  Perfil, Carrera y Preferencias
--   B4.  Cargos y Skills del Mercado         ← NUEVO
--   B5.  Onboarding y Niveles del Usuario    ← NUEVO
--   B6.  Banco de Preguntas y Respuestas     ← AMPLIADO
--   B7.  Generación IA de Preguntas         ← NUEVO
--   B8.  Tests de Nivelación y Práctica
--   B9.  Sesiones de Simulación en Vivo
--   B10. Reportes y Feedback por Skill       ← AMPLIADO
--   B11. Suscripciones, Licencias B2B y Billing
--   B12. Modelo IA de Visión por Computador  ← NUEVO
--
-- OPTIMIZACIONES:
--   · Índices de cobertura (INCLUDE) → Index Only Scan en hot-paths
--   · Índices parciales (WHERE ...) → hasta 80 % menos tamaño de índice
--   · Índices GIN sobre JSONB → búsquedas semánticas O(log n)
--   · TIMESTAMPTZ en todas las fechas
--   · batch insert obligatorio en metrica_video y etiqueta_frame
--
-- Seeds: BasedeDatos.EntrevistaApp.Seeds.sql (ejecutar después)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA  IF NOT EXISTS app;
SET search_path TO app, public;

-- =============================================================================
-- BLOQUE 1: IDENTIDAD Y AUTENTICACIÓN
-- =============================================================================

CREATE TABLE IF NOT EXISTS usuario (
    usuario_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    correo             VARCHAR(320) NOT NULL UNIQUE,
    contrasena_hash    VARCHAR(255) NOT NULL,
    nombre             VARCHAR(120),
    idioma             VARCHAR(10)  NOT NULL DEFAULT 'es',
    estado             VARCHAR(19)  NOT NULL DEFAULT 'activo',
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rol                VARCHAR(10)  NOT NULL DEFAULT 'user'
                       CHECK (rol IN ('user', 'admin')),
    telefono           VARCHAR(20),
    origen_registro    VARCHAR(20)  NOT NULL DEFAULT 'local'
                       CHECK (origen_registro IN ('local', 'google', 'otros')),
    fecha_ultimo_login TIMESTAMPTZ,
    fecha_nacimiento   DATE,
    genero             VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS idx_usuario_correo_activo
    ON usuario(correo) WHERE estado = 'activo';

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS refresh_token (
    refresh_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_refresh_usuario
    ON refresh_token(usuario_id);
CREATE INDEX IF NOT EXISTS idx_refresh_activo
    ON refresh_token(usuario_id, expires_at) WHERE revoked = FALSE;

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS oauth_account (
    oauth_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider       TEXT         NOT NULL CHECK (provider = 'google'),
    subject        TEXT         NOT NULL,
    email          VARCHAR(320),
    email_verified BOOLEAN,
    usuario_id     UUID         REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE TABLE IF NOT EXISTS password_reset (
    token      UUID         PRIMARY KEY,
    usuario_id UUID         NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    code       VARCHAR(12)  NOT NULL,
    issued_at  TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS recovery_code (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    codigo           VARCHAR(6)  NOT NULL,
    fecha_expiracion TIMESTAMPTZ NOT NULL,
    usado            BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- BLOQUE 2: CONSENTIMIENTOS LEGALES (GDPR-ready)
-- =============================================================================
-- Separamos el texto legal del registro de aceptación del usuario.
-- Cada versión del texto es inmutable. El usuario acepta una versión específica.

CREATE TABLE IF NOT EXISTS consentimiento_texto (
    version           VARCHAR(20)  PRIMARY KEY,
    titulo            TEXT         NOT NULL,
    cuerpo            TEXT         NOT NULL,
    -- JSON con secciones: ["uso_datos", "video_analisis", "ia_entrenamiento", "marketing"]
    alcances_posibles JSONB        NOT NULL DEFAULT '[]',
    fecha_publicacion TIMESTAMPTZ  NOT NULL DEFAULT now(),
    vigente           BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS consentimiento (
    consentimiento_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    version           VARCHAR(20) NOT NULL REFERENCES consentimiento_texto(version),
    -- Alcances que el usuario ACEPTÓ específicamente: ["uso_datos", "video_analisis"]
    alcances_aceptados JSONB      NOT NULL DEFAULT '[]',
    -- Si acepta "ia_entrenamiento", sus frames de video pueden usarse como dataset
    acepta_entrenamiento_ia BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_otorgado    TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_revocado    TIMESTAMPTZ,
    ip_origen         VARCHAR(45)  -- IPv4 o IPv6
);
CREATE INDEX IF NOT EXISTS idx_consentimiento_usuario
    ON consentimiento(usuario_id, version);

-- =============================================================================
-- BLOQUE 3: PERFIL, CARRERA Y PREFERENCIAS
-- =============================================================================

CREATE TABLE IF NOT EXISTS perfil_usuario (
    perfil_id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          UUID        NOT NULL UNIQUE REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    -- Se actualiza automáticamente al completar el test de nivelación
    nivel_experiencia   VARCHAR(20) CHECK (nivel_experiencia IN ('junior', 'semisenior', 'senior')),
    area                VARCHAR(50),
    flags_accesibilidad JSONB,
    nota_objetivos      TEXT,
    pais                VARCHAR(2),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recordatorio_preferencia (
    usuario_id    UUID        PRIMARY KEY REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    dias_semana   VARCHAR(50) NOT NULL,
    hora          VARCHAR(5)  NOT NULL,
    tipo_practica VARCHAR(32) NOT NULL,
    habilitado    BOOLEAN     NOT NULL DEFAULT TRUE
);

-- =============================================================================
-- BLOQUE 4: CARGOS Y SKILLS DEL MERCADO
-- =============================================================================
-- Un CARGO es un rol del mercado laboral (ej: "Backend Developer Sr", "Product Manager").
-- Cada cargo tiene un conjunto de SKILLS requeridas con distintos pesos.
-- Esta relación es la base para generar las preguntas de cada simulación.

CREATE TABLE IF NOT EXISTS skill (
    skill_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre        VARCHAR(100) NOT NULL UNIQUE,
    -- 'tecnica' | 'blanda'
    categoria     VARCHAR(10)  NOT NULL CHECK (categoria IN ('tecnica', 'blanda')),
    -- 'backend' | 'frontend' | 'data' | 'devops' | 'mobile' | 'hr' | 'liderazgo' | 'gestion'
    tipo_area     VARCHAR(50)  NOT NULL,
    descripcion   TEXT,
    -- Score 1-100 calculado por batch job semanal según oferta de trabajo
    demanda_score SMALLINT     NOT NULL DEFAULT 50 CHECK (demanda_score BETWEEN 1 AND 100),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_skill_lookup
    ON skill(categoria, tipo_area, activo)
    INCLUDE (skill_id, nombre, demanda_score);

-- Historial de tendencias (batch job semanal, fuera del hot-path)
CREATE TABLE IF NOT EXISTS skill_tendencia (
    tendencia_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id            UUID        NOT NULL REFERENCES skill(skill_id) ON DELETE CASCADE,
    frecuencia_ofertas  INTEGER     NOT NULL DEFAULT 0,
    nivel_requerido     VARCHAR(20) NOT NULL DEFAULT 'intermedio'
                        CHECK (nivel_requerido IN ('junior', 'semisenior', 'senior')),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_skill_tendencia
    ON skill_tendencia(skill_id, fecha_actualizacion DESC);

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS cargo (
    cargo_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      VARCHAR(150) NOT NULL UNIQUE,
    -- 'backend' | 'frontend' | 'data' | 'devops' | 'mobile' | 'management' | 'hr'
    area        VARCHAR(50)  NOT NULL,
    descripcion TEXT,
    -- Nivel que el cargo representa en el mercado laboral
    nivel_base  VARCHAR(20)  NOT NULL DEFAULT 'semisenior'
                CHECK (nivel_base IN ('junior', 'semisenior', 'senior')),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_cargo_area
    ON cargo(area, activo)
    INCLUDE (cargo_id, nombre, nivel_base);

-- Relación muchos-a-muchos: qué skills necesita cada cargo y con qué peso
CREATE TABLE IF NOT EXISTS cargo_skill (
    cargo_skill_id  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cargo_id        UUID        NOT NULL REFERENCES cargo(cargo_id)  ON DELETE CASCADE,
    skill_id        UUID        NOT NULL REFERENCES skill(skill_id)  ON DELETE CASCADE,
    -- Nivel mínimo requerido para esta skill en este cargo
    nivel_requerido VARCHAR(20) NOT NULL DEFAULT 'junior'
                    CHECK (nivel_requerido IN ('junior', 'semisenior', 'senior')),
    -- Peso 1-100: importancia relativa de esta skill para el cargo
    -- Usado para calcular el puntaje ponderado del reporte
    peso            SMALLINT    NOT NULL DEFAULT 50 CHECK (peso BETWEEN 1 AND 100),
    -- ¿Es obligatoria para el cargo? Las opcionales son bonus
    obligatoria     BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (cargo_id, skill_id)
);
CREATE INDEX IF NOT EXISTS idx_cargo_skill_cargo
    ON cargo_skill(cargo_id, obligatoria)
    INCLUDE (skill_id, nivel_requerido, peso);

-- =============================================================================
-- BLOQUE 5: ONBOARDING Y NIVELES DEL USUARIO POR SKILL
-- =============================================================================
-- El onboarding captura:
--   · El cargo al que aspira el usuario (cargo_objetivo)
--   · Su nivel de experiencia actual (autopercibido, luego verificado por test)
--   · El nivel al que quiere llegar (nivel_meta)
-- Con esto, el sistema sabe exactamente qué preguntas generar para ese usuario.

CREATE TABLE IF NOT EXISTS onboarding_usuario (
    onboarding_id  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    cargo_id       UUID        NOT NULL REFERENCES cargo(cargo_id),
    -- Nivel autopercibido al momento del onboarding
    nivel_actual   VARCHAR(20) NOT NULL
                   CHECK (nivel_actual IN ('junior', 'semisenior', 'senior')),
    -- Nivel al que el usuario quiere llegar
    nivel_meta     VARCHAR(20) NOT NULL
                   CHECK (nivel_meta IN ('junior', 'semisenior', 'senior')),
    -- ¿Ya completó el test de nivelación que verifica el nivel_actual?
    nivel_verificado BOOLEAN   NOT NULL DEFAULT FALSE,
    -- Notas libres del usuario sobre sus metas
    nota_metas     TEXT,
    activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Un usuario puede tener múltiples onboardings (cambio de cargo objetivo)
-- pero solo uno activo a la vez
CREATE UNIQUE INDEX IF NOT EXISTS idx_onboarding_usuario_activo
    ON onboarding_usuario(usuario_id) WHERE activo = TRUE;
CREATE INDEX IF NOT EXISTS idx_onboarding_cargo
    ON onboarding_usuario(cargo_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- Nivel por skill individual del usuario (resultado de tests y simulaciones)
-- Se actualiza cada vez que el usuario completa un test o simulación

CREATE TABLE IF NOT EXISTS nivel_skill_usuario (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    skill_id         UUID        NOT NULL REFERENCES skill(skill_id)    ON DELETE CASCADE,
    -- Nivel evaluado objetivamente por los tests
    nivel_evaluado   VARCHAR(20) CHECK (nivel_evaluado IN ('junior', 'semisenior', 'senior')),
    -- Puntaje numérico 0-100 de la última evaluación
    puntaje          NUMERIC(5,2) NOT NULL DEFAULT 0,
    -- Cuántas evaluaciones han contribuido a este puntaje (para ponderar)
    num_evaluaciones INTEGER     NOT NULL DEFAULT 0,
    fecha_evaluacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, skill_id)
);
CREATE INDEX IF NOT EXISTS idx_nivel_skill_usuario
    ON nivel_skill_usuario(usuario_id, skill_id)
    INCLUDE (nivel_evaluado, puntaje);

-- =============================================================================
-- BLOQUE 6: BANCO DE PREGUNTAS Y RESPUESTAS
-- =============================================================================
-- Diseño de baja latencia:
--   · idx_pregunta_lookup: índice parcial (solo 'aprobada') + cobertura → Index Only Scan
--   · Las preguntas de opción múltiple tienen sus opciones en opcion_pregunta
--   · Las preguntas abiertas tienen rubrica_evaluacion JSONB con criterios STAR
--   · Las preguntas de simulación de video tienen contexto_ia con guías de evaluación
--
-- Una pregunta puede vincularse a un cargo específico (cargo_id) o ser genérica (NULL).

CREATE TABLE IF NOT EXISTS pregunta (
    pregunta_id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id                UUID        REFERENCES skill(skill_id) ON DELETE SET NULL,
    -- Cargo específico al que aplica. NULL = genérica para cualquier cargo de esa skill
    cargo_id                UUID        REFERENCES cargo(cargo_id) ON DELETE SET NULL,
    -- 'opcion_multiple' | 'abierta_texto' | 'simulacion_video'
    tipo_pregunta           VARCHAR(20) NOT NULL
                            CHECK (tipo_pregunta IN ('opcion_multiple', 'abierta_texto', 'simulacion_video')),
    -- 'tecnica' | 'blanda'
    categoria_habilidad     VARCHAR(10) NOT NULL
                            CHECK (categoria_habilidad IN ('tecnica', 'blanda')),
    -- 'junior' | 'semisenior' | 'senior'
    nivel_dificultad        VARCHAR(20) NOT NULL
                            CHECK (nivel_dificultad IN ('junior', 'semisenior', 'senior')),
    enunciado               TEXT        NOT NULL,
    -- Para preguntas abiertas/video: respuesta ideal o respuesta de referencia completa
    -- Texto libre que el sistema usa para calibrar la evaluación IA
    respuesta_ideal         TEXT,
    -- Criterios STAR y rubrica de evaluación para la IA
    -- { "metodo": "STAR", "criterios": [...], "palabras_clave": [...], "tiempo_esperado_seg": 90 }
    rubrica_evaluacion      JSONB,
    -- Prompt auxiliar específico enviado al LLM para evaluar esta pregunta
    contexto_evaluacion_ia  JSONB,
    generada_por_ia         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 'pendiente' | 'aprobada' | 'rechazada'
    estado                  VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                            CHECK (estado IN ('pendiente', 'aprobada', 'rechazada')),
    -- Si fue rechazada, el motivo
    motivo_rechazo          TEXT,
    veces_usada             INTEGER     NOT NULL DEFAULT 0,
    fecha_creacion          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ÍNDICE ESTRELLA del hot-path (GET /practice/questions):
-- Cubre: WHERE skill_id=$1 AND nivel_dificultad=$2 AND estado='aprobada'
-- → Index Only Scan, cero acceso al heap
CREATE INDEX IF NOT EXISTS idx_pregunta_lookup
    ON pregunta(skill_id, nivel_dificultad)
    INCLUDE (pregunta_id, enunciado, tipo_pregunta, categoria_habilidad)
    WHERE estado = 'aprobada';

-- Para preguntas específicas de cargo (simulación personalizada)
CREATE INDEX IF NOT EXISTS idx_pregunta_cargo
    ON pregunta(cargo_id, nivel_dificultad)
    INCLUDE (pregunta_id, skill_id, enunciado, tipo_pregunta)
    WHERE estado = 'aprobada' AND cargo_id IS NOT NULL;

-- Panel admin: filtros dinámicos
CREATE INDEX IF NOT EXISTS idx_pregunta_admin
    ON pregunta(estado, generada_por_ia, tipo_pregunta, fecha_creacion DESC);

-- Búsqueda semántica en rúbricas desde el admin
CREATE INDEX IF NOT EXISTS idx_pregunta_rubrica_gin
    ON pregunta USING gin(rubrica_evaluacion jsonb_path_ops)
    WHERE rubrica_evaluacion IS NOT NULL;

-- ──────────────────────────────────────────────────────────────────────────────
-- Opciones para preguntas de opción múltiple

CREATE TABLE IF NOT EXISTS opcion_pregunta (
    opcion_id    UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    pregunta_id  UUID    NOT NULL REFERENCES pregunta(pregunta_id) ON DELETE CASCADE,
    texto_opcion TEXT    NOT NULL,
    es_correcta  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Explicación detallada de por qué es correcta o incorrecta
    explicacion  TEXT,
    -- Orden de presentación (para randomizar en la app)
    orden        SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_opcion_pregunta
    ON opcion_pregunta(pregunta_id, orden);

-- =============================================================================
-- BLOQUE 7: GENERACIÓN IA DE PREGUNTAS (TRAZABILIDAD COMPLETA)
-- =============================================================================
-- Cada vez que el sistema genera preguntas con un LLM se registra aquí.
-- Permite: auditoría, costos, revisión por admin, reentrenamiento futuro.

CREATE TABLE IF NOT EXISTS pregunta_generacion_ia (
    generacion_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Pregunta resultante (NULL si la respuesta del LLM fue inválida)
    pregunta_id      UUID        REFERENCES pregunta(pregunta_id) ON DELETE SET NULL,
    -- Qué cargo + skill + nivel motivó la generación
    cargo_id         UUID        REFERENCES cargo(cargo_id),
    skill_id         UUID        REFERENCES skill(skill_id),
    nivel_solicitado VARCHAR(20) CHECK (nivel_solicitado IN ('junior', 'semisenior', 'senior')),
    -- Modelo LLM usado: 'gpt-4o-mini', 'claude-3-haiku', 'gemini-1.5-flash', etc.
    modelo_llm       VARCHAR(60) NOT NULL,
    -- Prompt completo enviado al LLM (para auditoría y mejora de prompts)
    prompt_enviado   TEXT        NOT NULL,
    -- Respuesta raw del LLM antes de parsearse
    respuesta_raw    TEXT,
    -- ¿El JSON resultante fue válido y parseado correctamente?
    parse_exitoso    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Tokens y costo estimado (para control de gastos)
    tokens_input     INTEGER,
    tokens_output    INTEGER,
    costo_usd        NUMERIC(8,6),
    -- 'pendiente_revision' | 'aprobada' | 'rechazada' | 'error_parse'
    estado_revision  VARCHAR(25) NOT NULL DEFAULT 'pendiente_revision'
                     CHECK (estado_revision IN ('pendiente_revision', 'aprobada', 'rechazada', 'error_parse')),
    revisado_por     UUID        REFERENCES usuario(usuario_id) ON DELETE SET NULL,
    fecha_generacion TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gen_ia_estado
    ON pregunta_generacion_ia(estado_revision, fecha_generacion DESC)
    INCLUDE (cargo_id, skill_id, modelo_llm);
CREATE INDEX IF NOT EXISTS idx_gen_ia_pregunta
    ON pregunta_generacion_ia(pregunta_id) WHERE pregunta_id IS NOT NULL;

-- =============================================================================
-- BLOQUE 8: FLUJOS DE APRENDIZAJE (NIVELACIÓN → PRÁCTICA → SIMULACIÓN)
-- =============================================================================
--
-- El usuario pasa por 3 flujos secuenciales:
--
-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  FLUJO 1: TEST DE NIVELACIÓN                                            │
-- │  Objetivo: determinar en qué nivel está el usuario en las skills        │
-- │  requeridas por su cargo_meta y generar el gap analysis.               │
-- │                                                                         │
-- │  onboarding_usuario (cargo_id + nivel_actual + nivel_meta)             │
-- │    → cargo_skill (qué skills necesita el cargo y con qué nivel)        │
-- │    → test_nivelacion (preguntas mixtas por skills del cargo)           │
-- │    → intento_test (tipo='nivelacion')                                   │
-- │    → resultado_nivelacion (gap por skill: qué le falta)                │
-- │    → nivel_skill_usuario (actualizado por cada skill evaluada)         │
-- │    → onboarding_usuario.nivel_verificado = TRUE                        │
-- ├─────────────────────────────────────────────────────────────────────────┤
-- │  FLUJO 2: PRÁCTICA (texto o selección múltiple)                        │
-- │  Objetivo: el usuario practica skills específicas a su ritmo.          │
-- │  No hay video. Feedback inmediato por pregunta.                        │
-- │                                                                         │
-- │  sesion_practica (skill elegida + nivel + tipo)                        │
-- │    → preguntas tipo opcion_multiple o abierta_texto                    │
-- │    → respuesta_practica (respuesta + feedback_ia + puntaje)           │
-- │    → nivel_skill_usuario (actualizado al finalizar la sesión)         │
-- ├─────────────────────────────────────────────────────────────────────────┤
-- │  FLUJO 3: SIMULACIÓN DE ENTREVISTA EN VIVO (video + IA)               │
-- │  Objetivo: simular una entrevista real con cámara activa.             │
-- │  La IA analiza respuestas, expresiones y postura en tiempo real.       │
-- │                                                                         │
-- │  sesion_entrevista (cargo + nivel + webrtc_room_id)                   │
-- │    → sesion_pregunta_respuesta (tipo simulacion_video / abierta)       │
-- │       + transcripcion_audio (STT) + feedback_ia_tecnico/blando        │
-- │    → metrica_video (batch cada 500ms: postura, contacto_visual, ...)  │
-- │    → reporte_entrevista (generado async por coroutine worker)         │
-- │       + reporte_skill_detalle (puntaje ponderado por cargo_skill.peso) │
-- └─────────────────────────────────────────────────────────────────────────┘
--
-- =============================================================================

-- ─── FLUJO 1: TEST DE NIVELACIÓN ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS test_nivelacion (
    test_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo          VARCHAR(150) NOT NULL,
    -- Cargo al que apunta este test. El backend filtra por cargo del onboarding activo.
    cargo_id        UUID         REFERENCES cargo(cargo_id) ON DELETE SET NULL,
    area            VARCHAR(50)  NOT NULL,
    -- 'junior' | 'semisenior' | 'senior' | 'mixto'
    nivel_objetivo  VARCHAR(20)  NOT NULL DEFAULT 'mixto',
    descripcion     TEXT,
    -- Array de UUIDs pre-calculado: evita JOINs en runtime, se sirve directo desde Redis
    preguntas_ids   JSONB        NOT NULL DEFAULT '[]',
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_test_activo
    ON test_nivelacion(cargo_id, area, activo)
    INCLUDE (test_id, titulo, nivel_objetivo, preguntas_ids);

-- ──────────────────────────────────────────────────────────────────────────────

-- Registro de cada intento al test de nivelación
CREATE TABLE IF NOT EXISTS intento_test (
    intento_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id         UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    test_id            UUID        REFERENCES test_nivelacion(test_id)      ON DELETE SET NULL,
    onboarding_id      UUID        REFERENCES onboarding_usuario(onboarding_id) ON DELETE SET NULL,
    -- 'nivelacion' | 'practica_tecnica' | 'practica_blanda' | 'simulacion_cargo'
    tipo_test          VARCHAR(25) NOT NULL
                       CHECK (tipo_test IN ('nivelacion', 'practica_tecnica', 'practica_blanda', 'simulacion_cargo')),
    puntaje_obtenido   NUMERIC(5,2),
    nivel_asignado     VARCHAR(20) CHECK (nivel_asignado IN ('junior', 'semisenior', 'senior')),
    -- [{ "pregunta_id":"...", "opcion_id":"...", "respuesta_texto":"...", "correcta":true, "puntaje":8.5 }]
    respuestas_detalle JSONB       NOT NULL DEFAULT '[]',
    fecha_inicio       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin          TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_intento_usuario
    ON intento_test(usuario_id, tipo_test, fecha_inicio DESC)
    INCLUDE (intento_id, puntaje_obtenido, nivel_asignado);

-- ──────────────────────────────────────────────────────────────────────────────
-- Resultado del test de nivelación: brecha por skill (gap analysis)
-- Se genera automáticamente al finalizar intento_test tipo='nivelacion'.
-- Es la tabla que alimenta el plan de estudio y la selección de preguntas de práctica.

CREATE TABLE IF NOT EXISTS resultado_nivelacion (
    resultado_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    intento_id       UUID        NOT NULL UNIQUE REFERENCES intento_test(intento_id) ON DELETE CASCADE,
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    onboarding_id    UUID        NOT NULL REFERENCES onboarding_usuario(onboarding_id) ON DELETE CASCADE,
    -- Nivel global asignado tras el test (consolida todas las skills)
    nivel_global_asignado VARCHAR(20) NOT NULL
                     CHECK (nivel_global_asignado IN ('junior', 'semisenior', 'senior')),
    -- Puntaje global 0-100
    puntaje_global   NUMERIC(5,2) NOT NULL,
    -- Gap analysis por skill:
    -- [{ "skill_id":"...", "nombre":"Kotlin", "nivel_actual":"junior", "nivel_requerido":"semisenior",
    --    "puntaje_obtenido":62, "puntaje_minimo_requerido":75, "brecha":13, "prioridad":"alta" }]
    -- prioridad: calculada como peso_cargo_skill * brecha / 100
    skills_gap       JSONB        NOT NULL DEFAULT '[]',
    -- Skills donde ya cumple el nivel requerido del cargo
    skills_ok        JSONB        NOT NULL DEFAULT '[]',
    -- Resumen generado por IA: "Tienes buen dominio de SQL pero necesitas reforzar Arquitectura y Kotlin Coroutines."
    resumen_ia       TEXT,
    fecha_generacion TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_resultado_nivelacion_usuario
    ON resultado_nivelacion(usuario_id, fecha_generacion DESC)
    INCLUDE (nivel_global_asignado, puntaje_global, onboarding_id);

-- ─── FLUJO 2: PRÁCTICA (texto y opción múltiple, sin video) ──────────────────
-- Una sesión de práctica es una ronda de preguntas escritas sobre una skill.
-- El usuario puede hacer varias sesiones de práctica por skill.
-- Feedback inmediato tras cada respuesta (para opción múltiple).
-- Feedback por IA al finalizar la sesión (para preguntas abiertas).

CREATE TABLE IF NOT EXISTS sesion_practica (
    sesion_practica_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id         UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    onboarding_id      UUID        REFERENCES onboarding_usuario(onboarding_id) ON DELETE SET NULL,
    skill_id           UUID        NOT NULL REFERENCES skill(skill_id) ON DELETE CASCADE,
    cargo_id           UUID        REFERENCES cargo(cargo_id) ON DELETE SET NULL,
    -- 'opcion_multiple' | 'abierta_texto' (el usuario elige el modo de práctica)
    modo               VARCHAR(20) NOT NULL
                       CHECK (modo IN ('opcion_multiple', 'abierta_texto')),
    -- 'tecnica' | 'blanda'
    categoria          VARCHAR(10) NOT NULL
                       CHECK (categoria IN ('tecnica', 'blanda')),
    -- Nivel de las preguntas servidas en esta sesión
    nivel_preguntas    VARCHAR(20) NOT NULL
                       CHECK (nivel_preguntas IN ('junior', 'semisenior', 'senior')),
    -- 'en_progreso' | 'finalizada' | 'abandonada'
    estado             VARCHAR(15) NOT NULL DEFAULT 'en_progreso'
                       CHECK (estado IN ('en_progreso', 'finalizada', 'abandonada')),
    -- Puntaje global de la sesión (calculado al finalizar)
    puntaje_sesion     NUMERIC(5,2),
    -- Total de preguntas respondidas en la sesión
    total_preguntas    SMALLINT    NOT NULL DEFAULT 0,
    -- Preguntas respondidas correctamente
    correctas          SMALLINT    NOT NULL DEFAULT 0,
    fecha_inicio       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin          TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sesion_practica_usuario
    ON sesion_practica(usuario_id, skill_id, fecha_inicio DESC)
    INCLUDE (sesion_practica_id, modo, puntaje_sesion, nivel_preguntas);
-- Índice parcial: sesiones activas (para evitar duplicar sesiones abiertas)
CREATE INDEX IF NOT EXISTS idx_sesion_practica_activa
    ON sesion_practica(usuario_id, skill_id) WHERE estado = 'en_progreso';

-- ──────────────────────────────────────────────────────────────────────────────
-- Respuestas individuales dentro de una sesión de práctica

CREATE TABLE IF NOT EXISTS respuesta_practica (
    respuesta_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_practica_id UUID       NOT NULL REFERENCES sesion_practica(sesion_practica_id) ON DELETE CASCADE,
    pregunta_id       UUID        REFERENCES pregunta(pregunta_id) ON DELETE SET NULL,
    -- Snapshot del enunciado al momento de responder
    enunciado_snap    TEXT        NOT NULL,
    -- Para opción múltiple: ID de la opción elegida
    opcion_elegida_id UUID        REFERENCES opcion_pregunta(opcion_id) ON DELETE SET NULL,
    -- Para preguntas abiertas: texto escrito por el usuario
    respuesta_texto   TEXT,
    -- ¿La respuesta fue correcta? (calculado inmediatamente para opción múltiple)
    es_correcta       BOOLEAN,
    -- Puntaje de esta respuesta (0-10)
    puntaje           NUMERIC(4,2),
    -- Feedback inmediato para opción múltiple (tomado de opcion_pregunta.explicacion)
    -- o generado por IA para preguntas abiertas
    feedback_texto    TEXT,
    -- Para abierta_texto: feedback estructurado del LLM
    -- { "puntaje": 7.5, "fortalezas": [...], "mejoras": [...], "observacion": "..." }
    feedback_ia       JSONB,
    -- Tiempo que tardó el usuario en responder (milisegundos)
    tiempo_respuesta_ms INTEGER,
    orden             SMALLINT    NOT NULL,
    fecha_respuesta   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_respuesta_practica_sesion
    ON respuesta_practica(sesion_practica_id, orden);

-- =============================================================================
-- BLOQUE 9: SESIONES DE SIMULACIÓN EN VIVO (STREAMING / WEBRTC)
-- =============================================================================

CREATE TABLE IF NOT EXISTS sesion_entrevista (
    sesion_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    onboarding_id    UUID        REFERENCES onboarding_usuario(onboarding_id) ON DELETE SET NULL,
    -- Cargo simulado (puede diferir del cargo_objetivo del onboarding activo)
    cargo_id         UUID        REFERENCES cargo(cargo_id) ON DELETE SET NULL,
    cargo_objetivo   VARCHAR(120) NOT NULL,
    -- 'junior' | 'semisenior' | 'senior'
    nivel_dificultad VARCHAR(20) NOT NULL
                     CHECK (nivel_dificultad IN ('junior', 'semisenior', 'senior')),
    -- 'en_progreso' | 'finalizada' | 'cancelada'
    estado           VARCHAR(15) NOT NULL DEFAULT 'en_progreso'
                     CHECK (estado IN ('en_progreso', 'finalizada', 'cancelada')),
    webrtc_room_id   VARCHAR(100),
    -- Versión del modelo de visión IA activa durante la sesión (FK a bloque 12)
    modelo_vision_id UUID,       -- FK añadida en bloque 12 para evitar forward reference
    fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sesion_usuario
    ON sesion_entrevista(usuario_id, estado, fecha_inicio DESC)
    INCLUDE (sesion_id, cargo_objetivo, nivel_dificultad, cargo_id);
CREATE INDEX IF NOT EXISTS idx_sesion_activa
    ON sesion_entrevista(usuario_id) WHERE estado = 'en_progreso';

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sesion_pregunta_respuesta (
    respuesta_id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id            UUID      NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    pregunta_id          UUID      REFERENCES pregunta(pregunta_id) ON DELETE SET NULL,
    -- Snapshot inmutable del enunciado en el momento de la sesión
    enunciado_pregunta   TEXT      NOT NULL,
    -- Snapshot de la respuesta ideal en el momento de la sesión
    respuesta_ideal_snap TEXT,
    -- Texto transcripto por STT (Whisper / Google STT)
    transcripcion_audio  TEXT,
    -- URL del clip en object storage (S3, GCS)
    video_clip_url       VARCHAR(500),
    -- { "puntaje": 7.5, "keywords_detectados": [...], "observacion": "..." }
    feedback_ia_tecnico  JSONB,
    -- { "claridad": 8, "estructura_star": true, "confianza": 7, "observacion": "..." }
    feedback_ia_blando   JSONB,
    -- Puntaje combinado de esta respuesta (0-100)
    puntaje_respuesta    NUMERIC(5,2),
    orden                SMALLINT  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_spr_sesion
    ON sesion_pregunta_respuesta(sesion_id, orden);

-- ──────────────────────────────────────────────────────────────────────────────
-- Métricas de visión por computador frame a frame.
-- CRÍTICO: insertar en batch cada 500 ms, NUNCA fila a fila.
-- El worker de CV envía paquetes de ~30 filas por request.

CREATE TABLE IF NOT EXISTS metrica_video (
    metrica_id          UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id           UUID       NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    timestamp_ms        BIGINT     NOT NULL,
    -- Scores 0.00-100.00 generados por el modelo de visión (bloque 12)
    contacto_visual     NUMERIC(5,2),
    postura_score       NUMERIC(5,2),
    confianza_score     NUMERIC(5,2),
    -- { "parpadeo_frecuente": false, "toca_cara": true, "movimiento_cabeza": "leve" }
    gestos_detectados   JSONB,
    -- 'seguro' | 'nervioso' | 'distraido' | 'neutral' | 'confuso'
    expresion_dominante VARCHAR(20),
    -- Versión del modelo que generó esta métrica
    modelo_vision_id    UUID       -- FK añadida en bloque 12
);
CREATE INDEX IF NOT EXISTS idx_metrica_video
    ON metrica_video(sesion_id, timestamp_ms ASC);

-- =============================================================================
-- BLOQUE 10: REPORTES Y FEEDBACK POR SKILL
-- =============================================================================
-- reporte_entrevista: documento final de la sesión (se genera asincrónicamente)
-- reporte_skill_detalle: desglose del puntaje por cada skill evaluada
-- → Permite a la app mostrar "Tu SQL: 72/100 | Tu Comunicación: 85/100"

CREATE TABLE IF NOT EXISTS reporte_entrevista (
    reporte_id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id                 UUID        NOT NULL UNIQUE REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    puntaje_global            NUMERIC(5,2) NOT NULL DEFAULT 0,
    puntaje_tecnico           NUMERIC(5,2) NOT NULL DEFAULT 0,
    puntaje_blando            NUMERIC(5,2) NOT NULL DEFAULT 0,
    puntaje_lenguaje_corporal NUMERIC(5,2) NOT NULL DEFAULT 0,
    -- ["Buena estructura STAR", "Dominio claro de SQL", "Contacto visual consistente"]
    fortalezas                JSONB        NOT NULL DEFAULT '[]',
    -- ["Mejorar explicación de sistemas distribuidos", "Reducir muletillas"]
    areas_mejora              JSONB        NOT NULL DEFAULT '[]',
    -- [{ "skill_id":"...", "nombre":"Docker", "nivel_actual":"junior", "prioridad":"alta" }]
    recomendaciones_skills    JSONB        NOT NULL DEFAULT '[]',
    -- Resumen narrativo generado por el LLM (párrafo de 3-5 líneas)
    resumen_ia                TEXT,
    -- 'generando' | 'listo' | 'error'
    estado_generacion         VARCHAR(15)  NOT NULL DEFAULT 'generando'
                              CHECK (estado_generacion IN ('generando', 'listo', 'error')),
    error_detalle             TEXT,        -- Si estado_generacion='error', descripción del fallo
    fecha_generacion          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────────────────────────────────────
-- Desglose del reporte por skill (para el gráfico de radar en la app)

CREATE TABLE IF NOT EXISTS reporte_skill_detalle (
    detalle_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporte_id   UUID        NOT NULL REFERENCES reporte_entrevista(reporte_id) ON DELETE CASCADE,
    skill_id     UUID        REFERENCES skill(skill_id) ON DELETE SET NULL,
    nombre_skill VARCHAR(100) NOT NULL,  -- snapshot del nombre
    categoria    VARCHAR(10)  NOT NULL,
    puntaje      NUMERIC(5,2) NOT NULL,
    -- Nivel evaluado en esta sesión para esta skill
    nivel_evaluado VARCHAR(20),
    -- Observación específica de la IA para esta skill
    observacion  TEXT,
    -- Preguntas respondidas para esta skill en la sesión
    preguntas_respondidas INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_reporte_skill
    ON reporte_skill_detalle(reporte_id);

-- =============================================================================
-- BLOQUE 11: SUSCRIPCIONES, LICENCIAS B2B Y BILLING
-- =============================================================================

CREATE TABLE IF NOT EXISTS codigo_suscripcion (
    codigo_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo           VARCHAR(32) NOT NULL UNIQUE,
    label            VARCHAR(80),
    duracion_dias    INTEGER     NOT NULL,
    max_usos         INTEGER     NOT NULL DEFAULT 1,
    usos_realizados  INTEGER     NOT NULL DEFAULT 0,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_expiracion TIMESTAMPTZ,
    activo           BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS suscripcion (
    suscripcion_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    -- 'free' | 'premium_mensual' | 'premium_anual' | 'institucional'
    plan             VARCHAR(30) NOT NULL DEFAULT 'free',
    proveedor        VARCHAR(50),
    -- 'activa' | 'inactiva' | 'cancelada' | 'suspendida' | 'vencida'
    estado           VARCHAR(20) NOT NULL DEFAULT 'inactiva',
    fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_renovacion TIMESTAMPTZ,
    fecha_expiracion TIMESTAMPTZ,
    codigo_id        UUID        REFERENCES codigo_suscripcion(codigo_id)
);
-- Índice de cobertura: /billing/status resuelto sin tocar la tabla
CREATE INDEX IF NOT EXISTS idx_suscripcion_usuario
    ON suscripcion(usuario_id, estado)
    INCLUDE (plan, fecha_expiracion, fecha_inicio);

-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS institucion (
    institucion_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre         VARCHAR(150) NOT NULL,
    dominio_correo VARCHAR(100),
    contacto_email VARCHAR(320) NOT NULL,
    fecha_registro TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_institucion_dominio
    ON institucion(dominio_correo) WHERE dominio_correo IS NOT NULL;

CREATE TABLE IF NOT EXISTS licencia_institucional (
    licencia_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    institucion_id   UUID        NOT NULL REFERENCES institucion(institucion_id) ON DELETE CASCADE,
    codigo_acceso    VARCHAR(40) NOT NULL UNIQUE,
    max_usuarios     INTEGER     NOT NULL DEFAULT 50,
    usuarios_activos INTEGER     NOT NULL DEFAULT 0,
    fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_expiracion TIMESTAMPTZ NOT NULL,
    activa           BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_licencia_activa
    ON licencia_institucional(institucion_id, activa, fecha_expiracion);

CREATE TABLE IF NOT EXISTS usuario_licencia (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID        NOT NULL REFERENCES usuario(usuario_id)             ON DELETE CASCADE,
    licencia_id      UUID        NOT NULL REFERENCES licencia_institucional(licencia_id) ON DELETE CASCADE,
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, licencia_id)
);

-- =============================================================================
-- BLOQUE 12: MODELO IA DE VISIÓN POR COMPUTADOR (VIDEO STREAM)
-- =============================================================================
-- Arquitectura lista para:
--   1. Gestionar versiones del modelo de CV (MediaPipe, modelo custom, etc.)
--   2. Construir y versionar el dataset de entrenamiento con consentimiento
--   3. Registrar predicciones del modelo para evaluación offline
--   4. Permitir A/B testing entre versiones de modelo

CREATE TABLE IF NOT EXISTS modelo_vision_ia (
    modelo_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre           VARCHAR(100) NOT NULL,
    version          VARCHAR(30)  NOT NULL UNIQUE,  -- ej: 'v1.0.0', 'v2.3.1-beta'
    descripcion      TEXT,
    -- 'mediapipe' | 'custom_tensorflow' | 'custom_pytorch' | 'openai_vision' | 'otro'
    tipo_arquitectura VARCHAR(40) NOT NULL,
    -- URL del artefacto del modelo (S3, GCS, HuggingFace, etc.)
    artefacto_url    VARCHAR(500),
    -- { "precision": 0.92, "recall": 0.88, "f1": 0.90, "dataset_version": "v2.1" }
    metricas_rendimiento JSONB,
    -- Labels que detecta: ["expresion_dominante", "contacto_visual", "postura", "gestos"]
    labels_detectados JSONB       NOT NULL DEFAULT '[]',
    -- 'entrenando' | 'activo' | 'deprecado' | 'evaluacion'
    estado           VARCHAR(15)  NOT NULL DEFAULT 'evaluacion'
                     CHECK (estado IN ('entrenando', 'activo', 'deprecado', 'evaluacion')),
    -- Solo 1 modelo puede estar activo a la vez
    fecha_activacion TIMESTAMPTZ,
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_modelo_vision_activo
    ON modelo_vision_ia(estado) WHERE estado = 'activo';

-- Añadir FK diferida: sesion_entrevista → modelo_vision_ia
ALTER TABLE sesion_entrevista
    ADD CONSTRAINT fk_sesion_modelo_vision
    FOREIGN KEY (modelo_vision_id)
    REFERENCES modelo_vision_ia(modelo_id)
    ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;

-- Añadir FK diferida: metrica_video → modelo_vision_ia
ALTER TABLE metrica_video
    ADD CONSTRAINT fk_metrica_modelo_vision
    FOREIGN KEY (modelo_vision_id)
    REFERENCES modelo_vision_ia(modelo_id)
    ON DELETE SET NULL
    DEFERRABLE INITIALLY DEFERRED;

-- ──────────────────────────────────────────────────────────────────────────────
-- Dataset de entrenamiento del modelo de visión
-- Solo se incluyen frames de usuarios que dieron consentimiento (acepta_entrenamiento_ia=TRUE)

CREATE TABLE IF NOT EXISTS dataset_entrenamiento_video (
    dataset_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Versión semántica del dataset: 'v1.0', 'v2.1', etc.
    version       VARCHAR(20) NOT NULL,
    nombre        VARCHAR(100) NOT NULL,
    descripcion   TEXT,
    -- URL del dataset comprimido en object storage
    storage_url   VARCHAR(500),
    -- { "total_frames": 45000, "usuarios_contribuyentes": 180, "horas_video": 32.5 }
    estadisticas  JSONB       NOT NULL DEFAULT '{}',
    -- Labels disponibles en este dataset
    labels        JSONB       NOT NULL DEFAULT '[]',
    -- 'construyendo' | 'listo' | 'en_entrenamiento' | 'archivado'
    estado        VARCHAR(20) NOT NULL DEFAULT 'construyendo'
                  CHECK (estado IN ('construyendo', 'listo', 'en_entrenamiento', 'archivado')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────────────────────────────────────
-- Frames individuales etiquetados para entrenamiento
-- Solo de usuarios con consentimiento acepta_entrenamiento_ia=TRUE

CREATE TABLE IF NOT EXISTS frame_entrenamiento (
    frame_id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id     UUID        NOT NULL REFERENCES dataset_entrenamiento_video(dataset_id) ON DELETE CASCADE,
    -- Referencia a la sesión original (puede ser NULL si fue anonimizado)
    sesion_id      UUID        REFERENCES sesion_entrevista(sesion_id) ON DELETE SET NULL,
    -- URL del frame en object storage (imagen estática o segmento de video)
    frame_url      VARCHAR(500) NOT NULL,
    timestamp_ms   BIGINT,
    -- Etiquetas del frame para supervisión: { "expresion": "nervioso", "contacto_visual": 0.3 }
    etiquetas      JSONB       NOT NULL DEFAULT '{}',
    -- ¿Fue validado/corregido por un revisor humano?
    validado       BOOLEAN     NOT NULL DEFAULT FALSE,
    validado_por   UUID        REFERENCES usuario(usuario_id) ON DELETE SET NULL,
    fecha_captura  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Acceso por dataset para construir batches de entrenamiento
CREATE INDEX IF NOT EXISTS idx_frame_dataset
    ON frame_entrenamiento(dataset_id, validado);
-- Búsqueda por etiquetas (para balancear clases en el dataset)
CREATE INDEX IF NOT EXISTS idx_frame_etiquetas_gin
    ON frame_entrenamiento USING gin(etiquetas jsonb_path_ops);

-- ──────────────────────────────────────────────────────────────────────────────
-- Historial de entrenamiento: cada vez que se entrena el modelo

CREATE TABLE IF NOT EXISTS entrenamiento_modelo (
    entrenamiento_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    modelo_id        UUID        NOT NULL REFERENCES modelo_vision_ia(modelo_id) ON DELETE CASCADE,
    dataset_id       UUID        NOT NULL REFERENCES dataset_entrenamiento_video(dataset_id),
    -- Hiperparámetros usados: { "epochs": 50, "lr": 0.001, "batch_size": 32 }
    hiperparametros  JSONB       NOT NULL DEFAULT '{}',
    -- Métricas al finalizar: { "loss": 0.12, "val_loss": 0.15, "accuracy": 0.93 }
    metricas_resultado JSONB,
    -- 'en_progreso' | 'completado' | 'fallido'
    estado           VARCHAR(15) NOT NULL DEFAULT 'en_progreso'
                     CHECK (estado IN ('en_progreso', 'completado', 'fallido')),
    error_detalle    TEXT,
    duracion_segundos INTEGER,
    fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_entrenamiento_modelo
    ON entrenamiento_modelo(modelo_id, fecha_inicio DESC);

-- ──────────────────────────────────────────────────────────────────────────────
-- Predicciones del modelo en producción (para evaluación offline y drift detection)
-- Muestra aleatoria de predicciones para monitorear el rendimiento en producción

CREATE TABLE IF NOT EXISTS prediccion_vision (
    prediccion_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id        UUID        NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    modelo_id        UUID        NOT NULL REFERENCES modelo_vision_ia(modelo_id),
    timestamp_ms     BIGINT      NOT NULL,
    -- Input features enviadas al modelo
    input_features   JSONB       NOT NULL DEFAULT '{}',
    -- Output del modelo: { "expresion": "nervioso", "confianza": 0.87 }
    prediccion_raw   JSONB       NOT NULL DEFAULT '{}',
    -- Si un revisor corrigió la predicción (ground truth para reentrenamiento)
    ground_truth     JSONB,
    fue_revisada     BOOLEAN     NOT NULL DEFAULT FALSE
);
-- Solo insertar muestra aleatoria (ej: 10% de frames) para no inflar la tabla
CREATE INDEX IF NOT EXISTS idx_prediccion_sesion
    ON prediccion_vision(sesion_id, modelo_id);

-- =============================================================================
-- FIN DEL ESQUEMA DDL v3.0
--
-- ORDEN DE EJECUCIÓN:
--   1. psql -d <bd> -f BasedeDatos.EntrevistaApp.sql        (este archivo)
--   2. psql -d <bd> -f BasedeDatos.EntrevistaApp.Seeds.sql  (datos semilla)
--
-- DIAGRAMA DE DEPENDENCIAS CLAVE:
--   usuario
--     ├── onboarding_usuario → cargo → cargo_skill → skill
--     ├── nivel_skill_usuario → skill
--     ├── intento_test → test_nivelacion → cargo
--     └── sesion_entrevista → cargo → modelo_vision_ia
--           ├── sesion_pregunta_respuesta → pregunta → skill / cargo
--           ├── metrica_video → modelo_vision_ia
--           └── reporte_entrevista
--                 └── reporte_skill_detalle → skill
--
-- NOTAS REDIS (obligatorio para producción):
--   preguntas:{skill_id}:{nivel}     TTL 24h
--   skills:catalogo                  TTL 6h
--   cargos:lista                     TTL 6h
--   cargo:{cargo_id}:skills          TTL 12h
--   test:{test_id}:preguntas         TTL 12h
--   modelo:vision:activo             TTL 1h (refresh cada sesión)
-- =============================================================================
