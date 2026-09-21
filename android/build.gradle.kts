import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.android.junit5)
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
    testOptions {
        targetSdk = AndroidVersions.TARGET_SDK
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
    // Keep the published stdlib compatible with our Kotlin 1.9 metadata target.
    // AGP 9 requires KGP 2.2.10, but publishing stdlib 2.2.10 would break consumers at 1.9/2.0
    coreLibrariesVersion = libs.versions.kotlinCoreLibraries.get()
    explicitApi()
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
    api(project(":analytics-core"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.analytics.connector)
    implementation(libs.core.ktx)
    implementation(libs.curtains)
    compileOnly(libs.okhttp)
    compileOnly(libs.fragment.ktx)
    compileOnly(libs.compose.ui)

    testImplementation(libs.mockk)
    testImplementation(project(":analytics-core"))
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
    testRuntimeOnly(libs.junit.platform.launcher)

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
