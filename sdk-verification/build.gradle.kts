plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

val kotlinSdkVersion = providers.gradleProperty("sdkVerificationKotlinVersion").orElse(rootProject.version.toString())
val experimentVersion = providers.gradleProperty("sdkVerificationExperimentVersion").orElse("1.17.0")
val sessionReplayVersion =
    providers.gradleProperty("sdkVerificationSessionReplayVersion")
        .orElse("0.30.0")
val engagementVersion = providers.gradleProperty("sdkVerificationEngagementVersion").orElse("3.15.0")
val engagementNativeLibPath = providers.gradleProperty("sdkVerificationEngagementNativeLibPath")

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

dependencies {
    // Deliberately consume Maven coordinates instead of project dependencies. This catches the
    // same metadata and dependency-resolution failures a customer would see before release.
    testImplementation("com.amplitude:analytics-android:${kotlinSdkVersion.get()}")
    testImplementation("com.amplitude:experiment-android-client:${experimentVersion.get()}") {
        exclude(group = "com.amplitude", module = "analytics-core")
    }
    testImplementation("com.amplitude:plugin-session-replay-android:${sessionReplayVersion.get()}")
    testImplementation("com.amplitude:amplitude-engagement-android:${engagementVersion.get()}")
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.test.core)
    testImplementation(libs.robolectric)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit4)
}

tasks.withType<Test> {
    useJUnitPlatform()
    if (engagementNativeLibPath.isPresent) {
        jvmArgs("-Djava.library.path=${engagementNativeLibPath.get()}")
    } else {
        filter {
            excludeTestsMatching("com.amplitude.verification.engagement.EngagementPluginIntegrationTest")
            excludeTestsMatching("com.amplitude.verification.engagement.EngagementPluginNonAmplitudeHostTest")
            excludeTestsMatching("com.amplitude.verification.engagement.AllBladesNonAmplitudeHostTest")
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val verifySdkVerificationCoordinates =
    tasks.register("verifySdkVerificationCoordinates") {
        group = "verification"
        description = "Verifies that release tests resolved the intended SDK artifacts."

        doLast {
            val expectedVersions =
                mutableMapOf(
                    "analytics-android" to kotlinSdkVersion.get(),
                    "analytics-core" to kotlinSdkVersion.get(),
                    "experiment-android-client" to experimentVersion.get(),
                    "plugin-session-replay-android" to sessionReplayVersion.get(),
                    "session-replay-android" to sessionReplayVersion.get(),
                    "amplitude-engagement-android" to engagementVersion.get(),
                )
            val resolvedVersions =
                configurations
                    .getByName("debugUnitTestRuntimeClasspath")
                    .incoming
                    .resolutionResult
                    .allComponents
                    .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                    .filter { it.group == "com.amplitude" }
                    .associate { it.module to it.version }

            expectedVersions.forEach { (artifact, expectedVersion) ->
                val resolvedVersion = resolvedVersions[artifact]
                check(resolvedVersion == expectedVersion) {
                    "Expected com.amplitude:$artifact:$expectedVersion but resolved $resolvedVersion"
                }
            }
        }
    }

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(verifySdkVerificationCoordinates)
}
