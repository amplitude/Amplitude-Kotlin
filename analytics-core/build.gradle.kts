import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenPublish)
}

java {
    sourceCompatibility = JavaConfig.JAVA_VERSION
    targetCompatibility = JavaConfig.JAVA_VERSION
}

kotlin {
    // Keep the published stdlib compatible with our Kotlin 1.9 metadata target.
    // AGP 9 requires KGP 2.2.10, but publishing stdlib 2.2.10 would break consumers at 1.9/2.0
    coreLibrariesVersion = libs.versions.kotlinCoreLibraries.get()
    explicitApi()
}

mavenPublishing {
    coordinates(artifactId = "analytics-core")

    pom {
        name.set("Amplitude Kotlin Core")
        description.set("Amplitude Kotlin Core library")
    }

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            // whether to publish a sources jar
            sourcesJar = true,
        ),
    )
}

dependencies {
    // MAIN DEPS
    compileOnly(libs.json)
    compileOnly(libs.okhttp)
    implementation(libs.coroutines.core)

    testImplementation(libs.json)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

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

    testImplementation(libs.mockwebserver)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
