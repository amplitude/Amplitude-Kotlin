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

// Get AMPLITUDE_API_KEY from localProps. Fail build if not found.
val AMPLITUDE_API_KEY: String = localProps.getProperty("AMPLITUDE_API_KEY")
    ?: throw GradleException(
        "AMPLITUDE_API_KEY not found in root project's local.properties (${localPropertiesFile.absolutePath}). " +
        "Please ensure it is defined in that file."
    )

val EXPERIMENT_API_KEY: String = ""

android {
    namespace = "com.amplitude.android.sample"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.amplitude.android.sample"
        minSdk = 19
        targetSdk = 34
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
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":android"))
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("com.google.android.gms:play-services-ads:20.6.0")
    implementation("com.google.android.gms:play-services-appset:16.0.2")
    implementation("com.amplitude:experiment-android-client:1.6.3")
    // For trouble shooting plugin
    implementation(group = "com.google.code.gson", name = "gson", version = "2.10")
    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.12.0")
}
