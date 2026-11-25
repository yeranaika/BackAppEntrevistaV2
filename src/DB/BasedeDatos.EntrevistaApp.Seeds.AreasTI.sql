-- =============================================================================
-- BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql
-- Plataforma de Simulación de Entrevistas con IA · Áreas TI nuevas (D44)
-- =============================================================================
-- EJECUTAR DESPUÉS de BasedeDatos.EntrevistaApp.sql y BasedeDatos.EntrevistaApp.Seeds.sql
-- (usa skills de esa semilla: SQL, Python, Java, AWS, Docker, Kubernetes, CI/CD, blandas…).
--
-- CONTENIDO: 13 skills técnicas, 12 cargos, 69 relaciones cargo↔skill y
-- 234 preguntas aprobadas (117 de opción múltiple con 4 opciones y 117 abiertas), 18 por skill nueva:
-- 6 por nivel (junior, semisenior, senior), 3 de opción múltiple y 3 abiertas. Todas sin cargo (cargo_id NULL):
-- sirven a cualquier cargo que tenga la skill. Áreas: QA/Testing, Ciberseguridad, Diseño UX/UI, Soporte TI y Redes,
-- Análisis de datos/BI y Cloud.
--
-- Las escribió Claude (no el generador con IA del backend: sin costo ni traza en pregunta_generacion_ia) siguiendo
-- las reglas de generar-ia: rúbrica TECNICA con criterios, palabras clave, 2 respuestas alternativas, sinónimos y
-- feedback genérico correcto/medio/malo (D33, D36), y respuesta ideal de las abiertas con resumen y pasos
-- "1) 2) 3)" (D40). Revisarlas en el panel como cualquier otra: se pueden rechazar.
--
-- Re-ejecutable sin duplicar (como D25): skills y cargos con ON CONFLICT (nombre), relaciones con ON CONFLICT
-- (cargo_id, skill_id), una pregunta solo si no existe otra con el mismo enunciado + skill + sin cargo + nivel, y
-- sus opciones solo si todavía no tiene ninguna. Para retirar una pregunta, rechazarla en el panel (borrarla hace
-- que esta semilla la vuelva a crear). Todo en una transacción: si falta una skill de la semilla base, no se carga nada.
-- =============================================================================

BEGIN;

SET search_path TO app, public;

-- Las skills de la semilla base que usan los cargos nuevos tienen que existir (con el nombre exacto).
DO $$
DECLARE faltantes TEXT;
BEGIN
    SELECT string_agg(n, ', ') INTO faltantes
    FROM unnest(ARRAY['AWS', 'Arquitectura Microservicios', 'CI/CD', 'CSS / Tailwind', 'Comunicación', 'Docker', 'Gestión del tiempo', 'Java', 'Kubernetes', 'Liderazgo', 'Modelado de Datos', 'Pensamiento crítico', 'Python', 'Resolución de problemas', 'SQL', 'Trabajo en equipo']) AS n
    WHERE NOT EXISTS (SELECT 1 FROM skill s WHERE s.nombre = n);
    IF faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan skills de la semilla base: %. Corre antes BasedeDatos.EntrevistaApp.Seeds.sql', faltantes;
    END IF;
END $$;

-- =============================================================================
-- A1: SKILLS NUEVAS
-- =============================================================================

INSERT INTO skill (nombre, categoria, tipo_area, demanda_score, descripcion) VALUES
    ('Testing y QA', 'tecnica', 'qa', 78, 'Diseño de casos de prueba, tipos de prueba, gestión de defectos y calidad de software.'),
    ('Automatización de pruebas', 'tecnica', 'qa', 80, 'Pruebas automatizadas con Selenium, Cypress, Playwright, JUnit o pytest, integradas en CI.'),
    ('Seguridad de aplicaciones (OWASP)', 'tecnica', 'seguridad', 82, 'Vulnerabilidades web, OWASP Top 10, autenticación segura y desarrollo seguro.'),
    ('Seguridad de redes y SOC', 'tecnica', 'seguridad', 79, 'Firewalls, monitoreo con SIEM, respuesta a incidentes y hardening.'),
    ('Diseño UX', 'tecnica', 'diseno', 77, 'Investigación de usuarios, arquitectura de información, prototipos y pruebas de usabilidad.'),
    ('Diseño UI y Figma', 'tecnica', 'diseno', 76, 'Interfaces visuales, sistemas de diseño, accesibilidad y prototipado en Figma.'),
    ('Soporte técnico y Help Desk', 'tecnica', 'soporte', 75, 'Atención de incidencias, diagnóstico de hardware y software, Windows e ITIL.'),
    ('Redes TCP/IP', 'tecnica', 'soporte', 78, 'Direccionamiento IP, subredes, DNS, DHCP, VLAN y diagnóstico de conectividad.'),
    ('Linux', 'tecnica', 'soporte', 82, 'Administración de servidores Linux: terminal, permisos, servicios y scripting en Bash.'),
    ('Power BI y visualización de datos', 'tecnica', 'data', 80, 'Modelos de datos, DAX, dashboards e indicadores para el negocio.'),
    ('Excel para análisis de datos', 'tecnica', 'data', 79, 'Tablas dinámicas, BUSCARX, Power Query y análisis de datos en planillas.'),
    ('Azure', 'tecnica', 'cloud', 80, 'Nube de Microsoft: máquinas virtuales, App Service, Azure SQL, identidad y redes.'),
    ('Terraform e Infraestructura como Código', 'tecnica', 'cloud', 79, 'Infraestructura declarativa con Terraform: estado, módulos y buenas prácticas.')
ON CONFLICT (nombre) DO NOTHING;

-- =============================================================================
-- A2: CARGOS NUEVOS
-- =============================================================================

INSERT INTO cargo (nombre, area, descripcion, nivel_base) VALUES
    ('Analista QA Junior', 'qa', 'Diseña y ejecuta casos de prueba, reporta defectos y valida criterios de aceptación.', 'junior'),
    ('QA Automation Engineer', 'qa', 'Construye y mantiene suites de pruebas automatizadas integradas en CI/CD.', 'semisenior'),
    ('Analista de Ciberseguridad Junior', 'seguridad', 'Monitorea alertas, analiza eventos de seguridad y apoya la respuesta a incidentes.', 'junior'),
    ('Ingeniero de Ciberseguridad', 'seguridad', 'Protege aplicaciones e infraestructura: revisa vulnerabilidades, define controles y lidera incidentes.', 'semisenior'),
    ('Diseñador UX/UI Junior', 'diseno', 'Diseña pantallas y prototipos, y participa en pruebas con usuarios.', 'junior'),
    ('Diseñador UX/UI SemiSenior', 'diseno', 'Lidera la investigación y el diseño de flujos completos, y mantiene el sistema de diseño.', 'semisenior'),
    ('Técnico de Soporte TI', 'soporte', 'Atiende incidencias de usuarios, equipos y cuentas en mesa de ayuda.', 'junior'),
    ('Administrador de Redes y Sistemas', 'soporte', 'Administra servidores, redes y servicios de infraestructura de la organización.', 'semisenior'),
    ('Analista de Datos Junior', 'data', 'Limpia, analiza y presenta datos para apoyar decisiones del negocio.', 'junior'),
    ('Analista BI SemiSenior', 'data', 'Modela datos y construye dashboards e indicadores para las áreas del negocio.', 'semisenior'),
    ('Cloud Engineer', 'cloud', 'Despliega y opera infraestructura en la nube con contenedores e infraestructura como código.', 'semisenior'),
    ('Arquitecto Cloud', 'cloud', 'Diseña arquitecturas en la nube seguras, escalables y con costos controlados.', 'senior')
ON CONFLICT (nombre) DO NOTHING;

-- =============================================================================
-- A3: RELACIONES CARGO ↔ SKILL (nivel requerido, peso 1-100, obligatoria)
-- =============================================================================

-- Analista QA Junior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Testing y QA', 'junior', 90, TRUE),
    ('Automatización de pruebas', 'junior', 50, FALSE),
    ('SQL', 'junior', 50, TRUE),
    ('Comunicación', 'junior', 70, TRUE),
    ('Pensamiento crítico', 'junior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Analista QA Junior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- QA Automation Engineer
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Automatización de pruebas', 'semisenior', 95, TRUE),
    ('Testing y QA', 'semisenior', 80, TRUE),
    ('CI/CD', 'junior', 60, TRUE),
    ('Python', 'junior', 55, FALSE),
    ('Java', 'junior', 55, FALSE),
    ('Trabajo en equipo', 'semisenior', 60, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'QA Automation Engineer'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Analista de Ciberseguridad Junior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Seguridad de redes y SOC', 'junior', 90, TRUE),
    ('Seguridad de aplicaciones (OWASP)', 'junior', 70, TRUE),
    ('Redes TCP/IP', 'junior', 70, TRUE),
    ('Linux', 'junior', 60, TRUE),
    ('Resolución de problemas', 'junior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Analista de Ciberseguridad Junior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Ingeniero de Ciberseguridad
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Seguridad de aplicaciones (OWASP)', 'semisenior', 95, TRUE),
    ('Seguridad de redes y SOC', 'semisenior', 85, TRUE),
    ('Linux', 'semisenior', 65, TRUE),
    ('AWS', 'junior', 50, FALSE),
    ('Pensamiento crítico', 'semisenior', 70, TRUE),
    ('Comunicación', 'semisenior', 60, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Ingeniero de Ciberseguridad'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Diseñador UX/UI Junior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Diseño UX', 'junior', 90, TRUE),
    ('Diseño UI y Figma', 'junior', 90, TRUE),
    ('CSS / Tailwind', 'junior', 40, FALSE),
    ('Comunicación', 'junior', 75, TRUE),
    ('Trabajo en equipo', 'junior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Diseñador UX/UI Junior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Diseñador UX/UI SemiSenior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Diseño UX', 'semisenior', 95, TRUE),
    ('Diseño UI y Figma', 'semisenior', 85, TRUE),
    ('CSS / Tailwind', 'junior', 45, FALSE),
    ('Comunicación', 'semisenior', 80, TRUE),
    ('Pensamiento crítico', 'semisenior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Diseñador UX/UI SemiSenior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Técnico de Soporte TI
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Soporte técnico y Help Desk', 'junior', 95, TRUE),
    ('Redes TCP/IP', 'junior', 70, TRUE),
    ('Linux', 'junior', 50, FALSE),
    ('Comunicación', 'junior', 85, TRUE),
    ('Resolución de problemas', 'junior', 80, TRUE),
    ('Gestión del tiempo', 'junior', 60, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Técnico de Soporte TI'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Administrador de Redes y Sistemas
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Redes TCP/IP', 'semisenior', 95, TRUE),
    ('Linux', 'semisenior', 85, TRUE),
    ('Soporte técnico y Help Desk', 'semisenior', 60, TRUE),
    ('Seguridad de redes y SOC', 'junior', 60, FALSE),
    ('Docker', 'junior', 45, FALSE),
    ('Resolución de problemas', 'semisenior', 75, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Administrador de Redes y Sistemas'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Analista de Datos Junior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('SQL', 'junior', 90, TRUE),
    ('Excel para análisis de datos', 'junior', 85, TRUE),
    ('Power BI y visualización de datos', 'junior', 75, TRUE),
    ('Python', 'junior', 50, FALSE),
    ('Comunicación', 'junior', 70, TRUE),
    ('Pensamiento crítico', 'junior', 75, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Analista de Datos Junior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Analista BI SemiSenior
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('Power BI y visualización de datos', 'semisenior', 95, TRUE),
    ('SQL', 'semisenior', 90, TRUE),
    ('Modelado de Datos', 'semisenior', 75, TRUE),
    ('Excel para análisis de datos', 'semisenior', 60, FALSE),
    ('Comunicación', 'semisenior', 75, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Analista BI SemiSenior'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Cloud Engineer
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('AWS', 'semisenior', 85, TRUE),
    ('Azure', 'junior', 60, FALSE),
    ('Terraform e Infraestructura como Código', 'semisenior', 85, TRUE),
    ('Docker', 'semisenior', 80, TRUE),
    ('Kubernetes', 'junior', 65, TRUE),
    ('Linux', 'semisenior', 70, TRUE),
    ('CI/CD', 'semisenior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Cloud Engineer'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- Arquitecto Cloud
INSERT INTO cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria)
SELECT c.cargo_id, s.skill_id, v.nivel, v.peso, v.obligatoria
FROM cargo c
CROSS JOIN (VALUES
    ('AWS', 'senior', 90, TRUE),
    ('Azure', 'semisenior', 75, TRUE),
    ('Terraform e Infraestructura como Código', 'senior', 85, TRUE),
    ('Kubernetes', 'semisenior', 75, TRUE),
    ('Arquitectura Microservicios', 'senior', 80, TRUE),
    ('Liderazgo', 'senior', 70, TRUE),
    ('Comunicación', 'senior', 70, TRUE)
) AS v(skill, nivel, peso, obligatoria)
JOIN skill s ON s.nombre = v.skill
WHERE c.nombre = 'Arquitecto Cloud'
ON CONFLICT (cargo_id, skill_id) DO NOTHING;

-- =============================================================================
-- A4: BANCO DE PREGUNTAS (sin cargo) Y SUS OPCIONES
-- =============================================================================

-- ── QA / TESTING ────────────────────────────────────────────────────────────

-- Automatización de pruebas

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La espera implícita es una configuración global del driver que hace que cada búsqueda de elemento reintente durante un tiempo máximo antes de fallar. La espera explícita, con WebDriverWait y una condición como elementToBeClickable, espera algo concreto en un punto específico del código y por eso es más precisa.',
    '{"metodo": "TECNICA", "criterios": ["Explica que la implícita es global para la búsqueda de elementos", "Explica que la explícita espera una condición específica", "Menciona WebDriverWait o las condiciones esperadas", "Distingue ambas esperas de una pausa fija como Thread.sleep"], "palabras_clave": ["espera implícita", "espera explícita", "WebDriverWait", "condición", "elemento"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Con la implícita le digo al driver que, siempre que busque un elemento, espere hasta cierto tiempo a que aparezca. Con la explícita espero en un lugar puntual a que se cumpla una condición, por ejemplo que un botón sea clickeable.", "La explícita es selectiva: uso WebDriverWait para esperar que un elemento sea visible o que cambie un texto. La implícita aplica a todas las búsquedas con findElement y solo cubre la presencia del elemento, no otras condiciones."], "sinonimos": [["espera explícita", "explicit wait"], ["espera implícita", "implicit wait"]], "feedback_generico": {"correcto": "¡Muy bien! Distinguiste con precisión el alcance y el propósito de cada tipo de espera en Selenium.", "medio": "Vas bien, pero precisa mejor a qué se aplica cada espera y qué tipo de condición puede verificar.", "malo": "Repasa los mecanismos de espera de Selenium WebDriver y por qué conviene evitar pausas fijas en las pruebas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En Selenium WebDriver, ¿cuál es la principal diferencia entre una espera implícita y una espera explícita?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('La implícita pausa un tiempo fijo y la explícita espera hasta que cargue toda la página.', FALSE,
     'Ninguna de las dos es una pausa fija; la pausa fija corresponde a Thread.sleep.', 1),
    ('La implícita solo funciona con XPath y la explícita solo con selectores CSS.', FALSE,
     'Las esperas no dependen del tipo de localizador; ambas funcionan con cualquiera.', 2),
    ('La implícita aplica a toda búsqueda de elementos y la explícita espera una condición puntual.', TRUE,
     'Correcto: la implícita es global para findElement y la explícita espera una condición concreta con WebDriverWait.', 3),
    ('La explícita se configura una vez para todo el driver y la implícita se escribe por elemento.', FALSE,
     'Invierte los conceptos: la implícita es la global y la explícita se usa en un punto específico.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En Selenium WebDriver, ¿cuál es la principal diferencia entre una espera implícita y una espera explícita?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un atributo dedicado como data-testid es el localizador más robusto, porque no depende del estilo, del texto ni de la estructura del DOM, y sobrevive a los rediseños. Un id único y estable cumple la misma función; en cambio, los XPath absolutos y las clases de estilo se rompen ante cualquier cambio visual.',
    '{"metodo": "TECNICA", "criterios": ["Elige un atributo estable como data-testid o un id único", "Explica la fragilidad del XPath absoluto", "Explica por qué las clases de estilo cambian con el diseño", "Propone acordar los atributos de prueba con desarrollo"], "palabras_clave": ["localizador", "data-testid", "id único", "XPath", "estructura del DOM"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Lo más estable es pedir a desarrollo un atributo exclusivo para pruebas, como data-testid, o usar un id que no cambie. Así el test no se rompe cuando cambian colores, textos o la estructura de la página.", "Los localizadores frágiles dependen de la estructura o del estilo, como un XPath que recorre todo el DOM. Un atributo pensado para automatización es independiente del diseño y deja claro qué elemento se quiere ubicar."], "sinonimos": [["localizador", "selector", "locator"], ["XPath absoluto", "ruta absoluta"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste un localizador robusto y entiendes por qué otros se rompen con facilidad.", "medio": "Vas bien, pero revisa cuáles de estos localizadores dependen del diseño o de la estructura de la página.", "malo": "Repasa los tipos de localizadores en Selenium y qué características hacen que uno sea estable o frágil."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Estás automatizando el botón ''Confirmar compra'' de una página web. ¿Qué localizador suele ser el más estable ante cambios de diseño?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un atributo dedicado a pruebas, como data-testid, acordado con el equipo de desarrollo.', TRUE,
     'Correcto: no depende del estilo ni de la estructura, así que resiste los cambios de diseño.', 1),
    ('Un XPath absoluto copiado desde las herramientas del navegador, como /html/body/div[2]/div/button.', FALSE,
     'Depende de toda la estructura del DOM y se rompe si se agrega o mueve cualquier contenedor.', 2),
    ('La clase CSS de estilo del botón, ya que es la que define cómo se ve en la pantalla.', FALSE,
     'Las clases de estilo cambian con los rediseños y suelen repetirse en muchos elementos.', 3),
    ('La posición del botón en la pantalla, calculada en coordenadas de píxeles.', FALSE,
     'Cambia con la resolución, el tamaño de la ventana y cualquier ajuste del diseño.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Estás automatizando el botón ''Confirmar compra'' de una página web. ¿Qué localizador suele ser el más estable ante cambios de diseño?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las pruebas parametrizadas, o data-driven, separan la lógica de la prueba de los datos y ejecutan el mismo caso una vez por cada conjunto de datos, reportando cada resultado por separado. En pytest se usa @pytest.mark.parametrize y en JUnit 5, @ParameterizedTest.',
    '{"metodo": "TECNICA", "criterios": ["Propone una prueba parametrizada o data-driven", "Explica la separación entre lógica y datos", "Menciona que cada dato genera un resultado independiente", "Evita la duplicación de código de prueba"], "palabras_clave": ["parametrizadas", "data-driven", "conjunto de datos", "parametrize", "ParameterizedTest"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Usaría una prueba data-driven: escribo el caso una vez y le paso una lista de correos con su resultado esperado. El framework ejecuta una prueba por cada dato y muestra cuáles fallaron.", "Lo ideal es parametrizar la prueba. Así no duplico código, agrego nuevos casos solo sumando datos a la lista y, si una entrada falla, las demás se siguen ejecutando y reportando."], "sinonimos": [["prueba parametrizada", "data-driven testing", "prueba guiada por datos"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste una técnica que evita duplicar código y entrega un resultado claro por cada dato.", "medio": "Vas bien, pero piensa en cómo afecta tu opción al mantenimiento y a la claridad de los resultados cuando un dato falla.", "malo": "Repasa cómo ejecutar un mismo caso con muchas entradas en frameworks como pytest o JUnit sin duplicar código."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Necesitas probar la misma función de validación de correos con 20 entradas distintas, válidas e inválidas. ¿Qué técnica de automatización conviene usar?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Copiar la misma prueba 20 veces, cambiando solo el dato de entrada en cada copia.', FALSE,
     'Duplica código: cualquier cambio en la lógica obliga a editar las 20 copias.', 1),
    ('Una sola prueba con un ciclo que recorra los datos y se detenga en el primero que falle.', FALSE,
     'Oculta los demás fallos y entrega un único resultado para todas las entradas.', 2),
    ('Probar solo el primer correo válido, porque los demás siguen el mismo camino del código.', FALSE,
     'Deja sin verificar los casos inválidos y las variantes que la función debe manejar.', 3),
    ('Una prueba parametrizada que ejecute el mismo caso con cada conjunto de datos.', TRUE,
     'Correcto: reutiliza la lógica y reporta un resultado independiente por cada entrada.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Necesitas probar la misma función de validación de correos con 20 entradas distintas, válidas e inválidas. ¿Qué técnica de automatización conviene usar?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Page Object Model es un patrón que representa cada página o componente como una clase que encapsula sus localizadores y acciones. 1) Cada página tiene su clase, por ejemplo LoginPage con métodos como ingresarCredenciales o enviar. 2) Los localizadores quedan en un solo lugar, así que si cambia un elemento se corrige una sola vez. 3) Las pruebas usan esos métodos y se leen como pasos de negocio, sin detalles del DOM. 4) Se reutiliza código entre muchas pruebas y se reduce la duplicación. 5) Se recomienda dejar las aserciones en las pruebas y no dentro de las páginas, para separar responsabilidades.',
    '{"metodo": "TECNICA", "criterios": ["Define el patrón como clases por página o componente", "Explica que encapsula localizadores y acciones", "Menciona menor mantenimiento ante cambios de la interfaz", "Destaca la reutilización y la legibilidad de las pruebas", "Separa las aserciones de los page objects"], "palabras_clave": ["Page Object Model", "localizadores", "encapsula", "reutiliza", "duplicación", "aserciones"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Es una forma de organizar la automatización: por cada pantalla creo una clase con sus elementos y las acciones que se pueden hacer en ella. Las pruebas llaman a esas acciones en vez de buscar elementos directamente, así que si la interfaz cambia, ajusto solo la clase de esa página.", "El patrón separa el cómo se interactúa con la página del qué se está probando. Gracias a eso el código es más limpio, se reutilizan métodos en muchas pruebas y el mantenimiento baja, porque cada localizador vive en un único lugar."], "sinonimos": [["Page Object Model", "POM", "page objects"], ["localizadores", "selectores", "locators"]], "feedback_generico": {"correcto": "¡Excelente! Explicaste el patrón con claridad y mostraste por qué facilita el mantenimiento de la suite.", "medio": "Vas bien con la definición, pero te faltó explicar qué problemas concretos resuelve el patrón en una suite real.", "malo": "Repasa el patrón Page Object Model: cómo se estructura y qué problemas de mantenimiento y duplicación ayuda a resolver."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es el patrón Page Object Model y qué ventajas tiene al automatizar pruebas de interfaz web?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Validaría tanto el caso exitoso como los errores, con aserciones automáticas en Postman. 1) Envío un cuerpo JSON válido y verifico el código de estado 201 Created y los datos del usuario creado. 2) Reviso que la respuesta cumpla el esquema esperado y que el tiempo de respuesta sea razonable. 3) Pruebo casos negativos, como campos obligatorios faltantes, correo con formato inválido y usuario duplicado, esperando 400 o 409 según la especificación. 4) Verifico la autenticación enviando la petición sin token o con uno inválido, esperando 401. 5) Uso variables de entorno para la URL y los datos, y agrupo todo en una colección que pueda correr con Newman en CI.',
    '{"metodo": "TECNICA", "criterios": ["Valida el código de estado y el cuerpo de la respuesta", "Incluye casos negativos y validaciones de entrada", "Prueba la autenticación o autorización", "Usa aserciones automatizadas y variables de entorno", "Organiza las pruebas en colecciones ejecutables"], "palabras_clave": ["código de estado", "casos negativos", "aserciones", "variables de entorno", "colección", "Newman"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero confirmaría el camino feliz: crear un usuario válido y revisar que el código sea 201 y que el cuerpo traiga los datos correctos. Después probaría datos faltantes, mal formados o repetidos, y peticiones sin autorización. Todo lo dejaría como scripts de prueba en una colección con variables por ambiente.", "En Postman escribiría pruebas que validen el status, el contenido y la estructura de la respuesta. Sumaría escenarios de error para comprobar que la API rechaza entradas inválidas con el código adecuado, y ejecutaría la colección en el pipeline con Newman."], "sinonimos": [["código de estado", "status code", "status"], ["aserciones", "asserts", "validaciones"], ["ambiente", "entorno"]], "feedback_generico": {"correcto": "¡Muy bien! Cubriste el caso exitoso, los errores y la organización de las pruebas para poder repetirlas.", "medio": "Vas bien, pero tu plan se concentra en el caso exitoso; piensa en qué entradas o condiciones deberían hacer fallar la petición.", "malo": "Repasa cómo probar una API REST: códigos de estado, validación de respuestas, casos negativos y uso de colecciones en Postman."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Te piden probar con Postman un endpoint POST /usuarios que crea un usuario nuevo. ¿Qué validarías y cómo organizarías las pruebas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'La estructura AAA ordena cada prueba en tres bloques para que sea clara y verifique un solo comportamiento. 1) Arrange: preparo el escenario, por ejemplo un carrito con dos productos de 10.000 pesos y un descuento del 10%. 2) Act: ejecuto una sola acción, que es llamar a la función que calcula el total. 3) Assert: verifico el resultado esperado, que el total sea 18.000 pesos. 4) Mantengo una sola razón de fallo por prueba y un nombre descriptivo, como total_aplica_descuento_del_10_por_ciento. 5) Si necesito otro escenario, como un carrito vacío, escribo otra prueba independiente.',
    '{"metodo": "TECNICA", "criterios": ["Explica la fase Arrange de preparación", "Explica la fase Act con una sola acción", "Explica la fase Assert con el resultado esperado", "Aplica la estructura al ejemplo del carrito", "Menciona pruebas independientes y nombres descriptivos"], "palabras_clave": ["Arrange", "Act", "Assert", "resultado esperado", "prueba independiente"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero se prepara todo lo necesario, como crear el carrito con productos y un descuento. Luego se ejecuta la acción que quiero probar, calcular el total, y al final se compara el valor obtenido con el esperado. Separar esas tres partes hace que la prueba sea fácil de leer y de mantener.", "AAA significa preparar, actuar y afirmar. En el ejemplo, armo un carrito de 20.000 pesos con 10% de descuento, invoco el cálculo y compruebo con una aserción que devuelva 18.000. Cada escenario distinto va en su propia prueba."], "sinonimos": [["Arrange", "preparar"], ["Act", "actuar", "ejecutar"], ["Assert", "afirmar", "verificar"], ["prueba unitaria", "unit test"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste cada fase con claridad y la aplicaste correctamente al ejemplo del carrito.", "medio": "Vas bien, pero alguna de las fases quedó poco clara o no la relacionaste con el ejemplo pedido.", "malo": "Repasa el patrón Arrange, Act, Assert y practica escribiendo pruebas unitarias pequeñas que verifiquen un solo comportamiento."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica la estructura Arrange, Act, Assert de una prueba unitaria automatizada, usando como ejemplo una función que calcula el total de un carrito con descuento.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un mock es el doble de prueba pensado para verificar comportamiento: registra las interacciones y permite afirmar qué métodos se llamaron, cuántas veces y con qué argumentos, como con verify en Mockito. Un stub, en cambio, solo entrega respuestas predefinidas para controlar el estado de la prueba.',
    '{"metodo": "TECNICA", "criterios": ["Elige un mock para verificar interacciones", "Distingue mock de stub", "Menciona la verificación de cantidad de llamadas y argumentos", "Reconoce el propósito de dummy y fake"], "palabras_clave": ["mock", "stub", "interacciones", "verify", "doble de prueba"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Lo correcto es un mock, porque la prueba no revisa un valor devuelto sino que ocurrió una llamada concreta. Con herramientas como Mockito puedo verificar que enviarCorreo se invocó una vez con el correo esperado.", "Los stubs sirven para alimentar a la prueba con datos fijos, mientras que los mocks sirven para comprobar cómo se usó la dependencia. Como aquí interesa la cantidad de llamadas y el destinatario, se necesita un mock."], "sinonimos": [["doble de prueba", "test double"], ["mock", "objeto simulado"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste el doble de prueba adecuado y distinguiste bien entre verificar comportamiento y controlar estado.", "medio": "Vas bien, pero revisa qué tipo de doble de prueba está pensado para verificar interacciones y no solo para devolver datos.", "malo": "Repasa los distintos tipos de dobles de prueba y en qué situación conviene usar cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En una prueba unitaria de un servicio de registro, quieres comprobar que se llamó a enviarCorreo() exactamente una vez y con el destinatario correcto. ¿Qué doble de prueba es el más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un stub, porque devuelve respuestas predefinidas para que la prueba pueda avanzar.', FALSE,
     'Un stub controla lo que recibe el código probado, pero no está pensado para verificar llamadas.', 1),
    ('Un mock, porque permite verificar las llamadas recibidas, su cantidad y sus argumentos.', TRUE,
     'Correcto: el mock verifica interacciones, que es justo lo que pide el escenario.', 2),
    ('Un dummy, porque solo se pasa como parámetro para completar la firma del método probado.', FALSE,
     'Un dummy no se usa durante la prueba, así que no puede registrar ni verificar llamadas.', 3),
    ('Un fake, porque es una implementación simplificada que funciona en memoria.', FALSE,
     'Un fake reemplaza una dependencia con lógica real simplificada, pero su objetivo no es verificar interacciones.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En una prueba unitaria de un servicio de registro, quieres comprobar que se llamó a enviarCorreo() exactamente una vez y con el destinatario correcto. ¿Qué doble de prueba es el más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La cobertura de líneas solo mide qué sentencias se ejecutaron, y aquí se ejecutaron todas. La cobertura de ramas exige que cada decisión tome sus dos caminos; como la condición nunca fue falsa, solo se cubrió una de las dos ramas, es decir, el 50%.',
    '{"metodo": "TECNICA", "criterios": ["Distingue cobertura de líneas y cobertura de ramas", "Calcula correctamente ambas coberturas", "Explica que la rama falsa del if no se ejecutó", "Reconoce que el 100% de líneas no garantiza probar todos los caminos"], "palabras_clave": ["cobertura de líneas", "cobertura de ramas", "condición", "dos caminos", "decisión"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Todas las líneas se ejecutaron, así que la cobertura de sentencias es total. Pero un if tiene dos salidas, verdadera y falsa, y solo se recorrió una, por lo que la cobertura de ramas queda en la mitad.", "Es un ejemplo de por qué la cobertura de líneas puede engañar: marca 100% aunque nunca se probó qué pasa cuando la condición no se cumple. La cobertura de ramas lo detecta y muestra 50%."], "sinonimos": [["cobertura de líneas", "cobertura de sentencias", "line coverage"], ["cobertura de ramas", "branch coverage", "cobertura de decisiones"]], "feedback_generico": {"correcto": "¡Muy bien! Distinguiste con precisión los dos tipos de cobertura y explicaste por qué dan valores distintos.", "medio": "Vas bien, pero revisa cómo se cuentan los caminos de una decisión al calcular la cobertura de ramas.", "malo": "Repasa los tipos de cobertura de código, en especial la diferencia entre cobertura de líneas y cobertura de ramas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Una función tiene un único if sin else, y su única prueba usa un valor que hace verdadera la condición, ejecutando todas las líneas. ¿Qué cobertura obtiene?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('100% de líneas y 100% de ramas, porque se ejecutó todo el código de la función.', FALSE,
     'Ejecutar todas las líneas no implica recorrer ambos resultados de la condición.', 1),
    ('50% de líneas y 100% de ramas, porque solo se probó un valor de entrada.', FALSE,
     'Todas las líneas se ejecutaron, así que la cobertura de líneas es 100%, y la de ramas no puede ser total.', 2),
    ('100% de líneas y 50% de ramas, porque nunca se evaluó la condición como falsa.', TRUE,
     'Correcto: todas las sentencias corrieron, pero solo se cubrió la rama verdadera del if.', 3),
    ('50% de líneas y 50% de ramas, porque falta una segunda prueba para el caso falso.', FALSE,
     'La falta del caso falso afecta solo a las ramas; las líneas sí se ejecutaron todas.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Una función tiene un único if sin else, y su única prueba usa un valor que hace verdadera la condición, ejecutando todas las líneas. ¿Qué cobertura obtiene?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Con scope=''module'', pytest crea el fixture la primera vez que una prueba de ese archivo lo solicita y ejecuta su limpieza después de la última prueba del mismo archivo. El scope por defecto es function, y existen además class, package y session para compartir recursos costosos en distintos niveles.',
    '{"metodo": "TECNICA", "criterios": ["Identifica que el scope module equivale a un archivo de pruebas", "Indica que el scope por defecto es function", "Distingue los scopes class y session", "Relaciona el scope con el costo de crear recursos compartidos"], "palabras_clave": ["fixture", "scope", "module", "pytest", "session"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["El fixture se comparte entre todas las pruebas de un mismo archivo: se abre la conexión una vez y se cierra cuando termina la última prueba del módulo. Es útil para recursos costosos que no conviene recrear en cada prueba.", "Module significa que el ciclo de vida del fixture es el archivo .py de pruebas. Si fuera function se crearía en cada prueba y si fuera session, una sola vez para toda la ejecución."], "sinonimos": [["archivo de pruebas", "módulo"], ["limpieza", "teardown"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces el ciclo de vida de los fixtures en pytest y cómo el scope controla cuándo se comparten.", "medio": "Vas bien, pero revisa a qué unidad de pytest corresponde cada scope y cuál se aplica por defecto.", "malo": "Repasa cómo funcionan los fixtures en pytest, en particular el parámetro scope y sus distintos niveles."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En pytest declaras un fixture que abre una conexión a una base de datos de pruebas con scope=''module''. ¿Cuándo se crea y se destruye ese fixture?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Se crea una vez por archivo de pruebas y se destruye al terminar las pruebas de ese archivo.', TRUE,
     'Correcto: en pytest un módulo es un archivo de pruebas y el fixture se comparte entre sus pruebas.', 1),
    ('Se crea antes de cada función de prueba y se destruye al terminar cada una de ellas.', FALSE,
     'Ese es el comportamiento del scope function, que es el valor por defecto.', 2),
    ('Se crea una sola vez para toda la ejecución y se destruye al finalizar la sesión completa de pytest.', FALSE,
     'Ese comportamiento corresponde al scope session, no a module.', 3),
    ('Se crea una vez por cada clase de pruebas y se destruye al terminar esa clase.', FALSE,
     'Ese comportamiento corresponde al scope class.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En pytest declaras un fixture que abre una conexión a una base de datos de pruebas con scope=''module''. ¿Cuándo se crea y se destruye ese fixture?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'La trato como una prueba inestable y busco la causa en lugar de agregarle reintentos. 1) Reviso el historial de ejecuciones, logs, capturas y videos o trazas de las fallas para ver dónde se rompe. 2) La reproduzco en condiciones parecidas al CI: modo headless, misma resolución, menos recursos y muchas repeticiones. 3) Busco causas típicas: esperas fijas o insuficientes, datos compartidos con otras pruebas, dependencia del orden o de servicios externos. 4) Corrijo con esperas explícitas por condición, datos aislados propios de cada prueba y mocks para servicios externos. 5) Mientras la arreglo, la pongo en cuarentena para no bloquear al equipo, con un responsable y un plazo.',
    '{"metodo": "TECNICA", "criterios": ["Analiza evidencia de las fallas como logs, capturas o trazas", "Reproduce en condiciones similares al CI", "Identifica causas típicas como esperas, datos compartidos u orden", "Corrige la causa en vez de solo reintentar", "Gestiona la prueba en cuarentena con un responsable"], "palabras_clave": ["prueba inestable", "esperas explícitas", "datos aislados", "headless", "cuarentena"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero junto evidencia de las ejecuciones fallidas y trato de reproducir el problema con el mismo navegador y las condiciones del pipeline. Las causas más comunes son problemas de sincronización, datos que otras pruebas modifican o servicios externos lentos. Arreglo esa causa y, mientras tanto, aíslo la prueba para que no bloquee los despliegues.", "Una prueba flaky resta confianza a toda la suite, así que no basta con volver a ejecutarla. Analizo trazas y capturas, corro la prueba muchas veces en un ambiente similar al CI y reviso sincronización, estado compartido y dependencias de orden. Luego la estabilizo con esperas por condición y datos independientes."], "sinonimos": [["prueba inestable", "prueba flaky", "flaky test"], ["cuarentena", "quarantine"], ["E2E", "end to end", "extremo a extremo"]], "feedback_generico": {"correcto": "¡Excelente! Diagnosticaste con evidencia, atacaste la causa real y cuidaste que el equipo no quedara bloqueado.", "medio": "Vas bien, pero tu respuesta cubre solo parte del diagnóstico o propone soluciones que esconden el problema en vez de resolverlo.", "malo": "Repasa las causas más comunes de las pruebas inestables y las técnicas para reproducirlas y estabilizarlas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Una prueba E2E falla de forma intermitente en el pipeline de CI, pero siempre pasa en tu máquina. ¿Cómo la diagnosticas y la corriges?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Decido con criterios técnicos y del equipo, probando ambas herramientas con casos reales. 1) Reviso los navegadores que debo cubrir; Playwright incluye Chromium, Firefox y WebKit. 2) Considero el lenguaje del equipo: Cypress trabaja con JavaScript o TypeScript y Playwright también ofrece Python, Java y .NET. 3) Evalúo escenarios de la aplicación, como varias pestañas, que Playwright maneja de forma nativa y Cypress no soporta. 4) Comparo la depuración, como el runner interactivo de Cypress o el trace viewer de Playwright. 5) Considero la ejecución en paralelo, la integración con CI y el costo. 6) Hago una prueba de concepto con flujos críticos y decido con evidencia.',
    '{"metodo": "TECNICA", "criterios": ["Compara el soporte de navegadores", "Considera los lenguajes que domina el equipo", "Evalúa escenarios como múltiples pestañas", "Compara depuración, paralelismo e integración con CI", "Propone una prueba de concepto para decidir"], "palabras_clave": ["Cypress", "Playwright", "navegadores", "WebKit", "prueba de concepto", "paralelo"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No elegiría por popularidad, sino según el contexto. Si el equipo trabaja solo en JavaScript y valora una depuración muy visual, Cypress es una buena opción; si se necesita cubrir WebKit, usar otros lenguajes o manejar varias pestañas, Playwright se ajusta mejor. Antes de decidir, automatizaría dos o tres flujos críticos con ambas.", "Compararía cobertura de navegadores, lenguajes soportados, limitaciones de arquitectura, velocidad y facilidad para correr en paralelo en CI. Playwright controla el navegador desde fuera y soporta múltiples contextos, mientras Cypress corre dentro del navegador y ofrece una experiencia interactiva muy cómoda. Una prueba de concepto con casos reales define la elección."], "sinonimos": [["prueba de concepto", "PoC", "proof of concept"], ["E2E", "end to end", "extremo a extremo"]], "feedback_generico": {"correcto": "¡Muy bien! Comparaste las herramientas con criterios concretos y propusiste una forma objetiva de tomar la decisión.", "medio": "Vas bien, pero tu comparación se queda en pocos criterios o no explica cómo llegarías a una decisión final.", "malo": "Repasa las diferencias de arquitectura, navegadores y lenguajes entre Cypress y Playwright, y cómo se evalúa una herramienta antes de adoptarla."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu equipo debe elegir entre Cypress y Playwright para automatizar las pruebas E2E de una aplicación web nueva. ¿Qué criterios comparas y cómo decides?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Gestiono los datos de prueba para que cada prueba sea dueña de los suyos y no dependa del estado previo del ambiente. 1) Cada prueba crea lo que necesita al inicio, idealmente vía API o con scripts de base de datos, no por la interfaz. 2) Uso datos únicos, por ejemplo correos con un sufijo aleatorio o una marca de tiempo, para evitar choques. 3) Limpio lo creado al final con fixtures o hooks de teardown, o uso bases efímeras en contenedores. 4) Centralizo la generación con factories o builders para mantener datos válidos y legibles. 5) Nunca uso datos reales de clientes sin anonimizar, para cumplir la normativa de protección de datos.',
    '{"metodo": "TECNICA", "criterios": ["Propone que cada prueba cree sus propios datos", "Usa datos únicos para evitar colisiones", "Incluye limpieza o ambientes efímeros", "Centraliza la generación con factories o builders", "Considera la privacidad y la anonimización de datos reales"], "palabras_clave": ["datos de prueba", "datos únicos", "teardown", "factories", "anonimizar", "efímeras"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["El principio es que ninguna prueba dependa de lo que dejó otra. Por eso genero los datos al comenzar cada prueba usando la API, les pongo identificadores únicos y los elimino al terminar. Si es posible, levanto una base de datos desechable para cada ejecución.", "Usaría una capa de generación de datos con builders que creen entidades válidas en segundos, y fixtures que preparen y limpien el estado. Así cada prueba puede correr sola, en cualquier orden o en paralelo. Además evitaría copiar datos de producción sin enmascararlos."], "sinonimos": [["datos de prueba", "test data"], ["anonimizar", "enmascarar"], ["limpieza", "teardown"]], "feedback_generico": {"correcto": "¡Excelente! Lograste que cada prueba controle su propio estado y consideraste también la privacidad de los datos.", "medio": "Vas bien, pero te faltó explicar cómo evitar colisiones entre pruebas o cómo dejar el ambiente limpio después.", "malo": "Repasa las estrategias de gestión de datos de prueba: creación por prueba, limpieza, datos únicos y uso de ambientes aislados."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tus pruebas automatizadas fallan porque dependen de datos que otras pruebas o personas modifican en el ambiente de QA. ¿Cómo gestionas los datos de prueba para que sean independientes y repetibles?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Cuando la ejecución paralela rompe pruebas que pasan en serie, la causa más común es el estado compartido por falta de aislamiento: varias pruebas usan el mismo usuario, registro o recurso y se interfieren, o dependen del orden de ejecución. Se corrige haciendo que cada prueba cree y limpie sus propios datos y no dependa de otras.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el estado compartido como causa principal", "Menciona la dependencia del orden de ejecución", "Propone aislar datos y recursos por prueba", "Descarta causas que no explican la diferencia entre serie y paralelo"], "palabras_clave": ["ejecución paralela", "estado compartido", "aislamiento", "orden de ejecución", "en serie"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["En serie las pruebas nunca coinciden, pero en paralelo dos de ellas pueden modificar el mismo dato al mismo tiempo. El problema es el estado compartido y se resuelve dando a cada prueba sus propios datos y recursos.", "Esas fallas aleatorias indican que las pruebas no son independientes: comparten cuentas, registros o archivos, o asumen que otra prueba corrió antes. Para paralelizar con seguridad, cada caso debe poder ejecutarse solo y en cualquier orden."], "sinonimos": [["en paralelo", "ejecución paralela", "ejecución concurrente"], ["estado compartido", "datos compartidos"]], "feedback_generico": {"correcto": "¡Muy bien! Identificaste la causa de fondo y entiendes qué necesita una suite para ejecutarse en paralelo de forma segura.", "medio": "Vas bien, pero revisa qué cambia realmente entre ejecutar en serie y en paralelo para explicar estas fallas.", "malo": "Repasa qué cambia al ejecutar pruebas en paralelo y qué condiciones deben cumplir para hacerlo de forma confiable."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Al ejecutar la suite en paralelo con cuatro workers, empiezan a fallar al azar pruebas que siempre pasaban en serie. ¿Cuál es la causa más probable?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El navegador no permite abrir varias instancias al mismo tiempo en una misma máquina.', FALSE,
     'Los navegadores y los frameworks modernos sí permiten varias instancias o contextos simultáneos.', 1),
    ('Los localizadores XPath son más lentos y no alcanzan a responder cuando hay paralelismo.', FALSE,
     'El tipo de localizador no explica fallas que aparecen solo al ejecutar en paralelo.', 2),
    ('Las aserciones se evalúan antes que las acciones cuando hay más de un worker activo.', FALSE,
     'Dentro de cada prueba el orden de las instrucciones se mantiene, sin importar la cantidad de workers.', 3),
    ('Las pruebas comparten estado, como usuarios o registros, y se interfieren entre sí.', TRUE,
     'Correcto: en paralelo varias pruebas usan los mismos datos al mismo tiempo y se pisan.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Al ejecutar la suite en paralelo con cuatro workers, empiezan a fallar al azar pruebas que siempre pasaban en serie. ¿Cuál es la causa más probable?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las pruebas de contrato dirigidas por el consumidor, con herramientas como Pact, verifican que cada proveedor cumpla las expectativas de petición y respuesta que definen sus consumidores, sin levantar todo el sistema. Así se detectan cambios incompatibles de forma rápida y aislada, y se conservan solo unas pocas pruebas E2E para los flujos críticos.',
    '{"metodo": "TECNICA", "criterios": ["Propone pruebas de contrato entre servicios", "Explica el rol de consumidor y proveedor", "Menciona una herramienta como Pact", "Mantiene pocas pruebas E2E para flujos críticos", "Explica la ventaja de velocidad y aislamiento"], "palabras_clave": ["pruebas de contrato", "consumidor", "proveedor", "Pact", "cambios incompatibles"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Con contract testing cada servicio consumidor publica qué espera de la API que usa, y el proveedor verifica en su propio pipeline que cumple ese contrato. Eso reemplaza gran parte de las E2E por pruebas rápidas y localizadas.", "En vez de levantar los doce servicios juntos, se valida la integración par a par mediante contratos, por ejemplo con Pact. Si un proveedor rompe lo que un consumidor espera, el pipeline falla antes de desplegar."], "sinonimos": [["pruebas de contrato", "contract testing"], ["dirigidas por el consumidor", "consumer-driven"], ["E2E", "end to end", "extremo a extremo"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste un enfoque que mantiene la confianza en la integración y reduce la dependencia de pruebas lentas.", "medio": "Vas bien, pero revisa si tu opción realmente verifica la comunicación entre servicios o solo cada servicio por separado.", "malo": "Repasa las estrategias de prueba para microservicios y cómo se valida la integración sin depender de entornos completos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'En una arquitectura de doce microservicios, las pruebas E2E que los integran a todos son lentas, inestables y difíciles de diagnosticar. ¿Qué enfoque reduce esa dependencia sin perder confianza en la integración entre servicios?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Agregar más pruebas E2E con reintentos automáticos para compensar la inestabilidad.', FALSE,
     'Los reintentos esconden la inestabilidad y más pruebas E2E aumentan la lentitud.', 1),
    ('Pruebas de contrato guiadas por el consumidor, con una herramienta como Pact.', TRUE,
     'Correcto: verifican la integración entre pares de servicios de forma rápida y aislada.', 2),
    ('Subir la cobertura unitaria de cada servicio al 100% y eliminar las pruebas de integración.', FALSE,
     'Las pruebas unitarias no verifican que los servicios sigan siendo compatibles entre sí.', 3),
    ('Reemplazar las pruebas de integración automatizadas por una regresión manual antes de cada release.', FALSE,
     'Es más lento, menos repetible y detecta los problemas más tarde.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'En una arquitectura de doce microservicios, las pruebas E2E que los integran a todos son lentas, inestables y difíciles de diagnosticar. ¿Qué enfoque reduce esa dependencia sin perder confianza en la integración entre servicios?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un pipeline eficiente da retroalimentación rápida en cada pull request con las pruebas baratas, como las unitarias y un subconjunto de humo, y deja la suite E2E completa para etapas posteriores, paralelizada, antes de desplegar o en ejecuciones nocturnas. Así se equilibran velocidad y confianza sin bloquear al equipo por horas.',
    '{"metodo": "TECNICA", "criterios": ["Separa las pruebas en etapas según velocidad y costo", "Ejecuta pruebas rápidas en cada cambio como control de calidad", "Deja la suite E2E completa para etapas posteriores o nocturnas", "Propone paralelizar las pruebas lentas", "Equilibra velocidad de retroalimentación y confianza"], "palabras_clave": ["pipeline", "pull request", "humo", "nocturnas", "paralelizada", "retroalimentación"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Organizo el pipeline por etapas: en cada pull request corren las unitarias y unas pocas E2E críticas, y la suite completa corre en paralelo antes del despliegue o de forma programada. El equipo recibe feedback en minutos y no se pierde cobertura.", "No todas las pruebas deben correr en el mismo momento. Las rápidas funcionan como filtro en cada cambio y las lentas se ejecutan en una etapa posterior, divididas entre varios agentes, para no frenar la integración continua."], "sinonimos": [["humo", "smoke"], ["retroalimentación", "feedback"], ["nocturnas", "nightly"]], "feedback_generico": {"correcto": "¡Muy bien! Organizaste las pruebas por etapas y lograste equilibrar la rapidez del feedback con la confianza en el despliegue.", "medio": "Vas bien, pero revisa cómo afecta tu propuesta al tiempo de espera del equipo o a la cobertura antes de desplegar.", "malo": "Repasa cómo se estructuran las etapas de un pipeline de integración continua y qué pruebas conviene ejecutar en cada una."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu suite tiene 2.000 pruebas unitarias que tardan 3 minutos y 400 pruebas E2E que tardan 90 minutos. ¿Cómo las organizas en el pipeline de CI?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Correr todas las pruebas en cada commit y bloquear el merge hasta que termine la suite completa.', FALSE,
     'Hace esperar al equipo cerca de hora y media por cada cambio y frena la integración continua.', 1),
    ('Correr solo las pruebas E2E antes de cada release, porque son las que simulan al usuario real.', FALSE,
     'Deja de lado las pruebas unitarias, que son las más rápidas, y detecta los errores muy tarde.', 2),
    ('Unitarias y un smoke E2E en cada pull request, y la suite E2E completa en paralelo antes de desplegar.', TRUE,
     'Correcto: da feedback rápido en cada cambio y mantiene la cobertura completa antes de llegar a producción.', 3),
    ('Correr las pruebas de forma manual solo cuando alguien del equipo lo considere necesario antes de un merge.', FALSE,
     'Sin ejecución automática y sistemática el pipeline no protege la calidad de cada cambio.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Automatización de pruebas' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu suite tiene 2.000 pruebas unitarias que tardan 3 minutos y 400 pruebas E2E que tardan 90 minutos. ¿Cómo las organizas en el pipeline de CI?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño un framework simple, mantenible y común para los tres equipos, priorizando las pruebas de API sobre las de interfaz. 1) Elijo herramientas según el stack y las habilidades de los equipos, y las valido con una prueba de concepto. 2) Defino capas: clientes de API, Page Objects o componentes para la interfaz, y utilidades de datos separadas de las pruebas. 3) Externalizo la configuración por ambiente y manejo los secretos fuera del repositorio. 4) Incorporo generación de datos, reportes con evidencias y ejecución en paralelo desde el inicio. 5) Integro todo al pipeline de CI con etiquetas para smoke y regresión. 6) Documento convenciones, reviso el código de pruebas en pull requests y defino quién mantiene el núcleo.',
    '{"metodo": "TECNICA", "criterios": ["Elige herramientas según el contexto y con prueba de concepto", "Define una arquitectura en capas que separa pruebas, páginas y datos", "Externaliza la configuración y los secretos por ambiente", "Integra reportes, paralelismo y CI desde el inicio", "Establece convenciones y responsables del mantenimiento"], "palabras_clave": ["framework", "capas", "configuración por ambiente", "reportes", "pipeline", "convenciones"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Buscaría que el framework sea fácil de adoptar por los tres equipos. Separaría la lógica de interacción, ya sea páginas o clientes de API, de los casos de prueba y de los datos, con configuración por ambiente y credenciales seguras. Desde el comienzo incluiría reportes, paralelismo y ejecución en el pipeline, además de una guía de buenas prácticas.", "Mis decisiones apuntarían a mantenibilidad y escalabilidad: herramientas validadas con un piloto, una arquitectura modular, más cobertura en la capa de API que en la interfaz y pruebas etiquetadas por tipo. Definiría un equipo responsable del núcleo del framework y revisiones de código para que la calidad de las pruebas se mantenga."], "sinonimos": [["framework", "marco de automatización"], ["ambiente", "entorno"], ["secretos", "credenciales"]], "feedback_generico": {"correcto": "¡Excelente! Diseñaste un framework pensado para escalar entre equipos, con decisiones claras de arquitectura y operación.", "medio": "Tienes buenas decisiones técnicas, pero te faltó abordar cómo se mantendrá, configurará o integrará el framework en el día a día.", "malo": "Repasa los componentes de un framework de automatización: arquitectura por capas, configuración, datos, reportes e integración continua."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden diseñar desde cero un framework de automatización para una aplicación con interfaz web y API REST, que usarán tres equipos. ¿Qué decisiones de diseño tomas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Propongo recuperar primero la confianza y luego reducir el tamaño y el tiempo de la suite. 1) Mido el estado actual: duración, tasa de fallos por prueba y costo de mantenimiento de cada módulo. 2) Separo las pruebas inestables en cuarentena para que el resultado principal vuelva a ser confiable. 3) Elimino pruebas duplicadas o de bajo valor y muevo las validaciones de lógica a pruebas unitarias o de API. 4) Refactorizo localizadores y Page Objects para que los cambios de interfaz se corrijan en un solo lugar. 5) Paralelizo la ejecución y etiqueto un subconjunto crítico para cada pull request. 6) Asigno responsables por área y sigo métricas de duración y estabilidad.',
    '{"metodo": "TECNICA", "criterios": ["Mide el estado de la suite antes de actuar", "Aísla las pruebas inestables para recuperar la confianza", "Elimina pruebas de bajo valor y baja la verificación a capas inferiores", "Refactoriza para reducir el costo de mantenimiento", "Paraleliza y define responsables y métricas"], "palabras_clave": ["cuarentena", "pruebas duplicadas", "Page Objects", "paralelizo", "métricas", "costo de mantenimiento"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Lo primero es que el equipo vuelva a confiar, así que apartaría las pruebas inestables y mediría cuáles fallan y cuánto cuestan. Después recortaría la suite, eliminando casos repetidos y bajando validaciones a la capa de API o unitaria, y reorganizaría el código con Page Objects. Por último, paralelizaría y asignaría dueños para que no vuelva a degradarse.", "Trataría la suite como un producto con deuda técnica. Haría un inventario con datos de duración y fallos, eliminaría lo que no aporta valor, rebalancearía hacia la base de la pirámide y centralizaría los localizadores. Con ejecución paralela y un subconjunto crítico por cambio, el tiempo baja y la señal vuelve a ser útil."], "sinonimos": [["suite", "batería de pruebas"], ["pruebas inestables", "pruebas flaky"], ["paralelizo", "ejecución en paralelo"]], "feedback_generico": {"correcto": "¡Muy bien! Tu plan recupera la confianza, reduce el costo de mantenimiento y evita que la suite vuelva a degradarse.", "medio": "Vas bien, pero tu plan ataca solo uno de los problemas; piensa también en el tiempo de ejecución, la confianza o el mantenimiento.", "malo": "Repasa las prácticas para mantener sana una suite automatizada: priorización, refactorización, distribución por capas y métricas de estabilidad."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Heredas una suite de 1.500 pruebas E2E que tarda cuatro horas, se rompe con cada cambio de interfaz y el equipo ya no confía en sus resultados. ¿Qué plan propones para recuperarla?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Explico que automatizar todo no es rentable ni realista, y propongo priorizar por valor y retorno de inversión. 1) Clasifico los casos por frecuencia de ejecución, riesgo de negocio, estabilidad de la funcionalidad y costo de automatizar. 2) Priorizo la regresión crítica y repetitiva, como login, pagos y flujos principales, de preferencia a nivel de API. 3) Dejo fuera lo que conviene probar a mano: usabilidad, pruebas exploratorias y funcionalidades que cambian seguido. 4) Elimino casos obsoletos o duplicados antes de automatizar. 5) Acuerdo metas medibles, como tiempo de regresión o defectos escapados, en lugar de un porcentaje de casos. 6) Entrego avances por iteraciones y ajusto el plan con los resultados.',
    '{"metodo": "TECNICA", "criterios": ["Explica por qué automatizar el 100% no es conveniente", "Prioriza por frecuencia, riesgo, estabilidad y costo", "Identifica lo que conviene mantener manual", "Propone metas de valor en vez de un porcentaje de casos", "Plantea una entrega iterativa con resultados medibles"], "palabras_clave": ["retorno de inversión", "priorizar", "regresión crítica", "exploratorias", "metas medibles"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Le diría a la gerencia que el objetivo no debería ser un porcentaje, sino reducir el tiempo de regresión y el riesgo. Analizaría los 2.000 casos, depuraría los obsoletos y automatizaría primero los más repetitivos y críticos, dejando manuales la exploración y la usabilidad. Mostraría avances cada pocas semanas con métricas concretas.", "Automatizar sin criterio genera una suite cara de mantener. Propondría una matriz que cruce riesgo, frecuencia y estabilidad para elegir qué automatizar, empezando por la capa de API, y acordaría indicadores de valor. Lo que cambia mucho o requiere juicio humano seguiría siendo manual."], "sinonimos": [["retorno de inversión", "ROI"], ["casos de prueba", "test cases"]], "feedback_generico": {"correcto": "¡Excelente! Respondiste con criterio técnico, propusiste prioridades claras y orientaste la meta hacia resultados de negocio.", "medio": "Vas bien, pero te faltó explicar con qué criterios priorizarías o cómo medirías el éxito más allá de un porcentaje.", "malo": "Repasa los criterios para decidir qué conviene automatizar y cómo se estima el retorno de la inversión en automatización."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La gerencia quiere automatizar el 100% de los 2.000 casos de prueba manuales en tres meses. ¿Cómo respondes y qué plan propones?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Automatización de pruebas'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Testing y QA

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La severidad mide el impacto técnico del defecto en el funcionamiento del sistema y la prioridad mide la urgencia de corregirlo según el negocio. Un nombre mal escrito en la portada no rompe ninguna función, por lo que su severidad es baja, pero afecta la imagen frente a todos los clientes y su prioridad es alta.',
    '{"metodo": "TECNICA", "criterios": ["Distingue severidad de prioridad", "Asocia la severidad al impacto técnico en el funcionamiento", "Asocia la prioridad a la urgencia para el negocio", "Reconoce que un defecto visible puede ser urgente sin ser grave"], "palabras_clave": ["severidad", "prioridad", "impacto técnico", "urgencia", "negocio"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Es un caso de severidad baja con prioridad alta. El sistema funciona, así que el daño técnico es menor, pero la marca se ve mal ante todos los visitantes y conviene corregirlo cuanto antes.", "La severidad responde a qué tanto se rompe el sistema y la prioridad a qué tan rápido hay que arreglarlo. Aquí nada deja de funcionar, pero el error de imagen en la página principal justifica atenderlo con urgencia."], "sinonimos": [["defecto", "bug", "error"], ["severidad", "gravedad"]], "feedback_generico": {"correcto": "¡Muy bien! Separaste con claridad el impacto técnico del defecto de la urgencia que tiene para el negocio.", "medio": "Vas bien: identificaste uno de los dos ejes, pero te faltó justificar por qué el otro tiene un valor distinto en este caso.", "malo": "No te preocupes, es una confusión común. Repasa la diferencia entre severidad y prioridad y qué mide cada una en la gestión de defectos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En la portada de una tienda web, el nombre de la empresa aparece mal escrito en el encabezado, pero todas las funciones operan bien. ¿Cómo clasificarías este defecto?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Severidad alta y prioridad alta, porque el error queda visible para todos los usuarios del sitio.', FALSE,
     'La severidad mide el impacto en el funcionamiento, y aquí ninguna función está afectada.', 1),
    ('Severidad baja y prioridad alta, porque no afecta el funcionamiento pero daña la imagen.', TRUE,
     'Correcto: el sistema funciona, pero el error es muy visible y el negocio necesita corregirlo pronto.', 2),
    ('Severidad alta y prioridad baja, porque es un error de contenido que puede esperar.', FALSE,
     'Invierte los conceptos: un error de texto no tiene impacto técnico alto y su visibilidad lo hace urgente.', 3),
    ('Severidad baja y prioridad baja, porque los defectos visuales nunca se priorizan.', FALSE,
     'La prioridad depende del impacto en el negocio, y un error en la portada afecta la imagen ante todos los clientes.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En la portada de una tienda web, el nombre de la empresa aparece mal escrito en el encabezado, pero todas las funciones operan bien. ¿Cómo clasificarías este defecto?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El análisis de valores límite prueba cada frontera y el valor inmediatamente fuera del rango, porque ahí se concentran errores como usar menor que en vez de menor o igual. Para el rango de 18 a 65 eso da 17 y 66 como valores inválidos, y 18 y 65 como válidos dentro del rango.',
    '{"metodo": "TECNICA", "criterios": ["Identifica los dos límites del rango", "Incluye el valor justo fuera de cada límite", "Incluye el valor exactamente en cada límite", "Explica por qué los errores se concentran en las fronteras"], "palabras_clave": ["valores límite", "frontera", "inválidos", "dentro del rango", "fuera del rango"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Se prueban 17, 18, 65 y 66. Así se verifica que el sistema acepte los extremos válidos y rechace el primer valor inválido de cada lado.", "Con dos valores por límite se toma el borde y su vecino exterior: 18 con 17 y 65 con 66. Esos casos detectan errores de comparación, como confundir mayor con mayor o igual."], "sinonimos": [["valores límite", "valores frontera", "boundary values"], ["frontera", "límite", "borde"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste los valores que realmente ponen a prueba las fronteras del rango y entiendes por qué.", "medio": "Vas por buen camino, pero revisa si tu conjunto cubre tanto el lado válido como el inválido de cada frontera.", "malo": "Repasa la técnica de análisis de valores límite: dónde se ubican los valores que conviene probar y por qué ahí aparecen más errores."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un campo de formulario acepta edades enteras entre 18 y 65, ambos incluidos. Aplicando análisis de valores límite con dos valores por frontera, ¿qué conjunto conviene probar?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('18, 40 y 65, que son el mínimo, un valor central y el máximo del rango.', FALSE,
     'No incluye valores fuera del rango, y el valor central corresponde a partición de equivalencia, no a valores límite.', 1),
    ('0, 18, 65 y 100, para cubrir los extremos posibles que admite el campo.', FALSE,
     '0 y 100 están lejos de las fronteras, por lo que no detectan errores de comparación en los límites.', 2),
    ('17, 18, 65 y 66, justo en cada límite y justo fuera de él.', TRUE,
     'Correcto: prueba cada frontera válida y el primer valor inválido a cada lado.', 3),
    ('19, 20, 63 y 64, que están cerca de los límites pero sin tocarlos.', FALSE,
     'Son valores válidos internos; no prueban la frontera ni el primer valor que debe rechazarse.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Un campo de formulario acepta edades enteras entre 18 y 65, ambos incluidos. Aplicando análisis de valores límite con dos valores por frontera, ¿qué conjunto conviene probar?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las pruebas exploratorias combinan aprendizaje, diseño y ejecución en paralelo: el tester decide la siguiente prueba según lo que va descubriendo. No son improvisación sin control, porque se guían por un objetivo o charter, suelen tener un tiempo acotado y se documentan los hallazgos.',
    '{"metodo": "TECNICA", "criterios": ["Explica que diseño y ejecución ocurren de forma simultánea", "Menciona un objetivo o charter que guía la sesión", "Distingue exploratorias de pruebas aleatorias sin control", "Indica que los hallazgos se registran"], "palabras_clave": ["exploratorias", "charter", "aprendizaje", "diseño y ejecución", "tiempo acotado", "hallazgos"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Es una forma de probar donde no sigo un guion fijo: voy investigando el módulo, ideando pruebas según lo que observo y anotando lo que encuentro. Igual trabajo con un objetivo claro y un tiempo definido.", "En una prueba exploratoria el tester usa su experiencia para investigar el software en vivo y ajusta lo que prueba a medida que aprende. Se organiza en sesiones con una misión y un registro de defectos y observaciones."], "sinonimos": [["pruebas exploratorias", "exploratory testing"], ["charter", "misión", "objetivo de la sesión"]], "feedback_generico": {"correcto": "¡Muy bien! Reconociste que esta técnica tiene estructura y propósito, aunque no siga un guion fijo.", "medio": "Bien encaminado, pero precisa qué diferencia esta técnica de probar al azar y cómo se organiza una sesión.", "malo": "No te preocupes, es un concepto que se confunde seguido. Repasa las pruebas exploratorias, el uso de charters y en qué se diferencian de las pruebas guionadas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Tu líder te pide hacer una sesión de pruebas exploratorias sobre un módulo nuevo de la aplicación. ¿Qué describe mejor esa actividad?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Diseñar y ejecutar pruebas a la vez, guiándose por un objetivo y registrando lo que se encuentra.', TRUE,
     'Correcto: la exploración es simultánea, pero está guiada por un charter y deja registro de lo encontrado.', 1),
    ('Hacer clic al azar por toda la aplicación, sin un objetivo definido ni registro de lo que se probó.', FALSE,
     'Eso es probar sin método; las pruebas exploratorias tienen un objetivo y se documentan.', 2),
    ('Ejecutar paso a paso casos de prueba documentados antes, con sus resultados esperados.', FALSE,
     'Eso describe pruebas guionadas, donde el diseño ocurre antes de la ejecución.', 3),
    ('Correr un script automatizado que recorre todas las pantallas buscando errores.', FALSE,
     'La exploración depende del juicio y el aprendizaje del tester, no de un recorrido automático.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Tu líder te pide hacer una sesión de pruebas exploratorias sobre un módulo nuevo de la aplicación. ¿Qué describe mejor esa actividad?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Escribo un reporte claro, reproducible y sin ambigüedades. 1) Pongo un título breve que describa el problema y dónde ocurre. 2) Detallo los pasos para reproducir el error, con los datos de prueba usados. 3) Indico el resultado esperado y el resultado obtenido. 4) Registro el ambiente: versión o build, navegador, sistema operativo y usuario de prueba. 5) Adjunto evidencia como capturas, video o logs de la consola. 6) Propongo una severidad y verifico que no exista un reporte duplicado.',
    '{"metodo": "TECNICA", "criterios": ["Incluye pasos para reproducir con datos concretos", "Diferencia resultado esperado y resultado obtenido", "Registra el ambiente y la versión probada", "Adjunta evidencia como capturas o logs", "Indica severidad y evita duplicados"], "palabras_clave": ["pasos para reproducir", "resultado esperado", "resultado obtenido", "ambiente", "evidencia", "severidad"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Un buen reporte tiene un título descriptivo, los pasos exactos para reproducir el error y la diferencia entre lo que debería pasar y lo que pasó. También agrego el navegador, la versión de la aplicación y capturas o logs. Así el desarrollador no tiene que adivinar nada.", "Pienso en que otra persona pueda repetir el error sin preguntarme. Por eso anoto la ruta seguida, los datos ingresados, el comportamiento esperado y el real, el ambiente donde ocurrió y la evidencia. Al final asigno una severidad y reviso que el bug no esté reportado."], "sinonimos": [["bug", "defecto", "incidencia"], ["ambiente", "entorno"], ["pasos para reproducir", "pasos de reproducción"]], "feedback_generico": {"correcto": "¡Excelente reporte! Incluiste lo necesario para que cualquier persona del equipo reproduzca el error sin tener que preguntarte.", "medio": "Vas bien, tu reporte tiene una base útil, pero le faltan elementos que ayudan a reproducir el error y a entender su contexto.", "malo": "Ánimo, redactar bugs se aprende con práctica. Repasa la estructura de un reporte de defecto y qué datos necesita un desarrollador para reproducirlo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Encontraste un error en el carrito de compras de una aplicación web. ¿Qué información incluyes en el reporte del bug para que el desarrollador pueda reproducirlo y corregirlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El defecto pasa por estados que muestran quién lo tiene y qué falta para cerrarlo. 1) Nuevo: el tester lo registra con su evidencia. 2) Asignado: el líder o el equipo lo revisa y lo entrega a un desarrollador. 3) En corrección: el desarrollador analiza la causa y aplica el arreglo. 4) Corregido: la solución se despliega en el ambiente de QA para probarla. 5) Verificación: el tester vuelve a probar y ejecuta regresión en la zona afectada. 6) Cerrado si funciona, o reabierto si el error persiste; también puede rechazarse por duplicado o porque no es un defecto.',
    '{"metodo": "TECNICA", "criterios": ["Nombra los estados principales del defecto", "Explica la verificación antes de cerrar", "Menciona la posibilidad de reabrir un defecto", "Considera estados como rechazado o duplicado", "Indica quién es responsable en cada etapa"], "palabras_clave": ["asignado", "corregido", "verificación", "reabierto", "cerrado", "rechazarse"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Cuando reporto un bug queda como nuevo; luego se asigna a un desarrollador, que lo corrige y lo marca como resuelto. Yo lo vuelvo a probar en QA y, si está bien, lo cierro; si no, lo reabro. También puede rechazarse si es un duplicado o un comportamiento esperado.", "El flujo típico en una herramienta como Jira va de abierto a en progreso, luego a resuelto y finalmente a cerrado tras la confirmación de QA. Si la prueba de confirmación falla, el defecto vuelve a abrirse y regresa al desarrollador."], "sinonimos": [["defecto", "bug", "incidencia"], ["reabierto", "reabrir", "reopened"], ["verificación", "retest", "prueba de confirmación"]], "feedback_generico": {"correcto": "¡Muy bien! Describiste el recorrido completo del defecto y mostraste que entiendes quién actúa en cada etapa.", "medio": "Buen avance: nombraste parte del recorrido, pero te faltó explicar qué pasa después de la corrección y los caminos alternativos.", "malo": "No te desanimes. Repasa los estados típicos de un defecto en herramientas como Jira y qué condición permite pasar de uno a otro."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica el ciclo de vida de un defecto desde que lo reportas hasta que se cierra. ¿Qué estados puede recorrer?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Los cuatro niveles se diferencian por el alcance de lo que prueban y por quién suele ejecutarlos. 1) Unitaria: verifica una función o clase aislada, por ejemplo que el cálculo del IVA devuelva el monto correcto. 2) Integración: valida que dos o más componentes se comuniquen bien, como el servicio de pedidos guardando en la base de datos. 3) Sistema: prueba la aplicación completa contra los requisitos, por ejemplo comprar un producto desde el login hasta el pago. 4) Aceptación: el cliente o el Product Owner confirma que el sistema resuelve su necesidad real antes de liberarlo.',
    '{"metodo": "TECNICA", "criterios": ["Define el alcance de la prueba unitaria", "Explica que la integración valida la comunicación entre componentes", "Describe la prueba de sistema sobre la aplicación completa", "Asocia la aceptación al usuario o al negocio", "Da un ejemplo concreto por nivel"], "palabras_clave": ["unitaria", "integración", "sistema", "aceptación", "componentes", "requisitos"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["La unitaria revisa una pieza pequeña de código por separado; la de integración comprueba que esas piezas funcionen juntas, como una API con su base de datos. La de sistema evalúa todo el producto armado y la de aceptación confirma con el usuario que cumple lo que pidió.", "Van de lo más pequeño a lo más grande: primero métodos individuales, luego la interacción entre módulos y después la aplicación completa en un ambiente parecido a producción. Al final, el negocio valida en la prueba de aceptación, a veces llamada UAT, que el sistema sirve para su propósito."], "sinonimos": [["prueba unitaria", "unit test"], ["prueba de aceptación", "UAT", "prueba de aceptación de usuario"], ["componentes", "módulos"]], "feedback_generico": {"correcto": "¡Muy bien! Diferenciaste los niveles por su alcance y tus ejemplos muestran que sabes aplicarlos.", "medio": "Vas bien, pero algunos niveles quedaron poco diferenciados o sin un ejemplo que muestre su alcance real.", "malo": "Repasa los niveles de prueba: qué abarca cada uno, quién lo ejecuta normalmente y en qué orden se aplican durante el desarrollo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica la diferencia entre prueba unitaria, de integración, de sistema y de aceptación, con un ejemplo breve de cada una.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En una tabla de decisión completa cada regla es una combinación de valores de las condiciones. Con tres condiciones booleanas hay 2 × 2 × 2 = 8 combinaciones posibles; luego se pueden fusionar las reglas cuyo resultado no depende de alguna condición.',
    '{"metodo": "TECNICA", "criterios": ["Calcula las combinaciones como 2 elevado al número de condiciones", "Explica que cada regla es una combinación de condiciones", "Distingue la tabla completa de la tabla simplificada", "Relaciona cada regla con una acción o resultado esperado"], "palabras_clave": ["tabla de decisión", "combinaciones", "condiciones", "reglas", "booleanas"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Son 8, porque cada condición tiene dos valores posibles y se multiplican entre sí: dos por dos por dos. Cada una de esas columnas indica qué descuento corresponde.", "Una tabla completa enumera todas las combinaciones de verdadero y falso, que para tres condiciones son ocho. Después, si una condición no cambia el resultado, se colapsan reglas para reducir casos."], "sinonimos": [["tabla de decisión", "decision table"], ["reglas", "columnas"]], "feedback_generico": {"correcto": "¡Muy bien! Calculaste correctamente el tamaño de la tabla y entiendes cómo se construyen sus reglas.", "medio": "Te acercaste, pero revisa cómo se combinan los valores de cada condición para obtener el total de reglas.", "malo": "Repasa la técnica de tablas de decisión: cómo se forman las reglas a partir de las condiciones y cuándo se pueden simplificar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un descuento depende de tres condiciones que pueden ser verdaderas o falsas: cliente frecuente, compra sobre cierto monto y uso de cupón. ¿Cuántas reglas tiene la tabla de decisión completa, antes de simplificarla?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('3 reglas, una por cada condición que define el descuento.', FALSE,
     'Las reglas no corresponden a condiciones sueltas, sino a combinaciones de sus valores.', 1),
    ('6 reglas, dos valores por cada una de las tres condiciones.', FALSE,
     'Suma los valores en vez de multiplicarlos; las combinaciones crecen de forma multiplicativa.', 2),
    ('9 reglas, porque se elevan las tres condiciones al cuadrado.', FALSE,
     'El cálculo correcto es 2 elevado al número de condiciones, no el número de condiciones al cuadrado.', 3),
    ('8 reglas, porque son todas las combinaciones de 2 elevado a 3.', TRUE,
     'Correcto: cada condición tiene dos valores y 2 × 2 × 2 da ocho combinaciones.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un descuento depende de tres condiciones que pueden ser verdaderas o falsas: cliente frecuente, compra sobre cierto monto y uso de cupón. ¿Cuántas reglas tiene la tabla de decisión completa, antes de simplificarla?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un buen escenario Gherkin describe un contexto concreto en el Dado, una sola acción del usuario en el Cuando y un resultado observable y verificable en el Entonces. Además usa lenguaje de negocio, sin detalles de implementación como identificadores de botones o consultas SQL.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce la estructura Dado, Cuando, Entonces", "Exige datos concretos en el contexto", "Exige un resultado observable y verificable", "Evita detalles técnicos de implementación", "Usa lenguaje de negocio"], "palabras_clave": ["Gherkin", "Dado", "Cuando", "Entonces", "verificable", "lenguaje de negocio"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["El escenario correcto fija un saldo inicial preciso, ejecuta una acción concreta y termina con un resultado que se puede comprobar. Los otros son vagos o hablan de la implementación en vez del comportamiento.", "En BDD, los criterios deben poder leerse por el negocio y convertirse en pruebas. Por eso se prefieren datos concretos y resultados medibles, no frases como funciona correctamente ni referencias a tablas o botones."], "sinonimos": [["Dado", "Given"], ["Cuando", "When"], ["Entonces", "Then"], ["criterios de aceptación", "acceptance criteria"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste el escenario que cualquier persona del negocio puede leer y que además se puede automatizar.", "medio": "Vas bien, pero revisa con más cuidado si cada parte del escenario es concreta, verificable y libre de detalles técnicos.", "malo": "Repasa la estructura de Gherkin y las buenas prácticas de BDD para escribir criterios de aceptación claros y comprobables."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Estás revisando criterios de aceptación escritos en Gherkin para el retiro de dinero de una billetera digital. ¿Cuál escenario está mejor escrito?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Dado que el usuario ingresó a la aplicación con una cuenta activa y verificada, cuando realiza un retiro, entonces el sistema funciona correctamente.', FALSE,
     'El resultado es vago: funciona correctamente no se puede verificar de forma objetiva.', 1),
    ('Dado un usuario con saldo de 10.000 pesos, cuando solicita retirar 15.000 pesos, entonces ve el aviso de saldo insuficiente y su saldo sigue en 10.000.', TRUE,
     'Correcto: tiene un contexto concreto, una acción única y un resultado observable, en lenguaje de negocio.', 2),
    ('Cuando el usuario presiona el botón con id btnRetiro, entonces se ejecuta un SELECT a la tabla CUENTAS y luego un UPDATE del saldo.', FALSE,
     'Describe la implementación técnica y no el comportamiento esperado, y además no define el contexto inicial.', 3),
    ('Dado que el usuario retira 15.000 pesos y luego revisa su saldo, cuando el saldo es insuficiente, entonces debería mostrarse algún tipo de mensaje.', FALSE,
     'Pone acciones en el Dado, usa una condición como acción y su resultado es ambiguo.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Estás revisando criterios de aceptación escritos en Gherkin para el retiro de dinero de una billetera digital. ¿Cuál escenario está mejor escrito?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La pirámide de pruebas recomienda una base amplia de pruebas unitarias, rápidas y baratas, una capa intermedia de integración o servicios y pocas pruebas de interfaz de extremo a extremo. La distribución descrita es el antipatrón del cono de helado, que produce suites lentas y frágiles, y se corrige bajando la verificación de reglas de negocio a capas inferiores.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce la pirámide de pruebas y sus capas", "Identifica la distribución invertida o cono de helado", "Propone mover pruebas a las capas unitaria y de integración", "Relaciona las pruebas de interfaz con lentitud y fragilidad"], "palabras_clave": ["pirámide de pruebas", "cono de helado", "unitarias", "integración", "interfaz", "frágiles"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La suite está al revés de lo recomendado: la mayoría de las pruebas está en la capa más cara y frágil. Lo sano es tener muchas unitarias, varias de integración y solo las pruebas de interfaz necesarias para los flujos críticos.", "Ese es el antipatrón conocido como cono de helado. Para corregirlo se reescriben las validaciones de lógica como pruebas unitarias o de API y se conservan pocas pruebas end to end que cubran los recorridos principales."], "sinonimos": [["pruebas de interfaz", "pruebas de UI", "pruebas end to end"], ["cono de helado", "pirámide invertida"]], "feedback_generico": {"correcto": "¡Muy bien! Diagnosticaste el problema de distribución y propusiste una corrección que mejora velocidad y estabilidad.", "medio": "Vas bien encaminado, pero te faltó explicar hacia qué capas conviene mover las pruebas y por qué.", "malo": "Repasa el modelo de la pirámide de pruebas y sus antipatrones más comunes para entender cómo debería distribuirse una suite."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu equipo tiene 600 pruebas automatizadas de interfaz, 80 de integración y 40 unitarias, y la suite es lenta y se rompe con cada cambio visual. ¿Qué diagnóstico y acción corresponden?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Es la distribución ideal; solo hay que agregar más esperas a las pruebas de interfaz.', FALSE,
     'Tener la mayoría de las pruebas en la interfaz es justamente lo que vuelve la suite lenta y frágil.', 1),
    ('Faltan pruebas de interfaz; conviene duplicarlas para cubrir más escenarios de usuario.', FALSE,
     'Agregar más pruebas en la capa más cara empeora la lentitud y el mantenimiento.', 2),
    ('Es una pirámide invertida; conviene bajar la lógica a pruebas unitarias y de integración.', TRUE,
     'Correcto: la suite debe tener su base en pruebas unitarias y pocas pruebas de interfaz para flujos críticos.', 3),
    ('El problema es la herramienta; cambiando el framework de UI se resuelve sin tocar nada más.', FALSE,
     'Cambiar de herramienta no corrige una distribución de pruebas desequilibrada.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Tu equipo tiene 600 pruebas automatizadas de interfaz, 80 de integración y 40 unitarias, y la suite es lenta y se rompe con cada cambio visual. ¿Qué diagnóstico y acción corresponden?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Aplico partición de equivalencia: divido las entradas en clases que el sistema debería tratar igual y pruebo un representante de cada una. 1) Identifico las clases válidas: peso mayor que cero y hasta 5 kg con tarifa A, y sobre 5 hasta 20 kg con tarifa B. 2) Identifico las clases inválidas: cero o negativo, más de 20 kg y valores no numéricos o vacíos. 3) Elijo un valor representativo por clase, por ejemplo 3, 12, -1, 25 y un texto. 4) Defino el resultado esperado de cada caso: la tarifa correcta o el mensaje de rechazo. 5) Complemento con valores límite en 0, 5 y 20, porque la partición sola no revisa las fronteras.',
    '{"metodo": "TECNICA", "criterios": ["Separa clases válidas e inválidas", "Elige un valor representativo por clase", "Define el resultado esperado de cada caso", "Incluye entradas no numéricas o vacías", "Complementa con valores límite"], "palabras_clave": ["partición de equivalencia", "clases válidas", "clases inválidas", "representativo", "valores límite"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Agrupo los pesos en rangos que producen el mismo comportamiento: hasta 5 kg, de 5 a 20 kg, pesos imposibles y pesos excesivos. Pruebo un solo valor de cada grupo, porque si uno funciona se asume que el resto del grupo también, y agrego casos con texto o campo vacío. Luego refuerzo las fronteras con valores límite.", "La idea es reducir casos sin perder cobertura: identifico dos particiones válidas, una por tarifa, y varias inválidas, como peso negativo, cero, mayor a 20 kg o no numérico. Para cada partición escribo un caso con su resultado esperado y después reviso los bordes entre particiones."], "sinonimos": [["partición de equivalencia", "clases de equivalencia", "equivalence partitioning"], ["caso de prueba", "test case"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste bien las clases, elegiste representantes con criterio y reconociste los límites de la técnica.", "medio": "Vas bien, pero tu análisis dejó fuera algunas clases o no definió qué resultado esperas en cada caso.", "malo": "Repasa cómo se identifican clases de equivalencia válidas e inválidas a partir de un requisito y cómo se elige un valor representativo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un sistema de envíos cobra tarifa A hasta 5 kg, tarifa B sobre 5 kg y hasta 20 kg, y rechaza paquetes de más de 20 kg o con peso cero o negativo. ¿Cómo diseñas los casos de prueba con partición de equivalencia?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Integro la calidad a todo el sprint para que probar no sea una etapa final. 1) En el refinamiento reviso las historias y ayudo a escribir criterios de aceptación claros y verificables. 2) En la planificación estimo también el esfuerzo de pruebas y propongo historias más pequeñas. 3) Durante el sprint preparo casos y datos mientras se desarrolla, y pruebo cada historia apenas está lista. 4) Acuerdo con el equipo una Definition of Done que incluya pruebas y automatización. 5) Limito el trabajo en curso para que las historias se terminen de una en una y no se acumulen. 6) En la retrospectiva muestro con datos el cuello de botella y acordamos mejoras.',
    '{"metodo": "TECNICA", "criterios": ["Involucra a QA desde el refinamiento y los criterios de aceptación", "Propone probar historias de forma continua durante el sprint", "Incluye pruebas en la Definition of Done", "Propone historias pequeñas o limitar el trabajo en curso", "Usa la retrospectiva para mejorar el flujo"], "palabras_clave": ["refinamiento", "criterios de aceptación", "Definition of Done", "trabajo en curso", "retrospectiva"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["El problema es que la calidad se trata como una fase al final. Yo participaría desde el refinamiento definiendo criterios de aceptación, probaría cada historia en cuanto se termina y pediría que la Definition of Done incluya las pruebas. Así el equipo completo se hace responsable de terminar historias probadas.", "Propondría dividir las historias para que lleguen a pruebas durante todo el sprint y no en bloque. Además, prepararía los casos de prueba en paralelo al desarrollo, trabajaría con los desarrolladores en pruebas tempranas y llevaría el problema a la retrospectiva con métricas concretas."], "sinonimos": [["Definition of Done", "definición de terminado", "DoD"], ["historias", "historias de usuario"], ["refinamiento", "grooming"]], "feedback_generico": {"correcto": "¡Muy bien! Propusiste integrar la calidad durante todo el sprint y no solo al final, con acciones concretas para el equipo.", "medio": "Vas por buen camino, pero tu propuesta se concentra en una sola etapa del sprint; piensa en cómo participar antes y después del desarrollo.", "malo": "Repasa cómo trabaja QA dentro de Scrum: en qué ceremonias participa y cómo se evita que las pruebas se acumulen al final del sprint."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En un equipo Scrum con sprints de dos semanas, QA recibe todas las historias para probar recién el último día del sprint. ¿Cómo reorganizarías la participación de QA durante el sprint?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Planteo la prueba de carga a partir de objetivos medibles y un escenario realista. 1) Defino con negocio la carga esperada, como usuarios concurrentes o transacciones por segundo, y los umbrales aceptables. 2) Preparo un ambiente lo más parecido posible a producción y datos de prueba suficientes, usando una pasarela de pago simulada. 3) Diseño el script con una herramienta como JMeter o k6, con una subida gradual de usuarios. 4) Ejecuto la prueba midiendo tiempo de respuesta en percentil 95, throughput y tasa de errores. 5) Monitoreo CPU, memoria y base de datos para encontrar cuellos de botella. 6) Comparo con los umbrales, informo hallazgos y repito tras cada mejora.',
    '{"metodo": "TECNICA", "criterios": ["Define objetivos y umbrales medibles antes de probar", "Usa un ambiente y datos representativos", "Diseña una carga gradual con una herramienta adecuada", "Mide tiempo de respuesta, throughput y tasa de errores", "Monitorea recursos para encontrar cuellos de botella"], "palabras_clave": ["prueba de carga", "usuarios concurrentes", "tiempo de respuesta", "throughput", "tasa de errores", "cuellos de botella"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero acordaría cuánto tráfico se espera y qué tiempos son aceptables. Luego armaría un script en k6 o JMeter que suba la carga de forma progresiva contra un ambiente similar a producción, sin cobrar pagos reales. Mientras corre, revisaría latencias, errores y consumo de recursos, y entregaría un informe con resultados y riesgos.", "La prueba debe responder si el pago cumple los tiempos con la carga de la campaña. Para eso simulo usuarios concurrentes en etapas, mido percentiles de respuesta y porcentaje de fallos, y observo servidores y base de datos. Si no se cumplen los objetivos, identifico el cuello de botella y vuelvo a medir después de corregirlo."], "sinonimos": [["prueba de carga", "load testing"], ["throughput", "transacciones por segundo"], ["tiempo de respuesta", "latencia"]], "feedback_generico": {"correcto": "¡Muy bien! Planteaste una prueba con objetivos claros, métricas pertinentes y un análisis de los resultados.", "medio": "Tienes una buena base, pero te faltó definir umbrales de éxito o explicar qué métricas observarías durante la prueba.", "malo": "Repasa los fundamentos de las pruebas de rendimiento: cómo se definen objetivos, qué herramientas se usan y qué métricas importan."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Antes de una campaña de ventas, te piden verificar que el endpoint de pago soporte el aumento de tráfico esperado. ¿Cómo planteas una prueba de carga básica?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Shift-left significa mover las actividades de calidad hacia las etapas tempranas del ciclo de desarrollo, donde el costo de corrección de un defecto es mucho menor. Revisar requisitos, acordar criterios de aceptación y automatizar pruebas desde el inicio previene defectos en vez de encontrarlos al final.',
    '{"metodo": "TECNICA", "criterios": ["Define shift-left como adelantar las actividades de calidad", "Relaciona la detección temprana con un menor costo de corrección", "Propone revisión de requisitos y criterios de aceptación", "Distingue shift-left de shift-right o del monitoreo en producción"], "palabras_clave": ["shift-left", "etapas tempranas", "costo de corrección", "requisitos", "criterios de aceptación"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La idea es probar antes, no más al final. Si QA participa cuando se definen las historias y las pruebas se automatizan junto con el código, muchos defectos se evitan o aparecen cuando todavía son baratos de corregir.", "El enfoque desplaza la calidad hacia la izquierda de la línea de tiempo del proyecto: revisión de requisitos, análisis estático, pruebas unitarias y criterios claros desde el refinamiento. Más tiempo de pruebas al final o más monitoreo en producción no atacan la causa."], "sinonimos": [["shift-left", "pruebas tempranas"], ["defectos", "bugs"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste la medida que ataca la causa del problema y no solo sus síntomas al final del ciclo.", "medio": "Vas bien, pero distingue con más precisión entre acciones que previenen defectos temprano y acciones que solo los detectan más tarde.", "malo": "Repasa el concepto de shift-left y por qué el costo de corregir un defecto aumenta a medida que avanza el ciclo de desarrollo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'En tu organización la mayoría de los defectos se detecta en pruebas de sistema, justo antes de cada release, y corregirlos resulta caro. ¿Qué medida aplica mejor el enfoque shift-left?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Sumar a QA al refinamiento para revisar requisitos, acordar criterios y automatizar desde el inicio.', TRUE,
     'Correcto: adelanta las actividades de calidad a las etapas donde los defectos son más baratos de evitar.', 1),
    ('Agregar una semana extra de pruebas manuales al final de cada release para encontrar más defectos.', FALSE,
     'Sigue detectando tarde y encarece la entrega; no mueve la calidad hacia el inicio.', 2),
    ('Reforzar el monitoreo en producción para detectar los defectos apenas lleguen a manos de los usuarios.', FALSE,
     'Es una práctica de shift-right; aporta valor, pero detecta los defectos cuando ya llegaron al usuario.', 3),
    ('Contratar más testers para la etapa de pruebas de sistema y así acortar ese período final.', FALSE,
     'Acelera la misma etapa tardía, pero no previene defectos ni reduce su costo de corrección.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'En tu organización la mayoría de los defectos se detecta en pruebas de sistema, justo antes de cada release, y corregirlos resulta caro. ¿Qué medida aplica mejor el enfoque shift-left?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La eficiencia de remoción de defectos, o DRE, divide los defectos encontrados antes de liberar por el total de defectos, sumando los que se detectaron en producción. Mide directamente la efectividad del proceso, mientras que la fuga de defectos muestra cuántos escapan a los usuarios.',
    '{"metodo": "TECNICA", "criterios": ["Propone una métrica de efectividad y no de volumen o esfuerzo", "Explica el cálculo de la eficiencia de remoción de defectos", "Considera los defectos escapados a producción", "Advierte sobre métricas que generan incentivos perversos"], "palabras_clave": ["DRE", "eficiencia de remoción", "fuga de defectos", "producción", "efectividad"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Usaría la eficiencia de remoción de defectos: de todos los defectos conocidos, qué porcentaje encontró QA antes de la salida. Contar casos, horas o bugs por persona mide actividad, no resultados, y puede incentivar malas prácticas.", "La métrica clave es cuántos defectos se escapan a producción en proporción al total. Si ese porcentaje baja release tras release, el proceso de pruebas está siendo más efectivo."], "sinonimos": [["DRE", "eficiencia de remoción de defectos", "defect removal efficiency"], ["fuga de defectos", "defect leakage", "defectos escapados"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste una métrica que mide resultados del proceso y no solo la actividad del equipo.", "medio": "Vas bien, pero revisa si la métrica que propones refleja de verdad la efectividad o solo el volumen de trabajo realizado.", "malo": "Repasa las métricas de calidad más usadas en QA y la diferencia entre medir esfuerzo, volumen y efectividad del proceso de pruebas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La gerencia te pide una métrica para evaluar qué tan efectivo es el proceso de pruebas para detectar defectos antes de producción. ¿Cuál propones?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('La cantidad de casos de prueba escritos y ejecutados por cada tester durante el sprint.', FALSE,
     'Mide volumen de trabajo, no si el proceso logra detectar los defectos a tiempo.', 1),
    ('El número de bugs reportados por cada tester, para premiar a quien reporte más.', FALSE,
     'Genera incentivos perversos, como reportes duplicados o triviales, y no mide efectividad.', 2),
    ('El total de horas dedicadas a probar en cada release, comparado entre equipos.', FALSE,
     'Mide esfuerzo invertido; más horas no significan que se detecten más defectos importantes.', 3),
    ('El porcentaje de defectos hallados antes de producción respecto del total conocido.', TRUE,
     'Correcto: es la eficiencia de remoción de defectos, que considera también los defectos que escaparon.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'La gerencia te pide una métrica para evaluar qué tan efectivo es el proceso de pruebas para detectar defectos antes de producción. ¿Cuál propones?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las pruebas basadas en riesgo priorizan según la probabilidad de que algo falle y el impacto que tendría esa falla para los usuarios y el negocio. Se ejecutan primero los flujos críticos y las zonas que cambiaron en la release, y se informa explícitamente qué quedó sin probar.',
    '{"metodo": "TECNICA", "criterios": ["Aplica el enfoque de pruebas basadas en riesgo", "Combina probabilidad de falla e impacto", "Prioriza flujos críticos y zonas modificadas", "Comunica el riesgo residual de lo no probado"], "palabras_clave": ["riesgo", "probabilidad", "impacto", "flujos críticos", "sin probar"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Priorizo por riesgo: primero lo que más daño causaría si falla, como pagos o login, y lo que se modificó en esta versión. Lo que quede pendiente lo informo para que el negocio decida con conocimiento.", "Uso una matriz de riesgo que cruza qué tan probable es una falla y qué tan grave sería su efecto. Con eso ordeno la regresión y dejo al final los casos de bajo impacto y baja probabilidad."], "sinonimos": [["pruebas basadas en riesgo", "risk-based testing"], ["regresión", "pruebas de regresión"]], "feedback_generico": {"correcto": "¡Excelente! Usaste un criterio defendible ante el negocio y pensaste en cómo comunicar lo que queda pendiente.", "medio": "Vas bien, pero tu criterio considera solo una parte del problema; piensa también en las consecuencias de cada posible falla.", "malo": "Repasa las técnicas para priorizar pruebas cuando el tiempo no alcanza y cómo justificar esa priorización ante el negocio."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Faltan dos días para una release y no alcanzas a ejecutar toda la regresión manual. ¿Qué criterio usas para decidir qué casos ejecutar primero?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Los casos más rápidos de ejecutar, para completar la mayor cantidad antes de la fecha.', FALSE,
     'Maximiza la cantidad, no la protección: puede dejar sin probar los flujos más críticos.', 1),
    ('Los casos de mayor riesgo, combinando la probabilidad de falla con el impacto en el negocio.', TRUE,
     'Correcto: es el enfoque de pruebas basadas en riesgo, que concentra el esfuerzo donde una falla haría más daño.', 2),
    ('Los casos de los módulos que el equipo de QA conoce mejor, para avanzar con seguridad.', FALSE,
     'La familiaridad del equipo no indica dónde están los mayores riesgos para el negocio.', 3),
    ('Los casos en el orden en que fueron creados, empezando por los más antiguos del repositorio.', FALSE,
     'La antigüedad de un caso no tiene relación con su riesgo ni con los cambios de la release.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Testing y QA' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Faltan dos días para una release y no alcanzas a ejecutar toda la regresión manual. ¿Qué criterio usas para decidir qué casos ejecutar primero?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño la estrategia de pruebas partiendo del diagnóstico y del riesgo del negocio, no de las herramientas. 1) Levanto la situación actual: defectos en producción, módulos críticos, pruebas existentes y flujo del equipo. 2) Defino con negocio los riesgos principales y objetivos de calidad medibles, como reducir la fuga de defectos. 3) Establezco niveles y tipos de prueba siguiendo la pirámide, con responsables claros para cada capa. 4) Integro a QA desde el refinamiento y defino criterios de entrada, criterios de salida y una Definition of Done con pruebas. 5) Automatizo primero la regresión crítica en el pipeline de CI. 6) Mido avances con métricas acordadas y ajusto la estrategia en cada ciclo.',
    '{"metodo": "TECNICA", "criterios": ["Parte de un diagnóstico de la situación actual", "Alinea la estrategia con riesgos y objetivos del negocio", "Define niveles de prueba y responsabilidades", "Incorpora calidad temprana y criterios de salida", "Propone métricas y mejora continua"], "palabras_clave": ["estrategia de pruebas", "riesgos", "pirámide", "criterios de salida", "regresión crítica", "métricas"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Empezaría entendiendo dónde y por qué se escapan los defectos, revisando incidentes y hablando con el equipo. Con eso priorizaría los flujos de mayor riesgo, definiría qué se prueba en cada nivel y quién lo hace, y automatizaría la regresión de lo crítico. Además, acordaría criterios claros para liberar y métricas para evaluar si la estrategia funciona.", "Una estrategia útil responde qué probar, cómo, cuándo y quién. Yo la construiría con el negocio a partir de los riesgos, aplicando la pirámide de pruebas, incorporando QA desde la definición de historias y conectando las pruebas automatizadas al pipeline. La revisaría cada pocos sprints usando datos de defectos en producción."], "sinonimos": [["estrategia de pruebas", "test strategy"], ["Definition of Done", "definición de terminado", "DoD"], ["defectos", "bugs"]], "feedback_generico": {"correcto": "¡Muy bien! Tu estrategia parte del problema real, prioriza por riesgo y considera cómo medir si funciona.", "medio": "Tienes buenas ideas, pero tu estrategia necesita más estructura: diagnóstico inicial, responsables por nivel o forma de medir resultados.", "malo": "Repasa qué contiene una estrategia de pruebas y cómo se adapta a los riesgos del negocio y al ritmo de entregas del equipo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Llegas como líder de QA a un producto con releases cada dos semanas, sin estrategia de pruebas definida y con muchos defectos en producción. ¿Cómo diseñas la estrategia de pruebas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Mi rol es entregar información clara del riesgo para que la decisión se tome con datos, no imponerla. 1) Confirmo y acoto el defecto: qué usuarios afecta, con qué frecuencia y si existe una forma de evitarlo. 2) Estimo con desarrollo el impacto en clientes y negocio, y el tiempo real de corrección. 3) Presento opciones: retrasar, liberar sin la funcionalidad afectada mediante un feature flag, o liberar con un hotfix comprometido. 4) Propongo mitigaciones como monitoreo reforzado y un plan de rollback. 5) Dejo registrada la decisión y el riesgo aceptado por los responsables. 6) Después, analizo por qué el defecto apareció tan tarde.',
    '{"metodo": "TECNICA", "criterios": ["Acota el alcance e impacto del defecto con datos", "Presenta alternativas con sus riesgos al negocio", "Propone mitigaciones como feature flags, monitoreo o rollback", "Asume un rol de asesor y deja registrada la decisión", "Mantiene una comunicación profesional sin bloquear por reflejo"], "palabras_clave": ["riesgo", "impacto", "feature flag", "rollback", "hotfix", "mitigaciones"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No se trata de ganar una discusión, sino de que el Product Owner decida conociendo el riesgo. Mediría a cuántos usuarios afecta, cuánto costaría corregirlo ahora y qué alternativas existen, como desactivar esa parte con un feature flag. Si se decide liberar, pediría un plan de hotfix, monitoreo y que la aceptación del riesgo quede por escrito.", "Primero reuniría evidencia concreta del alcance del fallo en el pago. Luego plantearía escenarios con sus consecuencias: postergar, liberar parcialmente o liberar con mitigaciones y una reversión preparada. La decisión final es del negocio, pero debe quedar documentada, y después revisaría por qué no lo detectamos antes."], "sinonimos": [["defecto", "bug", "fallo"], ["rollback", "reversión"], ["feature flag", "feature toggle"]], "feedback_generico": {"correcto": "¡Excelente! Actuaste como asesor del riesgo, ofreciste alternativas realistas y cuidaste la trazabilidad de la decisión.", "medio": "Vas bien, pero tu respuesta se queda en aprobar o bloquear; piensa en qué alternativas y mitigaciones podrías ofrecer al negocio.", "malo": "Repasa cómo se comunica el riesgo de calidad a los stakeholders y qué opciones existen para liberar de forma controlada."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'El día de la release encuentras un defecto que impide a algunos usuarios completar el pago, y el Product Owner quiere liberar igual por compromisos comerciales. ¿Cómo manejas la situación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Conduzco un análisis de causa raíz sin culpables, centrado en mejorar el proceso y no en las personas. 1) Reconstruyo la línea de tiempo: cuándo se introdujo, qué pruebas pasó y cuándo se detectó. 2) Aplico una técnica como los 5 porqués o un diagrama de Ishikawa para llegar a la causa raíz. 3) Identifico en qué etapa pudo detectarse: requisitos, revisión de código, pruebas o monitoreo. 4) Agrego una prueba de regresión automatizada que reproduzca el defecto. 5) Defino acciones correctivas con responsables y plazos, y comparto los aprendizajes con todos los equipos. 6) Hago seguimiento con métricas de fuga de defectos.',
    '{"metodo": "TECNICA", "criterios": ["Realiza un análisis sin culpables", "Usa una técnica de causa raíz como los 5 porqués", "Identifica en qué etapa pudo detectarse el defecto", "Agrega una prueba de regresión que lo cubra", "Define acciones con responsables y seguimiento"], "palabras_clave": ["causa raíz", "5 porqués", "sin culpables", "prueba de regresión", "acciones correctivas"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Organizaría un postmortem enfocado en el proceso, no en las personas. Reconstruiría cómo el defecto pasó todas las barreras, buscaría la causa raíz con preguntas sucesivas y decidiría qué control habría podido detectarlo antes. Luego automatizaría una prueba para ese caso y daría seguimiento a las mejoras acordadas.", "Lo primero es entender la cadena completa: el cambio que lo introdujo, las pruebas que no lo encontraron y por qué. Con un análisis de causa raíz propondría mejoras concretas en requisitos, pruebas o monitoreo, asignaría responsables y verificaría con métricas si los defectos escapados disminuyen."], "sinonimos": [["análisis de causa raíz", "RCA", "root cause analysis"], ["sin culpables", "blameless"], ["análisis posterior", "postmortem", "post mortem"]], "feedback_generico": {"correcto": "¡Muy bien! Condujiste el análisis hacia la causa raíz y planteaste acciones concretas para evitar que el problema se repita.", "medio": "Tienes una buena base, pero te faltó profundizar en la causa raíz o en cómo asegurar que las acciones se cumplan.", "malo": "Repasa cómo se hace un análisis de causa raíz y un postmortem sin culpables, y qué acciones evitan que un defecto se repita."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un defecto grave llegó a producción y afectó a muchos clientes. Como líder de QA, ¿cómo conduces el análisis posterior para que no se repita?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Testing y QA'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- ── CIBERSEGURIDAD ──────────────────────────────────────────────────────────

-- Seguridad de aplicaciones (OWASP)

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La defensa principal contra la inyección SQL son las consultas parametrizadas o prepared statements, donde la entrada del usuario se envía como parámetro y no se concatena al texto SQL. Así el motor nunca interpreta esos datos como código. Se complementa con validación en el servidor y con un usuario de base de datos con privilegios mínimos.',
    '{"metodo": "TECNICA", "criterios": ["Identifica las consultas parametrizadas como defensa principal", "Explica que separan el código SQL de los datos", "Reconoce que la validación en el cliente se puede saltar", "Distingue medidas complementarias de la defensa principal"], "palabras_clave": ["consultas parametrizadas", "prepared statements", "inyección SQL", "concatena", "parámetro", "privilegios mínimos"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Lo correcto es no armar la consulta concatenando texto del usuario, sino usar marcadores de posición y pasar los valores como parámetros. De esa forma la base de datos distingue siempre entre la instrucción y los datos.", "Se previene usando sentencias preparadas, ya sea directamente o a través de un ORM que parametrice las consultas. Ocultar errores o validar en el frontend ayuda poco, porque no elimina la causa del problema."], "sinonimos": [["consultas parametrizadas", "prepared statements", "sentencias preparadas"], ["inyección SQL", "SQL injection", "SQLi"]], "feedback_generico": {"correcto": "¡Muy bien! Identificaste la defensa de fondo contra la inyección SQL y no te quedaste en medidas superficiales.", "medio": "Vas por buen camino, pero te faltó distinguir la defensa principal de las medidas que solo dificultan el ataque. Repasa cómo se construye una consulta segura.", "malo": "No te preocupes, es un tema clave. Repasa cómo se arma una consulta SQL con datos del usuario y por qué concatenar texto es peligroso."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En una aplicación web, ¿cuál es la medida principal para prevenir la inyección SQL en las consultas que usan datos ingresados por el usuario?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Enviar los datos del formulario por POST en lugar de GET para que no queden en la URL.', FALSE,
     'El método HTTP no cambia cómo se arma la consulta; un atacante puede enviar el mismo contenido malicioso por POST.', 1),
    ('Usar consultas parametrizadas (prepared statements) que separan el código SQL de los datos.', TRUE,
     'Con parámetros, el motor trata la entrada siempre como dato y nunca como parte del código SQL, lo que neutraliza la inyección.', 2),
    ('Ocultar los mensajes de error de la base de datos para que el atacante no vea ningún detalle.', FALSE,
     'Es una buena práctica, pero solo dificulta la explotación; la vulnerabilidad sigue existiendo, por ejemplo como inyección a ciegas.', 3),
    ('Validar con JavaScript en el navegador que los campos no contengan comillas simples.', FALSE,
     'La validación en el cliente se salta enviando la petición directo al servidor y, además, filtrar comillas no cubre todos los casos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En una aplicación web, ¿cuál es la medida principal para prevenir la inyección SQL en las consultas que usan datos ingresados por el usuario?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las contraseñas se almacenan como hash con una función lenta diseñada para ese fin, como bcrypt o Argon2, que agrega una sal única por usuario. El hash es de un solo sentido, a diferencia del cifrado, que es reversible con la llave. El costo de cómputo configurable hace que un ataque de fuerza bruta sobre una base filtrada sea muy caro.',
    '{"metodo": "TECNICA", "criterios": ["Distingue hashing (un solo sentido) de cifrado (reversible)", "Menciona un algoritmo lento para contraseñas como bcrypt o Argon2", "Explica el rol de la sal", "Descarta codificaciones como Base64 como protección"], "palabras_clave": ["hash", "sal", "bcrypt", "Argon2", "fuerza bruta", "cifrado"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Nunca se guardan en texto plano ni cifradas, sino como hash con sal usando un algoritmo resistente a fuerza bruta como Argon2 o bcrypt. Al iniciar sesión se vuelve a calcular el hash de lo ingresado y se compara.", "Lo correcto es usar una función de derivación de contraseñas con sal y factor de trabajo. Así, aunque roben la base de datos, recuperar cada contraseña cuesta mucho tiempo y contraseñas iguales no generan el mismo resultado."], "sinonimos": [["sal", "salt"], ["hash", "resumen criptográfico"]], "feedback_generico": {"correcto": "¡Excelente! Tienes clara la diferencia entre proteger una contraseña y simplemente esconderla.", "medio": "Bien, reconoces que no basta con ocultar la contraseña, pero revisa por qué la velocidad del algoritmo y la sal importan tanto.", "malo": "Calma, es una confusión común. Repasa la diferencia entre codificar, cifrar y hashear, y qué algoritmos se diseñaron para contraseñas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Cuál es la forma recomendada de almacenar las contraseñas de los usuarios en la base de datos de una aplicación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Cifrarlas con AES y guardar la llave en un archivo de configuración del mismo servidor.', FALSE,
     'El cifrado es reversible: si el atacante obtiene la llave, recupera todas las contraseñas. Además, una contraseña nunca necesita descifrarse.', 1),
    ('Aplicarles SHA-256 una sola vez, sin sal, para que la verificación sea rápida.', FALSE,
     'SHA-256 es muy rápido, lo que facilita la fuerza bruta y los ataques de diccionario; sin sal, además, contraseñas iguales producen el mismo hash.', 2),
    ('Guardar un hash generado con un algoritmo lento y con sal, como bcrypt o Argon2.', TRUE,
     'Son funciones diseñadas para contraseñas: usan una sal única y un costo configurable que encarece los ataques de fuerza bruta.', 3),
    ('Codificarlas en Base64 para que no se puedan leer a simple vista en la base de datos.', FALSE,
     'Base64 es una codificación, no una protección: cualquiera puede decodificarla al instante.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Cuál es la forma recomendada de almacenar las contraseñas de los usuarios en la base de datos de una aplicación?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'HTTPS usa TLS para proteger los datos en tránsito entre el cliente y el servidor: el cifrado da confidencialidad y los controles de integridad detectan cualquier modificación. Además, el certificado permite al navegador verificar la identidad del servidor. No protege los datos en reposo ni corrige vulnerabilidades del código como la inyección o el XSS.',
    '{"metodo": "TECNICA", "criterios": ["Indica que protege los datos en tránsito", "Menciona confidencialidad e integridad", "Explica el rol del certificado en la autenticación del servidor", "Reconoce que no protege datos en reposo ni fallas del código"], "palabras_clave": ["TLS", "datos en tránsito", "certificado", "cifrado", "integridad"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["HTTPS protege el canal: cifra el tráfico para evitar que alguien lo intercepte o lo altere, por ejemplo en una red wifi pública. El certificado digital confirma que el sitio es quien dice ser.", "Su función es asegurar la comunicación en la red mediante TLS, garantizando confidencialidad, integridad y autenticación del servidor. La seguridad de la base de datos y del código se resuelve con otros controles."], "sinonimos": [["datos en tránsito", "datos en movimiento"], ["certificado", "certificado digital"]], "feedback_generico": {"correcto": "¡Muy bien! Tienes claro qué protege HTTPS y también cuáles son sus límites.", "medio": "Vas bien, pero revisa con más precisión qué parte del recorrido de los datos protege HTTPS y qué riesgos quedan fuera de su alcance.", "malo": "No pasa nada. Repasa qué hace TLS durante la comunicación entre el navegador y el servidor, y qué riesgos quedan fuera de su alcance."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué protege principalmente HTTPS (HTTP sobre TLS) en una aplicación web?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('La confidencialidad e integridad de los datos en tránsito y la identidad del servidor.', TRUE,
     'TLS cifra la comunicación, detecta modificaciones y, mediante el certificado, permite verificar que hablas con el servidor legítimo.', 1),
    ('Los datos almacenados en la base de datos del servidor frente a cualquier acceso no autorizado.', FALSE,
     'HTTPS protege los datos mientras viajan por la red; los datos en reposo requieren otros controles, como cifrado en la base y control de acceso.', 2),
    ('La aplicación frente a ataques de inyección SQL y XSS enviados por formularios.', FALSE,
     'Un contenido malicioso viaja igual de bien por un canal cifrado; esas vulnerabilidades se corrigen en el código.', 3),
    ('La privacidad del usuario, ocultando su dirección IP frente al servidor web.', FALSE,
     'El servidor sigue viendo la IP del cliente; HTTPS no es una herramienta de anonimato.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Qué protege principalmente HTTPS (HTTP sobre TLS) en una aplicación web?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'XSS ocurre cuando la aplicación muestra datos no confiables sin escaparlos y el navegador de la víctima ejecuta un script inyectado. 1) Distingo los tipos: el reflejado viene en la petición, como un parámetro de la URL, y el almacenado queda guardado, por ejemplo en un comentario. 2) Aplico escape de salida según el contexto: HTML, atributo, JavaScript o URL. 3) Uso frameworks con auto-escape y evito insertar HTML crudo con innerHTML o equivalentes. 4) Valido la entrada en el servidor y sanitizo con una librería probada si debo aceptar HTML. 5) Agrego una Content Security Policy y cookies HttpOnly para reducir el impacto si algo se escapa.',
    '{"metodo": "TECNICA", "criterios": ["Define XSS como la ejecución de scripts inyectados en el navegador de la víctima", "Diferencia XSS reflejado de almacenado", "Propone escape de salida según el contexto", "Menciona frameworks con auto-escape o evitar innerHTML", "Agrega defensas complementarias como CSP o cookies HttpOnly"], "palabras_clave": ["escape de salida", "almacenado", "reflejado", "Content Security Policy", "HttpOnly", "sanitizar"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Es una falla en la que un atacante logra que la página muestre código JavaScript suyo, que se ejecuta con los permisos del usuario y puede robar su sesión. La principal defensa es codificar toda salida dinámica según dónde se inserte, apoyándose en el motor de plantillas, y sumar CSP como segunda capa.", "Cross-Site Scripting permite inyectar scripts que corren en el navegador de otros usuarios, ya sea porque se reflejan desde la URL o porque quedaron guardados en la base de datos. Lo prevengo escapando los datos al mostrarlos, sin usar HTML crudo, sanitizando lo que acepto y marcando las cookies de sesión como HttpOnly."], "sinonimos": [["escape de salida", "codificación de salida", "output encoding"], ["CSP", "Content Security Policy"], ["sanitizar", "sanitizo", "limpiar la entrada"]], "feedback_generico": {"correcto": "¡Muy buena respuesta! Explicaste el problema con claridad y propusiste defensas en varias capas.", "medio": "Bien, entiendes la idea general, pero te faltó profundizar en los tipos de XSS o en la defensa principal según el contexto de salida.", "malo": "Vamos de a poco: repasa cómo un dato ingresado por un usuario termina ejecutándose en el navegador de otro y qué significa escapar la salida."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es una vulnerabilidad XSS (Cross-Site Scripting) y qué medidas aplicarías para prevenirla en una aplicación web?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El OWASP Top 10 es un documento de referencia que agrupa los riesgos de seguridad más críticos en aplicaciones web y se actualiza periódicamente. 1) Lo uso para conocer las categorías clave, como control de acceso roto, fallas criptográficas, inyección y configuración insegura. 2) Lo aplico como checklist al diseñar y revisar código, preguntándome si mi funcionalidad cae en alguna categoría. 3) Lo uso en code reviews y al definir historias de usuario con criterios de seguridad. 4) Lo combino con herramientas automáticas que relacionan sus hallazgos con estas categorías. 5) Recuerdo que es una base de concientización, no una lista completa ni un estándar de verificación.',
    '{"metodo": "TECNICA", "criterios": ["Explica que es una lista de riesgos críticos en aplicaciones web mantenida por OWASP", "Nombra al menos dos categorías correctamente", "Propone usos prácticos en diseño, código o revisiones", "Reconoce que es una base de concientización y no una cobertura completa"], "palabras_clave": ["OWASP", "control de acceso", "inyección", "configuración insegura", "code review", "fallas criptográficas"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Es una lista publicada por la fundación OWASP con las categorías de vulnerabilidades web más relevantes, construida con datos de la industria. En el día a día me sirve como guía para revisar que mi código no tenga fallas típicas, como inyección o problemas de autorización.", "Es un ranking de riesgos que sirve para crear conciencia sobre seguridad en el desarrollo. Lo usaría para capacitarme, para revisar pull requests y para priorizar qué probar, entendiendo que para verificar a fondo existen guías más detalladas, como el ASVS."], "sinonimos": [["OWASP Top 10", "Top 10 de OWASP"], ["code review", "revisión de código"]], "feedback_generico": {"correcto": "¡Bien hecho! Mostraste que conoces el documento y, sobre todo, cómo llevarlo a la práctica en tu trabajo.", "medio": "Vas bien, pero te faltó nombrar categorías concretas o explicar cómo lo aplicarías en el día a día.", "malo": "No te desanimes. Repasa las categorías principales del documento y piensa en qué momento del desarrollo podrías usarlo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es el OWASP Top 10 y cómo lo usarías en tu trabajo diario como desarrollador?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Trato la contraseña como comprometida desde el momento en que se subió, aunque el archivo se borre después. 1) Aviso de inmediato al líder técnico o al equipo de seguridad. 2) Debo rotar la credencial de inmediato: genero una nueva y actualizo los servicios que la usan. 3) Reviso los logs de acceso a la base de datos para detectar uso indebido. 4) Elimino el secreto del repositorio y de su historial, porque un commit nuevo no lo borra de los commits anteriores. 5) Muevo los secretos a variables de entorno o a un gestor de secretos y agrego el archivo al .gitignore. 6) Activo un escaneo de secretos en pre-commit o en el pipeline para evitar que se repita.',
    '{"metodo": "TECNICA", "criterios": ["Considera la credencial comprometida y la rota de inmediato", "Sabe que borrar el archivo no la elimina del historial de Git", "Revisa los logs en busca de accesos indebidos", "Propone variables de entorno o un gestor de secretos", "Sugiere escaneo de secretos para prevenir"], "palabras_clave": ["rotar", "historial", "gestor de secretos", "variables de entorno", "escaneo de secretos"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Lo primero es cambiar esa contraseña, porque cualquiera con acceso al repositorio, o a un clon, pudo copiarla. Después limpio el historial, reviso si hubo accesos sospechosos y dejo la configuración sensible fuera del código, en un vault o en variables del entorno de despliegue.", "Escalo el incidente, invalido la credencial filtrada y reviso la actividad de la base de datos. Para que no vuelva a ocurrir, el equipo debe usar un gestor de secretos y herramientas que detecten contraseñas antes de hacer push."], "sinonimos": [["rotar", "cambiar", "renovar"], ["gestor de secretos", "vault", "bóveda de secretos"]], "feedback_generico": {"correcto": "¡Excelente! Priorizaste bien: contener el riesgo primero y luego prevenir que se repita.", "medio": "Vas bien, pero te faltó algún paso clave, como la urgencia de invalidar la credencial o qué pasa con el historial del repositorio.", "malo": "Calma, es una situación común. Repasa qué significa que un secreto esté comprometido y por qué no basta con borrar el archivo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un compañero subió por error al repositorio Git del equipo un archivo con la contraseña de la base de datos de producción. ¿Qué harías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La defensa clásica contra CSRF es el token anti-CSRF: un valor impredecible asociado a la sesión que se incluye en cada formulario o petición que cambia datos y que el servidor valida. Un sitio malicioso puede hacer que el navegador envíe la cookie, pero no puede conocer el token. Se complementa con el atributo SameSite en las cookies de sesión.',
    '{"metodo": "TECNICA", "criterios": ["Explica que en CSRF el navegador envía la cookie automáticamente", "Identifica el token anti-CSRF validado en el servidor", "Menciona SameSite como defensa complementaria", "Descarta HTTPS, POST o CORS como solución por sí solos"], "palabras_clave": ["token anti-CSRF", "SameSite", "cookies de sesión", "impredecible"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Se mitiga con el patrón de token sincronizador: el servidor genera un token secreto por sesión, lo incluye en el formulario y rechaza cualquier petición que no lo traiga correcto. Como el atacante no puede leer ese valor, no puede forjar la solicitud.", "Lo más efectivo es exigir un token CSRF en cada operación que modifica datos y, además, marcar la cookie de sesión con SameSite Lax o Strict. Usar POST, HTTPS o CORS no basta, porque no evita que el navegador adjunte la cookie."], "sinonimos": [["token anti-CSRF", "token CSRF", "token sincronizador"], ["CSRF", "Cross-Site Request Forgery", "falsificación de petición en sitios cruzados"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes por qué el navegador es el que facilita este ataque y cuál es la defensa adecuada.", "medio": "Vas bien, pero revisa por qué algunas medidas de seguridad generales no detienen este ataque en particular.", "malo": "No te preocupes. Repasa cómo funciona un ataque CSRF paso a paso, especialmente qué hace el navegador con las cookies."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Una aplicación web usa cookies de sesión y quieres protegerla contra CSRF (Cross-Site Request Forgery). ¿Qué medida es la más efectiva?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Exigir que todas las acciones que cambian datos se hagan solo con el método POST.', FALSE,
     'Un sitio malicioso también puede enviar un formulario POST de forma automática; el método por sí solo no evita CSRF.', 1),
    ('Forzar HTTPS en todo el sitio y activar HSTS en la configuración del servidor web.', FALSE,
     'HTTPS protege el canal, pero el navegador igual adjunta la cookie de sesión en la petición forjada.', 2),
    ('Configurar CORS para que solo el dominio propio pueda leer las respuestas de la API.', FALSE,
     'CORS controla qué orígenes pueden leer respuestas; no impide que el navegador envíe peticiones simples, como un formulario, con la cookie.', 3),
    ('Usar un token anti-CSRF impredecible ligado a la sesión y validarlo en el servidor.', TRUE,
     'El sitio atacante no conoce el token, así que su petición forjada es rechazada. Se complementa con cookies SameSite.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Una aplicación web usa cookies de sesión y quieres protegerla contra CSRF (Cross-Site Request Forgery). ¿Qué medida es la más efectiva?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Es un IDOR, una forma de control de acceso roto: el servidor entrega el recurso solo porque existe, sin verificar que pertenezca a quien lo pide. La corrección de fondo es validar la autorización en el servidor en cada petición, por ejemplo filtrando la consulta por el cliente autenticado. Usar UUID ayuda como defensa adicional, pero no reemplaza esa verificación.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la vulnerabilidad como IDOR o control de acceso roto", "Propone validar en el servidor que el recurso pertenezca al usuario", "Explica que ocultar o aleatorizar los IDs no es la solución de fondo", "Diferencia IDOR de la inyección y de CSRF"], "palabras_clave": ["IDOR", "control de acceso", "autorización", "servidor", "cada petición"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Es una referencia directa insegura a objetos. El backend debe comprobar, en cada solicitud, que el usuario de la sesión tiene permiso sobre esa factura específica, y responder con un error 403 o 404 si no lo tiene.", "Se trata de control de acceso roto: la autenticación funciona, pero falta la autorización a nivel de objeto. La solución es aplicar esa regla en el servidor de forma centralizada; cambiar el formato del identificador solo hace más difícil adivinarlo."], "sinonimos": [["IDOR", "referencia directa insegura a objetos", "Insecure Direct Object Reference"], ["control de acceso roto", "broken access control"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste la vulnerabilidad y elegiste la corrección que ataca la causa, no solo el síntoma.", "medio": "Bien, reconoces el tipo de problema, pero revisa si la solución que elegiste realmente impide el acceso o solo lo hace más difícil.", "malo": "Calma, es un error muy común en APIs. Repasa la diferencia entre autenticación y autorización a nivel de cada recurso."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un usuario autenticado cambia la URL /api/facturas/1001 por /api/facturas/1002 y obtiene la factura de otro cliente. ¿Qué vulnerabilidad es y cuál es la corrección de fondo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Inyección SQL; se corrige usando consultas parametrizadas en el endpoint de facturas.', FALSE,
     'No se está inyectando código; el usuario solo pide otro identificador válido que el servidor entrega sin verificar permisos.', 1),
    ('IDOR (control de acceso roto); validar en el servidor que la factura sea del usuario.', TRUE,
     'Es una referencia directa insegura a objetos: la corrección es verificar la autorización sobre cada recurso en cada petición.', 2),
    ('IDOR (control de acceso roto); cambiar los IDs numéricos por UUID difíciles de adivinar.', FALSE,
     'Ocultar los IDs dificulta adivinarlos, pero si un UUID se filtra el acceso sigue abierto; no corrige la falta de autorización.', 3),
    ('CSRF; agregar un token anti-CSRF a todas las peticiones que consultan facturas.', FALSE,
     'En CSRF el atacante fuerza acciones en nombre de la víctima; aquí es el propio usuario quien accede a datos ajenos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un usuario autenticado cambia la URL /api/facturas/1001 por /api/facturas/1002 y obtiene la factura de otro cliente. ¿Qué vulnerabilidad es y cuál es la corrección de fondo?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un JWT es confiable solo si el servidor verifica su firma con un algoritmo fijo definido por el propio servidor y valida claims como exp y, si corresponde, iss y aud. El payload de un JWT firmado no está cifrado, por lo que no debe contener datos sensibles. Además, conviene una expiración corta con tokens de refresco.',
    '{"metodo": "TECNICA", "criterios": ["Indica que la firma se verifica en el servidor", "Fija el algoritmo aceptado y rechaza ''none''", "Valida la expiración y otros claims", "Sabe que el payload de un JWT firmado es legible", "Propone expiración corta con tokens de refresco"], "palabras_clave": ["firma", "expiración", "algoritmo", "payload", "claims", "tokens de refresco"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La práctica correcta es validar siempre el token en el backend: comprobar la firma con la clave esperada, exigir el algoritmo configurado y rechazar los vencidos. Nunca se debe confiar en lo que el propio token diga sobre cómo validarlo.", "El servidor tiene que verificar la integridad del JWT y su vigencia antes de usar su contenido. Como cualquiera puede decodificar el payload, ahí solo van identificadores y roles, no secretos."], "sinonimos": [["JWT", "JSON Web Token"], ["expiración", "vencimiento"], ["tokens de refresco", "refresh token"]], "feedback_generico": {"correcto": "¡Muy bien! Tienes claro qué debe validar el servidor antes de confiar en un token.", "medio": "Vas bien, pero revisa qué información de un JWT es realmente secreta y quién decide cómo se valida.", "malo": "No te desanimes. Repasa la estructura de un JWT (header, payload y firma) y qué significa que esté firmado y no cifrado."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu API usa JWT para autenticar a los usuarios. ¿Cuál de estas prácticas es correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Guardar en el payload datos sensibles, como la contraseña, porque el token viaja cifrado.', FALSE,
     'Un JWT firmado no está cifrado: su payload está codificado en Base64URL y cualquiera puede leerlo.', 1),
    ('Aceptar el algoritmo que indica el header del token, incluso ''none'', por compatibilidad.', FALSE,
     'Si el servidor confía en el algoritmo que declara el token, un atacante puede enviar un token sin firma o con un algoritmo distinto.', 2),
    ('Verificar la firma en el servidor con un algoritmo fijo y validar la expiración (exp).', TRUE,
     'El servidor decide qué algoritmo acepta, comprueba la firma y rechaza tokens vencidos; así el token no puede alterarse ni usarse indefinidamente.', 3),
    ('Emitir tokens sin expiración para no obligar al usuario a iniciar sesión otra vez.', FALSE,
     'Un token robado sin expiración sirve para siempre; lo recomendado es una vida corta con tokens de refresco.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Tu API usa JWT para autenticar a los usuarios. ¿Cuál de estas prácticas es correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Le explicaría que CORS no protege la API, sino que define qué orígenes pueden leer sus respuestas desde un navegador, y que abrirlo a todos tiene riesgos. 1) Configuro una lista blanca explícita con los orígenes del frontend, separada por ambiente. 2) Nunca reflejo el header Origin sin validarlo, sobre todo si se envían cookies. 3) Activo Access-Control-Allow-Credentials solo si se usan cookies; el comodín * no es válido junto con credenciales. 4) Permito solo los métodos y headers necesarios y reviso la respuesta al preflight. 5) Recuerdo que CORS no reemplaza la autenticación ni la autorización, porque los clientes fuera del navegador lo ignoran.',
    '{"metodo": "TECNICA", "criterios": ["Explica que CORS controla qué orígenes pueden leer respuestas en el navegador", "Propone una lista blanca explícita de orígenes", "Advierte el riesgo de reflejar Origin cuando hay credenciales", "Limita métodos y headers y considera el preflight", "Aclara que CORS no reemplaza la autenticación ni la autorización"], "palabras_clave": ["lista blanca", "orígenes", "credenciales", "preflight", "header Origin"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Abrir CORS a cualquier origen es una mala idea; lo correcto es declarar exactamente qué dominios pueden llamar a la API y con qué métodos. Si la API usa cookies hay que tener especial cuidado, porque un origen malicioso que quede permitido podría leer datos del usuario.", "CORS es una relajación controlada de la política del mismo origen del navegador, no una barrera de seguridad del servidor. Configuraría los orígenes del frontend de forma explícita, sin comodines, y mantendría la autenticación y los permisos en el backend como siempre."], "sinonimos": [["lista blanca", "allowlist", "lista de permitidos"], ["preflight", "solicitud previa OPTIONS"], ["política del mismo origen", "Same-Origin Policy", "SOP"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste qué hace realmente CORS y propusiste una configuración restrictiva y bien justificada.", "medio": "Bien, propones restringir, pero te faltó explicar qué protege CORS realmente o el cuidado especial que exigen las credenciales.", "malo": "No te preocupes, CORS suele confundirse. Repasa la política del mismo origen del navegador y qué significan los headers Access-Control."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'El frontend, alojado en otro dominio, no puede consumir tu API por un error de CORS y alguien propone responder con Access-Control-Allow-Origin: * para todo. ¿Qué le dirías y cómo configurarías CORS correctamente?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Esto parece un ataque de credential stuffing o de fuerza bruta distribuida, así que combino varios controles, porque bloquear una sola IP no basta. 1) Aplico rate limiting por IP, por cuenta y global sobre el endpoint de login. 2) Uso bloqueo temporal progresivo por cuenta en lugar de bloqueos permanentes, que podrían usarse para negar el servicio a usuarios legítimos. 3) Incorporo MFA, que hace inútiles las contraseñas robadas. 4) Agrego un desafío anti-bots tras varios fallos y reviso la reputación de las IP. 5) Respondo con mensajes genéricos para no revelar si un usuario existe. 6) Genero alertas, vigilo las cuentas con un login exitoso tras muchos fallos y verifico si hay contraseñas filtradas.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce un ataque de credential stuffing o fuerza bruta distribuida", "Propone rate limiting por IP y por cuenta", "Prefiere bloqueos temporales o progresivos para no afectar a usuarios legítimos", "Incorpora MFA", "Usa mensajes genéricos y monitoreo con alertas"], "palabras_clave": ["rate limiting", "credential stuffing", "MFA", "bloqueo temporal", "fuerza bruta", "mensajes genéricos"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Es un relleno de credenciales con contraseñas filtradas de otros sitios. Limitaría la cantidad de intentos por cuenta y por origen, aumentaría el tiempo de espera tras cada fallo, pediría un segundo factor y activaría alertas para revisar los accesos exitosos sospechosos.", "Como el ataque está repartido en muchas IP, necesito límites en varias dimensiones y no solo bloqueo por dirección. Sumaría autenticación multifactor, desafíos para clientes automatizados, respuestas que no revelen si la cuenta existe y monitoreo en el SIEM."], "sinonimos": [["rate limiting", "limitación de tasa", "límite de intentos"], ["MFA", "autenticación multifactor", "segundo factor"], ["credential stuffing", "relleno de credenciales"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste el tipo de ataque y propusiste defensas en capas que no perjudican a los usuarios legítimos.", "medio": "Vas bien, pero te faltó considerar que el ataque viene desde muchas IP o que algunos bloqueos pueden afectar a usuarios legítimos.", "malo": "Calma, es un escenario frecuente. Repasa los controles contra ataques automatizados de login y piensa por qué bloquear una IP no es suficiente."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En los logs ves miles de intentos de inicio de sesión fallidos contra muchas cuentas distintas, provenientes de cientos de IP. ¿Qué controles implementarías en la aplicación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Registro lo necesario para detectar e investigar incidentes, sin convertir los logs en una nueva fuente de filtración. 1) Registro inicios de sesión exitosos y fallidos, cambios de contraseña o permisos, errores de autorización y acciones administrativas. 2) Incluyo fecha y hora, usuario, IP de origen, acción, resultado y un ID de correlación. 3) Nunca registro contraseñas, tokens, llaves de API, números de tarjeta ni datos personales completos, y enmascaro lo que sea necesario. 4) Neutralizo saltos de línea y caracteres de control en los datos del usuario para evitar la inyección de logs. 5) Centralizo los logs en un sistema protegido, con acceso restringido, control de integridad y retención definida.',
    '{"metodo": "TECNICA", "criterios": ["Identifica eventos relevantes como logins, cambios de permisos y errores de autorización", "Incluye contexto como usuario, IP, fecha y resultado", "Excluye contraseñas, tokens y datos sensibles o los enmascara", "Menciona el riesgo de inyección de logs", "Propone centralizar y proteger los logs"], "palabras_clave": ["datos personales", "enmascarar", "inyección de logs", "ID de correlación", "centralizar", "integridad"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Dejaría trazabilidad de quién hizo qué, cuándo y desde dónde en los eventos de autenticación, autorización y administración. Evitaría escribir secretos o información personal, y enviaría los registros a un servidor central donde nadie pueda borrarlos o alterarlos sin dejar rastro.", "Los logs deben servir para auditoría y respuesta a incidentes, así que registro eventos de seguridad con contexto suficiente. Las credenciales, los tokens y los datos de pago jamás se escriben, y los datos ingresados por usuarios se limpian antes de registrarlos para que no puedan falsificar entradas."], "sinonimos": [["logs", "registros", "bitácoras"], ["enmascarar", "enmascaro", "ofuscar"], ["centralizar", "centralizo", "sistema central"]], "feedback_generico": {"correcto": "¡Muy bien! Lograste un equilibrio entre registrar lo útil para investigar y proteger la información sensible.", "medio": "Bien, identificas eventos importantes, pero te faltó precisar qué datos nunca deben quedar en un log o cómo proteger los registros.", "malo": "No te desanimes. Repasa qué eventos de seguridad conviene registrar y piensa qué pasaría si alguien no autorizado leyera esos logs."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Te piden definir qué eventos de seguridad debe registrar una aplicación en sus logs. ¿Qué registrarías, qué evitarías registrar y cómo protegerías esos logs?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'SAST analiza el código fuente sin ejecutarlo, por lo que encaja temprano en el pipeline, en cada pull request, y entrega retroalimentación rápida al desarrollador. DAST prueba la aplicación en ejecución desde fuera, así que se corre contra un ambiente desplegado como staging. Son complementarios: cada uno detecta clases de fallas que el otro no ve.',
    '{"metodo": "TECNICA", "criterios": ["Explica que SAST analiza el código sin ejecutarlo", "Explica que DAST prueba la aplicación en ejecución", "Ubica cada análisis en la etapa correcta del pipeline", "Reconoce que son complementarios"], "palabras_clave": ["SAST", "DAST", "pipeline", "staging", "pull request", "código fuente"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El análisis estático revisa el código en busca de patrones inseguros y conviene ejecutarlo en cada cambio. El análisis dinámico prueba de forma controlada la aplicación ya desplegada en un ambiente de pruebas, por lo que se ejecuta más adelante en el pipeline.", "Lo ideal es usar ambos: SAST como control temprano y barato sobre el código, y DAST para validar el comportamiento real de la aplicación, incluida su configuración. Ninguno reemplaza por completo al otro."], "sinonimos": [["SAST", "análisis estático"], ["DAST", "análisis dinámico"], ["CI/CD", "integración y despliegue continuo"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes qué analiza cada herramienta y en qué etapa del pipeline aporta más valor.", "medio": "Vas bien, pero revisa qué necesita cada tipo de análisis para funcionar y qué tipo de fallas puede o no detectar.", "malo": "No te preocupes. Repasa la diferencia entre analizar código fuente y probar una aplicación en ejecución, y cómo encaja cada uno en CI/CD."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Estás diseñando los controles de seguridad del pipeline CI/CD de tu equipo. ¿Qué afirmación describe mejor cómo combinar SAST y DAST?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('SAST en cada pull request sobre el código y DAST contra staging, porque detectan fallas distintas.', TRUE,
     'SAST da retroalimentación temprana sobre el código fuente y DAST prueba la aplicación desplegada desde fuera, encontrando problemas de configuración y de ejecución.', 1),
    ('DAST en cada commit sobre el código fuente y SAST en producción, porque necesita tráfico real.', FALSE,
     'Está invertido: DAST requiere la aplicación en ejecución y SAST analiza el código sin ejecutarlo.', 2),
    ('Basta con DAST en staging, porque SAST solo reporta problemas de estilo y formato del código.', FALSE,
     'SAST detecta patrones de vulnerabilidad reales, como inyecciones o uso inseguro de criptografía, aunque puede generar falsos positivos.', 3),
    ('Basta con SAST, porque al revisar todo el código encuentra también los errores de configuración.', FALSE,
     'SAST no ve la configuración del servidor ni el comportamiento en ejecución, como los headers, TLS o la autenticación desplegada.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Estás diseñando los controles de seguridad del pipeline CI/CD de tu equipo. ¿Qué afirmación describe mejor cómo combinar SAST y DAST?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es evaluar el riesgo real: si la función vulnerable es alcanzable desde tu código, si el servicio está expuesto y si hay explotación conocida. Luego se actualiza o se fuerza la versión corregida de la dependencia transitiva y, si aún no existe parche, se aplican mitigaciones temporales y se documenta el riesgo. Ignorarla o apagar el escáner deja el riesgo abierto.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce que una dependencia transitiva también es un riesgo", "Evalúa la explotabilidad y la exposición en el contexto propio", "Propone actualizar o forzar la versión corregida", "Considera mitigaciones temporales si no hay parche", "Evita respuestas desproporcionadas o apagar controles"], "palabras_clave": ["dependencia transitiva", "versión corregida", "mitigaciones temporales", "explotación conocida", "alcanzable"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Primero confirmaría si la vulnerabilidad aplica: si el código vulnerable se usa y si el servicio está expuesto. Después actualizaría la dependencia directa que la trae, o fijaría la versión segura de la transitiva, y si no hay corrección, mitigaría con configuración o controles externos.", "La respuesta debe ser proporcional al riesgo: analizar impacto y explotabilidad, priorizar la corrección y dejar registro de la decisión. No corresponde ignorarla por ser indirecta ni detener a toda la organización."], "sinonimos": [["SCA", "análisis de composición de software"], ["dependencia transitiva", "dependencia indirecta"], ["parche", "versión corregida"]], "feedback_generico": {"correcto": "¡Excelente! Tomaste una decisión proporcional, basada en el riesgo real y sin apagar controles.", "medio": "Vas bien, pero revisa cómo equilibrar la urgencia de la corrección con el análisis del riesgo real en tu contexto.", "malo": "Calma, la gestión de dependencias es compleja. Repasa qué son las dependencias transitivas y cómo se prioriza una vulnerabilidad."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un escáner de dependencias (SCA) reporta una CVE crítica en una librería transitiva de un servicio en producción. ¿Cuál es la decisión más adecuada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Ignorarla, porque la librería es transitiva y tu código no la importa de forma directa.', FALSE,
     'Las dependencias transitivas se ejecutan igual dentro de tu aplicación y pueden ser explotables aunque no las importes.', 1),
    ('Desactivar el escáner en el pipeline hasta que el mantenedor de la librería publique un parche.', FALSE,
     'Apagar el control te deja ciego frente a esta y otras vulnerabilidades; el riesgo sigue ahí.', 2),
    ('Bloquear todos los despliegues de la empresa hasta eliminar todas las CVE de cualquier severidad.', FALSE,
     'Es desproporcionado y paraliza la operación; la remediación debe priorizarse según el riesgo.', 3),
    ('Evaluar si es explotable en tu contexto y forzar la versión corregida, o mitigar si no hay parche.', TRUE,
     'Priorizar por riesgo real, actualizar la transitiva (por ejemplo, con overrides de versión) y aplicar mitigaciones temporales es la respuesta proporcional.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un escáner de dependencias (SCA) reporta una CVE crítica en una librería transitiva de un servicio en producción. ¿Cuál es la decisión más adecuada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Alterar datos mientras viajan entre componentes corresponde a Tampering, la categoría de STRIDE que afecta la integridad. Se mitiga con controles de integridad en tránsito, como TLS mutuo entre servicios o la firma de los mensajes con HMAC o firmas digitales. Además, el servicio receptor debería validar las reglas de negocio del monto.',
    '{"metodo": "TECNICA", "criterios": ["Clasifica la amenaza como Tampering", "Relaciona la categoría con la propiedad de integridad", "Propone mTLS o la firma de los mensajes", "Diferencia Tampering de Spoofing, Repudiation y Denial of Service"], "palabras_clave": ["STRIDE", "Tampering", "integridad", "TLS mutuo", "HMAC", "firma"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["Es una amenaza de manipulación de datos. Para mitigarla, la comunicación entre microservicios debe ir autenticada y protegida, por ejemplo con mTLS, o el mensaje debe firmarse para que el receptor detecte cualquier modificación.", "En STRIDE corresponde a la categoría de manipulación, que rompe la integridad de la información. El control adecuado es asegurar el canal y la integridad del mensaje con mutual TLS o firmas criptográficas."], "sinonimos": [["Tampering", "manipulación", "alteración"], ["TLS mutuo", "mTLS", "mutual TLS"], ["modelado de amenazas", "threat modeling"]], "feedback_generico": {"correcto": "¡Muy bien! Clasificaste bien la amenaza y elegiste un control que protege justo la propiedad afectada.", "medio": "Vas bien, pero revisa qué propiedad de seguridad se ve afectada y si el control elegido la protege directamente.", "malo": "No te desanimes. Repasa las seis categorías de STRIDE y qué propiedad de seguridad amenaza cada una."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'En una sesión de modelado de amenazas con STRIDE identificas que un atacante podría alterar el monto de una transferencia mientras viaja entre dos microservicios. ¿Qué categoría es y qué control la mitiga mejor?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Spoofing (suplantación); exigir MFA a los usuarios finales al iniciar sesión.', FALSE,
     'La suplantación consiste en hacerse pasar por otra identidad; además, el MFA del usuario no protege el mensaje entre servicios.', 1),
    ('Tampering (manipulación); proteger la integridad con TLS mutuo o firmando los mensajes.', TRUE,
     'Alterar datos en tránsito es manipulación; mTLS o una firma como HMAC permiten detectar cualquier cambio.', 2),
    ('Repudiation (repudio); guardar logs de auditoría detallados de cada transferencia procesada.', FALSE,
     'El repudio es negar haber realizado una acción; los logs ayudan a investigar, pero no evitan que se altere el monto.', 3),
    ('Denial of Service (denegación); aplicar rate limiting entre los microservicios.', FALSE,
     'La denegación de servicio afecta la disponibilidad; limitar la tasa no protege la integridad del mensaje.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'En una sesión de modelado de amenazas con STRIDE identificas que un atacante podría alterar el monto de una transferencia mientras viaja entre dos microservicios. ¿Qué categoría es y qué control la mitiga mejor?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El riesgo principal es SSRF: un atacante podría usar el servidor para alcanzar recursos internos, como servicios de administración o el endpoint de metadatos de la nube. 1) Acepto solo los esquemas http y https y, si el negocio lo permite, una lista blanca de dominios. 2) Resuelvo el DNS y bloqueo IP privadas, de loopback y link-local, validando la IP a la que realmente me conecto para evitar DNS rebinding. 3) No sigo redirecciones o vuelvo a validar cada destino. 4) Ejecuto la descarga en un servicio aislado, con salida a red restringida y sin credenciales. 5) Limito tiempo, tamaño y tipo de contenido, y verifico que sea realmente una imagen. 6) Registro y monitoreo las peticiones rechazadas.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el riesgo de SSRF hacia recursos internos o metadatos de la nube", "Propone validar el esquema y una lista blanca de destinos", "Bloquea rangos de IP internas y considera DNS rebinding y redirecciones", "Aísla el componente que descarga y restringe su salida a red", "Limita tamaño, tiempo y tipo de contenido"], "palabras_clave": ["SSRF", "lista blanca", "IP privadas", "DNS rebinding", "metadatos", "redirecciones"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Esta funcionalidad abre la puerta a Server-Side Request Forgery, porque el servidor hace peticiones a destinos que elige el usuario. La diseñaría con un componente de descarga separado, sin acceso a la red interna, que valide la IP de destino después de resolver el nombre y que rechace redirecciones no verificadas.", "El peligro es que el servidor termine consultando direcciones internas en nombre del atacante. Mitigaría con validación estricta de destinos, filtrado de salida a nivel de red, límites de tamaño y tiempo, y verificación del contenido descargado, además de proteger el servicio de metadatos de la nube."], "sinonimos": [["SSRF", "Server-Side Request Forgery", "falsificación de peticiones del lado del servidor"], ["lista blanca", "allowlist", "lista de permitidos"]], "feedback_generico": {"correcto": "¡Excelente diseño! Identificaste el riesgo y lo mitigaste en varias capas, desde la validación hasta la red.", "medio": "Bien, ves el riesgo, pero te faltaron controles importantes, como los casos en que la validación de la URL se puede evadir o el aislamiento de red.", "malo": "Calma, es un riesgo menos conocido. Repasa qué pasa cuando un servidor hace peticiones a URLs elegidas por el usuario y qué recursos internos podría alcanzar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu aplicación permitirá que los usuarios ingresen una URL para que el servidor descargue desde internet su imagen de perfil. ¿Qué riesgo de seguridad ves y cómo diseñarías la funcionalidad para mitigarlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Trato todo archivo subido como no confiable y diseño controles desde la recepción hasta la descarga. 1) Valido el tipo real por su contenido, no solo por la extensión o el Content-Type, y uso una lista blanca de formatos. 2) Limito el tamaño y la cantidad de archivos para evitar abusos. 3) Guardo los archivos fuera de la raíz web, por ejemplo en un bucket privado, con nombres aleatorios generados por el servidor. 4) Los escaneo con antivirus y, si es posible, reproceso las imágenes para eliminar contenido incrustado. 5) Sirvo las descargas verificando la autorización, con URLs firmadas de corta duración y headers como Content-Disposition y X-Content-Type-Options. 6) Registro quién sube y quién descarga cada archivo.',
    '{"metodo": "TECNICA", "criterios": ["Valida el tipo de archivo por su contenido con una lista blanca", "Limita tamaño y cantidad", "Almacena fuera de la raíz web con nombres generados por el servidor", "Incluye escaneo de malware", "Controla la autorización en la descarga con URLs firmadas o headers seguros"], "palabras_clave": ["lista blanca", "raíz web", "bucket privado", "antivirus", "URLs firmadas", "Content-Disposition"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["No confío en la extensión ni en lo que declara el cliente: verifico el formato real, impongo límites y renombro los archivos. Los almaceno en un repositorio privado, separado del servidor de la aplicación, los analizo en busca de malware y solo los entrego a usuarios con permiso mediante enlaces temporales.", "Diseñaría la carga con validación estricta de formato y tamaño, almacenamiento aislado que nunca ejecute lo subido y escaneo antimalware. En la descarga verificaría los permisos en cada petición y enviaría headers que obliguen a descargar el archivo en lugar de interpretarlo en el navegador."], "sinonimos": [["lista blanca", "allowlist", "lista de permitidos"], ["URLs firmadas", "enlaces temporales", "presigned URL"], ["antivirus", "antimalware"]], "feedback_generico": {"correcto": "¡Muy bien! Cubriste el ciclo completo del archivo, desde que se recibe hasta que se descarga, con controles concretos.", "medio": "Vas bien, pero te faltó cubrir alguna etapa, como dónde se almacenan los archivos o cómo se controla quién los descarga.", "malo": "No te desanimes. Repasa por qué un archivo subido por un usuario puede ser peligroso y en qué etapas se puede proteger."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes diseñar una funcionalidad para que los usuarios suban documentos (PDF e imágenes) que luego otros usuarios autorizados pueden descargar. ¿Qué controles de seguridad incluirías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Priorizo según el riesgo real para el negocio y ataco las causas raíz, no solo los síntomas. 1) Valido y deduplico los hallazgos, y los agrupo por causa común. 2) Priorizo combinando severidad, facilidad de explotación, exposición a internet y criticidad de los datos o procesos afectados. 3) Corrijo primero lo crítico expuesto y, si algo no se puede corregir pronto, aplico mitigaciones temporales, como reglas de WAF o deshabilitar la función. 4) Acuerdo plazos con los dueños de cada sistema y formalizo la aceptación de riesgo cuando corresponde. 5) Para evitar recurrencias, incorporo controles sistémicos: librerías seguras, pruebas de regresión, reglas en el pipeline y capacitación. 6) Pido un re-test para verificar las correcciones.',
    '{"metodo": "TECNICA", "criterios": ["Valida, deduplica y agrupa los hallazgos por causa raíz", "Prioriza por riesgo considerando explotabilidad, exposición e impacto", "Propone mitigaciones temporales cuando no se puede corregir de inmediato", "Formaliza plazos y aceptación de riesgo con los responsables", "Previene recurrencias con controles sistémicos y verifica con un re-test"], "palabras_clave": ["causas raíz", "exposición", "mitigaciones temporales", "aceptación de riesgo", "re-test", "explotación"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["No ordenaría solo por el puntaje del informe: cruzaría la severidad con qué tan expuesto está cada sistema y qué datos maneja. Resolvería primero lo crítico y accesible desde internet, pondría controles compensatorios donde no alcance y dejaría por escrito los riesgos que el negocio decide asumir.", "Agruparía los hallazgos por patrón, porque muchos suelen venir de la misma práctica insegura, y así una corrección de fondo resuelve varios. Además de priorizar por impacto, sumaría controles automáticos y formación para que el equipo no repita esos errores, y validaría todo con una nueva prueba."], "sinonimos": [["pentest", "prueba de penetración", "test de intrusión"], ["mitigaciones temporales", "controles compensatorios"], ["re-test", "nueva prueba"]], "feedback_generico": {"correcto": "¡Excelente! Priorizaste con criterio de negocio y pensaste en cómo evitar que los problemas se repitan.", "medio": "Vas bien, pero te faltó considerar algún aspecto, como el contexto de cada hallazgo o qué hacer para que no vuelva a ocurrir.", "malo": "Calma, priorizar con recursos limitados es difícil. Repasa cómo se evalúa el riesgo de una vulnerabilidad más allá de su severidad técnica."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Recibes el informe de un pentest con decenas de hallazgos de distinta severidad y tu equipo tiene capacidad limitada este trimestre. ¿Cómo priorizas la remediación y cómo evitas que los mismos problemas vuelvan a aparecer?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de aplicaciones (OWASP)'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Seguridad de redes y SOC

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Se ve afectada la integridad, que garantiza que la información sea exacta y no se modifique sin autorización. La confidencialidad se refiere a que solo accedan personas autorizadas y la disponibilidad, a que el sistema esté accesible cuando se necesita. En este caso, el daño principal es que los montos ya no son confiables.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la integridad como el principio afectado", "Define correctamente los tres principios de la tríada", "Distingue alteración de divulgación y de indisponibilidad", "Reconoce que la autenticación no forma parte de la tríada"], "palabras_clave": ["integridad", "confidencialidad", "disponibilidad", "sin autorización", "confiables"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["El principio comprometido es la integridad, porque los datos fueron cambiados por alguien sin permiso. Si el atacante solo los hubiera leído, sería confidencialidad, y si hubiera dejado el sistema fuera de servicio, disponibilidad.", "Corresponde a la integridad de la información: los registros de ventas dejaron de reflejar la realidad. Para protegerla se usan controles de acceso, registros de auditoría y mecanismos de verificación como los hashes."], "sinonimos": [["tríada CIA", "tríada CID"], ["modificar", "alterar", "cambiar"]], "feedback_generico": {"correcto": "¡Muy bien! Relacionaste correctamente el tipo de daño con el principio de seguridad afectado.", "medio": "Vas bien, pero revisa con más cuidado qué le pasó a la información en el caso: ¿fue vista, alterada o dejó de estar accesible?", "malo": "No te preocupes. Repasa los tres principios de la tríada CIA y piensa en un ejemplo de ataque para cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un atacante modifica sin autorización los montos registrados en la base de datos de ventas de una empresa. ¿Qué principio de la tríada CIA se ve afectado principalmente?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Confidencialidad, porque el atacante logró acceder a información interna de la empresa.', FALSE,
     'Puede haber habido acceso, pero el daño principal descrito es la alteración de los datos, no su divulgación.', 1),
    ('Integridad, porque los datos fueron alterados y ya no son exactos ni confiables.', TRUE,
     'La integridad garantiza que la información no se modifique sin autorización; aquí los montos fueron cambiados.', 2),
    ('Disponibilidad, porque la base de datos de ventas dejó de funcionar para los usuarios.', FALSE,
     'El caso no indica que el sistema haya dejado de estar accesible; los datos siguen disponibles, pero alterados.', 3),
    ('Autenticación, porque el sistema no verificó correctamente la identidad del atacante.', FALSE,
     'La autenticación no es uno de los tres principios de la tríada CIA; es un mecanismo que ayuda a cumplirlos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Un atacante modifica sin autorización los montos registrados en la base de datos de ventas de una empresa. ¿Qué principio de la tríada CIA se ve afectado principalmente?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un IDS (sistema de detección de intrusos) analiza el tráfico, normalmente una copia, y genera alertas cuando detecta algo sospechoso, pero no lo detiene. Un IPS (sistema de prevención de intrusos) se ubica en línea con el tráfico y puede bloquearlo en tiempo real. Ambos se diferencian de un firewall tradicional, que filtra principalmente según reglas de IP, puerto y protocolo.',
    '{"metodo": "TECNICA", "criterios": ["Indica que el IDS detecta y alerta", "Indica que el IPS está en línea y puede bloquear", "Diferencia ambos del firewall tradicional", "Reconoce que ambos pueden ser de red o de host"], "palabras_clave": ["IDS", "IPS", "en línea", "bloquear", "alertas", "firewall"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["La diferencia está en la reacción: el IDS es pasivo y avisa al equipo de seguridad, mientras que el IPS es activo y descarta el tráfico malicioso. Por eso el IPS va en el camino de los paquetes y un error de configuración puede bloquear tráfico legítimo.", "El sistema de detección observa y notifica; el de prevención además actúa bloqueando conexiones o paquetes. Ambos complementan al firewall, que decide el paso del tráfico según reglas más simples."], "sinonimos": [["IDS", "sistema de detección de intrusos"], ["IPS", "sistema de prevención de intrusos"], ["en línea", "inline"]], "feedback_generico": {"correcto": "¡Muy bien! Tienes clara la diferencia entre detectar y prevenir, y dónde se ubica cada sistema.", "medio": "Vas bien, pero revisa con más cuidado el rol de cada sistema frente al tráfico malicioso y dónde se ubica en la red.", "malo": "No te desanimes. Repasa qué significa detectar frente a prevenir en seguridad de redes y cómo se diferencia cada uno de un firewall."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Cuál es la diferencia principal entre un IDS y un IPS en una red corporativa?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El IDS bloquea el tráfico malicioso en línea y el IPS solo se limita a generar alertas.', FALSE,
     'Está invertido: el IDS detecta y alerta, mientras que el IPS puede bloquear.', 1),
    ('El IPS cifra el tráfico entre sedes, mientras que el IDS filtra según IP y puerto.', FALSE,
     'Cifrar el tráfico entre sedes es función de una VPN, y filtrar por IP y puerto es lo típico de un firewall.', 2),
    ('El IPS se ubica en línea y puede bloquear tráfico malicioso; el IDS solo detecta y alerta.', TRUE,
     'El IDS suele analizar una copia del tráfico y avisa; el IPS está en el camino del tráfico y puede descartarlo en tiempo real.', 3),
    ('El IDS se instala solo en los servidores y el IPS solo en los equipos de los usuarios finales.', FALSE,
     'Ambos pueden ser de red o de host; la diferencia no está en dónde se instalan, sino en si solo detectan o también previenen.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Cuál es la diferencia principal entre un IDS y un IPS en una red corporativa?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo correcto es no hacer clic ni entregar datos, y reportar el correo al equipo de seguridad por el canal oficial, por ejemplo con el botón de reporte de phishing. La urgencia y la petición de credenciales son señales típicas de phishing. Si tienes dudas, verificas con soporte por un canal conocido, nunca respondiendo al mismo correo.',
    '{"metodo": "TECNICA", "criterios": ["Identifica señales de phishing como la urgencia y la petición de credenciales", "Evita hacer clic o entregar datos", "Reporta por el canal oficial de seguridad", "Verifica por un canal independiente y no respondiendo al correo"], "palabras_clave": ["phishing", "reportar", "canal oficial", "credenciales", "urgencia"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Es un caso típico de phishing: presión de tiempo y solicitud de contraseña. No se abre el enlace y se informa al área de seguridad para que bloquee el sitio y alerte a la organización.", "No interactúo con el mensaje y lo reporto con la herramienta o la casilla que la empresa definió para eso. Si quiero confirmar, llamo o escribo a soporte por los medios oficiales que ya conozco."], "sinonimos": [["phishing", "correo fraudulento", "suplantación de identidad"], ["credenciales", "usuario y contraseña"]], "feedback_generico": {"correcto": "¡Muy bien! Reconociste las señales de alerta y elegiste la acción que protege a toda la organización.", "medio": "Vas bien, pero piensa en qué acción ayuda no solo a ti, sino también a tus compañeros y al equipo de seguridad.", "malo": "No pasa nada. Repasa las señales típicas de un correo fraudulento y cuál es el canal correcto para avisar en una empresa."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Recibes un correo que dice venir del área de soporte TI y te pide ingresar tu usuario y contraseña en un enlace porque tu cuenta «será bloqueada hoy». ¿Qué es lo más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Responder el correo preguntándole al remitente si el mensaje es legítimo.', FALSE,
     'Si el correo es malicioso, el atacante responderá que sí; además, le confirmas que tu dirección está activa.', 1),
    ('Abrir el enlace en una ventana de incógnito para revisarlo sin correr riesgos.', FALSE,
     'El modo incógnito no protege contra sitios maliciosos ni descargas; solo evita guardar el historial local.', 2),
    ('Reenviar el correo a tus compañeros de equipo para advertirles que no hagan clic.', FALSE,
     'Así difundes el enlace malicioso y alguien podría abrirlo; la alerta debe canalizarla el equipo de seguridad.', 3),
    ('No hacer clic y reportarlo al equipo de seguridad por el canal oficial definido.', TRUE,
     'Evitas exponerte y permites que seguridad analice el correo, bloquee el dominio y avise a otros posibles afectados.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Recibes un correo que dice venir del área de soporte TI y te pide ingresar tu usuario y contraseña en un enlace porque tu cuenta «será bloqueada hoy». ¿Qué es lo más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'MFA exige verificar la identidad con al menos dos factores de tipos distintos, por lo que una contraseña filtrada no basta para entrar. 1) Los factores son algo que sabes, como una contraseña; algo que tienes, como el celular o una llave física; y algo que eres, como la huella. 2) Si un atacante roba la contraseña, igual necesita el segundo factor, que no controla. 3) No todos los factores son igual de fuertes: las apps autenticadoras y las llaves FIDO2 o passkeys son más robustas que el SMS. 4) El usuario debe rechazar y reportar las solicitudes de aprobación que no inició, porque pueden indicar un ataque de fatiga de MFA.',
    '{"metodo": "TECNICA", "criterios": ["Define MFA como el uso de dos o más factores de distinto tipo", "Nombra los tipos de factores: algo que sabes, que tienes y que eres", "Explica por qué una contraseña filtrada no basta", "Reconoce que hay factores más robustos que otros"], "palabras_clave": ["segundo factor", "algo que tienes", "apps autenticadoras", "FIDO2", "contraseña filtrada"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["La autenticación multifactor combina distintos tipos de prueba de identidad, como una contraseña más un código del celular. Así, aunque alguien obtenga la contraseña por phishing o por una filtración, no puede iniciar sesión sin el otro factor.", "Es pedir más de una evidencia de identidad de categorías diferentes: conocimiento, posesión o algo biométrico. Reduce mucho el riesgo de robo de cuentas, sobre todo si se usan llaves de seguridad o aplicaciones autenticadoras en lugar de mensajes SMS."], "sinonimos": [["MFA", "autenticación multifactor", "doble factor"], ["passkeys", "llaves de acceso"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste con claridad qué es MFA y por qué protege incluso si la contraseña se filtra.", "medio": "Vas bien, pero te faltó explicar los tipos de factores o por qué no todos ofrecen el mismo nivel de protección.", "malo": "No te preocupes. Repasa los tipos de factores de autenticación y piensa qué necesitaría un atacante para entrar a una cuenta protegida con MFA."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es la autenticación multifactor (MFA) y por qué reduce el riesgo cuando se filtra la contraseña de un usuario?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El mínimo privilegio consiste en dar a cada usuario, servicio o sistema solo los permisos necesarios para su función y durante el tiempo necesario. 1) Asigno permisos por roles según el cargo, no persona por persona. 2) Separo las cuentas: el administrador usa una cuenta normal para el día a día y una cuenta privilegiada solo para tareas de administración. 3) Las cuentas de servicio y las aplicaciones solo acceden a los recursos que usan, por ejemplo, lectura en una base de datos específica. 4) Reviso los accesos periódicamente y los retiro de inmediato cuando alguien cambia de cargo o deja la empresa. Así, si una cuenta se ve comprometida, el daño queda acotado.',
    '{"metodo": "TECNICA", "criterios": ["Define el mínimo privilegio como dar solo los permisos necesarios", "Propone asignar permisos por roles", "Separa las cuentas normales de las cuentas privilegiadas", "Incluye revisiones periódicas y retiro de accesos", "Explica que limita el impacto de una cuenta comprometida"], "palabras_clave": ["mínimo privilegio", "roles", "cuenta privilegiada", "cuentas de servicio", "revisión de accesos"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Significa que nadie tiene más acceso del que necesita para trabajar. Por ejemplo, alguien de ventas no debería poder entrar al sistema de remuneraciones, y un técnico usa permisos de administrador solo cuando realmente los necesita.", "Es limitar los permisos al mínimo indispensable para reducir el impacto de errores o de cuentas robadas. Lo aplicaría con roles bien definidos, cuentas de administración separadas, permisos acotados para aplicaciones y auditorías regulares de quién tiene acceso a qué."], "sinonimos": [["mínimo privilegio", "menor privilegio", "least privilege"], ["roles", "RBAC", "control de acceso basado en roles"], ["revisión de accesos", "reviso los accesos", "auditoría de accesos"]], "feedback_generico": {"correcto": "¡Excelente! Explicaste bien el principio y lo aterrizaste con ejemplos prácticos y realistas.", "medio": "Vas bien con la definición, pero te faltaron ejemplos concretos o considerar qué pasa con los accesos a lo largo del tiempo.", "malo": "No te desanimes. Repasa la idea de dar solo los permisos necesarios y piensa cómo se aplicaría a usuarios, administradores y aplicaciones."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica el principio de mínimo privilegio y da ejemplos concretos de cómo lo aplicarías en una empresa.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'La regla 3-2-1 busca que siempre exista una copia recuperable de los datos, incluso si el ransomware cifra los sistemas principales. 1) Mantengo tres copias de los datos: la original y dos respaldos. 2) Uso dos tipos de medios o almacenamiento distintos, para que una sola falla no afecte a todas las copias. 3) Guardo una copia fuera del sitio, idealmente desconectada o inmutable, para que el atacante no pueda cifrarla ni borrarla. 4) Protejo el acceso a los respaldos con credenciales separadas de las del dominio. 5) Pruebo la restauración periódicamente, porque un respaldo que nunca se probó puede fallar justo cuando se necesita.',
    '{"metodo": "TECNICA", "criterios": ["Explica las tres copias, los dos medios distintos y la copia fuera del sitio", "Menciona respaldos desconectados o inmutables frente al ransomware", "Separa las credenciales de acceso a los respaldos", "Destaca la importancia de probar la restauración"], "palabras_clave": ["tres copias", "fuera del sitio", "inmutable", "ransomware", "restauración", "desconectada"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Consiste en tener tres copias de la información, en dos soportes diferentes y con una de ellas en otra ubicación. Frente al ransomware lo crucial es que al menos una copia esté aislada de la red o no se pueda modificar, y que se haya comprobado que se puede restaurar.", "La regla asegura redundancia: varias copias, en distintos medios y una fuera de las instalaciones. Si el ransomware cifra los servidores y los respaldos conectados, la copia aislada permite recuperar la operación sin depender del atacante."], "sinonimos": [["respaldo", "backup", "copia de seguridad"], ["fuera del sitio", "offsite", "fuera de las instalaciones"], ["desconectada", "offline", "aislada"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste la regla y, sobre todo, por qué es decisiva frente al ransomware.", "medio": "Vas bien, pero te faltó algún elemento clave, como dejar una copia fuera del alcance del atacante o probar la restauración.", "malo": "No te preocupes. Repasa qué significa cada número de la regla y piensa qué pasaría si el ransomware alcanzara también los respaldos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿En qué consiste la regla de respaldos 3-2-1 y por qué es clave para recuperarse de un ataque de ransomware?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Una regla de correlación en un SIEM relaciona varios eventos, a menudo de fuentes distintas, dentro de una ventana de tiempo para detectar un patrón de ataque. En el ejemplo se combina un evento del gateway de correo con uno del proxy web, lo que sugiere una posible infección. Retener logs, bloquear por un solo indicador o generar reportes de rendimiento no son correlaciones.',
    '{"metodo": "TECNICA", "criterios": ["Define la correlación como relacionar varios eventos en una ventana de tiempo", "Reconoce el valor de combinar fuentes distintas", "Distingue la correlación de la retención de logs y de los reportes", "Distingue la correlación de una acción automática de bloqueo"], "palabras_clave": ["SIEM", "correlación", "ventana de tiempo", "fuentes distintas", "patrón de ataque"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Correlacionar es unir eventos que por separado parecen inofensivos para detectar algo sospechoso. Que un equipo reciba un adjunto peligroso y luego contacte un dominio malicioso es una secuencia que el SIEM puede detectar al cruzar ambos registros.", "La correlación en un SIEM busca relaciones entre eventos de diferentes sistemas, como correo, proxy, EDR o directorio. Las otras opciones son almacenamiento, respuesta automática o monitoreo de rendimiento, no detección por correlación."], "sinonimos": [["SIEM", "gestión de eventos e información de seguridad"], ["lista negra", "blocklist", "lista de bloqueo"], ["dominio de mala reputación", "dominio malicioso"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes qué aporta la correlación en un SIEM frente a otras funciones de la plataforma.", "medio": "Vas bien, pero revisa qué hace que una regla sea de correlación y no solo de almacenamiento, respuesta o reporte.", "malo": "No te desanimes. Repasa para qué sirve un SIEM y cómo relaciona eventos de distintas fuentes para detectar ataques."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Estás configurando casos de uso en el SIEM del SOC. ¿Cuál de estas opciones es un ejemplo de regla de correlación de eventos?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Alertar si un equipo que abrió un adjunto malicioso se conecta luego a un dominio de mala reputación.', TRUE,
     'Combina eventos de dos fuentes distintas, correo y proxy, en una ventana de tiempo para detectar un patrón que ninguna fuente revela por sí sola.', 1),
    ('Conservar durante un año todos los logs del firewall perimetral en almacenamiento de bajo costo.', FALSE,
     'Es una política de retención de logs; sirve para investigar, pero no correlaciona eventos ni genera detecciones.', 2),
    ('Configurar el firewall para bloquear automáticamente las IP que aparezcan en una lista negra pública.', FALSE,
     'Es una acción de bloqueo en el firewall basada en un solo indicador, no una regla que relacione eventos para detectar un patrón.', 3),
    ('Generar cada mes un reporte con el uso de CPU y memoria de todos los servidores de la empresa.', FALSE,
     'Es un reporte de rendimiento; no relaciona eventos de seguridad para detectar una amenaza.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Estás configurando casos de uso en el SIEM del SOC. ¿Cuál de estas opciones es un ejemplo de regla de correlación de eventos?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El principal beneficio de segmentar es limitar el movimiento lateral: si un dispositivo, como una cámara IP vulnerable, es comprometido, el atacante no puede alcanzar directamente los servidores críticos. Las zonas se separan con VLAN o subredes y se controlan con reglas de firewall que solo permiten el tráfico necesario. Así se reduce la superficie de ataque y se contienen los incidentes.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la contención del movimiento lateral como beneficio principal", "Menciona VLAN o subredes con reglas de firewall entre ellas", "Reconoce que la segmentación no reemplaza los parches ni cifra el tráfico", "Relaciona la segmentación con reducir la superficie de ataque"], "palabras_clave": ["segmentar", "movimiento lateral", "VLAN", "reglas de firewall", "superficie de ataque"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Separar la red en zonas evita que un atacante que entra por un equipo débil se desplace libremente hasta las bases de datos. Entre zonas solo se permite el tráfico estrictamente necesario, aplicando reglas de filtrado.", "La red plana permite que cualquier equipo hable con cualquier otro, lo que facilita la propagación de un ataque. Segmentar con VLAN y políticas de acceso contiene el daño y protege los activos críticos."], "sinonimos": [["movimiento lateral", "desplazamiento lateral"], ["red plana", "red sin segmentar"], ["segmentar", "dividir en zonas"]], "feedback_generico": {"correcto": "¡Muy bien! Identificaste el beneficio de seguridad clave de segmentar una red.", "medio": "Vas bien, pero revisa qué problema de seguridad concreto resuelve la segmentación y qué cosas no hace por sí sola.", "malo": "No te preocupes. Repasa qué es una red plana y piensa qué podría hacer un atacante que compromete un solo equipo dentro de ella."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Una empresa tiene servidores de base de datos, estaciones de trabajo y cámaras IP en una misma red plana. ¿Cuál es el principal beneficio de segmentarla en zonas con reglas de tráfico entre ellas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Aumentar la velocidad del enlace a internet al repartir el tráfico entre más redes.', FALSE,
     'La segmentación organiza y controla el tráfico interno; no aumenta la capacidad del enlace a internet.', 1),
    ('Eliminar la necesidad de aplicar parches a los equipos que quedan en zonas internas.', FALSE,
     'Segmentar reduce la exposición, pero los equipos siguen necesitando parches; es defensa en profundidad, no un reemplazo.', 2),
    ('Limitar el movimiento lateral, de modo que un equipo comprometido no alcance toda la red.', TRUE,
     'Con zonas y reglas entre ellas, una cámara o un PC comprometido no llega directamente a los servidores críticos, lo que contiene el incidente.', 3),
    ('Cifrar de forma automática todo el tráfico que circula entre los equipos de la red interna.', FALSE,
     'Las VLAN y las reglas entre segmentos no cifran el tráfico; para eso se requieren protocolos como TLS o IPsec.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Una empresa tiene servidores de base de datos, estaciones de trabajo y cámaras IP en una misma red plana. ¿Cuál es el principal beneficio de segmentarla en zonas con reglas de tráfico entre ellas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'CVSS es un estándar que puntúa la severidad técnica de una vulnerabilidad identificada con un CVE, según factores como el vector de ataque, la complejidad y el impacto. Es un buen punto de partida, pero la prioridad real se define agregando contexto: si el activo está expuesto a internet, qué tan crítico es y si existe explotación activa.',
    '{"metodo": "TECNICA", "criterios": ["Distingue CVE (identificador) de CVSS (puntaje de severidad)", "Explica que CVSS mide la severidad técnica y no la probabilidad de ataque", "Propone complementar con la exposición y la criticidad del activo", "Considera la existencia de explotación activa"], "palabras_clave": ["CVSS", "CVE", "severidad", "explotación activa", "contexto", "expuesto"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El CVE identifica la vulnerabilidad y el CVSS indica qué tan grave es en términos técnicos. Para decidir qué parchar primero hay que cruzar ese puntaje con el valor del activo, su exposición y la evidencia de que se está explotando.", "CVSS entrega una medida estandarizada de severidad, útil para comparar vulnerabilidades. Sin embargo, priorizar solo por el número lleva a malas decisiones; el riesgo depende de dónde está la vulnerabilidad y de si hay atacantes usándola."], "sinonimos": [["parche", "actualización de seguridad"], ["explotación activa", "explotación conocida"], ["priorizar", "jerarquizar"]], "feedback_generico": {"correcto": "¡Excelente! Tienes claro qué mide CVSS y por qué no basta por sí solo para priorizar.", "medio": "Vas bien, pero revisa qué mide exactamente el puntaje y qué información adicional necesitas para decidir qué parchar primero.", "malo": "No te desanimes. Repasa la diferencia entre CVE y CVSS, y qué factores además del puntaje influyen en la prioridad de un parche."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En la gestión de vulnerabilidades, ¿qué representa el puntaje CVSS de una CVE y cómo se debe usar al priorizar parches?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Es la probabilidad exacta de que tu organización sea atacada usando esa vulnerabilidad.', FALSE,
     'CVSS no mide la probabilidad de un ataque contra tu organización; para estimar la explotación se usan otras fuentes, como catálogos de vulnerabilidades explotadas.', 1),
    ('Es el identificador único que se asigna a cada vulnerabilidad cuando se publica.', FALSE,
     'Ese identificador es el CVE; CVSS es un sistema para puntuar su severidad.', 2),
    ('Es un orden obligatorio: siempre se parcha primero el puntaje más alto, sin importar el activo.', FALSE,
     'Priorizar solo por puntaje ignora el contexto: un sistema crítico expuesto con una vulnerabilidad media puede ser más urgente.', 3),
    ('Mide la severidad técnica y se complementa con contexto: exposición, criticidad y explotación activa.', TRUE,
     'El puntaje base describe la gravedad de la vulnerabilidad, pero la prioridad real depende del contexto de cada organización.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En la gestión de vulnerabilidades, ¿qué representa el puntaje CVSS de una CVE y cómo se debe usar al priorizar parches?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Sigo el ciclo de respuesta a incidentes, apoyándome en lo definido en la fase de preparación, como playbooks, contactos y herramientas. 1) En detección y análisis valido la alerta, reviso procesos, conexiones e indicadores de compromiso, y defino el alcance. 2) En contención aíslo el equipo de la red con el EDR, preservando la evidencia, y bloqueo los dominios o IP maliciosos. 3) En erradicación elimino el malware y su persistencia, y reseteo las credenciales que pudieron quedar expuestas. 4) En recuperación restauro o reinstalo el equipo, lo devuelvo a producción y lo monitoreo de cerca. 5) En lecciones aprendidas documento la línea de tiempo y la causa raíz, y mejoro las detecciones y los controles.',
    '{"metodo": "TECNICA", "criterios": ["Nombra y ordena las fases: preparación, detección y análisis, contención, erradicación, recuperación y lecciones aprendidas", "Contiene aislando el equipo sin destruir la evidencia", "Elimina la persistencia y resetea credenciales en la erradicación", "Monitorea el equipo después de la recuperación", "Documenta y mejora los controles en lecciones aprendidas"], "palabras_clave": ["contención", "erradicación", "recuperación", "lecciones aprendidas", "indicadores de compromiso", "evidencia"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero confirmo que no sea un falso positivo y dimensiono el alcance revisando si otros equipos muestran los mismos indicadores. Luego aíslo el equipo, conservo la evidencia, elimino la amenaza y cambio las credenciales afectadas, restauro el equipo y cierro con un informe que mejore las defensas.", "Aplicaría el ciclo de gestión de incidentes: analizar la alerta, contener la propagación aislando el equipo, erradicar el malware y sus mecanismos de persistencia, recuperar la operación con monitoreo reforzado y, finalmente, revisar qué falló para que no se repita."], "sinonimos": [["indicadores de compromiso", "IoC"], ["EDR", "detección y respuesta en endpoints"], ["aislar", "poner en cuarentena"]], "feedback_generico": {"correcto": "¡Muy bien! Recorriste las fases en orden y con acciones concretas y bien justificadas en cada una.", "medio": "Vas bien, pero te faltó alguna fase o una acción clave, como cuidar la evidencia o qué hacer después de resolver el incidente.", "malo": "No te preocupes. Repasa las fases del ciclo de respuesta a incidentes y qué objetivo tiene cada una antes de pensar en acciones concretas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'El EDR alerta que el equipo de un usuario del área de finanzas ejecutó un archivo sospechoso y está generando conexiones extrañas hacia internet. Describe cómo actuarías siguiendo las fases de respuesta a incidentes.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El patrón sugiere un escaneo automatizado en busca de archivos y paneles expuestos, así que analizo si tuvo éxito antes de reaccionar. 1) Filtro las peticiones de esa IP y busco respuestas distintas de 404, como 200, 403 o 500, que indiquen recursos existentes. 2) Si algún archivo sensible respondió 200, lo trato como posible filtración: lo retiro y roto los secretos que contenga. 3) Correlaciono la IP con otros logs, como firewall, WAF o autenticación, y reviso su reputación. 4) Aplico bloqueo o rate limiting en el WAF y verifico que el servidor no exponga archivos de configuración ni respaldos. 5) Documento el evento y creo una regla de detección para patrones similares.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce el patrón de escaneo o enumeración automatizada", "Busca respuestas exitosas que indiquen una exposición real", "Trata un archivo sensible expuesto como filtración y rota los secretos", "Correlaciona con otras fuentes y revisa la reputación de la IP", "Aplica bloqueo o rate limiting y crea detecciones"], "palabras_clave": ["escaneo", "404", "WAF", "reputación", "correlacionar", "rate limiting"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Esto parece reconocimiento automatizado, por la cantidad de peticiones y las rutas típicas. Lo clave es comprobar si alguna petición obtuvo contenido real; si es así, se escala como incidente. Después bloqueo la IP en el perímetro, endurezco la configuración del servidor y dejo una alerta configurada.", "Analizaría los códigos de respuesta para saber si el escaneo encontró algo, cruzaría la IP con otras fuentes y evaluaría su reputación. Además de bloquearla, revisaría que no haya archivos sensibles publicados y ajustaría el WAF para frenar este tipo de barridos."], "sinonimos": [["escaneo", "enumeración", "reconocimiento"], ["rate limiting", "limitación de tasa"], ["logs", "registros"], ["correlacionar", "correlaciono", "cruzar"]], "feedback_generico": {"correcto": "¡Excelente análisis! No te quedaste en bloquear: verificaste si el atacante logró algo y mejoraste las defensas.", "medio": "Vas bien, pero te faltó confirmar si alguna petición tuvo éxito o pensar en prevenir que el patrón se repita.", "malo": "No te desanimes. Repasa cómo leer los códigos de estado HTTP en un log y qué indica un gran volumen de peticiones a rutas conocidas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Revisando los logs de un servidor web ves miles de peticiones en pocos minutos desde una misma IP hacia rutas como /admin, /.env y /backup.zip, casi todas con código 404. ¿Cómo lo analizas y qué acciones tomas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Investigo antes de concluir, porque una VPN o un proxy corporativo pueden generar falsos positivos, pero actúo rápido si se confirma. 1) Reviso los detalles de ambos inicios de sesión: IP, dispositivo, navegador, aplicación y si se cumplió el MFA. 2) Descarto causas legítimas, como VPN o servicios en la nube, y contacto al usuario por un canal verificado. 3) Si parece un compromiso, revoco las sesiones activas, reseteo la contraseña y reviso los métodos de MFA registrados. 4) Busco actividad posterior del atacante, como reglas de reenvío en el correo, accesos a archivos o cambios de permisos. 5) Documento el incidente y ajusto la regla con excepciones acotadas para las VPN conocidas.',
    '{"metodo": "TECNICA", "criterios": ["Considera causas legítimas como VPN o proxy antes de concluir", "Revisa los detalles del login, como IP, dispositivo y MFA", "Verifica con el usuario por un canal confiable", "Contiene revocando sesiones, reseteando la contraseña y revisando los métodos de MFA", "Busca actividad posterior, como reglas de reenvío o cambios de permisos"], "palabras_clave": ["revocar sesiones", "MFA", "reglas de reenvío", "falsos positivos", "VPN", "canal verificado"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero verifico si hay una explicación válida, como el uso de una VPN, comparando IP, dispositivos y el estado del segundo factor. Si no la hay, cierro todas las sesiones de la cuenta, cambio la contraseña y reviso qué hizo el atacante mientras tuvo acceso.", "Trato la alerta como un posible robo de credenciales: confirmo con el usuario, analizo los registros de autenticación y, si se confirma, contengo bloqueando o reseteando la cuenta. Luego reviso el buzón, los archivos y los permisos en busca de persistencia, y ajusto la regla para reducir falsos positivos."], "sinonimos": [["revocar sesiones", "revoco las sesiones", "cerrar sesiones activas"], ["MFA", "segundo factor"], ["reglas de reenvío", "reenvío automático de correo"]], "feedback_generico": {"correcto": "¡Muy bien! Equilibraste el análisis de falsos positivos con una contención rápida y la búsqueda de persistencia.", "medio": "Vas bien, pero te faltó considerar causas legítimas de la alerta o revisar qué pudo hacer el atacante con la cuenta.", "malo": "Calma, las alertas de identidad son frecuentes en un SOC. Repasa qué información entrega un registro de inicio de sesión y cómo se contiene una cuenta comprometida."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'El SIEM genera una alerta de «viaje imposible»: la cuenta de un usuario inició sesión desde Santiago y, 15 minutos después, desde otro continente. ¿Cómo investigas y respondes?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Zero Trust elimina la confianza implícita por estar dentro de la red corporativa: cada acceso se verifica de forma explícita según la identidad, el estado del dispositivo y el contexto. Se combina con mínimo privilegio, microsegmentación y monitoreo continuo, asumiendo que puede haber una brecha. Es una estrategia que se implementa por etapas, no un producto.',
    '{"metodo": "TECNICA", "criterios": ["Explica la eliminación de la confianza implícita por ubicación en la red", "Menciona la verificación explícita de identidad, dispositivo y contexto", "Relaciona Zero Trust con mínimo privilegio y microsegmentación", "Reconoce que es una estrategia y no un producto"], "palabras_clave": ["Zero Trust", "confianza implícita", "identidad", "microsegmentación", "mínimo privilegio"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El modelo parte de la idea de nunca confiar y siempre verificar: estar en la oficina o conectado por VPN no otorga acceso por sí solo. Cada solicitud a un recurso se evalúa según quién la hace, desde qué dispositivo y bajo qué condiciones.", "Zero Trust cambia el perímetro de la red por controles centrados en la identidad y en los recursos. Implica autenticación fuerte, autorización granular, segmentación fina y monitoreo continuo, implementados de forma progresiva."], "sinonimos": [["Zero Trust", "confianza cero"], ["microsegmentación", "segmentación fina"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes que Zero Trust es un cambio de enfoque sobre la confianza y no una herramienta puntual.", "medio": "Vas bien, pero revisa qué tipo de confianza elimina realmente este modelo y cómo se decide cada acceso.", "malo": "No te desanimes. Repasa el principio de nunca confiar y siempre verificar, y compáralo con el modelo tradicional de perímetro."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu organización decide adoptar un modelo Zero Trust. ¿Qué cambio de enfoque representa mejor este modelo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Reemplazar el firewall perimetral por una VPN única que dé acceso completo a la red interna.', FALSE,
     'Una VPN con acceso amplio mantiene la confianza implícita una vez dentro, justo lo que Zero Trust busca eliminar.', 1),
    ('No confiar por estar dentro de la red y verificar cada acceso según identidad, dispositivo y contexto.', TRUE,
     'Zero Trust no confía de forma implícita: cada solicitud se autentica y autoriza con mínimo privilegio, esté dentro o fuera de la red.', 2),
    ('Bloquear el acceso a internet de todos los empleados, porque el modelo no confía en ninguno de ellos.', FALSE,
     'Zero Trust no significa desconfiar de las personas ni cortar servicios, sino verificar explícitamente cada acceso.', 3),
    ('Comprar e instalar en el perímetro de la red un producto certificado como Zero Trust.', FALSE,
     'Zero Trust es una estrategia y una arquitectura que se implementa por etapas, no un producto único.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu organización decide adoptar un modelo Zero Trust. ¿Qué cambio de enfoque representa mejor este modelo?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es un acceso de mínimo privilegio y trazable: el proveedor entra mediante un bastión o una solución PAM, con MFA y cuentas nominativas, solo al servidor que necesita. El acceso se habilita en ventanas aprobadas o justo a tiempo, y las sesiones se registran o graban para auditoría. Así se limita el impacto si sus credenciales se ven comprometidas.',
    '{"metodo": "TECNICA", "criterios": ["Aplica mínimo privilegio limitando el acceso al servidor necesario", "Exige MFA y cuentas nominativas, no compartidas", "Limita el acceso en el tiempo con ventanas aprobadas o acceso justo a tiempo", "Registra o graba las sesiones para auditoría", "Descarta exponer servicios de administración a internet"], "palabras_clave": ["bastión", "PAM", "MFA", "justo a tiempo", "sesiones grabadas", "cuentas nominativas"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["El proveedor debería conectarse a través de un punto de acceso controlado, con su propia cuenta y segundo factor, y ver únicamente el servidor que soporta. Los permisos se activan solo cuando se aprueba una intervención y todo lo que hace queda grabado.", "La mejor opción es la gestión de accesos privilegiados: acceso acotado a un activo, con autenticación fuerte, por tiempo limitado y con auditoría completa de la sesión. Las alternativas con VPN amplia, RDP expuesto o cuentas de dominio dan demasiado poder y poca trazabilidad."], "sinonimos": [["PAM", "gestión de accesos privilegiados"], ["bastión", "jump server", "servidor de salto"], ["justo a tiempo", "just-in-time", "JIT"], ["sesiones grabadas", "grabación de sesiones"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste un esquema que equilibra la necesidad del proveedor con el control y la trazabilidad.", "medio": "Vas bien, pero revisa si el esquema elegido limita el acceso al mínimo necesario y deja registro de lo que hace el proveedor.", "malo": "No te preocupes. Repasa los riesgos del acceso de terceros y cómo se aplican el mínimo privilegio y la auditoría en los accesos remotos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un proveedor externo necesita dar soporte durante tres meses a un servidor crítico de tu empresa. ¿Qué esquema de acceso remoto es el más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Acceso por un bastión o PAM con MFA, solo a ese servidor, en ventanas aprobadas y con sesiones grabadas.', TRUE,
     'Combina mínimo privilegio, autenticación fuerte, acceso acotado en el tiempo y trazabilidad de lo que hace el proveedor.', 1),
    ('Una cuenta VPN con acceso a toda la red interna y credenciales compartidas por el equipo del proveedor.', FALSE,
     'Da un acceso mucho mayor al necesario y las credenciales compartidas impiden saber quién hizo cada acción.', 2),
    ('Publicar el escritorio remoto (RDP) del servidor en internet, protegido con una contraseña larga.', FALSE,
     'Exponer RDP a internet es un vector de ataque muy explotado; una contraseña robusta no compensa esa exposición.', 3),
    ('Crear una cuenta temporal de administrador de dominio y deshabilitarla manualmente al terminar.', FALSE,
     'Otorga privilegios excesivos sobre toda la infraestructura y depende de que alguien recuerde deshabilitarla.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un proveedor externo necesita dar soporte durante tres meses a un servidor crítico de tu empresa. ¿Qué esquema de acceso remoto es el más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La ingesta debe guiarse por casos de uso de detección y por el riesgo del negocio, priorizando fuentes de alto valor como identidad, EDR, perímetro, correo y sistemas críticos. Para cada fuente se define qué detecciones habilita y se filtra el ruido que no aporta. Luego se amplía de forma iterativa, midiendo la cobertura frente a técnicas de ataque conocidas.',
    '{"metodo": "TECNICA", "criterios": ["Prioriza la ingesta según casos de uso y riesgo", "Identifica fuentes de alto valor como identidad, EDR y perímetro", "Evita ingerir todo sin propósito o depender de una sola fuente", "Propone ampliar de forma iterativa midiendo la cobertura"], "palabras_clave": ["casos de uso", "ingesta", "EDR", "identidad", "cobertura", "riesgo"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["Partiría de las amenazas más relevantes para la organización y de las detecciones que quiero construir, y desde ahí elegiría las fuentes que las hacen posibles. Lo habitual es comenzar con autenticación, endpoints, perímetro y aplicaciones críticas, y filtrar los eventos de poco valor.", "Con un presupuesto acotado, cada fuente debe justificar su costo con detecciones concretas. Se priorizan los logs de identidad, EDR y sistemas críticos, y la cobertura se va ampliando por etapas, por ejemplo comparándola con MITRE ATT&CK."], "sinonimos": [["ingesta", "ingestión", "recolección de logs"], ["EDR", "detección y respuesta en endpoints"], ["casos de uso", "casos de detección"]], "feedback_generico": {"correcto": "¡Excelente! Tomaste una decisión de arquitectura basada en riesgo y valor, no en volumen.", "medio": "Vas bien, pero revisa cómo justificar cada fuente de logs según lo que permite detectar y lo que cuesta.", "malo": "No te desanimes. Repasa qué fuentes de logs son más útiles en un SOC y por qué la ingesta debe partir de casos de uso."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Estás diseñando un SOC con un presupuesto limitado de ingesta de datos en el SIEM. ¿Qué estrategia es la más adecuada para decidir qué logs ingerir?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Ingerir todos los logs de todos los equipos, sin ningún filtro, y decidir más adelante qué alertas crear.', FALSE,
     'Dispara los costos y genera ruido sin casos de uso definidos; más datos no implica mejor detección.', 1),
    ('Ingerir solo los logs del firewall perimetral, porque todo el tráfico relevante pasa por él.', FALSE,
     'Deja sin visibilidad los ataques internos, el abuso de identidades y la actividad en endpoints o en la nube.', 2),
    ('Priorizar fuentes según casos de uso y riesgo, como identidad, EDR, perímetro y sistemas críticos.', TRUE,
     'Alinear la ingesta con amenazas y detecciones concretas maximiza el valor del presupuesto y permite crecer de forma ordenada.', 3),
    ('Postergar la ingesta hasta contar con presupuesto suficiente para centralizar todos los logs.', FALSE,
     'Mientras tanto no hay visibilidad; es mejor partir con las fuentes de mayor valor e ir ampliando.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Seguridad de redes y SOC' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Estás diseñando un SOC con un presupuesto limitado de ingesta de datos en el SIEM. ¿Qué estrategia es la más adecuada para decidir qué logs ingerir?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Abordo el problema con datos, porque la fatiga de alertas aumenta el riesgo de pasar por alto un ataque real. 1) Mido el volumen y la tasa de falsos positivos por regla para identificar las más ruidosas. 2) Ajusto esas reglas con umbrales, contexto y excepciones acotadas y documentadas, nunca desactivándolas a ciegas. 3) Enriquezco las alertas con contexto del activo, del usuario y de inteligencia de amenazas para priorizar mejor. 4) Automatizo el triage repetitivo con playbooks de SOAR. 5) Trato las detecciones como código, con revisión, pruebas y control de versiones, y valido con simulaciones que siguen detectando. 6) Sigo métricas como MTTD, MTTR y tasa de falsos positivos, y retroalimento a los analistas.',
    '{"metodo": "TECNICA", "criterios": ["Mide la tasa de falsos positivos por regla para priorizar", "Ajusta las reglas con excepciones acotadas sin desactivarlas a ciegas", "Enriquece las alertas con contexto e inteligencia de amenazas", "Automatiza el triage con SOAR o playbooks", "Valida las detecciones y sigue métricas como MTTD y MTTR"], "palabras_clave": ["falsos positivos", "excepciones acotadas", "inteligencia de amenazas", "SOAR", "playbooks", "MTTD"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero identificaría qué reglas generan más ruido y por qué, y las afinaría con umbrales y excepciones específicas en lugar de apagarlas. Sumaría contexto automático a cada alerta, automatizaría las tareas repetitivas de triage y mediría el efecto con indicadores de tiempo de detección y de respuesta.", "Es un problema de ingeniería de detección: hay que revisar el ciclo de vida de las reglas, probarlas con escenarios simulados, versionarlas y priorizar las alertas por riesgo. Con eso y con automatización, los analistas se concentran en lo que realmente importa sin perder cobertura."], "sinonimos": [["falsos positivos", "alertas falsas"], ["ajustar reglas", "tuning", "afinar reglas"], ["playbooks", "guías de respuesta"], ["fatiga de alertas", "alert fatigue"]], "feedback_generico": {"correcto": "¡Muy bien! Propusiste un plan medible que reduce el ruido sin sacrificar la capacidad de detección.", "medio": "Vas bien, pero te faltó algún elemento clave, como medir el problema, automatizar o validar que las reglas ajustadas siguen detectando.", "malo": "No te desanimes, es un desafío real de todo SOC. Repasa cómo se ajusta una regla de detección y qué métricas usa un SOC para medir su desempeño."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Lideras un SOC donde la mayoría de las alertas resultan ser falsos positivos y los analistas empiezan a ignorarlas. ¿Qué plan implementarías para mejorar la calidad de la detección sin perder visibilidad?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño el threat hunting como un ciclo basado en hipótesis, que busca amenazas que las detecciones actuales no capturan. 1) Defino hipótesis a partir de inteligencia de amenazas y de técnicas de MITRE ATT&CK relevantes para la organización, por ejemplo, el uso indebido de herramientas administrativas legítimas. 2) Verifico que tengo los datos necesarios, como logs de EDR, autenticación y red, con la retención adecuada. 3) Establezco líneas base de comportamiento normal para encontrar anomalías. 4) Ejecuto las búsquedas, investigo los hallazgos y escalo a respuesta a incidentes si encuentro actividad maliciosa. 5) Convierto cada hallazgo útil en una nueva regla de detección automatizada. 6) Documento cada cacería y mido la cobertura ganada.',
    '{"metodo": "TECNICA", "criterios": ["Plantea el hunting como un ciclo basado en hipótesis", "Usa inteligencia de amenazas o MITRE ATT&CK para definir hipótesis", "Verifica la disponibilidad y calidad de los datos", "Escala a respuesta a incidentes cuando corresponde", "Convierte los hallazgos en detecciones automatizadas"], "palabras_clave": ["hipótesis", "MITRE ATT&CK", "inteligencia de amenazas", "líneas base", "regla de detección"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["El threat hunting supone que un atacante ya pudo evadir los controles y sale a buscarlo de forma proactiva. Partiría de hipótesis concretas sobre técnicas de ataque, revisaría los datos disponibles, compararía con el comportamiento normal y transformaría lo aprendido en nuevas alertas.", "Lo organizaría como un programa repetible: priorizar técnicas según el riesgo, asegurar visibilidad en endpoints y red, ejecutar búsquedas documentadas y retroalimentar al equipo de detección. Si una búsqueda encuentra actividad maliciosa, se activa el proceso de respuesta a incidentes."], "sinonimos": [["threat hunting", "cacería de amenazas", "búsqueda proactiva de amenazas"], ["líneas base", "baseline"], ["hipótesis", "supuesto"]], "feedback_generico": {"correcto": "¡Excelente! Diseñaste un programa estructurado que además fortalece las detecciones existentes.", "medio": "Vas bien, pero te faltó estructura, como partir de hipótesis o qué hacer con lo que se encuentra en cada búsqueda.", "malo": "No te desanimes. Repasa la diferencia entre atender alertas y buscar amenazas de forma proactiva, y qué papel cumple una hipótesis."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden iniciar un programa de threat hunting proactivo en el SOC, además de la atención de alertas. ¿Cómo lo diseñarías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Abordo el hardening como un estándar medible y automatizado, no como una tarea manual que se hace una sola vez. 1) Defino líneas base por tipo de sistema a partir de referencias reconocidas, como los CIS Benchmarks, adaptadas al negocio. 2) Incluyo medidas clave: desactivar servicios y cuentas innecesarios, aplicar parches, usar mínimo privilegio, configurar registros y cifrado. 3) Pruebo los cambios en un grupo piloto antes de desplegarlos, para no romper aplicaciones. 4) Aplico la configuración de forma automatizada con herramientas como GPO, Ansible o infraestructura como código. 5) Mido el cumplimiento de forma continua y detecto desviaciones de la línea base. 6) Gestiono las excepciones de manera formal, con controles compensatorios y fecha de revisión.',
    '{"metodo": "TECNICA", "criterios": ["Define líneas base por tipo de sistema basadas en referencias como CIS Benchmarks", "Incluye medidas concretas como desactivar servicios innecesarios y aplicar parches", "Prueba en un grupo piloto antes de desplegar", "Automatiza la aplicación de la configuración", "Mide el cumplimiento, detecta desviaciones y gestiona excepciones"], "palabras_clave": ["línea base", "CIS Benchmarks", "automatizada", "cumplimiento", "desviaciones", "excepciones"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Partiría de guías de configuración segura reconocidas para cada sistema operativo, las ajustaría a la realidad de la empresa y las aplicaría con herramientas de gestión de configuración. Después monitorearía que los equipos no se aparten del estándar y documentaría cualquier excepción aprobada.", "A esa escala, el hardening tiene que ser código: plantillas versionadas, despliegue automatizado y validación periódica del cumplimiento. Así se reduce la superficie de ataque de forma consistente y se detecta rápido cuando alguien cambia una configuración."], "sinonimos": [["hardening", "bastionado", "endurecimiento"], ["línea base", "baseline", "configuración base"], ["desviaciones", "drift"]], "feedback_generico": {"correcto": "¡Muy bien! Planteaste el hardening como un estándar automatizado y medible, adecuado para operar a escala.", "medio": "Vas bien, pero te faltó algún elemento para sostenerlo en el tiempo, como la automatización, la medición del cumplimiento o el manejo de excepciones.", "malo": "No te preocupes. Repasa qué es una línea base de configuración segura y por qué aplicarla a mano en cientos de equipos no es sostenible."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes definir y mantener la configuración segura (hardening) de cientos de servidores y estaciones de trabajo con distintos sistemas operativos. ¿Cómo lo abordarías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Seguridad de redes y SOC'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- ── DISEÑO UX/UI ────────────────────────────────────────────────────────────

-- Diseño UI y Figma

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'WCAG 2.x en nivel AA exige una relación de contraste mínima de 4,5:1 entre el texto de tamaño normal y su fondo. Para texto grande el mínimo baja a 3:1, y el nivel AAA sube la exigencia a 7:1 para texto normal.',
    '{"metodo": "TECNICA", "criterios": ["Indica 4,5:1 como mínimo AA para texto normal", "Distingue el requisito para texto grande", "Distingue los niveles AA y AAA", "Relaciona el contraste con la legibilidad para personas con baja visión"], "palabras_clave": ["WCAG", "contraste", "4,5:1", "nivel AA", "texto grande"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["El mínimo de nivel AA para texto normal es 4,5 a 1. Si el texto es grande basta con 3 a 1, y quien apunte a AAA necesita 7 a 1.", "La relación debe ser de al menos 4,5:1 para cumplir AA con texto de cuerpo. Es un requisito pensado para que personas con baja visión, o en condiciones de mucha luz, puedan leer sin esfuerzo."], "sinonimos": [["WCAG", "pautas de accesibilidad"], ["contraste", "relación de contraste"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces el requisito de contraste de WCAG AA, una base clave para diseñar interfaces accesibles.", "medio": "Vas por buen camino, pero repasa qué valor corresponde a cada tamaño de texto y a cada nivel de conformidad.", "malo": "No te desanimes: repasa los criterios de contraste de WCAG y practica midiendo combinaciones con un verificador de contraste."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Según las pautas WCAG 2.x en nivel AA, ¿cuál es la relación de contraste mínima entre un texto de tamaño normal y su fondo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('3:1', FALSE,
     '3:1 es el mínimo AA para texto grande y para componentes de interfaz, no para texto de tamaño normal.', 1),
    ('7:1', FALSE,
     '7:1 corresponde al nivel AAA para texto normal, un requisito más exigente que AA.', 2),
    ('4,5:1', TRUE,
     'Correcta: WCAG en nivel AA exige al menos 4,5:1 para texto de tamaño normal.', 3),
    ('2:1', FALSE,
     '2:1 es insuficiente en cualquier nivel de WCAG; muchas personas con baja visión no podrían leer ese texto.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Según las pautas WCAG 2.x en nivel AA, ¿cuál es la relación de contraste mínima entre un texto de tamaño normal y su fondo?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La serie 8, 16, 24, 32 y 48 es coherente porque todos sus valores son múltiplos de 8. Un sistema de 8 pt usa esa base para márgenes, rellenos y tamaños, lo que da consistencia visual y facilita el trabajo entre diseño y desarrollo.',
    '{"metodo": "TECNICA", "criterios": ["Identifica valores múltiplos de 8", "Explica que el sistema busca consistencia y ritmo visual", "Menciona que aplica a márgenes, rellenos y tamaños", "Reconoce que facilita la comunicación con desarrollo"], "palabras_clave": ["múltiplos de 8", "espaciado", "consistencia", "ritmo visual", "padding"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["En un sistema de 8 pt todas las distancias se eligen entre múltiplos de 8, como 8, 16, 24 o 32. Así las pantallas se ven ordenadas y los desarrolladores no tienen que adivinar valores sueltos.", "La idea es tener una escala limitada basada en 8 para que el espaciado sea predecible. Por eso la única serie válida es la formada solo por múltiplos de esa base."], "sinonimos": [["espaciado", "spacing"], ["relleno", "padding"], ["margen", "margin"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes la lógica de un sistema de espaciado y cómo aporta consistencia a la interfaz.", "medio": "Vas bien, pero revisa cuál es la base del sistema y por qué conviene limitarse a una escala de valores.", "malo": "No te preocupes: repasa cómo funcionan los sistemas de espaciado de 8 pt y por qué los equipos los adoptan."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Tu equipo adopta un sistema de espaciado de 8 pt. ¿Qué serie de valores de margen y espaciado es coherente con ese sistema?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('5, 10, 15, 20 y 30', FALSE,
     'Son múltiplos de 5, no de 8; usarlos rompe el ritmo que busca el sistema de 8 pt.', 1),
    ('8, 16, 24, 32 y 48', TRUE,
     'Correcta: todos son múltiplos de 8, lo que genera un ritmo visual consistente y fácil de comunicar.', 2),
    ('6, 12, 18, 30 y 36', FALSE,
     'Son múltiplos de 6; no siguen la base de 8 del sistema acordado.', 3),
    ('7, 14, 21, 28 y 35', FALSE,
     'Son múltiplos de 7; ninguno coincide con la escala de 8 pt.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Tu equipo adopta un sistema de espaciado de 8 pt. ¿Qué serie de valores de margen y espaciado es coherente con ese sistema?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El estado de foco visible indica qué elemento está seleccionado y recibirá la acción, por ejemplo al navegar con la tecla Tab. Es fundamental para la accesibilidad de quienes usan teclado o tecnologías de apoyo, y es distinto de los estados hover, presionado y deshabilitado.',
    '{"metodo": "TECNICA", "criterios": ["Explica que el foco indica el elemento activo", "Relaciona el foco con la navegación por teclado", "Lo vincula con la accesibilidad", "Distingue el foco de los estados hover y deshabilitado"], "palabras_clave": ["foco", "teclado", "accesibilidad", "estado", "hover"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["El foco muestra dónde está parado el usuario dentro de la interfaz cuando avanza con el teclado. Sin un indicador visible, una persona que no usa mouse no sabría qué botón va a activar.", "Sirve para que la navegación sin puntero sea posible: resalta el control que se activará al presionar Enter o Espacio. Por eso cada componente interactivo debe diseñarse con un estado de foco claro."], "sinonimos": [["foco", "focus"], ["deshabilitado", "disabled", "inactivo"], ["hover", "estado al pasar el cursor"]], "feedback_generico": {"correcto": "¡Muy bien! Distingues los estados de un componente y entiendes por qué el foco es clave para la accesibilidad.", "medio": "Vas bien, pero repasa en qué se diferencia el estado de foco de los otros estados de un componente.", "malo": "No te desanimes: repasa los estados típicos de un componente interactivo y cuándo aparece cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En un sistema de diseño, ¿para qué sirve el estado de foco (focus) visible de un botón?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Para mostrar que el cursor del mouse está ubicado sobre el botón.', FALSE,
     'Eso corresponde al estado hover, que depende del puntero y no aparece en la navegación con teclado.', 1),
    ('Para indicar que el botón está deshabilitado de forma temporal.', FALSE,
     'El estado deshabilitado comunica que la acción no está disponible; es un estado distinto del foco.', 2),
    ('Para confirmar que la acción del botón terminó con éxito.', FALSE,
     'La confirmación de una acción se comunica con retroalimentación, como un mensaje, no con el estado de foco.', 3),
    ('Para indicar qué elemento recibirá la acción al usar el teclado.', TRUE,
     'Correcta: el foco visible permite a quienes navegan con teclado o tecnologías de apoyo saber dónde están.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En un sistema de diseño, ¿para qué sirve el estado de foco (focus) visible de un botón?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Combino varios recursos visuales para que lo más importante se perciba primero y el resto se lea en orden. 1) Tamaño y peso tipográfico: títulos más grandes y gruesos que el texto de apoyo. 2) Color y contraste: reservo el color más llamativo para la acción principal. 3) Espacio en blanco: separo bloques y doy aire a los elementos clave. 4) Agrupación por proximidad, para que lo relacionado se vea junto. 5) Posición y alineación, aprovechando patrones de lectura como la F o la Z. 6) Limito los elementos destacados, porque si todo resalta, nada resalta.',
    '{"metodo": "TECNICA", "criterios": ["Usa el tamaño y el peso tipográfico", "Usa el color y el contraste con intención", "Aprovecha el espacio en blanco y la proximidad", "Considera la posición y los patrones de lectura", "Evita destacar demasiados elementos"], "palabras_clave": ["tamaño", "contraste", "espacio en blanco", "proximidad", "patrones de lectura", "peso tipográfico"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Juego con el tamaño, el grosor de la letra y el color para que el título y el botón principal destaquen. También uso el espacio en blanco para agrupar y separar contenidos, y ubico lo prioritario donde la vista llega primero, cuidando no competir con demasiados elementos llamativos.", "Aplico principios de percepción: lo grande, lo contrastado y lo aislado capta la atención primero. Ordeno el contenido por prioridad, agrupo lo relacionado, alineo todo a una grilla y dejo un solo elemento dominante por pantalla, como la llamada a la acción."], "sinonimos": [["espacio en blanco", "espacio negativo", "whitespace"], ["peso tipográfico", "grosor de la fuente"], ["acción principal", "llamada a la acción", "CTA"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Usas varios recursos de jerarquía de forma intencionada para guiar la mirada del usuario.", "medio": "Vas bien, pero puedes sumar más recursos o explicar cómo los combinas para que la prioridad quede clara.", "malo": "No te desanimes: repasa los principios básicos de jerarquía visual y analiza pantallas bien diseñadas para identificarlos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué recursos usas para crear jerarquía visual en una pantalla y guiar la atención del usuario hacia lo más importante?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Priorizo la legibilidad en pantalla y una escala tipográfica acotada y consistente. 1) Elijo una familia muy legible en pantallas pequeñas, por lo general sans serif, con varios pesos disponibles. 2) Limito el diseño a una o dos familias. 3) Defino una escala tipográfica con pocos tamaños y roles claros, como título, subtítulo, cuerpo y etiqueta. 4) Uso un tamaño de cuerpo cómodo y un interlineado de alrededor de 1,4 a 1,6 veces el tamaño del texto. 5) Verifico el contraste del texto con su fondo. 6) Pruebo que el diseño soporte el texto agrandado desde la configuración del sistema operativo.',
    '{"metodo": "TECNICA", "criterios": ["Elige una familia legible en pantalla", "Limita la cantidad de familias", "Define una escala tipográfica con roles", "Considera el tamaño de cuerpo y el interlineado", "Considera el contraste y el texto escalable"], "palabras_clave": ["escala tipográfica", "legibilidad", "interlineado", "sans serif", "jerarquía", "contraste"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Busco una fuente clara en pantalla y no mezclo más de dos familias. Armo una escala con pocos estilos definidos para títulos, cuerpo y textos secundarios, cuido el interlineado y el contraste, y reviso que la interfaz no se rompa cuando el usuario agranda la letra desde su teléfono.", "Para mí lo clave es la consistencia: una escala tipográfica con nombres por función que todo el equipo usa, en vez de tamaños sueltos. A eso sumo una familia legible, un cuerpo de texto cómodo, un espaciado entre líneas generoso y compatibilidad con los tamaños de texto accesibles del sistema."], "sinonimos": [["interlineado", "altura de línea", "line height"], ["familia tipográfica", "fuente", "tipografía"]], "feedback_generico": {"correcto": "¡Excelente! Consideras la legibilidad, la consistencia y la accesibilidad al definir la tipografía de la app.", "medio": "Vas por buen camino, pero te faltaron aspectos como la escala tipográfica, el interlineado o el texto escalable.", "malo": "No te desanimes: repasa los fundamentos de tipografía para interfaces y cómo se define una escala tipográfica."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Debes definir la tipografía de una nueva app móvil. ¿Qué aspectos consideras para que el texto sea legible y consistente?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Los componentes y las variantes permiten reutilizar elementos de interfaz y mantenerlos consistentes desde un solo lugar. 1) Un componente es un elemento reutilizable, como un botón, que tiene un componente principal y copias llamadas instancias. 2) Los cambios en el componente principal se propagan a todas sus instancias. 3) En cada instancia puedo hacer ajustes puntuales, como cambiar el texto, sin romper el vínculo. 4) Las variantes agrupan versiones de un mismo componente en un conjunto, organizadas por propiedades como tamaño, tipo o estado. 5) Así el equipo diseña más rápido, mantiene la consistencia y facilita el traspaso a desarrollo.',
    '{"metodo": "TECNICA", "criterios": ["Explica el componente principal y sus instancias", "Indica que los cambios se propagan a las instancias", "Menciona los ajustes locales en las instancias", "Explica que las variantes agrupan versiones por propiedades", "Señala los beneficios de consistencia y velocidad"], "palabras_clave": ["componente principal", "instancias", "variantes", "propiedades", "consistencia"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Un componente es una pieza que creo una vez y reutilizo como instancias en todo el archivo; si edito el original, todas las copias se actualizan. Las variantes reúnen en un solo componente sus distintas versiones, como primario o secundario, o normal y deshabilitado, y se eligen desde el panel de propiedades.", "Sirven para no rediseñar lo mismo una y otra vez. El componente principal define el elemento y las instancias lo heredan, permitiendo cambios locales como el texto. Con variantes organizo estados y tamaños bajo propiedades, lo que ordena la librería y acelera el trabajo."], "sinonimos": [["componente principal", "main component", "componente maestro"], ["instancia", "instance", "copia"], ["variantes", "variants"]], "feedback_generico": {"correcto": "¡Muy bien! Explicas con claridad cómo funcionan los componentes y las variantes, y qué beneficios aportan al equipo.", "medio": "Vas bien, pero precisa la relación entre el componente principal y sus instancias, o cómo se organizan las variantes.", "malo": "No te desanimes: repasa en Figma cómo crear un componente, sus instancias y un conjunto de variantes con propiedades."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica qué son los componentes y las variantes en Figma y por qué conviene usarlos al diseñar una interfaz.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Se configura auto layout en el marco del botón, con padding horizontal y el ancho en Hug contents. Así el botón se ajusta al largo del texto y mantiene siempre el mismo relleno, mientras que Fill container lo haría ocupar el ancho disponible del contenedor padre.',
    '{"metodo": "TECNICA", "criterios": ["Usa auto layout en el botón", "Elige Hug contents para el ancho", "Define el padding horizontal", "Distingue Hug contents de Fill container y de las constraints"], "palabras_clave": ["auto layout", "Hug contents", "padding", "Fill container", "constraints"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Aplico auto layout al botón y defino su relleno lateral; luego dejo el redimensionamiento horizontal en Hug. Con eso, si la etiqueta se alarga o se acorta, el botón cambia de ancho solo y conserva los márgenes internos.", "La clave es que el botón abrace su contenido: auto layout con Hug contents en el eje horizontal y padding fijo. Las constraints o Fill container sirven para responder al contenedor padre, no al largo del texto."], "sinonimos": [["padding", "relleno", "margen interno"], ["Hug contents", "Hug", "ajustar al contenido"]], "feedback_generico": {"correcto": "¡Muy bien! Dominas cómo configurar auto layout para que un componente se adapte a su contenido.", "medio": "Vas por buen camino, pero revisa la diferencia entre las opciones de redimensionamiento de auto layout y las constraints.", "malo": "No te desanimes: practica en Figma creando un botón con auto layout y prueba cada opción de redimensionamiento."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En Figma quieres que un botón se ensanche automáticamente al cambiar el texto de su etiqueta, manteniendo el mismo relleno a los lados. ¿Qué configuras?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Auto layout en el botón, con padding horizontal y ancho en Hug contents.', TRUE,
     'Correcta: con auto layout y ancho Hug contents, el marco se ajusta al contenido y respeta el padding definido.', 1),
    ('Auto layout en el botón, con padding horizontal y ancho en Fill container.', FALSE,
     'Fill container hace que el botón ocupe el espacio disponible del contenedor padre, no que se ajuste al texto.', 2),
    ('Constraints Left and right en el texto, con el botón en un ancho fijo.', FALSE,
     'Las constraints responden al tamaño del marco padre; con ancho fijo, el botón no crece al alargar el texto.', 3),
    ('Agrupar el texto y el fondo en un Group y escalarlo con la herramienta Scale.', FALSE,
     'Escalar redimensiona todo de forma proporcional, incluido el texto, y habría que hacerlo a mano en cada cambio.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En Figma quieres que un botón se ensanche automáticamente al cambiar el texto de su etiqueta, manteniendo el mismo relleno a los lados. ¿Qué configuras?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El problema es que el error se comunica solo con color, lo que excluye a personas con daltonismo o baja visión y contradice WCAG. La solución es sumar un mensaje de texto junto al campo que explique cómo corregirlo y un ícono, además de asociar el mensaje al campo para los lectores de pantalla.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el uso del color como único medio", "Menciona a personas con daltonismo o baja visión", "Propone un mensaje de texto específico junto al campo", "Agrega un indicador adicional, como un ícono", "Considera los lectores de pantalla"], "palabras_clave": ["color", "daltonismo", "mensaje de error", "ícono", "WCAG", "lector de pantalla"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Depender solo del rojo deja fuera a quienes no distinguen colores. El campo debe mostrar también un ícono de alerta y un texto claro que diga qué está mal y cómo arreglarlo, de modo que el error se entienda sin ver el color.", "Según WCAG, el color no puede ser el único medio para transmitir información. Por eso el estado de error del campo debe incluir un mensaje escrito y otro indicador visual, y el mensaje debe quedar vinculado al campo para que lo anuncie el lector de pantalla."], "sinonimos": [["daltonismo", "deficiencia en la visión del color"], ["lector de pantalla", "screen reader"], ["mensaje de error", "texto de error"]], "feedback_generico": {"correcto": "¡Excelente! Detectas el problema de accesibilidad y propones una solución que funciona para todas las personas.", "medio": "Vas bien, pero precisa por qué el diseño actual excluye a algunos usuarios y qué elementos debe tener el estado de error.", "malo": "No te desanimes: repasa el criterio de WCAG sobre el uso del color y cómo diseñar mensajes de error accesibles."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En el diseño de un formulario, los campos con error solo se distinguen por un borde rojo. ¿Cuál es el principal problema de accesibilidad y su mejor solución?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El rojo es un color agresivo; conviene cambiarlo por un naranja más amable.', FALSE,
     'Cambiar el tono no resuelve nada: el error se seguiría comunicando solo con color.', 1),
    ('El rojo elegido tiene poco contraste; basta con usar un rojo más oscuro.', FALSE,
     'Mejorar el contraste ayuda, pero el error se seguiría comunicando solo con color y sin explicar qué corregir.', 2),
    ('Se comunica solo con color; hay que sumar un mensaje de texto y un ícono.', TRUE,
     'Correcta: WCAG pide no depender solo del color, y el texto además explica cómo corregir el error.', 3),
    ('Le falta movimiento; conviene hacer vibrar el campo para que se note más.', FALSE,
     'La animación puede ayudar a notarlo, pero no explica el error y no sirve a quienes usan lector de pantalla.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En el diseño de un formulario, los campos con error solo se distinguen por un borde rojo. ¿Cuál es el principal problema de accesibilidad y su mejor solución?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La solución es ampliar el área táctil del ícono a al menos 48 × 48 dp con relleno invisible, manteniendo el ícono visible en 24 dp. Material Design recomienda ese mínimo y las Human Interface Guidelines recomiendan 44 × 44 pt, porque los objetivos pequeños provocan errores y frustración, sobre todo a personas con movilidad reducida.',
    '{"metodo": "TECNICA", "criterios": ["Distingue el área táctil del tamaño visual del ícono", "Propone un área táctil de al menos 48 × 48 dp", "Mantiene el ícono visible en su tamaño original", "Relaciona el tamaño del objetivo con la accesibilidad y los errores"], "palabras_clave": ["área táctil", "48 dp", "Material Design", "accesibilidad", "relleno"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["El ícono puede seguir viéndose de 24 dp, pero la zona que responde al toque debe crecer hasta 48 × 48 dp como mínimo. Eso se logra con padding o un contenedor transparente alrededor, y reduce los toques fallidos.", "Hay que separar lo visual de lo interactivo: el dibujo queda igual y se amplía el objetivo táctil hasta el mínimo recomendado por Material Design. En iOS, la referencia equivalente de las Human Interface Guidelines es 44 × 44 pt."], "sinonimos": [["área táctil", "objetivo táctil", "touch target"], ["relleno", "padding"]], "feedback_generico": {"correcto": "¡Muy bien! Distingues el tamaño visual del área táctil y conoces las recomendaciones de las guías de plataforma.", "medio": "Vas por buen camino, pero revisa cómo resolver el problema sin alterar el diseño visual del ícono.", "malo": "No te desanimes: repasa las recomendaciones de tamaño de áreas táctiles en Material Design y en las Human Interface Guidelines."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En una app Android, un ícono de cerrar de 24 × 24 dp es difícil de tocar, y Material Design recomienda áreas táctiles de al menos 48 × 48 dp. ¿Cuál es la solución adecuada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Mantenerlo así, porque el área táctil siempre coincide con el tamaño visible del ícono.', FALSE,
     'El área táctil puede ser mayor que el ícono visible; limitarla al dibujo es justamente lo que causa el problema.', 1),
    ('Ampliar el área táctil a 48 × 48 dp con relleno invisible, sin cambiar el ícono visible.', TRUE,
     'Correcta: así se cumple la recomendación de Material Design y se mantiene el diseño visual del ícono.', 2),
    ('Agregar un tooltip que aparezca al pasar el dedo sobre el ícono, para indicar dónde tocar.', FALSE,
     'En pantallas táctiles no hay un hover como en escritorio, y un tooltip no agranda la zona que se puede tocar.', 3),
    ('Acercar más los íconos entre sí, para que la barra tenga más zonas en las que tocar.', FALSE,
     'Juntar los objetivos aumenta los toques accidentales; se necesita más área y separación, no menos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En una app Android, un ícono de cerrar de 24 × 24 dp es difícil de tocar, y Material Design recomienda áreas táctiles de al menos 48 × 48 dp. ¿Cuál es la solución adecuada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Preparo el archivo para que desarrollo encuentre todo lo necesario sin depender de mí. 1) Ordeno páginas y frames, y nombro las capas con criterio. 2) Uso componentes, estilos y variables del sistema de diseño en vez de valores sueltos. 3) Documento todos los estados y casos borde: carga, vacío, error y textos largos. 4) Muestro el comportamiento responsive y las interacciones con prototipos o anotaciones. 5) Marco las secciones listas como Ready for dev en Dev Mode y configuro la exportación de assets. 6) Hago una revisión conjunta con desarrollo antes de que empiecen y quedo disponible durante la implementación.',
    '{"metodo": "TECNICA", "criterios": ["Organiza y nombra el archivo con criterio", "Usa componentes y variables del sistema de diseño", "Documenta estados y casos borde", "Usa Dev Mode y anotaciones", "Hace una revisión conjunta con desarrollo"], "palabras_clave": ["Dev Mode", "casos borde", "estados", "variables", "anotaciones", "componentes"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Me aseguro de que el archivo esté limpio y de que todo use elementos de la librería, para que el código pueda reutilizar lo existente. Incluyo cada estado de la pantalla, cómo se adapta a distintos tamaños y notas sobre interacciones, marco lo que está listo en Dev Mode y hago una reunión de traspaso con el equipo.", "El handoff no es enviar un link: es una conversación. Antes preparo especificaciones claras con tokens y componentes, casos de error y contenido extremo, y assets exportables. Luego lo reviso con los desarrolladores, resuelvo dudas temprano y acompaño la implementación revisando el resultado."], "sinonimos": [["handoff", "traspaso", "entrega a desarrollo"], ["casos borde", "edge cases", "casos extremos"], ["assets", "recursos gráficos"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Tu handoff es ordenado, completo y muestra una colaboración real con el equipo de desarrollo.", "medio": "Vas bien, pero te faltó cubrir aspectos como los estados, los casos borde o la revisión conjunta con desarrollo.", "malo": "No te desanimes: repasa buenas prácticas de handoff en Figma y cómo usar Dev Mode para comunicar especificaciones."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Terminaste el diseño de una funcionalidad en Figma y debes entregarla a desarrollo. ¿Cómo preparas el handoff para reducir dudas e idas y vueltas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo abordo priorizando el contenido y adaptando la estructura a cada tamaño, no solo achicando. 1) Defino breakpoints y una grilla por tamaño, por ejemplo 12 columnas en escritorio, 8 en tablet y 4 en móvil. 2) Priorizo qué información y acciones son esenciales en pantallas pequeñas. 3) Reorganizo el layout: apilo columnas y convierto tablas anchas en tarjetas o listas. 4) Adapto la navegación, por ejemplo a una barra inferior o un menú. 5) Cuido las áreas táctiles y los tamaños de texto en móvil. 6) Construyo con auto layout y constraints en Figma, y pruebo con contenido real.',
    '{"metodo": "TECNICA", "criterios": ["Define breakpoints y grillas por tamaño", "Prioriza el contenido para pantallas pequeñas", "Reorganiza el layout en lugar de solo escalar", "Adapta la navegación y las áreas táctiles", "Usa auto layout o constraints y prueba con contenido real"], "palabras_clave": ["breakpoints", "grilla", "columnas", "priorizar contenido", "auto layout", "navegación"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No se trata de achicar el escritorio. Primero decido qué datos son críticos en cada dispositivo, luego defino puntos de quiebre y grillas con menos columnas, reacomodo los módulos uno debajo de otro, transformo tablas en tarjetas y simplifico la navegación, cuidando el tamaño de los objetivos táctiles.", "Trabajaría con un enfoque mobile first: diseño la versión móvil con lo esencial y voy sumando información en tablet y escritorio. Uso grillas fluidas y componentes con auto layout que se adaptan, y reviso cada breakpoint con datos reales para detectar desbordes."], "sinonimos": [["breakpoints", "puntos de quiebre", "puntos de corte"], ["grilla", "retícula", "grid"], ["layout", "disposición", "diseño de página"]], "feedback_generico": {"correcto": "¡Excelente! Adaptas el dashboard con criterio, priorizando el contenido y reorganizando la estructura para cada dispositivo.", "medio": "Vas por buen camino, pero profundiza en cómo priorizas el contenido o cómo cambian la grilla y la navegación según el tamaño.", "malo": "No te desanimes: repasa los conceptos de breakpoints, grillas responsive y el enfoque mobile first."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tienes un dashboard diseñado para escritorio y debes adaptarlo a tablet y móvil. ¿Cómo abordas el diseño responsive?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Defino la microinteracción como un pequeño sistema que da retroalimentación clara sin estorbar. 1) El disparador: el toque en el botón, con un estado presionado inmediato. 2) Las reglas: qué ocurre al agregar, por ejemplo si el producto ya estaba en el carro. 3) La retroalimentación: un estado de carga breve y luego una confirmación, como un check o el contador del carro que se actualiza. 4) Los estados de error, como sin stock o sin conexión, con un mensaje claro. 5) Animaciones cortas y sutiles, que respeten la opción de reducir movimiento del sistema. 6) Evito bloquear al usuario mientras se confirma la acción.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el disparador, las reglas y la retroalimentación", "Diseña los estados de carga, éxito y error", "Usa animaciones breves y con propósito", "Respeta la preferencia de reducir movimiento", "Evita bloquear o distraer al usuario"], "palabras_clave": ["disparador", "retroalimentación", "estados", "animación", "reducir movimiento"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Pienso en qué inicia la interacción, qué reglas la gobiernan y cómo responde la interfaz. El botón reacciona al toque, muestra que está procesando, confirma con un cambio visual y actualiza el ícono del carro, y si algo falla avisa con un mensaje. Las animaciones son rápidas y se desactivan si el usuario lo pide.", "Diseño todos los estados del botón: normal, presionado, cargando, agregado y error. La confirmación debe ser visible pero breve, por ejemplo una animación del contador del carro, y nunca debe impedir seguir comprando. También considero la accesibilidad del movimiento y los avisos para lectores de pantalla."], "sinonimos": [["retroalimentación", "feedback", "respuesta visual"], ["disparador", "trigger"], ["reducir movimiento", "reduced motion"]], "feedback_generico": {"correcto": "¡Muy bien! Diseñas la microinteracción con todos sus estados y cuidas que la retroalimentación sea clara y accesible.", "medio": "Vas bien, pero te faltaron estados, como el de error, o criterios sobre la duración y la accesibilidad de la animación.", "malo": "No te desanimes: repasa la estructura de una microinteracción y los estados que debe tener un botón de acción."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Debes diseñar la microinteracción del botón ''Agregar al carro'' de una app de compras. ¿Qué aspectos defines?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es una arquitectura en capas: tokens primitivos con la paleta base y tokens semánticos por rol, como el color de superficie o de texto principal, que apuntan a distintos primitivos según el tema y la marca. Los componentes consumen solo tokens semánticos, por lo que cambiar de tema no exige tocarlos; en Figma se implementa con variables y modos.',
    '{"metodo": "TECNICA", "criterios": ["Separa tokens primitivos de tokens semánticos", "Nombra los tokens semánticos por rol o intención", "Hace que los componentes consuman solo tokens semánticos", "Permite cambiar de tema o marca sin duplicar componentes", "Relaciona la solución con variables y modos de Figma"], "palabras_clave": ["tokens primitivos", "tokens semánticos", "variables", "modos", "temas"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Organizo los tokens en niveles: una paleta base sin significado y una capa semántica con nombres por uso, como fondo o borde de error. Cada modo o marca define sus propios valores para la capa semántica, y los componentes, al depender solo de ella, cambian de apariencia automáticamente.", "La clave es la indirección: los componentes nunca usan colores directos, sino tokens con intención. Esos tokens se resuelven a distintos valores de la paleta según el tema o la marca, algo que Figma permite con colecciones de variables y modos."], "sinonimos": [["design tokens", "tokens de diseño"], ["modo oscuro", "dark mode", "tema oscuro"], ["tokens semánticos", "tokens de alias"]], "feedback_generico": {"correcto": "¡Excelente! Propones una arquitectura de tokens escalable que permite manejar temas y marcas sin duplicar trabajo.", "medio": "Vas por buen camino, pero precisa cómo se relacionan las capas de tokens y cómo las consumen los componentes.", "malo": "No te desanimes: repasa los tipos de design tokens y cómo se usan para implementar temas en un sistema de diseño."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu sistema de diseño debe soportar modo claro, modo oscuro y una segunda marca sin duplicar componentes. ¿Qué arquitectura de design tokens es la más adecuada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Tokens con nombre de color, como azul-500, aplicados directo en los componentes y cambiados a mano por tema.', FALSE,
     'Cambiar valores a mano en cada componente no escala y es propenso a errores cuando hay varios temas y marcas.', 1),
    ('Una copia completa de la librería de componentes para cada combinación de tema y de marca.', FALSE,
     'Duplicar componentes multiplica el mantenimiento: cada cambio hay que replicarlo en todas las copias.', 2),
    ('Valores hexadecimales fijos en los componentes, más una tabla de equivalencias por tema.', FALSE,
     'Una tabla de equivalencias no aplica cambios automáticamente; los componentes siguen atados a valores fijos.', 3),
    ('Tokens primitivos con la paleta y tokens semánticos por rol que cambian de valor según tema y marca.', TRUE,
     'Correcta: los componentes usan solo tokens semánticos y cada tema o marca redefine a qué primitivo apuntan.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu sistema de diseño debe soportar modo claro, modo oscuro y una segunda marca sin duplicar componentes. ¿Qué arquitectura de design tokens es la más adecuada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es canalizar la solicitud por un proceso de contribución: evaluar si la necesidad es real y reutilizable y, si lo es, incorporarla al sistema con documentación y una nueva versión. Así el sistema evoluciona de forma controlada, sin bloquear al equipo ni llenarse de variantes desvinculadas.',
    '{"metodo": "TECNICA", "criterios": ["Propone un proceso de contribución o gobernanza", "Evalúa si la necesidad es reutilizable", "Documenta y versiona los cambios", "Evita tanto el rechazo rígido como la publicación sin revisión"], "palabras_clave": ["gobernanza", "contribución", "versionado", "documentación", "librería"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Lo trato con el modelo de gobernanza del sistema: el equipo presenta su caso, revisamos si otros lo necesitan y si se puede resolver con lo existente. Si suma valor general, la variante se diseña, se documenta y se publica en una versión nueva con su registro de cambios.", "El sistema debe evolucionar con las necesidades de los productos, pero con orden. Recibo la propuesta, la evaluamos en conjunto y, si aplica a más de un caso, la incorporamos oficialmente con documentación; si es muy específica, acordamos una solución local registrada."], "sinonimos": [["gobernanza", "governance"], ["librería", "biblioteca", "library"], ["versionado", "control de versiones"]], "feedback_generico": {"correcto": "¡Muy bien! Manejas la evolución del sistema de diseño con un proceso claro que equilibra flexibilidad y coherencia.", "medio": "Vas por buen camino, pero precisa cómo decides si la variante entra al sistema y cómo comunicas el cambio.", "malo": "No te desanimes: repasa los modelos de gobernanza y contribución en sistemas de diseño."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Varios equipos usan tu sistema de diseño y uno de ellos necesita una variante de botón que hoy no existe. Como responsable del sistema, ¿qué es lo más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Evaluarla con el proceso de contribución y, si es reutilizable, sumarla con documentación y versión.', TRUE,
     'Correcta: un proceso de contribución equilibra la necesidad del equipo con la coherencia del sistema y deja trazabilidad.', 1),
    ('Pedir al equipo que desvincule la instancia y la modifique localmente, sin avisar a nadie más.', FALSE,
     'Desvincular sin registro genera inconsistencias invisibles y el sistema nunca aprende de la nueva necesidad.', 2),
    ('Rechazar la solicitud, porque un sistema publicado no debe cambiar para no afectar a los demás.', FALSE,
     'Un sistema de diseño es un producto vivo; negarse a evolucionar empuja a los equipos a crear soluciones paralelas.', 3),
    ('Crear de inmediato la variante en la librería principal y publicarla a todos los equipos sin revisión.', FALSE,
     'Publicar sin evaluar puede duplicar variantes existentes o romper la coherencia, y afecta a todos los consumidores.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Varios equipos usan tu sistema de diseño y uno de ellos necesita una variante de botón que hoy no existe. Como responsable del sistema, ¿qué es lo más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es usar propiedades de componente: booleanas para mostrar u ocultar los íconos e instance swap para elegir qué ícono va, dejando las variantes solo para lo que cambia el estilo o la estructura, como el tamaño, el tipo y el estado. Así la cantidad de variantes cae mucho y el componente sigue siendo flexible y fácil de mantener.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la explosión de variantes como un problema de mantenimiento", "Usa propiedades booleanas para los elementos opcionales", "Usa instance swap para intercambiar íconos", "Reserva las variantes para cambios de estilo o estructura"], "palabras_clave": ["propiedades de componente", "booleanas", "instance swap", "variantes", "mantenimiento"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El problema es usar variantes para todo. Con una propiedad booleana puedo activar o desactivar el ícono y con instance swap cambiarlo por otro, así que esas combinaciones dejan de ser variantes. Solo el tamaño, el tipo y el estado quedan como propiedades de variante.", "Reduciría las combinaciones aprovechando las propiedades de componente de Figma: los íconos opcionales pasan a ser propiedades booleanas con intercambio de instancia, y el texto, una propiedad de texto. El conjunto queda mucho más pequeño y mantenible."], "sinonimos": [["instance swap", "intercambio de instancia"], ["propiedades de componente", "component properties"], ["variantes", "variants"]], "feedback_generico": {"correcto": "¡Excelente! Conoces las propiedades de componente de Figma y sabes usarlas para mantener un sistema escalable.", "medio": "Vas por buen camino, pero precisa qué tipo de propiedad conviene en cada caso y qué debe quedar como variante.", "malo": "No te desanimes: repasa los tipos de propiedades de componente en Figma y cuándo conviene cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu componente Botón en Figma suma 240 variantes al combinar tamaños, estados, tipos e íconos opcionales, y ya es difícil de mantener. ¿Qué es lo más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Separarlo en 240 componentes independientes, para editar cada uno sin afectar a los demás.', FALSE,
     'Separarlos elimina el vínculo entre versiones y multiplica el mantenimiento: un cambio global habría que hacerlo 240 veces.', 1),
    ('Usar propiedades booleanas e instance swap para los íconos, y variantes para tamaño, tipo y estado.', TRUE,
     'Correcta: las propiedades de componente reducen drásticamente la cantidad de variantes sin perder flexibilidad.', 2),
    ('Eliminar los estados del componente y describirlos solo con texto en la documentación.', FALSE,
     'Quitar los estados obliga a cada diseñador a recrearlos y aumenta las inconsistencias entre diseños.', 3),
    ('Pedir a cada diseñador que desvincule la instancia y agregue los íconos a mano cuando los necesite.', FALSE,
     'Las instancias desvinculadas dejan de recibir actualizaciones y rompen la consistencia del sistema.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UI y Figma' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu componente Botón en Figma suma 240 variantes al combinar tamaños, estados, tipos e íconos opcionales, y ya es difícil de mantener. ¿Qué es lo más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo abordo como un producto interno, partiendo por lo que ya existe y construyéndolo junto con desarrollo. 1) Hago una auditoría de interfaz para inventariar colores, tipografías y componentes repetidos. 2) Defino los fundamentos como design tokens: color, tipografía, espaciado y radios. 3) Priorizo los componentes más usados, como botones, campos y tarjetas, en vez de diseñarlo todo de una vez. 4) Construyo cada componente en Figma y en código, en paralelo con desarrollo. 5) Documento uso, estados y accesibilidad, y defino un modelo de gobernanza y versionado. 6) Mido la adopción e itero con los equipos de producto.',
    '{"metodo": "TECNICA", "criterios": ["Parte con una auditoría o inventario de la interfaz", "Define los fundamentos como design tokens", "Prioriza los componentes de alto uso", "Trabaja en conjunto con desarrollo", "Considera documentación, gobernanza y medición de la adopción"], "palabras_clave": ["auditoría de interfaz", "design tokens", "componentes", "documentación", "gobernanza", "adopción"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Empiezo mostrando el problema con un inventario visual de las inconsistencias, lo que ayuda a conseguir apoyo. Luego unifico fundamentos como paleta, tipografía y espaciado en tokens, construyo primero los componentes más repetidos junto a desarrollo, los documento y establezco cómo se proponen y publican los cambios.", "Lo trataría como un producto con usuarios internos: diseñadores y desarrolladores. Arranco con una auditoría, acuerdo principios y tokens, entrego un conjunto mínimo de componentes clave en Figma y en código, y voy midiendo cuánto lo usan los equipos para priorizar lo siguiente."], "sinonimos": [["sistema de diseño", "design system"], ["auditoría de interfaz", "inventario de interfaz", "UI audit"], ["design tokens", "tokens de diseño"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Planteas una estrategia realista y escalable, con foco en la adopción y la colaboración con desarrollo.", "medio": "Vas bien, pero tu plan necesita más detalle sobre cómo priorizas, cómo trabajas con desarrollo o cómo gobiernas el sistema.", "malo": "No te desanimes: repasa cómo se construye un sistema de diseño, desde la auditoría inicial hasta la gobernanza."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La empresa tiene cuatro productos con interfaces inconsistentes, como 12 tonos de azul y 8 estilos de botón distintos, y te piden liderar la creación de un sistema de diseño. ¿Cómo lo abordas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Unifico la identidad y la lógica del producto, y adapto los patrones donde las convenciones de cada plataforma afectan la usabilidad. 1) Mantengo comunes la marca, los tokens de color, la iconografía, el tono y la arquitectura de información. 2) Respeto las convenciones de navegación de cada plataforma, como el gesto o el botón de retroceso. 3) Uso controles nativos cuando el usuario espera su comportamiento, como los selectores de fecha y los diálogos del sistema. 4) Considero la tipografía del sistema y las medidas propias de cada guía. 5) Armo componentes compartidos con variantes por plataforma. 6) Resuelvo los casos dudosos con pruebas de usuarios y considerando el costo de desarrollo.',
    '{"metodo": "TECNICA", "criterios": ["Unifica la identidad visual y la arquitectura de información", "Respeta las convenciones de navegación de cada plataforma", "Usa controles nativos donde el usuario los espera", "Organiza componentes compartidos con variantes por plataforma", "Decide con pruebas de usuario y costo de desarrollo"], "palabras_clave": ["Material Design", "Human Interface Guidelines", "convenciones", "controles nativos", "navegación", "identidad"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Separo lo que es del producto de lo que es de la plataforma. La marca, el contenido y los flujos son iguales en ambas, pero la navegación, los gestos y los controles del sistema siguen lo que cada usuario conoce en su teléfono. Documento esas diferencias como variantes de los mismos componentes.", "Busco una base común para no duplicar trabajo, con tokens y componentes compartidos, y adapto solo lo que impacta en la experiencia, como la navegación hacia atrás, los selectores y las alertas. Cuando hay dudas, pruebo con usuarios de cada plataforma y considero el esfuerzo técnico."], "sinonimos": [["Human Interface Guidelines", "HIG"], ["controles nativos", "componentes del sistema"]], "feedback_generico": {"correcto": "¡Excelente! Equilibras la consistencia de marca con las convenciones de cada plataforma y justificas cómo decides.", "medio": "Vas por buen camino, pero precisa qué elementos unificas, cuáles adaptas y con qué criterio resuelves los casos dudosos.", "malo": "No te desanimes: repasa las diferencias principales entre Material Design y las Human Interface Guidelines, sobre todo en navegación."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Lideras el diseño de una app que se lanzará en Android e iOS con un equipo pequeño. ¿Cómo decides qué unificar entre plataformas y qué adaptar a Material Design y a las Human Interface Guidelines?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Construyo un sistema que conserve el amarillo como protagonista, pero que lo use solo donde cumple con el contraste. 1) Genero una escala tonal del amarillo y uso sus tonos más oscuros para texto e íconos sobre fondo claro. 2) Uso el amarillo como fondo de botones o superficies con texto oscuro encima, una combinación con buen contraste. 3) Reservo el amarillo puro para acentos, ilustraciones y elementos decorativos. 4) Verifico 4,5:1 para texto normal y 3:1 para texto grande y componentes. 5) Defino tokens semánticos que fijan qué combinaciones están permitidas. 6) Presento las alternativas al equipo de marca con ejemplos, para acordar la decisión.',
    '{"metodo": "TECNICA", "criterios": ["Crea una escala tonal del color de marca", "Usa el amarillo como fondo con texto oscuro", "Reserva el color puro para acentos o decoración", "Verifica los umbrales de contraste según el uso", "Formaliza las combinaciones con tokens y las acuerda con marca"], "palabras_clave": ["escala tonal", "contraste", "tokens semánticos", "accesibilidad", "identidad de marca"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No elimino el amarillo; cambio cómo se usa. Para textos y bordes sobre blanco uso versiones más oscuras de la misma tonalidad, y el amarillo original lo aplico como fondo con texto negro o en elementos decorativos. Documento las combinaciones válidas en el sistema y las valido con el equipo de marca.", "Separo el color de identidad del color funcional. Creo una paleta extendida con tonos que cumplan los contrastes de WCAG para cada uso, defino tokens que indiquen qué combinaciones se permiten y muestro al equipo de marca ejemplos de cómo se mantiene la identidad cumpliendo con la accesibilidad."], "sinonimos": [["escala tonal", "paleta tonal", "escala de tonos"], ["identidad de marca", "branding"], ["contraste", "relación de contraste"]], "feedback_generico": {"correcto": "¡Muy bien! Resuelves la tensión entre marca y accesibilidad con una propuesta concreta y bien fundamentada.", "medio": "Vas bien, pero precisa cómo usarías el color según el contexto y cómo verificarías el contraste de cada combinación.", "malo": "No te desanimes: repasa los criterios de contraste de WCAG y cómo se construyen paletas tonales accesibles."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'El color principal de la marca es un amarillo que sobre fondo blanco no alcanza el contraste mínimo de WCAG AA para texto. ¿Cómo construyes un sistema de color accesible sin perder la identidad de marca?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UI y Figma'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Diseño UX

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un wireframe de baja fidelidad sirve para definir la estructura de una pantalla: qué contenido va, en qué orden y dónde se ubica cada elemento. Deja fuera color, tipografía e imágenes finales para discutir la organización y cambiarla rápido y a bajo costo.',
    '{"metodo": "TECNICA", "criterios": ["Identifica que el wireframe define estructura y distribución", "Reconoce que omite el detalle visual a propósito", "Asocia la baja fidelidad con iterar rápido y barato", "Distingue el wireframe del diseño visual final y de la especificación para desarrollo"], "palabras_clave": ["estructura", "jerarquía", "distribución", "baja fidelidad", "boceto"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Es un esquema simple, a veces en papel o en escala de grises, que muestra cómo se organiza la información y dónde va cada bloque. Permite validar la disposición antes de invertir tiempo en el diseño visual.", "Su objetivo es acordar el esqueleto de la interfaz: secciones, contenido y prioridades. Como no tiene estilos finales, el equipo y los usuarios se concentran en la organización y no en los colores."], "sinonimos": [["wireframe", "esquema de pantalla", "maqueta de baja fidelidad"], ["boceto", "sketch"]], "feedback_generico": {"correcto": "¡Muy bien! Reconoces para qué sirve un wireframe de baja fidelidad y en qué etapa del proceso aporta más valor.", "medio": "Vas bien, pero repasa qué decisiones se toman en baja fidelidad y cuáles se dejan para etapas posteriores del diseño.", "malo": "No te preocupes: repasa la diferencia entre wireframe, mockup y prototipo, y qué nivel de detalle tiene cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En las primeras etapas de un proyecto, ¿cuál es el propósito principal de un wireframe de baja fidelidad?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Definir la paleta de colores, la tipografía y las imágenes finales de la interfaz.', FALSE,
     'La baja fidelidad omite a propósito el color, la tipografía y las imágenes finales; eso se trabaja en el diseño visual o en alta fidelidad.', 1),
    ('Definir la estructura, la jerarquía del contenido y la ubicación de los elementos.', TRUE,
     'Correcta: el wireframe de baja fidelidad se centra en la estructura y la distribución sin distraer con detalles visuales, y es barato de cambiar.', 2),
    ('Entregar a desarrollo las medidas exactas de cada elemento para empezar a programar.', FALSE,
     'Las especificaciones exactas se entregan en el handoff a partir de diseños de alta fidelidad, no desde un boceto.', 3),
    ('Medir con usuarios reales la tasa de conversión del producto ya lanzado.', FALSE,
     'La conversión se mide con analítica sobre un producto en uso; un wireframe es una representación temprana sin funcionamiento real.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En las primeras etapas de un proyecto, ¿cuál es el propósito principal de un wireframe de baja fidelidad?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El diseño aplica la heurística de visibilidad del estado del sistema, que pide mantener al usuario informado de lo que está pasando mediante retroalimentación oportuna. El indicador de carga y la confirmación final le muestran que su acción se está procesando y que terminó bien.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la heurística de visibilidad del estado del sistema", "Explica la importancia de la retroalimentación oportuna", "Relaciona el indicador de carga y la confirmación con el estado de la acción", "Distingue esta heurística de las que tratan sobre errores"], "palabras_clave": ["visibilidad", "estado del sistema", "retroalimentación", "heurística", "Nielsen"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Corresponde a la primera heurística de Nielsen: el sistema siempre debe informar al usuario qué está ocurriendo. Mostrar ''Guardando…'' y después ''Cambios guardados'' evita que el usuario dude o repita la acción.", "Es un ejemplo de mantener visible el estado del sistema. El usuario recibe feedback mientras se procesa su envío y una confirmación al final, por lo que sabe en todo momento en qué punto está."], "sinonimos": [["retroalimentación", "feedback"], ["indicador de carga", "spinner", "loader"]], "feedback_generico": {"correcto": "¡Excelente! Identificas la heurística correcta y entiendes por qué la retroalimentación al usuario es clave en este caso.", "medio": "Vas por buen camino, pero repasa el enunciado de cada heurística de Nielsen y con qué tipo de situación se relaciona.", "malo": "No te preocupes, se aprende practicando: repasa las 10 heurísticas de Nielsen con un ejemplo concreto de cada una."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Al enviar un formulario, la app muestra un indicador de carga con el texto ''Guardando…'' y luego ''Cambios guardados''. ¿Qué heurística de Nielsen aplica principalmente este diseño?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Ayudar a reconocer, diagnosticar y recuperarse de errores', FALSE,
     'Esta heurística trata de mensajes de error claros que expliquen el problema y cómo resolverlo; en este caso no ocurre ningún error.', 1),
    ('Estética y diseño minimalista', FALSE,
     'Esta heurística busca eliminar información irrelevante; el indicador de carga no tiene que ver con reducir elementos.', 2),
    ('Flexibilidad y eficiencia de uso', FALSE,
     'Se refiere a atajos y aceleradores para usuarios expertos, no a informar qué está pasando.', 3),
    ('Visibilidad del estado del sistema', TRUE,
     'Correcta: el sistema mantiene informado al usuario sobre lo que ocurre mediante retroalimentación oportuna.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Al enviar un formulario, la app muestra un indicador de carga con el texto ''Guardando…'' y luego ''Cambios guardados''. ¿Qué heurística de Nielsen aplica principalmente este diseño?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Una persona es un personaje ficticio construido a partir de datos de investigación, que resume los objetivos, necesidades, comportamientos y frustraciones de un segmento de usuarios. Ayuda al equipo a tomar decisiones centradas en el usuario y a hablar un lenguaje común sobre para quién diseña.',
    '{"metodo": "TECNICA", "criterios": ["Define la persona como un arquetipo ficticio", "Indica que se basa en datos de investigación", "Menciona objetivos, necesidades y frustraciones", "Explica su utilidad para alinear al equipo"], "palabras_clave": ["arquetipo", "investigación", "objetivos", "frustraciones", "segmento"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Es una representación semificticia de un tipo de usuario, creada al sintetizar entrevistas y otros datos. Incluye metas, contexto y puntos de dolor, y sirve para que todo el equipo recuerde para quién está diseñando.", "Es un modelo de usuario que agrupa patrones reales observados en la investigación en un personaje con nombre y contexto. No describe a una persona concreta, sino a un grupo con necesidades similares."], "sinonimos": [["persona", "user persona", "arquetipo de usuario"], ["frustraciones", "puntos de dolor", "pain points"]], "feedback_generico": {"correcto": "¡Muy bien! Sabes qué es una persona y de dónde debe salir la información que la compone.", "medio": "Vas por buen camino, aunque te faltó precisar en qué se basa una persona y para qué la usa el equipo de diseño.", "malo": "No te preocupes: repasa cómo se construyen las personas a partir de la investigación y en qué se diferencian de un usuario real."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En un proyecto UX, ¿qué es una persona (user persona)?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un arquetipo ficticio, basado en investigación, que representa a un grupo de usuarios.', TRUE,
     'Correcta: la persona sintetiza patrones encontrados en la investigación para que el equipo diseñe pensando en usuarios concretos.', 1),
    ('Un usuario real que el equipo elige para validar cada una de las decisiones del proyecto.', FALSE,
     'La persona no es un individuo real; depender de una sola persona real sesga las decisiones hacia sus gustos particulares.', 2),
    ('Una ficha con los datos demográficos promedio del mercado, como edad y comuna.', FALSE,
     'Una persona no se reduce a datos demográficos: lo central son los objetivos, comportamientos y frustraciones que revela la investigación.', 3),
    ('El stakeholder o cliente que financia el proyecto y aprueba el diseño.', FALSE,
     'El stakeholder es una parte interesada del negocio, no un modelo de los usuarios finales.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En un proyecto UX, ¿qué es una persona (user persona)?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Preparo un guion flexible y conduzco la entrevista escuchando más de lo que hablo. 1) Defino el objetivo de investigación y el perfil de participantes a reclutar. 2) Escribo un guion con preguntas abiertas y neutrales, sin preguntas inductoras. 3) Pido ejemplos concretos de experiencias pasadas, como la forma en que organizaron sus gastos el mes anterior, en vez de opiniones sobre el futuro. 4) Profundizo con repreguntas como por qué o cuéntame más, sin sugerir respuestas. 5) Pido consentimiento para grabar y tomo notas textuales. 6) Sintetizo los hallazgos agrupando patrones, por ejemplo con un mapa de afinidad.',
    '{"metodo": "TECNICA", "criterios": ["Define el objetivo y el perfil de participantes", "Usa preguntas abiertas y evita preguntas inductoras", "Pide ejemplos de comportamiento pasado en lugar de opiniones hipotéticas", "Considera el consentimiento y el registro de la entrevista", "Sintetiza los hallazgos en patrones"], "palabras_clave": ["preguntas abiertas", "guion", "consentimiento", "repreguntas", "mapa de afinidad", "sesgo"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero aclaro qué quiero aprender y a quién debo entrevistar. Luego armo una guía con preguntas abiertas que no sugieran la respuesta y, durante la sesión, pido que me cuenten situaciones reales que vivieron. Al final, con permiso, uso las grabaciones para agrupar hallazgos comunes.", "Recluto a personas del perfil objetivo y uso una guía semiestructurada. Evito preguntar si usarían una app y me enfoco en cómo resuelven hoy el problema, profundizando con repreguntas. Después transcribo, busco patrones entre entrevistas y los presento al equipo."], "sinonimos": [["guion", "guía de entrevista"], ["mapa de afinidad", "affinity map", "diagrama de afinidad"], ["preguntas inductoras", "preguntas sesgadas"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Planificas la entrevista con un objetivo claro y cuidas que tus preguntas no sesguen a los participantes.", "medio": "Vas bien, pero tu respuesta puede ser más completa: revisa cómo formular las preguntas y qué haces con la información después de las entrevistas.", "malo": "No te desanimes: repasa cómo se prepara un guion de entrevista, qué tipo de preguntas conviene hacer y cuáles evitar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Te piden entrevistar a usuarios para entender cómo organizan sus gastos mensuales antes de diseñar una app. ¿Cómo preparas y realizas esas entrevistas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Un customer journey map es una representación visual, basada en investigación, de la experiencia completa de un usuario al intentar lograr un objetivo. 1) Defino la persona y el escenario, por ejemplo comprar un regalo con poco tiempo. 2) Ordeno las etapas, como descubrir, comparar, comprar, recibir y postventa. 3) En cada etapa registro acciones, pensamientos y emociones del usuario. 4) Marco los puntos de contacto, como el sitio web, el correo o el despacho. 5) Identifico los puntos de dolor donde la emoción cae. 6) Traduzco esos dolores en oportunidades de mejora priorizadas.',
    '{"metodo": "TECNICA", "criterios": ["Define el journey map como representación de la experiencia en el tiempo", "Organiza el mapa en etapas", "Incluye acciones, pensamientos y emociones", "Identifica puntos de contacto y puntos de dolor", "Deriva oportunidades de mejora"], "palabras_clave": ["etapas", "puntos de contacto", "emociones", "puntos de dolor", "oportunidades", "persona"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Es un mapa que cuenta, paso a paso, cómo vive un usuario la compra desde que siente la necesidad hasta que recibe el producto. Para cada fase anoto qué hace, qué piensa, cómo se siente y con qué canal interactúa, y destaco los momentos frustrantes para convertirlos en mejoras.", "Lo construyo a partir de entrevistas y datos reales, no de supuestos. Uso una línea de tiempo con las fases de la compra, una curva emocional, los canales involucrados y los problemas detectados, y termino con una lista de oportunidades para el equipo."], "sinonimos": [["customer journey map", "journey map", "mapa de viaje del cliente"], ["puntos de contacto", "touchpoints"], ["puntos de dolor", "pain points"]], "feedback_generico": {"correcto": "¡Excelente! Explicas con claridad qué es un journey map y propones elementos que lo hacen útil para tomar decisiones.", "medio": "Buen inicio, pero te faltaron algunos componentes del mapa o explicar cómo pasar de lo observado a mejoras concretas.", "malo": "No te preocupes: repasa la estructura típica de un journey map y qué información se registra en cada etapa."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica qué es un customer journey map y qué elementos incluirías al construir uno para el proceso de compra en una tienda online.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Propongo prevenir los errores y, cuando ocurran, ayudar a corregirlos sin perder trabajo. 1) Conservo los datos ingresados para que el usuario no tenga que escribir todo de nuevo. 2) Muestro el mensaje junto al campo con problema, en lenguaje claro y diciendo cómo corregirlo. 3) Valido en línea al salir de cada campo, no mientras el usuario escribe. 4) Indico de antemano el formato esperado y cuáles campos son obligatorios. 5) Uso controles que restrinjan entradas inválidas, como selectores de fecha. 6) No dependo solo del color rojo: agrego un ícono y texto.',
    '{"metodo": "TECNICA", "criterios": ["Propone conservar los datos ingresados", "Ubica mensajes de error específicos junto al campo", "Usa lenguaje claro que explique cómo corregir", "Incluye prevención con validación en línea, formatos y restricciones", "Considera la accesibilidad del mensaje de error"], "palabras_clave": ["validación en línea", "mensaje de error", "prevención", "conservar datos", "campo"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Lo primero es no borrar lo que la persona escribió. Después marcaría cada campo con error con un texto específico que explique qué está mal y cómo arreglarlo, y agregaría validación al terminar cada campo, ejemplos de formato e indicación de campos obligatorios.", "Aplicaría las heurísticas de prevención de errores y de ayudar a reconocer y recuperarse de ellos. Eso significa restringir entradas inválidas, avisar el formato antes de escribir, validar campo por campo y mostrar mensajes concretos al lado del problema, manteniendo siempre los datos."], "sinonimos": [["validación en línea", "validación inline"], ["mensaje de error", "aviso de error"]], "feedback_generico": {"correcto": "¡Muy bien! Tus propuestas evitan que el usuario pierda su trabajo y lo ayudan a corregir errores con claridad.", "medio": "Vas bien, aunque te faltó cubrir tanto la prevención como la recuperación del error, y cómo comunicar el problema en cada campo.", "malo": "No te desanimes: repasa las buenas prácticas de formularios y las heurísticas de Nielsen relacionadas con los errores."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un usuario llena un formulario de registro de diez campos, presiona Enviar y la página se recarga vacía con el mensaje ''Error en el formulario''. ¿Qué mejoras de diseño propones?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El card sorting abierto es el método adecuado, porque los participantes agrupan tarjetas de contenido y le ponen nombre a cada grupo sin categorías previas. Así se descubre su modelo mental y su vocabulario, que luego sirven para proponer la arquitectura de información.',
    '{"metodo": "TECNICA", "criterios": ["Elige el card sorting abierto", "Explica que los usuarios crean y nombran los grupos", "Relaciona el método con el modelo mental del usuario", "Distingue el card sorting abierto del cerrado y del tree testing"], "palabras_clave": ["card sorting abierto", "modelo mental", "arquitectura de información", "categorías", "tree testing"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Usaría un card sorting abierto: cada participante ordena tarjetas con los contenidos y crea las categorías que le parezcan lógicas, con sus propios nombres. Eso muestra cómo piensan los usuarios y qué etiquetas entienden.", "Como no quiero imponer categorías, conviene un ordenamiento de tarjetas abierto. El cerrado o el tree testing sirven después, para validar una estructura ya propuesta."], "sinonimos": [["card sorting", "ordenamiento de tarjetas", "clasificación de tarjetas"], ["arquitectura de información", "information architecture"]], "feedback_generico": {"correcto": "¡Muy bien! Eliges el método que permite descubrir cómo los usuarios organizan el contenido y entiendes por qué.", "medio": "Vas por buen camino, pero revisa qué métodos sirven para descubrir una estructura y cuáles sirven para validarla.", "malo": "No te desanimes: repasa los métodos de arquitectura de información y en qué momento del proyecto conviene cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Vas a rediseñar el menú de un sitio con mucho contenido y quieres descubrir cómo los usuarios agrupan y nombran los temas, sin darles categorías predefinidas. ¿Qué método es el más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Card sorting cerrado, entregando a los usuarios las categorías del menú actual.', FALSE,
     'En el card sorting cerrado las categorías ya están definidas, así que no revela cómo los usuarios las nombrarían por sí mismos.', 1),
    ('Tree testing, pidiendo encontrar contenidos en la estructura de navegación.', FALSE,
     'El tree testing valida si una jerarquía ya propuesta permite encontrar contenido; no sirve para descubrir agrupaciones nuevas.', 2),
    ('Card sorting abierto, donde los usuarios crean y nombran sus propios grupos.', TRUE,
     'Correcta: el card sorting abierto revela el modelo mental de los usuarios y el vocabulario que usan para las categorías.', 3),
    ('Prueba de cinco segundos, mostrando la portada y preguntando qué recuerdan.', FALSE,
     'La prueba de cinco segundos mide la primera impresión y la claridad de una pantalla, no cómo se organiza el contenido.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Vas a rediseñar el menú de un sitio con mucho contenido y quieres descubrir cómo los usuarios agrupan y nombran los temas, sin darles categorías predefinidas. ¿Qué método es el más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un puntaje SUS de 68 está alrededor del promedio observado en estudios de referencia, así que indica una usabilidad intermedia, ni mala ni destacada, con margen de mejora. El SUS va de 0 a 100 y no es un porcentaje ni una tasa de éxito: mide la percepción de usabilidad y conviene complementarlo con métricas de desempeño y hallazgos cualitativos.',
    '{"metodo": "TECNICA", "criterios": ["Interpreta 68 como un valor cercano al promedio de referencia", "Aclara que el puntaje SUS no es un porcentaje", "Distingue la percepción de usabilidad de la tasa de éxito en tareas", "Sugiere complementar con otras métricas o hallazgos"], "palabras_clave": ["SUS", "promedio", "percepción", "usabilidad", "cuestionario"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["El resultado es intermedio: 68 es aproximadamente la media de referencia del SUS, por lo que el producto no es malo, pero tampoco destaca. Para saber qué mejorar hay que mirar los problemas observados en la prueba.", "El SUS es un cuestionario de 10 afirmaciones que entrega un puntaje de 0 a 100. Un 68 queda cerca del promedio, lo que sugiere una experiencia normal, con oportunidades claras de mejora; no se debe leer como porcentaje."], "sinonimos": [["SUS", "System Usability Scale", "escala de usabilidad del sistema"], ["puntaje", "puntuación", "score"]], "feedback_generico": {"correcto": "¡Excelente! Interpretas bien el puntaje SUS y evitas confundirlo con otras métricas de usabilidad.", "medio": "Vas bien, pero precisa qué mide el SUS y cómo se compara un puntaje con los valores de referencia.", "malo": "No te preocupes: repasa cómo se calcula el SUS, qué rango tiene y cuáles son sus valores de referencia."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tras una prueba de usabilidad aplicas el cuestionario SUS (System Usability Scale) y obtienes un puntaje promedio de 68. ¿Cómo interpretas ese resultado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Está cerca del promedio de referencia del SUS: un resultado intermedio, con margen de mejora.', TRUE,
     'Correcta: en estudios de referencia el puntaje SUS promedio ronda los 68, por lo que el resultado es intermedio y no destacado.', 1),
    ('Significa que el 68% de los participantes logró completar con éxito todas las tareas de la prueba.', FALSE,
     'El SUS mide la percepción de usabilidad con 10 afirmaciones; la tasa de éxito en tareas es una métrica distinta.', 2),
    ('Equivale a un 68% de satisfacción, así que casi un tercio de los usuarios quedó insatisfecho.', FALSE,
     'El puntaje SUS va de 0 a 100, pero no es un porcentaje ni indica qué proporción de usuarios quedó conforme.', 3),
    ('Es un resultado reprobatorio, porque el SUS exige al menos 90 puntos para considerar usable un producto.', FALSE,
     'No existe un mínimo de 90; un puntaje sobre 80 suele considerarse muy bueno y 68 es aproximadamente el promedio.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Tras una prueba de usabilidad aplicas el cuestionario SUS (System Usability Scale) y obtienes un puntaje promedio de 68. ¿Cómo interpretas ese resultado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es dejar correr el test hasta alcanzar el tamaño de muestra y la duración que se definieron antes de empezar, y recién entonces evaluar si la diferencia es estadísticamente significativa. Detenerlo al ver un buen resultado temprano aumenta el riesgo de un falso positivo y no considera la variación entre días.',
    '{"metodo": "TECNICA", "criterios": ["Respeta el tamaño de muestra y la duración planificados", "Menciona la significancia estadística", "Reconoce el riesgo de falsos positivos al detener antes de tiempo", "Evita modificar las variantes durante el test"], "palabras_clave": ["test A/B", "tamaño de muestra", "significancia estadística", "falso positivo", "conversión"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["No hay que adelantarse: dos días no bastan y la diferencia puede deberse al azar o a un comportamiento distinto según el día. Mantengo el experimento sin cambios hasta completar la muestra definida y analizo la significancia al final.", "Mirar los resultados a cada rato y cortar cuando se ven bien es un error común que infla los falsos positivos. Lo correcto es seguir el plan del experimento y decidir solo cuando se cumple la muestra calculada."], "sinonimos": [["test A/B", "prueba A/B", "A/B testing"], ["conversión", "tasa de conversión"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes cómo tomar decisiones confiables con un experimento y evitas conclusiones apresuradas.", "medio": "Vas por buen camino, pero revisa por qué importa respetar el plan del experimento antes de sacar conclusiones.", "malo": "No te desanimes: repasa los fundamentos de los test A/B, en especial el tamaño de muestra y la significancia estadística."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Lanzas un test A/B planificado para dos semanas y, al segundo día, la variante B muestra un 12% más de conversión. ¿Qué es lo más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Detener el test y declarar ganadora a B, porque la diferencia ya es suficientemente grande.', FALSE,
     'Revisar antes de tiempo y detener al ver una diferencia aumenta los falsos positivos; con pocos días la variación puede ser azar.', 1),
    ('Agregar un cambio de color al botón de B para amplificar la mejora mientras el test sigue.', FALSE,
     'Modificar una variante en pleno test contamina el experimento: ya no sabrías qué cambio causó el efecto.', 2),
    ('Detener el test y preguntar a cinco usuarios qué variante prefieren para confirmar el resultado.', FALSE,
     'La preferencia declarada de cinco personas no reemplaza un experimento cuantitativo ni confirma diferencias de conversión.', 3),
    ('Esperar a completar la muestra y la duración planificadas antes de evaluar la significancia.', TRUE,
     'Correcta: decidir con la muestra y la duración definidas evita conclusiones prematuras y cubre variaciones según el día de la semana.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Lanzas un test A/B planificado para dos semanas y, al segundo día, la variante B muestra un 12% más de conversión. ¿Qué es lo más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'La planifico con objetivos y tareas claras, y la conduzco observando sin guiar al participante. 1) Defino qué quiero aprender y las métricas: tasa de éxito, tiempo en tarea y errores. 2) Recluto unos cinco participantes del perfil objetivo por ronda. 3) Escribo escenarios realistas, como pagar un pedido con tarjeta, sin revelar en el texto dónde está cada botón. 4) Hago una sesión piloto para ajustar el guion. 5) Durante la sesión pido pensar en voz alta, no ayudo ni opino y anoto dónde se traban. 6) Sintetizo los hallazgos, los clasifico por severidad y los comparto con el equipo.',
    '{"metodo": "TECNICA", "criterios": ["Define objetivos y métricas como tasa de éxito y tiempo en tarea", "Recluta participantes del perfil objetivo", "Redacta escenarios realistas sin dar pistas", "Aplica pensar en voz alta y evita guiar al participante", "Sintetiza los hallazgos por severidad"], "palabras_clave": ["tareas", "think aloud", "tasa de éxito", "tiempo en tarea", "severidad", "participantes"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Parto definiendo objetivos y escribiendo tareas basadas en situaciones reales de pago. Recluto pocos participantes representativos, hago un piloto y, en cada sesión, les pido que verbalicen lo que piensan mientras yo observo sin intervenir. Mido si completan la tarea y cuánto demoran, y al final agrupo y priorizo los problemas encontrados.", "Armo un plan con preguntas de investigación, perfil de usuarios, escenarios y métricas. En la sesión actúo como moderador neutral: no explico la interfaz, uso repreguntas abiertas y registro errores y dudas. Después consolido los resultados de todas las sesiones en un informe con problemas priorizados por gravedad."], "sinonimos": [["pensar en voz alta", "think aloud"], ["tiempo en tarea", "time on task"], ["severidad", "gravedad"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Tu plan es completo y muestra cómo conducir la sesión sin sesgar a los participantes.", "medio": "Vas bien, pero te faltaron elementos del plan, como las métricas, el piloto o cómo priorizas lo observado.", "malo": "No te desanimes: repasa cómo se estructura una prueba de usabilidad moderada y el rol neutral del moderador."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Debes planificar una prueba de usabilidad moderada del flujo de pago de una app móvil. ¿Cómo la planificas y la conduces?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Priorizo combinando la severidad para el usuario, el impacto en el negocio y el esfuerzo de implementación. 1) Agrupo hallazgos repetidos o que comparten una misma causa raíz. 2) Califico la severidad de cada problema según su frecuencia, su impacto en la tarea y su persistencia. 3) Estimo el esfuerzo junto con desarrollo. 4) Los ubico en una matriz de impacto y esfuerzo, partiendo por los de alto impacto y bajo esfuerzo. 5) Reviso cuáles bloquean objetivos clave del negocio, como completar una compra. 6) Comparto la priorización con evidencia, como clips y métricas, y dejo el resto documentado en el backlog.',
    '{"metodo": "TECNICA", "criterios": ["Agrupa los hallazgos por causa raíz", "Usa una escala de severidad con frecuencia e impacto", "Considera el esfuerzo junto con desarrollo", "Usa una matriz de impacto y esfuerzo o un criterio equivalente", "Comunica la priorización con evidencia"], "palabras_clave": ["severidad", "impacto", "esfuerzo", "frecuencia", "matriz", "backlog"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero consolido los hallazgos para no tratar el mismo problema varias veces. Luego asigno una severidad según cuántos usuarios lo sufrieron y cuánto les impidió avanzar, estimo con el equipo cuánto cuesta resolverlo y elijo los que más valor entregan con menos esfuerzo, priorizando todo lo que bloquee tareas críticas.", "Uso una escala de severidad, por ejemplo de cosmético a catastrófico, y la cruzo con el costo de solución y los objetivos del producto. Presento la propuesta con evidencia de las sesiones para que el equipo entienda por qué unos problemas van primero, y los demás quedan registrados en el backlog."], "sinonimos": [["hallazgos", "insights", "problemas detectados"], ["severidad", "gravedad"]], "feedback_generico": {"correcto": "¡Excelente! Priorizas con criterios claros y equilibras la experiencia del usuario con la capacidad del equipo.", "medio": "Vas por buen camino, pero tu priorización necesita más criterios, como la severidad, el esfuerzo o la evidencia para comunicarla.", "malo": "No te desanimes: repasa las escalas de severidad de usabilidad y técnicas simples de priorización, como la matriz de impacto y esfuerzo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Después de una ronda de pruebas tienes 25 hallazgos de usabilidad y el equipo solo puede abordar algunos en el próximo sprint. ¿Cómo los priorizas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Primero entiendo dónde y por qué abandonan, y luego simplifico el onboarding para mostrar valor rápido. 1) Analizo el embudo pantalla por pantalla para ubicar los mayores puntos de abandono. 2) Complemento con investigación cualitativa, como pruebas de usabilidad o grabaciones de sesión, para entender las causas. 3) Elimino o postergo los pasos no esenciales y pido datos solo cuando se necesitan. 4) Permito omitir el recorrido introductorio y llevo al usuario a una primera acción de valor. 5) Uso divulgación progresiva, con ayudas contextuales en el momento de uso. 6) Valido la nueva versión con un test A/B que mida activación y retención.',
    '{"metodo": "TECNICA", "criterios": ["Analiza el embudo para ubicar el abandono", "Investiga las causas con métodos cualitativos", "Reduce pasos y pide solo la información esencial", "Lleva al usuario a una acción de valor temprana", "Valida el rediseño con métricas como activación o retención"], "palabras_clave": ["embudo", "abandono", "divulgación progresiva", "activación", "retención", "onboarding"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Empiezo por los datos: qué pantalla concentra el abandono. Luego observo a usuarios reales para entender qué los frena. Con eso acorto el flujo, dejo como opcionales los pasos que no aportan, muestro el beneficio principal lo antes posible y mido el cambio con un experimento controlado.", "No rediseñaría a ciegas: combinaría la analítica del embudo con entrevistas o pruebas para encontrar la causa. Las soluciones típicas son pedir menos datos al inicio, permitir saltar la introducción, enseñar funciones en contexto y medir activación y retención comparando contra la versión actual."], "sinonimos": [["onboarding", "incorporación", "bienvenida"], ["embudo", "funnel"], ["abandono", "deserción", "drop-off"]], "feedback_generico": {"correcto": "¡Muy bien! Combinas datos e investigación para encontrar la causa y propones mejoras que puedes medir.", "medio": "Vas bien, pero te faltó investigar la causa del abandono o definir cómo medirás si el rediseño funciona.", "malo": "No te desanimes: repasa buenas prácticas de onboarding y cómo analizar un embudo de conversión antes de rediseñar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'La analítica muestra que el 60% de los nuevos usuarios abandona una app durante su onboarding de seis pantallas. ¿Cómo abordas el rediseño?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es triangular: observar el comportamiento real de los usuarios en ese paso, con pruebas de usabilidad o grabaciones de sesión, para entender por qué abandonan. La analítica indica dónde está el problema y las entrevistas reflejan percepción, pero lo que las personas dicen puede diferir de lo que hacen.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce la diferencia entre lo que los usuarios dicen y lo que hacen", "Propone triangular con observación del comportamiento", "Entiende que la analítica muestra dónde ocurre el problema, pero no por qué", "Evita rediseñar sin conocer la causa"], "palabras_clave": ["triangulación", "comportamiento", "analítica", "observación", "abandono"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Hay una contradicción entre datos conductuales y declarativos, así que necesito observar directamente qué ocurre en ese paso. Una prueba de usabilidad enfocada o revisar grabaciones de sesión me mostrará dónde se traban, algo que la entrevista no capta.", "La analítica responde qué y dónde, y la investigación observacional responde por qué. Antes de decidir, combino ambas fuentes mirando a usuarios reales completar ese paso, porque la opinión declarada no siempre refleja la conducta."], "sinonimos": [["analítica", "analytics", "datos cuantitativos"], ["abandono", "drop-off", "deserción"], ["grabaciones de sesión", "session recordings"]], "feedback_generico": {"correcto": "¡Excelente! Manejas bien la contradicción entre fuentes y sabes qué método aporta la información que falta.", "medio": "Vas por buen camino, pero profundiza en qué tipo de dato entrega cada fuente y cómo combinarlas para decidir.", "malo": "No te desanimes: repasa la diferencia entre investigación actitudinal y conductual, y el concepto de triangulación."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La analítica muestra un alto abandono en un paso del flujo de contratación, pero en entrevistas los usuarios dicen que el flujo les parece fácil. ¿Qué es lo más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Confiar en las entrevistas, porque reflejan directamente la opinión de los usuarios sobre el flujo.', FALSE,
     'Lo que las personas dicen no siempre coincide con lo que hacen; la opinión declarada no explica el abandono medido.', 1),
    ('Observar el comportamiento en ese paso, con pruebas o grabaciones, para entender la causa.', TRUE,
     'Correcta: cuando los datos declarados y los conductuales se contradicen, conviene triangular observando qué hacen los usuarios en ese punto.', 2),
    ('Confiar solo en la analítica y rediseñar ese paso de inmediato con la mejor idea del equipo.', FALSE,
     'La analítica muestra dónde ocurre el problema, pero no por qué; rediseñar sin conocer la causa puede no resolverlo.', 3),
    ('Lanzar una encuesta de satisfacción general para decidir por mayoría cuál de los dos datos es correcto.', FALSE,
     'Una encuesta general vuelve a medir opiniones y no resuelve la contradicción; los datos no se validan por votación.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'La analítica muestra un alto abandono en un paso del flujo de contratación, pero en entrevistas los usuarios dicen que el flujo les parece fácil. ¿Qué es lo más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Les recomiendo aumentar bastante la muestra, porque con cinco participantes el margen de error de una tasa de éxito es demasiado amplio para comparar versiones. La regla de los cinco usuarios sirve para estudios cualitativos que buscan problemas, no para estimar métricas cuantitativas con precisión.',
    '{"metodo": "TECNICA", "criterios": ["Distingue estudios cualitativos de cuantitativos", "Explica el margen de error con muestras pequeñas", "Recomienda una muestra mayor para métricas comparables", "Ubica correctamente el alcance de la regla de los cinco usuarios"], "palabras_clave": ["tamaño de muestra", "margen de error", "cuantitativo", "cualitativo", "tasa de éxito"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Cinco usuarios bastan para descubrir los problemas principales en un estudio formativo, pero no para entregar números confiables. Si el objetivo es una métrica comparable, hay que planificar un estudio cuantitativo con más participantes y calcular su margen de error.", "Con cinco personas cada participante mueve la tasa en 20 puntos, así que cualquier diferencia con la versión anterior podría ser azar. Para reportar una cifra a gerencia conviene un estudio con una muestra bastante más grande, por ejemplo remoto y no moderado."], "sinonimos": [["tamaño de muestra", "cantidad de participantes"], ["tasa de éxito", "tasa de finalización", "completion rate"]], "feedback_generico": {"correcto": "¡Muy bien! Distingues cuándo una muestra pequeña es suficiente y cuándo no, y lo justificas con criterio estadístico.", "medio": "Vas bien, pero precisa la diferencia entre los estudios para encontrar problemas y los estudios para medir con precisión.", "malo": "No te desanimes: repasa la diferencia entre investigación cualitativa y cuantitativa, y cómo influye el tamaño de muestra."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu equipo quiere hacer una prueba con solo cinco participantes para reportar a gerencia una tasa de éxito precisa y compararla con la de la versión anterior. ¿Qué les recomiendas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Mantener cinco participantes, porque esa cantidad detecta la mayoría de los problemas en cualquier estudio.', FALSE,
     'La regla de los cinco usuarios aplica a estudios cualitativos para encontrar problemas, no a estimar métricas con precisión.', 1),
    ('Reemplazar la prueba por una evaluación heurística, que entrega la tasa de éxito sin usuarios.', FALSE,
     'La evaluación heurística la hacen expertos y detecta problemas probables; no produce tasas de éxito de usuarios reales.', 2),
    ('Aumentar bastante la muestra, porque con cinco el margen de error es muy amplio para comparar.', TRUE,
     'Correcta: cinco participantes sirven para encontrar problemas, pero una métrica comparable requiere una muestra mayor para tener un margen de error razonable.', 3),
    ('Hacer la prueba con cinco y presentar el resultado como porcentaje, que ya es una medida objetiva.', FALSE,
     'Expresarlo en porcentaje no lo hace preciso: con cinco personas cada participante equivale a 20 puntos y la incertidumbre es enorme.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu equipo quiere hacer una prueba con solo cinco participantes para reportar a gerencia una tasa de éxito precisa y compararla con la de la versión anterior. ¿Qué les recomiendas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es rechazar el patrón oscuro y proponer una cancelación clara y simple, sumando alternativas de retención transparentes, como pausar la suscripción o una oferta opcional. Dificultar la baja artificialmente daña la confianza, aumenta los reclamos y puede tener riesgos legales; el efecto de las alternativas se mide con datos.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el pedido como un patrón oscuro", "Propone un flujo de baja claro y simple", "Ofrece alternativas de retención legítimas", "Considera la confianza, los reclamos y los riesgos legales", "Propone medir el efecto de las alternativas"], "palabras_clave": ["patrón oscuro", "confianza", "retención", "ética", "cancelación"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Lo que se pide es un dark pattern: retener usuarios a la fuerza. Como líder, argumento que eso genera frustración y reclamos, y propongo una baja directa acompañada de opciones honestas, como pausar o cambiar de plan, midiendo cuántos las aceptan.", "Mi rol es equilibrar negocio y usuario sin engañar. Propondría un flujo de cancelación corto y visible, con una encuesta breve de salida y ofertas de retención que el usuario pueda rechazar fácilmente, y mostraría con datos su impacto en las bajas."], "sinonimos": [["patrón oscuro", "dark pattern", "patrón engañoso"], ["baja", "cancelación"]], "feedback_generico": {"correcto": "¡Excelente! Defiendes al usuario sin ignorar el objetivo del negocio y propones una alternativa ética y medible.", "medio": "Vas por buen camino, pero refuerza por qué el pedido es problemático y qué alternativa concreta propondrías al área comercial.", "malo": "No te desanimes: repasa qué son los patrones oscuros, sus consecuencias y cómo proponer alternativas de retención éticas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Para reducir bajas, el área comercial pide que cancelar una suscripción requiera seis pantallas y que la opción quede escondida en la configuración. Como líder de diseño, ¿qué postura es la más adecuada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Proponer una baja clara y simple, con alternativas transparentes como pausar, y medir su efecto.', TRUE,
     'Correcta: evita un patrón oscuro que daña la confianza y puede tener riesgos legales, y aun así ofrece opciones legítimas de retención.', 1),
    ('Implementarlo tal como se pide y dejar que las métricas de bajas decidan si se mantiene el diseño.', FALSE,
     'Que las bajas disminuyan no prueba que el diseño sea bueno: puede generar frustración, reclamos y pérdida de confianza.', 2),
    ('Aceptar las seis pantallas, pero agregar una barra de progreso para que el trámite sea más tolerable.', FALSE,
     'La barra de progreso suaviza la experiencia, pero mantiene la fricción artificial que busca impedir la cancelación.', 3),
    ('Mantener la opción escondida, pero facilitar la baja a quienes llamen o escriban al soporte.', FALSE,
     'Ocultar la opción sigue siendo un patrón oscuro y traslada el problema al soporte, con más costo y molestia.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Diseño UX' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Para reducir bajas, el área comercial pide que cancelar una suscripción requiera seis pantallas y que la opción quede escondida en la configuración. Como líder de diseño, ¿qué postura es la más adecuada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Manejo la situación con empatía y evidencia, separando las preferencias personales de los resultados. 1) Escucho su preocupación de fondo, porque puede haber un problema real detrás del gusto. 2) Presento la evidencia del rediseño, como la tasa de éxito, el tiempo en tarea y citas o clips de usuarios. 3) Conecto los resultados con los objetivos de negocio que a él le importan. 4) Si su inquietud es válida, propongo ajustes puntuales sin descartar lo validado. 5) Si persiste la duda, sugiero un experimento, como un test A/B o una prueba comparativa. 6) Documento la decisión y sus razones para futuras discusiones.',
    '{"metodo": "TECNICA", "criterios": ["Escucha la preocupación de fondo del stakeholder", "Presenta evidencia de la validación con usuarios", "Conecta la decisión con los objetivos de negocio", "Propone un experimento o ajuste ante la duda", "Documenta la decisión"], "palabras_clave": ["evidencia", "objetivos de negocio", "tasa de éxito", "experimento", "documentar"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No lo trato como una pelea de gustos. Pregunto qué le preocupa exactamente, muestro los resultados de las pruebas con usuarios y cómo el nuevo diseño apoya las metas del área, y ofrezco medir ambas versiones si todavía hay dudas. Lo acordado queda documentado.", "Primero valido su punto de vista, porque puede revelar una necesidad no cubierta. Después llevo la conversación a datos: métricas antes y después, comentarios de usuarios e impacto en los objetivos. Si es necesario, incorporo mejoras puntuales o propongo un experimento controlado para decidir."], "sinonimos": [["gerente", "stakeholder", "parte interesada"], ["evidencia", "datos", "resultados"]], "feedback_generico": {"correcto": "¡Muy buen trabajo! Defiendes tu diseño con evidencia y mantienes una relación colaborativa con el stakeholder.", "medio": "Vas bien, pero apoya más tu postura en datos concretos y en cómo el diseño se conecta con los objetivos del negocio.", "malo": "No te desanimes: repasa cómo presentar resultados de investigación y cómo negociar decisiones de diseño con stakeholders."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un gerente pide volver al diseño anterior del dashboard porque ''le gustaba más'', aunque tu rediseño fue validado con usuarios. ¿Cómo manejas la situación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Aplicaría design thinking como un ciclo iterativo que parte por entender al usuario antes de proponer soluciones. 1) Empatizar: reviso datos de uso y entrevisto u observo a clientes para entender su relación con el ahorro. 2) Definir: sintetizo los hallazgos en un problema centrado en el usuario y lo formulo como una pregunta del tipo ¿cómo podríamos…? 3) Idear: facilito sesiones con un equipo multidisciplinario para generar muchas ideas y luego priorizarlas. 4) Prototipar: construyo prototipos rápidos y baratos de las ideas más prometedoras. 5) Testear: los pruebo con usuarios y mido si cambian el comportamiento. 6) Itero según lo aprendido y vuelvo a fases anteriores cuando es necesario.',
    '{"metodo": "TECNICA", "criterios": ["Parte por comprender al usuario con investigación", "Redefine el problema centrado en el usuario", "Genera y prioriza ideas con un equipo multidisciplinario", "Prototipa rápido y prueba con usuarios", "Reconoce que el proceso es iterativo"], "palabras_clave": ["empatizar", "definir", "idear", "prototipar", "testear", "iterativo"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Lo trabajaría en cinco momentos que se repiten: entender a los clientes con investigación, reformular el problema desde sus necesidades, generar ideas junto con otras áreas, prototipar soluciones baratas y probarlas con usuarios reales. Según los resultados, vuelvo atrás y ajusto.", "Evitaría saltar a soluciones. Primero investigaría por qué no usan la función, luego definiría el desafío como una pregunta abierta, haría una ideación amplia con el equipo, construiría prototipos de baja fidelidad y los validaría con usuarios antes de invertir en desarrollo."], "sinonimos": [["design thinking", "pensamiento de diseño"], ["testear", "probar", "validar"]], "feedback_generico": {"correcto": "¡Excelente! Aplicas design thinking de forma práctica y muestras cómo liderar al equipo desde el problema hasta la validación.", "medio": "Vas por buen camino, pero te faltó alguna fase o mostrar que el proceso es iterativo y centrado en el usuario.", "malo": "No te desanimes: repasa las fases de design thinking y qué actividades concretas se hacen en cada una."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden liderar la exploración de un problema poco definido: ''los clientes casi no usan la función de ahorro de la app''. ¿Cómo aplicarías design thinking para abordarlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo mido comparando contra una línea base con métricas definidas antes del lanzamiento, combinando comportamiento y percepción. 1) Defino qué significa mejorar, ligado a objetivos del usuario y del negocio. 2) Uso como línea base el diseño anterior, medido con datos equivalentes. 3) Mido métricas conductuales, como tasa de éxito, tiempo en tarea, errores y abandono. 4) Sumo métricas actitudinales con un cuestionario estandarizado, como el SUS, o una pregunta de facilidad percibida. 5) Si es posible, uso un grupo de control o un test A/B para aislar factores externos como la estacionalidad. 6) Complemento con investigación cualitativa para explicar los números.',
    '{"metodo": "TECNICA", "criterios": ["Define las métricas de éxito antes del lanzamiento", "Compara contra una línea base", "Combina métricas conductuales y actitudinales", "Controla factores externos con un grupo de control o test A/B", "Complementa con investigación cualitativa"], "palabras_clave": ["línea base", "métricas conductuales", "métricas actitudinales", "tasa de éxito", "grupo de control"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Antes de comparar, acuerdo qué indicadores reflejan una mejor experiencia y tomo la medición del diseño anterior como referencia. Después reviso cambios en la completitud de tareas, los tiempos, los errores y el abandono, junto con la satisfacción declarada, y uso entrevistas o pruebas para entender el porqué.", "Lo ideal es un experimento controlado en que una parte de los usuarios vea el diseño anterior, para descartar efectos externos. Combino datos de comportamiento con un cuestionario de usabilidad y reporto a la dirección tanto los números como los hallazgos cualitativos que los explican."], "sinonimos": [["línea base", "baseline", "medición inicial"], ["métricas conductuales", "métricas de comportamiento"], ["métricas actitudinales", "métricas de percepción"]], "feedback_generico": {"correcto": "¡Muy bien! Planteas una medición rigurosa que combina distintos tipos de datos y permite atribuir el cambio al rediseño.", "medio": "Vas bien, pero te faltó una línea base, un control de factores externos o combinar comportamiento con percepción.", "malo": "No te desanimes: repasa cómo se mide la experiencia de usuario con métricas conductuales y actitudinales, y cómo compararlas en el tiempo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Lanzaste el rediseño de un flujo crítico de la app y la dirección te pregunta si realmente mejoró la experiencia. ¿Cómo lo mides?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Diseño UX'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- ── SOPORTE TI Y REDES ──────────────────────────────────────────────────────

-- Linux

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En notación octal, lectura vale 4, escritura 2 y ejecución 1, y cada dígito corresponde al dueño, al grupo y a los otros. 754 se traduce en rwx (4+2+1) para el dueño, r-x (4+1) para el grupo y r-- (4) para los demás, es decir, rwxr-xr--.',
    '{"metodo": "TECNICA", "criterios": ["Conoce los valores r=4, w=2 y x=1", "Asocia cada dígito al dueño, al grupo y a los otros", "Traduce 754 a rwxr-xr--", "Distingue el permiso de ejecución del dueño y del grupo"], "palabras_clave": ["chmod", "octal", "rwxr-xr--", "dueño", "grupo", "otros"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Cada número suma lectura (4), escritura (2) y ejecución (1). El 7 da todos los permisos al dueño, el 5 da lectura y ejecución al grupo y el 4 da solo lectura a los demás usuarios.", "El resultado es rwxr-xr--: el propietario puede leer, modificar y ejecutar el script, el grupo puede leerlo y ejecutarlo, y el resto solo puede leerlo."], "sinonimos": [["dueño", "propietario", "usuario"], ["permisos", "modo"]], "feedback_generico": {"correcto": "¡Muy bien! Traduces sin problemas la notación octal a permisos simbólicos.", "medio": "Vas bien, pero revisa el valor de cada permiso o a quién corresponde cada dígito.", "malo": "Repasa la notación octal de permisos en Linux y cómo se suma cada permiso para el dueño, el grupo y los otros."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué permisos quedan en un archivo después de ejecutar chmod 754 script.sh?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('rwxr--r-x', FALSE,
     'Eso equivale a 745: el grupo tendría solo lectura y los otros, lectura y ejecución.', 1),
    ('rwxrw-r--', FALSE,
     'Eso equivale a 764, porque el grupo tendría lectura y escritura (6).', 2),
    ('rwxr-xr--', TRUE,
     '7 = rwx para el dueño, 5 = r-x para el grupo y 4 = r-- para los demás.', 3),
    ('rw-r-xr--', FALSE,
     'Eso equivale a 654: el dueño no tendría permiso de ejecución.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Qué permisos quedan en un archivo después de ejecutar chmod 754 script.sh?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Según el estándar de jerarquía de archivos (FHS), la configuración global del sistema y de los servicios se guarda en /etc. Por ejemplo, el servidor SSH se configura en /etc/ssh/sshd_config, mientras que /var guarda datos variables como los logs y /home los archivos de los usuarios.',
    '{"metodo": "TECNICA", "criterios": ["Identifica /etc como el directorio de configuración", "Da un ejemplo de archivo de configuración en /etc", "Diferencia /etc de /var, /bin y /home", "Menciona el estándar FHS o la jerarquía de directorios"], "palabras_clave": ["/etc", "configuración", "FHS", "sshd_config"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Los archivos de configuración van en /etc. Ahí encuentras, por ejemplo, la configuración del servidor SSH, la tabla de montajes en /etc/fstab y las cuentas de usuario en /etc/passwd.", "El directorio /etc concentra la configuración del sistema. Los logs y datos cambiantes van en /var, los binarios básicos en /bin y los archivos personales en /home."], "sinonimos": [["directorio", "carpeta"], ["FHS", "jerarquía de directorios"]], "feedback_generico": {"correcto": "¡Bien! Conoces la estructura de directorios estándar de Linux.", "medio": "Te acercaste, pero revisa qué guarda cada directorio principal de la jerarquía de Linux.", "malo": "Repasa el estándar de jerarquía de directorios (FHS) y el propósito de cada directorio principal."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En un sistema Linux, ¿en qué directorio se guardan normalmente los archivos de configuración del sistema y de los servicios, como el del servidor SSH?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('/etc', TRUE,
     'Ahí están las configuraciones globales, por ejemplo /etc/ssh/sshd_config o /etc/fstab.', 1),
    ('/var', FALSE,
     'Contiene datos variables, como logs, colas y cachés, no la configuración del sistema.', 2),
    ('/bin', FALSE,
     'Contiene ejecutables de comandos básicos, no archivos de configuración.', 3),
    ('/home', FALSE,
     'Contiene los directorios personales de los usuarios, no la configuración global.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En un sistema Linux, ¿en qué directorio se guardan normalmente los archivos de configuración del sistema y de los servicios, como el del servidor SSH?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Primero se ejecuta sudo apt update, que actualiza la lista de paquetes disponibles desde los repositorios. Luego sudo apt upgrade instala las versiones nuevas de los paquetes ya instalados, usando esa lista recién descargada.',
    '{"metodo": "TECNICA", "criterios": ["Ejecuta apt update antes de apt upgrade", "Explica que update actualiza la lista de paquetes", "Explica que upgrade instala las versiones nuevas", "Usa sudo porque se requieren privilegios"], "palabras_clave": ["apt update", "apt upgrade", "repositorios", "lista de paquetes", "sudo"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Hay que refrescar primero el índice de paquetes con apt update y después aplicar las actualizaciones con apt upgrade, ambos con sudo. Si se invierte el orden, se trabajaría con información antigua de los repositorios.", "apt update no instala nada: solo descarga la información de versiones desde los repositorios. Con esa información, apt upgrade actualiza los paquetes instalados."], "sinonimos": [["repositorios", "repos"], ["paquete", "package"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces el orden correcto y lo que hace cada comando de apt.", "medio": "Vas bien, pero revisa qué hace exactamente cada comando y en qué orden deben ejecutarse.", "malo": "Repasa la gestión de paquetes con apt en Ubuntu: actualizar el índice, instalar actualizaciones y limpiar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En un servidor Ubuntu quieres instalar las actualizaciones disponibles de los paquetes que ya están instalados. ¿Qué secuencia de comandos es la correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('sudo apt upgrade y luego sudo apt update', FALSE,
     'El orden está invertido: upgrade usaría una lista de paquetes desactualizada.', 1),
    ('sudo apt install update', FALSE,
     'Intenta instalar un paquete llamado ''update''; no actualiza el sistema.', 2),
    ('sudo apt clean y luego sudo apt autoremove', FALSE,
     'Limpia la caché de paquetes y elimina dependencias que ya no se usan, pero no instala actualizaciones.', 3),
    ('sudo apt update y luego sudo apt upgrade', TRUE,
     'update descarga la lista actualizada de paquetes de los repositorios y upgrade instala las versiones nuevas.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En un servidor Ubuntu quieres instalar las actualizaciones disponibles de los paquetes que ya están instalados. ¿Qué secuencia de comandos es la correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Creo el usuario, le doy privilegios mediante sudo y evito trabajar directamente como root. 1) Creo la cuenta con sudo adduser soporte, que genera su directorio personal y pide la contraseña. 2) Lo agrego al grupo de administradores con sudo usermod -aG sudo soporte; en distribuciones de la familia Red Hat el grupo es wheel. 3) Verifico la pertenencia con id soporte o groups soporte. 4) Inicio sesión como ese usuario y ejecuto sudo -l para ver qué puede hacer. 5) Si solo necesita algunos comandos, los defino con visudo en lugar de darle acceso total, aplicando el mínimo privilegio. 6) Prefiero el acceso SSH con llaves y registro el cambio.',
    '{"metodo": "TECNICA", "criterios": ["Crea el usuario con adduser o useradd", "Lo agrega al grupo sudo con usermod -aG", "Verifica los grupos o permisos del usuario", "Aplica el principio de mínimo privilegio", "Evita usar root directamente"], "palabras_clave": ["adduser", "usermod", "grupo sudo", "visudo", "mínimo privilegio"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Uso adduser para crear la cuenta con su home y contraseña, y luego usermod -aG sudo para sumarlo al grupo sudo sin quitarle otros grupos. Compruebo con id que quedó en el grupo y que sudo funciona. Si no necesita acceso completo, le asigno solo los comandos necesarios con visudo.", "La idea es no compartir la cuenta root. Creo un usuario personal, le doy privilegios a través del grupo sudo o de reglas en sudoers, y así cada acción queda registrada a su nombre. Siempre aplico el mínimo privilegio y verifico la configuración antes de entregarla."], "sinonimos": [["root", "superusuario"], ["sudoers", "visudo"], ["directorio personal", "home"]], "feedback_generico": {"correcto": "¡Muy bien! Creaste el usuario de forma correcta y aplicaste buenas prácticas de seguridad.", "medio": "Vas bien con los comandos, pero te faltó verificar el resultado o considerar el mínimo privilegio.", "malo": "Repasa la gestión de usuarios y grupos en Linux y cómo se otorgan permisos de administración con sudo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Te piden crear en un servidor Ubuntu un usuario nuevo llamado ''soporte'' y darle permiso para ejecutar comandos de administración. ¿Cómo lo haces y qué buenas prácticas consideras?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Identifico el proceso, intento un cierre ordenado y solo lo fuerzo si es necesario. 1) Ejecuto top o htop, ordeno por uso de CPU y anoto el PID y el nombre del proceso. 2) Confirmo de qué se trata con ps aux | grep nombre, para no detener un proceso equivocado. 3) Envío la señal de término con kill PID, que por defecto es SIGTERM y permite cerrar ordenadamente. 4) Si no responde tras unos segundos, uso kill -9 PID, que envía SIGKILL y lo fuerza. 5) Si la aplicación es un servicio, prefiero systemctl restart con el nombre del servicio. 6) Reviso los logs para entender por qué se colgó.',
    '{"metodo": "TECNICA", "criterios": ["Usa top, htop o ps para identificar el PID", "Confirma el proceso antes de detenerlo", "Usa kill con SIGTERM como primera opción", "Reserva kill -9 o SIGKILL para cuando no responde", "Revisa los logs o usa systemctl si es un servicio"], "palabras_clave": ["top", "PID", "kill", "SIGTERM", "SIGKILL", "ps aux"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Con top veo qué proceso usa más CPU y obtengo su PID. Luego le envío kill PID para que termine de forma limpia, y solo si sigue vivo uso kill -9. Si era un servicio administrado por systemd, lo reinicio con systemctl y reviso sus logs.", "Primero lo localizo con htop o con ps aux y grep, verificando que sea el proceso correcto. Lo detengo con la señal SIGTERM para que libere sus recursos, y si no responde lo fuerzo con SIGKILL. Después investigo en los logs la causa del bloqueo."], "sinonimos": [["colgado", "bloqueado", "congelado"], ["SIGKILL", "kill -9"], ["SIGTERM", "kill -15"]], "feedback_generico": {"correcto": "¡Muy bien! Detuviste el proceso con criterio, priorizando un cierre ordenado.", "medio": "Identificaste el proceso, pero revisa el orden de las señales o la verificación antes de detenerlo.", "malo": "Repasa la gestión de procesos en Linux: cómo listarlos, qué es un PID y qué señales se usan para detenerlos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Una aplicación en un servidor Linux quedó colgada y está consumiendo casi el 100% de la CPU. ¿Cómo identificas el proceso y lo detienes de forma segura?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El comando cuenta cuántas líneas del archivo contienen la palabra error, y find permite buscar archivos por nombre y fecha. 1) grep busca en app.log las líneas que contienen ''error''. 2) La opción -i hace que no distinga mayúsculas de minúsculas, así que también encuentra Error o ERROR. 3) La tubería | envía la salida de grep como entrada a wc. 4) wc -l cuenta las líneas recibidas, así que el resultado es la cantidad de líneas con error. 5) Para los archivos uso find /var/log -name ''*.log'' -mtime -1, donde -name filtra por nombre y -mtime -1 por modificación en el último día. 6) Si aparecen errores de permisos, lo ejecuto con sudo.',
    '{"metodo": "TECNICA", "criterios": ["Explica que grep filtra las líneas que contienen el texto", "Explica que -i ignora mayúsculas y minúsculas", "Explica la tubería y que wc -l cuenta líneas", "Usa find con -name para filtrar por nombre", "Usa -mtime -1 para las últimas 24 horas"], "palabras_clave": ["grep", "tubería", "wc -l", "find", "mtime", "mayúsculas"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Ese comando filtra con grep las líneas de app.log que tienen la palabra error, sin importar mayúsculas, y le pasa el resultado a wc -l para contarlas. Para encontrar los .log modificados en el último día usaría find /var/log -name ''*.log'' -mtime -1.", "La primera parte busca coincidencias de error ignorando mayúsculas, el pipe conecta esa salida con wc y la opción -l devuelve el número de líneas. Para la búsqueda de archivos, find recorre /var/log y filtra por patrón de nombre y por fecha de modificación reciente."], "sinonimos": [["tubería", "pipe"], ["no distinga mayúsculas", "insensible a mayúsculas", "case insensitive"]], "feedback_generico": {"correcto": "¡Excelente! Explicaste cada parte del comando y armaste bien la búsqueda de archivos.", "medio": "Vas bien, pero te faltó explicar alguna opción del comando o algún filtro de la búsqueda.", "malo": "Repasa grep, find y el uso de tuberías para combinar comandos en Linux."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica qué hace el comando grep -i ''error'' app.log | wc -l y cómo buscarías todos los archivos .log modificados en las últimas 24 horas dentro de /var/log.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'systemctl enable crea los enlaces para que el servicio se inicie en el arranque, y la opción --now hace que además se inicie de inmediato. Por eso sudo systemctl enable --now nginx cumple ambos objetivos en un solo comando, y se puede comprobar con systemctl status nginx.',
    '{"metodo": "TECNICA", "criterios": ["Elige systemctl enable --now", "Diferencia enable (arranque) de start (ahora)", "Sabe que reload solo recarga la configuración", "Propone verificar con systemctl status"], "palabras_clave": ["systemctl", "enable", "--now", "start", "status"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Con systemctl enable --now nginx el servicio se habilita para los próximos arranques y se inicia en el momento. Es equivalente a ejecutar enable y luego start por separado.", "start solo afecta a la sesión actual y enable solo al arranque; la opción --now une ambos. Después conviene revisar con systemctl status nginx que quedó active y enabled."], "sinonimos": [["arranque", "inicio del sistema", "boot"], ["habilitar", "enable"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces la diferencia entre iniciar y habilitar un servicio con systemd.", "medio": "Vas bien, pero revisa qué parte del objetivo cumple cada comando de systemctl.", "malo": "Repasa los comandos básicos de systemctl para administrar servicios y la diferencia entre iniciar y habilitar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Instalaste nginx en un servidor con systemd y quieres que el servicio quede corriendo ahora y que además se inicie solo en cada arranque. ¿Qué comando lo logra en un solo paso?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('sudo systemctl start nginx', FALSE,
     'Lo inicia ahora, pero no lo habilita para que arranque solo después de un reinicio.', 1),
    ('sudo systemctl enable --now nginx', TRUE,
     'enable lo habilita en el arranque y --now además lo inicia de inmediato.', 2),
    ('sudo systemctl enable nginx', FALSE,
     'Lo habilita para el próximo arranque, pero no lo inicia ahora.', 3),
    ('sudo systemctl reload nginx', FALSE,
     'Recarga la configuración de un servicio que ya está activo; no lo habilita en el arranque.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Instalaste nginx en un servidor con systemd y quieres que el servicio quede corriendo ahora y que además se inicie solo en cada arranque. ¿Qué comando lo logra en un solo paso?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'journalctl consulta el journal de systemd: la opción -u filtra por unidad y --since today limita la salida desde la medianoche del día actual. Así, journalctl -u api --since today muestra solo los mensajes del servicio api de hoy, y se puede agregar -p err para ver solo los errores.',
    '{"metodo": "TECNICA", "criterios": ["Usa journalctl para consultar los logs", "Filtra por servicio con la opción -u", "Limita el tiempo con --since today", "Diferencia los logs del estado o de la definición de la unidad"], "palabras_clave": ["journalctl", "--since", "today", "unidad", "systemd"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Usaría journalctl -u api --since today. El parámetro -u limita los mensajes a esa unidad de systemd y --since today descarta todo lo anterior a las 00:00 de hoy.", "Los logs de los servicios de systemd se consultan en el journal. Filtrando por la unidad api y por el período de hoy obtengo exactamente lo que pasó con el servicio desde la medianoche, a diferencia de systemctl status, que solo muestra las últimas líneas."], "sinonimos": [["logs", "registros", "bitácora"], ["unidad", "servicio", "unit"]], "feedback_generico": {"correcto": "¡Excelente! Filtraste los logs por servicio y por tiempo con precisión.", "medio": "Vas bien, pero revisa si el comando filtra tanto por el servicio como por el período pedido.", "malo": "Repasa cómo consultar logs con journalctl y sus filtros por unidad y por fecha."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un servicio de systemd llamado ''api'' falló esta mañana. ¿Qué comando muestra solo los logs de ese servicio desde la medianoche de hoy?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('journalctl -b -f', FALSE,
     'Muestra en vivo los logs de todo el sistema desde el último arranque, sin filtrar por servicio.', 1),
    ('systemctl status api', FALSE,
     'Muestra el estado del servicio y solo sus últimas líneas de log, no todo lo ocurrido desde la medianoche.', 2),
    ('journalctl -u api --since today', TRUE,
     '-u filtra por la unidad ''api'' y --since today muestra desde las 00:00 del día actual.', 3),
    ('cat /etc/systemd/system/api.service', FALSE,
     'Muestra la definición de la unidad, es decir, cómo se ejecuta, no sus logs.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un servicio de systemd llamado ''api'' falló esta mañana. ¿Qué comando muestra solo los logs de ese servicio desde la medianoche de hoy?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Se genera el par de llaves en el equipo cliente con ssh-keygen y se copia solo la llave pública al archivo ~/.ssh/authorized_keys del usuario en el servidor, por ejemplo con ssh-copy-id. La llave privada se queda en el cliente, idealmente protegida con una frase de contraseña.',
    '{"metodo": "TECNICA", "criterios": ["Genera el par de llaves con ssh-keygen en el cliente", "Copia solo la llave pública al servidor", "Ubica la llave en ~/.ssh/authorized_keys", "Protege la llave privada y no la comparte"], "palabras_clave": ["ssh-keygen", "llave pública", "llave privada", "authorized_keys", "ssh-copy-id"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["En mi equipo ejecuto ssh-keygen y luego ssh-copy-id usuario@servidor, que agrega mi llave pública al authorized_keys del servidor. La privada nunca se copia y conviene protegerla con una passphrase.", "La autenticación con llaves funciona porque el servidor conoce mi llave pública y yo demuestro tener la privada. Por eso solo la pública va al servidor, en el archivo authorized_keys de mi usuario, y la privada queda guardada en mi computador."], "sinonimos": [["llave", "clave"], ["frase de contraseña", "passphrase"]], "feedback_generico": {"correcto": "¡Muy bien! Sabes configurar el acceso con llaves y proteger la parte sensible.", "medio": "Vas bien, pero revisa qué llave debe quedar en el servidor y en qué archivo se guarda.", "malo": "Repasa cómo funciona la autenticación SSH con llaves públicas y privadas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Quieres acceder por SSH a un servidor Linux usando autenticación con llaves en lugar de contraseña. ¿Cuál es el procedimiento correcto?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Generar el par con ssh-keygen en tu equipo y copiar solo la llave pública a ~/.ssh/authorized_keys del servidor.', TRUE,
     'La llave privada nunca sale de tu equipo; el servidor solo necesita la pública para verificarte, y ssh-copy-id automatiza la copia.', 1),
    ('Generar el par con ssh-keygen en tu equipo y copiar la llave privada al servidor para que pueda validarte.', FALSE,
     'La llave privada no debe salir nunca de tu equipo; el servidor valida con la llave pública.', 2),
    ('Generar el par en el servidor y dejar ambas llaves en /etc/ssh para que todos los usuarios puedan usarlas.', FALSE,
     'Compartir llaves entre usuarios elimina la trazabilidad, y dejar la privada en el servidor la expone.', 3),
    ('Agregar la llave pública al archivo /etc/passwd del servidor, en la línea que corresponde a tu usuario.', FALSE,
     '/etc/passwd guarda datos de las cuentas; SSH busca las llaves públicas en ~/.ssh/authorized_keys.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Quieres acceder por SSH a un servidor Linux usando autenticación con llaves en lugar de contraseña. ¿Cuál es el procedimiento correcto?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo programo con cron, cuidando los permisos, las rutas absolutas y la redirección de la salida. 1) Doy permiso de ejecución con chmod +x /opt/scripts/respaldo.sh y lo pruebo a mano. 2) Edito el crontab del usuario que debe ejecutarlo, por ejemplo root, con sudo crontab -e. 3) Agrego la línea 30 2 * * * /opt/scripts/respaldo.sh >> /var/log/respaldo.log 2>&1, donde los campos son minuto, hora, día del mes, mes y día de la semana. 4) La redirección >> agrega la salida al log y 2>&1 envía también los errores. 5) Dentro del script uso rutas absolutas, porque cron tiene un PATH reducido. 6) Verifico con sudo crontab -l y, al día siguiente, reviso el log y los registros de cron.',
    '{"metodo": "TECNICA", "criterios": ["Da permiso de ejecución y prueba el script", "Edita el crontab con crontab -e", "Escribe la expresión 30 2 * * * correctamente", "Redirige la salida y los errores al log con 2>&1", "Usa rutas absolutas y verifica la ejecución"], "palabras_clave": ["crontab", "30 2", "redirección", "rutas absolutas", "chmod +x"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero hago ejecutable el script y lo pruebo manualmente. Luego, con crontab -e del usuario adecuado, agrego 30 2 * * * con la ruta completa del script y redirijo la salida estándar y la de errores al archivo de log. Como cron no carga el mismo entorno que mi terminal, uso rutas absolutas y después reviso el log para confirmar que corrió.", "Uso cron, que ejecuta tareas según cinco campos de tiempo: minuto 30 y hora 2, con asterisco en el resto para que sea diario. Agrego la redirección >> al log y 2>&1 para capturar los errores, compruebo la entrada con crontab -l y reviso el log y el registro de cron tras la primera ejecución."], "sinonimos": [["crontab", "tabla de cron"], ["errores", "stderr", "salida de error"], ["log", "registro", "bitácora"]], "feedback_generico": {"correcto": "¡Excelente! Programaste la tarea correctamente y consideraste los detalles que suelen hacer fallar a cron.", "medio": "Vas bien con la programación, pero te faltó algún detalle, como la redirección de errores, las rutas o la verificación.", "malo": "Repasa la sintaxis de crontab, sus cinco campos de tiempo y cómo redirigir la salida de un comando."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Necesitas que el script /opt/scripts/respaldo.sh se ejecute todos los días a las 2:30 AM y que su salida y sus errores queden guardados en un log. ¿Cómo lo configuras y lo verificas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Primero mido, después libero espacio de forma segura y finalmente evito que se repita. 1) Con df -h confirmo qué sistema de archivos está lleno, y con df -i descarto que se hayan agotado los inodos. 2) Busco los directorios más pesados con sudo du -xh --max-depth=1 / | sort -h y voy bajando por los más grandes. 3) Reviso los lugares típicos: logs en /var/log, el journal, la caché de paquetes y los archivos temporales. 4) Libero espacio con seguridad, por ejemplo con journalctl --vacuum-size=500M y apt clean, y comprimo o muevo logs antiguos en vez de borrar a ciegas. 5) Si df sigue lleno tras borrar, busco archivos eliminados que siguen abiertos con lsof +L1 y reinicio ese servicio. 6) Configuro logrotate y alertas de disco.',
    '{"metodo": "TECNICA", "criterios": ["Confirma la partición llena con df y revisa los inodos", "Localiza lo que ocupa espacio con du", "Libera espacio de forma segura sin borrar a ciegas", "Considera archivos borrados que siguen abiertos", "Previene con logrotate o alertas"], "palabras_clave": ["df -h", "du", "logs", "lsof", "logrotate", "inodos"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Uso df -h para ver qué partición está llena y du para ir encontrando los directorios más grandes, normalmente logs o cachés. Libero espacio de forma controlada, por ejemplo limpiando el journal, la caché de apt o comprimiendo logs viejos. Si el espacio no se libera, reviso con lsof si un proceso mantiene abierto un archivo borrado y lo reinicio, y al final configuro la rotación de logs.", "No borro nada sin saber qué es. Mido con df y du, identifico al responsable, que suele ser un log que crece sin control, y lo reduzco o roto con cuidado. También reviso los inodos y los archivos eliminados que siguen abiertos, y dejo monitoreo para recibir una alerta antes de llegar al 100%."], "sinonimos": [["partición", "sistema de archivos", "filesystem"], ["rotación de logs", "logrotate"]], "feedback_generico": {"correcto": "¡Muy bien! Diagnosticaste con método, liberaste espacio con cuidado y pensaste en prevenir.", "medio": "Vas bien, pero te faltó algún caso típico, como los inodos, los archivos borrados abiertos o la prevención.", "malo": "Repasa los comandos para analizar el uso de disco en Linux y las causas más comunes de una partición llena."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un servidor Linux muestra el error ''No space left on device'' y la partición raíz está al 100%. ¿Cómo encuentras qué ocupa el espacio y lo resuelves sin romper nada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Reviso desde el servicio hacia la red, para ubicar en qué punto se bloquea la conexión. 1) Con systemctl status confirmo que el servicio está activo y sin errores. 2) Con ss -tulpn veo si escucha en el puerto esperado y en qué dirección: si escucha solo en 127.0.0.1, no aceptará conexiones externas. 3) Pruebo localmente con curl http://localhost y el puerto del servicio. 4) Reviso la IP y las rutas del servidor con ip a e ip route. 5) Reviso el firewall con ufw status o firewall-cmd --list-all y abro el puerto si falta. 6) Pruebo desde otro equipo con curl o nc y reviso los logs del servicio con journalctl.',
    '{"metodo": "TECNICA", "criterios": ["Verifica el estado del servicio con systemctl", "Usa ss para revisar el puerto y la dirección de escucha", "Detecta si el servicio escucha solo en localhost", "Revisa el firewall del servidor", "Prueba desde el propio servidor y desde otro equipo"], "palabras_clave": ["ss -tulpn", "puerto", "localhost", "firewall", "curl", "journalctl"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero confirmo que el servicio está activo y luego uso ss para ver si realmente escucha en el puerto y en todas las interfaces, porque si está ligado a localhost solo responde desde el propio servidor. Después pruebo con curl en local, reviso el firewall con ufw o firewalld y finalmente pruebo desde otro equipo.", "Separo capas: proceso, puerto, red y firewall. systemctl y journalctl me dicen si el servicio funciona, ss me muestra en qué dirección y puerto escucha, ip me confirma la configuración de red y el firewall me dice si el puerto está permitido. Con curl o nc compruebo cada paso."], "sinonimos": [["firewall", "cortafuegos"], ["localhost", "127.0.0.1", "loopback"]], "feedback_generico": {"correcto": "¡Excelente! Tu diagnóstico recorre cada capa y usa las herramientas correctas de Linux.", "medio": "Vas bien, pero te faltó revisar algún punto, como la dirección en que escucha el servicio o el firewall.", "malo": "Repasa las herramientas de red en Linux, como ss, ip y curl, y cómo se configura el firewall del servidor."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un servicio web en un servidor Linux no responde desde otros equipos de la red, aunque el proceso parece estar corriendo. ¿Cómo lo diagnosticas con herramientas de Linux?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En Linux, el load average cuenta los procesos que se están ejecutando, los que esperan CPU y los que están en espera no interrumpible, normalmente por E/S. Con CPU baja e iowait alto, el load elevado se explica por procesos bloqueados esperando al disco, así que hay que investigar el almacenamiento con herramientas como iostat o iotop.',
    '{"metodo": "TECNICA", "criterios": ["Explica qué cuenta el load average en Linux", "Relaciona el iowait alto con la espera de E/S", "Descarta la saturación de CPU por el bajo uso", "Propone investigar el almacenamiento con iostat o iotop"], "palabras_clave": ["load average", "iowait", "estado D", "iostat", "disco", "cuello de botella"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El load alto no siempre es CPU. En Linux también suman los procesos bloqueados esperando entrada y salida, y el iowait alto confirma que el sistema pasa tiempo esperando al disco. Revisaría el almacenamiento con iostat o iotop antes de pensar en más CPU.", "Como la CPU está casi ociosa, el problema no es de procesamiento. Los procesos están en estado D esperando operaciones de disco, lo que infla el load average; hay que buscar qué proceso genera tanta E/S o si el almacenamiento está degradado."], "sinonimos": [["E/S", "entrada y salida", "I/O"], ["cuello de botella", "bottleneck"], ["almacenamiento", "disco"]], "feedback_generico": {"correcto": "¡Excelente! Interpretaste las métricas en conjunto y no te quedaste solo con el número del load.", "medio": "Vas bien, pero revisa qué procesos cuenta el load average en Linux y qué indica el iowait.", "malo": "Repasa cómo interpretar top: el load average, el uso de CPU y sus distintos componentes, como el iowait."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un servidor de 4 núcleos muestra en top un load average sostenido de 12, pero el uso de CPU es bajo y el valor ''wa'' (iowait) es alto. ¿Cuál es la interpretación más acertada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('La CPU está saturada y hay que agregar núcleos, porque el load triplica la cantidad de núcleos.', FALSE,
     'Si la CPU estuviera saturada, su uso sería alto; aquí está baja y lo alto es la espera de E/S.', 1),
    ('El valor es un error de top, porque el load average solo cuenta los procesos que usan la CPU.', FALSE,
     'En Linux el load average también cuenta los procesos en espera no interrumpible (estado D), típicamente esperando al disco.', 2),
    ('Falta memoria RAM, porque el load average mide el porcentaje de memoria ocupada por los procesos.', FALSE,
     'El load average no mide memoria; mide procesos en ejecución o esperando CPU o E/S.', 3),
    ('Hay muchos procesos esperando E/S, así que el cuello de botella probablemente es el almacenamiento.', TRUE,
     'El iowait alto con la CPU ociosa indica procesos bloqueados esperando al disco; se confirma con iostat o iotop.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un servidor de 4 núcleos muestra en top un load average sostenido de 12, pero el uso de CPU es bajo y el valor ''wa'' (iowait) es alto. ¿Cuál es la interpretación más acertada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Los servicios de systemd no heredan el entorno de tu terminal ni leen archivos como ~/.bashrc. La variable se define en la unidad con Environment=DB_HOST=valor o con EnvironmentFile= apuntando a un archivo; después de editar la unidad se ejecuta systemctl daemon-reload y se reinicia el servicio.',
    '{"metodo": "TECNICA", "criterios": ["Explica que systemd no hereda el entorno de la terminal", "Usa Environment= o EnvironmentFile= en la unidad", "Ejecuta systemctl daemon-reload tras editar la unidad", "Reinicia el servicio y verifica"], "palabras_clave": ["Environment", "EnvironmentFile", "daemon-reload", "variables de entorno", "unidad"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Como systemd arranca los servicios con un entorno propio y limpio, el export de mi terminal no llega. Hay que declarar la variable en el archivo de la unidad, con Environment o con un EnvironmentFile, recargar systemd con daemon-reload y reiniciar el servicio.", "La solución persistente es configurar la variable en la definición del servicio, por ejemplo con systemctl edit para crear un override con Environment=DB_HOST=valor. Archivos como .bashrc o /etc/profile no aplican, porque systemd no inicia los servicios desde un shell."], "sinonimos": [["variables de entorno", "variable de entorno", "environment"], ["unidad", "unit", "archivo de servicio"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes cómo systemd construye el entorno de los servicios y cómo configurarlo.", "medio": "Vas por buen camino, pero revisa dónde se define de forma persistente el entorno de un servicio y qué pasos siguen a la edición.", "malo": "Repasa cómo funcionan las variables de entorno en Linux y por qué un servicio no ve las variables de tu sesión."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Una aplicación lee la variable DB_HOST. Funciona cuando la ejecutas desde tu terminal después de hacer export DB_HOST=10.0.0.5, pero falla cuando corre como servicio de systemd. ¿Cuál es la solución correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Agregar el export al archivo ~/.bashrc del usuario que ejecuta el servicio y luego reiniciarlo.', FALSE,
     'systemd no inicia los servicios desde un shell interactivo, así que no lee ~/.bashrc.', 1),
    ('Definirla en la unidad con Environment= o EnvironmentFile=, ejecutar daemon-reload y reiniciar el servicio.', TRUE,
     'Los servicios reciben solo el entorno que define systemd, y estas directivas lo configuran de forma persistente.', 2),
    ('Ejecutar export DB_HOST en una terminal como root justo antes de reiniciar el servicio con systemctl.', FALSE,
     'El export solo afecta a esa sesión de shell; systemd inicia el servicio con su propio entorno.', 3),
    ('Reiniciar el servicio con sudo -E systemctl restart, para que herede las variables de tu sesión.', FALSE,
     'systemctl solo le pide a systemd que reinicie la unidad; el servicio no hereda el entorno de quien ejecuta el comando.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Una aplicación lee la variable DB_HOST. Funciona cuando la ejecutas desde tu terminal después de hacer export DB_HOST=10.0.0.5, pero falla cuando corre como servicio de systemd. ¿Cuál es la solución correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'set -e detiene el script cuando un comando falla, set -u lo detiene al usar una variable no definida y pipefail hace que un pipeline devuelva error si falla cualquiera de sus comandos, no solo el último. Juntos evitan que un despliegue siga avanzando en un estado inconsistente.',
    '{"metodo": "TECNICA", "criterios": ["Explica que -e detiene el script ante un error", "Explica que -u falla con variables no definidas", "Explica el efecto de pipefail en los pipelines", "Relaciona la línea con scripts más seguros y predecibles"], "palabras_clave": ["set -e", "variables no definidas", "pipefail", "pipeline", "código de salida"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Con esa línea el script se vuelve estricto: termina apenas un comando devuelve un código de salida distinto de cero, no permite usar variables vacías por un error de tipeo y detecta fallas en medio de un pipeline. Así un despliegue no sigue ejecutándose después de un error.", "Es el llamado modo estricto de Bash. La opción -e corta ante errores, -u evita que una variable mal escrita se expanda como vacía y -o pipefail propaga el error de cualquier comando de una tubería, lo que hace el script más seguro."], "sinonimos": [["pipeline", "tubería", "pipe"], ["código de salida", "exit code", "estado de salida"]], "feedback_generico": {"correcto": "¡Excelente! Conoces bien el modo estricto de Bash y por qué importa en scripts críticos.", "medio": "Vas bien, pero revisa qué controla cada una de las opciones de esa línea.", "malo": "Repasa el manejo de errores en Bash: códigos de salida, el comando set y sus opciones más usadas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'En un script Bash de despliegue que usará todo el equipo, agregas al inicio la línea set -euo pipefail. ¿Qué logras con esto?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Activar el modo de depuración, de modo que el script imprima cada comando antes de ejecutarlo.', FALSE,
     'Eso lo hace set -x; -euo pipefail controla cómo se manejan los errores.', 1),
    ('Que el script ignore los errores y siga hasta el final, para que el despliegue no quede a medias.', FALSE,
     'Es justamente lo contrario: el script se detiene ante los errores en lugar de continuar.', 2),
    ('Que se detenga ante comandos fallidos o variables sin definir, y que un pipeline falle si falla cualquier parte.', TRUE,
     '-e corta ante errores, -u ante variables no definidas y pipefail hace que el pipeline devuelva el error de cualquier comando, no solo del último.', 3),
    ('Que el script se ejecute con privilegios de root y que sus variables queden protegidas contra cambios accidentales.', FALSE,
     'set no cambia privilegios ni protege variables; para eso se usan sudo y readonly.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Linux' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'En un script Bash de despliegue que usará todo el equipo, agregas al inicio la línea set -euo pipefail. ¿Qué logras con esto?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Aplico defensa en capas, reduciendo la superficie de ataque y manteniendo la trazabilidad. 1) Actualizo el sistema y activo las actualizaciones automáticas de seguridad. 2) Endurezco SSH: solo llaves, PermitRootLogin no y PasswordAuthentication no, y si es posible acceso solo desde una VPN o un bastión. 3) Configuro el firewall para permitir solo los puertos necesarios, como el 443, y restrinjo el acceso administrativo. 4) Uso cuentas personales con sudo y mínimo privilegio, y desinstalo los servicios que no se usan. 5) Ejecuto el servicio web con un usuario sin privilegios, con HTTPS y fail2ban contra ataques de fuerza bruta. 6) Envío los logs a un servidor central, monitoreo y tengo respaldos probados.',
    '{"metodo": "TECNICA", "criterios": ["Mantiene el sistema actualizado con parches de seguridad", "Endurece SSH deshabilitando root y contraseñas", "Restringe los puertos con un firewall", "Aplica mínimo privilegio y elimina servicios innecesarios", "Considera logs centralizados, monitoreo o respaldos"], "palabras_clave": ["hardening", "PermitRootLogin", "firewall", "fail2ban", "mínimo privilegio", "actualizaciones"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero parcho el sistema y dejo configuradas las actualizaciones automáticas de seguridad. Luego cierro todo lo que no sea necesario: firewall con solo los puertos del servicio, SSH solo con llaves y sin root, y servicios innecesarios desinstalados. Además uso usuarios nominales con sudo, protejo contra fuerza bruta y centralizo logs y alertas.", "Mi objetivo es reducir la superficie de ataque y poder detectar incidentes. Para eso combino actualizaciones al día, acceso administrativo restringido y con llaves, un firewall de lista blanca, servicios corriendo sin privilegios y monitoreo con logs enviados fuera del servidor, más respaldos que pueda restaurar."], "sinonimos": [["hardening", "endurecimiento", "bastionado"], ["fuerza bruta", "brute force"], ["parches", "actualizaciones"]], "feedback_generico": {"correcto": "¡Excelente! Tu plan cubre acceso, red, privilegios y monitoreo, con una mirada de defensa en capas.", "medio": "Buenas medidas, pero te faltó alguna capa importante, como el acceso remoto, el firewall o el monitoreo.", "malo": "Repasa las buenas prácticas de hardening de servidores Linux, desde las actualizaciones hasta el acceso remoto seguro."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes poner en producción un servidor Linux nuevo que quedará expuesto a Internet con un servicio web. ¿Qué medidas de endurecimiento (hardening) aplicas antes de publicarlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño un proceso automatizado, por etapas y con capacidad de reversa. 1) Inventario los servidores y los clasifico por criticidad y ambiente: desarrollo, pruebas y producción. 2) Automatizo la aplicación de parches con una herramienta de gestión de configuración como Ansible, en lugar de hacerlo a mano. 3) Aplico primero en desarrollo y pruebas, y después en producción por grupos, en ventanas de mantenimiento acordadas. 4) Antes de cada grupo tomo snapshots o respaldos y defino cómo revertir. 5) Programo los reinicios necesarios, por ejemplo por cambios de kernel, sin bajar todos los nodos de un servicio a la vez. 6) Verifico con monitoreo y reportes de cumplimiento, y aplico los parches críticos de seguridad con un proceso acelerado.',
    '{"metodo": "TECNICA", "criterios": ["Inventaria y clasifica los servidores por criticidad", "Automatiza los parches con una herramienta como Ansible", "Despliega por etapas, primero en ambientes de prueba", "Define respaldos, snapshots o un plan de reversa", "Verifica con monitoreo y considera los parches urgentes"], "palabras_clave": ["Ansible", "ventana de mantenimiento", "snapshots", "por etapas", "reversa", "kernel"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero necesito saber qué tengo, así que armo un inventario por criticidad. Luego automatizo con Ansible u otra herramienta similar y aplico los parches en olas: desarrollo, pruebas y finalmente producción en ventanas de mantenimiento. Antes de cada ola tomo snapshots y después verifico con el monitoreo que todo siga funcionando.", "La idea es pasar de algo manual a un ciclo predecible. Defino una frecuencia regular, uso automatización para aplicar y reportar, pruebo antes en ambientes no productivos, reinicio de forma escalonada para no cortar servicios y dejo un procedimiento rápido para vulnerabilidades críticas."], "sinonimos": [["parches", "actualizaciones", "patches"], ["reversa", "rollback", "vuelta atrás"], ["snapshots", "instantáneas"]], "feedback_generico": {"correcto": "¡Muy bien! Diseñaste un proceso automatizado, gradual y seguro, como se espera de un rol senior.", "medio": "Buena propuesta, pero te faltó algún elemento, como las pruebas previas, el plan de reversa o la verificación posterior.", "malo": "Repasa la gestión de parches a escala: automatización, despliegue por etapas y planes de reversa."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Administras 60 servidores Linux y hoy los parches se aplican a mano, cuando alguien se acuerda. ¿Cómo diseñas un proceso de actualización confiable?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo trato como un incidente de seguridad: contengo, preservo evidencia, investigo y reconstruyo. 1) Aíslo el servidor de la red, con el firewall o desde la plataforma, sin apagarlo, para no perder la evidencia que hay en memoria. 2) Aviso al equipo de seguridad y documento cada acción con su hora. 3) Reviso procesos y conexiones con ps y ss -tunap, e identifico el binario sospechoso. 4) Busco persistencia: tareas de cron, servicios de systemd nuevos, llaves en authorized_keys y usuarios agregados. 5) Reviso los accesos en los logs de autenticación y con el comando last. 6) No confío en limpiarlo: reconstruyo el servidor desde una imagen limpia, restauro datos verificados, roto las credenciales y corrijo la vulnerabilidad por la que entraron.',
    '{"metodo": "TECNICA", "criterios": ["Aísla el servidor sin destruir la evidencia", "Escala al equipo de seguridad y documenta", "Analiza procesos, conexiones y logs de autenticación", "Busca mecanismos de persistencia como cron, servicios o llaves", "Reconstruye desde una imagen limpia y rota las credenciales"], "palabras_clave": ["aislar", "evidencia", "persistencia", "authorized_keys", "imagen limpia", "rotar credenciales"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero corto la comunicación del servidor con la red para frenar el daño, pero lo mantengo encendido para conservar la evidencia. Con el equipo de seguridad reviso procesos, conexiones, logs de acceso y posibles puertas traseras como cron, servicios o llaves SSH agregadas. Luego reconstruyo el servidor desde cero, cambio todas las credenciales y corrijo la falla que permitió el ataque.", "Sigo un ciclo de respuesta a incidentes: contención, análisis, erradicación y recuperación. Contengo aislando el equipo, analizo con ss, ps y los logs para entender qué pasó y cómo entraron, y como un sistema comprometido no es confiable, lo reinstalo desde una imagen conocida y roto contraseñas, llaves y tokens."], "sinonimos": [["comprometido", "vulnerado", "hackeado"], ["persistencia", "puerta trasera", "backdoor"], ["rotar credenciales", "cambiar credenciales"]], "feedback_generico": {"correcto": "¡Excelente! Respondiste con método, cuidando la evidencia y sin confiar en un sistema comprometido.", "medio": "Buen enfoque, pero te faltó alguna etapa, como preservar la evidencia, buscar persistencia o rotar las credenciales.", "malo": "Repasa las etapas de respuesta a incidentes de seguridad y qué revisar en un servidor Linux sospechoso."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'El monitoreo detecta que un servidor Linux envía tráfico hacia una IP desconocida y aparece un proceso extraño consumiendo CPU. Sospechas que fue comprometido. ¿Cómo respondes?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Linux'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Redes TCP/IP

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'HTTPS es HTTP protegido con TLS y por defecto escucha en el puerto 443 sobre TCP. El puerto 80 corresponde a HTTP sin cifrar, el 22 a SSH y el 53 a DNS.',
    '{"metodo": "TECNICA", "criterios": ["Indica el puerto 443", "Indica TCP como protocolo de transporte", "Diferencia HTTPS de HTTP en el puerto 80", "Relaciona HTTPS con el cifrado TLS"], "palabras_clave": ["443", "TCP", "HTTPS", "TLS"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Se habilita el puerto 443 con TCP, que es el estándar para HTTPS. El tráfico web cifrado con TLS viaja por ese puerto, a diferencia del HTTP tradicional, que usa el 80.", "El servicio HTTPS escucha por defecto en TCP 443. Abrir el 80 serviría solo para HTTP sin cifrar, y los puertos 22 o 53 corresponden a otros servicios."], "sinonimos": [["firewall", "cortafuegos"], ["TLS", "SSL"]], "feedback_generico": {"correcto": "¡Bien! Conoces el puerto y el protocolo de transporte de uno de los servicios más usados.", "medio": "Casi: revisa bien qué combinación de puerto y protocolo de transporte corresponde a la versión cifrada del servicio web.", "malo": "Repasa la tabla de puertos comunes (HTTP, HTTPS, SSH, DNS) y si usan TCP o UDP."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Debes permitir en el firewall el acceso a un servidor web que atiende solo por HTTPS con su configuración por defecto. ¿Qué puerto y protocolo de transporte habilitas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('443/TCP', TRUE,
     'HTTPS usa por defecto el puerto 443 sobre TCP, donde se establece la sesión cifrada con TLS.', 1),
    ('80/TCP', FALSE,
     'Es el puerto por defecto de HTTP sin cifrar, no de HTTPS.', 2),
    ('22/TCP', FALSE,
     'Es el puerto de SSH, usado para administración remota, no para servir sitios web.', 3),
    ('53/UDP', FALSE,
     'Es el puerto de DNS; sirve para resolver nombres, no para atender HTTPS.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Debes permitir en el firewall el acceso a un servidor web que atiende solo por HTTPS con su configuración por defecto. ¿Qué puerto y protocolo de transporte habilitas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El RFC 1918 define tres rangos privados: 10.0.0.0/8, 172.16.0.0/12 (de 172.16.0.0 a 172.31.255.255) y 192.168.0.0/16. La dirección 172.20.15.4 está dentro del segundo rango, mientras que las otras quedan justo fuera de esos límites y son públicas.',
    '{"metodo": "TECNICA", "criterios": ["Identifica 172.20.15.4 como privada", "Conoce los tres rangos del RFC 1918", "Sabe que 172.16.0.0/12 llega hasta 172.31.255.255", "Reconoce las direcciones que quedan fuera de los rangos privados"], "palabras_clave": ["RFC 1918", "IP privada", "IP pública", "rango privado"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Solo 172.20.15.4 es privada, porque el bloque 172.16.0.0/12 cubre desde 172.16 hasta 172.31. Las otras opciones parecen privadas, pero están fuera de los rangos 10.0.0.0/8, 172.16.0.0/12 y 192.168.0.0/16.", "Los rangos privados no se enrutan en Internet y son 10.x.x.x, de 172.16.x.x a 172.31.x.x y 192.168.x.x. Por eso 172.20.15.4 es privada, y 172.32.10.5, 192.169.1.10 y 11.0.0.5 son públicas."], "sinonimos": [["IP privada", "dirección privada"], ["IP pública", "dirección pública"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces con precisión los límites de los rangos privados.", "medio": "Te acercaste, pero revisa con cuidado dónde empieza y dónde termina cada rango privado.", "malo": "Repasa los tres rangos privados del RFC 1918 y sus límites exactos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Cuál de estas direcciones IPv4 pertenece a un rango privado definido en el RFC 1918?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('172.32.10.5', FALSE,
     'El rango privado 172.16.0.0/12 va de 172.16.0.0 a 172.31.255.255, así que 172.32.x.x es pública.', 1),
    ('192.169.1.10', FALSE,
     'El rango privado es 192.168.0.0/16; 192.169.x.x queda fuera y es pública.', 2),
    ('11.0.0.5', FALSE,
     'El rango privado es 10.0.0.0/8; 11.x.x.x es una red pública.', 3),
    ('172.20.15.4', TRUE,
     'Está dentro de 172.16.0.0/12, que abarca de 172.16.0.0 a 172.31.255.255.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Cuál de estas direcciones IPv4 pertenece a un rango privado definido en el RFC 1918?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Cuando un equipo en modo automático no logra completar el intercambio DHCP (Discover, Offer, Request y Acknowledge), Windows se autoasigna una dirección APIPA del rango 169.254.0.0/16. Con esa IP solo se comunica en el enlace local, así que hay que revisar el cable o el Wi-Fi, la VLAN y el servidor DHCP.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce el rango 169.254 como APIPA", "Relaciona la falla con DHCP", "Indica que con esa IP no hay salida a Internet", "Propone revisar la conexión física, la VLAN o el servidor DHCP"], "palabras_clave": ["APIPA", "DHCP", "169.254", "enlace local", "autoasignada"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Esa dirección es de enlace local: Windows la genera solo cuando no recibe una oferta del servidor DHCP. El equipo no tendrá gateway ni salida a Internet hasta resolver el problema con DHCP o con la conexión.", "Una 169.254 indica que el cliente DHCP falló y el sistema se autoconfiguró con APIPA. Conviene revisar el cable o la red Wi-Fi, el puerto del switch y si el servidor DHCP está disponible o sin direcciones libres."], "sinonimos": [["APIPA", "dirección de enlace local", "link-local"], ["autoasignada", "autoconfigurada"]], "feedback_generico": {"correcto": "¡Excelente! Reconociste el síntoma y lo relacionaste con el servicio correcto.", "medio": "Vas bien, pero precisa qué servicio falló y qué consecuencias tiene esa dirección para el equipo.", "malo": "Repasa cómo un equipo obtiene su IP de forma automática y qué ocurre cuando ese intercambio no se completa."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un PC con Windows configurado para obtener IP automáticamente muestra en ipconfig la dirección 169.254.23.10. ¿Qué indica esto?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Que el PC recibió correctamente una IP pública desde el proveedor de Internet.', FALSE,
     '169.254.x.x no es pública ni la entrega un proveedor; es una dirección de enlace local.', 1),
    ('Que no obtuvo respuesta de un servidor DHCP y se autoasignó una dirección APIPA.', TRUE,
     'Windows usa APIPA (169.254.0.0/16) cuando no completa el intercambio DHCP, y con esa IP no saldrá a Internet.', 2),
    ('Que el servidor DNS está caído y por eso el equipo usa una dirección de respaldo.', FALSE,
     'DNS resuelve nombres; no asigna direcciones IP al equipo.', 3),
    ('Que alguien le configuró a mano una IP estática de un rango equivocado.', FALSE,
     'El equipo está en modo automático; 169.254 es el rango que Windows se asigna solo cuando falla DHCP.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Un PC con Windows configurado para obtener IP automáticamente muestra en ipconfig la dirección 169.254.23.10. ¿Qué indica esto?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El switch conecta equipos dentro de una misma red y el router conecta redes distintas. 1) El switch trabaja en la capa 2, de enlace de datos, y reenvía tramas según la dirección MAC. 2) Para eso aprende qué MAC hay detrás de cada puerto y arma su tabla de direcciones MAC. 3) El router trabaja en la capa 3, de red, y reenvía paquetes según la dirección IP de destino. 4) Usa una tabla de enrutamiento y actúa como gateway por defecto de los equipos de la LAN. 5) El router de una casa suele combinar router, switch, punto de acceso Wi-Fi, NAT y DHCP en un solo equipo.',
    '{"metodo": "TECNICA", "criterios": ["Ubica al switch en la capa 2 y al router en la capa 3", "Relaciona el switch con direcciones MAC y tramas", "Relaciona el router con direcciones IP y tabla de enrutamiento", "Explica que el router interconecta redes distintas", "Menciona el rol de gateway por defecto"], "palabras_clave": ["capa 2", "capa 3", "dirección MAC", "dirección IP", "tabla de enrutamiento", "gateway"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Un switch une dispositivos de la misma LAN y decide a qué puerto enviar cada trama mirando la dirección MAC, por eso es un equipo de capa 2. Un router, en cambio, une redes diferentes, como la LAN con Internet, y decide por dónde enviar cada paquete según la IP de destino y su tabla de rutas, trabajando en capa 3.", "La diferencia está en qué dirección usan y qué conectan. El switch usa MAC y conecta equipos de una misma red; el router usa IP, conecta redes distintas y es la puerta de enlace hacia afuera. Los equipos domésticos suelen integrar ambas funciones."], "sinonimos": [["router", "enrutador"], ["gateway", "puerta de enlace"], ["tabla de enrutamiento", "tabla de rutas"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste con claridad la función de cada equipo y la capa en que opera.", "medio": "Vas bien, pero te faltó precisar qué tipo de dirección usa cada equipo o en qué capa trabaja.", "malo": "Repasa el modelo OSI y cómo se reenvía el tráfico dentro de una red y entre redes distintas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica la diferencia entre un switch y un router, y en qué capa del modelo OSI trabaja principalmente cada uno.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'DNS traduce el nombre del sitio a la dirección IP que el equipo necesita para conectarse. 1) El equipo busca primero en su caché y en el archivo hosts. 2) Si no lo encuentra, consulta al servidor DNS configurado, que actúa como resolutor recursivo. 3) Ese servidor pregunta a los servidores raíz, luego a los del dominio de primer nivel, como .cl, y finalmente al servidor autoritativo del dominio. 4) La respuesta, por ejemplo un registro A con la IPv4, vuelve al equipo y se guarda en caché según su TTL. 5) Para comprobarlo uso nslookup con el nombre, comparo un ping al nombre con un ping a la IP y, si hay datos antiguos, limpio la caché con ipconfig /flushdns.',
    '{"metodo": "TECNICA", "criterios": ["Explica que DNS traduce nombres a direcciones IP", "Menciona la caché local y el servidor DNS configurado", "Describe la consulta a servidores raíz, TLD y autoritativo", "Menciona el registro A o el TTL", "Propone nslookup u otra prueba para verificar"], "palabras_clave": ["resolución de nombres", "registro A", "caché", "nslookup", "servidor autoritativo", "TTL"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Cuando escribo un nombre, el equipo necesita su IP. Si no la tiene en caché, le pregunta al DNS configurado, que recorre la jerarquía desde la raíz hasta el servidor autoritativo y devuelve, por ejemplo, el registro A. Para probarlo uso nslookup y compruebo si el sitio responde por IP pero no por nombre.", "DNS funciona como una guía que convierte nombres en direcciones. El resolutor consulta la jerarquía de servidores y la respuesta se guarda en caché por el tiempo que indique el TTL. Si sospecho de DNS, ejecuto nslookup, reviso qué servidor DNS tiene configurado el PC y limpio la caché con ipconfig /flushdns."], "sinonimos": [["resolutor", "resolver", "servidor recursivo"], ["caché", "cache"], ["TLD", "dominio de primer nivel"]], "feedback_generico": {"correcto": "¡Excelente! Describiste el recorrido de una consulta DNS y cómo verificarla en la práctica.", "medio": "Vas bien con el concepto, pero te faltó parte del recorrido de la consulta o una forma concreta de comprobarla.", "malo": "Repasa cómo se resuelve un nombre de dominio paso a paso y qué comandos sirven para probar DNS."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica qué ocurre con DNS cuando escribes el nombre de un sitio en el navegador y cómo comprobarías desde un PC que la resolución funciona.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'TCP privilegia la confiabilidad y UDP privilegia la rapidez y la baja latencia. 1) TCP es orientado a conexión: establece la sesión con el saludo de tres vías, SYN, SYN-ACK y ACK. 2) TCP garantiza la entrega y el orden, con acuses de recibo, retransmisiones y control de flujo. 3) UDP no establece conexión ni confirma la entrega, por lo que tiene menos sobrecarga. 4) TCP se usa en la web, el correo o SSH, donde no se puede perder información. 5) UDP se usa en consultas DNS, VoIP, videollamadas o juegos en línea, donde importa más la latencia que recuperar un paquete perdido.',
    '{"metodo": "TECNICA", "criterios": ["Explica que TCP es orientado a conexión y UDP no", "Menciona confiabilidad, orden y retransmisión en TCP", "Menciona la menor latencia y sobrecarga de UDP", "Da un ejemplo correcto para TCP", "Da un ejemplo correcto para UDP"], "palabras_clave": ["orientado a conexión", "three-way handshake", "retransmisión", "latencia", "VoIP", "confiabilidad"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["TCP abre una conexión antes de enviar datos, numera los segmentos y reenvía los que se pierden, por eso es confiable pero más pesado; lo usan HTTP, HTTPS o el correo. UDP manda datagramas sin conexión ni confirmación, es más liviano y rápido, y por eso se usa en voz sobre IP, streaming en vivo o DNS.", "La diferencia es confiabilidad contra velocidad. Con TCP una descarga llega completa y ordenada gracias a los acuses de recibo, mientras que en una llamada por VoIP con UDP es preferible perder un fragmento que esperar a que se reenvíe."], "sinonimos": [["saludo de tres vías", "three-way handshake"], ["acuse de recibo", "ACK"], ["latencia", "retardo"]], "feedback_generico": {"correcto": "¡Muy bien! Diferenciaste los dos protocolos y justificaste cada ejemplo según sus características.", "medio": "Diste buenos ejemplos, pero te faltó explicar qué característica de cada protocolo los hace adecuados.", "malo": "Repasa la capa de transporte: cómo funciona cada protocolo y qué aplicaciones se benefician de cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Cuál es la diferencia entre TCP y UDP? Da un ejemplo de aplicación o servicio que use cada uno y explica por qué.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Una máscara /26 deja 6 bits para hosts, es decir, 2^6 = 64 direcciones por subred. Se restan la dirección de red (192.168.10.0) y la de broadcast (192.168.10.63), así que quedan 62 hosts utilizables, del .1 al .62.',
    '{"metodo": "TECNICA", "criterios": ["Calcula 6 bits de host para una /26", "Obtiene 64 direcciones totales", "Resta red y broadcast para llegar a 62 hosts", "Identifica 192.168.10.63 como broadcast"], "palabras_clave": ["subnetting", "62 hosts", "broadcast", "bits de host", "máscara"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La /26 equivale a la máscara 255.255.255.192, con bloques de 64 direcciones. La primera subred va de 192.168.10.0 a 192.168.10.63, por lo que el broadcast es la .63 y los hosts válidos son 62.", "Con 26 bits de red quedan 32 menos 26, o sea 6 bits de host. Eso da 64 direcciones, de las cuales 62 se pueden asignar a equipos, y la última del bloque, la .63, es el broadcast."], "sinonimos": [["subnetting", "subneteo", "división en subredes"], ["broadcast", "difusión"], ["máscara", "máscara de subred"]], "feedback_generico": {"correcto": "¡Excelente! Hiciste el cálculo de subred completo y sin errores.", "medio": "Vas bien con el cálculo, pero revisa qué direcciones del bloque no se pueden asignar a equipos o dónde termina el bloque.", "malo": "Repasa cómo calcular el tamaño de un bloque a partir del prefijo CIDR y cómo obtener la dirección de broadcast."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'A una oficina se le asigna la red 192.168.10.0/26. ¿Cuántos hosts utilizables tiene y cuál es su dirección de broadcast?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('64 hosts utilizables y broadcast 192.168.10.63', FALSE,
     'Una /26 tiene 64 direcciones en total, pero hay que restar la de red y la de broadcast.', 1),
    ('62 hosts utilizables y broadcast 192.168.10.63', TRUE,
     'Con 6 bits de host hay 64 direcciones; menos red y broadcast quedan 62, y el bloque va de .0 a .63.', 2),
    ('62 hosts utilizables y broadcast 192.168.10.64', FALSE,
     '192.168.10.64 es la dirección de red de la siguiente subred /26, no el broadcast de esta.', 3),
    ('30 hosts utilizables y broadcast 192.168.10.31', FALSE,
     'Esos valores corresponden a una /27, que tiene solo 5 bits de host.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'A una oficina se le asigna la red 192.168.10.0/26. ¿Cuántos hosts utilizables tiene y cuál es su dirección de broadcast?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El registro MX (Mail Exchanger) indica a qué servidor o servidores se debe entregar el correo dirigido a un dominio, con una preferencia numérica. Hay que apuntarlo al nuevo servidor y considerar que el cambio se propaga según el TTL del registro.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el registro MX", "Explica que el MX define dónde se entrega el correo entrante", "Diferencia el MX de SPF, CNAME y PTR", "Considera el TTL al propagar el cambio"], "palabras_clave": ["registro MX", "correo entrante", "TTL", "SPF", "DNS"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Hay que cambiar el MX del dominio para que apunte al nuevo servidor de correo. Los servidores de otras organizaciones consultan ese registro antes de entregar un mensaje, así que mientras apunte al antiguo, el correo seguirá llegando allá.", "El registro que decide la entrega del correo entrante es el MX. Se actualiza con el nombre del nuevo servidor y se espera que expire el TTL anterior; SPF también conviene revisarlo, pero solo afecta la autorización de envío."], "sinonimos": [["registro MX", "Mail Exchanger"], ["correo entrante", "correo recibido"]], "feedback_generico": {"correcto": "¡Muy bien! Identificaste el registro correcto y entiendes qué función cumple en la entrega del correo.", "medio": "Elegiste un registro relacionado con el correo, pero revisa cuál decide realmente a dónde se entregan los mensajes.", "malo": "Repasa los tipos de registros DNS (A, CNAME, MX, TXT, PTR) y para qué sirve cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'La empresa migró su correo a un nuevo servidor, pero los correos que llegan desde Internet se siguen entregando en el servidor antiguo porque nadie actualizó el DNS. ¿Qué registro debes modificar?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El registro CNAME, porque define el alias que usa el dominio para su servicio de correo.', FALSE,
     'Un CNAME crea un alias de un nombre hacia otro, pero no indica dónde entregar el correo del dominio.', 1),
    ('El registro TXT con SPF, porque indica qué servidor recibe los correos del dominio.', FALSE,
     'SPF autoriza qué servidores pueden enviar correo en nombre del dominio; no define dónde se recibe.', 2),
    ('El registro PTR, porque la resolución inversa decide a qué servidor se entrega el correo.', FALSE,
     'El PTR asocia una IP con un nombre y se usa en la reputación del remitente, no para decidir la entrega.', 3),
    ('El registro MX, porque indica a qué servidor se deben entregar los correos del dominio.', TRUE,
     'Los servidores remitentes consultan el MX del dominio para saber a qué servidor de correo entregar los mensajes.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'La empresa migró su correo a un nuevo servidor, pero los correos que llegan desde Internet se siguen entregando en el servidor antiguo porque nadie actualizó el DNS. ¿Qué registro debes modificar?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Cada VLAN es un dominio de broadcast y, en la práctica, una subred distinta, por lo que el tráfico entre ellas debe enrutarse. Se necesita un router (por ejemplo, router-on-a-stick con un enlace troncal 802.1Q) o un switch capa 3 con interfaces SVI, y cada PC debe usar como gateway la IP de su VLAN.',
    '{"metodo": "TECNICA", "criterios": ["Explica que cada VLAN es un dominio de broadcast distinto", "Indica que se requiere enrutamiento entre VLAN", "Menciona un router con troncal o un switch capa 3", "Indica que cada PC usa como gateway la interfaz de su VLAN"], "palabras_clave": ["enrutamiento entre VLAN", "capa 3", "gateway", "troncal", "SVI"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Las VLAN aíslan el tráfico en capa 2, así que dos equipos en VLAN distintas solo se comunican si un equipo de capa 3 enruta entre ellas. Se puede usar un switch capa 3 con una interfaz virtual por VLAN o un router conectado por un troncal.", "Hace falta inter-VLAN routing. Cada VLAN tiene su subred y su gateway, y el router o switch de capa 3 recibe el tráfico de una VLAN y lo reenvía a la otra, aplicando incluso ACL si se quiere controlar qué se permite."], "sinonimos": [["enrutamiento entre VLAN", "inter-VLAN routing"], ["troncal", "trunk"], ["gateway", "puerta de enlace"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes qué separa una VLAN y qué se necesita para unirlas de forma controlada.", "medio": "Vas bien, pero precisa qué tipo de dispositivo y qué configuración permiten el paso de tráfico entre VLAN.", "malo": "Repasa qué es una VLAN, qué es un dominio de broadcast y cómo se comunican redes distintas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Dos PC están conectados al mismo switch, pero uno está en la VLAN 10 y el otro en la VLAN 20, y no logran comunicarse. ¿Qué se necesita para que se comuniquen?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un router o switch capa 3 que enrute entre las VLAN y actúe como gateway de cada una.', TRUE,
     'Cada VLAN es una red distinta, así que el tráfico entre ellas debe enrutarse en capa 3.', 1),
    ('Cambiar los cables de red por categoría 6, porque los de menor categoría no transportan VLAN.', FALSE,
     'La categoría del cable no tiene relación con las VLAN; la etiqueta 802.1Q viaja dentro de la trama.', 2),
    ('Asignar a ambos PC direcciones del mismo rango IP, manteniendo cada uno en su VLAN actual.', FALSE,
     'Las VLAN separan los dominios de broadcast en capa 2; aunque compartan rango, no se verán entre VLAN distintas.', 3),
    ('Activar Spanning Tree en el switch, para que habilite el camino entre ambas VLAN.', FALSE,
     'Spanning Tree evita bucles en capa 2; no enruta tráfico entre VLAN.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Dos PC están conectados al mismo switch, pero uno está en la VLAN 10 y el otro en la VLAN 20, y no logran comunicarse. ¿Qué se necesita para que se comuniquen?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Avanzo capa por capa, desde el PC hacia afuera, hasta encontrar dónde se corta. 1) Con ipconfig /all reviso la máscara, el gateway por defecto y los servidores DNS. 2) Hago ping al gateway para confirmar la conexión con la red local. 3) Hago ping a una IP pública conocida para probar la salida a Internet sin depender de DNS. 4) Si la IP responde pero los nombres no, pruebo con nslookup, reviso los DNS configurados y limpio la caché con ipconfig /flushdns. 5) Si no llego a la IP externa, uso tracert para ver en qué salto se pierde. 6) Reviso proxy, firewall o navegador, y pregunto si hay más usuarios afectados para saber si es general.',
    '{"metodo": "TECNICA", "criterios": ["Revisa gateway y DNS con ipconfig /all", "Prueba la conectividad con el gateway", "Prueba la salida a Internet por IP sin depender de DNS", "Diagnostica DNS con nslookup si falla la resolución", "Usa tracert y revisa si el problema es general"], "palabras_clave": ["ipconfig", "ping", "gateway", "nslookup", "tracert", "DNS"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero confirmo que el PC tenga gateway y DNS correctos. Luego hago ping al gateway, después a una IP de Internet y, si eso responde, pruebo la resolución de nombres con nslookup, porque si funciona por IP y no por nombre el problema es DNS. Si no llego afuera, tracert me muestra dónde se corta y pregunto si a otros les pasa lo mismo.", "Sigo un descarte ordenado: configuración del equipo, red local, salida a Internet y resolución de nombres. Cada prueba, como el ping al gateway, el ping a una IP externa, nslookup y tracert, me dice qué capa funciona. Al final reviso proxy o firewall y verifico si es un problema de un solo usuario o general."], "sinonimos": [["gateway", "puerta de enlace", "gateway por defecto"], ["tracert", "traceroute"]], "feedback_generico": {"correcto": "¡Excelente! Tu diagnóstico es ordenado y cada prueba descarta una parte del problema.", "medio": "Vas bien, pero te faltó alguna prueba clave para separar un problema de red de uno de resolución de nombres.", "malo": "Repasa los comandos de diagnóstico de red en Windows y en qué orden conviene usarlos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un usuario dice que no tiene internet. Con ipconfig ves que su PC tiene una IP válida de la red local, pero no abre ningún sitio. ¿Qué pruebas haces y en qué orden?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El PAT permite que muchos equipos con IP privada salgan a Internet compartiendo una sola IP pública. 1) Las IP privadas no se enrutan en Internet, así que el router reemplaza la IP de origen por su IP pública. 2) Para distinguir cada conexión, también cambia el puerto de origen y guarda la relación en su tabla de traducciones. 3) Cuando llega la respuesta, consulta esa tabla y la entrega al equipo interno correcto. 4) Para publicar el servidor, configuro un reenvío de puertos o NAT estático, por ejemplo del puerto 443 público hacia la IP interna del servidor en su puerto 443. 5) El servidor debe tener una IP fija o reservada. 6) Agrego la regla en el firewall y expongo solo los puertos necesarios.',
    '{"metodo": "TECNICA", "criterios": ["Explica que PAT traduce muchas IP privadas a una pública", "Menciona el uso de puertos para distinguir conexiones", "Describe la tabla de traducciones para el tráfico de retorno", "Explica el reenvío de puertos o NAT estático para publicar el servidor", "Considera la IP fija del servidor y las reglas de firewall"], "palabras_clave": ["PAT", "IP pública", "tabla de traducciones", "reenvío de puertos", "NAT estático", "firewall"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Con PAT, todos los equipos de la oficina salen con la misma IP pública y el router los diferencia por el puerto de origen que asigna a cada conexión, guardando esa correspondencia para devolver las respuestas. Para que desde afuera lleguen a un servidor interno hay que crear un port forwarding que envíe un puerto público a la IP y puerto del servidor, con su regla de firewall.", "El router hace una traducción dinámica de dirección y puerto para el tráfico saliente. El tráfico entrante no tiene una entrada previa en la tabla, por eso se necesita un NAT estático o un reenvío de puertos hacia el servidor, que debe tener IP fija, abriendo solo los puertos del servicio."], "sinonimos": [["PAT", "NAT con sobrecarga", "NAT overload"], ["reenvío de puertos", "port forwarding", "redirección de puertos"], ["IP fija", "IP reservada"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste tanto la traducción del tráfico saliente como la publicación segura del servidor.", "medio": "Explicaste parte del mecanismo, pero te faltó detallar cómo vuelven las respuestas o cómo se publica el servidor.", "malo": "Repasa los tipos de NAT, cómo se traduce el tráfico saliente y qué se necesita para recibir conexiones desde Internet."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Explica cómo funciona el NAT con sobrecarga (PAT) en el router de una oficina y qué hay que configurar para publicar hacia Internet un servidor web interno.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Primero mido para saber si el problema es de cobertura, de interferencia o de saturación. 1) Hago un recorrido con un analizador Wi-Fi y armo un mapa de calor de la intensidad de señal en cada zona. 2) Reviso los canales: en 2,4 GHz uso solo 1, 6 y 11, que no se solapan, y comparo con las redes vecinas. 3) Busco interferencias y obstáculos, como muros gruesos, metal o microondas. 4) Reviso cuántos clientes tiene cada punto de acceso y cuánto tráfico cursa. 5) Muevo a los clientes compatibles a 5 GHz, que tiene más canales y menos interferencia, aunque menos alcance. 6) Agrego o reubico puntos de acceso, ajusto la potencia y verifico que el roaming funcione.',
    '{"metodo": "TECNICA", "criterios": ["Mide la cobertura con un analizador o un mapa de calor", "Revisa los canales y el solapamiento en 2,4 GHz", "Considera interferencias y obstáculos físicos", "Evalúa la cantidad de clientes por punto de acceso", "Propone usar 5 GHz y ajustar o agregar puntos de acceso"], "palabras_clave": ["mapa de calor", "canales", "interferencia", "2,4 GHz", "5 GHz", "punto de acceso"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["No cambiaría equipos sin medir. Con un análisis de sitio veo dónde la señal es débil y qué canales usan mis puntos de acceso y los vecinos. Después reorganizo los canales, llevo a los equipos compatibles a la banda de 5 GHz y agrego o muevo puntos de acceso donde falta cobertura.", "Separo tres causas posibles: poca señal, interferencia y demasiados usuarios por equipo. Para la primera reubico o sumo puntos de acceso, para la segunda uso canales que no se solapen y la banda de 5 GHz, y para la tercera reparto la carga entre más puntos de acceso con un roaming bien configurado."], "sinonimos": [["punto de acceso", "AP", "access point"], ["mapa de calor", "heatmap"], ["análisis de sitio", "site survey"], ["Wi-Fi", "red inalámbrica", "WLAN"]], "feedback_generico": {"correcto": "¡Excelente! Basaste tu propuesta en mediciones y atacaste cada posible causa de la lentitud.", "medio": "Buenas ideas, pero te faltó medir antes de actuar o considerar alguna causa, como la interferencia o la saturación.", "malo": "Repasa los factores que afectan una red Wi-Fi: cobertura, bandas, canales, interferencia y cantidad de clientes."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En una oficina los usuarios se quejan de que el Wi-Fi es lento y se corta en ciertas zonas. ¿Cómo lo diagnosticas y qué mejoras propones?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Con VLSM se asigna primero la sede más grande y se usa el prefijo más pequeño que alcance: 100 hosts necesitan una /25 (126 hosts), 50 una /26 (62) y 20 una /27 (30). Así quedan 10.0.0.0/25, 10.0.0.128/26 y 10.0.0.192/27, contiguas y sin solaparse, con 10.0.0.224/27 libre para crecer.',
    '{"metodo": "TECNICA", "criterios": ["Ordena las sedes de mayor a menor necesidad", "Elige /25, /26 y /27 según los hosts requeridos", "Verifica que las subredes no se solapen", "Calcula correctamente los hosts utilizables de cada prefijo"], "palabras_clave": ["VLSM", "solapamiento", "prefijo", "hosts utilizables", "/25"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["Se parte por la sede de 100 hosts, que necesita una /25 (de 10.0.0.0 a 10.0.0.127). Luego la de 50 recibe una /26 desde la .128 hasta la .191 y la de 20 una /27 desde la .192 hasta la .223. Las demás opciones se solapan o se quedan cortas de hosts.", "Para cada sede se busca la potencia de 2 que cubra los hosts más la red y el broadcast: 128 direcciones para 100, 64 para 50 y 32 para 20. Asignándolas en orden desde 10.0.0.0, la única opción válida es la /25, la /26 en la .128 y la /27 en la .192."], "sinonimos": [["solapamiento", "traslape", "superposición"], ["subred", "segmento"]], "feedback_generico": {"correcto": "¡Excelente! Dimensionaste cada subred y verificaste los límites de los bloques sin errores.", "medio": "Tu razonamiento va bien, pero revisa si cada bloque alcanza para los hosts requeridos y si alguno se superpone.", "malo": "Repasa VLSM: cómo elegir el prefijo según los hosts y cómo ubicar cada bloque sin que se solapen."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes dividir la red 10.0.0.0/24 con VLSM para tres sedes que necesitan 100, 50 y 20 hosts, usando para cada una el bloque más pequeño que alcance y sin solaparlos. ¿Qué asignación es correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('10.0.0.0/25, 10.0.0.64/26 y 10.0.0.128/27', FALSE,
     '10.0.0.64/26 queda dentro de 10.0.0.0/25 (que va de .0 a .127), así que las subredes se solapan.', 1),
    ('10.0.0.0/26, 10.0.0.64/26 y 10.0.0.128/27', FALSE,
     'Una /26 tiene solo 62 hosts utilizables, insuficiente para la sede de 100.', 2),
    ('10.0.0.0/25, 10.0.0.128/26 y 10.0.0.192/27', TRUE,
     'La /25 da 126 hosts (.0 a .127), la /26 da 62 (.128 a .191) y la /27 da 30 (.192 a .223), sin solaparse.', 3),
    ('10.0.0.0/25, 10.0.0.128/27 y 10.0.0.160/28', FALSE,
     'La /27 da 30 hosts, insuficiente para 50, y la /28 da 14, insuficiente para 20.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Debes dividir la red 10.0.0.0/24 con VLSM para tres sedes que necesitan 100, 50 y 20 hosts, usando para cada una el bloque más pequeño que alcance y sin solaparlos. ¿Qué asignación es correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La estrategia más usada para una transición gradual es la doble pila (dual stack): las interfaces tienen direcciones IPv4 e IPv6 a la vez y usan el protocolo que soporte el destino. Así se habilita IPv6 por etapas, se prueban los servicios y se mantiene IPv4 mientras siga siendo necesario.',
    '{"metodo": "TECNICA", "criterios": ["Elige la doble pila como estrategia de transición", "Explica que conviven IPv4 e IPv6 en las mismas interfaces", "Plantea una migración gradual por etapas", "Descarta el uso de direcciones link-local para salir a Internet"], "palabras_clave": ["doble pila", "dual stack", "IPv6", "transición gradual", "link-local"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Lo recomendable es dual stack: cada equipo y cada red tienen IPv4 e IPv6 simultáneamente. De esa forma los servicios nuevos pueden usar IPv6 mientras los antiguos siguen con IPv4, y la migración se hace por etapas.", "Una transición segura no apaga IPv4 de golpe. Con doble pila ambos protocolos conviven, se planifica el direccionamiento IPv6, se prueba en segmentos piloto y se retira IPv4 recién cuando ya no quedan dependencias."], "sinonimos": [["doble pila", "dual stack"], ["link-local", "enlace local"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste una estrategia de transición realista y de bajo riesgo.", "medio": "Vas bien, pero revisa qué estrategia permite que ambos protocolos convivan mientras se completa la migración.", "malo": "Repasa los mecanismos de transición de IPv4 a IPv6 y las características básicas de las direcciones IPv6."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu empresa quiere adoptar IPv6 de forma gradual sin perder el acceso a servicios que solo funcionan con IPv4. ¿Qué estrategia es la más adecuada para la red corporativa?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Apagar IPv4 en toda la red durante un fin de semana y migrar todos los equipos a IPv6 de una vez.', FALSE,
     'Un corte total rompe el acceso a todo lo que aún depende de IPv4 y concentra el riesgo en un solo evento.', 1),
    ('Implementar doble pila, para que equipos y redes usen IPv4 e IPv6 al mismo tiempo durante la transición.', TRUE,
     'Dual stack permite usar IPv6 donde ya está disponible sin perder IPv4, y migrar servicio por servicio.', 2),
    ('Usar solo direcciones link-local fe80::/10 en los equipos, porque permiten salir a Internet sin más planificación.', FALSE,
     'Las direcciones link-local solo sirven dentro del enlace local y no se enrutan.', 3),
    ('Convertir cada IPv4 a IPv6 cambiando su máscara por un prefijo /64 y mantener el plan de direcciones actual.', FALSE,
     'IPv4 e IPv6 son protocolos distintos, con direcciones de 32 y 128 bits; no se convierte una en otra cambiando la máscara.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu empresa quiere adoptar IPv6 de forma gradual sin perder el acceso a servicios que solo funcionan con IPv4. ¿Qué estrategia es la más adecuada para la red corporativa?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Con muchas sedes y enlaces redundantes conviene un protocolo de enrutamiento dinámico, como OSPF, que intercambia información entre routers y recalcula rutas automáticamente ante fallas. Las rutas estáticas siguen siendo útiles en casos puntuales, pero a esta escala son difíciles de mantener y no aprovechan la redundancia.',
    '{"metodo": "TECNICA", "criterios": ["Elige enrutamiento dinámico para una red de 15 sedes", "Menciona un protocolo como OSPF", "Explica la convergencia automática ante caídas", "Reconoce las limitaciones de las rutas estáticas a esta escala"], "palabras_clave": ["enrutamiento dinámico", "OSPF", "convergencia", "rutas estáticas", "redundancia"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["A esta escala lo adecuado es pasar a enrutamiento dinámico. Un protocolo como OSPF permite que los routers descubran las redes y elijan otro camino cuando un enlace se cae, sin intervención manual.", "Las rutas estáticas sirven en redes pequeñas, pero con 15 sedes y caminos redundantes se vuelven inmanejables y no conmutan solas ante una falla. Un protocolo de estado de enlace como OSPF aprovecha la redundancia y reduce los errores de configuración."], "sinonimos": [["enrutamiento", "ruteo"], ["convergencia", "reconvergencia"], ["casa matriz", "sede central"]], "feedback_generico": {"correcto": "¡Excelente! Tomaste una decisión de arquitectura que escala y aprovecha la redundancia.", "medio": "Te acercaste, pero revisa qué opción reacciona sola ante fallas y sigue siendo mantenible con muchas sedes.", "malo": "Repasa las diferencias entre enrutamiento estático y dinámico y cuándo conviene cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Una empresa pasó de 2 a 15 sedes interconectadas con enlaces redundantes, y hoy todo funciona con rutas estáticas. ¿Qué decisión de enrutamiento es la más adecuada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Mantener solo rutas estáticas, porque con muchas sedes y enlaces redundantes escalan mejor que un protocolo.', FALSE,
     'Con muchas sedes, las rutas estáticas se vuelven difíciles de mantener y no reaccionan solas ante la caída de un enlace.', 1),
    ('Configurar en todas las sedes una ruta por defecto hacia la casa matriz y dejar sin uso los enlaces redundantes.', FALSE,
     'Desaprovecha la redundancia y deja a la casa matriz como punto único de falla.', 2),
    ('Unir las 15 sedes en una sola VLAN extendida, para que todo quede en la misma red y no haya que enrutar.', FALSE,
     'Crea un dominio de broadcast enorme y frágil, propenso a bucles y tormentas de broadcast.', 3),
    ('Implementar un protocolo de enrutamiento dinámico, como OSPF, que recalcule las rutas si cae un enlace.', TRUE,
     'OSPF aprende las rutas automáticamente y converge hacia caminos alternativos cuando falla un enlace.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Redes TCP/IP' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Una empresa pasó de 2 a 15 sedes interconectadas con enlaces redundantes, y hoy todo funciona con rutas estáticas. ¿Qué decisión de enrutamiento es la más adecuada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño una red segmentada, con direccionamiento planificado y control del tráfico entre segmentos. 1) Creo una VLAN por función: administración, ventas, invitados, cámaras y una de gestión para los equipos de red. 2) Asigno a cada VLAN una subred privada con margen de crecimiento, por ejemplo una /24 dentro de 10.0.0.0/8. 3) Enruto entre VLAN en un switch capa 3 o en el firewall, con reglas: invitados solo hacia Internet y cámaras aisladas, accesibles solo desde el grabador. 4) Configuro DHCP por VLAN, con relay si el servidor está centralizado, y DNS interno. 5) Asocio cada SSID del Wi-Fi a su VLAN. 6) Agrego redundancia en enlaces y energía, y dejo documentación y monitoreo.',
    '{"metodo": "TECNICA", "criterios": ["Segmenta la red en VLAN por función", "Planifica subredes con margen de crecimiento", "Controla el tráfico entre VLAN con reglas o ACL", "Aísla a los invitados y las cámaras", "Considera DHCP, Wi-Fi por SSID, redundancia o documentación"], "palabras_clave": ["VLAN", "segmentación", "subredes", "ACL", "firewall", "SSID"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Separaría la red en VLAN para cada área, más una de gestión, y les daría subredes privadas con espacio para crecer. El enrutamiento entre ellas pasaría por un firewall o switch de capa 3 con ACL, de modo que los invitados solo salgan a Internet y las cámaras queden aisladas. Completaría con DHCP por VLAN, SSID asociados a cada VLAN, redundancia y documentación.", "La base es segmentar y controlar. Cada tipo de usuario o dispositivo tiene su VLAN y su subred, y la comunicación entre ellas se permite solo cuando hace falta, mediante reglas. Así un problema en la red de invitados o en una cámara no afecta a administración, y el diseño se puede monitorear y escalar."], "sinonimos": [["ACL", "listas de control de acceso", "reglas de firewall"], ["segmentación", "segmentar"], ["grabador", "NVR"]], "feedback_generico": {"correcto": "¡Excelente! Tu diseño es segmentado, seguro y pensado para crecer y operarse bien.", "medio": "Buen diseño base, pero te faltó algún elemento clave, como el control entre segmentos o el aislamiento de dispositivos sensibles.", "malo": "Repasa los principios de diseño de una red LAN: segmentación con VLAN, plan de direccionamiento y control del tráfico."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes diseñar la red de una oficina nueva de 120 personas con áreas de administración, ventas, invitados y cámaras IP. ¿Cómo la diseñas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Investigo con datos para separar si el problema es de la red, del enlace o de la aplicación. 1) Acoto quiénes se ven afectados, en qué horarios y con qué funciones de la aplicación. 2) Reviso el uso del enlace de la sucursal en esos horarios con monitoreo SNMP o NetFlow, para detectar saturación y qué tráfico la causa. 3) Mido latencia, pérdida y jitter con pings continuos y con tracert o mtr hacia el servidor. 4) Reviso errores en las interfaces, como CRC o problemas de dúplex. 5) Si hace falta, capturo tráfico con Wireshark para ver retransmisiones TCP. 6) Según la causa, aplico QoS para priorizar la aplicación, amplío el enlace o escalo al equipo de la aplicación.',
    '{"metodo": "TECNICA", "criterios": ["Acota el patrón de usuarios, horarios y funciones afectadas", "Revisa la utilización del enlace con monitoreo", "Mide latencia, pérdida y jitter hacia el servidor", "Analiza errores de interfaz o capturas de tráfico", "Propone soluciones como QoS, más ancho de banda o escalar"], "palabras_clave": ["latencia", "pérdida de paquetes", "NetFlow", "Wireshark", "QoS", "ancho de banda"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Como el patrón depende del horario, sospecho de saturación, pero lo confirmo con datos. Reviso los gráficos de uso del enlace, mido latencia y pérdida hacia el datacenter y busco errores en las interfaces. Si el enlace se satura por otro tráfico, aplico QoS o amplío el ancho de banda; si la red está bien, escalo al equipo de la aplicación con evidencia.", "Primero correlaciono los reportes con las métricas de red de esos horarios. Luego uso pruebas activas, como ping continuo y mtr, y una captura con Wireshark para ver retransmisiones o demoras del servidor. Con eso decido si corresponde priorizar tráfico, aumentar la capacidad o revisar la aplicación."], "sinonimos": [["ancho de banda", "capacidad del enlace"], ["pérdida de paquetes", "packet loss"], ["jitter", "variación de latencia"]], "feedback_generico": {"correcto": "¡Muy bien! Investigaste con método y datos, y tu propuesta depende de la causa encontrada.", "medio": "Buen enfoque, pero te faltó alguna medición clave o separar mejor si la causa está en la red o en la aplicación.", "malo": "Repasa las herramientas de monitoreo y diagnóstico de red para problemas intermitentes de rendimiento."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Usuarios de una sucursal reportan lentitud intermitente al usar una aplicación alojada en el datacenter central, sobre todo en ciertos horarios. ¿Cómo lo investigas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Agrego redundancia en cada capa y la pruebo, para que una sola falla no deje sin servicio a la casa matriz. 1) Identifico todos los puntos únicos de falla: router, enlace, switches centrales y energía. 2) Instalo un segundo router o firewall y uso un protocolo de redundancia de primer salto, como VRRP o HSRP, con una IP virtual de gateway compartida. 3) Contrato un segundo enlace a Internet con otro proveedor y configuro conmutación automática ante fallas o balanceo de carga. 4) Duplico los enlaces hacia los switches centrales con agregación de enlaces y uso Spanning Tree para evitar bucles. 5) Protejo la energía con UPS. 6) Monitoreo todo y hago pruebas periódicas de conmutación.',
    '{"metodo": "TECNICA", "criterios": ["Identifica los puntos únicos de falla", "Propone un segundo gateway con VRRP o HSRP", "Propone un segundo enlace a Internet de otro proveedor", "Considera redundancia en switches y energía", "Incluye monitoreo y pruebas de conmutación"], "palabras_clave": ["redundancia", "VRRP", "HSRP", "IP virtual", "failover", "segundo enlace"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Pondría dos routers trabajando como un gateway virtual con VRRP o HSRP, para que si uno cae el otro asuma la IP que usan los equipos. Además sumaría un segundo enlace a Internet de otro proveedor con failover automático, y redundaría switches y energía. Todo debe probarse con simulacros de falla.", "El objetivo es que ningún equipo o enlace sea indispensable. Para el gateway uso un protocolo de redundancia de primer salto, para Internet dos proveedores con conmutación automática y para el núcleo enlaces agregados y equipos duplicados, con UPS. Luego monitoreo y pruebo la conmutación para confirmar que funciona."], "sinonimos": [["failover", "conmutación por error", "conmutación automática"], ["punto único de falla", "SPOF"], ["agregación de enlaces", "LACP", "EtherChannel"]], "feedback_generico": {"correcto": "¡Excelente! Abordaste la redundancia de forma integral y no olvidaste probarla.", "medio": "Buen planteamiento, pero te faltó cubrir alguno de los puntos únicos de falla o cómo verificar que la redundancia funciona.", "malo": "Repasa los conceptos de alta disponibilidad en redes: redundancia del gateway, de los enlaces y de los equipos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La red de la casa matriz depende de un solo router como gateway por defecto y de un solo enlace a Internet. ¿Cómo eliminas esos puntos únicos de falla?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Redes TCP/IP'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Soporte técnico y Help Desk

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La prioridad se calcula combinando el impacto (cuántos usuarios afecta o qué tan crítico es el servicio) y la urgencia (qué tan rápido se necesita la solución). La caída de facturación tiene impacto y urgencia altos, mientras que el fondo de pantalla tiene ambos bajos, así que la facturación va primero.',
    '{"metodo": "TECNICA", "criterios": ["Prioriza la caída del sistema de facturación", "Define impacto como alcance o criticidad para el negocio", "Define urgencia como la rapidez con que se necesita la solución", "Descarta el orden de llegada como criterio de prioridad"], "palabras_clave": ["impacto", "urgencia", "prioridad", "matriz", "facturación"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Primero va la facturación caída, porque afecta a un área completa y frena un proceso del que depende el negocio. El cambio de fondo de pantalla es un problema menor que puede esperar sin consecuencias.", "Según la matriz, un servicio crítico detenido para muchos usuarios recibe prioridad alta o crítica, y una molestia individual sin efecto en el trabajo recibe prioridad baja. Por eso se resuelve primero la facturación."], "sinonimos": [["ticket", "caso", "incidencia"], ["prioridad", "priorización"]], "feedback_generico": {"correcto": "¡Bien! Aplicaste la matriz considerando el alcance y la criticidad de cada caso, como se espera en una mesa de ayuda.", "medio": "Vas bien encaminado, pero te faltó justificar la decisión con los dos ejes de la matriz y no solo con uno.", "malo": "Repasa la matriz de impacto y urgencia y cómo se usa para decidir qué ticket atender primero."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Llegan dos tickets al mismo tiempo: un usuario no puede cambiar el fondo de pantalla de su equipo y el sistema de facturación está caído para todo el equipo de ventas. Usando la matriz de impacto y urgencia, ¿cómo los priorizas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Atiendo primero el fondo de pantalla, porque se resuelve en minutos y así bajo la cola de tickets.', FALSE,
     'Resolver rápido no define la prioridad; ese ticket tiene bajo impacto y puede esperar sin afectar al negocio.', 1),
    ('Atiendo primero la facturación, porque afecta a muchos usuarios y detiene un proceso crítico del negocio.', TRUE,
     'Alto impacto (varios usuarios y un proceso crítico) y alta urgencia (las ventas están detenidas) dan la prioridad más alta.', 2),
    ('Los atiendo por orden de llegada, porque así se respeta la equidad entre todos los usuarios que reportan.', FALSE,
     'El orden de llegada ignora el impacto en el negocio, que es justamente lo que mide la matriz.', 3),
    ('Les doy la misma prioridad a ambos, porque los dos usuarios reportaron que no pueden trabajar como quieren.', FALSE,
     'No tienen el mismo impacto ni la misma urgencia: uno es cosmético y el otro detiene la facturación.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Llegan dos tickets al mismo tiempo: un usuario no puede cambiar el fondo de pantalla de su equipo y el sistema de facturación está caído para todo el equipo de ventas. Usando la matriz de impacto y urgencia, ¿cómo los priorizas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El Administrador de tareas (Ctrl + Shift + Esc) muestra en tiempo real cuánta CPU, memoria, disco y red usa cada proceso. Ordenando por esas columnas se identifica qué programa causa la lentitud y, si corresponde, se puede finalizar o investigar.',
    '{"metodo": "TECNICA", "criterios": ["Elige el Administrador de tareas", "Menciona que muestra el consumo en tiempo real", "Ordena los procesos por CPU, memoria o disco", "Diferencia el diagnóstico en vivo de los registros históricos"], "palabras_clave": ["Administrador de tareas", "procesos", "CPU", "memoria", "tiempo real"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Abriría el Administrador de tareas y revisaría la pestaña Procesos, ordenando por CPU, memoria y disco. Así veo qué aplicación está acaparando recursos en este momento.", "Lo primero es mirar el consumo actual con el Administrador de tareas, porque la lentitud suele venir de un proceso que usa demasiada CPU, RAM o disco. Herramientas como el Visor de eventos sirven después, para buscar causas registradas."], "sinonimos": [["Administrador de tareas", "Task Manager"], ["memoria", "RAM"], ["lento", "lentitud"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste la herramienta adecuada para un diagnóstico en vivo del consumo de recursos.", "medio": "Elegiste una herramienta útil, pero te faltó pensar en cuál muestra el consumo de cada proceso en este momento.", "malo": "Repasa las herramientas de diagnóstico de Windows y para qué sirve cada una: no todas muestran el consumo en tiempo real."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un usuario reporta que su PC con Windows está muy lento desde la mañana. ¿Qué herramienta abres primero para ver en tiempo real qué proceso está consumiendo CPU, memoria o disco?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El Visor de eventos, para revisar los errores que registró el sistema durante el día.', FALSE,
     'Sirve para revisar errores y advertencias registrados, pero no muestra el consumo de recursos en tiempo real.', 1),
    ('El Administrador de dispositivos, para comprobar si algún componente perdió su driver.', FALSE,
     'Muestra el estado del hardware y sus controladores, no qué proceso consume recursos.', 2),
    ('El Administrador de tareas, ordenando los procesos por uso de CPU, memoria o disco.', TRUE,
     'Muestra en tiempo real el consumo de cada proceso y permite finalizar el que esté causando el problema.', 3),
    ('El Liberador de espacio en disco, para borrar los archivos temporales acumulados.', FALSE,
     'Libera espacio, pero no indica qué proceso causa la lentitud; usarlo primero es actuar sin diagnóstico.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Un usuario reporta que su PC con Windows está muy lento desde la mañana. ¿Qué herramienta abres primero para ver en tiempo real qué proceso está consumiendo CPU, memoria o disco?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Primero se valida la identidad del usuario según el procedimiento de la mesa de ayuda. Luego se desbloquea la cuenta en Active Directory y se restablece la contraseña con una clave temporal, marcando que deba cambiarla en el siguiente inicio de sesión, y se registra todo en el ticket.',
    '{"metodo": "TECNICA", "criterios": ["Verifica la identidad del usuario antes de actuar", "Desbloquea la cuenta en Active Directory", "Restablece la contraseña obligando a cambiarla al iniciar sesión", "No pide ni conoce la contraseña definitiva del usuario", "Registra la atención en el ticket"], "palabras_clave": ["verificar identidad", "desbloquear", "Active Directory", "restablecer contraseña", "contraseña temporal"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Confirmo que quien llama es realmente el dueño de la cuenta, con las preguntas o el método que defina el procedimiento. Después desbloqueo la cuenta y restablezco la contraseña con una temporal que el usuario debe cambiar en su próximo inicio de sesión.", "Desde la consola Usuarios y equipos de Active Directory desbloqueo la cuenta y restablezco la clave, activando la opción de cambiarla en el siguiente inicio. Antes de eso valido la identidad del usuario y al final dejo registrado el caso en el ticket."], "sinonimos": [["contraseña", "clave", "password"], ["restablecer", "resetear"], ["cuenta bloqueada", "bloqueo de cuenta"]], "feedback_generico": {"correcto": "¡Excelente! Resolviste el caso sin comprometer la seguridad y siguiendo un procedimiento ordenado.", "medio": "Resolviste el bloqueo, pero te faltó considerar algún control de seguridad del procedimiento.", "malo": "Repasa el procedimiento de desbloqueo y restablecimiento de contraseñas en Active Directory y los controles de seguridad que lo acompañan."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un usuario llama porque su cuenta de dominio quedó bloqueada después de varios intentos fallidos y no recuerda su contraseña. Como técnico N1, ¿qué haces?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Verifico su identidad, desbloqueo la cuenta en Active Directory y le doy una clave temporal que deba cambiar al ingresar.', TRUE,
     'Valida la identidad, resuelve el bloqueo y obliga al usuario a definir una contraseña que solo él conozca.', 1),
    ('Le pido que me diga su contraseña anterior para probarla yo mismo y confirmar que la cuenta efectivamente quedó bloqueada.', FALSE,
     'Nunca se pide la contraseña a un usuario; además, eso no resuelve el bloqueo ni el olvido.', 2),
    ('Elimino su cuenta en Active Directory y le creo una nueva con el mismo nombre, para que parta de cero sin bloqueos.', FALSE,
     'Borrar la cuenta hace perder su identificador, grupos, permisos y accesos; es una medida desproporcionada.', 3),
    ('Desactivo la directiva de bloqueo de cuentas de todo el dominio, así el usuario puede seguir intentando hasta acordarse.', FALSE,
     'Debilita la seguridad de toda la organización frente a ataques de fuerza bruta y no corresponde a N1.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Un usuario llama porque su cuenta de dominio quedó bloqueada después de varios intentos fallidos y no recuerda su contraseña. Como técnico N1, ¿qué haces?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo diagnostico de lo más simple a lo más complejo, empezando por la energía. 1) Reviso que el cable de poder esté bien conectado al equipo y al enchufe, y que el interruptor trasero de la fuente esté encendido. 2) Pruebo otro enchufe o quito la zapatilla para descartar la toma eléctrica. 3) Cambio el cable de poder por uno que funcione. 4) Con el equipo desenchufado, abro el gabinete y reviso que los conectores de la fuente a la placa madre y el cable del botón de encendido estén firmes. 5) Pruebo con una fuente de poder que funcione; si así enciende, la fuente está dañada, y si no, sospecho de la placa madre y escalo a N2. 6) Registro en el ticket cada prueba y su resultado.',
    '{"metodo": "TECNICA", "criterios": ["Empieza por lo más simple: cable, enchufe e interruptor de la fuente", "Descarta la toma eléctrica y la zapatilla", "Revisa las conexiones internas con el equipo desenchufado", "Aísla la fuente de poder probando con una que funcione", "Escala o documenta según el resultado"], "palabras_clave": ["cable de poder", "enchufe", "fuente de poder", "placa madre", "botón de encendido"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero descarto lo externo: que el cable esté conectado, que el enchufe tenga energía y que el interruptor de la fuente esté encendido. Si todo eso está bien, desenchufo el equipo, lo abro y reviso los conectores internos y el botón. Si sigue sin dar señales, pruebo con otra fuente y, si no es la fuente, escalo por una posible falla de la placa madre.", "Sigo un orden de descarte desde la energía hacia adentro. Cambio el enchufe y el cable, verifico el interruptor de la fuente y luego reviso los cables internos con el equipo apagado y desconectado. Finalmente aíslo si la falla es de la fuente o de la placa madre y dejo todo documentado en el ticket."], "sinonimos": [["fuente de poder", "fuente", "PSU"], ["placa madre", "motherboard", "placa base"], ["zapatilla", "regleta", "alargador"]], "feedback_generico": {"correcto": "¡Muy bien! Tu diagnóstico es ordenado, parte por lo más simple y aísla cada componente antes de escalar.", "medio": "Buen inicio, pero te faltaron pasos para aislar el componente que falla o para documentar el caso.", "malo": "Repasa cómo se diagnostica un equipo que no enciende, descartando causas en orden de lo simple a lo complejo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un usuario reporta que su PC de escritorio no enciende: al presionar el botón no se prende ninguna luz ni giran los ventiladores. ¿Cómo lo diagnosticas paso a paso?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Manejo la llamada con empatía, lenguaje simple y confirmando cada avance. 1) Escucho sin interrumpir, reconozco su molestia y le explico que lo voy a ayudar. 2) Hago preguntas concretas para entender qué ve en pantalla y desde cuándo ocurre. 3) Le doy instrucciones de a una, sin jerga técnica, describiendo lo que verá, por ejemplo ''el ícono azul abajo a la derecha''. 4) Le pido que me confirme cada paso antes de seguir. 5) Al terminar, verifico con él que puede trabajar y le cuento brevemente qué pasó. 6) Registro la solución en el ticket y, si no se resolvió, le explico el escalamiento y los plazos.',
    '{"metodo": "TECNICA", "criterios": ["Muestra empatía y escucha activa ante la molestia", "Usa lenguaje simple y evita la jerga técnica", "Da instrucciones de a una y confirma cada paso", "Verifica con el usuario que el problema quedó resuelto", "Documenta o informa el escalamiento y los plazos"], "palabras_clave": ["empatía", "lenguaje simple", "escucha activa", "confirmar", "sin jerga"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero calmo la situación: dejo que el usuario explique, valido su frustración y le transmito que vamos a resolverlo juntos. Luego le doy pasos cortos y claros, con palabras cotidianas, y espero que me confirme cada uno. Al final compruebo que todo funciona y dejo registrado el caso.", "La clave es la paciencia y adaptar el lenguaje. Evito términos técnicos, uso referencias visuales de lo que aparece en su pantalla y avanzo solo cuando confirma cada acción. Cierro verificando que puede trabajar y explicándole qué pasó o, si hay que escalar, cuándo tendrá respuesta."], "sinonimos": [["usuario", "cliente"], ["jerga técnica", "tecnicismos"]], "feedback_generico": {"correcto": "¡Excelente! Combinaste empatía con una guía clara y verificable, que es lo que más valora un usuario no técnico.", "medio": "Vas bien con la actitud, pero te faltó describir cómo guiar los pasos o cómo cerrar la llamada.", "malo": "Repasa técnicas de atención a usuarios: manejo de la molestia, comunicación clara y cierre de la llamada."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Debes guiar por teléfono a un usuario sin conocimientos técnicos, que está molesto porque lleva horas sin poder trabajar. ¿Cómo manejas la llamada para resolver su problema?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Actúo para contener primero y limpiar después, siguiendo el procedimiento de seguridad. 1) Desconecto el equipo de la red, cable y Wi-Fi, para evitar que se propague o envíe datos. 2) Aviso o escalo al área de seguridad según el procedimiento y registro el ticket. 3) Ejecuto un análisis completo con el antivirus corporativo actualizado y, si es posible, un análisis sin conexión. 4) Reviso los programas instalados recientemente y las extensiones del navegador, y elimino lo sospechoso. 5) Pido que el usuario cambie sus contraseñas desde otro equipo seguro. 6) Si la infección persiste, reinstalo el equipo desde una imagen limpia y restauro sus datos desde un respaldo.',
    '{"metodo": "TECNICA", "criterios": ["Aísla el equipo de la red como primera medida", "Escala o informa al área de seguridad", "Analiza con antivirus actualizado y revisa programas y extensiones", "Considera el cambio de contraseñas desde otro equipo", "Propone reinstalar desde una imagen limpia si persiste"], "palabras_clave": ["aislar", "antivirus", "extensiones", "contraseñas", "imagen limpia", "seguridad"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Lo primero es contener: saco el equipo de la red para que no afecte a otros. Después informo a seguridad, hago un escaneo completo con el antimalware y reviso extensiones y software sospechoso. Si no queda limpio, lo formateo con la imagen corporativa, restauro los datos y hago que el usuario cambie sus claves.", "Trato el caso como un incidente de seguridad. Desconecto el equipo, lo reporto, lo analizo con herramientas antimalware actualizadas y elimino lo que encuentre. Como el malware pudo robar credenciales, el usuario cambia sus contraseñas desde otro dispositivo, y si hay dudas sobre la limpieza reinstalo el sistema."], "sinonimos": [["malware", "virus", "software malicioso"], ["antivirus", "antimalware"], ["reinstalar", "formatear"]], "feedback_generico": {"correcto": "¡Muy bien! Priorizaste la contención y cerraste el caso pensando en la seguridad de la cuenta del usuario.", "medio": "Buen trabajo con la limpieza, pero te faltó alguna medida de contención o de seguridad posterior.", "malo": "Repasa cómo responder ante un equipo infectado: qué hacer primero para contenerlo y cómo asegurarte de que quede limpio."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un usuario avisa que en su equipo aparecen ventanas emergentes extrañas, que el navegador cambió solo su página de inicio y que todo anda lento. Sospechas malware. ¿Qué haces?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En ITIL, un incidente es una interrupción que se gestiona para restaurar el servicio lo antes posible, y un problema es la causa, a menudo desconocida, de uno o más incidentes. Como las caídas se repiten, cada ticket se atiende como incidente y se abre un problema para encontrar la causa raíz y eliminarla.',
    '{"metodo": "TECNICA", "criterios": ["Distingue incidente de problema", "Atiende cada caída como incidente para restaurar el servicio", "Abre un registro de problema por la repetición", "Reconoce que reconectar es una solución temporal", "Busca la causa raíz"], "palabras_clave": ["incidente", "problema", "causa raíz", "ITIL", "solución temporal"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Los tickets individuales son incidentes y se resuelven reconectando para que el usuario siga trabajando. Pero como se repiten con un patrón, corresponde abrir una gestión de problemas que analice por qué se cae la VPN y proponga una solución definitiva.", "La reconexión funciona como workaround para cerrar cada incidente. La repetición diaria indica que existe una causa subyacente, así que se registra un problema, se documenta el error conocido y se investiga hasta eliminarlo."], "sinonimos": [["solución temporal", "workaround", "solución alternativa"], ["causa raíz", "causa de fondo"], ["incidente", "incidencia"]], "feedback_generico": {"correcto": "¡Muy bien! Diferenciaste correctamente las prácticas de ITIL y entendiste por qué la repetición importa.", "medio": "Vas bien, pero te faltó explicar qué se hace con la repetición de los casos además de atender cada uno.", "malo": "Repasa en ITIL la diferencia entre incidente, problema, solicitud y cambio, y cuándo se usa cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Durante una semana llegan diez tickets de usuarios que pierden la conexión a la VPN cada tarde, y en todos los casos se soluciona reconectando. Según ITIL, ¿cómo corresponde gestionar esta situación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Como una solicitud de servicio, porque los usuarios piden reconectarse y eso es una tarea estándar del catálogo.', FALSE,
     'Una solicitud es un pedido de algo previsto, como un acceso o un equipo; aquí hay una falla del servicio.', 1),
    ('Como un único cambio de emergencia, reemplazando de inmediato el concentrador VPN sin analizar la causa.', FALSE,
     'Cambiar equipos sin diagnóstico puede no resolver nada y agrega riesgo; primero hay que investigar la causa raíz.', 2),
    ('Cerrando todos los tickets como duplicados, porque reconectar ya resuelve el problema de cada usuario.', FALSE,
     'Reconectar es solo una solución temporal; si no se investiga, las caídas seguirán ocurriendo.', 3),
    ('Cada caída como incidente para restaurar el servicio, y un registro de problema para buscar la causa raíz.', TRUE,
     'El incidente restablece el servicio lo antes posible y el problema investiga la causa común de los incidentes repetidos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Durante una semana llegan diez tickets de usuarios que pierden la conexión a la VPN cada tarde, y en todos los casos se soluciona reconectando. Según ITIL, ¿cómo corresponde gestionar esta situación?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El tiempo de respuesta se mide desde que llega el ticket hasta que alguien lo toma, y aquí fue de 20 minutos, dentro de los 30 comprometidos. El tiempo de resolución se mide desde la llegada hasta la solución y fue de 5 horas, por lo que se incumplió el plazo de 4 horas.',
    '{"metodo": "TECNICA", "criterios": ["Distingue tiempo de respuesta de tiempo de resolución", "Calcula 20 minutos de respuesta", "Calcula 5 horas de resolución", "Concluye que solo se incumplió la resolución"], "palabras_clave": ["SLA", "tiempo de respuesta", "tiempo de resolución", "incumplimiento"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Hay que revisar cada indicador por separado. La respuesta llegó a los 20 minutos, así que cumple; la resolución llegó a las 5 horas, así que se pasó por una hora del compromiso de 4 horas.", "El ticket se tomó a tiempo, pero se resolvió tarde. Con un plazo de resolución de 4 horas, el límite era las 14:00 y se resolvió a las 15:00, por lo que hay un incumplimiento del SLA de resolución."], "sinonimos": [["SLA", "acuerdo de nivel de servicio"], ["incumplir", "vencer", "romper"]], "feedback_generico": {"correcto": "¡Muy bien! Mediste cada indicador por separado y calculaste los tiempos sin confundirlos.", "medio": "Calculaste bien uno de los plazos, pero revisa con cuidado cómo se mide el otro indicador del SLA.", "malo": "Repasa qué mide el tiempo de respuesta y qué mide el tiempo de resolución en un SLA, y desde qué momento se cuenta cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'El SLA de tu mesa de ayuda fija para prioridad alta 30 minutos de respuesta y 4 horas de resolución, en horario continuo y sin pausas. Un ticket de prioridad alta llega a las 10:00, un técnico lo toma a las 10:20 y se resuelve a las 15:00. ¿Qué pasó con el SLA?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Se cumplieron ambos plazos, porque el técnico respondió antes de los 30 minutos comprometidos.', FALSE,
     'La respuesta sí cumplió, pero la resolución tomó 5 horas desde las 10:00, más que las 4 comprometidas.', 1),
    ('Se incumplió la respuesta, porque la resolución tardó más de 4 horas desde que se tomó el ticket.', FALSE,
     'Mezcla los dos indicadores: la respuesta (20 minutos) cumplió; lo que falló fue la resolución.', 2),
    ('Se cumplió la respuesta en 20 minutos, pero se incumplió la resolución, que tomó 5 horas.', TRUE,
     'La respuesta (10:20) está dentro de 30 minutos y la resolución (15:00) supera el plazo de 4 horas, que vencía a las 14:00.', 3),
    ('Se incumplieron ambos plazos, porque el ticket estuvo 20 minutos sin que nadie lo tomara.', FALSE,
     '20 minutos está dentro de los 30 comprometidos para la respuesta; solo se incumplió la resolución.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'El SLA de tu mesa de ayuda fija para prioridad alta 30 minutos de respuesta y 4 horas de resolución, en horario continuo y sin pausas. Un ticket de prioridad alta llega a las 10:00, un técnico lo toma a las 10:20 y se resuelve a las 15:00. ¿Qué pasó con el SLA?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Cuando N1 agota los procedimientos disponibles, debe escalar a N2 sin dejar vencer el SLA. El escalamiento tiene que ir documentado con los síntomas, los pasos ya realizados, capturas o mensajes de error y el impacto, para que el siguiente nivel no repita pruebas.',
    '{"metodo": "TECNICA", "criterios": ["Escala al siguiente nivel en lugar de cerrar o insistir", "Documenta síntomas y pasos ya realizados", "Adjunta evidencias como capturas o mensajes de error", "Considera el SLA y el impacto en el usuario"], "palabras_clave": ["escalar", "N2", "documentar", "evidencias", "SLA"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Lo correcto es derivar el caso a N2 dejando todo registrado en el ticket: qué falla, qué se probó, qué resultado dio y a cuántos usuarios afecta. Así el especialista parte con información completa.", "N1 ya cumplió su parte al aplicar la base de conocimiento, así que corresponde un escalamiento funcional. El valor está en entregar un ticket claro, con evidencia y contexto, y en avisar al usuario que su caso pasó a otro nivel."], "sinonimos": [["escalar", "derivar"], ["N1", "nivel 1", "primer nivel"], ["N2", "nivel 2", "segundo nivel"]], "feedback_generico": {"correcto": "¡Bien hecho! Sabes cuándo y cómo escalar un caso para que avance sin perder tiempo.", "medio": "Identificaste que hay que mover el caso, pero te faltó detallar qué información debe acompañarlo.", "malo": "Repasa el modelo de niveles N1, N2 y N3 y qué debe incluir un buen escalamiento de ticket."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un técnico N1 aplicó todos los pasos de la base de conocimiento para un error en una aplicación interna, pero el problema persiste y parece venir de la propia aplicación. ¿Qué debe hacer?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Cerrar el ticket indicando que no tiene solución en N1 y pedirle al usuario que lo reporte a otra área.', FALSE,
     'Cerrar sin resolver deja al usuario sin atención y rompe la trazabilidad; el ticket debe seguir vivo y escalarse.', 1),
    ('Escalarlo al siguiente nivel con el ticket documentado: síntomas, pasos ya aplicados, evidencias e impacto.', TRUE,
     'Un escalamiento bien documentado evita que N2 repita pruebas y acelera la solución.', 2),
    ('Seguir probando soluciones por su cuenta, sin límite de tiempo, hasta encontrar la causa aunque venza el SLA.', FALSE,
     'Insistir sin las herramientas o permisos necesarios retrasa la solución; escalar a tiempo es parte del rol de N1.', 3),
    ('Reinstalar el sistema operativo del usuario, porque es la forma más segura de descartar problemas locales.', FALSE,
     'Es una acción drástica y sin diagnóstico, que hace perder tiempo y probablemente no resuelve un error de la aplicación.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un técnico N1 aplicó todos los pasos de la base de conocimiento para un error en una aplicación interna, pero el problema persiste y parece venir de la propia aplicación. ¿Qué debe hacer?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Como afecta a varios usuarios, reviso la impresora y el servidor de impresión antes que los PC. 1) Miro el panel de la impresora en busca de falta de papel o tóner, atascos o mensajes de error. 2) Hago ping a la IP de la impresora para confirmar que está en la red y que es la misma configurada en el puerto. 3) En el servidor de impresión reviso la cola, elimino los trabajos atascados y reinicio el servicio Cola de impresión (Spooler). 4) Confirmo que el driver y el puerto de la impresora compartida sean los correctos. 5) Envío una página de prueba y pido a un usuario que imprima. 6) Documento la causa y, si la IP había cambiado, configuro una reserva DHCP para que no se repita.',
    '{"metodo": "TECNICA", "criterios": ["Acota el problema a la impresora o al servidor por afectar a varios usuarios", "Revisa el estado físico de la impresora", "Comprueba la conectividad y la IP de la impresora", "Limpia la cola y reinicia el servicio Cola de impresión", "Verifica con una prueba y documenta la causa"], "palabras_clave": ["cola de impresión", "Spooler", "ping", "servidor de impresión", "reserva DHCP", "driver"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Si el problema es de todo un piso, la falla suele estar en la impresora, la red o el servidor de impresión. Reviso errores físicos en el equipo, compruebo que responda a ping en su IP y luego limpio la cola y reinicio el Spooler del servidor. Termino con una impresión de prueba y documento lo que encontré.", "Descarto primero lo evidente en la impresora, como papel, tóner o atascos. Después verifico la conectividad, porque una IP cambiada deja los trabajos en cola, y reviso el servicio de cola de impresión del servidor, eliminando los trabajos trabados. Si fue la IP, la fijo con una reserva para que no vuelva a pasar."], "sinonimos": [["Cola de impresión", "Spooler", "Print Spooler"], ["driver", "controlador"], ["atasco", "papel atascado"]], "feedback_generico": {"correcto": "¡Muy bien! Tu diagnóstico considera el alcance del problema y revisa cada punto en un orden lógico.", "medio": "Vas bien, pero te faltó revisar alguna capa del problema, como la conectividad o el servidor de impresión.", "malo": "Repasa cómo funciona la impresión en red, desde el equipo del usuario hasta la impresora, para diagnosticar dónde se traban los trabajos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Varios usuarios de un piso reportan que la impresora de red no imprime: los trabajos quedan en cola y no salen. ¿Cómo lo diagnosticas y resuelves?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Investigo desde el usuario hacia el servicio, para saber si es algo local o general. 1) Pregunto a otros usuarios si les pasa lo mismo, para acotar el alcance. 2) Reviso Outlook en la web: si ahí tampoco llegan, descarto el cliente de escritorio. 3) Reviso la carpeta de correo no deseado, las reglas de la bandeja y los reenvíos configurados. 4) En el Centro de administración de Exchange uso el seguimiento de mensajes para ver si los correos llegaron, se rechazaron o quedaron en cuarentena. 5) Reviso el estado del servicio en el centro de administración de Microsoft 365. 6) Si afecta a todos, escalo a N2 para revisar el registro MX y los conectores, y documento el caso.',
    '{"metodo": "TECNICA", "criterios": ["Acota si afecta a un usuario o a varios", "Descarta el cliente probando Outlook en la web", "Revisa correo no deseado, reglas y reenvíos", "Usa el seguimiento de mensajes de Exchange", "Revisa el estado del servicio o escala si es general"], "palabras_clave": ["seguimiento de mensajes", "OWA", "reglas", "cuarentena", "estado del servicio", "registro MX"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero veo si es un caso aislado o general. Si es solo ese usuario, pruebo en Outlook en la web, reviso reglas, reenvíos y correo no deseado, y busco los mensajes con el seguimiento de mensajes de Exchange para saber si fueron entregados, filtrados o rechazados. Si son varios usuarios, reviso el estado del servicio de Microsoft 365 y escalo.", "Separo cliente, buzón y servicio. Outlook en la web me dice si el problema es la aplicación local, las reglas y la cuarentena me dicen si el correo se está desviando, y el message trace confirma qué pasó con cada mensaje. Si el problema es de todo el dominio, se revisa el MX y se escala."], "sinonimos": [["seguimiento de mensajes", "message trace"], ["Outlook en la web", "OWA"], ["correo no deseado", "spam"]], "feedback_generico": {"correcto": "¡Excelente! Tu investigación separa bien lo local de lo general y usa las herramientas de administración adecuadas.", "medio": "Buen enfoque, pero te faltó alguna verificación clave, como rastrear los mensajes o acotar el alcance.", "malo": "Repasa cómo se diagnostica un problema de correo en Microsoft 365: cliente, buzón, filtros y herramientas del centro de administración."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un usuario no recibe correos externos en Outlook desde hace dos horas, aunque sí le llegan los internos. La empresa usa Microsoft 365. ¿Cómo lo investigas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Busco un patrón que me diga si la causa es de software, de un driver o de hardware. 1) Anoto el código de detención que muestra la pantalla azul. 2) Reviso el Visor de eventos, en el registro Sistema, buscando errores críticos y eventos BugCheck cercanos a cada reinicio. 3) Pregunto por cambios recientes, como actualizaciones, drivers o hardware nuevo. 4) Si el sistema no es estable, inicio en modo seguro para probar sin drivers de terceros, y actualizo o revierto el driver sospechoso. 5) Descarto hardware con el Diagnóstico de memoria de Windows, chkdsk y la revisión de temperaturas. 6) Ejecuto sfc /scannow para reparar archivos del sistema y documento el resultado o escalo.',
    '{"metodo": "TECNICA", "criterios": ["Registra el código de detención de la pantalla azul", "Revisa el Visor de eventos para correlacionar los reinicios", "Considera cambios recientes de drivers o actualizaciones", "Usa el modo seguro para aislar drivers", "Descarta fallas de hardware como memoria o disco"], "palabras_clave": ["código de detención", "Visor de eventos", "modo seguro", "driver", "memoria", "sfc"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Empiezo por el código de error de la pantalla azul y por el Visor de eventos para ver qué ocurrió antes de cada reinicio. Luego reviso si hubo actualizaciones o drivers nuevos y pruebo en modo seguro. Si sigue fallando, hago pruebas de memoria y disco para descartar hardware.", "Una pantalla azul suele venir de un controlador defectuoso, de archivos del sistema dañados o de hardware. Por eso cruzo el código de detención con los eventos del sistema, revierto drivers recientes, reparo archivos con sfc y pruebo la RAM y el disco antes de escalar a N2."], "sinonimos": [["pantalla azul", "BSOD"], ["driver", "controlador"], ["código de detención", "stop code"]], "feedback_generico": {"correcto": "¡Muy bien! Tu diagnóstico es sistemático y usa las herramientas correctas para separar software de hardware.", "medio": "Vas bien, pero te faltó alguna herramienta o verificación para aislar si la causa es de software o de hardware.", "malo": "Repasa las herramientas de diagnóstico de Windows y cómo usarlas para encontrar la causa de reinicios con pantalla azul."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un equipo con Windows se reinicia varias veces al día mostrando una pantalla azul. ¿Cómo lo diagnosticas y qué herramientas usas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un cambio estándar es de bajo riesgo, se repite con frecuencia y sigue un procedimiento documentado, por lo que queda preautorizado. Un cambio normal debe evaluarse en riesgo e impacto y ser aprobado por la autoridad de cambios antes de ejecutarse, mientras que el de emergencia se usa para restaurar un servicio con urgencia.',
    '{"metodo": "TECNICA", "criterios": ["Define el cambio estándar como de bajo riesgo y repetitivo", "Indica que el cambio estándar está preautorizado", "Explica que el cambio normal requiere evaluación y aprobación", "Diferencia ambos del cambio de emergencia"], "palabras_clave": ["cambio estándar", "cambio normal", "preautorizado", "riesgo", "aprobación", "cambio de emergencia"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La clave es el riesgo y la autorización. Lo rutinario y probado, como instalar una aplicación aprobada del catálogo, se define como cambio estándar y no pide aprobación cada vez; lo que no es rutinario se trata como cambio normal y pasa por evaluación.", "Un cambio estándar tiene un procedimiento conocido y riesgo bajo, así que se aprueba una vez como modelo y luego se ejecuta directamente. Un cambio normal se registra, se evalúa su impacto y se autoriza caso a caso, por ejemplo en un CAB."], "sinonimos": [["CAB", "comité de cambios", "comité asesor de cambios"], ["preautorizado", "preaprobado"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes cómo la gestión de cambios equilibra agilidad y control según el riesgo.", "medio": "Tienes parte de la idea, pero te faltó precisar qué criterio separa los tipos de cambio y cómo se autorizan.", "malo": "Repasa los tipos de cambio de ITIL (estándar, normal y de emergencia) y cómo se autoriza cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Estás definiendo el proceso de gestión de cambios de la mesa de ayuda según ITIL. ¿Qué distingue a un cambio estándar de un cambio normal?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('El estándar es rutinario, de bajo riesgo y preautorizado; el normal debe evaluarse y aprobarse antes de aplicarse.', TRUE,
     'Así se agiliza lo rutinario y se controla lo que tiene más riesgo con una evaluación caso a caso.', 1),
    ('El estándar se aplica solo fuera del horario laboral y el normal puede aplicarse en cualquier momento del día.', FALSE,
     'La diferencia no está en el horario, sino en el riesgo y en si el cambio requiere una autorización individual.', 2),
    ('El estándar lo aprueba siempre el comité de cambios (CAB) y el normal lo puede aplicar cualquier técnico.', FALSE,
     'Es al revés: el estándar ya está preautorizado y el normal es el que pasa por evaluación y aprobación.', 3),
    ('El estándar se usa para restaurar con urgencia un servicio caído y el normal para mejoras planificadas.', FALSE,
     'Un cambio para resolver una caída urgente es un cambio de emergencia, no un cambio estándar.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Estás definiendo el proceso de gestión de cambios de la mesa de ayuda según ITIL. ¿Qué distingue a un cambio estándar de un cambio normal?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La forma sostenible de subir el FCR es entender qué se está escalando y por qué. Analizando los tickets escalados por categoría se detectan casos repetitivos que N1 podría resolver con artículos de la base de conocimiento, capacitación o permisos adecuados, y luego se mide el efecto.',
    '{"metodo": "TECNICA", "criterios": ["Analiza los datos de los tickets escalados", "Identifica casos repetitivos que N1 podría resolver", "Propone mejorar la base de conocimiento", "Propone capacitar a N1", "Rechaza maquillar las métricas"], "palabras_clave": ["FCR", "base de conocimiento", "capacitación", "análisis de tickets", "escalamientos"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Primero revisaría qué tipos de casos se escalan más y si N1 tiene la información para resolverlos. Con eso armaría o actualizaría artículos de la base de conocimiento y haría capacitaciones cortas, midiendo después si el FCR mejora.", "Bajar los escalamientos a la fuerza solo mueve el problema. Lo efectivo es encontrar el patrón en los datos, documentar soluciones para los casos frecuentes y darle a N1 las herramientas y permisos necesarios para cerrarlos en el primer contacto."], "sinonimos": [["FCR", "resolución en primer contacto"], ["base de conocimiento", "KB"], ["capacitar", "entrenar", "formar"]], "feedback_generico": {"correcto": "¡Excelente! Tu decisión ataca la causa del problema y no solo el indicador, como se espera de un líder.", "medio": "Vas por buen camino, pero te faltó apoyar la decisión en datos o en cómo se transfiere el conocimiento a N1.", "malo": "Repasa qué mide el FCR y cómo mejorarlo sin maquillar las métricas, usando la gestión del conocimiento."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Lideras una mesa de ayuda y notas que la resolución en primer contacto (FCR) bajó mucho, mientras subieron los escalamientos a N2. ¿Cuál es la acción más efectiva para revertirlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Prohibir los escalamientos a N2 hasta que el FCR vuelva a subir, obligando a N1 a resolver todo.', FALSE,
     'Sube el indicador de forma artificial, pero alarga los tiempos y empeora la atención de los casos complejos.', 1),
    ('Cerrar los tickets apenas se aplica una solución, aunque el usuario no haya confirmado que funciona.', FALSE,
     'Maquilla las métricas y genera reaperturas; el FCR debe medir problemas realmente resueltos.', 2),
    ('Aumentar los plazos del SLA de resolución, para que N1 tenga más tiempo antes de tener que escalar.', FALSE,
     'Relaja el compromiso con el usuario y no ataca la causa: N1 sigue sin saber resolver esos casos.', 3),
    ('Analizar los tickets escalados por categoría, crear artículos de base de conocimiento y capacitar a N1.', TRUE,
     'Ataca la causa: identifica los casos repetidos que N1 podría resolver y le entrega el conocimiento para hacerlo.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Lideras una mesa de ayuda y notas que la resolución en primer contacto (FCR) bajó mucho, mientras subieron los escalamientos a N2. ¿Cuál es la acción más efectiva para revertirlo?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El soporte remoto a escala necesita seguridad y trazabilidad. Lo adecuado es una herramienta corporativa con autenticación multifactor para los técnicos, consentimiento del usuario al iniciar la sesión, permisos por rol y registro o grabación de las sesiones para auditoría.',
    '{"metodo": "TECNICA", "criterios": ["Elige una herramienta corporativa centralizada", "Exige autenticación multifactor para los técnicos", "Incluye consentimiento del usuario y permisos por rol", "Considera la auditoría o el registro de sesiones", "Descarta exponer RDP o compartir credenciales"], "palabras_clave": ["soporte remoto", "MFA", "consentimiento", "permisos por rol", "registro de sesiones", "auditoría"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Elegiría una plataforma de acceso remoto corporativa donde cada técnico tenga su propia cuenta con MFA y permisos según su rol. El usuario debe aceptar la conexión y todas las sesiones deben quedar registradas para poder auditarlas.", "Lo importante es no abrir puertas innecesarias ni compartir credenciales. Una solución centralizada con autenticación fuerte, control de accesos por perfil, aprobación del usuario y bitácora de sesiones permite dar soporte a muchos equipos sin perder el control."], "sinonimos": [["MFA", "autenticación multifactor", "doble factor"], ["registro de sesiones", "bitácora", "log de sesiones"], ["teletrabajadores", "trabajadores remotos"]], "feedback_generico": {"correcto": "¡Muy bien! Tu elección equilibra la operación diaria con la seguridad y la trazabilidad.", "medio": "Elegiste un buen camino, pero revisa qué controles de seguridad y auditoría son indispensables a esta escala.", "malo": "Repasa las buenas prácticas de soporte remoto: autenticación, control de accesos y trazabilidad de las sesiones."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes definir cómo la mesa de ayuda dará soporte remoto a 300 teletrabajadores. ¿Qué enfoque es el más adecuado en seguridad y operación?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Habilitar RDP en cada equipo y publicar el puerto 3389 hacia Internet para conectarse sin intermediarios.', FALSE,
     'Exponer RDP a Internet es un vector clásico de ataques de fuerza bruta y de ransomware.', 1),
    ('Usar una herramienta gratuita con una sola cuenta compartida por todos los técnicos para simplificar el acceso.', FALSE,
     'Una cuenta compartida impide saber quién hizo qué y, si se filtra, compromete a todos los equipos.', 2),
    ('Usar una herramienta corporativa con MFA, consentimiento del usuario, permisos por rol y registro de sesiones.', TRUE,
     'Combina acceso seguro, control de quién puede conectarse y trazabilidad para auditoría.', 3),
    ('Pedir a cada usuario su contraseña de dominio para conectarse fuera de horario, cuando no esté en su equipo.', FALSE,
     'Nunca se pide la contraseña al usuario; rompe la trazabilidad y viola las políticas de seguridad.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Soporte técnico y Help Desk' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Debes definir cómo la mesa de ayuda dará soporte remoto a 300 teletrabajadores. ¿Qué enfoque es el más adecuado en seguridad y operación?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo gestiono como incidente mayor, coordinando el diagnóstico técnico y la comunicación en paralelo. 1) Declaro el incidente mayor, abro un ticket principal y asocio a él los reportes de los usuarios. 2) Reúno a los especialistas de infraestructura, redes y Microsoft 365 en un canal único y asigno roles. 3) Comunico a la empresa qué pasa y cuándo será la próxima actualización, por ejemplo cada 30 minutos. 4) Priorizo restaurar el servicio: reviso los controladores de dominio, el DNS, el estado de Microsoft 365 y los cambios recientes, y aplico una solución temporal si existe. 5) Llevo una línea de tiempo de acciones y decisiones. 6) Al cerrar, hago una revisión post incidente sin culpables y abro un problema para eliminar la causa raíz.',
    '{"metodo": "TECNICA", "criterios": ["Declara el incidente mayor y centraliza los reportes", "Coordina a los especialistas con roles claros", "Mantiene una comunicación periódica con la empresa", "Prioriza restaurar el servicio revisando identidad, DNS y cambios recientes", "Cierra con revisión post incidente y gestión de problemas"], "palabras_clave": ["incidente mayor", "comunicación", "controladores de dominio", "DNS", "post incidente", "causa raíz"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero declaro el incidente mayor y armo un equipo de respuesta con responsables claros, mientras otra persona se encarga de informar a los usuarios con actualizaciones periódicas. Técnicamente reviso lo común a ambos servicios, como la autenticación, los controladores de dominio, el DNS y los cambios del fin de semana. Una vez restaurado, hago un análisis post mortem y gestiono la causa raíz como problema.", "Separo dos frentes: restaurar y comunicar. El equipo técnico revisa identidad, DNS, sincronización con Microsoft 365 y cambios recientes, buscando una solución temporal rápida. Yo coordino, registro la línea de tiempo y envío avisos regulares, y al final documento lecciones aprendidas y abro un problema para que no se repita."], "sinonimos": [["post incidente", "post mortem", "lecciones aprendidas"], ["solución temporal", "workaround"], ["controladores de dominio", "Active Directory"]], "feedback_generico": {"correcto": "¡Excelente! Coordinaste el diagnóstico, la comunicación y el cierre como se espera de un líder ante un incidente mayor.", "medio": "Buen manejo técnico, pero te faltó algún aspecto de liderazgo, como la comunicación periódica o la revisión posterior.", "malo": "Repasa cómo se gestiona un incidente mayor en ITIL: roles, comunicación, restauración del servicio y cierre."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un lunes a las 9:00 nadie en la empresa puede iniciar sesión ni en sus equipos ni en el correo. Eres el líder de soporte. ¿Cómo gestionas este incidente mayor?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño una estrategia basada en la criticidad de los datos y en la regla 3-2-1. 1) Inventario qué datos y sistemas son críticos y acuerdo con la gerencia el RPO, cuánta información se puede perder, y el RTO, en cuánto tiempo hay que recuperarse. 2) Aplico la regla 3-2-1: tres copias, en dos medios distintos y una fuera de la oficina. 3) Agrego una copia inmutable o desconectada, para que el ransomware no pueda cifrarla. 4) Automatizo los respaldos, por ejemplo incrementales diarios y completos semanales, con cifrado y acceso restringido. 5) Pruebo restauraciones periódicamente. 6) Documento el procedimiento y monitoreo las alertas de respaldos fallidos.',
    '{"metodo": "TECNICA", "criterios": ["Define RPO y RTO según la criticidad de los datos", "Aplica la regla 3-2-1", "Incluye una copia inmutable o fuera de línea contra ransomware", "Automatiza y protege los respaldos con cifrado y acceso restringido", "Prueba restauraciones periódicamente"], "palabras_clave": ["3-2-1", "RPO", "RTO", "inmutable", "restauración", "fuera de línea"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Partiría definiendo cuántos datos puede perder la empresa y en cuánto tiempo debe volver a operar. Con eso aplicaría la regla 3-2-1, sumando una copia que el ransomware no pueda tocar, ya sea inmutable en la nube o desconectada. Los respaldos serían automáticos y cifrados, y haría pruebas de restauración cada cierto tiempo.", "Un respaldo que no se prueba no sirve. Por eso combino copias automáticas locales y externas, al menos una aislada o inmutable, con objetivos claros de recuperación acordados con la gerencia. Además restrinjo quién puede borrar respaldos, monitoreo los fallos y ensayo la restauración periódicamente."], "sinonimos": [["respaldo", "backup", "copia de seguridad"], ["fuera de línea", "offline", "desconectada"], ["inmutable", "WORM"]], "feedback_generico": {"correcto": "¡Muy bien! Tu estrategia es completa, considera el ransomware y no olvida comprobar que se puede restaurar.", "medio": "Buen diseño base, pero te faltó algún elemento clave, como la protección contra ransomware, los objetivos de recuperación o las pruebas.", "malo": "Repasa las buenas prácticas de respaldo, cómo se definen los objetivos de recuperación y cómo proteger las copias frente a ransomware."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Una oficina de 40 personas no tiene una política de respaldos y la gerencia teme un ataque de ransomware. ¿Qué estrategia de respaldo diseñas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño un despliegue automatizado y repetible, en lugar de preparar cada equipo a mano. 1) Defino perfiles de usuario y la configuración base de cada uno: aplicaciones, políticas y accesos. 2) Uso una herramienta de gestión de endpoints, como Microsoft Intune con Windows Autopilot, para aplicar configuración y aplicaciones automáticamente. 3) Incluyo seguridad desde el inicio: cifrado con BitLocker, antivirus, actualizaciones y usuarios sin privilegios de administrador. 4) Hago un piloto con pocos equipos y usuarios para corregir errores. 5) Registro cada equipo en el inventario con su número de serie y responsable. 6) Planifico la entrega por áreas, con migración de datos, una guía breve y soporte reforzado los primeros días.',
    '{"metodo": "TECNICA", "criterios": ["Define perfiles y una configuración base estándar", "Automatiza el despliegue con una herramienta de gestión", "Incluye seguridad como cifrado, antivirus y mínimo privilegio", "Valida con un piloto antes del despliegue masivo", "Planifica inventario, entrega y soporte posterior"], "palabras_clave": ["perfiles", "Intune", "Autopilot", "BitLocker", "piloto", "inventario"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Evitaría configurar equipo por equipo. Definiría una configuración estándar por perfil y la aplicaría automáticamente con una herramienta de gestión de dispositivos, incluyendo cifrado, antivirus y actualizaciones. Antes del despliegue total haría un piloto, y luego entregaría por áreas, dejando cada notebook en el inventario.", "La idea es que el usuario reciba un equipo listo y seguro con el menor trabajo manual posible. Para eso uso aprovisionamiento automático, por ejemplo con Autopilot e Intune, aplicaciones asignadas por grupo y políticas de seguridad. Valido con un grupo piloto, documento el procedimiento y planifico la entrega con apoyo en terreno."], "sinonimos": [["notebook", "portátil", "laptop"], ["inventario", "CMDB"], ["piloto", "prueba piloto"]], "feedback_generico": {"correcto": "¡Excelente! Tu diseño es escalable, seguro y considera la experiencia del usuario en la entrega.", "medio": "Buena base, pero te faltó algún componente, como la automatización, la seguridad o la validación previa.", "malo": "Repasa cómo se aprovisionan equipos a escala con herramientas de gestión de endpoints y buenas prácticas de seguridad."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La empresa comprará 150 notebooks nuevos y te piden estandarizar su preparación (sistema, aplicaciones y seguridad) para entregarlos rápido a los usuarios. ¿Cómo lo diseñas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Soporte técnico y Help Desk'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- ── ANÁLISIS DE DATOS / BI ──────────────────────────────────────────────────

-- Excel para análisis de datos

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Al copiar hacia abajo, la referencia relativa B2 se ajusta a B3, mientras que $F$1 es absoluta porque el signo $ fija su columna y su fila. Por eso en C3 queda =B3*$F$1, algo útil cuando todas las filas deben multiplicarse por un mismo valor, como una tasa o un tipo de cambio.',
    '{"metodo": "TECNICA", "criterios": ["Distingue referencia relativa de referencia absoluta", "Explica que el signo $ fija la columna y la fila", "Predice correctamente la fórmula copiada", "Da un uso práctico de la referencia absoluta"], "palabras_clave": ["referencia absoluta", "referencia relativa", "fijar", "copiar fórmula", "F4"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["La parte B2 cambia a B3 porque es relativa y se mueve con la fórmula. La parte $F$1 queda igual porque está anclada con signos peso, que se agregan rápido con la tecla F4.", "Excel ajusta las referencias sin $ según cuántas filas y columnas se desplazó la fórmula. Como se bajó una fila, B2 pasa a B3; la celda F1, al estar fijada como $F$1, sigue apuntando al mismo lugar."], "sinonimos": [["referencia absoluta", "referencia fija", "celda anclada"]], "feedback_generico": {"correcto": "¡Bien! Entiendes cómo se comportan las referencias al copiar fórmulas, algo básico y muy usado en análisis.", "medio": "Vas bien, pero revisa qué parte de la fórmula se ajusta al copiar y cuál queda fija gracias al signo $.", "malo": "Sigue practicando: repasa la diferencia entre referencias relativas y absolutas, y practica copiando fórmulas en distintas direcciones."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En la celda C2 tienes la fórmula =B2*$F$1 y la copias hacia abajo a la celda C3. ¿Qué fórmula queda en C3?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('=B3*$F$2', FALSE,
     'El signo $ fija tanto la columna como la fila de F1, por lo que esa referencia no cambia al copiar.', 1),
    ('=B2*$F$1', FALSE,
     'B2 es una referencia relativa y se ajusta al copiar una fila hacia abajo, así que no puede quedar igual.', 2),
    ('=B3*$F$1', TRUE,
     'Correcto: la referencia relativa B2 pasa a B3 y la referencia absoluta $F$1 se mantiene fija.', 3),
    ('=C3*$F$1', FALSE,
     'Al copiar hacia abajo solo cambia la fila de las referencias relativas, no la columna; B no pasa a C.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En la celda C2 tienes la fórmula =B2*$F$1 y la copias hacia abajo a la celda C3. ¿Qué fórmula queda en C3?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La sintaxis es SUMAR.SI.CONJUNTO(rango_suma; rango_criterios1; criterio1; rango_criterios2; criterio2), por lo que primero va C:C y luego cada rango con su criterio. Así, =SUMAR.SI.CONJUNTO(C:C;A:A;"Norte";B:B;"Hogar") suma solo las filas que cumplen ambas condiciones.',
    '{"metodo": "TECNICA", "criterios": ["Elige SUMAR.SI.CONJUNTO para varias condiciones", "Ubica el rango a sumar como primer argumento", "Escribe pares de rango y criterio", "Distingue sumar de contar con criterios"], "palabras_clave": ["SUMAR.SI.CONJUNTO", "rango suma", "criterios", "CONTAR.SI.CONJUNTO", "varias condiciones"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Uso SUMAR.SI.CONJUNTO poniendo primero la columna de montos y después la columna de región con Norte y la de categoría con Hogar. La función suma solo las filas donde se cumplen las dos condiciones a la vez.", "Para sumar con dos o más condiciones, la función indicada es SUMAR.SI.CONJUNTO, cuyo primer argumento es el rango que se suma. SUMAR.SI solo acepta un criterio y CONTAR.SI.CONJUNTO cuenta filas en vez de sumar montos."], "sinonimos": [["SUMAR.SI.CONJUNTO", "SUMIFS"], ["CONTAR.SI.CONJUNTO", "COUNTIFS"]], "feedback_generico": {"correcto": "¡Muy bien! Dominas la sintaxis de una de las funciones de resumen más usadas en análisis con Excel.", "medio": "Vas bien, pero revisa el orden de los argumentos y qué hace exactamente cada función de esta familia.", "malo": "No te desanimes: repasa las funciones que suman o cuentan con condiciones y practica el orden de sus argumentos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Tienes la región en la columna A, la categoría en la columna B y el monto en la columna C. ¿Qué fórmula suma los montos de la región "Norte" y la categoría "Hogar"?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('=SUMAR.SI.CONJUNTO(C:C;A:A;"Norte";B:B;"Hogar")', TRUE,
     'Correcto: SUMAR.SI.CONJUNTO recibe primero el rango a sumar y luego pares de rango de criterio y criterio.', 1),
    ('=SUMAR.SI.CONJUNTO(A:A;"Norte";B:B;"Hogar";C:C)', FALSE,
     'El orden está mal: en SUMAR.SI.CONJUNTO el rango que se suma va como primer argumento, no al final.', 2),
    ('=CONTAR.SI.CONJUNTO(A:A;"Norte";B:B;"Hogar")', FALSE,
     'Esta fórmula es válida, pero cuenta las filas que cumplen ambos criterios en lugar de sumar los montos.', 3),
    ('=SUMAR.SI(C:C;A:A;"Norte";B:B;"Hogar")', FALSE,
     'SUMAR.SI admite un solo criterio y su sintaxis es (rango; criterio; [rango_suma]), así que no sirve para dos condiciones.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Tienes la región en la columna A, la categoría en la columna B y el monto en la columna C. ¿Qué fórmula suma los montos de la región "Norte" y la categoría "Hogar"?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Una tabla con formato se amplía automáticamente al agregar filas o columnas, y las fórmulas con referencias estructuradas, como =SUMA(Ventas[Monto]), incluyen los datos nuevos. Además, al usarla como origen de una tabla dinámica o un gráfico, basta actualizar para que tomen los registros agregados.',
    '{"metodo": "TECNICA", "criterios": ["Explica que la tabla se expande automáticamente", "Menciona las referencias estructuradas", "Relaciona la tabla con tablas dinámicas y gráficos como origen", "Descarta funciones que la tabla no cumple, como quitar duplicados"], "palabras_clave": ["tabla con formato", "referencias estructuradas", "se expande", "origen de datos", "Ctrl+T"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Al convertir el rango en tabla, Excel lo trata como un objeto con nombre que crece cuando agrego registros. Las fórmulas, gráficos y tablas dinámicas que apuntan a la tabla no necesitan que cambie el rango a mano.", "La principal ventaja es que el rango deja de ser fijo: al pegar o escribir filas nuevas, la tabla las absorbe y las referencias estructuradas siguen funcionando. También facilita los filtros, el formato y tener nombres de columna claros."], "sinonimos": [["tabla con formato", "tabla de Excel", "dar formato como tabla"], ["referencias estructuradas", "structured references"], ["tabla dinámica", "pivot table"]], "feedback_generico": {"correcto": "¡Bien hecho! Reconoces por qué las tablas con formato hacen más robustos los análisis cuando los datos crecen.", "medio": "Vas bien, pero revisa qué pasa con el rango de la tabla y con las fórmulas cuando agregas nuevos registros.", "malo": "No te preocupes: repasa qué es una tabla con formato en Excel y en qué se diferencia de un rango normal de celdas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué ventaja tiene convertir un rango de datos en una tabla con formato (Ctrl+T) antes de usarlo en fórmulas o tablas dinámicas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Elimina automáticamente las filas duplicadas cada vez que se agregan datos nuevos.', FALSE,
     'Una tabla no quita duplicados por sí sola; para eso está Quitar duplicados en la pestaña Datos o Power Query.', 1),
    ('Crece sola al agregar filas y las fórmulas que la usan toman los datos nuevos.', TRUE,
     'Correcto: la tabla crece con los datos nuevos y las fórmulas, gráficos y tablas dinámicas que la usan como origen la toman completa; la tabla dinámica solo requiere actualizar.', 2),
    ('Bloquea las celdas para que nadie pueda modificar los datos ya ingresados.', FALSE,
     'Proteger celdas se hace con Proteger hoja en la pestaña Revisar; la tabla con formato no restringe la edición.', 3),
    ('Convierte los textos en números y corrige las fechas mal escritas.', FALSE,
     'La tabla no cambia los tipos de datos; esos problemas se corrigen con funciones, Texto en columnas o Power Query.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Qué ventaja tiene convertir un rango de datos en una tabla con formato (Ctrl+T) antes de usarlo en fórmulas o tablas dinámicas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Uso una tabla dinámica porque resume miles de filas sin escribir fórmulas. 1) Reviso que los datos tengan encabezados, no tengan filas vacías y que las fechas estén en formato de fecha. 2) Convierto el rango en tabla con Ctrl+T para que crezca con los datos nuevos. 3) Voy a Insertar, Tabla dinámica, y la ubico en una hoja nueva. 4) Arrastro Región al área de filas, Fecha al área de columnas y Monto al área de valores con la operación Suma. 5) Agrupo la fecha por meses y años, y doy formato de número al monto. 6) Cuando lleguen datos nuevos, uso Actualizar.',
    '{"metodo": "TECNICA", "criterios": ["Verifica la calidad y el formato de los datos de origen", "Inserta la tabla dinámica desde una tabla o rango", "Ubica región, fecha y monto en las áreas correctas", "Agrupa las fechas por mes", "Sabe que debe actualizar cuando cambian los datos"], "palabras_clave": ["tabla dinámica", "agrupar por meses", "campo de valores", "actualizar", "encabezados"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero me aseguro de que la base esté limpia y tenga encabezados. Inserto una tabla dinámica, pongo la región en filas, la fecha agrupada por mes en columnas y la suma del monto en valores. Si mañana agregan ventas, actualizo la tabla dinámica para que las incluya.", "Creo una tabla dinámica desde los datos, idealmente convertidos en tabla para que el origen crezca solo. Arrastro los campos para cruzar región contra mes, uso Suma de monto y aplico formato de miles. Así obtengo el resumen en segundos y puedo filtrar por producto si lo necesito."], "sinonimos": [["tabla dinámica", "pivot table", "tabla pivote"], ["agrupar", "agrupación"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste paso a paso cómo resumir los datos y cuidaste la calidad del origen.", "medio": "Vas bien, pero te faltó algún paso clave, como ubicar bien los campos, agrupar las fechas o mantener el resumen actualizado.", "malo": "No te desanimes: repasa cómo se crea una tabla dinámica y para qué sirve cada una de sus áreas de campos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Te entregan una hoja con 5.000 filas de ventas con fecha, región, producto y monto. ¿Cómo usas una tabla dinámica para saber cuánto vendió cada región por mes?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Limpio los datos en orden y sobre una copia, para no perder el original. 1) Guardo una copia del archivo antes de modificar nada. 2) Uso ESPACIOS en una columna auxiliar para quitar los espacios al inicio, al final y los dobles entre palabras. 3) Separo el nombre completo con Texto en columnas usando el espacio como separador, o con Relleno rápido. 4) Uso Quitar duplicados de la pestaña Datos, eligiendo las columnas que identifican al cliente. 5) Copio las columnas limpias y las pego como valores. 6) Reviso una muestra para confirmar que no se perdieron datos.',
    '{"metodo": "TECNICA", "criterios": ["Trabaja sobre una copia del archivo original", "Usa ESPACIOS para eliminar espacios sobrantes", "Separa el texto con Texto en columnas o Relleno rápido", "Quita duplicados después de normalizar los datos", "Verifica el resultado final"], "palabras_clave": ["ESPACIOS", "Texto en columnas", "Quitar duplicados", "Relleno rápido", "pegar como valores"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Primero respaldo el archivo. Luego normalizo el texto con la función ESPACIOS, separo los nombres con Texto en columnas o con Ctrl+E y, cuando todo está uniforme, aplico Quitar duplicados. Al final convierto las fórmulas en valores y reviso algunos casos.", "También se puede hacer en Power Query: cargo la lista, aplico Recortar, divido la columna por el delimitador espacio y uso Quitar duplicados. La ventaja es que los pasos quedan guardados y se repiten la próxima vez que llegue el archivo."], "sinonimos": [["ESPACIOS", "TRIM", "recortar"], ["Relleno rápido", "Flash Fill", "Ctrl+E"], ["duplicados", "registros repetidos"]], "feedback_generico": {"correcto": "¡Excelente! Resolviste cada problema con la herramienta adecuada y en un orden que evita errores.", "medio": "Vas bien, pero revisa si atacaste todos los problemas de la lista y si el orden de los pasos evita que se escapen duplicados.", "malo": "Sigue practicando: repasa las herramientas de limpieza de Excel para texto, separación de columnas y registros repetidos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Recibes una lista de clientes exportada de un sistema, con registros duplicados, espacios de más y el nombre completo en una sola celda. ¿Cómo la limpias en Excel?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Uso la validación de datos para que Excel solo acepte valores correctos desde el momento en que se escriben. 1) Selecciono la columna de región y en Datos, Validación de datos, elijo Lista con un rango de regiones permitidas. 2) En la cantidad permito solo números enteros mayores que cero. 3) En la fecha permito solo fechas dentro de un rango razonable. 4) Configuro un mensaje de entrada que explique qué ingresar y un mensaje de error de estilo Grave, que impide ingresar valores no válidos. 5) Uso Rodear con un círculo datos no válidos para revisar lo que ya estaba escrito. 6) Aviso que pegar datos puede saltarse la validación y protejo la hoja para que no se cambien las reglas.',
    '{"metodo": "TECNICA", "criterios": ["Usa listas desplegables para los valores permitidos", "Restringe números y fechas a rangos válidos", "Configura mensajes de entrada y de error", "Revisa los datos existentes con Rodear con un círculo datos no válidos", "Considera limitaciones, como pegar datos"], "palabras_clave": ["validación de datos", "lista desplegable", "mensaje de error", "número entero", "datos no válidos"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Creo reglas de validación por columna: una lista desplegable para la región, números enteros positivos para la cantidad y fechas entre ciertos límites para la fecha. Agrego mensajes que expliquen el formato esperado y un aviso de error que bloquee los valores incorrectos.", "La idea es evitar el error en lugar de corregirlo después. Con Validación de datos limito qué se puede escribir en cada columna, tomo la lista de regiones desde una tabla para mantenerla fácilmente y marco con círculos los registros que ya no cumplen las reglas."], "sinonimos": [["validación de datos", "data validation"], ["lista desplegable", "menú desplegable"]], "feedback_generico": {"correcto": "¡Muy bien! Propusiste reglas concretas para cada tipo de dato y pensaste en cómo guiar a quien llena la planilla.", "medio": "Vas bien, pero te faltó cubrir algún tipo de dato o explicar cómo se informa al usuario cuando se equivoca.", "malo": "No te desanimes: repasa las opciones de Validación de datos en Excel, como listas, rangos numéricos y mensajes."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Varias personas llenan la misma planilla de pedidos y cometen errores de digitación en la región, la cantidad y la fecha. ¿Cómo usas la validación de datos para evitarlo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'BUSCARX recibe por separado la matriz donde busca y la matriz que devuelve, así que puede buscar en D y traer el dato de B, que está a la izquierda. BUSCARV no puede hacerlo porque siempre busca en la primera columna del rango; la alternativa clásica es =INDICE(B:B;COINCIDIR(H2;D:D;0)).',
    '{"metodo": "TECNICA", "criterios": ["Elige BUSCARX para buscar hacia la izquierda", "Explica la limitación de BUSCARV con la primera columna", "Conoce INDICE con COINCIDIR como alternativa", "Sabe que BUSCARX usa coincidencia exacta por defecto"], "palabras_clave": ["BUSCARX", "BUSCARV", "INDICE", "COINCIDIR", "coincidencia exacta"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Con BUSCARX indico que busque H2 en la columna D y que devuelva el valor de la columna B. BUSCARV no sirve aquí porque solo devuelve columnas ubicadas a la derecha de la primera columna del rango.", "La clave es que el resultado está a la izquierda de la columna de búsqueda. BUSCARX resuelve eso directamente y, en versiones antiguas sin BUSCARX, se usa INDICE sobre la columna B combinado con COINCIDIR del código en la columna D."], "sinonimos": [["BUSCARX", "XLOOKUP"], ["BUSCARV", "VLOOKUP"], ["INDICE", "INDEX"], ["COINCIDIR", "MATCH"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes cómo funciona cada función de búsqueda y cuál resuelve un caso en que el dato está a la izquierda.", "medio": "Vas bien, pero revisa en qué columna busca cada función y desde qué columna puede devolver el resultado.", "malo": "No te desanimes: repasa BUSCARV, BUSCARX e INDICE con COINCIDIR, y practica casos donde el resultado está a la izquierda."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En tu hoja, el nombre del producto está en la columna B y el código en la columna D. ¿Qué fórmula devuelve el nombre del producto cuyo código está en H2?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('=BUSCARV(H2;B:D;1;FALSO)', FALSE,
     'BUSCARV busca siempre en la primera columna del rango, que aquí es B, la de nombres; buscaría el código entre los nombres y daría #N/A.', 1),
    ('=INDICE(D:D;COINCIDIR(H2;B:B;0))', FALSE,
     'Los rangos están invertidos: busca el código en la columna de nombres y, aunque lo encontrara, devolvería un código y no un nombre.', 2),
    ('=BUSCARV(H2;D:D;-2;FALSO)', FALSE,
     'BUSCARV no acepta índices de columna negativos y devuelve #¡VALOR!; no puede traer datos de columnas a la izquierda.', 3),
    ('=BUSCARX(H2;D:D;B:B)', TRUE,
     'Correcto: BUSCARX separa la columna de búsqueda (D) de la de resultado (B), por eso puede devolver datos a la izquierda, y por defecto usa coincidencia exacta.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En tu hoja, el nombre del producto está en la columna B y el código en la columna D. ¿Qué fórmula devuelve el nombre del producto cuyo código está en H2?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'SI.CONJUNTO devuelve el resultado de la primera condición verdadera, por eso hay que evaluar de mayor a menor: primero B2>=500000 y luego B2>=100000. El par final VERDADERO;"Bajo" funciona como valor por defecto y evita el error #N/A cuando no se cumple ninguna de las condiciones anteriores.',
    '{"metodo": "TECNICA", "criterios": ["Sabe que SI.CONJUNTO devuelve la primera condición verdadera", "Ordena las condiciones de mayor a menor", "Incluye un valor por defecto con VERDADERO", "Detecta el error #N/A cuando falta el caso por defecto"], "palabras_clave": ["SI.CONJUNTO", "orden de condiciones", "VERDADERO", "valor por defecto", "SI anidado"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La fórmula correcta revisa primero si el monto llega a 500.000 y después si llega a 100.000, porque SI.CONJUNTO se detiene en la primera condición que se cumple. Para todo lo demás se agrega VERDADERO con el texto Bajo.", "También funcionaría un SI anidado bien ordenado, como =SI(B2>=500000;\"Alto\";SI(B2>=100000;\"Medio\";\"Bajo\")). Lo importante es el orden de los umbrales y tener un resultado para cuando ninguna condición se cumple."], "sinonimos": [["SI.CONJUNTO", "IFS"], ["SI anidado", "SI anidados", "nested IF"]], "feedback_generico": {"correcto": "¡Muy bien! Entiendes cómo SI.CONJUNTO evalúa las condiciones y cómo cubrir todos los casos sin errores.", "medio": "Vas bien, pero revisa en qué orden se evalúan las condiciones y qué pasa cuando ninguna se cumple.", "malo": "No te desanimes: repasa cómo funcionan SI y SI.CONJUNTO, y prueba tus fórmulas con un valor de cada tramo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Debes clasificar el monto de B2 como "Bajo" si es menor a 100.000, "Medio" si va de 100.000 a 499.999 y "Alto" si es 500.000 o más. ¿Qué fórmula lo hace correctamente?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('=SI.CONJUNTO(B2>=100000;"Medio";B2>=500000;"Alto";VERDADERO;"Bajo")', FALSE,
     'El orden falla: un monto de 600.000 cumple primero B2>=100000 y queda como Medio, porque SI.CONJUNTO devuelve la primera condición verdadera.', 1),
    ('=SI.CONJUNTO(B2>=500000;"Alto";B2>=100000;"Medio";VERDADERO;"Bajo")', TRUE,
     'Correcto: evalúa de mayor a menor, y el último par con VERDADERO actúa como valor por defecto para los montos bajos.', 2),
    ('=SI(B2<100000;"Bajo";SI(B2<500000;"Alto";"Medio"))', FALSE,
     'Las etiquetas están invertidas: un monto de 200.000 quedaría como Alto y uno de 600.000 como Medio.', 3),
    ('=SI.CONJUNTO(B2>=500000;"Alto";B2>=100000;"Medio")', FALSE,
     'Falta el caso por defecto: si ninguna condición se cumple, SI.CONJUNTO devuelve #N/A para los montos menores a 100.000.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Debes clasificar el monto de B2 como "Bajo" si es menor a 100.000, "Medio" si va de 100.000 a 499.999 y "Alto" si es 500.000 o más. ¿Qué fórmula lo hace correctamente?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La regla se escribe pensando en la primera celda del rango, A2, y se usa la referencia mixta $E2. El $ fija la columna E para todas las celdas de la fila, mientras que la fila queda relativa y se ajusta, así cada fila se evalúa según su propio estado.',
    '{"metodo": "TECNICA", "criterios": ["Usa una regla de formato condicional basada en fórmula", "Fija la columna con una referencia mixta", "Deja la fila relativa para que se ajuste", "Escribe la fórmula respecto de la primera celda del rango"], "palabras_clave": ["formato condicional", "referencia mixta", "fijar columna", "fila completa", "regla con fórmula"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Uso Formato condicional, Nueva regla, con la opción de usar una fórmula, y escribo =$E2=\"Atrasado\". El signo peso antes de la E obliga a todas las columnas de la fila a revisar la columna E, y el 2 sin fijar permite que cada fila revise su propio valor.", "La clave es una referencia mixta: columna absoluta y fila relativa. Así Excel evalúa la condición de la columna E en cada fila y aplica el formato a todas las celdas de esa fila, de A a H."], "sinonimos": [["formato condicional", "conditional formatting"], ["referencia mixta", "referencia semiabsoluta"]], "feedback_generico": {"correcto": "¡Excelente! Manejas bien las referencias mixtas dentro del formato condicional, un detalle que confunde a muchos.", "medio": "Vas bien, pero revisa qué parte de la referencia debe quedar fija para que toda la fila mire la misma columna.", "malo": "No te desanimes: repasa las referencias relativas, absolutas y mixtas, y cómo se aplican en una regla de formato condicional."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Quieres resaltar la fila completa de cada pedido cuya columna E dice "Atrasado", aplicando formato condicional con fórmula al rango A2:H100. ¿Qué fórmula usas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('=E2="Atrasado", sin fijar columna ni fila', FALSE,
     'Sin $ la columna se desplaza: en la celda B2 la regla revisaría F2, en C2 revisaría G2, y la fila no se resalta completa.', 1),
    ('=$E$2="Atrasado", con columna y fila fijas', FALSE,
     'Al fijar también la fila, todas las filas dependen de E2; se resaltaría todo el rango o nada según ese único pedido.', 2),
    ('=$E2="Atrasado", con solo la columna fija', TRUE,
     'Correcto: $E fija la columna para que todas las celdas de la fila miren la columna E, y la fila relativa se ajusta en cada fila.', 3),
    ('=E$2="Atrasado", con solo la fila fija', FALSE,
     'Fijar la fila y dejar libre la columna hace lo contrario de lo necesario: cada columna miraría su propia celda de la fila 2.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Quieres resaltar la fila completa de cada pedido cuya columna E dice "Atrasado", aplicando formato condicional con fórmula al rango A2:H100. ¿Qué fórmula usas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo automatizo con una consulta que combine todos los archivos de una carpeta, para que el trabajo mensual sea solo actualizar. 1) Guardo los CSV en una carpeta fija. 2) En Datos, Obtener datos, elijo obtenerlos desde una carpeta y luego Combinar y transformar datos. 3) En el Editor de Power Query reviso los tipos de datos, quito filas sobrantes y conservo el nombre del archivo como columna de sucursal. 4) Cargo el resultado en una tabla o en el modelo de datos. 5) Construyo la tabla dinámica o el informe sobre esa consulta. 6) Cada mes reemplazo o agrego los archivos en la carpeta y uso Actualizar todo.',
    '{"metodo": "TECNICA", "criterios": ["Usa el conector de carpeta de Power Query", "Combina los archivos en una sola consulta", "Aplica transformaciones y tipos de datos en Power Query", "Conserva el origen de cada archivo como columna", "Automatiza el proceso mensual con Actualizar todo"], "palabras_clave": ["Power Query", "carpeta", "combinar archivos", "actualizar todo", "tipos de datos"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Uso Power Query para leer la carpeta completa en vez de abrir archivo por archivo. La consulta combina todos los CSV, aplica la misma limpieza a cada uno y agrega una columna con el nombre de la sucursal tomado del archivo. El mes siguiente solo dejo los nuevos archivos en la carpeta y actualizo.", "Creo una consulta que lee la carpeta y une los doce archivos con la misma estructura. Defino los tipos, quito encabezados repetidos y cargo la tabla final. Así el proceso queda documentado en los pasos aplicados y se repite con un clic, sin copiar y pegar."], "sinonimos": [["Power Query", "Obtener y transformar datos"], ["combinar archivos", "combinar y transformar", "anexar"]], "feedback_generico": {"correcto": "¡Muy bien! Automatizaste el proceso completo y dejaste el trabajo mensual reducido a una actualización.", "medio": "Vas bien, pero te faltó explicar cómo se combinan los archivos o cómo se repite el proceso cada mes.", "malo": "No te desanimes: repasa los conectores de Power Query para carpetas y cómo combinar varios archivos con la misma estructura."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Cada mes recibes doce archivos CSV de sucursales con la misma estructura y pasas horas copiándolos y pegándolos en una sola hoja. ¿Cómo lo automatizas con Power Query en Excel?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diagnostico cada error por separado antes de ocultarlo, porque tienen causas distintas. 1) El #N/A indica que la búsqueda no encontró el valor, así que reviso espacios sobrantes con ESPACIOS, números guardados como texto y el tipo de coincidencia. 2) Si el valor realmente puede no existir, uso SI.ND o el cuarto argumento de BUSCARX, que define qué mostrar si no encuentra el valor. 3) El #¡REF! indica una referencia a celdas eliminadas, así que corrijo la fórmula o restauro la columna. 4) Para evitarlo, uso tablas con referencias estructuradas o BUSCARX en vez de índices de columna fijos. 5) Uso SI.ERROR con cuidado, porque oculta cualquier error, incluso los que revelan un problema real.',
    '{"metodo": "TECNICA", "criterios": ["Explica la causa del error #N/A", "Explica la causa del error #¡REF!", "Propone revisar espacios y tipos de datos en las búsquedas", "Distingue SI.ND de SI.ERROR", "Advierte que SI.ERROR puede ocultar errores reales"], "palabras_clave": ["SI.ERROR", "SI.ND", "ESPACIOS", "referencia eliminada", "valor no encontrado", "referencias estructuradas"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["El #N/A sale cuando la búsqueda no encuentra coincidencia, muchas veces por espacios o números guardados como texto, así que primero limpio los datos. El #¡REF! aparece porque la fórmula apuntaba a una columna que ya no existe y hay que reconstruirla. Solo después uso SI.ND o SI.ERROR para mostrar un mensaje, sabiendo que SI.ERROR esconde todo tipo de errores.", "No tapo los errores de inmediato. Investigo si el #N/A es un dato faltante legítimo o un problema de formato, y para el #¡REF! reviso qué referencia se perdió. Prefiero SI.ND en las búsquedas porque solo captura el #N/A, y diseño las fórmulas con tablas y BUSCARX para que eliminar columnas no las rompa."], "sinonimos": [["SI.ERROR", "IFERROR"], ["SI.ND", "IFNA"], ["ESPACIOS", "TRIM"], ["BUSCARX", "XLOOKUP"]], "feedback_generico": {"correcto": "¡Excelente! Diagnosticaste cada error por su causa y usas el manejo de errores de forma responsable.", "medio": "Vas bien, pero te faltó explicar la causa de alguno de los errores o cuándo conviene ocultarlos y cuándo no.", "malo": "No te desanimes: repasa qué significa cada código de error en Excel y qué diferencia hay entre corregir un error y ocultarlo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En tu informe aparecen celdas con #N/A en una búsqueda y otras con #¡REF! después de que alguien eliminó una columna. ¿Cómo diagnosticas cada error y cuándo usarías SI.ERROR?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Uso funciones de fecha, recordando que Excel guarda cada fecha como un número de serie. 1) Verifico que ambas columnas sean fechas reales y no texto, y si vienen como texto las convierto con FECHANUMERO o con Texto en columnas. 2) Creo un rango con nombre, Feriados, con los feriados del año. 3) Calculo los días hábiles con =DIAS.LAB(B2;C2;Feriados), que cuenta ambos extremos y excluye sábados, domingos y los feriados indicados. 4) Si el pago es a 30 días corridos uso =B2+30, y si es a 30 días hábiles uso =DIA.LAB(B2;30;Feriados). 5) El último día del mes lo obtengo con =FIN.MES(B2;0). 6) Doy formato de fecha o de número según corresponda.',
    '{"metodo": "TECNICA", "criterios": ["Verifica que las fechas sean valores de fecha y no texto", "Usa una tabla de feriados", "Calcula días hábiles con DIAS.LAB", "Distingue días corridos de días hábiles con DIA.LAB", "Obtiene el fin de mes con FIN.MES"], "palabras_clave": ["DIAS.LAB", "DIA.LAB", "FIN.MES", "feriados", "número de serie", "días hábiles"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero confirmo que las fechas no estén como texto. Para los días hábiles uso DIAS.LAB con un rango de feriados; para un plazo en días hábiles uso DIA.LAB y para días corridos sumo directamente a la fecha. El último día del mes lo calculo con FIN.MES y cero meses de desplazamiento.", "Como Excel trata las fechas como números, puedo sumar y restar días directamente, pero para excluir fines de semana y feriados necesito DIAS.LAB, o DIAS.LAB.INTL si el fin de semana es distinto. Mantengo los feriados en una tabla aparte y uso FIN.MES para agrupar por cierre mensual."], "sinonimos": [["DIAS.LAB", "NETWORKDAYS"], ["DIA.LAB", "WORKDAY"], ["FIN.MES", "EOMONTH"], ["feriados", "festivos", "días festivos"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste las funciones de fecha adecuadas y cuidaste que los datos de origen fueran fechas reales.", "medio": "Vas bien, pero te faltó resolver alguno de los tres cálculos o distinguir entre días corridos y días hábiles.", "malo": "No te desanimes: repasa cómo guarda Excel las fechas y las funciones para días hábiles y fin de mes."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tienes una tabla de pedidos con fecha de emisión y fecha de entrega. ¿Cómo calculas en Excel los días hábiles que tardó cada pedido descontando feriados, su fecha de pago a 30 días y el último día del mes en que se emitió?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Cuando los datos son grandes, los editan varias personas y se necesita integridad, conviene migrarlos a una base de datos con un proceso de carga controlado. Excel o Power BI se conectan a ella para analizar, y así se ganan concurrencia, respaldos, validaciones y una sola fuente de verdad.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce las limitaciones de Excel como base de datos", "Propone migrar a una base de datos", "Menciona concurrencia, integridad o respaldos", "Mantiene Excel o Power BI como capa de análisis", "Descarta parches que no resuelven la causa"], "palabras_clave": ["base de datos", "concurrencia", "integridad", "fuente única", "Power BI"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Excel no está pensado para que varias personas editen casi un millón de filas compartidas por correo. Lo correcto es mover esos datos a una base de datos con permisos y validaciones, y conectar Excel o Power BI para los reportes.", "Los archivos corruptos y las versiones distintas indican que el volumen y el uso superaron a Excel. La decisión es centralizar en una base de datos y dejar las planillas o dashboards solo como herramientas de consulta y análisis."], "sinonimos": [["base de datos", "BD", "motor de base de datos"], ["fuente única", "fuente de verdad", "única versión de la verdad"]], "feedback_generico": {"correcto": "¡Excelente! Reconoces cuándo Excel dejó de ser la herramienta adecuada y propones una arquitectura sostenible.", "medio": "Vas bien, pero revisa si tu opción resuelve la causa del problema o solo alivia el síntoma del archivo pesado.", "malo": "No te desanimes: repasa los límites de Excel para datos grandes y compartidos, y qué aporta una base de datos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un área mantiene un Excel de 900.000 filas que cinco personas actualizan a mano cada semana, se comparte por correo y se corrompe seguido. ¿Cuál es la mejor decisión técnica?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Dividir el archivo en libros mensuales vinculados entre sí con fórmulas externas.', FALSE,
     'Los vínculos entre libros son frágiles, se rompen al mover archivos y no resuelven la edición concurrente ni el control de calidad.', 1),
    ('Guardarlo como .xlsb y agregar macros que validen los datos al guardar.', FALSE,
     'El formato binario y las macros pueden aliviar el tamaño, pero no resuelven la concurrencia, el historial ni el gobierno de los datos.', 2),
    ('Aumentar el límite de filas de Excel desde las opciones avanzadas del programa.', FALSE,
     'El límite de 1.048.576 filas por hoja es fijo y no se puede cambiar; además, el problema principal no es solo el tamaño.', 3),
    ('Migrar los datos a una base de datos y usar Excel o Power BI solo para consultar.', TRUE,
     'Correcto: una base de datos ofrece concurrencia, integridad y respaldos, y Excel o Power BI quedan como capa de análisis conectada.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un área mantiene un Excel de 900.000 filas que cinco personas actualizan a mano cada semana, se comparte por correo y se corrompe seguido. ¿Cuál es la mejor decisión técnica?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo adecuado es cargar las tablas con Power Query al modelo de datos de Excel (Power Pivot), crear las relaciones entre ventas, productos y sucursales y definir medidas DAX. El modelo de datos no depende del límite de 1.048.576 filas por hoja, y la tabla dinámica se construye directamente sobre él.',
    '{"metodo": "TECNICA", "criterios": ["Propone usar el modelo de datos o Power Pivot", "Conoce el límite de filas de una hoja", "Crea relaciones entre tablas en lugar de usar BUSCARV", "Menciona medidas DAX o Power Query", "Descarta soluciones que fragmentan los datos"], "palabras_clave": ["modelo de datos", "Power Pivot", "relaciones", "medidas DAX", "límite de filas"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Uso Power Query para cargar las tres tablas solo como conexión y agregarlas al modelo de datos. Ahí las relaciono por código de producto y de sucursal, creo medidas y armo la tabla dinámica desde el modelo, sin pasar los 3 millones de filas por una hoja.", "Como una hoja admite poco más de un millón de filas, la solución es Power Pivot: un modelo en memoria, comprimido y con relaciones entre tablas. Así la tabla dinámica combina ventas con productos y sucursales sin usar BUSCARV."], "sinonimos": [["modelo de datos", "Power Pivot", "Data Model"], ["tabla dinámica", "pivot table"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste la herramienta de Excel pensada para volúmenes grandes y varias tablas relacionadas.", "medio": "Vas bien, pero revisa si tu opción respeta los límites de una hoja y si permite relacionar las tablas sin fórmulas fila a fila.", "malo": "No te desanimes: repasa qué es el modelo de datos de Excel y cómo se combina con Power Query y las tablas dinámicas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes analizar en una tabla dinámica una tabla de ventas de 3 millones de filas junto con las tablas de productos y sucursales. ¿Qué enfoque es el más adecuado en Excel?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Cargar las tablas al modelo de datos con Power Query, relacionarlas y usar medidas DAX.', TRUE,
     'Correcto: el modelo de datos (Power Pivot) no está limitado por las filas de una hoja, comprime los datos y permite relacionar tablas y crear medidas.', 1),
    ('Pegar todo en una sola hoja y traer producto y sucursal con BUSCARV en cada fila.', FALSE,
     'Una hoja admite como máximo 1.048.576 filas, así que 3 millones no caben; además, millones de BUSCARV serían muy lentos.', 2),
    ('Dividir las ventas en tres hojas de un millón de filas y hacer una tabla dinámica en cada una.', FALSE,
     'Fragmentar los datos obliga a sumar resultados a mano y multiplica los errores; no permite un análisis único.', 3),
    ('Usar la herramienta Consolidar para unir las tres tablas antes de crear la tabla dinámica.', FALSE,
     'Consolidar resume rangos con la misma estructura en una hoja; no crea relaciones entre tablas ni maneja millones de filas.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Debes analizar en una tabla dinámica una tabla de ventas de 3 millones de filas junto con las tablas de productos y sucursales. ¿Qué enfoque es el más adecuado en Excel?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'DESREF e INDIRECTO son funciones volátiles: Excel las recalcula ante cualquier cambio en el libro, junto con todas las fórmulas que dependen de ellas. Reemplazarlas por INDICE, referencias directas o tablas con referencias estructuradas reduce mucho el recálculo.',
    '{"metodo": "TECNICA", "criterios": ["Identifica DESREF e INDIRECTO como funciones volátiles", "Explica que las funciones volátiles se recalculan ante cualquier cambio", "Propone INDICE o referencias directas como reemplazo", "Descarta cambios que no atacan la causa"], "palabras_clave": ["funciones volátiles", "DESREF", "INDIRECTO", "INDICE", "recálculo"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El problema es la volatilidad: cada vez que se edita una celda, Excel vuelve a calcular todas las fórmulas con DESREF o INDIRECTO y sus dependientes. Cambiarlas por INDICE o por rangos directos hace que solo se recalcule lo que realmente cambió.", "Funciones como DESREF, INDIRECTO, HOY o ALEATORIO no siguen la cadena normal de dependencias y se recalculan siempre. Sustituirlas por alternativas no volátiles, como INDICE o referencias estructuradas, es lo que más acelera un libro grande."], "sinonimos": [["DESREF", "OFFSET"], ["INDIRECTO", "INDIRECT"], ["INDICE", "INDEX"], ["funciones volátiles", "volatilidad"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste la causa típica de un libro lento y la forma correcta de corregirla.", "medio": "Vas bien, pero revisa qué tienen de especial ciertas funciones que hacen que Excel las recalcule siempre.", "malo": "No te desanimes: repasa cómo recalcula Excel y qué funciones se consideran volátiles."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un libro con miles de fórmulas que usan DESREF e INDIRECTO tarda varios segundos en recalcular cada vez que alguien escribe un dato. ¿Qué cambio mejora más el rendimiento?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Envolver cada fórmula en SI.ERROR para que Excel no procese los errores.', FALSE,
     'SI.ERROR no evita el recálculo y agrega una función más por celda; no ataca la causa de la lentitud.', 1),
    ('Reemplazar DESREF e INDIRECTO por INDICE o referencias directas, porque son volátiles.', TRUE,
     'Correcto: las funciones volátiles se recalculan con cualquier cambio en el libro, junto con todo lo que depende de ellas; INDICE no es volátil.', 2),
    ('Aplicar formato condicional a todo el libro para detectar cuáles son las celdas más lentas.', FALSE,
     'El formato condicional no mide rendimiento y, aplicado a rangos grandes, también agrega carga de cálculo.', 3),
    ('Guardar el libro en formato .csv para que las fórmulas se calculen más rápido.', FALSE,
     'El formato CSV no guarda fórmulas ni varias hojas; se perderían los cálculos en lugar de acelerarlos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Excel para análisis de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un libro con miles de fórmulas que usan DESREF e INDIRECTO tarda varios segundos en recalcular cada vez que alguien escribe un dato. ¿Qué cambio mejora más el rendimiento?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo rediseño separando datos, cálculos y presentación, y documentando cada parte para que otro analista pueda mantenerlo. 1) Primero entiendo qué reportes entrega y a quién, y guardo sus cifras actuales como línea base. 2) Reemplazo los datos pegados a mano por consultas de Power Query conectadas al origen. 3) Saco los números fijos de las fórmulas a una hoja de parámetros con celdas con nombre. 4) Organizo el libro en pestañas de datos, cálculos y reportes, usando tablas con referencias estructuradas. 5) Agrego controles de cuadratura que alerten si los totales no calzan. 6) Documento supuestos y fuentes, protejo las hojas de cálculo y comparo los resultados con la versión anterior antes de reemplazarla.',
    '{"metodo": "TECNICA", "criterios": ["Valida las cifras actuales antes de cambiar el libro", "Separa datos, cálculos y presentación", "Reemplaza procesos manuales por Power Query", "Saca los valores fijos a una hoja de parámetros", "Agrega controles, documentación y protección"], "palabras_clave": ["Power Query", "hoja de parámetros", "celdas con nombre", "controles de cuadratura", "documentación", "línea base"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Lo primero es no romper nada: documento qué hace el libro y guardo sus resultados como referencia. Luego automatizo la entrada de datos con Power Query, muevo las constantes a una pestaña de supuestos, ordeno el flujo en datos, cálculo y salida, y agrego chequeos de totales. Antes de liberarlo comparo ambas versiones y dejo un manual breve.", "Aplicaría buenas prácticas de modelamiento: ninguna cifra escrita dentro de una fórmula, insumos en una sola hoja, tablas con nombre, cálculos trazables y reportes que solo leen resultados. Sumaría validaciones automáticas y control de versiones, y capacitaría a otra persona para que el archivo no dependa de una sola."], "sinonimos": [["Power Query", "Obtener y transformar datos"], ["celdas con nombre", "rangos con nombre", "nombres definidos"], ["controles de cuadratura", "cuadratura", "controles de totales"]], "feedback_generico": {"correcto": "¡Excelente! Tu rediseño ataca los riesgos del libro y deja una herramienta que otros pueden entender y mantener.", "medio": "Vas bien, pero te faltó cubrir cómo validas que el nuevo libro entrega las mismas cifras o cómo eliminas los procesos manuales.", "malo": "No te desanimes: repasa buenas prácticas de modelamiento en Excel, como separar insumos, cálculos y reportes, y evitar valores fijos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Heredas un Excel crítico de reportes que solo entendía quien lo creó: tiene valores pegados a mano, números fijos dentro de las fórmulas y pestañas sin orden. ¿Cómo lo rediseñas para que sea confiable y mantenible?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Construyo un modelo con supuestos separados y luego aplico las herramientas de análisis de hipótesis según cada pregunta. 1) Armo una hoja con las celdas de entrada, como precio, volumen, costo unitario y costos fijos, y una celda de margen calculada con fórmulas. 2) Uso Buscar objetivo para saber qué precio o volumen logra un margen específico, como el punto de equilibrio. 3) Creo una Tabla de datos de dos variables para ver el margen con distintas combinaciones de precio y volumen. 4) Defino escenarios pesimista, base y optimista con el Administrador de escenarios. 5) Uso Solver para maximizar el margen con la restricción de capacidad. 6) Presento los resultados explicando los supuestos y sus límites.',
    '{"metodo": "TECNICA", "criterios": ["Separa los supuestos de los cálculos en el modelo", "Usa Buscar objetivo para alcanzar un valor meta", "Usa una tabla de datos de dos variables para la sensibilidad", "Usa Solver con restricciones para optimizar", "Comunica los supuestos y las limitaciones"], "palabras_clave": ["Buscar objetivo", "tabla de datos", "Solver", "escenarios", "restricción", "sensibilidad"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero dejo todos los supuestos en celdas de entrada y el margen como resultado de fórmulas. Con una tabla de datos de dos variables muestro la sensibilidad del margen ante precio y volumen, con Buscar objetivo encuentro el punto de equilibrio y con Solver busco el máximo margen sin superar la capacidad. Finalmente comparo escenarios y explico qué supuestos son más críticos.", "Uso el menú Análisis de hipótesis: escenarios para comparar casos, tabla de datos para la sensibilidad cruzada y Buscar objetivo para metas puntuales. Como hay una restricción de capacidad y se busca el óptimo, complemento con Solver y documento el modelo para que el negocio pueda cambiar los supuestos."], "sinonimos": [["Buscar objetivo", "Goal Seek"], ["tabla de datos", "Data Table"], ["análisis de hipótesis", "análisis Y si", "what-if"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste la herramienta adecuada para cada pregunta del negocio y construiste un modelo ordenado.", "medio": "Vas bien, pero te faltó cubrir alguna parte del encargo, como la sensibilidad cruzada o la optimización con la restricción.", "malo": "No te desanimes: repasa las herramientas de análisis de hipótesis de Excel y en qué caso conviene cada una."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden evaluar cómo cambia el margen de un producto según el precio y el volumen, y encontrar la mejor combinación respetando un límite de capacidad de producción. ¿Cómo lo modelas en Excel?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Le explico que un gráfico debe responder una pregunta con honestidad y sin esfuerzo para quien lo lee, y corregimos juntos cada problema. 1) Reemplazo los gráficos circulares 3D de doce categorías por barras horizontales ordenadas de mayor a menor, porque el 3D distorsiona las proporciones. 2) Hago que el eje de las barras parta en cero, ya que el largo de la barra representa el valor y cortarlo exagera las diferencias. 3) Uso una paleta sobria y un color de acento solo para lo que quiero destacar. 4) Agrego títulos que cuenten la conclusión y etiquetas de datos donde ayuden. 5) Quito adornos como sombras y líneas de cuadrícula innecesarias. 6) Le comparto una guía de estilo del equipo y revisamos juntos su siguiente informe.',
    '{"metodo": "TECNICA", "criterios": ["Da retroalimentación constructiva y formativa", "Reemplaza gráficos circulares con muchas categorías por barras ordenadas", "Exige que el eje de las barras parta en cero", "Usa el color con intención", "Propone títulos con conclusión y menos adornos"], "palabras_clave": ["barras ordenadas", "eje en cero", "color de acento", "gráfico circular", "guía de estilo", "distorsiona"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Le diría que sus gráficos pueden llevar a conclusiones equivocadas y le mostraría por qué. Cambiaría las tortas 3D por barras ordenadas, haría partir el eje desde cero, usaría un color neutro con un solo color destacado y pondría títulos que expliquen el mensaje. Luego le dejaría una guía breve para sus próximos informes.", "Mi retroalimentación partiría por lo positivo y luego por los riesgos: el 3D y el eje cortado distorsionan las magnitudes, y los colores al azar distraen. Le propondría elegir el gráfico según la pregunta, simplificar el diseño y revisar juntos el informe corregido para que aprenda el criterio."], "sinonimos": [["gráfico circular", "gráfico de torta", "pie chart"], ["retroalimentación", "feedback"], ["color de acento", "color destacado"]], "feedback_generico": {"correcto": "¡Excelente! Diste una retroalimentación clara y formativa, y tus correcciones hacen los gráficos más honestos y legibles.", "medio": "Vas bien, pero te faltó corregir alguno de los problemas señalados o explicar por qué afecta la lectura de los datos.", "malo": "No te desanimes: repasa buenas prácticas de visualización, como elegir el gráfico según la pregunta y evitar distorsiones."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un analista junior te muestra un informe en Excel con gráficos circulares 3D de doce categorías, barras cuyo eje vertical no parte en cero y colores elegidos al azar. ¿Qué retroalimentación le das y cómo corriges los gráficos?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Excel para análisis de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Power BI y visualización de datos

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las métricas de cada transacción y sus claves foráneas van en la tabla de hechos, que está al centro del modelo estrella. Las dimensiones, como Producto, Cliente y Calendario, describen el contexto y filtran los hechos mediante relaciones uno a varios.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la tabla de hechos como destino de las métricas", "Distingue hechos de dimensiones", "Menciona las claves foráneas hacia las dimensiones", "Relaciona el modelo estrella con relaciones uno a varios"], "palabras_clave": ["tabla de hechos", "dimensiones", "modelo estrella", "clave foránea", "uno a varios"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["En un esquema estrella, los valores que se suman o promedian, como montos y cantidades, se guardan en la tabla de hechos junto con las claves que apuntan a cada dimensión. Las tablas de dimensión solo describen productos, clientes o fechas.", "La tabla central del modelo registra cada evento de venta con sus valores numéricos y sus llaves. Alrededor quedan las dimensiones, que tienen una fila por entidad y sirven para filtrar y agrupar."], "sinonimos": [["tabla de hechos", "fact table"], ["tabla de dimensión", "dimensión", "dimension table"], ["modelo estrella", "esquema estrella", "star schema"]], "feedback_generico": {"correcto": "¡Bien! Reconoces qué tipo de tabla guarda las transacciones y sus métricas en un modelo estrella.", "medio": "Vas bien, pero repasa qué diferencia a una tabla que registra eventos de una que describe entidades como productos o fechas.", "malo": "No te preocupes: repasa el modelo estrella, en particular el rol de la tabla central y de las tablas que la rodean."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿En qué tabla de un modelo estrella de ventas en Power BI deberían quedar columnas numéricas como Monto y Cantidad de cada transacción, junto con las claves de producto, cliente y fecha?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('En la dimensión Producto, para tener cada venta junto al nombre del producto.', FALSE,
     'Las dimensiones tienen una fila por entidad; guardar ahí cada transacción repetiría los productos y rompería la relación uno a varios.', 1),
    ('En la tabla de hechos, que registra cada venta y se une a las dimensiones.', TRUE,
     'Correcto: la tabla de hechos guarda los eventos medibles con sus claves foráneas, y las dimensiones la filtran mediante relaciones uno a varios.', 2),
    ('En la tabla de calendario, porque toda venta ocurre en una fecha determinada.', FALSE,
     'El calendario es una dimensión con una fila por día; se relaciona con las ventas por la clave de fecha, pero no almacena las transacciones.', 3),
    ('En una tabla puente ubicada entre las dimensiones para conectarlas entre sí.', FALSE,
     'Las tablas puente se usan para resolver relaciones varios a varios; no reemplazan a la tabla de hechos del modelo estrella.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿En qué tabla de un modelo estrella de ventas en Power BI deberían quedar columnas numéricas como Monto y Cantidad de cada transacción, junto con las claves de producto, cliente y fecha?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Para mostrar la evolución de una métrica en el tiempo, lo más adecuado es un gráfico de líneas con los meses en el eje horizontal. Permite ver tendencias, estacionalidad y cambios bruscos de un vistazo, algo que no logran los gráficos de partes de un total.',
    '{"metodo": "TECNICA", "criterios": ["Elige el gráfico de líneas para series de tiempo", "Ubica el tiempo en el eje horizontal", "Explica que permite ver tendencia y estacionalidad", "Descarta gráficos de partes de un total para este caso"], "palabras_clave": ["gráfico de líneas", "serie de tiempo", "tendencia", "eje horizontal", "estacionalidad"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Uso un gráfico de líneas, porque su función es mostrar cómo cambia un valor a lo largo del tiempo. Con los meses en el eje X se aprecian las alzas, las caídas y los patrones que se repiten cada año.", "Cuando el objetivo es ver la evolución temporal, conviene una línea continua ordenada por fecha. Los gráficos circulares, los mapas de árbol o las tarjetas sirven para proporciones o totales, no para tendencias."], "sinonimos": [["gráfico de líneas", "line chart"], ["gráfico circular", "gráfico de torta", "pie chart"], ["mapa de árbol", "treemap"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste el visual que mejor comunica la evolución de una métrica en el tiempo.", "medio": "Vas en buen camino, pero piensa qué visual permite seguir la tendencia mes a mes sin perder el orden temporal.", "malo": "Sigue practicando: repasa qué tipo de gráfico se usa para series de tiempo y cuáles se usan para proporciones o totales."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Necesitas mostrar cómo evolucionaron las ventas mes a mes durante los últimos dos años. ¿Qué visual de Power BI es el más adecuado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un gráfico circular con una porción por cada mes del período.', FALSE,
     'Un gráfico circular muestra partes de un total; con 24 porciones es imposible leer la tendencia en el tiempo.', 1),
    ('Un mapa de árbol con un rectángulo por cada mes del período.', FALSE,
     'El mapa de árbol compara proporciones dentro de un total y no muestra el orden temporal de los datos.', 2),
    ('Un gráfico de líneas con los meses en el eje horizontal.', TRUE,
     'Correcto: el gráfico de líneas es el estándar para series de tiempo porque muestra tendencia, estacionalidad y cambios de pendiente.', 3),
    ('Una tarjeta que muestre el total vendido en los dos años.', FALSE,
     'La tarjeta muestra un solo número y oculta por completo la evolución mensual que se pide.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Necesitas mostrar cómo evolucionaron las ventas mes a mes durante los últimos dos años. ¿Qué visual de Power BI es el más adecuado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un segmentador es un visual que muestra una lista de valores, como años, regiones o categorías, y permite al usuario filtrar de forma interactiva los demás visuales de la página. Se puede sincronizar entre páginas, pero no es un mecanismo de seguridad.',
    '{"metodo": "TECNICA", "criterios": ["Explica que el segmentador filtra otros visuales", "Menciona que el usuario elige el filtro de forma interactiva", "Distingue el segmentador de la seguridad a nivel de fila", "Sabe que puede sincronizarse entre páginas"], "palabras_clave": ["segmentador", "filtrar", "interactivo", "visuales", "sincronizar"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Sirve para que quien ve el informe elija valores, por ejemplo una región o un año, y todos los gráficos conectados se actualicen mostrando solo esos datos. Es un filtro visible y fácil de usar.", "Es un control de filtro en pantalla: el usuario marca una o varias opciones y los visuales de la página se recalculan con esa selección. No restringe permisos; para eso existe la seguridad a nivel de fila."], "sinonimos": [["segmentador", "segmentación de datos", "slicer"], ["seguridad a nivel de fila", "RLS", "row-level security"]], "feedback_generico": {"correcto": "¡Bien hecho! Tienes claro el propósito de este visual y lo distingues de otras funciones del informe.", "medio": "Vas bien, pero precisa qué efecto tiene este visual sobre el resto de la página y quién lo controla.", "malo": "No te desanimes: repasa los tipos de visuales de Power BI y cuáles sirven para filtrar la información de forma interactiva."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Para qué sirve un segmentador (segmentación de datos) en un informe de Power BI?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Para que el usuario filtre de forma interactiva los visuales de la página.', TRUE,
     'Correcto: el segmentador muestra valores, como años o regiones, y al seleccionarlos filtra los demás visuales de la página, o de varias si se sincroniza.', 1),
    ('Para dividir la tabla de hechos en tablas más pequeñas y acelerar el modelo.', FALSE,
     'El segmentador no cambia la estructura del modelo; solo aplica filtros en el informe.', 2),
    ('Para programar la actualización automática del informe en el servicio.', FALSE,
     'La actualización programada se configura en el modelo semántico dentro del servicio de Power BI, no con un visual.', 3),
    ('Para restringir qué filas puede ver cada usuario según su rol.', FALSE,
     'Eso es la seguridad a nivel de fila (RLS); el propio usuario puede cambiar un segmentador, así que no es un mecanismo de seguridad.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Para qué sirve un segmentador (segmentación de datos) en un informe de Power BI?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Limpio los datos en el Editor de Power Query para que cada paso quede registrado y se repita en cada actualización. 1) Cargo el archivo con Obtener datos y reviso la calidad y el perfil de cada columna. 2) Elimino las filas en blanco con Quitar filas. 3) Aplico Recortar a la columna de comunas para eliminar los espacios sobrantes y unifico mayúsculas y minúsculas. 4) Cambio el tipo de la columna de fecha a Fecha, usando la configuración regional si viene como día, mes y año. 5) Reviso las celdas con error que aparezcan y las corrijo o las quito. 6) Cierro y aplico, sabiendo que los pasos aplicados se repetirán solos al actualizar.',
    '{"metodo": "TECNICA", "criterios": ["Usa el Editor de Power Query en lugar de editar el archivo a mano", "Elimina las filas en blanco", "Recorta espacios y unifica el texto", "Convierte el tipo de dato de fecha considerando la configuración regional", "Sabe que los pasos aplicados se repiten al actualizar"], "palabras_clave": ["Power Query", "quitar filas", "recortar", "tipo de dato", "configuración regional", "pasos aplicados"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Abro los datos en el Editor de Power Query y reviso la calidad de cada columna. Elimino las filas vacías, aplico Recortar a la columna de comunas para sacar los espacios y transformo la columna de fecha al tipo Fecha indicando el formato regional correcto. Así cada paso queda grabado y se repite solo en cada actualización.", "Prefiero no corregir el archivo de origen, sino transformar en Power Query. Filtro las filas nulas, limpio el texto con Recortar y Limpiar, y cambio el tipo de la fecha con la configuración regional de Chile. Luego reviso los errores que aparezcan antes de cerrar y aplicar."], "sinonimos": [["Power Query", "Editor de Power Query", "editor de consultas"], ["recortar", "trim"], ["pasos aplicados", "applied steps"]], "feedback_generico": {"correcto": "¡Excelente! Abordaste cada problema de calidad con la transformación adecuada y pensaste en que la limpieza se repita sola.", "medio": "Vas bien, pero te faltó resolver alguno de los problemas del archivo o explicar cómo se mantiene la limpieza al actualizar.", "malo": "No te preocupes: repasa las transformaciones básicas del Editor de Power Query para filas, texto y tipos de datos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Te entregan un archivo de ventas con filas en blanco, fechas guardadas como texto y nombres de comunas con espacios sobrantes. ¿Cómo lo limpias en Power Query antes de cargarlo a Power BI?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Un KPI es un indicador clave que mide el avance hacia un objetivo del negocio, y se entiende mejor cuando aparece junto a su meta. 1) Defino con el negocio qué indicador importa, por ejemplo las ventas del mes, y lo creo como medida. 2) Le agrego una meta o un valor de comparación, como el mismo mes del año anterior. 3) Lo muestro en un visual de tarjeta o de KPI en la parte superior de la página. 4) Uso colores o íconos para indicar si está sobre o bajo la meta. 5) Agrego una tendencia breve para ver si mejora o empeora.',
    '{"metodo": "TECNICA", "criterios": ["Define KPI como un indicador ligado a un objetivo", "Incluye una meta o un valor de comparación", "Propone un visual de tarjeta o de KPI destacado", "Usa color o íconos para indicar el estado", "Muestra la tendencia del indicador"], "palabras_clave": ["indicador clave", "meta", "tarjeta", "comparación", "tendencia"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["Un KPI es una métrica que dice si el negocio está cumpliendo un objetivo, como el porcentaje de cumplimiento de ventas. En Power BI lo pondría arriba, en una tarjeta grande o en el visual de KPI, con la meta al lado y un color que indique si vamos bien. También sumaría una línea pequeña con la evolución reciente.", "Es un indicador de desempeño que se compara contra una meta. Para que se lea rápido, lo muestro destacado al inicio de la página, con el valor actual, el objetivo, la diferencia y un formato condicional verde o rojo."], "sinonimos": [["KPI", "indicador clave de desempeño", "indicador clave"], ["tarjeta", "card"]], "feedback_generico": {"correcto": "¡Muy bien! Definiste el concepto con claridad y propusiste una forma de mostrarlo que se entiende rápido.", "medio": "Vas por buen camino, pero a tu propuesta le falta algo que dé contexto al número, como una referencia para saber si es bueno o malo.", "malo": "No te preocupes: repasa qué hace que un indicador sea clave y qué elementos visuales ayudan a interpretarlo de inmediato."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es un KPI y cómo lo mostrarías en un dashboard de Power BI para que se entienda de un vistazo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Una columna calculada se evalúa fila por fila al actualizar el modelo y queda guardada, mientras que una medida se calcula al momento según los filtros del visual. 1) La columna calculada usa el contexto de fila y ocupa memoria, porque se almacena en la tabla. 2) Sirve para crear atributos, por ejemplo un tramo de precio o una categoría que quiero usar en un segmentador o en un eje. 3) La medida no se almacena y responde al contexto de filtro del informe. 4) Sirve para agregaciones, como Total Ventas = SUM(Ventas[Monto]) o un margen porcentual. 5) Como regla, si el valor debe cambiar con los filtros uso una medida, y si necesito agrupar o filtrar por él, una columna.',
    '{"metodo": "TECNICA", "criterios": ["Explica que la columna calculada se evalúa fila por fila y se almacena", "Explica que la medida se calcula según los filtros del visual", "Da un ejemplo válido de columna calculada", "Da un ejemplo válido de medida", "Menciona el impacto en memoria"], "palabras_clave": ["columna calculada", "medida", "contexto de fila", "contexto de filtro", "memoria"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["La columna calculada agrega un valor fijo a cada fila de una tabla cuando se actualiza el modelo, por ejemplo un tramo de precio para usar en un eje. La medida no se guarda: se recalcula en cada visual según los filtros, como la suma de ventas o un margen. Por rendimiento, prefiero medidas para las agregaciones.", "Una columna calculada es como una columna más de la tabla, calculada con el contexto de fila y guardada en memoria. Una medida es una fórmula de agregación dinámica que responde a segmentadores y ejes. Uso columna si necesito agrupar o filtrar por el resultado, y medida si el resultado depende de la selección."], "sinonimos": [["medida", "measure"], ["columna calculada", "calculated column"]], "feedback_generico": {"correcto": "¡Bien hecho! Distinguiste cuándo se calcula cada uno y diste ejemplos que muestran que sabes cuándo usarlos.", "medio": "Vas bien, pero te faltó precisar cuándo se evalúa cada uno o dar un ejemplo concreto para ambos casos.", "malo": "No te desanimes: repasa cómo y cuándo se calculan las columnas calculadas y las medidas en DAX, y cuál de ellas consume memoria."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica la diferencia entre una columna calculada y una medida en DAX, y da un ejemplo de cuándo usarías cada una.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El filtro Clientes[Ciudad] = "Santiago" equivale a FILTER(ALL(Clientes[Ciudad]), Clientes[Ciudad] = "Santiago"), así que reemplaza el filtro de ciudad que llega desde cada fila. Por eso todas las filas, y también el total, muestran las ventas de Santiago. Para respetar el filtro de la fila habría que usar KEEPFILTERS.',
    '{"metodo": "TECNICA", "criterios": ["Sabe que CALCULATE modifica el contexto de filtro", "Explica que un filtro sobre la misma columna reemplaza el filtro externo", "Predice que todas las filas muestran el mismo valor", "Menciona KEEPFILTERS como alternativa para intersectar filtros"], "palabras_clave": ["CALCULATE", "contexto de filtro", "reemplaza", "KEEPFILTERS", "filtro externo"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["CALCULATE sobrescribe el filtro que existe sobre Clientes[Ciudad] con el valor Santiago. Como la tabla agrupa justamente por esa columna, cada fila pierde su ciudad y todas terminan mostrando el monto de Santiago.", "Un filtro booleano dentro de CALCULATE quita el filtro previo de esa columna y pone el nuevo. Así, sin importar la fila, se calcula siempre la suma de Santiago; con KEEPFILTERS, en cambio, solo la fila Santiago tendría valor."], "sinonimos": [["contexto de filtro", "filter context"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes cómo CALCULATE interactúa con los filtros que vienen del visual, uno de los puntos más difíciles de DAX.", "medio": "Vas bien, pero revisa qué pasa cuando el filtro de CALCULATE y el filtro de la fila actúan sobre la misma columna.", "malo": "No te preocupes: repasa cómo CALCULATE modifica el contexto de filtro y la diferencia entre reemplazar e intersectar filtros."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tienes la medida Ventas Santiago = CALCULATE(SUM(Ventas[Monto]), Clientes[Ciudad] = "Santiago") en una tabla que tiene Clientes[Ciudad] en las filas. ¿Qué muestra cada fila?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Las ventas de su propia ciudad, porque el filtro de la fila tiene prioridad.', FALSE,
     'En CALCULATE, un filtro sobre la misma columna reemplaza el filtro externo de la fila, así que no se respeta la ciudad de cada fila.', 1),
    ('Las ventas de Santiago solo en la fila Santiago y vacío en las demás filas.', FALSE,
     'Ese resultado se obtendría con KEEPFILTERS, que intersecta los filtros; sin él, el filtro de CALCULATE sobrescribe el de la fila.', 2),
    ('Un error, porque CALCULATE no acepta filtros escritos con el signo igual.', FALSE,
     'La forma Columna = valor es un filtro booleano válido en CALCULATE; DAX lo traduce internamente a un FILTER sobre ALL de esa columna.', 3),
    ('El total de ventas de Santiago repetido en todas las filas de la tabla.', TRUE,
     'Correcto: el filtro booleano de CALCULATE reemplaza el filtro de ciudad que viene de cada fila, por eso todas muestran el mismo valor.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Tienes la medida Ventas Santiago = CALCULATE(SUM(Ventas[Monto]), Clientes[Ciudad] = "Santiago") en una tabla que tiene Clientes[Ciudad] en las filas. ¿Qué muestra cada fila?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Como cada producto aparece una vez en Productos y muchas veces en Ventas, la relación es de uno a varios. Lo recomendado es una dirección de filtro única, desde la dimensión Productos hacia la tabla de hechos Ventas, dejando los filtros bidireccionales solo para casos justificados.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la cardinalidad uno a varios", "Reconoce a Productos como el lado uno", "Elige una dirección de filtro única desde la dimensión", "Explica los riesgos del filtro bidireccional"], "palabras_clave": ["uno a varios", "cardinalidad", "dirección del filtro", "filtro único", "bidireccional"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["La relación correcta tiene a Productos en el lado uno, porque su clave no se repite, y a Ventas en el lado varios. El filtro debe ir en un solo sentido, de Productos a Ventas, ya que la bidireccionalidad agrega ambigüedad y afecta el rendimiento.", "Productos es una dimensión con clave única y Ventas es la tabla de hechos, así que corresponde una relación de uno a varios. Dejo el filtro cruzado en Único para que la dimensión filtre los hechos, como en un modelo estrella clásico."], "sinonimos": [["uno a varios", "uno a muchos", "one-to-many"], ["dirección del filtro", "dirección de filtro cruzado", "cross filter direction"], ["bidireccional", "ambas direcciones"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste la cardinalidad y la dirección de filtro que corresponden a un modelo estrella bien diseñado.", "medio": "Vas bien en una parte de la configuración, pero revisa si la cardinalidad y la dirección del filtro son las dos correctas.", "malo": "No te desanimes: repasa los tipos de cardinalidad de una relación y en qué sentido deben fluir los filtros entre dimensiones y hechos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En tu modelo, la tabla Productos tiene una fila por producto y la tabla Ventas tiene muchas filas por producto. ¿Qué configuración de relación es la recomendada?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Varios a varios, con filtro en ambas direcciones para tener más flexibilidad.', FALSE,
     'No es varios a varios porque cada producto aparece una sola vez en Productos; además el filtro bidireccional puede generar ambigüedad y lentitud.', 1),
    ('Uno a varios, con dirección de filtro única desde Productos hacia Ventas.', TRUE,
     'Correcto: Productos es el lado uno y Ventas el lado varios; el filtro único desde la dimensión hacia los hechos es el diseño estándar del modelo estrella.', 2),
    ('Uno a uno, porque cada venta corresponde a un solo producto del catálogo.', FALSE,
     'Que cada venta tenga un producto no la hace uno a uno: un mismo producto aparece en muchas ventas.', 3),
    ('Uno a varios, con dirección de filtro única desde Ventas hacia Productos.', FALSE,
     'La cardinalidad es correcta, pero la dirección está invertida: en una relación uno a varios con filtro único, el filtro fluye desde la dimensión hacia los hechos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'En tu modelo, la tabla Productos tiene una fila por producto y la tabla Ventas tiene muchas filas por producto. ¿Qué configuración de relación es la recomendada?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La inteligencia de tiempo de DAX necesita una tabla de calendario con una fila por cada día, sin fechas faltantes ni repetidas, que cubra años completos. Además, conviene marcarla como tabla de fechas y relacionarla con la columna de fecha de la tabla de hechos.',
    '{"metodo": "TECNICA", "criterios": ["Exige fechas continuas, sin huecos", "Exige fechas únicas, sin duplicados", "Menciona que debe cubrir años completos", "Recomienda marcarla como tabla de fechas", "Relaciona el calendario con la tabla de hechos"], "palabras_clave": ["tabla de calendario", "fechas continuas", "años completos", "tabla de fechas", "inteligencia de tiempo"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El calendario tiene que ser una columna de tipo fecha con todos los días del rango, uno por fila y sin saltos, desde el primer día del primer año hasta el último día del último año. Luego se marca como tabla de fechas y se relaciona con los hechos.", "Funciones como SAMEPERIODLASTYEAR asumen un calendario completo. Si la tabla solo tiene los días con ventas, tiene duplicados o cubre años parciales, los cálculos de períodos anteriores y acumulados salen mal."], "sinonimos": [["tabla de calendario", "tabla de fechas", "dimensión fecha"], ["inteligencia de tiempo", "time intelligence"]], "feedback_generico": {"correcto": "¡Excelente! Conoces los requisitos de la tabla de fechas que hacen confiables los cálculos de períodos.", "medio": "Vas bien, pero te falta alguna condición clave de la tabla de fechas para que los cálculos de períodos no fallen.", "malo": "Sigue practicando: repasa cómo debe construirse una tabla de calendario en Power BI y por qué las funciones de tiempo dependen de ella."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Las medidas con SAMEPERIODLASTYEAR y TOTALYTD de tu informe devuelven valores incorrectos. ¿Qué requisito debe cumplir la tabla de calendario para que estas funciones trabajen bien?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Contener solo las fechas en que hubo ventas, para no inflar el modelo.', FALSE,
     'Si faltan días, las funciones de inteligencia de tiempo calculan mal los períodos; el calendario debe ser continuo aunque haya días sin ventas.', 1),
    ('Tener la fecha como texto con formato AAAA-MM-DD, para ordenarla bien.', FALSE,
     'La columna debe ser de tipo fecha; como texto, DAX no la puede usar en las funciones de inteligencia de tiempo.', 2),
    ('Tener una fila por día, sin huecos ni duplicados, cubriendo años completos.', TRUE,
     'Correcto: un calendario continuo, con fechas únicas y años completos, idealmente marcado como tabla de fechas, es lo que estas funciones necesitan.', 3),
    ('Estar relacionada con todas las demás tablas mediante filtros bidireccionales.', FALSE,
     'La dirección bidireccional no es un requisito y suele traer ambigüedad; basta una relación uno a varios desde el calendario hacia los hechos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Las medidas con SAMEPERIODLASTYEAR y TOTALYTD de tu informe devuelven valores incorrectos. ¿Qué requisito debe cumplir la tabla de calendario para que estas funciones trabajen bien?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo diagnostico siguiendo el camino entre el servicio de Power BI y el servidor local. 1) Reviso el historial de actualizaciones del modelo semántico para leer el mensaje de error exacto. 2) Verifico que haya una puerta de enlace de datos local instalada en un equipo siempre encendido, en línea y actualizada. 3) Confirmo que el origen de datos esté creado en la puerta de enlace con el mismo servidor y la misma base que usa el archivo pbix, y con credenciales vigentes. 4) En la configuración del modelo semántico asigno esa conexión de la puerta de enlace. 5) Pruebo con Actualizar ahora y luego reviso el horario programado y la zona horaria. 6) Si falla por tiempo de espera, reviso el volumen de datos y el plegado de consultas.',
    '{"metodo": "TECNICA", "criterios": ["Revisa el historial y el mensaje de error de la actualización", "Identifica la necesidad de una puerta de enlace de datos local", "Verifica el origen de datos y las credenciales en la puerta de enlace", "Asigna la conexión en la configuración del modelo semántico", "Prueba la actualización y revisa la programación"], "palabras_clave": ["puerta de enlace", "historial de actualizaciones", "credenciales", "modelo semántico", "origen de datos", "actualización programada"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero leo el error en el historial de actualizaciones del servicio. Como la base está en servidores locales, el servicio necesita una puerta de enlace instalada y en línea; reviso que el origen de datos esté configurado con el mismo servidor y base que el pbix y que las credenciales sigan vigentes. Luego vinculo esa conexión al modelo semántico y pruebo una actualización manual.", "El servicio en la nube no puede llegar directo a un servidor interno, así que reviso la puerta de enlace de datos local: si está encendida, actualizada y con el origen bien registrado. También verifico las credenciales, que el nombre del servidor sea idéntico al del archivo y la configuración del modelo semántico, y después pruebo antes de volver a programar."], "sinonimos": [["puerta de enlace", "gateway", "on-premises data gateway"], ["modelo semántico", "conjunto de datos", "dataset"], ["actualización programada", "scheduled refresh"]], "feedback_generico": {"correcto": "¡Muy bien! Diagnosticaste de forma ordenada y consideraste el componente clave para conectar el servicio con datos locales.", "medio": "Vas bien, pero te faltó revisar alguna pieza entre el servicio y el servidor local, como la configuración o las credenciales.", "malo": "No te desanimes: repasa cómo el servicio de Power BI accede a datos que están dentro de la red de la empresa."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Publicaste un informe que se conecta a una base SQL Server instalada en los servidores de la empresa, y la actualización programada en el servicio de Power BI falla. ¿Cómo lo diagnosticas y lo resuelves?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Ocurre porque una columna calculada tiene contexto de fila pero no contexto de filtro, y SUM no filtra por la fila actual. 1) El contexto de fila solo indica qué producto se está evaluando, pero no filtra la tabla Ventas. 2) Por eso SUM recorre todas las ventas y devuelve el total general en cada fila. 3) Lo corrijo envolviendo la suma en CALCULATE, que hace la transición de contexto y convierte la fila actual en un filtro que llega a Ventas por la relación. 4) Otra opción es SUMX(RELATEDTABLE(Ventas), Ventas[Monto]). 5) Además evalúo si de verdad necesito una columna, porque una medida de ventas usada en un visual por producto resuelve lo mismo sin ocupar memoria.',
    '{"metodo": "TECNICA", "criterios": ["Explica que la columna calculada tiene contexto de fila pero no de filtro", "Explica por qué SUM devuelve el total general", "Propone CALCULATE para la transición de contexto", "Menciona una alternativa como RELATEDTABLE o una medida", "Evalúa si conviene una medida en vez de una columna"], "palabras_clave": ["contexto de fila", "contexto de filtro", "transición de contexto", "CALCULATE", "RELATEDTABLE"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["En una columna calculada DAX recorre la tabla fila por fila, pero ese contexto de fila no filtra la tabla Ventas, así que SUM suma todo. Si escribo CALCULATE(SUM(Ventas[Monto])), la fila actual se convierte en filtro y viaja por la relación hasta Ventas, entregando el total de cada producto.", "El problema es que el contexto de fila no se propaga solo por las relaciones. Hay que forzar la transición de contexto con CALCULATE o usar SUMX sobre RELATEDTABLE(Ventas). Aun así, lo más limpio suele ser una medida de ventas mostrada en un visual por producto."], "sinonimos": [["contexto de fila", "row context"], ["transición de contexto", "context transition"], ["columna calculada", "calculated column"]], "feedback_generico": {"correcto": "¡Excelente! Explicaste la causa con los conceptos correctos de DAX y propusiste una solución sólida.", "medio": "Vas bien, pero tu explicación no deja claro por qué la fila actual no filtra la otra tabla o cómo lograr que lo haga.", "malo": "No te preocupes: repasa la diferencia entre contexto de fila y contexto de filtro, y cómo CALCULATE se relaciona con ambos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En la tabla Productos creaste la columna calculada Total = SUM(Ventas[Monto]) y todas las filas muestran el mismo valor, el total general de ventas. ¿Por qué ocurre y cómo lo corriges?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo rediseño partiendo de las decisiones que el usuario necesita tomar, no de todos los datos disponibles. 1) Converso con el gerente para saber qué preguntas debe responder y con qué frecuencia mira el informe. 2) Dejo arriba a la izquierda los tres a cinco KPIs principales, porque es lo primero que se lee. 3) Elimino o combino los gráficos redundantes y llevo el detalle a otras páginas con obtención de detalles o información sobre herramientas. 4) Uso pocos colores y con significado, por ejemplo rojo solo para alertas. 5) Escribo títulos que cuentan la conclusión, como Ventas del norte caen 8% en octubre. 6) Pruebo el nuevo diseño con usuarios reales y lo ajusto.',
    '{"metodo": "TECNICA", "criterios": ["Parte de las preguntas y decisiones del usuario", "Prioriza pocos KPIs en la zona superior izquierda", "Reduce visuales y lleva el detalle a otras páginas", "Usa el color con significado y de forma consistente", "Usa títulos que comunican la conclusión"], "palabras_clave": ["audiencia", "KPIs", "jerarquía visual", "obtención de detalles", "storytelling", "color"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero entiendo quién lo usa y qué decisiones toma. Después dejo en la portada solo los indicadores principales arriba, uno o dos gráficos que expliquen su evolución, y llevo el resto a páginas de detalle. Uso una paleta sobria, resalto solo lo importante y escribo títulos que digan la conclusión.", "Aplico storytelling: el informe debe responder una pregunta en orden, de lo general a lo particular. Elimino gráficos redundantes, agrupo por tema, uso obtención de detalles e información sobre herramientas para el detalle, y valido el nuevo diseño con el gerente y otros usuarios."], "sinonimos": [["obtención de detalles", "drill-through", "drillthrough"], ["storytelling", "narrativa con datos", "contar historias con datos"], ["KPI", "indicador clave"]], "feedback_generico": {"correcto": "¡Muy bien! Tu rediseño parte del usuario y aplica buenas prácticas de jerarquía y narrativa visual.", "medio": "Vas bien, pero te faltó explicar cómo priorizas la información o cómo validas el diseño con quienes lo usan.", "malo": "No te desanimes: repasa principios de diseño de dashboards, como la jerarquía visual, el uso del color y el storytelling con datos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un gerente te dice que tu dashboard tiene 15 gráficos en una sola página y que nadie entiende qué mirar. ¿Cómo lo rediseñas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El motor VertiPaq comprime cada columna por separado, y las que tienen muchos valores distintos, como identificadores únicos o fecha y hora con segundos, son las que más memoria ocupan. Quitar las columnas que nadie usa, o separar la fecha de la hora, suele reducir mucho el tamaño del modelo.',
    '{"metodo": "TECNICA", "criterios": ["Sabe que el modelo importado se comprime por columna", "Identifica la alta cardinalidad como causa principal del tamaño", "Propone eliminar columnas sin uso", "Distingue el costo en memoria de columnas calculadas frente a medidas"], "palabras_clave": ["alta cardinalidad", "VertiPaq", "compresión", "columnas sin uso", "tamaño del modelo"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Lo que más pesa en un modelo importado son las columnas con muchos valores únicos. Por eso lo primero es eliminar las columnas que ningún visual ni medida usa y reducir la cardinalidad, por ejemplo separando fecha y hora o quitando decimales innecesarios.", "VertiPaq usa compresión por columnas y diccionarios, que funcionan mal con columnas casi únicas. Sacar identificadores, textos largos o marcas de tiempo que no se analizan suele bajar el tamaño más que cualquier cambio en relaciones o visuales."], "sinonimos": [["alta cardinalidad", "muchos valores distintos"], ["VertiPaq", "motor en memoria"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste qué determina el tamaño de un modelo importado y la acción con mayor impacto.", "medio": "Vas bien, pero revisa qué tipo de columnas consume más memoria en el motor de Power BI y por qué.", "malo": "No te desanimes: repasa cómo comprime VertiPaq los datos y qué factores hacen crecer un modelo importado."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Un modelo importado en Power BI pesa varios gigabytes y las páginas cargan lento. ¿Qué acción suele reducir más el tamaño del modelo en memoria?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Quitar columnas sin uso, sobre todo las de alta cardinalidad, como ID o fecha y hora.', TRUE,
     'Correcto: VertiPaq comprime por columna y las columnas con muchos valores distintos son las que más pesan; eliminarlas o dividirlas reduce mucho el modelo.', 1),
    ('Convertir las medidas en columnas calculadas para dejar todos los valores precalculados.', FALSE,
     'Las columnas calculadas se almacenan y ocupan memoria, mientras que las medidas no; el cambio aumentaría el tamaño del modelo.', 2),
    ('Cambiar todas las relaciones a filtro bidireccional para que el motor filtre menos.', FALSE,
     'La dirección del filtro no reduce el tamaño, y los filtros bidireccionales suelen empeorar el rendimiento y generar ambigüedad.', 3),
    ('Guardar los valores numéricos como texto para mejorar la compresión por diccionario.', FALSE,
     'Los números se comprimen mejor que el texto; convertirlos suele aumentar el tamaño y además complica los cálculos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Un modelo importado en Power BI pesa varios gigabytes y las páginas cargan lento. ¿Qué acción suele reducir más el tamaño del modelo en memoria?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Lo recomendado es seguridad a nivel de fila dinámica: una tabla que asocia cada correo con su región, relacionada con el modelo, y un rol cuyo filtro DAX compara esa columna con USERPRINCIPALNAME(). Así un solo informe sirve a todos y los cambios de asignación se hacen en la tabla, no en el diseño.',
    '{"metodo": "TECNICA", "criterios": ["Elige seguridad a nivel de fila dinámica", "Usa una tabla de asignación de usuarios y regiones", "Usa USERPRINCIPALNAME en el filtro del rol", "Explica que filtros y segmentadores no son seguridad", "Considera la mantenibilidad con un solo informe"], "palabras_clave": ["RLS dinámica", "USERPRINCIPALNAME", "rol", "tabla de usuarios", "filtro DAX"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Implemento RLS con un rol que filtra la tabla de regiones según el usuario que inicia sesión, usando USERPRINCIPALNAME() y una tabla de permisos. Luego asigno los usuarios al rol en el servicio y pruebo con la opción Ver como.", "La forma correcta es aplicar la seguridad en el modelo, no en el informe. Una tabla de mapeo entre usuario y región y un rol dinámico permiten que cada jefe vea solo su región en el mismo informe, y basta editar la tabla cuando cambian las asignaciones."], "sinonimos": [["RLS", "seguridad a nivel de fila", "row-level security"], ["rol", "role"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste una solución de seguridad real, escalable y fácil de mantener cuando cambian las asignaciones.", "medio": "Vas bien, pero revisa si tu solución protege de verdad los datos y si escala cuando cambian los usuarios.", "malo": "No te desanimes: repasa la diferencia entre filtrar un informe y aplicar seguridad sobre el modelo de datos en Power BI."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Necesitas que cada jefe regional vea solo las ventas de su región en un mismo informe publicado, y las asignaciones de regiones cambian seguido. ¿Cuál es el enfoque más seguro y mantenible?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Publicar una copia del informe por región y compartir cada copia por separado.', FALSE,
     'Funciona al principio, pero multiplica informes y modelos; cada cambio de diseño o de asignación se vuelve inmanejable.', 1),
    ('Agregar un segmentador de región y ocultarlo en las páginas que ven los jefes.', FALSE,
     'Un segmentador oculto tiene el mismo valor para todos los usuarios, así que no puede mostrar a cada jefe su región; además, ocultar un filtro no protege los datos del modelo.', 2),
    ('Usar RLS dinámica con una tabla de usuarios y regiones y USERPRINCIPALNAME().', TRUE,
     'Correcto: un solo rol filtra según el correo del usuario conectado, y las asignaciones se mantienen actualizando la tabla, no el informe.', 3),
    ('Aplicar filtros a nivel de informe y bloquearlos antes de publicar el informe.', FALSE,
     'Un filtro bloqueado tiene el mismo valor para todos y solo impide cambiarlo en la interfaz; no distingue usuarios ni protege el modelo.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Necesitas que cada jefe regional vea solo las ventas de su región en un mismo informe publicado, y las asignaciones de regiones cambian seguido. ¿Cuál es el enfoque más seguro y mantenible?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'La actualización incremental divide la tabla en particiones por fecha usando los parámetros RangeStart y RangeEnd en Power Query, de modo que solo se refresca el período reciente y la historia queda guardada. Funciona mejor cuando el filtro de fechas se pliega a la consulta del origen.',
    '{"metodo": "TECNICA", "criterios": ["Propone actualización incremental", "Menciona los parámetros RangeStart y RangeEnd", "Explica las particiones por fecha", "Considera el plegado de consultas", "Descarta soluciones que no reducen el volumen procesado"], "palabras_clave": ["actualización incremental", "RangeStart", "RangeEnd", "particiones", "plegado de consultas"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Lo adecuado es la actualización incremental: defino en Power Query los parámetros de fecha y hora RangeStart y RangeEnd, filtro la tabla con ellos y configuro cuántos años archivar y cuántos días refrescar. Así cada noche solo se recargan las particiones recientes.", "En vez de recargar ocho años completos, conviene particionar la tabla por fecha y refrescar solo la ventana que cambia. Eso es lo que hace la actualización incremental, y rinde mejor si el origen admite plegado de consultas."], "sinonimos": [["actualización incremental", "incremental refresh"], ["plegado de consultas", "query folding"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste la técnica que ataca la causa: procesar solo lo que cambia en una tabla grande.", "medio": "Vas bien, pero revisa qué solución reduce de verdad el volumen de datos que se procesa en cada actualización.", "malo": "No te desanimes: repasa las opciones de Power BI para actualizar tablas grandes sin recargar toda la historia."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Una tabla de hechos con ocho años de historia tarda horas en actualizarse cada noche, aunque solo cambian los datos de los últimos días. ¿Qué solución es la más adecuada en Power BI?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Programar más actualizaciones al día para que cada una procese una menor cantidad de filas.', FALSE,
     'Cada actualización completa vuelve a cargar toda la tabla; programar más actualizaciones solo multiplica la carga.', 1),
    ('Separar el archivo en ocho informes, uno por año, y unirlos con marcadores.', FALSE,
     'Los marcadores guardan estados de la vista, no unen modelos; además se fragmentaría el análisis entre años.', 2),
    ('Desactivar la carga de la tabla de calendario para liberar tiempo de proceso.', FALSE,
     'El calendario es pequeño y necesario para la inteligencia de tiempo; quitarlo no resuelve el costo de la tabla de hechos.', 3),
    ('Configurar actualización incremental con los parámetros RangeStart y RangeEnd.', TRUE,
     'Correcto: la tabla se divide en particiones por fecha y solo se refrescan las recientes, idealmente con plegado de consultas hacia el origen.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Power BI y visualización de datos' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Una tabla de hechos con ocho años de historia tarda horas en actualizarse cada noche, aunque solo cambian los datos de los últimos días. ¿Qué solución es la más adecuada en Power BI?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño un modelo estrella con dos tablas de hechos de distinta granularidad que comparten dimensiones. 1) Defino la granularidad de cada hecho, con Ventas por día, producto y tienda, y Metas por mes y tienda. 2) Creo dimensiones compartidas de Tienda y Producto, y una tabla Calendario marcada como tabla de fechas. 3) Relaciono Metas con Calendario usando el primer día de cada mes, o con una dimensión de mes, y no la relaciono con Producto. 4) Uso relaciones de uno a varios con filtro en una sola dirección desde las dimensiones. 5) Creo medidas de Ventas, Meta y Cumplimiento con DIVIDE, y evito que la meta se muestre en niveles más finos que su granularidad, por ejemplo con ISINSCOPE. 6) Documento el modelo y valido los totales con el negocio.',
    '{"metodo": "TECNICA", "criterios": ["Identifica dos tablas de hechos con distinta granularidad", "Usa dimensiones compartidas y una tabla de calendario", "Relaciona las metas al nivel de mes y tienda", "Usa relaciones uno a varios con filtro único", "Controla cómo se muestran las metas en niveles más finos"], "palabras_clave": ["granularidad", "tablas de hechos", "dimensiones compartidas", "tabla de calendario", "ISINSCOPE", "DIVIDE"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Separaría Ventas y Metas en dos tablas de hechos, porque no tienen el mismo nivel de detalle, y las conectaría a dimensiones comunes de Tienda y Calendario. Producto solo filtra Ventas. Las medidas de cumplimiento comparan ambas al nivel de mes y tienda, y oculto la meta cuando el usuario baja a día o a producto.", "No mezclaría metas y ventas en una sola tabla, porque repetiría la meta en cada venta. Uso un modelo estrella con dimensiones conformadas, una tabla de fechas marcada y relaciones simples. Para la meta mensual relaciono por mes y valido con el negocio que los totales y porcentajes cuadren."], "sinonimos": [["granularidad", "grano", "nivel de detalle"], ["dimensiones compartidas", "dimensiones conformadas"]], "feedback_generico": {"correcto": "¡Excelente! Diseñaste un modelo que respeta la granularidad de cada tabla y evita totales engañosos.", "medio": "Vas bien, pero te faltó explicar cómo se relacionan datos con distinto nivel de detalle o cómo evitas que la meta se muestre mal.", "malo": "No te desanimes: repasa el concepto de granularidad en el modelo estrella y cómo combinar hechos con distinto nivel de detalle."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes diseñar en Power BI un modelo para analizar ventas diarias por producto y tienda, y compararlas con metas que vienen por mes y por tienda. ¿Cómo diseñas el modelo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Las construyo sobre una tabla de calendario completa y medidas base reutilizables. 1) Verifico que exista una tabla Calendario continua, marcada como tabla de fechas, con columnas de año y mes fiscal. 2) Creo la medida base Ventas = SUM(Ventas[Monto]). 3) Para el acumulado uso TOTALYTD o DATESYTD, indicando el 30 de junio como fecha de cierre del año fiscal. 4) Para el año anterior uso CALCULATE con SAMEPERIODLASTYEAR sobre la columna de fecha del calendario. 5) Calculo la variación con DIVIDE, que evita errores por división por cero. 6) Para no mostrar meses futuros, devuelvo BLANK cuando el período del contexto es posterior a la última fecha con ventas, por ejemplo con una columna del calendario que marque las fechas pasadas.',
    '{"metodo": "TECNICA", "criterios": ["Parte de una tabla de calendario completa con año fiscal", "Usa una medida base reutilizable", "Calcula el acumulado fiscal con fecha de cierre", "Calcula el año anterior con una función de inteligencia de tiempo", "Usa DIVIDE y controla los meses futuros"], "palabras_clave": ["TOTALYTD", "SAMEPERIODLASTYEAR", "año fiscal", "DIVIDE", "tabla de calendario"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Primero aseguro un calendario marcado como tabla de fechas, con año fiscal de julio a junio. Luego defino una medida Ventas y sobre ella el acumulado con DATESYTD indicando cierre al 30 de junio, el año anterior con DATEADD de menos un año y la variación con DIVIDE. Para los meses futuros devuelvo BLANK si el período es posterior a la última venta.", "Construyo medidas en capas: una medida base, una de acumulado fiscal con TOTALYTD y fecha de término el 30 de junio, una del año anterior con SAMEPERIODLASTYEAR y una de variación porcentual con DIVIDE. Agrego una columna en el calendario que indique si la fecha ya pasó, para no mostrar valores en meses sin datos."], "sinonimos": [["año fiscal", "ejercicio fiscal"], ["acumulado del año", "YTD", "año a la fecha"], ["año anterior", "año pasado", "período anterior"]], "feedback_generico": {"correcto": "¡Muy bien! Construiste las medidas de tiempo de forma ordenada y cuidaste detalles como el año fiscal y los meses sin datos.", "medio": "Vas bien, pero a tu propuesta le falta resolver el año fiscal, la comparación con el año anterior o los meses futuros.", "malo": "No te desanimes: repasa las funciones de inteligencia de tiempo de DAX y qué necesita la tabla de calendario para usarlas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden medidas de ventas acumuladas del año fiscal, que empieza en julio, y de variación porcentual contra el año anterior, sin mostrar valores en meses que aún no ocurren. ¿Cómo las construyes en DAX?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo resuelvo creando una única fuente de verdad y reglas de gobierno para que la métrica se defina una sola vez. 1) Reúno a las áreas para acordar la definición oficial de ventas netas y la registro en un diccionario de métricas. 2) Construyo un modelo semántico compartido con esa medida y lo publico en un área de trabajo controlada. 3) Lo marco como certificado y pido que los informes nuevos se conecten a él en vez de importar datos propios. 4) Separo desarrollo, prueba y producción en áreas de trabajo distintas, usando canalizaciones de implementación. 5) Migro los informes antiguos por prioridad y retiro los duplicados. 6) Defino responsables del modelo y un flujo para solicitar cambios.',
    '{"metodo": "TECNICA", "criterios": ["Acuerda una definición oficial de la métrica con el negocio", "Propone un modelo semántico compartido como fuente única", "Usa la certificación del modelo", "Separa entornos de desarrollo, prueba y producción", "Plantea la migración y el retiro de informes duplicados"], "palabras_clave": ["fuente única", "modelo semántico", "certificado", "diccionario de métricas", "gobierno de datos", "canalizaciones"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["El problema es de gobierno, no de un informe puntual. Definiría con finanzas y el área comercial una única fórmula de ventas netas, la implementaría en un modelo semántico central certificado y haría que los informes se conecten a él en vez de calcular por su cuenta. Después migraría los informes existentes y controlaría los cambios con entornos de desarrollo, prueba y producción.", "Crearía una sola versión de la verdad: un diccionario de métricas aprobado, un modelo compartido con las medidas oficiales y reportes livianos conectados a ese modelo. Además asignaría dueños del modelo, publicaría con canalizaciones de implementación y retiraría los informes con cálculos propios."], "sinonimos": [["modelo semántico", "conjunto de datos", "dataset"], ["canalizaciones de implementación", "deployment pipelines"], ["fuente única", "fuente de verdad", "única versión de la verdad"]], "feedback_generico": {"correcto": "¡Excelente! Abordaste el problema como un tema de gobierno y propusiste una solución técnica y organizacional sostenible.", "medio": "Vas bien, pero te faltó cubrir cómo aseguras que la métrica se defina una sola vez o cómo controlas los cambios a futuro.", "malo": "No te desanimes: repasa conceptos de gobierno de BI, como modelos semánticos compartidos, certificación y entornos de publicación."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'En tu empresa hay decenas de informes de Power BI y cada uno calcula las ventas netas de forma distinta, con cifras que no cuadran. Como líder técnico de BI, ¿cómo lo resuelves?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Power BI y visualización de datos'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- ── CLOUD ───────────────────────────────────────────────────────────────────

-- Azure

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El nivel Archive tiene el menor costo de almacenamiento, pero sus blobs quedan sin conexión y deben rehidratarse a un nivel en línea antes de leerlos, lo que puede tardar horas. Por eso calza con respaldos de largo plazo que casi nunca se consultan. Hot y Cool cuestan más por GB almacenado, aunque permiten leer los datos de inmediato.',
    '{"metodo": "TECNICA", "criterios": ["Identifica Archive como el nivel de menor costo de almacenamiento", "Explica que los datos en Archive deben rehidratarse antes de leerse", "Relaciona la baja frecuencia de acceso con el nivel elegido", "Distingue el costo de almacenar del costo y la demora de acceder"], "palabras_clave": ["Archive", "rehidratación", "costo de almacenamiento", "nivel de acceso", "largo plazo"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Conviene Archive: es el nivel más barato para guardar datos, a cambio de que leerlos requiere rehidratarlos primero y eso toma horas. Como los respaldos casi no se consultan, esa espera es aceptable.", "Para datos que se guardan años y se leen muy rara vez, el nivel Archive minimiza el costo por GB. La contrapartida es que el acceso no es inmediato, porque el blob debe pasar antes a un nivel en línea como Hot o Cool."], "sinonimos": [["nivel de acceso", "access tier", "capa de acceso"], ["rehidratación", "rehydration"], ["respaldo", "backup", "copia de seguridad"]], "feedback_generico": {"correcto": "¡Muy bien! Relacionaste la baja frecuencia de acceso y la tolerancia a la espera con el nivel de menor costo de almacenamiento.", "medio": "Vas bien, pero revisa la diferencia entre el costo de guardar un dato y el costo o la demora de leerlo en cada nivel de acceso.", "malo": "No te preocupes, este tema se aprende rápido. Repasa los niveles de acceso de Blob Storage y qué compromiso hace cada uno entre precio y disponibilidad inmediata."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Una empresa debe conservar respaldos de documentos por varios años en Azure Blob Storage. Casi nunca se consultan y, si se necesitan, se puede esperar varias horas. ¿Qué nivel de acceso conviene para minimizar el costo de almacenamiento?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Hot, porque ofrece el menor costo de almacenamiento por GB.', FALSE,
     'Hot tiene el costo de almacenamiento más alto y el acceso más barato; está pensado para datos que se leen seguido.', 1),
    ('Archive, porque es el más barato y tolera una lectura lenta.', TRUE,
     'Correcto: Archive minimiza el costo por GB y los datos se rehidratan cuando se necesitan, lo que puede tomar horas.', 2),
    ('Cool, porque es el único nivel pensado para guardar años.', FALSE,
     'Cool sirve para datos de acceso poco frecuente, pero no es el más barato para guardar a largo plazo; Archive está diseñado justamente para eso.', 3),
    ('Premium, porque los respaldos necesitan la menor latencia.', FALSE,
     'Premium es un nivel de rendimiento de baja latencia y alto costo, inadecuado para datos que casi no se leen.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Una empresa debe conservar respaldos de documentos por varios años en Azure Blob Storage. Casi nunca se consultan y, si se necesitan, se puede esperar varias horas. ¿Qué nivel de acceso conviene para minimizar el costo de almacenamiento?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un grupo de recursos es un contenedor lógico dentro de una suscripción que agrupa recursos relacionados para administrarlos juntos: permisos, etiquetas, costos y ciclo de vida. Cada recurso pertenece a un único grupo, aunque puede estar en una región distinta a la del grupo. Al eliminar el grupo se eliminan todos sus recursos.',
    '{"metodo": "TECNICA", "criterios": ["Define el grupo de recursos como un contenedor lógico", "Indica que cada recurso pertenece a un solo grupo de recursos", "Ubica el grupo de recursos dentro de una suscripción", "Sabe que los recursos de un grupo pueden estar en distintas regiones"], "palabras_clave": ["grupo de recursos", "contenedor lógico", "suscripción", "ciclo de vida", "permisos"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Es una agrupación lógica de recursos relacionados, por ejemplo todo lo de una aplicación en un entorno, que vive dentro de una suscripción. Sirve para asignar permisos, revisar costos y borrar todo junto cuando ya no se usa.", "Un resource group organiza recursos que comparten ciclo de vida. Un recurso solo puede estar en un grupo a la vez y el grupo no obliga a que sus recursos estén en la misma región."], "sinonimos": [["grupo de recursos", "resource group"], ["suscripción", "subscription"], ["etiqueta", "tag"]], "feedback_generico": {"correcto": "¡Excelente! Tienes clara la función del grupo de recursos y cómo se relaciona con la suscripción y con la ubicación de los recursos.", "medio": "Bien encaminado. Te falta precisar a cuántos grupos puede pertenecer un recurso y si el grupo restringe la región de sus recursos.", "malo": "Tranquilo, es un concepto base. Repasa la jerarquía de Azure: grupos de administración, suscripciones, grupos de recursos y recursos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En Azure, ¿qué afirmación describe correctamente a un grupo de recursos?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Es una red virtual que conecta entre sí todos los recursos que contiene.', FALSE,
     'Eso describe una VNet. El grupo de recursos es una agrupación administrativa y no establece conectividad de red.', 1),
    ('Es una cuenta de facturación que puede abarcar varias suscripciones.', FALSE,
     'Ocurre al revés: el grupo de recursos vive dentro de una sola suscripción y no es una cuenta de facturación.', 2),
    ('Es un contenedor lógico en el que cada recurso pertenece a un solo grupo.', TRUE,
     'Correcto: agrupa recursos para administrarlos juntos y un recurso no puede estar en dos grupos a la vez.', 3),
    ('Es un límite físico que obliga a crear todos sus recursos en su región.', FALSE,
     'La ubicación del grupo solo indica dónde se guardan sus metadatos; sus recursos pueden estar en otras regiones.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En Azure, ¿qué afirmación describe correctamente a un grupo de recursos?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En PaaS el proveedor administra la infraestructura, el sistema operativo y el runtime, y tú solo despliegas y configuras tu aplicación. Azure App Service es un ejemplo típico de PaaS. En IaaS, como una máquina virtual, tú mantienes el sistema operativo, y en SaaS consumes un software ya terminado.',
    '{"metodo": "TECNICA", "criterios": ["Identifica App Service como un servicio PaaS", "Explica qué administra el proveedor en PaaS", "Distingue IaaS, PaaS y SaaS con ejemplos", "Reconoce que en una máquina virtual el parchado del sistema operativo es responsabilidad del cliente"], "palabras_clave": ["PaaS", "App Service", "IaaS", "SaaS", "sistema operativo"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["La opción correcta es App Service, porque es plataforma como servicio: Azure se ocupa de los servidores, del sistema operativo y de sus parches, y tú te concentras en el código.", "Lo que se describe es PaaS. Con App Service subes tu aplicación y la plataforma gestiona el entorno, a diferencia de una máquina virtual, donde el sistema operativo queda a tu cargo."], "sinonimos": [["PaaS", "plataforma como servicio"], ["IaaS", "infraestructura como servicio"], ["SaaS", "software como servicio"], ["máquina virtual", "VM"]], "feedback_generico": {"correcto": "¡Muy bien! Distinguiste qué parte administra el proveedor y qué parte administras tú en cada modelo de servicio.", "medio": "Vas bien, pero revisa quién se encarga del sistema operativo y del runtime en cada modelo de servicio.", "malo": "No pasa nada. Repasa el modelo de responsabilidad compartida y compara IaaS, PaaS y SaaS con un ejemplo de cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Quieres publicar una API web sin administrar el sistema operativo ni aplicar parches al servidor, solo desplegando tu código. ¿Qué opción corresponde a ese modelo de servicio?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Azure App Service, un servicio PaaS que administra la plataforma por ti.', TRUE,
     'Correcto: App Service es PaaS; Azure gestiona servidores, sistema operativo y runtime, y tú despliegas el código.', 1),
    ('Azure Virtual Machines, un servicio PaaS que parcha el sistema operativo solo.', FALSE,
     'Las máquinas virtuales son IaaS: el sistema operativo y sus parches quedan bajo tu responsabilidad.', 2),
    ('Microsoft 365, un servicio IaaS donde despliegas el código de tu API.', FALSE,
     'Microsoft 365 es SaaS: se consume como software terminado y no sirve para hospedar tu propia API.', 3),
    ('Azure Virtual Network, un servicio SaaS pensado para hospedar APIs.', FALSE,
     'Una VNet es un componente de red para aislar y conectar recursos; no ejecuta aplicaciones ni es SaaS.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Quieres publicar una API web sin administrar el sistema operativo ni aplicar parches al servidor, solo desplegando tu código. ¿Qué opción corresponde a ese modelo de servicio?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Una región es un área geográfica con uno o más datacenters, y las zonas de disponibilidad son ubicaciones físicamente separadas dentro de una misma región. 1) La región define dónde viven los datos y la latencia hacia los usuarios. 2) Cada zona tiene energía, refrigeración y red independientes, conectadas entre sí con baja latencia. 3) Verifico que la región elegida ofrezca zonas, porque no todas las tienen. 4) Despliego instancias de la aplicación en al menos dos zonas, detrás de un balanceador con redundancia de zona. 5) Aclaro que las zonas no protegen ante la caída de toda la región; para eso se necesita una segunda región.',
    '{"metodo": "TECNICA", "criterios": ["Define región como un área geográfica con datacenters", "Define zona de disponibilidad como una ubicación aislada dentro de una región", "Propone desplegar en varias zonas para tolerar la caída de un datacenter", "Menciona servicios con redundancia de zona", "Reconoce que las zonas no cubren la caída de una región completa"], "palabras_clave": ["región", "zona de disponibilidad", "datacenter", "redundancia de zona", "alta disponibilidad"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["La región es la ubicación geográfica donde corre el servicio y la zona de disponibilidad es un datacenter o grupo de datacenters independiente dentro de esa región. Para resistir la falla de uno, reparto las instancias en varias zonas detrás de un balanceador. Si quiero sobrevivir a la caída de la región entera, debo replicar en otra región.", "Una región agrupa datacenters en un área geográfica; las zonas son subdivisiones aisladas con infraestructura propia. Uso despliegues con redundancia de zona para que, si una zona cae, las otras sigan atendiendo el tráfico."], "sinonimos": [["zona de disponibilidad", "availability zone", "AZ"], ["datacenter", "centro de datos"], ["alta disponibilidad", "HA"]], "feedback_generico": {"correcto": "¡Muy buena respuesta! Diferenciaste bien ambos conceptos y propusiste cómo usarlos para tolerar fallas, incluyendo su límite.", "medio": "Vas bien con las definiciones. Te faltó explicar cómo repartirías la aplicación para tolerar una falla o hasta dónde llega esa protección.", "malo": "Tranquilo, es un tema base de cloud. Repasa cómo se organiza la infraestructura global de Azure y qué tipo de falla cubre cada nivel."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica la diferencia entre una región y una zona de disponibilidad en Azure, y cómo las usarías para que una aplicación web siga funcionando si falla un datacenter.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El riesgo es que cualquiera con acceso al repositorio ve la credencial, y queda en el historial aunque se borre del código. 1) Cambio de inmediato la contraseña expuesta, porque debe considerarse comprometida. 2) Guardo la cadena de conexión como secreto en Azure Key Vault. 3) Configuro la app para leer el secreto desde Key Vault en tiempo de ejecución, sin copiarlo al código. 4) Doy acceso al Key Vault solo a la identidad de la app, con permiso de lectura de secretos. 5) Habilito los registros de diagnóstico del Key Vault para auditar quién accede a los secretos.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el riesgo de exponer credenciales en el código y en el historial", "Propone rotar la credencial expuesta", "Guarda el secreto en Key Vault y lo lee en tiempo de ejecución", "Aplica mínimo privilegio en el acceso al Key Vault", "Menciona la auditoría del acceso a secretos"], "palabras_clave": ["Key Vault", "secreto", "rotar", "mínimo privilegio", "cadena de conexión"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["El problema es que la credencial queda visible para todo el que lea el código y permanece en el historial de Git. Primero cambiaría la contraseña, luego la guardaría como secreto en Key Vault y haría que la aplicación la obtenga al ejecutarse, dando acceso solo a la identidad de esa app.", "Una credencial en el código es una filtración esperando ocurrir. La solución es centralizarla en Key Vault, controlar con permisos quién puede leerla, registrar los accesos y reemplazar la contraseña que ya quedó expuesta."], "sinonimos": [["secreto", "credencial"], ["cadena de conexión", "connection string"], ["rotar", "cambiar", "renovar"]], "feedback_generico": {"correcto": "¡Excelente! Reconociste el riesgo, actuaste sobre la credencial expuesta y propusiste una gestión segura del secreto.", "medio": "Buen avance. Revisa qué harías con la credencial que ya quedó expuesta y quién debería poder leer el secreto.", "malo": "No te desanimes. Repasa por qué los secretos no van en el código y qué servicio de Azure se usa para guardarlos de forma segura."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Un compañero dejó la cadena de conexión de la base de datos escrita en el código de una app que se despliega en Azure. ¿Qué riesgo ves y cómo lo resolverías usando Azure Key Vault?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Microsoft Entra ID es el servicio de identidad en la nube de Microsoft: administra usuarios, grupos y aplicaciones, y gestiona el inicio de sesión. 1) La autenticación responde quién eres: Entra ID verifica la identidad con contraseña, MFA u otro método. 2) La autorización responde qué puedes hacer: en los recursos de Azure la controla RBAC con roles asignados a esa identidad. 3) Entra ID también ofrece inicio de sesión único para varias aplicaciones. 4) Recomiendo exigir MFA y políticas de acceso condicional para reducir el riesgo de cuentas robadas. 5) Asigno permisos a grupos y no a personas sueltas, para administrarlos mejor.',
    '{"metodo": "TECNICA", "criterios": ["Define Entra ID como servicio de identidad y acceso", "Explica que la autenticación verifica quién es el usuario", "Explica que la autorización define qué puede hacer", "Relaciona la autorización en recursos de Azure con RBAC", "Menciona MFA o inicio de sesión único"], "palabras_clave": ["Entra ID", "autenticación", "autorización", "MFA", "RBAC", "identidad"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Entra ID es el directorio de identidades de Microsoft en la nube, donde viven los usuarios y aplicaciones de una organización. Autenticar es comprobar la identidad, por ejemplo con contraseña y MFA; autorizar es decidir a qué recursos accede, lo que en Azure se hace asignando roles.", "Es el servicio que gestiona cuentas, inicio de sesión y SSO. Primero valida quién se conecta y luego los roles de Azure determinan qué acciones puede realizar esa identidad sobre cada recurso."], "sinonimos": [["Microsoft Entra ID", "Entra ID", "Azure AD", "Azure Active Directory"], ["MFA", "autenticación multifactor", "doble factor"], ["inicio de sesión único", "SSO"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste qué es Entra ID y distinguiste con claridad verificar la identidad de otorgar permisos.", "medio": "Vas por buen camino. Te faltó precisar la diferencia entre comprobar quién es el usuario y decidir qué puede hacer, o qué controla cada parte.", "malo": "Tranquilo, es un concepto clave en cloud. Repasa qué hace un servicio de identidad y la diferencia entre autenticación y autorización."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es Microsoft Entra ID (antes Azure AD) y qué diferencia hay entre autenticación y autorización cuando un usuario accede a recursos de Azure?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Azure SQL Database hace respaldos automáticos que permiten una restauración a un punto en el tiempo (PITR) dentro del período de retención. Se restaura una copia a un momento previo al DELETE como base nueva y luego se copian las filas perdidas a la base original. La geo-replicación no sirve, porque replica también el borrado.',
    '{"metodo": "TECNICA", "criterios": ["Identifica la restauración a un punto en el tiempo como solución", "Sabe que los respaldos de Azure SQL Database son automáticos", "Explica que la restauración crea una base nueva desde donde copiar los datos", "Distingue respaldo de replicación"], "palabras_clave": ["restauración", "PITR", "respaldo automático", "base nueva", "retención"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Uso la restauración point-in-time: Azure SQL guarda respaldos automáticos y puedo crear una copia de la base tal como estaba justo antes del borrado. Desde esa copia recupero las filas y las reinserto en producción.", "Lo correcto es restaurar desde los respaldos automáticos a un instante anterior al error, en una base de datos separada, y luego mover los datos faltantes. Una réplica no ayuda porque ya tiene el DELETE aplicado."], "sinonimos": [["restauración a un punto en el tiempo", "point-in-time restore", "PITR"], ["respaldo", "backup", "copia de seguridad"], ["geo-replicación", "replicación geográfica"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste el mecanismo adecuado y entendiste por qué replicar no es lo mismo que respaldar.", "medio": "Bien. Revisa con más detalle cómo funciona la recuperación desde respaldos en Azure SQL y dónde queda la base restaurada.", "malo": "No te preocupes. Repasa las opciones de continuidad de negocio de Azure SQL Database y la diferencia entre respaldar y replicar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un desarrollador ejecutó por error un DELETE sin WHERE en una base de Azure SQL Database hace dos horas. ¿Cuál es la forma más directa de recuperar los datos?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Activar ahora la geo-replicación y leer los datos desde la réplica.', FALSE,
     'Una réplica nueva copia el estado actual de la base, que ya no tiene las filas borradas.', 1),
    ('Escalar la base a un nivel de servicio superior para recuperar filas.', FALSE,
     'Cambiar el nivel de servicio solo modifica capacidad y rendimiento; no recupera datos eliminados.', 2),
    ('Consultar Application Insights, que guarda una copia de cada fila.', FALSE,
     'Application Insights guarda telemetría de la aplicación, no el contenido de las tablas.', 3),
    ('Restaurar a un punto en el tiempo previo al error en una base nueva.', TRUE,
     'Correcto: los respaldos automáticos permiten crear una copia de la base en un instante anterior al DELETE y recuperar las filas desde ahí.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un desarrollador ejecutó por error un DELETE sin WHERE en una base de Azure SQL Database hace dos horas. ¿Cuál es la forma más directa de recuperar los datos?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Una identidad administrada le da a la Function una identidad en Microsoft Entra ID cuyas credenciales gestiona y rota Azure. Basta con asignarle un rol RBAC acotado, como Storage Blob Data Reader, sobre la cuenta o el contenedor. Así no hay claves que guardar, filtrar ni rotar a mano.',
    '{"metodo": "TECNICA", "criterios": ["Propone usar una identidad administrada", "Asigna un rol con el mínimo privilegio necesario", "Explica que Azure gestiona las credenciales de la identidad", "Descarta claves en la configuración y contenedores públicos"], "palabras_clave": ["identidad administrada", "rol", "RBAC", "sin credenciales", "mínimo privilegio"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Le activo a la Function una managed identity y, en la cuenta de almacenamiento, le doy solo el rol de lector de datos de blobs. La aplicación obtiene tokens automáticamente y nadie maneja secretos.", "Lo ideal es que la Function se autentique con su propia identidad administrada por Azure y que el acceso se controle con un rol de lectura. Esto evita guardar claves de acceso, que dan control total sobre la cuenta."], "sinonimos": [["identidad administrada", "managed identity"], ["clave de acceso", "access key"], ["rol", "role"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste una forma de autenticación sin secretos y con permisos acotados.", "medio": "Vas bien, pero revisa cómo puede autenticarse un servicio de Azure sin guardar credenciales y qué permiso mínimo le darías.", "malo": "Tranquilo, es un tema muy pedido. Repasa cómo se autentican los servicios de Azure entre sí sin contraseñas ni claves, y cómo se limitan sus permisos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Una Azure Function necesita leer blobs de una cuenta de almacenamiento y no quieres guardar claves ni contraseñas en su configuración. ¿Qué es lo más recomendado?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Copiar la clave de acceso de la cuenta en una variable de entorno.', FALSE,
     'Sigue siendo un secreto guardado en la configuración, y la clave de acceso da control total sobre la cuenta.', 1),
    ('Habilitar una identidad administrada y darle un rol de lectura de blobs.', TRUE,
     'Correcto: Azure gestiona las credenciales de la identidad y el rol limita el acceso a lo necesario.', 2),
    ('Hacer público el contenedor para que la Function lea sin autenticarse.', FALSE,
     'Expone los datos a cualquier persona en Internet; resuelve el acceso sacrificando la seguridad.', 3),
    ('Crear un usuario de Entra ID para la Function y poner su clave en el código.', FALSE,
     'Mueve el problema al código: sigue existiendo una contraseña que se puede filtrar y que hay que rotar a mano.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Una Azure Function necesita leer blobs de una cuenta de almacenamiento y no quieres guardar claves ni contraseñas en su configuración. ¿Qué es lo más recomendado?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Las ranuras de implementación (deployment slots) permiten desplegar la versión nueva en una ranura como staging, probarla y luego hacer swap con producción. El intercambio precalienta la app antes de cambiar el tráfico, lo que evita el tiempo de inactividad. Si algo falla, se vuelve a intercambiar para regresar a la versión anterior.',
    '{"metodo": "TECNICA", "criterios": ["Identifica las ranuras de implementación como solución", "Explica el intercambio entre staging y producción", "Menciona que el swap evita tiempo de inactividad", "Explica que la vuelta atrás se hace con otro intercambio"], "palabras_clave": ["ranuras de implementación", "deployment slots", "swap", "staging", "rollback"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Uso deployment slots: subo la versión a la ranura de staging, la valido con su propia URL y hago swap con producción. Si aparece un problema, repito el swap y vuelvo a la versión anterior en poco tiempo.", "La característica indicada son las ranuras de App Service. Permiten tener producción y staging en paralelo e intercambiarlas cuando la nueva versión está lista y caliente, sin cortar el servicio."], "sinonimos": [["ranuras de implementación", "deployment slots", "slots"], ["intercambio", "swap"], ["volver atrás", "rollback", "revertir"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste la característica que permite validar y publicar sin cortes, con una vuelta atrás sencilla.", "medio": "Buen intento. Revisa qué ofrece App Service para tener dos versiones en paralelo y cambiar entre ellas sin cortar el servicio.", "malo": "No pasa nada. Repasa las estrategias de despliegue sin tiempo de inactividad en App Service, como staging y producción en paralelo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu API corre en Azure App Service y quieres publicar una nueva versión, probarla en un entorno idéntico y pasarla a producción sin tiempo de inactividad, pudiendo volver atrás rápido. ¿Qué característica usas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Escalado horizontal, agregando instancias nuevas con la versión actualizada.', FALSE,
     'Escalar agrega capacidad a la misma aplicación; no ofrece un entorno separado para probar ni un mecanismo de vuelta atrás.', 1),
    ('Reiniciar la aplicación después de subir los archivos nuevos por FTP.', FALSE,
     'Reemplazar archivos y reiniciar provoca cortes y no deja una forma rápida de regresar a la versión anterior.', 2),
    ('Ranuras de implementación, intercambiando la de staging con producción.', TRUE,
     'Correcto: el swap publica sin tiempo de inactividad y otro swap permite volver a la versión anterior.', 3),
    ('Copias de seguridad de App Service, restaurando la versión nueva encima.', FALSE,
     'Las copias de seguridad sirven para recuperar un estado anterior, no para validar y publicar versiones sin cortes.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Tu API corre en Azure App Service y quieres publicar una nueva versión, probarla en un entorno idéntico y pasarla a producción sin tiempo de inactividad, pudiendo volver atrás rápido. ¿Qué característica usas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Elijo según cuánto control necesito y cuánta operación estoy dispuesto a asumir. 1) Para la API web uso App Service, porque es PaaS, escala con facilidad y no administro servidores. 2) Para el proceso disparado por archivos uso Azure Functions con un trigger de Blob Storage, y en un plan de consumo pago por ejecución. 3) Para los microservicios uso AKS, que da orquestación, escalado y despliegues controlados con Kubernetes. 4) Considero Azure Container Apps si el equipo no quiere operar Kubernetes directamente. 5) Dejo las máquinas virtuales para software que requiere control del sistema operativo o que no se puede contenerizar.',
    '{"metodo": "TECNICA", "criterios": ["Elige App Service para la API web y lo justifica", "Elige Azure Functions con un trigger para el proceso por eventos", "Elige AKS u otro orquestador de contenedores para los microservicios", "Explica cuándo conviene una máquina virtual", "Considera el costo operativo de cada opción"], "palabras_clave": ["App Service", "Azure Functions", "AKS", "contenedores", "máquina virtual", "trigger"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["La API va bien en App Service porque es administrada y escala sola; el procesamiento de archivos encaja en Functions, que reacciona al evento del blob y no consume recursos cuando no hay trabajo; los 15 microservicios necesitan un orquestador, así que usaría AKS o Container Apps. Las máquinas virtuales solo si algo exige control total del servidor.", "Busco el servicio más administrado que cumpla el requisito. App Service para la web, Functions para trabajos cortos disparados por eventos y Kubernetes en AKS cuando hay muchos contenedores que coordinar, aceptando que AKS requiere más conocimiento operativo."], "sinonimos": [["AKS", "Azure Kubernetes Service", "Kubernetes"], ["máquina virtual", "VM"], ["trigger", "desencadenador", "disparador"]], "feedback_generico": {"correcto": "¡Muy buena decisión técnica! Asociaste cada carga con un servicio adecuado y justificaste el equilibrio entre control y operación.", "medio": "Vas bien en varias cargas. Te faltó justificar alguna elección o comparar cuánto debes administrar en cada servicio.", "malo": "Tranquilo, es una decisión que se practica. Repasa para qué sirve cada servicio de cómputo de Azure y qué responsabilidad operativa implica cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Debes desplegar en Azure tres cargas: una API web tradicional, un proceso que se ejecuta cuando llega un archivo a Blob Storage y un sistema de 15 microservicios en contenedores. ¿Qué servicio de cómputo eliges para cada una y por qué?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo resuelvo con RBAC aplicando mínimo privilegio y el ámbito correcto. 1) Separo los entornos en suscripciones o grupos de recursos distintos para asignar permisos por ámbito. 2) Creo un grupo de Entra ID para los desarrolladores, en vez de asignar roles a cada persona. 3) Asigno al grupo el rol Colaborador (Contributor) en el ámbito de desarrollo. 4) Asigno al mismo grupo el rol Lector (Reader) en el ámbito de producción. 5) Reviso que nadie herede permisos mayores desde un ámbito superior, porque los roles se heredan hacia abajo. 6) Para cambios en producción uso un pipeline con su propia identidad o un acceso temporal con aprobación.',
    '{"metodo": "TECNICA", "criterios": ["Aplica el principio de mínimo privilegio", "Asigna roles a grupos y no a usuarios individuales", "Usa roles distintos según el ámbito de cada entorno", "Explica la herencia de permisos entre ámbitos", "Propone un mecanismo controlado para cambios en producción"], "palabras_clave": ["RBAC", "mínimo privilegio", "ámbito", "Contributor", "Reader", "herencia"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Usaría un grupo de seguridad con todos los desarrolladores y le daría Contributor sobre la suscripción o grupo de recursos de desarrollo, y Reader sobre producción. Como los permisos se heredan, revisaría que no tengan roles más amplios en la suscripción o en el grupo de administración. Los despliegues a producción los haría un pipeline.", "La clave es asignar el rol en el nivel adecuado: permisos de escritura solo donde se desarrolla y de lectura donde está producción. Lo administro con grupos para no gestionar persona por persona y dejo los cambios productivos a una identidad de automatización."], "sinonimos": [["RBAC", "control de acceso basado en roles"], ["Colaborador", "Contributor"], ["Lector", "Reader"], ["ámbito", "scope", "alcance"]], "feedback_generico": {"correcto": "¡Excelente! Aplicaste mínimo privilegio, usaste bien los ámbitos y pensaste en cómo se heredan los permisos.", "medio": "Buen planteamiento. Te faltó considerar la herencia de permisos, el uso de grupos o cómo se harían los cambios en producción.", "malo": "No te desanimes. Repasa los conceptos de rol, entidad de seguridad y ámbito en Azure RBAC, y el principio de mínimo privilegio."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'En tu equipo, los desarrolladores deben poder crear y modificar recursos en el entorno de desarrollo, pero en producción solo deben poder ver. ¿Cómo lo configurarías con Azure RBAC?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Parto de la telemetría para ubicar dónde se pierde el tiempo antes de cambiar nada. 1) Reviso en Application Insights las solicitudes fallidas y los tiempos de respuesta por operación. 2) Uso el mapa de aplicación y las dependencias para ver si la lentitud viene de la base de datos o de una API externa. 3) Abro las excepciones y las trazas de una solicitud con error para encontrar la causa. 4) Miro en Azure Monitor las métricas del plan, como CPU y memoria, para descartar falta de recursos. 5) Consulto los registros con KQL en Log Analytics para correlacionar eventos. 6) Configuro alertas sobre errores y latencia para enterarme antes que los usuarios.',
    '{"metodo": "TECNICA", "criterios": ["Usa Application Insights para revisar solicitudes fallidas y latencia", "Analiza dependencias como la base de datos o APIs externas", "Revisa excepciones y trazas para encontrar la causa raíz", "Considera las métricas de infraestructura del plan", "Propone alertas para detectar el problema a tiempo"], "palabras_clave": ["Application Insights", "Azure Monitor", "dependencias", "excepciones", "KQL", "alertas"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero miraría en Application Insights qué operaciones están fallando y cuáles son las más lentas. Después revisaría si las llamadas a la base de datos u otros servicios tardan demasiado, leería las excepciones asociadas a los errores 500 y compararía con la CPU y memoria del plan en Azure Monitor. Terminaría creando alertas.", "Usaría la telemetría para separar si el problema está en mi código, en una dependencia o en los recursos del servidor. Las trazas y excepciones de Application Insights muestran el error, las métricas de Azure Monitor muestran la carga, y con consultas KQL cruzo ambas fuentes."], "sinonimos": [["Application Insights", "App Insights"], ["error 500", "error interno del servidor"], ["alerta", "alarma"]], "feedback_generico": {"correcto": "¡Muy bien! Seguiste un diagnóstico ordenado usando telemetría de la aplicación y de la infraestructura, y pensaste en alertas.", "medio": "Vas bien. Te faltó revisar las dependencias externas, las excepciones o las métricas del plan para acotar mejor la causa.", "malo": "Tranquilo, el diagnóstico se aprende practicando. Repasa qué datos entrega Application Insights y en qué se complementa con Azure Monitor."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Los usuarios reportan que una aplicación en App Service responde lento y a veces entrega errores 500. ¿Cómo usarías Azure Monitor y Application Insights para diagnosticar el problema?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Para sobrevivir a la caída de una región se necesita una segunda región con la aplicación desplegada y los datos replicados, por ejemplo con geo-replicación de la base de datos. Un balanceador global como Azure Front Door o Traffic Manager detecta con sondas de salud que la región principal falló y envía el tráfico a la otra. Las zonas de disponibilidad solo protegen dentro de una misma región.',
    '{"metodo": "TECNICA", "criterios": ["Reconoce que se requiere una segunda región", "Incluye la replicación de datos entre regiones", "Usa un balanceador global con sondas de salud", "Distingue la protección zonal de la protección regional"], "palabras_clave": ["multirregión", "Front Door", "replicación", "sondas de salud", "recuperación ante desastres"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["El único diseño que tolera perder una región entera es tener la app en otra región, con los datos sincronizados y un enrutador global que cambie el tráfico cuando detecta la falla. Escalar o usar zonas dentro de la misma región no cubre ese escenario.", "Necesito un esquema activo-activo o activo-pasivo entre dos regiones. Azure Front Door o Traffic Manager redirigen a los usuarios a la región sana y la base de datos debe estar replicada para no perder información."], "sinonimos": [["recuperación ante desastres", "DR", "disaster recovery"], ["sonda de salud", "health probe"], ["multirregión", "varias regiones"]], "feedback_generico": {"correcto": "¡Excelente! Identificaste qué nivel de falla debe cubrirse y qué piezas necesita un diseño que lo tolere.", "medio": "Vas bien, pero revisa qué tipo de falla cubre cada opción y si protege ante la pérdida de una región completa.", "malo": "No te preocupes. Repasa qué tipo de falla cubre cada mecanismo de alta disponibilidad y de recuperación ante desastres en Azure."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Una aplicación web crítica en App Service debe seguir operando incluso si se cae una región completa de Azure. ¿Qué diseño cumple ese requisito?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Usar dos regiones con datos replicados y Azure Front Door según salud.', TRUE,
     'Correcto: la segunda región, la replicación de datos y el enrutamiento global por salud permiten seguir operando si cae una región.', 1),
    ('Repartir instancias en tres zonas de disponibilidad de la misma región.', FALSE,
     'Las zonas protegen ante la caída de un datacenter, pero no ante la caída de toda la región.', 2),
    ('Escalar a más instancias en la región actual con reglas automáticas.', FALSE,
     'Más instancias en la misma región aumentan la capacidad, pero caen junto con la región.', 3),
    ('Programar respaldos diarios en una cuenta de almacenamiento de la región.', FALSE,
     'Los respaldos en la misma región se pierden con ella y restaurar desde respaldos no mantiene la operación continua.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Una aplicación web crítica en App Service debe seguir operando incluso si se cae una región completa de Azure. ¿Qué diseño cumple ese requisito?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Bicep es un lenguaje declarativo específico de Azure que se transpila a plantillas ARM JSON. No requiere administrar un archivo de estado, porque el estado real se consulta directamente en Azure Resource Manager. A cambio, solo sirve para Azure, mientras que Terraform es multicloud pero exige gestionar su estado.',
    '{"metodo": "TECNICA", "criterios": ["Sabe que Bicep se transpila a plantillas ARM", "Explica que Bicep no usa un archivo de estado propio", "Reconoce que Bicep es declarativo", "Compara el alcance de Bicep con el enfoque multicloud de Terraform"], "palabras_clave": ["Bicep", "ARM", "archivo de estado", "declarativo", "multicloud"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La afirmación correcta es que Bicep genera plantillas ARM y no maneja un tfstate ni nada equivalente: el estado es lo que realmente existe en Azure. Esto simplifica la operación, aunque limita a Bicep a Azure.", "Bicep es una capa más legible sobre ARM. Como Azure Resource Manager es la fuente de verdad, el equipo no tiene que guardar ni bloquear un archivo de estado, algo que sí exige Terraform."], "sinonimos": [["ARM", "Azure Resource Manager"], ["archivo de estado", "state", "tfstate"], ["transpilar", "compilar"]], "feedback_generico": {"correcto": "¡Muy bien! Conoces cómo funciona Bicep por dentro y qué implica frente a una herramienta multicloud.", "medio": "Vas bien, pero revisa cómo maneja Bicep el estado de la infraestructura y cuál es su relación con ARM.", "malo": "Tranquilo. Repasa qué es Bicep, cómo se relaciona con las plantillas ARM y en qué se diferencia de Terraform."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu equipo evalúa Bicep frente a Terraform para una plataforma que vivirá solo en Azure. ¿Qué afirmación sobre Bicep es correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Permite desplegar recursos de AWS y Google Cloud con el mismo lenguaje.', FALSE,
     'Bicep es específico de Azure; para varias nubes con una sola herramienta se usa algo como Terraform.', 1),
    ('Guarda un archivo de estado propio que el equipo debe proteger y bloquear.', FALSE,
     'Bicep no usa archivo de estado; esa es una responsabilidad característica de Terraform.', 2),
    ('Es imperativo: ejecuta los pasos en el orden exacto en que se escriben.', FALSE,
     'Bicep es declarativo; Azure Resource Manager resuelve el orden a partir de las dependencias entre recursos.', 3),
    ('Se transpila a plantillas ARM JSON y no requiere un archivo de estado.', TRUE,
     'Correcto: Bicep genera ARM JSON y el estado se consulta en Azure, por lo que no hay un archivo de estado que administrar.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu equipo evalúa Bicep frente a Terraform para una plataforma que vivirá solo en Azure. ¿Qué afirmación sobre Bicep es correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Azure Policy evalúa las propiedades de los recursos, como la ubicación, y con el efecto Deny bloquea las creaciones que no cumplen. Asignarla en un grupo de administración hace que se herede a todas las suscripciones que están debajo. RBAC define quién puede actuar, pero no restringe dónde ni cómo se crean los recursos.',
    '{"metodo": "TECNICA", "criterios": ["Identifica Azure Policy como control de cumplimiento", "Usa el efecto Deny para impedir la creación", "Asigna la política en un grupo de administración para heredarla", "Distingue Azure Policy de RBAC"], "palabras_clave": ["Azure Policy", "Deny", "grupo de administración", "gobernanza", "herencia"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Usaría la política integrada de ubicaciones permitidas de Azure Policy con efecto Deny, asignada en el grupo de administración que agrupa esas suscripciones. Así ningún despliegue fuera de las regiones autorizadas se completa, sin importar el rol del usuario.", "El control correcto es de gobernanza y no de permisos: una Azure Policy que niegue ubicaciones no autorizadas, aplicada en lo alto de la jerarquía para que todas las suscripciones la hereden."], "sinonimos": [["grupo de administración", "management group"], ["Deny", "denegar"], ["gobernanza", "governance"]], "feedback_generico": {"correcto": "¡Excelente! Elegiste un control preventivo, a escala y en el nivel correcto de la jerarquía.", "medio": "Bien encaminado. Revisa la diferencia entre controlar quién puede actuar y controlar qué se permite crear, y dónde asignarías ese control.", "malo": "No te desanimes. Repasa las herramientas de gobernanza de Azure y en qué se diferencian de los permisos que otorga RBAC."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La organización tiene decenas de suscripciones y necesita impedir que cualquier equipo cree recursos fuera de las regiones autorizadas. ¿Cuál es la mejor solución?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Quitar el rol Owner a todos y dejarles solo el rol Contributor.', FALSE,
     'Contributor puede crear recursos en cualquier región; RBAC controla quién actúa, no dónde se crean los recursos.', 1),
    ('Asignar una Azure Policy con efecto Deny en un grupo de administración.', TRUE,
     'Correcto: la política bloquea las ubicaciones no permitidas y se hereda a todas las suscripciones del grupo.', 2),
    ('Crear alertas de presupuesto que avisen cuando aparezcan recursos.', FALSE,
     'Los presupuestos alertan sobre gasto; no impiden crear recursos ni miran su ubicación.', 3),
    ('Exigir una etiqueta de región en cada recurso con revisión manual.', FALSE,
     'Una revisión manual no es preventiva ni escala a decenas de suscripciones; el recurso ya estaría creado.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Azure' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'La organización tiene decenas de suscripciones y necesita impedir que cualquier equipo cree recursos fuera de las regiones autorizadas. ¿Cuál es la mejor solución?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Primero hago visible el gasto y después optimizo y dejo controles para que no se repita. 1) Analizo en Cost Management el costo por servicio, grupo de recursos y etiqueta para encontrar qué creció. 2) Exijo etiquetas como dueño, proyecto y entorno con Azure Policy, para atribuir cada costo a un responsable. 3) Elimino recursos huérfanos, como discos y direcciones IP sin uso, y ajusto el tamaño de las máquinas sobredimensionadas. 4) Apago los entornos de desarrollo fuera del horario laboral y muevo los datos poco usados a niveles de almacenamiento más baratos. 5) Evalúo reservas o planes de ahorro para las cargas estables. 6) Creo presupuestos con alertas por equipo y reviso los costos con ellos cada mes.',
    '{"metodo": "TECNICA", "criterios": ["Analiza el gasto por servicio, recurso y etiqueta con Cost Management", "Propone etiquetado obligatorio para atribuir costos", "Identifica optimizaciones como recursos huérfanos y ajuste de tamaño", "Considera reservas o planes de ahorro para cargas estables", "Establece presupuestos y alertas como control continuo"], "palabras_clave": ["Cost Management", "etiquetas", "presupuestos", "reservas", "recursos huérfanos", "alertas"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Empezaría por Cost Management para ver qué servicios y grupos de recursos explican el aumento. Luego atacaría lo evidente, como recursos olvidados, máquinas demasiado grandes o entornos de prueba encendidos de noche. Para cargas permanentes evaluaría reservas. Finalmente, dejaría etiquetas obligatorias y presupuestos con alertas para que cada equipo vea y responda por su gasto.", "Lo abordo con un enfoque FinOps: visibilidad, optimización y gobierno. Con los costos por etiqueta identifico a los responsables, aplico ajustes de tamaño y apagado programado, aprovecho descuentos por compromiso y dejo alertas de presupuesto para detectar desvíos temprano."], "sinonimos": [["presupuesto", "budget"], ["etiqueta", "tag"], ["ajuste de tamaño", "rightsizing", "redimensionamiento"], ["reserva", "instancia reservada"]], "feedback_generico": {"correcto": "¡Muy buena respuesta! Combinaste un diagnóstico con datos, optimizaciones concretas y controles para mantener el gasto bajo control.", "medio": "Buen avance. Te faltó explicar cómo atribuir los costos a responsables o qué controles dejarías para que el problema no se repita.", "malo": "Tranquilo, el control de costos es una habilidad muy valorada. Repasa Cost Management, el etiquetado, los presupuestos y las formas típicas de optimizar recursos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'La factura mensual de Azure de tu área subió mucho en los últimos meses y nadie sabe bien por qué. ¿Cómo investigas la causa y qué medidas propones para controlar el gasto?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño la red por capas y expongo a Internet solo lo indispensable. 1) Creo una VNet con una subred por capa: frontend, API y datos. 2) Publico la entrada con Application Gateway con WAF, como único punto expuesto a Internet. 3) Aplico un NSG en cada subred para permitir solo el tráfico necesario, por ejemplo que solo la subred de la API llegue a la de datos. 4) Conecto Azure SQL mediante un private endpoint y deshabilito su acceso público. 5) Evito IP públicas en las máquinas y uso Azure Bastion para la administración. 6) Habilito registros de flujo y diagnósticos para auditar el tráfico.',
    '{"metodo": "TECNICA", "criterios": ["Segmenta la VNet en subredes por capa", "Expone un solo punto de entrada protegido, como un WAF", "Usa NSG para restringir el tráfico entre subredes", "Conecta la base de datos por un private endpoint sin acceso público", "Considera la administración segura y los registros de tráfico"], "palabras_clave": ["VNet", "subred", "NSG", "private endpoint", "WAF", "Bastion"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Separaría cada capa en su propia subred dentro de una red virtual y controlaría el tráfico entre ellas con grupos de seguridad de red. Solo el frontend recibiría tráfico de Internet, detrás de un firewall de aplicaciones web. La base de datos quedaría accesible únicamente por un endpoint privado y la administración se haría sin abrir RDP ni SSH a Internet.", "Aplico defensa en profundidad: segmentación por subredes, reglas NSG de mínimo acceso, un Application Gateway con WAF como entrada, acceso privado a Azure SQL sin red pública y registros de red para detectar accesos anómalos."], "sinonimos": [["VNet", "red virtual"], ["NSG", "grupo de seguridad de red"], ["private endpoint", "punto de conexión privado"], ["WAF", "firewall de aplicaciones web"]], "feedback_generico": {"correcto": "¡Excelente diseño! Segmentaste la red, limitaste la exposición y protegiste la base de datos con acceso privado.", "medio": "Buen diseño base. Te faltó restringir el tráfico entre capas, proteger el acceso a la base de datos o cuidar la administración de los servidores.", "malo": "No te preocupes, el diseño de redes se construye por partes. Repasa VNet, subredes, NSG y los endpoints privados en Azure."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Debes diseñar la red en Azure para una aplicación de tres capas: frontend web, API y base de datos Azure SQL. ¿Cómo la diseñas para que sea segura?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Planifico por etapas y elijo la estrategia según el valor y el riesgo, no por moda. 1) Hago un inventario y una evaluación con Azure Migrate, revisando dependencias, rendimiento y licencias. 2) Comparo estrategias: rehost a máquinas virtuales para ir rápido, o replatform a App Service y Azure SQL para reducir la operación. 3) Para la base elijo Azure SQL Database o SQL Managed Instance según la compatibilidad que necesite, y migro con Azure Database Migration Service. 4) Preparo la base de la plataforma: red, identidades, gobierno y monitoreo. 5) Hago un piloto y ensayo el corte con un plan de vuelta atrás. 6) Migro en una ventana acordada, valido con los usuarios y luego optimizo costos.',
    '{"metodo": "TECNICA", "criterios": ["Propone una evaluación inicial de dependencias y rendimiento", "Compara estrategias como rehost y replatform con sus trade-offs", "Elige un destino adecuado para la base de datos según compatibilidad", "Incluye piloto, plan de corte y vuelta atrás", "Considera la base de la plataforma: red, identidad y gobierno"], "palabras_clave": ["Azure Migrate", "rehost", "replatform", "SQL Managed Instance", "vuelta atrás", "evaluación"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Empezaría evaluando la aplicación y sus dependencias para decidir entre llevarla tal cual a máquinas virtuales o adaptarla a servicios administrados. Si el plazo es corto, haría rehost primero y modernizaría después. La base de datos iría a SQL Managed Instance si usa funciones propias de SQL Server, y antes del corte haría un ensayo completo con plan de reversión.", "Lo trato como un proyecto con fases: descubrimiento, diseño de la zona de aterrizaje, migración piloto, migración final y optimización. En cada fase defino criterios de éxito y riesgos, y elijo cuánto cambiar la aplicación según el beneficio que aporta."], "sinonimos": [["rehost", "lift and shift", "rehospedaje"], ["vuelta atrás", "rollback", "reversión"], ["on-premise", "on-premises", "local"]], "feedback_generico": {"correcto": "¡Muy buena planificación! Evaluaste antes de actuar, justificaste la estrategia y redujiste el riesgo del corte.", "medio": "Buen enfoque general. Te faltó comparar estrategias de migración, justificar el destino de la base de datos o preparar el plan de vuelta atrás.", "malo": "Tranquilo, liderar una migración se aprende con práctica. Repasa las estrategias de migración a la nube y las herramientas de Azure para evaluar y migrar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden liderar la migración a Azure de una aplicación monolítica on-premise con base de datos SQL Server. ¿Cómo planificas la migración y qué estrategia eliges?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Azure'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

-- Terraform e Infraestructura como Código

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'En un enfoque declarativo describes cómo debe quedar la infraestructura, no los pasos para llegar ahí. Terraform compara ese estado deseado con el estado actual y calcula qué crear, modificar o eliminar, resolviendo el orden según las dependencias. En un enfoque imperativo, en cambio, tú escribes la secuencia exacta de comandos.',
    '{"metodo": "TECNICA", "criterios": ["Explica que se describe el estado final deseado", "Indica que Terraform calcula los cambios necesarios", "Contrasta con el enfoque imperativo", "Menciona que el orden lo determinan las dependencias y no el archivo"], "palabras_clave": ["declarativo", "estado deseado", "imperativo", "dependencias", "cambios necesarios"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Declarativo quiere decir que defino el resultado, por ejemplo que existan dos máquinas y una red, y Terraform decide qué acciones hacen falta para llegar a eso. No escribo instrucciones paso a paso como en un script.", "Significa que el código expresa qué infraestructura quiero tener. Terraform compara eso con lo que ya existe y genera las acciones, ordenándolas según las dependencias entre recursos."], "sinonimos": [["estado deseado", "estado final"], ["imperativo", "procedural"]], "feedback_generico": {"correcto": "¡Muy bien! Tienes claro qué describe el código en Terraform y quién calcula los pasos para llegar ahí.", "medio": "Vas bien, pero revisa la diferencia entre describir un resultado y escribir una secuencia de pasos.", "malo": "No te preocupes, es un concepto base de IaC. Repasa la diferencia entre los enfoques declarativo e imperativo con un ejemplo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué significa que Terraform use un enfoque declarativo?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Que escribes cada comando en orden y Terraform los ejecuta paso a paso.', FALSE,
     'Eso describe un enfoque imperativo, como un script de comandos.', 1),
    ('Que solo puede leer la infraestructura existente, sin crear ni modificar.', FALSE,
     'Terraform crea, modifica y elimina recursos; declarativo se refiere a cómo se expresa la configuración.', 2),
    ('Que describes el estado final deseado y Terraform calcula los cambios.', TRUE,
     'Correcto: defines qué quieres tener y Terraform determina qué acciones hacen falta para lograrlo.', 3),
    ('Que siempre crea los recursos en el orden en que aparecen en los archivos.', FALSE,
     'Terraform arma un grafo de dependencias; el orden de los bloques en los archivos no determina el orden de creación.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = '¿Qué significa que Terraform use un enfoque declarativo?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El comando terraform plan compara la configuración con el estado y con la infraestructura real, y muestra qué recursos se crearían, modificarían o destruirían, sin ejecutar cambios. El flujo típico es init para preparar el directorio, plan para revisar, apply para aplicar y destroy para eliminar. Revisar el plan evita sorpresas antes de tocar la infraestructura.',
    '{"metodo": "TECNICA", "criterios": ["Indica que plan muestra los cambios propuestos sin aplicarlos", "Distingue plan de init, apply y destroy", "Explica que compara la configuración con el estado actual", "Valora revisar el plan antes de aplicar"], "palabras_clave": ["terraform plan", "init", "apply", "destroy", "cambios propuestos"], "tiempo_esperado_seg": 45, "respuestas_alternativas": ["Plan es una vista previa: calcula la diferencia entre lo que dice el código y lo que existe, y la muestra para que la revises. Recién con apply se ejecutan esos cambios.", "Sirve para saber qué va a pasar antes de que pase. Lista los recursos que se agregarían, cambiarían o eliminarían, pero no modifica nada en la nube."], "sinonimos": [["plan", "vista previa"], ["aplicar", "apply"], ["destruir", "eliminar", "destroy"]], "feedback_generico": {"correcto": "¡Excelente! Conoces el rol de cada comando en el flujo de trabajo de Terraform.", "medio": "Vas bien. Repasa qué comando prepara el directorio, cuál solo muestra cambios y cuál los ejecuta.", "malo": "Tranquilo, se aprende rápido. Repasa el ciclo init, plan, apply y destroy, y qué hace cada paso."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'En el flujo habitual de trabajo con Terraform, ¿qué hace el comando terraform plan?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Muestra los cambios que haría en la infraestructura, sin aplicarlos.', TRUE,
     'Correcto: plan es una vista previa de lo que se crearía, modificaría o destruiría.', 1),
    ('Descarga los providers y prepara el directorio de trabajo para usarlo.', FALSE,
     'Eso lo hace terraform init, que se ejecuta antes de plan.', 2),
    ('Crea o modifica los recursos en la nube según la configuración escrita.', FALSE,
     'Ejecutar los cambios es tarea de terraform apply.', 3),
    ('Elimina todos los recursos administrados por la configuración actual.', FALSE,
     'Eliminar todos los recursos administrados es lo que hace terraform destroy.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'En el flujo habitual de trabajo con Terraform, ¿qué hace el comando terraform plan?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un bloque output expone valores de la configuración, como una IP o un ID, que se muestran al terminar el apply y quedan disponibles para el módulo que lo invoca. Los bloques variable, en cambio, reciben valores de entrada para parametrizar el código. Así, un módulo entrega datos por sus outputs y otro los recibe por sus variables.',
    '{"metodo": "TECNICA", "criterios": ["Identifica el bloque output para exponer valores", "Distingue variables de entrada de outputs", "Explica cómo se comparten valores entre módulos", "Reconoce que locals son valores internos del módulo"], "palabras_clave": ["output", "variable", "módulo", "IP pública", "locals"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Uso un output con la IP de la máquina. Terraform la muestra al final del apply y el módulo padre puede leerla para pasársela como variable a otro módulo.", "Los outputs son la salida de una configuración o módulo, como el valor de retorno de una función, y las variables son sus parámetros de entrada. Para compartir la IP, la declaro como output."], "sinonimos": [["output", "salida"], ["variable", "variable de entrada", "input"], ["módulo", "module"]], "feedback_generico": {"correcto": "¡Muy bien! Distingues cómo entra y cómo sale la información de una configuración de Terraform.", "medio": "Vas bien, pero revisa qué bloque recibe valores, cuál los expone y cuál solo se usa internamente.", "malo": "No pasa nada. Repasa los bloques variable, output y locals de Terraform y para qué sirve cada uno."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Después de crear una máquina virtual con un módulo de Terraform, necesitas mostrar su IP pública y que el módulo que lo llama pueda usarla. ¿Qué bloque usas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Un bloque variable, que recibe valores desde fuera de la configuración.', FALSE,
     'Las variables son entradas para parametrizar el código; no exponen resultados hacia afuera.', 1),
    ('Un bloque provider, que define la conexión con la nube que se usará.', FALSE,
     'El provider configura cómo hablar con la plataforma; no publica valores de los recursos.', 2),
    ('Un bloque locals, que publica valores para otros módulos y la consola.', FALSE,
     'Los locals son valores internos del módulo y no se exponen fuera de él.', 3),
    ('Un bloque output, que expone valores al módulo padre y tras el apply.', TRUE,
     'Correcto: los outputs muestran valores al terminar y el módulo padre puede leerlos y pasarlos a otros módulos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'junior'
  AND p.enunciado = 'Después de crear una máquina virtual con un módulo de Terraform, necesitas mostrar su IP pública y que el módulo que lo llama pueda usarla. ¿Qué bloque usas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'La Infraestructura como Código consiste en definir servidores, redes y demás recursos en archivos de código que una herramienta aplica de forma automática. 1) Repetibilidad: puedo crear entornos idénticos de desarrollo, pruebas y producción. 2) Control de versiones: los cambios quedan en Git, con historial y revisión por pull request. 3) Menos errores manuales: no dependo de recordar una secuencia de clics en la consola. 4) Documentación viva: el código muestra cómo está construida la infraestructura. 5) Velocidad y recuperación: puedo recrear un entorno completo rápidamente si algo falla.',
    '{"metodo": "TECNICA", "criterios": ["Define IaC como infraestructura descrita en archivos de código", "Menciona entornos repetibles y consistentes", "Menciona el control de versiones y la revisión de cambios", "Explica la reducción de errores manuales", "Menciona automatización o recuperación rápida"], "palabras_clave": ["infraestructura como código", "control de versiones", "repetible", "automatización", "consistencia"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["IaC significa que la infraestructura se describe en archivos, igual que una aplicación, y una herramienta como Terraform la crea. Así cada entorno sale igual, los cambios se revisan en Git antes de aplicarse y se evitan configuraciones hechas a mano que nadie recuerda.", "Es tratar la infraestructura como software: código versionado, revisable y reutilizable. Frente a la consola, gana en consistencia, trazabilidad y rapidez para crear o reconstruir entornos."], "sinonimos": [["IaC", "infraestructura como código"], ["consola", "portal"], ["control de versiones", "versionamiento"]], "feedback_generico": {"correcto": "¡Excelente! Explicaste bien qué es IaC y diste ventajas concretas frente al trabajo manual.", "medio": "Buen comienzo. Te faltó mencionar más beneficios, como el control de versiones o la creación de entornos idénticos.", "malo": "Tranquilo, es el punto de partida. Repasa qué significa definir infraestructura con código y qué problemas del trabajo manual resuelve."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es la Infraestructura como Código (IaC) y qué ventajas tiene frente a crear los recursos a mano desde la consola del proveedor cloud?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El provider es el plugin que sabe comunicarse con la API de una plataforma, y el resource es cada objeto de infraestructura que quiero administrar. 1) El provider, como azurerm o aws, se declara y se configura con opciones como la suscripción o la región. 2) El comando terraform init descarga los providers necesarios. 3) Cada resource tiene un tipo y un nombre, por ejemplo un azurerm_resource_group llamado principal. 4) El prefijo del tipo indica qué provider lo gestiona. 5) Al aplicar, Terraform usa el provider para crear, leer, actualizar o borrar ese recurso mediante la API.',
    '{"metodo": "TECNICA", "criterios": ["Define provider como plugin que se comunica con la API de una plataforma", "Define resource como un objeto de infraestructura administrado", "Explica que init descarga los providers", "Da un ejemplo concreto de tipo de recurso y su provider"], "palabras_clave": ["provider", "resource", "API", "terraform init", "plugin"], "tiempo_esperado_seg": 90, "respuestas_alternativas": ["Un provider es el componente que traduce el código de Terraform en llamadas a la API de Azure, AWS u otro servicio. Un resource es lo que se crea con él, como una red o una máquina virtual. Por ejemplo, el provider azurerm permite declarar un recurso azurerm_storage_account.", "El provider aporta los tipos de recursos y la conexión con la plataforma; los resources son las piezas concretas de infraestructura que describo en el código. Sin el provider, Terraform no sabría cómo crear esos recursos."], "sinonimos": [["provider", "proveedor"], ["resource", "recurso"]], "feedback_generico": {"correcto": "¡Muy bien! Explicaste claramente el rol de cada bloque y cómo se conectan con un ejemplo concreto.", "medio": "Vas bien. Te faltó explicar cómo se obtiene el provider o mostrar con un ejemplo cómo se relaciona con un recurso.", "malo": "No te preocupes. Repasa la estructura básica de una configuración de Terraform: bloque provider, bloques resource y el comando init."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', 'Explica qué son un provider y un resource en Terraform, y da un ejemplo de cómo se relacionan.')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'El archivo de estado es el registro que usa Terraform para relacionar cada recurso del código con el objeto real en la nube. 1) Guarda los identificadores y atributos de los recursos administrados. 2) Terraform lo usa para calcular el plan comparando código, estado e infraestructura. 3) Editarlo a mano puede corromperlo y hacer que Terraform recree o pierda el rastro de recursos. 4) Si hay que corregirlo, uso comandos como terraform state mv o terraform state rm. 5) No va en Git porque puede contener secretos en texto plano y provoca conflictos entre personas. 6) Lo guardo en un backend remoto con acceso restringido.',
    '{"metodo": "TECNICA", "criterios": ["Explica que el estado relaciona el código con los recursos reales", "Indica que Terraform lo usa para calcular el plan", "Advierte el riesgo de corromperlo al editarlo a mano", "Menciona que puede contener secretos y no debe ir a Git", "Propone comandos de estado o un backend remoto"], "palabras_clave": ["tfstate", "estado", "secretos", "backend remoto", "terraform state"], "tiempo_esperado_seg": 120, "respuestas_alternativas": ["Es el mapa entre lo que declaro y lo que existe realmente, con los IDs de cada recurso. Si lo modifico a mano puedo dejarlo inconsistente y Terraform tomaría malas decisiones. Tampoco lo subo a Git porque puede tener contraseñas y porque varias personas lo sobrescribirían; se guarda en un backend remoto.", "Terraform necesita el estado para saber qué administra. Debe protegerse porque puede incluir datos sensibles, y cualquier ajuste se hace con los subcomandos de terraform state, no editando el JSON."], "sinonimos": [["archivo de estado", "tfstate", "state"], ["backend remoto", "remote backend"], ["secreto", "dato sensible", "credencial"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes para qué sirve el estado y por qué hay que protegerlo y manejarlo con cuidado.", "medio": "Vas bien. Te faltó explicar qué riesgos tiene editarlo a mano o por qué no debe quedar en un repositorio.", "malo": "Tranquilo, el estado es lo que más cuesta al principio. Repasa qué guarda el archivo tfstate y cómo lo usa Terraform en cada plan."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('junior', '¿Qué es el archivo de estado (terraform.tfstate) y por qué no se recomienda editarlo a mano ni subirlo a un repositorio Git?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Un backend remoto con bloqueo de estado, como Azure Storage o S3 configurado con bloqueo, permite que solo una operación modifique el estado a la vez. Si otra persona intenta aplicar, Terraform espera o falla indicando que el estado está bloqueado. Además, el estado queda centralizado y no en el computador de cada persona.',
    '{"metodo": "TECNICA", "criterios": ["Propone un backend remoto compartido", "Explica el bloqueo del estado durante las operaciones", "Describe qué ocurre cuando otra operación encuentra el estado bloqueado", "Descarta Git como mecanismo para compartir el estado"], "palabras_clave": ["backend remoto", "bloqueo", "state locking", "estado compartido", "concurrencia"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["La solución es el state locking: el estado vive en un backend remoto que admite bloqueo, y mientras alguien ejecuta plan o apply nadie más puede escribirlo. Git no sirve porque no impide que dos personas trabajen sobre versiones distintas del estado.", "Configuro un backend compartido, por ejemplo una cuenta de almacenamiento en Azure, que toma un bloqueo al iniciar cada operación. Así las ejecuciones simultáneas se serializan y el estado no se corrompe."], "sinonimos": [["bloqueo", "lock", "locking"], ["backend remoto", "remote backend"], ["estado", "state", "tfstate"]], "feedback_generico": {"correcto": "¡Muy bien! Identificaste el mecanismo que protege el estado cuando varias personas trabajan a la vez.", "medio": "Vas bien, pero revisa dónde debería vivir el estado en un equipo y cómo se evita que dos operaciones lo modifiquen juntas.", "malo": "No te desanimes. Repasa dónde conviene guardar el estado de Terraform cuando trabaja un equipo y cómo se protegen las operaciones simultáneas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Dos integrantes del equipo ejecutan terraform apply al mismo tiempo sobre la misma infraestructura y el estado termina inconsistente. ¿Qué evita este problema?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Guardar el tfstate en Git y hacer pull justo antes de cada apply.', FALSE,
     'Git no bloquea el estado: dos personas pueden aplicar con copias distintas y además se exponen posibles secretos.', 1),
    ('Usar un backend remoto que bloquee el estado mientras dura la operación.', TRUE,
     'Correcto: con el bloqueo, solo una operación puede escribir el estado a la vez y la otra espera o falla.', 2),
    ('Ejecutar terraform fmt antes de aplicar para normalizar la configuración.', FALSE,
     'fmt solo da formato al código; no tiene relación con el acceso concurrente al estado.', 3),
    ('Separar los recursos en más archivos .tf dentro del mismo directorio.', FALSE,
     'Los archivos de un mismo directorio forman una sola configuración con un solo estado; dividirlos no evita la concurrencia.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Dos integrantes del equipo ejecutan terraform apply al mismo tiempo sobre la misma infraestructura y el estado termina inconsistente. ¿Qué evita este problema?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Con count cada instancia se identifica por su posición en la lista, así que al quitar un elemento del medio los índices se desplazan y Terraform intenta modificar o recrear las instancias siguientes. Con for_each sobre un set o un mapa, cada instancia queda asociada a una clave estable, como el nombre. Así, agregar o quitar un elemento solo afecta a ese recurso.',
    '{"metodo": "TECNICA", "criterios": ["Explica que count identifica las instancias por índice", "Describe el problema de desplazamiento al quitar un elemento", "Explica que for_each usa claves estables", "Relaciona la elección con evitar destrucciones accidentales"], "palabras_clave": ["for_each", "count", "índice", "clave estable", "recrear"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Si uso count y borro el segundo nombre de la lista, el tercero pasa a ocupar su índice y Terraform lo trata como un cambio, pudiendo destruir y recrear cuentas que no debían tocarse. Con for_each cada cuenta se identifica por su nombre y eso no ocurre.", "for_each asocia cada recurso a una clave del mapa o set, mientras count depende del orden de la lista. Por eso for_each es más seguro cuando la colección puede cambiar."], "sinonimos": [["recrear", "reemplazar", "destruir y crear"], ["clave", "key"], ["índice", "posición"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes cómo identifica Terraform cada instancia y qué riesgo trae eso al cambiar una lista.", "medio": "Vas bien, pero revisa qué pasa con las demás instancias cuando quitas un elemento del medio de la lista.", "malo": "Tranquilo, es un error muy común en la práctica. Repasa cómo funcionan count y for_each y cómo se identifica cada instancia en el estado."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Creas varias cuentas de almacenamiento a partir de una lista de nombres que irá cambiando. ¿Por qué suele preferirse for_each sobre count en este caso?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Porque for_each crea en paralelo y count siempre crea de uno en uno.', FALSE,
     'Ambos generan instancias que Terraform puede crear en paralelo según el grafo; la diferencia está en cómo se identifican.', 1),
    ('Porque count no acepta variables y obliga a escribir un número fijo.', FALSE,
     'count acepta expresiones, por ejemplo length(var.nombres); su problema es identificar instancias por índice.', 2),
    ('Porque for_each evita guardar esas cuentas en el archivo de estado.', FALSE,
     'Las instancias creadas con for_each también quedan registradas en el estado, cada una con su clave.', 3),
    ('Porque for_each identifica cada instancia por clave y no por índice.', TRUE,
     'Correcto: con claves estables, quitar un elemento no desplaza a los demás ni provoca recreaciones innecesarias.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Creas varias cuentas de almacenamiento a partir de una lista de nombres que irá cambiando. ¿Por qué suele preferirse for_each sobre count en este caso?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Para adoptar un recurso existente se escribe su bloque resource en el código y se lo vincula al estado con el comando terraform import o con un bloque import. Después se ejecuta terraform plan y se ajusta el código hasta que no muestre cambios. Un apply sin importar intentaría crear un recurso nuevo y fallaría por el nombre repetido o crearía un duplicado.',
    '{"metodo": "TECNICA", "criterios": ["Propone terraform import o un bloque import", "Indica que se debe escribir el bloque resource correspondiente", "Verifica con plan que no queden diferencias", "Explica por qué apply o refresh no adoptan el recurso"], "palabras_clave": ["terraform import", "bloque import", "estado", "plan sin cambios", "recurso existente"], "tiempo_esperado_seg": 60, "respuestas_alternativas": ["Declaro el recurso en mi configuración y uso import para asociarlo con su ID real en Azure. Luego reviso el plan y ajusto atributos hasta que Terraform no quiera modificar nada.", "Lo correcto es importar: Terraform no adopta solo lo que ya existe. Con el comando terraform import o un bloque import lo agrego al estado, y el plan me confirma que el código refleja la configuración real."], "sinonimos": [["importar", "import"], ["portal", "consola"], ["estado", "state"]], "feedback_generico": {"correcto": "¡Muy bien! Sabes cómo incorporar infraestructura existente a Terraform sin afectar el servicio.", "medio": "Vas bien, pero revisa cómo llega un recurso existente al estado y cómo confirmas que el código coincide con la realidad.", "malo": "No pasa nada. Repasa cómo Terraform relaciona el código con los recursos reales y qué herramienta existe para adoptar lo creado a mano."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Un grupo de recursos se creó a mano en el portal de Azure y ahora quieres administrarlo con Terraform sin destruirlo ni recrearlo. ¿Qué haces?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Escribo su bloque resource y lo incorporo al estado con terraform import.', TRUE,
     'Correcto: import vincula el recurso real con el bloque del código sin recrearlo; luego el plan confirma que no hay diferencias.', 1),
    ('Ejecuto terraform apply, que detecta el recurso y lo adopta solo.', FALSE,
     'apply no adopta recursos existentes: intentaría crear uno nuevo y fallaría por el nombre repetido o crearía un duplicado.', 2),
    ('Ejecuto terraform refresh, que agrega al estado los recursos que no conoce.', FALSE,
     'refresh solo actualiza los atributos de recursos que ya están en el estado; no incorpora recursos nuevos.', 3),
    ('Lo borro desde el portal y lo vuelvo a crear desde Terraform igual.', FALSE,
     'Recrearlo contradice el requisito, provoca interrupción y puede perder configuraciones o datos.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'semisenior'
  AND p.enunciado = 'Un grupo de recursos se creó a mano en el portal de Azure y ahora quieres administrarlo con Terraform sin destruirlo ni recrearlo. ¿Qué haces?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Extraigo el código repetido a un módulo con una interfaz clara y lo versiono. 1) Muevo los recursos de red a un directorio de módulo con sus archivos de recursos, variables y outputs. 2) Defino variables de entrada con tipos, descripciones, validaciones y valores por defecto razonables. 3) Expongo como outputs solo lo que otros necesitan, como los IDs de las subredes. 4) Publico el módulo en un repositorio o registro y lo referencio con una versión fija. 5) Documento un ejemplo de uso y lo pruebo en un entorno aislado. 6) Evito sobreparametrizar: si un módulo acepta cualquier cosa, deja de simplificar.',
    '{"metodo": "TECNICA", "criterios": ["Propone extraer el código a un módulo con variables y outputs", "Define variables con tipos, descripciones o validaciones", "Versiona el módulo y lo referencia con una versión fija", "Expone solo los outputs necesarios", "Advierte sobre módulos demasiado genéricos"], "palabras_clave": ["módulo", "variables", "outputs", "versión", "reutilizable", "registro"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Crearía un módulo de red con entradas como el rango de direcciones y las subredes, y salidas como los IDs que usan otros componentes. Lo guardaría en un repositorio propio con etiquetas de versión, para que cada proyecto elija cuándo actualizar. También agregaría documentación y validaciones de entrada.", "El objetivo es una pieza reutilizable con una interfaz pequeña y estable. Encapsulo la lógica de red, la parametrizo con variables tipadas, publico versiones semánticas y evito que el módulo intente cubrir todos los casos posibles."], "sinonimos": [["módulo", "module"], ["reutilizable", "reusable"], ["versión", "tag", "release"]], "feedback_generico": {"correcto": "¡Excelente! Propusiste un módulo con una interfaz clara, versionado y pensado para que otros lo usen sin problemas.", "medio": "Buen enfoque. Te faltó definir bien las entradas y salidas, versionar el módulo o considerar cuánto parametrizarlo.", "malo": "Tranquilo, modularizar se aprende con práctica. Repasa cómo se estructura un módulo de Terraform con variables, outputs y fuentes versionadas."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu equipo copia y pega el mismo código de Terraform para crear la red de cada proyecto. ¿Cómo lo convertirías en un módulo reutilizable y qué buenas prácticas aplicarías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Trato el drift como un desvío entre el código y la realidad que hay que entender antes de corregir. 1) Reviso el plan para ver exactamente qué atributo cambió. 2) Averiguo quién hizo el cambio y por qué, usando el registro de actividad de la nube. 3) Si el cambio era un error, aplico el código y Terraform restaura la configuración declarada. 4) Si el cambio era necesario, lo incorporo al código mediante un pull request y verifico que el plan quede sin diferencias. 5) Para prevenirlo, limito los permisos de escritura en producción y ejecuto planes programados que alerten sobre drift.',
    '{"metodo": "TECNICA", "criterios": ["Define drift como diferencia entre el código y la realidad", "Investiga el origen del cambio antes de actuar", "Decide entre revertir con apply o incorporar el cambio al código", "Propone prevención con permisos restringidos", "Propone detección periódica del drift"], "palabras_clave": ["drift", "terraform plan", "registro de actividad", "pull request", "permisos", "detección"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["Primero entiendo el cambio: qué se modificó, quién lo hizo y si resolvía algo urgente. Si no correspondía, dejo que Terraform lo revierta con apply; si era válido, lo paso al código para que vuelvan a coincidir. Después restrinjo los cambios manuales y programo un plan diario que avise cuando aparezca drift.", "El drift indica que alguien se saltó el flujo de IaC. Lo corrijo eligiendo una única fuente de verdad, que debe ser el código, y lo evito con permisos de solo lectura en el portal para producción y con detección automática en el pipeline."], "sinonimos": [["drift", "desviación", "deriva de configuración"], ["portal", "consola"], ["registro de actividad", "activity log"]], "feedback_generico": {"correcto": "¡Muy bien! Investigaste antes de corregir, decidiste qué hacer con el cambio y propusiste cómo evitar que se repita.", "medio": "Vas bien. Te faltó decidir qué hacer con el cambio según su origen o proponer cómo prevenir y detectar el drift.", "malo": "No te desanimes. Repasa qué es el drift en IaC, cómo lo muestra terraform plan y qué opciones tienes para resolverlo."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Al ejecutar terraform plan en producción aparecen cambios que nadie hizo en el código: alguien modificó a mano una regla de firewall desde el portal. ¿Cómo manejas este drift?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Evito que el secreto quede en el código y protejo el estado, porque ahí también puede terminar. 1) No escribo la contraseña en archivos .tf ni en archivos tfvars versionados. 2) La leo desde un gestor de secretos, como Azure Key Vault, con un data source, o la genero con el provider random. 3) Si llega por variable, la marco como sensitive y la paso como variable de entorno del pipeline. 4) Tengo claro que sensitive solo la oculta en la salida, pero el valor puede quedar en el estado en texto plano. 5) Por eso guardo el estado en un backend cifrado y con acceso restringido.',
    '{"metodo": "TECNICA", "criterios": ["Evita escribir secretos en el código o en archivos versionados", "Usa un gestor de secretos o genera el valor de forma segura", "Explica el alcance real de sensitive", "Reconoce que el estado puede guardar secretos en texto plano", "Protege el backend con cifrado y acceso restringido"], "palabras_clave": ["sensitive", "Key Vault", "estado", "variables de entorno", "cifrado"], "tiempo_esperado_seg": 150, "respuestas_alternativas": ["La contraseña no va en el repositorio: la obtengo de Key Vault o un servicio similar, o la recibo como variable de entorno en el pipeline, marcada como sensitive. Como Terraform igual puede guardarla en el estado, ese estado debe estar en un almacenamiento cifrado al que acceda muy poca gente.", "Separo el secreto del código usando un gestor de secretos y variables sensibles. Tengo claro que sensitive oculta el valor en pantalla pero no lo cifra en el tfstate, así que trato el estado como información confidencial."], "sinonimos": [["secreto", "credencial", "contraseña"], ["gestor de secretos", "bóveda de secretos"], ["estado", "tfstate", "state"]], "feedback_generico": {"correcto": "¡Excelente! Protegiste el secreto en el código y también consideraste dónde más puede quedar guardado.", "medio": "Buen avance. Revisa qué protege realmente la marca sensitive y qué pasa con el valor dentro del archivo de estado.", "malo": "Tranquilo, es un tema delicado y frecuente en entrevistas. Repasa cómo maneja Terraform los valores sensibles y por qué el estado debe protegerse."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('semisenior', 'Tu configuración de Terraform necesita la contraseña de administrador de una base de datos. ¿Cómo manejas ese secreto de forma segura?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Los workspaces de la CLI mantienen un estado separado por workspace usando el mismo código y el mismo backend. No ofrecen aislamiento de credenciales ni de permisos, así que alguien con acceso a desarrollo puede seleccionar producción por error. Por eso, para producción muchos equipos prefieren carpetas o configuraciones de backend separadas, con identidades distintas.',
    '{"metodo": "TECNICA", "criterios": ["Sabe que cada workspace tiene su propio estado", "Explica que comparten código y backend", "Reconoce que no aíslan credenciales ni permisos", "Propone alternativas como carpetas o backends separados para producción"], "palabras_clave": ["workspaces", "estado separado", "backend", "credenciales", "aislamiento"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Un workspace es solo otra instancia de estado para la misma configuración. Sirve para entornos parecidos, pero si producción requiere otras credenciales y controles de acceso, conviene separarla en su propio directorio o backend.", "Los workspaces separan el estado, no la seguridad. Como todos usan el mismo backend y la misma configuración, un error al seleccionar el workspace puede aplicar cambios en el entorno equivocado."], "sinonimos": [["workspace", "espacio de trabajo"], ["entorno", "ambiente"], ["estado", "state"]], "feedback_generico": {"correcto": "¡Excelente! Entiendes qué separan realmente los workspaces y cuáles son sus límites para entornos críticos.", "medio": "Vas bien, pero revisa qué comparten los workspaces entre sí y qué riesgos implica eso para producción.", "malo": "No te preocupes. Repasa cómo funcionan los workspaces de Terraform y las alternativas para separar entornos."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tu equipo quiere separar los entornos de desarrollo y producción usando workspaces de la CLI de Terraform. ¿Qué afirmación es correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Cada workspace usa por sí solo credenciales y un backend distintos.', FALSE,
     'Todos los workspaces de una configuración usan el mismo backend y las credenciales con que se ejecuta Terraform.', 1),
    ('Todos los workspaces comparten un mismo archivo de estado entre sí.', FALSE,
     'Cada workspace tiene su propio estado; lo que comparten es el código y la configuración del backend.', 2),
    ('Tienen estados separados, pero no aíslan credenciales ni permisos.', TRUE,
     'Correcto: separan el estado, pero no el control de acceso, por eso no bastan para aislar producción.', 3),
    ('Al crear un workspace se copian los recursos del workspace actual.', FALSE,
     'Un workspace nuevo parte con un estado vacío; no copia ni despliega los recursos de otro.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Tu equipo quiere separar los entornos de desarrollo y producción usando workspaces de la CLI de Terraform. ¿Qué afirmación es correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'El argumento prevent_destroy = true dentro de un bloque lifecycle hace que Terraform rechace con error cualquier plan que implique destruir ese recurso, incluido un reemplazo o un terraform destroy. Su límite es que, si alguien quita el bloque del recurso del código, la protección desaparece. Por eso conviene complementarlo con un bloqueo del recurso en la nube, como un resource lock de Azure.',
    '{"metodo": "TECNICA", "criterios": ["Identifica prevent_destroy como la protección adecuada", "Explica que el plan falla si implica destruir el recurso", "Reconoce el límite cuando se elimina el bloque del código", "Distingue prevent_destroy de ignore_changes y create_before_destroy"], "palabras_clave": ["prevent_destroy", "lifecycle", "reemplazo", "resource lock", "ignore_changes"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["Agrego prevent_destroy en el lifecycle de la base de datos. Así cualquier plan que intente borrarla o reemplazarla termina en error y obliga a revisar el cambio. Como defensa extra uso un bloqueo de eliminación a nivel de la nube.", "La opción es prevent_destroy, que actúa como un seguro dentro de Terraform. No es infalible si alguien elimina el recurso del código, así que lo combino con permisos restringidos y bloqueos del propio proveedor."], "sinonimos": [["bloqueo de recurso", "resource lock"], ["reemplazo", "recreación"], ["destruir", "eliminar", "borrar"]], "feedback_generico": {"correcto": "¡Muy bien! Elegiste la protección correcta y conoces su límite, lo que muestra criterio para producción.", "medio": "Vas bien, pero revisa la diferencia entre los argumentos de lifecycle y qué hace realmente cada uno.", "malo": "No te desanimes. Repasa el bloque lifecycle de Terraform y sus argumentos prevent_destroy, ignore_changes y create_before_destroy."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Quieres asegurarte de que ningún plan de Terraform pueda destruir por accidente la base de datos de producción, ni siquiera por un cambio que obligue a reemplazarla. ¿Qué usas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('lifecycle con ignore_changes = all, para que nunca se toque el recurso.', FALSE,
     'ignore_changes solo ignora diferencias en atributos; no impide un terraform destroy ni la eliminación si se quita el bloque.', 1),
    ('lifecycle con prevent_destroy = true, que hace fallar ese tipo de plan.', TRUE,
     'Correcto: Terraform rechaza cualquier plan que destruya o reemplace el recurso mientras el argumento esté en el código.', 2),
    ('Marcar sus atributos como sensitive para que el plan no los modifique.', FALSE,
     'sensitive solo oculta valores en la salida de la CLI; no protege el recurso de cambios ni de su destrucción.', 3),
    ('lifecycle con create_before_destroy = true, que impide eliminarlo.', FALSE,
     'create_before_destroy solo cambia el orden de un reemplazo: crea el nuevo antes de destruir el antiguo, pero igual lo destruye.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Quieres asegurarte de que ningún plan de Terraform pueda destruir por accidente la base de datos de producción, ni siquiera por un cambio que obligue a reemplazarla. ¿Qué usas?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'opcion_multiple', 'tecnica', v.nivel, v.enunciado,
    'Ansible se usa principalmente para gestión de configuración: ejecuta tareas sobre servidores sin agente, por SSH o WinRM, y no mantiene un archivo de estado de la infraestructura como Terraform. CloudFormation es propio de AWS y Pulumi permite usar lenguajes de programación como TypeScript o Python. Por eso es común combinar Terraform para aprovisionar y Ansible para configurar.',
    '{"metodo": "TECNICA", "criterios": ["Distingue aprovisionamiento de gestión de configuración", "Sabe que Ansible no mantiene un archivo de estado", "Reconoce que CloudFormation es específico de AWS", "Sabe que Pulumi usa lenguajes de programación de propósito general"], "palabras_clave": ["Ansible", "gestión de configuración", "aprovisionamiento", "CloudFormation", "Pulumi", "estado"], "tiempo_esperado_seg": 75, "respuestas_alternativas": ["La afirmación correcta es la de Ansible: es una herramienta de configuración sin agente que no lleva un registro de estado como el tfstate. Terraform tampoco usa agentes, CloudFormation solo cubre AWS y Pulumi trabaja con lenguajes como Python o Go.", "Terraform y Ansible resuelven problemas distintos: el primero crea y mantiene la infraestructura con un estado, y el segundo configura lo que corre dentro de los servidores sin guardar estado."], "sinonimos": [["gestión de configuración", "configuration management"], ["aprovisionamiento", "provisioning"], ["sin agente", "agentless"]], "feedback_generico": {"correcto": "¡Excelente! Conoces el propósito de cada herramienta y en qué se diferencia de Terraform.", "medio": "Vas bien, pero revisa el alcance de cada herramienta: qué nubes cubre, qué lenguajes usa y si mantiene estado.", "malo": "Tranquilo, comparar herramientas requiere práctica. Repasa la diferencia entre aprovisionar infraestructura y configurar servidores, y qué hace cada herramienta."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Al comparar Terraform con otras herramientas de automatización de infraestructura, ¿qué afirmación es correcta?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO opcion_pregunta (pregunta_id, texto_opcion, es_correcta, explicacion, orden)
SELECT p.pregunta_id, o.texto_opcion, o.es_correcta, o.explicacion, o.orden
FROM pregunta p
JOIN skill s ON s.skill_id = p.skill_id
CROSS JOIN (VALUES
    ('Pulumi solo acepta YAML y no permite usar lenguajes de programación.', FALSE,
     'Pulumi se caracteriza justamente por permitir lenguajes como TypeScript, Python, Go o C#.', 1),
    ('CloudFormation gestiona de forma nativa recursos de AWS, Azure y GCP.', FALSE,
     'CloudFormation es el servicio de IaC de AWS y está orientado a recursos de AWS.', 2),
    ('Terraform requiere instalar un agente en cada servidor que administra.', FALSE,
     'Terraform no usa agentes: trabaja llamando a las API de los proveedores mediante sus providers.', 3),
    ('Ansible se usa sobre todo para configurar servidores y no guarda estado.', TRUE,
     'Correcto: Ansible se enfoca en la gestión de configuración y no mantiene un archivo de estado como Terraform.', 4)
) AS o(texto_opcion, es_correcta, explicacion, orden)
WHERE s.nombre = 'Terraform e Infraestructura como Código' AND p.cargo_id IS NULL AND p.nivel_dificultad = 'senior'
  AND p.enunciado = 'Al comparar Terraform con otras herramientas de automatización de infraestructura, ¿qué afirmación es correcta?'
  AND p.tipo_pregunta = 'opcion_multiple'
  AND NOT EXISTS (SELECT 1 FROM opcion_pregunta x WHERE x.pregunta_id = p.pregunta_id);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Diseño un flujo donde todo cambio pasa por pull request y lo que se aplica es exactamente lo revisado. 1) Al abrir el pull request ejecuto terraform fmt -check, terraform validate, un linter como tflint y un análisis de seguridad como Checkov. 2) Genero el plan de cada entorno y lo publico en el pull request para que el equipo lo revise. 3) Exijo la aprobación de otra persona antes de fusionar. 4) Tras fusionar, aplico primero en desarrollo y luego en producción con una aprobación manual, usando el plan guardado con -out que se revisó. 5) El pipeline se autentica con una identidad federada, sin secretos de larga duración. 6) Solo el pipeline tiene permisos de escritura en producción.',
    '{"metodo": "TECNICA", "criterios": ["Valida formato, sintaxis y seguridad en cada pull request", "Publica el plan para revisarlo antes de aplicar", "Exige aprobación antes de aplicar en producción", "Aplica el mismo plan que se revisó", "Usa una identidad del pipeline sin secretos de larga duración y con mínimo privilegio"], "palabras_clave": ["pull request", "terraform plan", "aprobación", "validate", "pipeline", "plan guardado"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Cada cambio entra por un pull request donde el pipeline formatea, valida y escanea el código y deja el plan como comentario. Con la revisión aprobada se fusiona, y la aplicación en producción requiere una aprobación explícita y usa el plan guardado, para no aplicar algo distinto de lo revisado. Las credenciales del pipeline son federadas y nadie aplica desde su computador.", "Separaría CI y CD: en CI se revisa la calidad y se genera el plan; en CD se promueve por entornos con puertas de aprobación. El estado vive en un backend remoto con bloqueo y solo la identidad del pipeline puede modificar producción."], "sinonimos": [["pull request", "PR", "merge request"], ["fusionar", "merge"], ["identidad federada", "federación de identidad", "OIDC"]], "feedback_generico": {"correcto": "¡Excelente diseño! Incluiste validaciones automáticas, revisión del plan, aprobaciones y un manejo seguro de credenciales.", "medio": "Buen diseño base. Te faltó asegurar que se aplique lo mismo que se revisó, exigir aprobaciones o cuidar las credenciales del pipeline.", "malo": "No te desanimes. Repasa cómo se integra Terraform en un pipeline de CI/CD y por qué el plan debe revisarse antes de aplicar."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Te piden diseñar el pipeline de CI/CD para los cambios de Terraform de la empresa, de modo que nada llegue a producción sin revisión. ¿Cómo lo diseñas?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Divido el estado por componentes y entornos para reducir el radio de impacto y acelerar los planes. 1) Defino los límites según el ritmo de cambio y los dueños, por ejemplo red, datos y aplicaciones, separados por entorno. 2) Creo una configuración y un estado por componente en el backend remoto. 3) Muevo los recursos sin recrearlos: los saco del estado original con terraform state rm o un bloque removed que no destruya, y los importo en el nuevo. 4) Compruebo que cada plan quede sin cambios después de mover. 5) Comparto datos entre componentes con outputs leídos mediante terraform_remote_state o data sources. 6) Hago la migración por etapas, con un respaldo del estado antes de cada paso.',
    '{"metodo": "TECNICA", "criterios": ["Divide el estado por componentes y entornos", "Explica el beneficio en radio de impacto y velocidad", "Mueve recursos entre estados sin recrearlos", "Comparte datos entre estados con outputs o data sources", "Planifica una migración gradual con respaldo del estado"], "palabras_clave": ["radio de impacto", "estado", "terraform_remote_state", "import", "componentes", "respaldo"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Separaría el estado monolítico en varios estados más pequeños, por ejemplo red compartida, bases de datos y cada aplicación, y uno por entorno. Movería los recursos quitándolos del estado original sin destruirlos e importándolos en el nuevo, validando que el plan quede limpio. Los IDs que se necesitan entre componentes los leería desde outputs remotos.", "El objetivo es que un cambio en una aplicación no ponga en riesgo la red de toda la empresa. Diseño estados por dominio y dueño, migro de a poco con copias de seguridad del tfstate y conecto los componentes con data sources en vez de dependencias directas."], "sinonimos": [["radio de impacto", "blast radius"], ["estado", "state", "tfstate"], ["respaldo", "backup", "copia de seguridad"]], "feedback_generico": {"correcto": "¡Muy buena estrategia! Definiste límites razonables, migraste sin recrear recursos y resolviste cómo se comunican los componentes.", "medio": "Buen enfoque. Te faltó explicar cómo mover recursos sin recrearlos o cómo compartir datos entre los nuevos estados.", "malo": "Tranquilo, es un desafío típico de equipos grandes. Repasa el concepto de radio de impacto y los comandos de Terraform para manipular el estado."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Toda la infraestructura de la empresa está en un único estado de Terraform: el plan tarda mucho y un error puede afectar a todo. ¿Cómo lo reorganizarías?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

INSERT INTO pregunta (skill_id, cargo_id, tipo_pregunta, categoria_habilidad, nivel_dificultad,
    enunciado, respuesta_ideal, rubrica_evaluacion, estado)
SELECT s.skill_id, NULL, 'abierta_texto', 'tecnica', v.nivel, v.enunciado,
    'Lo trato como un cambio riesgoso que se hace de forma controlada y gradual. 1) Reviso la guía de actualización y las notas de versión del provider para identificar cambios incompatibles. 2) Verifico que cada configuración fije la versión con restricciones explícitas y que versione el archivo .terraform.lock.hcl. 3) Actualizo primero en desarrollo con terraform init -upgrade y ajusto el código. 4) Reviso cada plan buscando reemplazos o destrucciones inesperadas antes de aplicar. 5) Actualizo primero los módulos compartidos, publico una versión nueva y la promuevo por entornos. 6) Respaldo el estado antes de aplicar, porque un provider nuevo puede actualizar el esquema del estado y no siempre se puede volver atrás.',
    '{"metodo": "TECNICA", "criterios": ["Revisa la guía de actualización y los cambios incompatibles", "Fija versiones con restricciones y con el archivo de bloqueo", "Prueba primero en un entorno no productivo", "Revisa los planes buscando reemplazos o destrucciones", "Despliega de forma gradual y respalda el estado"], "palabras_clave": ["notas de versión", "restricción de versión", "lock file", "reemplazos", "despliegue gradual"], "tiempo_esperado_seg": 180, "respuestas_alternativas": ["Empezaría leyendo la guía de migración del provider y probando el cambio en una configuración de desarrollo representativa. Ajustaría el código hasta que el plan no muestre reemplazos inesperados y recién ahí avanzaría por entornos y equipos. Antes de cada apply respaldaría el estado, porque no siempre se puede volver a la versión anterior.", "Planifico la actualización como un proyecto: inventario de configuraciones y versiones, restricciones de versión explícitas, pruebas en entornos no productivos, revisión estricta de cada plan y despliegue escalonado partiendo por los módulos compartidos."], "sinonimos": [["lock file", ".terraform.lock.hcl", "archivo de bloqueo"], ["notas de versión", "changelog"], ["cambio incompatible", "breaking change"]], "feedback_generico": {"correcto": "¡Excelente planificación! Redujiste el riesgo con pruebas, revisión de planes y un despliegue gradual.", "medio": "Buen enfoque. Te faltó revisar los cambios incompatibles, fijar versiones o desplegar de forma gradual con respaldo.", "malo": "No te preocupes, actualizar dependencias es un desafío habitual. Repasa cómo maneja Terraform las versiones de providers y el archivo de bloqueo de dependencias."}}'::jsonb,
    'aprobada'
FROM skill s, (VALUES ('senior', 'Tienes decenas de configuraciones de Terraform que usan una versión mayor antigua del provider de Azure y debes llevarlas a la versión mayor nueva. ¿Cómo planificas la actualización sin romper producción?')) AS v(nivel, enunciado)
WHERE s.nombre = 'Terraform e Infraestructura como Código'
  AND NOT EXISTS (
      SELECT 1 FROM pregunta p
      WHERE p.enunciado = v.enunciado AND p.skill_id = s.skill_id
        AND p.cargo_id IS NULL AND p.nivel_dificultad = v.nivel);

COMMIT;
