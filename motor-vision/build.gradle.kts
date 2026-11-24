plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(project(":nucleo-vision"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.config.yaml)
    implementation("io.ktor:ktor-server-status-pages-jvm:3.3.1")
    implementation(libs.logback.classic)

    // Inferencia ONNX Runtime del modelo de visión DivLara (aquí vive el modelo, no en el backend)
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

// El modelo se busca en `models/<version>/` de la raíz del repositorio (misma ruta que antes con el backend).
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.test { workingDir = rootProject.projectDir }
