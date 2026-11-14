plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val computedVersionCode = if (ciRunNumber != null) 100 + ciRunNumber else 5
val computedVersionName = if (ciRunNumber != null) "2.3.$ciRunNumber" else "2.3.0"

val cloudName = (System.getenv("CLOUDINARY_CLOUD_NAME") ?: "pf9pdwlp").ifBlank { "pf9pdwlp" }
val cloudApiKey = System.getenv("CLOUDINARY_API_KEY") ?: ""
val cloudApiSecret = System.getenv("CLOUDINARY_API_SECRET") ?: ""

fun escapeBuildConfig(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "cz.pshornicek.vyveska"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.pshornicek.vyveska"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${escapeBuildConfig(cloudName)}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${escapeBuildConfig(cloudApiKey)}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${escapeBuildConfig(cloudApiSecret)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // pro snadné stažení a spuštění bez vlastního klíče
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
}
