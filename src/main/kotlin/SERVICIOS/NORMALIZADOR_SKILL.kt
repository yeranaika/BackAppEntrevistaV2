package SERVICIOS

import INTEGRACIONES.OfertaLaboral
import MODELOS.NivelExperiencia
import java.util.regex.Pattern

/** Detecta skills y nivel de seniority en el texto libre de una oferta laboral. */
object NormalizadorSkill {

    /** Nombres canónicos de las habilidades blandas que reconoce el normalizador. */
    val SKILLS_BLANDAS = setOf(
        "Comunicación", "Trabajo en equipo", "Liderazgo", "Resolución de problemas",
        "Adaptabilidad", "Gestión del tiempo", "Inteligencia emocional", "Pensamiento crítico"
    )


    // Definición de skills canónicas y sus alias/expresiones regulares asociadas
    private val SKILL_PATTERNS: Map<String, List<Pattern>> = mapOf(
        "Kotlin" to listOf(
            Pattern.compile("""\bkotlin\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bkotlin/jvm\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Java" to listOf(
            Pattern.compile("""\bjava\b(?!\s*script)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bjvm\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcore\s+java\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Python" to listOf(
            Pattern.compile("""\bpython\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpython3\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Node.js" to listOf(
            Pattern.compile("""\bnode\.?js\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bnodejs\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bnode\s+js\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bnode\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Spring Boot" to listOf(
            Pattern.compile("""\bspring\s*boot\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bspring\s+framework\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bspring\s+cloud\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Ktor" to listOf(
            Pattern.compile("""\bktor\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bktor\s+framework\b""", Pattern.CASE_INSENSITIVE)
        ),
        "REST APIs" to listOf(
            Pattern.compile("""\brest\s*api(s)?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\brestful\s*(api(s)?)?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\brest\s+web\s+services\b""", Pattern.CASE_INSENSITIVE)
        ),
        "GraphQL" to listOf(
            Pattern.compile("""\bgraphql\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bgraph\s*ql\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bapollo\s+graphql\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Arquitectura Microservicios" to listOf(
            Pattern.compile("""\bmicroservicios\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bmicroservices\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bmicroservice\s+architecture\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdistributed\s+systems\b""", Pattern.CASE_INSENSITIVE)
        ),
        "SQL" to listOf(
            Pattern.compile("""\bsql\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpl/sql\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bt-?sql\b""", Pattern.CASE_INSENSITIVE)
        ),
        "PostgreSQL" to listOf(
            Pattern.compile("""\bpostgresql\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpostgres\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpsql\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Redis" to listOf(
            Pattern.compile("""\bredis\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bredis\s+cache\b""", Pattern.CASE_INSENSITIVE)
        ),
        "MongoDB" to listOf(
            Pattern.compile("""\bmongodb\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bmongo\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdocumentdb\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Modelado de Datos" to listOf(
            Pattern.compile("""\bmodelado\s+de\s+datos\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdata\s+modeling\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdatabase\s+design\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bschema\s+design\b""", Pattern.CASE_INSENSITIVE)
        ),
        "React" to listOf(
            Pattern.compile("""\breact\.?js\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\breactjs\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\breact\b(?!\s*native)""", Pattern.CASE_INSENSITIVE)
        ),
        "TypeScript" to listOf(
            Pattern.compile("""\btypescript\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bts\b(?!\s*sql)""", Pattern.CASE_INSENSITIVE)
        ),
        "Next.js" to listOf(
            Pattern.compile("""\bnext\.?js\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bnextjs\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bnext\s+js\b""", Pattern.CASE_INSENSITIVE)
        ),
        "CSS / Tailwind" to listOf(
            Pattern.compile("""\btailwind(\s*css)?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcss3?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bsass\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bscss\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Docker" to listOf(
            Pattern.compile("""\bdocker\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdocker-compose\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcontainerization\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcontenedores\b""", Pattern.CASE_INSENSITIVE)
        ),
        "AWS" to listOf(
            Pattern.compile("""\baws\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bamazon\s+web\s+services\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(ec2|s3|rds|lambda|ecs|eks)\b""", Pattern.CASE_INSENSITIVE)
        ),
        "CI/CD" to listOf(
            Pattern.compile("""\bci[/-]cd\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcicd\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bgithub\s+actions\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bgitlab\s+ci\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bjenkins\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Kubernetes" to listOf(
            Pattern.compile("""\bkubernetes\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bk8s\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Android (Kotlin)" to listOf(
            Pattern.compile("""\bandroid\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bandroid\s+sdk\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bandroid\s+development\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Flutter" to listOf(
            Pattern.compile("""\bflutter\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdart\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Jetpack Compose" to listOf(
            Pattern.compile("""\bjetpack\s+compose\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcompose\s+ui\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Python (ML/AI)" to listOf(
            Pattern.compile("""\bmachine\s+learning\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bdeep\s+learning\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(pytorch|tensorflow|scikit-learn|pandas|numpy)\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Computer Vision" to listOf(
            Pattern.compile("""\bcomputer\s+vision\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bvisi[oó]n\s+por\s+computador\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(opencv|mediapipe|yolo)\b""", Pattern.CASE_INSENSITIVE)
        ),
        "LLM / Prompt Engineering" to listOf(
            Pattern.compile("""\bllm(s)?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\blarge\s+language\s+models?\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bprompt\s+engineering\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(openai|langchain|rag|claude|gemini)\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Comunicación" to listOf(
            Pattern.compile("""\bcomunicaci[oó]n\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcommunication\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Trabajo en equipo" to listOf(
            Pattern.compile("""\btrabajo\s+en\s+equipo\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bteamwork\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcollaboration\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bteam\s+player\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Liderazgo" to listOf(
            Pattern.compile("""\bliderazgo\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bleadership\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bteam\s+lead\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bmentoring\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Resolución de problemas" to listOf(
            Pattern.compile("""\bresoluci[oó]n\s+de\s+problemas\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bproblem\s*solving\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\btroubleshooting\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Adaptabilidad" to listOf(
            Pattern.compile("""\badaptabilidad\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\badaptability\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bflexibilidad\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bfast\s+learner\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Gestión del tiempo" to listOf(
            Pattern.compile("""\bgesti[oó]n\s+del\s+tiempo\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\btime\s+management\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpriorit(y|ization)\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Inteligencia emocional" to listOf(
            Pattern.compile("""\binteligencia\s+emocional\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bemotional\s+intelligence\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bempat[ií]a\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bempathy\b""", Pattern.CASE_INSENSITIVE)
        ),
        "Pensamiento crítico" to listOf(
            Pattern.compile("""\bpensamiento\s+cr[ií]tico\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bcritical\s+thinking\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpensamiento\s+anal[ií]tico\b""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\banalytical\s+thinking\b""", Pattern.CASE_INSENSITIVE)
        )
    )

    private val JUNIOR_PATTERN = Pattern.compile("""\b(junior|jr\.?|trainee|entry[\s-]level|principiante)\b""", Pattern.CASE_INSENSITIVE)
    private val SENIOR_PATTERN = Pattern.compile("""\b(senior|sr\.?|lead|principal|staff|arquitecto|architect)\b""", Pattern.CASE_INSENSITIVE)

    /** Nombre canónico (el de la BD) para un nombre libre de tecnología. */
    fun normalizarNombre(nombreLibre: String): String {
        val trimmed = nombreLibre.trim()
        for ((canonical, patterns) in SKILL_PATTERNS) {
            for (p in patterns) {
                if (p.matcher(trimmed).find()) {
                    return canonical
                }
            }
        }
        return trimmed
    }

    /** Nivel pedido en una oferta; si no menciona ninguno se asume semisenior. */
    fun detectarNivel(texto: String): NivelExperiencia = when {
        SENIOR_PATTERN.matcher(texto).find() -> NivelExperiencia.SENIOR
        JUNIOR_PATTERN.matcher(texto).find() -> NivelExperiencia.JUNIOR
        else -> NivelExperiencia.SEMISENIOR
    }

    /** Cuántas veces aparece cada skill canónica en el texto. */
    fun extraerSkills(text: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for ((canonical, patterns) in SKILL_PATTERNS) {
            var occurrences = 0
            for (p in patterns) {
                val matcher = p.matcher(text)
                while (matcher.find()) {
                    occurrences++
                }
            }
            if (occurrences > 0) {
                counts[canonical] = (counts[canonical] ?: 0) + occurrences
            }
        }
        return counts
    }

    /**
     * Recorre las ofertas y, por cada skill mencionada, cuenta menciones, en cuántas ofertas aparece
     * y qué nivel piden con más frecuencia. Analiza título y descripción completos de cada oferta.
     */
    fun analizarOfertas(ofertas: List<OfertaLaboral>): Map<String, EstadisticaSkill> {
        val menciones = mutableMapOf<String, Int>()
        val ofertasConSkill = mutableMapOf<String, Int>()
        val nivelesPorSkill = mutableMapOf<String, MutableMap<NivelExperiencia, Int>>()
        for (oferta in ofertas) {
            val texto = oferta.textoCompleto
            val nivel = detectarNivel(texto)
            for ((skill, veces) in extraerSkills(texto)) {
                menciones.merge(skill, veces, Int::plus)
                ofertasConSkill.merge(skill, 1, Int::plus)
                nivelesPorSkill.getOrPut(skill) { mutableMapOf() }.merge(nivel, 1, Int::plus)
            }
        }
        return menciones.mapValues { (skill, total) ->
            EstadisticaSkill(
                menciones = total,
                ofertasConSkill = ofertasConSkill.getValue(skill),
                nivelPredominante = nivelesPorSkill.getValue(skill).maxBy { it.value }.key
            )
        }
    }
}

data class EstadisticaSkill(
    val menciones: Int,
    val ofertasConSkill: Int,
    val nivelPredominante: NivelExperiencia
)
