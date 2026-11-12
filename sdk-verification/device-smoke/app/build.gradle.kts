import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releasedUnified = Properties().apply {
    rootDir.resolve("../../unified/gradle.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME")
val unifiedVersion = providers.gradleProperty("unifiedVersion").orElse(releasedUnified).get()

// Optional: force a newer analytics-android than the one unified-android pins, to verify the
// "customer upgrades Analytics independently" combination.
val analyticsVersion = providers.gradleProperty("analyticsVersion").orNull

// Reached from the device through `adb reverse tcp:8089 tcp:8089`.
val mockServerUrl = providers.gradleProperty("mockServerUrl").orElse("http://127.0.0.1:8089").get()

android {
    namespace = "com.amplitude.unified.smoke"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amplitude.unified.smoke"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MOCK_SERVER_URL", "\"$mockServerUrl\"")
    }

    buildTypes {
        release {
            // The point of the release variant: shrink with R8 using only the SDKs' consumer rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
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
    constraints {
        analyticsVersion?.let { version ->
            implementation("com.amplitude:analytics-android") { version { strictly(version) } }
            implementation("com.amplitude:analytics-core") { version { strictly(version) } }
        }
    }
}

// Record selected versions rather than guessing which transitive dependency won resolution.
tasks.register("writeSmokeVersions") {
    doLast {
        val versions = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .filter { it.group == "com.amplitude" }
            .associate { it.module to it.version }
        check(versions["unified-android"] == unifiedVersion)
        analyticsVersion?.let { check(versions["analytics-android"] == it) }
        check(versions["analytics-core"] == versions["analytics-android"])
        val output = rootDir.resolve("build/smoke/versions.txt")
        output.parentFile.mkdirs()
        output.writeText("${versions.getValue("unified-android")}\n${versions.getValue("analytics-android")}\n")
    }
}
