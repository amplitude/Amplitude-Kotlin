import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties")

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

android {
    namespace = "com.amplitude.android.streaming.sample"

    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        applicationId = "com.amplitude.android.streaming.sample"
        minSdk = AndroidVersions.MIN_SDK
        targetSdk = AndroidVersions.TARGET_SDK
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${amplitudeApiKey}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        abortOnError = false
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = false
    }
}

dependencies {
    implementation(project(":android"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.ktx)

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}
