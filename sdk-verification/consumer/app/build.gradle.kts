import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releasedUnified = Properties().apply {
    rootDir.resolve("../../unified/gradle.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME")
val unifiedVersion = providers.gradleProperty("unifiedVersion").orElse(releasedUnified).get()

android {
    namespace = "com.amplitude.unified.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amplitude.unified.consumer"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Exercise the SDKs' packaged consumer rules without app keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.amplitude:unified-android:$unifiedVersion")
}
