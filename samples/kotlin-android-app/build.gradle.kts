plugins {
    id("com.android.application")
    kotlin("android")
}

extra.apply {
    set("AMPLITUDE_API_KEY", if (project.hasProperty("AMPLITUDE_API_KEY")) project.property("AMPLITUDE_API_KEY") else "")
    set("EXPERIMENT_API_KEY", "")
}

android {
    namespace = "com.amplitude.android.sample"

    compileSdk = 34

    defaultConfig {
        applicationId = "com.amplitude.android.sample"
        minSdk = 19
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${extra["AMPLITUDE_API_KEY"]}\"")
        buildConfigField("String", "EXPERIMENT_API_KEY", "\"${extra["EXPERIMENT_API_KEY"]}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    lint {
        abortOnError = false
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":android"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.ads)
    implementation(libs.play.services.appset)
    implementation(libs.experiment.client)
    // For trouble shooting plugin
    implementation(libs.gson)
    implementation(libs.okhttp)
}
