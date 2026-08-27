import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.amplitude.unified"
    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidVersions.MIN_SDK
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "UNIFIED_VERSION", "\"${version}\"")
    }

    compileOptions {
        sourceCompatibility = JavaConfig.JAVA_VERSION
        targetCompatibility = JavaConfig.JAVA_VERSION
    }

    testOptions {
        targetSdk = 35
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        targetSdk = AndroidVersions.TARGET_SDK
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    coreLibrariesVersion = libs.versions.kotlinCoreLibraries.get()
    explicitApi()
}

mavenPublishing {
    coordinates(artifactId = "unified-android")

    pom {
        name.set("Amplitude Unified Android SDK")
        description.set("Unified entry point for Amplitude Android SDKs")
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
    api(project(":android"))
    api(libs.session.replay.android)
    api(libs.unified.experiment.android.client)
    api(libs.engagement.android)

    testImplementation(libs.mockk)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
