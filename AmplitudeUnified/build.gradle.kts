plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.amplitude.publish-module-plugin")
}

android {
    namespace = "com.amplitude.android.unified"
    compileSdk = BuildConfig.Versions.Android.COMPILE_SDK

    defaultConfig {
        minSdk = BuildConfig.Versions.Android.MIN_SDK
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

publication {
    name = "Amplitude Unified Android SDK"
    description = "Amplitude Unified client-side SDK for Android"
    artifactId = "analytics-unified-android"
}

dependencies {
    api(project(":android"))
    api(libs.sessionReplayAndroid)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
