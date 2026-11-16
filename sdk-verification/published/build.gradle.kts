import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

val releasedUnified =
    Properties().apply {
        file("../../unified/gradle.properties").inputStream().use { load(it) }
    }.getProperty("VERSION_NAME")
val unifiedVersion = providers.gradleProperty("unifiedVersion").orElse(releasedUnified)
val overrides =
    mapOf(
        "analytics-android" to "analyticsVersion",
        "analytics-core" to "analyticsVersion",
        "experiment-android-client" to "experimentVersion",
        "plugin-session-replay-android" to "sessionReplayVersion",
        "session-replay-android" to "sessionReplayVersion",
    ).mapNotNull { (module, property) -> providers.gradleProperty(property).orNull?.let { module to it } }
        .toMap()

android {
    namespace = "com.amplitude.sdk.verification"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets {
        getByName("main").manifest.srcFile("../src/main/AndroidManifest.xml")
        getByName("test") {
            java.directories += "../src/test/java"
            kotlin.directories += "../src/test/kotlin"
        }
    }
    testOptions {
        targetSdk = 35
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    testImplementation("com.amplitude:unified-android:${unifiedVersion.get()}") {
        version { strictly(unifiedVersion.get()) }
    }
    constraints {
        overrides.forEach { (module, selectedVersion) ->
            testImplementation("com.amplitude:$module") { version { strictly(selectedVersion) } }
        }
    }
}

apply(from = "../test-dependencies.gradle.kts")

val verifyPublishedDependencies = tasks.register("verifyPublishedDependencies") {
    group = "verification"
    description = "Reports resolved SDK versions and verifies published dependencies and explicit overrides."
    val report = layout.buildDirectory.file("reports/sdk-versions.txt")
    doLast {
        val resolution = configurations.getByName("debugUnitTestRuntimeClasspath").incoming.resolutionResult
        check(
            resolution.allComponents.none {
                it.id is ProjectComponentIdentifier && it.id != resolution.rootComponent.get().id
            },
        ) { "Published regression tests must not substitute project dependencies" }
        val versions = resolution.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .filter { it.group == "com.amplitude" }
            .associate { it.module to it.version }
        val resolved = versions.toSortedMap().entries.joinToString("\n") { (module, version) -> "com.amplitude:$module:$version" }
        logger.lifecycle("Resolved SDKs:\n$resolved")
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText("$resolved\n")
        }
        (overrides + ("unified-android" to unifiedVersion.get())).forEach { (module, expected) ->
            check(versions[module] == expected) { "Expected com.amplitude:$module:$expected but resolved ${versions[module]}" }
        }
        val unified = resolution.allComponents.single {
            (it.id as? ModuleComponentIdentifier)?.let { id -> id.group == "com.amplitude" && id.module == "unified-android" } == true
        }
        val declaredModules = unified.dependencies
            .mapNotNull { (it as? ResolvedDependencyResult)?.selected?.id as? ModuleComponentIdentifier }
            .filter { it.group == "com.amplitude" }
            .map { it.module }.toSet()
        val expectedModules = setOf("analytics-android", "experiment-android-client", "plugin-session-replay-android")
        check(declaredModules.containsAll(expectedModules)) {
            "Unified metadata must declare $expectedModules but found $declaredModules"
        }
    }
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach { dependsOn(verifyPublishedDependencies) }
