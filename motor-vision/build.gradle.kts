import java.io.File
import java.util.Properties

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

    // Inferencia ONNX Runtime (GPU CUDA con respaldo automático a CPU) del modelo de visión DivLara
    implementation("com.microsoft.onnxruntime:onnxruntime_gpu:1.20.0")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

fun cargarPropiedadesLocalesMotor(): Properties {
    val archivo = rootProject.file("local.properties")
    return Properties().apply {
        if (archivo.isFile) archivo.inputStream().use { load(it) }
    }
}

fun aplicarEntornoGpuLocal(asignar: (String, Any) -> Unit) {
    val props = cargarPropiedadesLocalesMotor()
    props.getProperty("motor.cuda.visible.devices")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        asignar("CUDA_VISIBLE_DEVICES", it)
    }
    props.getProperty("motor.cuda.device.id")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        asignar("ONNX_CUDA_DEVICE_ID", it)
    }
    props.getProperty("motor.onnx.dll.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { dir ->
        val pathActual = System.getenv("PATH") ?: ""
        val nuevoPath = if (pathActual.contains(dir, ignoreCase = true)) {
            pathActual
        } else {
            "$dir${File.pathSeparator}$pathActual"
        }
        asignar("PATH", nuevoPath)
    }
}

// El modelo se busca en `models/<version>/` de la raíz del repositorio (misma ruta que antes con el backend).
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    aplicarEntornoGpuLocal { clave, valor -> environment(clave, valor) }
}
tasks.test {
    workingDir = rootProject.projectDir
    aplicarEntornoGpuLocal { clave, valor -> environment(clave, valor) }
}
