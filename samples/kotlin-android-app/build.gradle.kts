import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// Load properties from local.properties in the root project
val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties") // Refers to local.properties in the root project directory

if (localPropertiesFile.exists() && localPropertiesFile.isFile) {
    try {
        localPropertiesFile.inputStream().use { input ->
            localProps.load(input)
        }
    } catch (e: Exception) {
        throw GradleException("Failed to load local.properties from ${localPropertiesFile.absolutePath}", e)
    }
}

val AMPLITUDE_API_KEY: String = localProps.getProperty("AMPLITUDE_API_KEY") ?: ""
val EXPERIMENT_API_KEY: String = localProps.getProperty("EXPERIMENT_API_KEY") ?: ""

android {
    namespace = "com.amplitude.android.sample"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.amplitude.android.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${AMPLITUDE_API_KEY}\"")
        buildConfigField("String", "EXPERIMENT_API_KEY", "\"${EXPERIMENT_API_KEY}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    lint {
        abortOnError = false
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(project(":android"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.playServicesAds)
    implementation(libs.playServicesAppset)
    implementation(libs.experiment.android.client)
    // For trouble shooting plugin
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.activity.compose)

    // Optional - Compose UI tooling for debugging
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
