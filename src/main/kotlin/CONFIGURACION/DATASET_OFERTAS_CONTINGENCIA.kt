package CONFIGURACION

import INTEGRACIONES.OfertaLaboral

private const val FUENTE = "Dataset-Estructurado"

/** Ofertas de referencia para cuando ninguna API de empleo responde (sin internet, cuota agotada). */
val OFERTAS_CONTINGENCIA = listOf(
    OfertaLaboral(
        "Senior Backend Engineer (Kotlin & Spring Boot)",
        "Buscamos Backend Developer Senior con experiencia sólida en Kotlin, Java, Spring Boot, Ktor, REST APIs y Microservicios. " +
            "Manejo de bases de datos relacionales SQL, PostgreSQL, modelado de datos y Redis. Infraestructura con Docker, Kubernetes, AWS y pipelines CI/CD. " +
            "Habilidades blandas: comunicación asertiva, trabajo en equipo, resolución de problemas y liderazgo técnico.",
        FUENTE
    ),
    OfertaLaboral(
        "Junior Backend Developer (Kotlin / Java)",
        "Buscamos Desarrollador Junior Backend con conocimientos en Kotlin, Java, REST APIs, SQL, PostgreSQL y Git. " +
            "Capacidad de aprendizaje rápido, adaptabilidad, proactividad, pensamiento crítico y buena comunicación.",
        FUENTE
    ),
    OfertaLaboral(
        "Full Stack Developer (React & Node.js)",
        "Empresa internacional busca Full Stack Developer SemiSenior. Frontend con React, Next.js, TypeScript y Tailwind CSS. " +
            "Backend con Node.js, Express, REST APIs, MongoDB y PostgreSQL. Contenedores con Docker y despliegue en AWS. Trabajo en equipo e inteligencia emocional.",
        FUENTE
    ),
    OfertaLaboral(
        "Senior Android Developer (Jetpack Compose & Kotlin)",
        "Estamos contratando Android Developer Senior. Experiencia en Android SDK nativo, Kotlin, Jetpack Compose, coroutines y arquitectura limpia. " +
            "Consumo de REST APIs y GraphQL. CI/CD para Google Play. Liderazgo, resolución de problemas y gestión del tiempo.",
        FUENTE
    ),
    OfertaLaboral(
        "Data Engineer & Machine Learning Specialist",
        "Buscamos Ingeniero de Datos con Python, SQL avanzado, modelado de datos, PostgreSQL, Redis y pipelines ETL. " +
            "Conocimientos en Python ML/AI (PyTorch, Pandas, Scikit-learn), Computer Vision (OpenCV) y aplicaciones con LLM / Prompt Engineering. " +
            "Pensamiento crítico, trabajo en equipo y adaptabilidad.",
        FUENTE
    ),
    OfertaLaboral(
        "DevOps & Cloud Infrastructure Engineer",
        "Ingeniero DevOps con amplia experiencia en Docker, Kubernetes, AWS, Terraform y CI/CD con GitHub Actions. " +
            "Monitoreo, microservicios, seguridad y scripts en Python. Comunicación efectiva y resolución de problemas bajo presión.",
        FUENTE
    ),
    OfertaLaboral(
        "Frontend Specialist (React, TypeScript & Tailwind)",
        "Desarrollador Frontend SemiSenior con React, TypeScript, Next.js, CSS / Tailwind y GraphQL. " +
            "Diseño responsive, pruebas unitarias y colaboración ágil. Gran adaptabilidad y trabajo en equipo.",
        FUENTE
    ),
    OfertaLaboral(
        "Mobile Developer (Flutter / Dart / Android)",
        "Desarrollador móvil con experiencia en Flutter, Dart, Android Kotlin y consumo de REST APIs. " +
            "Diseño centrado en el usuario, resolución de problemas y gestión del tiempo.",
        FUENTE
    ),
    OfertaLaboral(
        "AI & Prompt Engineer Specialist",
        "Ingeniero de Inteligencia Artificial enfocado en LLM / Prompt Engineering, LangChain, RAG y OpenAI APIs. " +
            "Backend con Python, FastAPI, PostgreSQL y vector stores. Pensamiento crítico e inteligencia emocional.",
        FUENTE
    )
)
