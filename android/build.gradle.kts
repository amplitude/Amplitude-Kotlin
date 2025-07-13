import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    alias(libs.plugins.mavenPublish)
}

android {
    namespace = "com.amplitude.android"
    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidVersions.MIN_SDK
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "AMPLITUDE_VERSION", "\"${version}\"")
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
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        buildConfig = true
    }
}

mavenPublishing {
    coordinates(artifactId = "analytics-android")

    pom {
        name.set("Amplitude Android Kotlin SDK")
        description.set("Amplitude Kotlin client-side SDK for Android")
    }

    configure(
        AndroidSingleVariantLibrary(
            // the published variant
            variant = "release",
            // whether to publish a sources jar
            sourcesJar = true,
            // whether to publish a javadoc jar
            publishJavadocJar = true,
        ),
    )
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

    // Junit 5 dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // Junit 5 required dependencies
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // Junit optional dependencies
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.vintage.engine)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    testImplementation(libs.json)
    testImplementation(libs.play.services.base)
    testImplementation(libs.playServicesAdsIdentifier)
    testImplementation(libs.playServicesAppset)
    testImplementation(libs.test.runner)

    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
