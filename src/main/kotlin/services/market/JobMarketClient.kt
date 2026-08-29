package services.market

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

data class RawJobPosting(
    val title: String,
    val description: String,
    val source: String
)

class JobMarketClient(
    private val rapidApiKey: String? = null,
    private val rapidApiHost: String = "jsearch.p.rapidapi.com"
) {
    private val logger = LoggerFactory.getLogger(JobMarketClient::class.java)
    private val htmlTagPattern = Pattern.compile("<[^>]*>")

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }

    private fun cleanHtml(raw: String): String {
        return htmlTagPattern.matcher(raw).replaceAll(" ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    /**
     * Consulta en tiempo real las APIs de empleo en vivo (JSearch RapidAPI, Remotive y Arbeitnow).
     */
    suspend fun fetchTechJobPostings(specificQuery: String? = null): List<RawJobPosting> {
        val jobs = mutableListOf<RawJobPosting>()

        // 1. Consulta en VIVO a JSearch API (LinkedIn, Indeed, Glassdoor agregados)
        if (!rapidApiKey.isNullOrBlank() && !rapidApiKey.startsWith("your_")) {
            try {
                logger.info("Conectando en vivo a JSearch API (RapidAPI) para: {}", specificQuery ?: "Roles tech")
                val queries = if (!specificQuery.isNullOrBlank()) listOf(specificQuery)
                else listOf("Software Engineer", "Backend Developer", "Frontend Developer", "Data Engineer", "Android Developer", "DevOps")

                for (query in queries) {
                    val response = client.get("https://$rapidApiHost/search") {
                        header("X-RapidAPI-Key", rapidApiKey)
                        header("X-RapidAPI-Host", rapidApiHost)
                        parameter("query", query)
                        parameter("page", "1")
                        parameter("num_pages", "1")
                    }

                    if (response.status.isSuccess()) {
                        val text = response.bodyAsText()
                        val parsed = jsonConfig.parseToJsonElement(text).jsonObject
                        val dataArray = parsed["data"]?.jsonArray
                        dataArray?.forEach { el ->
                            val obj = el.jsonObject
                            val title = obj["job_title"]?.jsonPrimitive?.content ?: ""
                            val desc = obj["job_description"]?.jsonPrimitive?.content ?: ""
                            val qualifications = obj["job_highlights"]?.jsonObject?.get("Qualifications")?.jsonArray
                                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                                ?.joinToString(" ") ?: ""
                            val fullText = cleanHtml("$desc $qualifications")
                            if (title.isNotBlank() || fullText.isNotBlank()) {
                                jobs.add(RawJobPosting(title, fullText, "JSearch (LinkedIn/Indeed/Glassdoor)"))
                            }
                        }
                    }
                }
                if (jobs.isNotEmpty()) {
                    logger.info("JSearch API devolvió {} ofertas reales en vivo", jobs.size)
                    return jobs
                }
            } catch (e: Exception) {
                logger.warn("Aviso JSearch API: {}: {}. Probando fuente en vivo Remotive...", e.javaClass.simpleName, e.message)
            }
        }

        // 2. Consulta en VIVO a Remotive API (ofertas remotas en vivo con tags y descripciones reales)
        try {
            logger.info("Conectando en vivo a Remotive API...")
            val response = client.get("https://remotive.com/api/remote-jobs") {
                parameter("category", "software-dev")
                if (!specificQuery.isNullOrBlank()) {
                    parameter("search", specificQuery)
                }
                parameter("limit", "50")
            }
            if (response.status.isSuccess()) {
                val text = response.bodyAsText()
                val parsed = jsonConfig.parseToJsonElement(text).jsonObject
                val jobsArray = parsed["jobs"]?.jsonArray
                jobsArray?.forEach { el ->
                    val obj = el.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val desc = cleanHtml(obj["description"]?.jsonPrimitive?.content ?: "")
                    val tags = obj["tags"]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }?.joinToString(" ") ?: ""
                    val fullContent = "$desc $tags"
                    if (title.isNotBlank() || fullContent.isNotBlank()) {
                        jobs.add(RawJobPosting(title, fullContent, "Remotive API"))
                    }
                }
                if (jobs.isNotEmpty()) {
                    logger.info("Remotive API devolvió {} ofertas reales en vivo", jobs.size)
                    return jobs
                }
            }
        } catch (e: Exception) {
            logger.warn("Aviso Remotive API: {}: {}. Probando Arbeitnow API en vivo...", e.javaClass.simpleName, e.message)
        }

        // 3. Consulta en VIVO a Arbeitnow API (ofertas tech directas de ATS Greenhouse/Lever)
        try {
            logger.info("Conectando en vivo a Arbeitnow API...")
            val response = client.get("https://www.arbeitnow.com/api/job-board-api") {
                if (!specificQuery.isNullOrBlank()) {
                    parameter("search", specificQuery)
                }
            }
            if (response.status.isSuccess()) {
                val text = response.bodyAsText()
                val parsed = jsonConfig.parseToJsonElement(text).jsonObject
                val dataArray = parsed["data"]?.jsonArray
                dataArray?.forEach { el ->
                    val obj = el.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val desc = cleanHtml(obj["description"]?.jsonPrimitive?.content ?: "")
                    val tags = obj["tags"]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }?.joinToString(" ") ?: ""
                    val fullContent = "$desc $tags"
                    if (title.isNotBlank() || fullContent.isNotBlank()) {
                        jobs.add(RawJobPosting(title, fullContent, "Arbeitnow API"))
                    }
                }
                if (jobs.isNotEmpty()) {
                    logger.info("Arbeitnow API devolvió {} ofertas reales en vivo", jobs.size)
                    return jobs
                }
            }
        } catch (e: Exception) {
            logger.warn("Aviso Arbeitnow API: {}: {}. Usando dataset estructurado de contingencia...", e.javaClass.simpleName, e.message)
        }

        // 4. Contingencia segura si no hay conexión a internet
        logger.warn("Sin acceso a APIs externas. Cargando dataset estructurado de contingencia...")
        return getStructuredFallbackDataset(specificQuery)
    }

    private fun getStructuredFallbackDataset(specificQuery: String? = null): List<RawJobPosting> {
        val full = listOf(
            RawJobPosting(
                "Senior Backend Engineer (Kotlin & Spring Boot)",
                "Buscamos Backend Developer Senior con experiencia sólida en Kotlin, Java, Spring Boot, Ktor, REST APIs y Microservicios. " +
                        "Manejo de bases de datos relacionales SQL, PostgreSQL, modelado de datos y Redis. Infraestructura con Docker, Kubernetes, AWS y pipelines CI/CD. " +
                        "Habilidades blandas: comunicación asertiva, trabajo en equipo, resolución de problemas y liderazgo técnico.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Junior Backend Developer (Kotlin / Java)",
                "Buscamos Desarrollador Junior Backend con conocimientos en Kotlin, Java, REST APIs, SQL, PostgreSQL y Git. " +
                        "Capacidad de aprendizaje rápido, adaptabilidad, proactividad, pensamiento crítico y buena comunicación.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Full Stack Developer (React & Node.js)",
                "Empresa internacional busca Full Stack Developer SemiSenior. Frontend con React, Next.js, TypeScript y Tailwind CSS. " +
                        "Backend con Node.js, Express, REST APIs, MongoDB y PostgreSQL. Contenedores con Docker y despliegue en AWS. Trabajo en equipo e inteligencia emocional.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Senior Android Developer (Jetpack Compose & Kotlin)",
                "Estamos contratando Android Developer Senior. Experiencia en Android SDK nativo, Kotlin, Jetpack Compose, coroutines y arquitectura limpia. " +
                        "Consumo de REST APIs y GraphQL. CI/CD para Google Play. Liderazgo, resolución de problemas y gestión del tiempo.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Data Engineer & Machine Learning Specialist",
                "Buscamos Ingeniero de Datos con Python, SQL avanzado, modelado de datos, PostgreSQL, Redis y pipelines ETL. " +
                        "Conocimientos en Python ML/AI (PyTorch, Pandas, Scikit-learn), Computer Vision (OpenCV) y aplicaciones con LLM / Prompt Engineering. " +
                        "Pensamiento crítico, trabajo en equipo y adaptabilidad.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "DevOps & Cloud Infrastructure Engineer",
                "Ingeniero DevOps con amplia experiencia en Docker, Kubernetes, AWS, Terraform y CI/CD con GitHub Actions. " +
                        "Monitoreo, microservicios, seguridad y scripts en Python. Comunicación efectiva y resolución de problemas bajo presión.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Frontend Specialist (React, TypeScript & Tailwind)",
                "Desarrollador Frontend SemiSenior con React, TypeScript, Next.js, CSS / Tailwind y GraphQL. " +
                        "Diseño responsive, pruebas unitarias y colaboración ágil. Gran adaptabilidad y trabajo en equipo.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "Mobile Developer (Flutter / Dart / Android)",
                "Desarrollador móvil con experiencia en Flutter, Dart, Android Kotlin y consumo de REST APIs. " +
                        "Diseño centrado en el usuario, resolución de problemas y gestión del tiempo.",
                "Dataset-Estructurado"
            ),
            RawJobPosting(
                "AI & Prompt Engineer Specialist",
                "Ingeniero de Inteligencia Artificial enfocado en LLM / Prompt Engineering, LangChain, RAG y OpenAI APIs. " +
                        "Backend con Python, FastAPI, PostgreSQL y vector stores. Pensamiento crítico e inteligencia emocional.",
                "Dataset-Estructurado"
            )
        )
        if (specificQuery.isNullOrBlank()) return full
        val filtered = full.filter {
            it.title.contains(specificQuery, ignoreCase = true) ||
            it.description.contains(specificQuery, ignoreCase = true)
        }
        return if (filtered.isNotEmpty()) filtered else full
    }

    fun close() {
        client.close()
    }
}
