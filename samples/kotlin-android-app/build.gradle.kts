import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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

val amplitudeApiKey: String = localProps.getProperty("AMPLITUDE_API_KEY") ?: ""
val experimentApiKey: String = localProps.getProperty("EXPERIMENT_API_KEY") ?: ""

android {
    namespace = "com.amplitude.android.sample"

    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        applicationId = "com.amplitude.android.sample"
        minSdk = AndroidVersions.MIN_SDK
        targetSdk = AndroidVersions.TARGET_SDK
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${amplitudeApiKey}\"")
        buildConfigField("String", "EXPERIMENT_API_KEY", "\"${experimentApiKey}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaConfig.JAVA_VERSION
        targetCompatibility = JavaConfig.JAVA_VERSION
    }
    kotlinOptions {
        jvmTarget = KotlinConfig.JVM_TARGET
    }
    lint {
        abortOnError = false
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlinCompilerExtension.get()
    }
}

dependencies {
    implementation(project(":android"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
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
