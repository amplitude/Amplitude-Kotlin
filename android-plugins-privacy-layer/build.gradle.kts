import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.amplitude.android.plugins.privacylayer"
    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidVersions.MIN_SDK
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaConfig.JAVA_VERSION
        targetCompatibility = JavaConfig.JAVA_VERSION
    }
    kotlinOptions {
        jvmTarget = KotlinConfig.JVM_TARGET
    }
    testOptions {
        targetSdk = AndroidVersions.TARGET_SDK
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    lint {
        targetSdk = AndroidVersions.TARGET_SDK
    }
}

mavenPublishing {
    coordinates(artifactId = "android-plugins-privacy-layer")

    pom {
        name.set("Amplitude Privacy Layer Plugin")
        description.set("Privacy layer plugin for Amplitude Android SDK that detects and redacts PII data")
    }

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )
}

dependencies {
    // Depend on the android SDK
    api(project(":android"))

    // MLKit Entity Extraction (will be added in Phase 2)
    // implementation("com.google.mlkit:entity-extraction:16.0.0-beta6")

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.mockk)
    testImplementation(project(":core"))
    testImplementation(libs.coroutines.test)

    // Junit 5 dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.vintage.engine)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
