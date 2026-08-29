package services.market

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillNormalizerTest {

    @Test
    fun testNormalizationOfTechnologyNames() {
        assertEquals("React", SkillNormalizer.normalizeTechnologyName("ReactJS"))
        assertEquals("React", SkillNormalizer.normalizeTechnologyName("react.js"))
        assertEquals("Ktor", SkillNormalizer.normalizeTechnologyName("Ktor framework"))
        assertEquals("PostgreSQL", SkillNormalizer.normalizeTechnologyName("postgres"))
        assertEquals("PostgreSQL", SkillNormalizer.normalizeTechnologyName("psql"))
        assertEquals("Node.js", SkillNormalizer.normalizeTechnologyName("nodejs"))
        assertEquals("CSS / Tailwind", SkillNormalizer.normalizeTechnologyName("tailwindcss"))
        assertEquals("Spring Boot", SkillNormalizer.normalizeTechnologyName("Spring Boot"))
        assertEquals("Arquitectura Microservicios", SkillNormalizer.normalizeTechnologyName("microservices"))
        assertEquals("LLM / Prompt Engineering", SkillNormalizer.normalizeTechnologyName("prompt engineering"))
    }

    @Test
    fun testJavaDoesNotMatchJavaScript() {
        val jsText = "We are seeking a senior JavaScript developer with Node and React."
        val skills = SkillNormalizer.extractSkillsFromText(jsText)
        assertTrue(skills.containsKey("React"))
        assertTrue(skills.containsKey("Node.js"))
        assertTrue(!skills.containsKey("Java"), "JavaScript no debe contar como Java")
    }

    @Test
    fun testExtractionOfMultipleSkillsFromJobDescription() {
        val jobDesc = """
            Buscamos un Ingeniero Backend con dominio de Kotlin, Spring Boot, Ktor y REST APIs.
            Experiencia en bases de datos PostgreSQL y Redis.
            Deseable manejo de Docker y CI/CD con GitHub Actions.
            Habilidades clave: trabajo en equipo, comunicacion asertiva y resolucion de problemas.
        """.trimIndent()

        val skills = SkillNormalizer.extractSkillsFromText(jobDesc)

        assertTrue(skills.containsKey("Kotlin"))
        assertTrue(skills.containsKey("Spring Boot"))
        assertTrue(skills.containsKey("Ktor"))
        assertTrue(skills.containsKey("REST APIs"))
        assertTrue(skills.containsKey("PostgreSQL"))
        assertTrue(skills.containsKey("Redis"))
        assertTrue(skills.containsKey("Docker"))
        assertTrue(skills.containsKey("CI/CD"))
        assertTrue(skills.containsKey("Trabajo en equipo"))
        assertTrue(skills.containsKey("Comunicación"))
        assertTrue(skills.containsKey("Resolución de problemas"))
    }

    @Test
    fun testSeniorityDetection() {
        assertEquals("junior", SkillNormalizer.detectSeniority("Junior Android Developer"))
        assertEquals("junior", SkillNormalizer.detectSeniority("Entry-level Software Engineer"))
        assertEquals("senior", SkillNormalizer.detectSeniority("Senior Tech Lead & Architect"))
        assertEquals("senior", SkillNormalizer.detectSeniority("Principal Backend Engineer"))
        assertEquals("semisenior", SkillNormalizer.detectSeniority("Full Stack Developer"))
    }
}
