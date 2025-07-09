import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("java")
    id("kotlin")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.mavenPublish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
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
    implementation(libs.kotlin.stdlib)

    // MAIN DEPS
    compileOnly(libs.json)
    compileOnly(libs.okhttp)
    implementation(libs.coroutines.core)

    testImplementation(libs.json)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
