import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.agronomia"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.agronomia"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val aiBaseUrl: String = (localProperties.getProperty("AI_BASE_URL") ?: "")
            .trim()
            .removeSurrounding("\"")
        val aiApiKey: String = (localProperties.getProperty("AI_API_KEY") ?: "")
            .trim()
            .removeSurrounding("\"")
        val aiModel: String = (localProperties.getProperty("AI_MODEL") ?: "gpt-4o-mini")
            .trim()
            .removeSurrounding("\"")
        // Ruta del endpoint de chat, relativa a AI_BASE_URL.
        // OpenAI: "v1/chat/completions"  |  Gemini: "chat/completions"
        val aiIdentifyPath: String = (
            localProperties.getProperty("AI_IDENTIFY_PATH") ?: "v1/chat/completions"
            )
            .trim()
            .removeSurrounding("\"")

        // Pl@ntNet: identificacion de especie por imagen.
        val plantNetBaseUrl: String = (localProperties.getProperty("PLANTNET_BASE_URL")
            ?: "https://my-api.plantnet.org/")
            .trim()
            .removeSurrounding("\"")
        val plantNetApiKey: String = (localProperties.getProperty("PLANTNET_API_KEY") ?: "")
            .trim()
            .removeSurrounding("\"")
        val plantNetProject: String = (localProperties.getProperty("PLANTNET_PROJECT") ?: "all")
            .trim()
            .removeSurrounding("\"")
        val plantNetLang: String = (localProperties.getProperty("PLANTNET_LANG") ?: "es")
            .trim()
            .removeSurrounding("\"")

        buildConfigField("String", "AI_BASE_URL", "\"$aiBaseUrl\"")
        buildConfigField("String", "AI_API_KEY", "\"$aiApiKey\"")
        buildConfigField("String", "AI_MODEL", "\"$aiModel\"")
        buildConfigField("String", "AI_IDENTIFY_PATH", "\"$aiIdentifyPath\"")

        buildConfigField("String", "PLANTNET_BASE_URL", "\"$plantNetBaseUrl\"")
        buildConfigField("String", "PLANTNET_API_KEY", "\"$plantNetApiKey\"")
        buildConfigField("String", "PLANTNET_PROJECT", "\"$plantNetProject\"")
        buildConfigField("String", "PLANTNET_LANG", "\"$plantNetLang\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coil
    implementation(libs.coil.compose)

    // Lectura de orientación EXIF (fotos de cámara rotadas)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
