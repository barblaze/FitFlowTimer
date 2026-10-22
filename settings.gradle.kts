pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FitFlowTimer"

include(":core")

// El modulo :app solo se incluye si hay SDK de Android disponible.
// Asi los tests de :core se pueden ejecutar en local sin instalar el SDK Android.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    rootProject.file("local.properties").exists()

if (hasAndroidSdk) {
    include(":app")
}