package PRUEBAS

import MODELOS.NivelExperiencia
import PRUEBAS.DOBLES.oferta
import SERVICIOS.NormalizadorSkill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruebaNormalizadorSkill {

    @Test
    fun `normaliza nombres libres al nombre canonico`() {
        mapOf(
            "ReactJS" to "React", "react.js" to "React", "Ktor framework" to "Ktor", "postgres" to "PostgreSQL",
            "psql" to "PostgreSQL", "nodejs" to "Node.js", "tailwindcss" to "CSS / Tailwind", "Spring Boot" to "Spring Boot",
            "microservices" to "Arquitectura Microservicios", "prompt engineering" to "LLM / Prompt Engineering"
        ).forEach { (libre, canonico) -> assertEquals(canonico, NormalizadorSkill.normalizarNombre(libre), libre) }
    }

    @Test
    fun `JavaScript no cuenta como Java`() {
        val skills = NormalizadorSkill.extraerSkills("We are seeking a senior JavaScript developer with Node and React.")
        assertTrue("React" in skills && "Node.js" in skills)
        assertFalse("Java" in skills)
    }

    @Test
    fun `extrae skills tecnicas y blandas de una descripcion`() {
        val skills = NormalizadorSkill.extraerSkills(
            """
            Buscamos un Ingeniero Backend con dominio de Kotlin, Spring Boot, Ktor y REST APIs.
            Experiencia en bases de datos PostgreSQL y Redis. Deseable Docker y CI/CD con GitHub Actions.
            Habilidades clave: trabajo en equipo, comunicacion asertiva y resolucion de problemas.
            """.trimIndent()
        )
        listOf("Kotlin", "Spring Boot", "Ktor", "REST APIs", "PostgreSQL", "Redis", "Docker", "CI/CD",
            "Trabajo en equipo", "Comunicación", "Resolución de problemas").forEach { assertTrue(it in skills, it) }
    }

    @Test
    fun `detecta el nivel pedido`() {
        assertEquals(NivelExperiencia.JUNIOR, NormalizadorSkill.detectarNivel("Junior Android Developer"))
        assertEquals(NivelExperiencia.JUNIOR, NormalizadorSkill.detectarNivel("Entry-level Software Engineer"))
        assertEquals(NivelExperiencia.SENIOR, NormalizadorSkill.detectarNivel("Senior Tech Lead & Architect"))
        assertEquals(NivelExperiencia.SENIOR, NormalizadorSkill.detectarNivel("Principal Backend Engineer"))
        assertEquals(NivelExperiencia.SEMISENIOR, NormalizadorSkill.detectarNivel("Full Stack Developer"))
    }

    @Test
    fun `analizar ofertas cuenta menciones, ofertas y nivel predominante usando titulo y descripcion`() {
        val estadisticas = NormalizadorSkill.analizarOfertas(
            listOf(
                oferta("Senior Kotlin Developer", "Kotlin y Ktor en producción"),
                oferta("Senior Backend", "Kotlin, PostgreSQL"),
                oferta("Junior Frontend", "React")
            )
        )
        val kotlin = estadisticas.getValue("Kotlin")
        assertEquals(3, kotlin.menciones) // 2 en la primera oferta (título + descripción) y 1 en la segunda
        assertEquals(2, kotlin.ofertasConSkill)
        assertEquals(NivelExperiencia.SENIOR, kotlin.nivelPredominante)
        assertEquals(NivelExperiencia.JUNIOR, estadisticas.getValue("React").nivelPredominante)
    }
}
