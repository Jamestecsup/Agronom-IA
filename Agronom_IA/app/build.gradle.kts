plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.correa.agronom_ia"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.correa.agronom_ia"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val apiBaseUrl = providers.gradleProperty("PLANT_API_BASE_URL").getOrElse("http://10.0.2.2/agronomia-api")
        buildConfigField("String", "PLANT_API_BASE_URL", "\"$apiBaseUrl\"")
        val plantNetKey = providers.gradleProperty("PLANTNET_API_KEY")
            .orElse(providers.environmentVariable("PLANTNET_API_KEY")).getOrElse("")
        val openCodeBaseUrl = providers.gradleProperty("OPENCODE_SERVER_BASE_URL")
            .orElse(providers.environmentVariable("OPENCODE_SERVER_BASE_URL"))
            .getOrElse("http://10.0.2.2:4096")
        fun String.asBuildConfigString() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
        buildConfigField("String", "PLANTNET_API_KEY", plantNetKey.asBuildConfigString())
        buildConfigField("String", "OPENCODE_SERVER_BASE_URL", openCodeBaseUrl.asBuildConfigString())
    }

    buildTypes {
        debug {
            manifestPlaceholders["allowCleartextTraffic"] = "true"
        }
        release {
            manifestPlaceholders["allowCleartextTraffic"] = "false"
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
