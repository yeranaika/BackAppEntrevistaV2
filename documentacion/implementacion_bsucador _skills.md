Implementation Plan
Plan de Implementación: Worker de Actualización de Tendencias y Skills del Mercado
Este documento detalla el diseño, la investigación de APIs y la implementación técnica para el Worker de Actualización de Tendencias y Skills del Mercado, cumpliendo con los criterios de aceptación y checklist requeridos en la tarjeta de tareas.

1. Investigación de APIs de Empleo y Mercado
Para consultar tendencias y ofertas de trabajo del sector tecnológico, se evaluaron las principales opciones:

API / Fuente	Ventajas	Desventajas / Cuotas	Recomendación
JSearch (RapidAPI) (Ya referenciada en .env)	Agrega ofertas reales de LinkedIn, Indeed, Glassdoor, ZipRecruiter en formato JSON limpio.	Cuota gratuita limitada (50-200 peticiones/mes en RapidAPI).	Primaria (Aprovechando las credenciales existentes en .env).
Remotive API	100% gratuita, sin necesidad de API Key, enfocada en ofertas tech y remotas.	Solo roles remotos.	Secundaria / Fallback gratis.
Arbeitnow API	Gratuita, sin API key, ofertas tech directas de ATS (Greenhouse, Lever).	Principalmente Europa y remoto.	Complementaria.
Dataset Estructurado / Fallback Local	Cero latencia, no depende de cuotas ni fallos de red.	Estático si no hay conexión externa.	Garantía de resiliencia si fallan las APIs externas o cuota excedida.
Estrategia de Consulta e Integración:
Implementaremos un JobMarketService multicanal y tolerante a fallos:

Intenta consultar JSearch API (usando JSEARCH_API_KEY y JSEARCH_API_HOST).
Si falla o no hay cuota, consulta Remotive / Arbeitnow API.
Si no hay internet o ambas fallan, utiliza un Dataset sintético / estructurado de mercado para garantizar que el cálculo de scores nunca arroje excepción ni deje la BD inconsistente.
2. Checklist y Requerimientos de la Tarjeta
 Consumo de APIs de ofertas / dataset estructurado: Cliente HTTP asíncrono con Ktor Client CIO.
 Algoritmo de normalización de nombres: Mapeo de sinónimos y variaciones (ej: ReactJS 
→
→ React, Ktor framework 
→
→ Ktor, Postgres 
→
→ PostgreSQL, Golang 
→
→ Go, TailwindCSS 
→
→ CSS / Tailwind) utilizando expresiones regulares con límites de palabra (\b) para evitar falsos positivos (ej. evitar que "Go" coincida con "Google").
 Cálculo de demanda_score: Conteo de frecuencias en títulos y descripciones de puestos, normalización a escala 1–100, y actualización de la columna demanda_score en la tabla skill.
 Histórico semanal en skill_tendencia: Inserción de registros con skill_id, frecuencia_ofertas, nivel_requerido (junior, semisenior, senior) y fecha de actualización.
 Actualización de matriz de ponderación en cargo_skill: Ajuste dinámico proporcional del peso (1–100) en cargo_skill si la demanda del mercado incrementa o cambia.
 Ejecución no bloqueante: Corutina en Dispatchers.IO programada semanalmente, además de un endpoint administrativo para ejecución bajo demanda (POST /admin/market/sync-trends).
3. Propuesta de Cambios en el Código
Modelos y Tablas Exposed (Base de Datos)
[NEW] 
src/main/kotlin/data/tables/market/MarketTables.kt
Definición de las tablas Exposed correspondientes al esquema PostgreSQL:

SkillTable (skill): skill_id, nombre, categoria, tipo_area, descripcion, demanda_score, activo.
SkillTendenciaTable (skill_tendencia): tendencia_id, skill_id, frecuencia_ofertas, nivel_requerido, fecha_actualizacion.
CargoTable (cargo): cargo_id, nombre, area, descripcion, nivel_base, activo.
CargoSkillTable (cargo_skill): cargo_skill_id, cargo_id, skill_id, nivel_requerido, peso, obligatoria.
Repositorios y Servicios de Mercado
[NEW] 
src/main/kotlin/data/repository/market/SkillMarketRepository.kt
Métodos para:

Listar todas las skills activas.
Actualizar demanda_score por skill_id.
Insertar registro en skill_tendencia.
Actualizar pesos en cargo_skill según el nuevo score de demanda.
Consultar tendencias históricas y reporte de skills.
[NEW] 
src/main/kotlin/services/market/SkillNormalizer.kt
Motor de normalización y extracción de tecnologías:

Diccionario exhaustivo de alias/sinónimos para las 38 skills del sistema.
Compilación de Regex con Word Boundaries ((?i)\b...\b) para conteo exacto de menciones en títulos y descripciones de empleo.
Clasificación de nivel de experiencia inferido (junior, semisenior, senior) en base a palabras clave contextuales.
[NEW] 
src/main/kotlin/services/market/JobMarketClient.kt
Cliente HTTP usando io.ktor.client.HttpClient (CIO engine):

Consulta a JSearch (RapidAPI) para búsquedas de cargos tech (Software Engineer, Backend Developer, Frontend Developer, Data Engineer, DevOps, Mobile Developer).
Fallback a Remotive API / dataset estructurado.
[NEW] 
src/main/kotlin/services/market/SkillTrendWorker.kt
Worker asíncrono con corutinas:

Ejecución periódica (intervalo configurable, por defecto 7 días).
Algoritmo de cálculo de demanda_score (1 a 100) basado en frecuencia relativa de aparición: 
demanda_score
i
=
clamp
(
round
(
frecuencia
i
max_frecuencia
×
70
+
30
)
,
1
,
100
)
demanda_score 
i
​
 =clamp(round( 
max_frecuencia
frecuencia 
i
​
 
​
 ×70+30),1,100)
Registro en skill_tendencia.
Recálculo ponderado en cargo_skill.
Manejo de excepciones y logging detallado.
Rutas e Integración
[NEW] 
src/main/kotlin/routes/market/MarketRoutes.kt
Endpoints HTTP:

POST /admin/market/sync-trends: Disparador manual para ejecutar el worker de inmediato (útil para pruebas y panel admin).
GET /market/skills: Listado de skills con su demanda_score actual y categoría.
GET /market/skills/{id}/tendencias: Historial semanal de tendencias de una skill.
[MODIFY] 
src/main/kotlin/routes/Routing.kt
Registrar marketRoutes en el router principal.

[MODIFY] 
src/main/kotlin/Application.kt
Inicializar SkillTrendWorker.start() en el ciclo de vida de la aplicación Ktor de forma no bloqueante.

4. Plan de Verificación
Pruebas Unitarias e Integración
Normalizador (SkillNormalizerTest):
Verificar que alias como "ReactJS", "Ktor framework", "Postgres", "Node" mapeen a "React", "Ktor", "PostgreSQL", "Node.js".
Verificar que no existan falsos positivos (ej. "Good" o "Google" no deben contar como "Go").
Cálculo de Score:
Comprobar que todos los scores resultantes estén dentro del rango 
[
1
,
100
]
[1,100].
Ejecución del Worker:
Invocar la ejecución manual del worker y verificar la actualización de la tabla skill y la inserción en skill_tendencia.
Compilación y Build Gradle:
Ejecutar ./gradlew test y ./gradlew build para certificar que todo compile limpiamente.
¿Deseas que proceda con la implementación según este plan?