import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.JavaExec

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization) // <- NUEVO
}

group = "com.example"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // 🔹 Fuerza Java 21
    }
}

kotlin {
    jvmToolchain(21) // 🔹 Fuerza Kotlin a compilar para JVM 21
}


application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    //dpara manejar variables de entorno
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)

    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.config.yaml)

    // Logging
    implementation(libs.logback.classic)

    //implementacion login google
    // Ktor (server + oauth + client HTTP para hablar con Google)
    implementation("io.ktor:ktor-server-auth:3.+")
    implementation("io.ktor:ktor-server-sessions:3.+")
    implementation("io.ktor:ktor-client-cio:3.+")
    implementation("io.ktor:ktor-client-content-negotiation:3.+")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.+")
    implementation("io.ktor:ktor-client-logging-jvm:0.55.0")

    // Opción A (simple, recomendada por Google) para validar ID tokens:
    implementation("com.google.api-client:google-api-client:2.+")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")


    // Tests
    testImplementation(libs.ktor.server.test.host) // alias correcto
    testImplementation(libs.kotlin.test.junit)

    // para la serialización JSON
    implementation("org.jetbrains.exposed:exposed-json:0.55.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")    

    // DB
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")    // <- ACTUALIZA ESTO
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.55.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // para JSON en DB
    implementation("org.jetbrains.exposed:exposed-json:0.55.0")

    // Seguridad: hash de contraseñas en la app 
    implementation("de.mkammerer:argon2-jvm:2.11")     
    implementation("at.favre.lib:bcrypt:0.10.2")                // Argon2id (reemplaza contraseña en claro)

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Logs
    implementation("ch.qos.logback:logback-classic:1.5.6")

    //
    implementation("io.ktor:ktor-server-status-pages-jvm:3.0.0")
    implementation("io.ktor:ktor-server-auth-jvm:3.0.0")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.0.0")

    // Email (Jakarta Mail para envío de correos)
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("jakarta.activation:jakarta.activation-api:2.1.0")
    implementation("org.eclipse.angus:angus-activation:2.0.1")

    // Excel Export
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}
