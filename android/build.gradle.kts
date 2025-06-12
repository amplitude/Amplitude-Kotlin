plugins {
    id("com.android.library")
    kotlin("android")
}

extra.apply {
    set("PUBLISH_NAME", "Amplitude Android Kotlin SDK")
    set("PUBLISH_DESCRIPTION", "Amplitude Kotlin client-side SDK for Android")
    set("PUBLISH_ARTIFACT_ID", "analytics-android")
}

apply(from = "${'$'}{rootDir}/gradle/publish-module.gradle.kts")

android {
    namespace = "com.amplitude.android"

    compileSdk = 34

    defaultConfig {
        multiDexEnabled = true

        minSdk = 19
        targetSdk = 34
        versionName = project.property("PUBLISH_VERSION") as String
        buildConfigField("String", "AMPLITUDE_VERSION", "\"${'$'}version\"")

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.analytics.connector)
    implementation(libs.core.ktx)
    implementation(libs.activity.ktx)
    compileOnly(libs.fragment.ktx)
    compileOnly(libs.compose.ui)

    testImplementation(libs.mockk)
    testImplementation(project(":core"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    testImplementation(libs.json)
    testImplementation(libs.play.services.base)
    testImplementation(libs.play.services.ads.identifier)
    testImplementation(libs.play.services.appset)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.test.runner)
}

tasks.dokkaHtmlPartial.configure {
    failOnWarning.set(true)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}
