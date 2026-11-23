pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ktor-sample"

// Visión desacoplada (T3.11): el backend (raíz) solo habla con el motor por HTTP.
//  - nucleo-vision: contrato JSON, errores, validación y extractor de features (sin modelo ni ONNX).
//  - motor-vision: segunda app Ktor (puerto 8094) que carga el modelo ONNX y expone POST /predict.
include("nucleo-vision", "motor-vision")
