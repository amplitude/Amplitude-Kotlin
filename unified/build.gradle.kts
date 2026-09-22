import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.amplitude.unified"
    // Session Replay 0.30.0 carries minCompileSdk = 36 AAR metadata.
    compileSdk = 36

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
    api(libs.unified.analytics.android)
    api(libs.unified.session.replay.android)
    api(libs.unified.experiment.android.client)

    compileOnly(libs.coroutines.core)

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

// Unified releases on its own cadence (release-unified.yml), so core releases must not publish it.
val isUnifiedRelease =
    providers.gradleProperty("amplitude.unified.release")
        .map(String::toBooleanStrict)
        .getOrElse(false)
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf("unified publishes only with -Pamplitude.unified.release=true") { isUnifiedRelease }
}
tasks.matching { it.name == "createStagingRepository" }.configureEach {
    onlyIf("unified stages only with -Pamplitude.unified.release=true") { isUnifiedRelease }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// BCV does not register API tasks for AGP 9 built-in Kotlin Android libraries.
val bcvRuntimeClasspath =
    configurations.register("bcvRuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    add(bcvRuntimeClasspath.name, "org.ow2.asm:asm:9.6")
    add(bcvRuntimeClasspath.name, "org.ow2.asm:asm-tree:9.6")
    add(bcvRuntimeClasspath.name, "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
}

val apiDumpFile = layout.projectDirectory.file("api/${project.name}.api")

val apiBuild =
    tasks.register<KotlinApiBuildTask>("apiBuild") {
        description = "Builds Kotlin API for release compilations of ${project.name}"
        dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
        inputClassesDirs.from(
            tasks.named("compileReleaseKotlin").map { it.outputs.files },
            tasks.named("compileReleaseJavaWithJavac").map { it.outputs.files },
        )
        outputApiFile.set(layout.buildDirectory.file("api/${project.name}.api"))
        runtimeClasspath.from(bcvRuntimeClasspath)
    }

val apiCheck =
    tasks.register<KotlinApiCompareTask>("apiCheck") {
        group = "verification"
        description = "Checks public API against the dump for ${project.name}"
        projectApiFile.set(apiDumpFile)
        generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
    }

tasks.register("apiDump") {
    group = "other"
    description = "Syncs the API file for ${project.name}"
    dependsOn(apiBuild)
    val builtApi = apiBuild.flatMap { it.outputApiFile }
    doLast {
        builtApi.get().asFile.copyTo(apiDumpFile.asFile, overwrite = true)
    }
}

tasks.named("check") {
    dependsOn(apiCheck)
}
