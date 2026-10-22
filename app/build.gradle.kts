plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fitflow.timer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitflow.timer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        val encoded = System.getenv("KEYSTORE_BASE64")
        if (!encoded.isNullOrBlank()) {
            val keystoreFile = File(project.layout.buildDirectory.get().asFile, "release.p12")
            keystoreFile.parentFile?.mkdirs()
            keystoreFile.writeBytes(java.util.Base64.getMimeDecoder().decode(encoded))

            val configure: (com.android.build.api.dsl.SigningConfig) -> Unit = { signing ->
                signing.storeFile = keystoreFile
                signing.storeType = "pkcs12"
                signing.storePassword = System.getenv("KEYSTORE_PASSWORD")
                signing.keyAlias = System.getenv("KEY_ALIAS")
                signing.keyPassword = System.getenv("KEY_PASSWORD")
                signing.enableV1Signing = true
                signing.enableV2Signing = true
                signing.enableV3Signing = true
            }

            if (names.contains("release")) {
                configure(getByName("release"))
            } else {
                configure(create("release"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (!System.getenv("KEYSTORE_BASE64").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}