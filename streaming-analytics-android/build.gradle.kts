import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // TODO: Publishing is off until the public API is ready.
    // alias(libs.plugins.mavenPublish)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.amplitude.android.streaming"
    compileSdk = AndroidVersions.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidVersions.MIN_SDK
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"${version}\"")
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

/*
TODO: Publishing is off until the public API is ready.
mavenPublishing {
    coordinates(artifactId = "streaming-analytics-android")

    pom {
        name.set("Amplitude Streaming Analytics Android")
        description.set("Amplitude streaming analytics for Android (Media3 / ExoPlayer)")
    }

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )
}
*/

dependencies {
    api(project(":android"))
    api(libs.media3.common)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.ima)

    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
    testImplementation(libs.mockwebserver)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    testImplementation(libs.test.runner)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// BCV's plugin does not register apiDump/apiCheck for AGP 9 built-in Kotlin.
// https://github.com/Kotlin/binary-compatibility-validator/issues/312
val bcvRuntimeClasspath =
    configurations.register("bcvRuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    add(bcvRuntimeClasspath.name, "org.ow2.asm:asm:9.6")
    add(bcvRuntimeClasspath.name, "org.ow2.asm:asm-tree:9.6")
    add(
        bcvRuntimeClasspath.name,
        "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}",
    )
}

val apiDumpFile = layout.projectDirectory.file("api/${project.name}.api")

val apiBuild =
    tasks.register<KotlinApiBuildTask>("apiBuild") {
        description =
            "Builds Kotlin API for release compilations of ${project.name}. Complementary task and shouldn't be called manually"
        dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
        inputClassesDirs.from(
            tasks.named("compileReleaseKotlin").map { it.outputs.files },
            tasks.named("compileReleaseJavaWithJavac").map { it.outputs.files },
        )
        ignoredClasses.add("com.amplitude.android.streaming.BuildConfig")
        outputApiFile.set(layout.buildDirectory.file("api/${project.name}.api"))
        runtimeClasspath.from(bcvRuntimeClasspath)
    }

val apiCheck =
    tasks.register<KotlinApiCompareTask>("apiCheck") {
        group = "verification"
        description =
            "Checks signatures of public API against the golden value in API folder for ${project.name}"
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
