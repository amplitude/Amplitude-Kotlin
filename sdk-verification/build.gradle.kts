plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.amplitude.sdk.verification"
    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidVersions.MIN_SDK
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
}

// Keep the checkout under test even when a published blade requests Analytics transitively.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.amplitude:analytics-core")).using(project(":analytics-core"))
        substitute(module("com.amplitude:analytics-android")).using(project(":android"))
    }
}

dependencies {
    testImplementation(project(":unified"))
}

apply(from = "test-dependencies.gradle.kts")

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    kotlinScriptAdditionalPaths {
        include(fileTree("published") { include("*.gradle.kts") })
    }
}
